package app.letostudio.gameemulator.data.library

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import app.letostudio.gameemulator.analytics.Analytics
import app.letostudio.gameemulator.analytics.AppEvents
import app.letostudio.gameemulator.analytics.AppParams
import app.letostudio.gameemulator.analytics.GameHash
import app.letostudio.gameemulator.crash.CrashContext
import app.letostudio.gameemulator.crash.CrashReporter
import app.letostudio.gameemulator.data.db.GameDao
import app.letostudio.gameemulator.data.db.GameEntity
import app.letostudio.gameemulator.data.db.SaveSlotDao
import app.letostudio.gameemulator.data.prefs.SettingsStore
import app.letostudio.gameemulator.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** One file in the Import result list. [reason] null = added (or already in the library). */
data class ScanItem(val name: String, val ext: String, val sizeBytes: Long, val added: Boolean, val reason: String?)

data class ScanResult(
    val added: Int,
    val skipped: Int,
    val totalFiles: Int,
    val items: List<ScanItem>,
    val durationMs: Long,
)

sealed interface ScanState {
    data object Idle : ScanState
    data class Scanning(val checked: Int) : ScanState
    data class Done(val result: ScanResult) : ScanState
    data class Failed(val reason: String) : ScanState
}

enum class ScanTrigger(val wire: String) { FIRST_PICK("first_pick"), APP_START("app_start"), PULL_REFRESH("pull_refresh"), MANUAL("manual") }

/**
 * Library + import (F02–F05). SAF only: a persisted tree permission for the games folder
 * and single OpenDocument files — never MANAGE_EXTERNAL_STORAGE. Metadata comes from
 * [DiscReader]; ICON0 covers are cached under filesDir/covers.
 */
@Singleton
class LibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val saveSlotDao: SaveSlotDao,
    private val settingsStore: SettingsStore,
    private val analytics: Analytics,
    private val crashReporter: CrashReporter,
    private val crash: CrashContext,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val resolver: ContentResolver get() = context.contentResolver
    private val scanMutex = Mutex()
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    val games: Flow<List<GameEntity>> = gameDao.observeAll()
    val gameCount: Flow<Int> = gameDao.observeCount()
    val folderUri: Flow<String?> = settingsStore.folderUri

    fun game(id: Long): Flow<GameEntity?> = gameDao.observe(id)
    suspend fun getGame(id: Long): GameEntity? = gameDao.get(id)

    /** User picked a games folder (S06). Persists the read permission and scans it. */
    suspend fun onFolderPicked(tree: Uri): ScanState {
        val isChange = settingsStore.folderUri.first() != null
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching { resolver.takePersistableUriPermission(tree, flags) }
            .onFailure { crashReporter.recordNonFatal("saf", it, mapOf("op" to "persist_tree")) }
        settingsStore.setFolderUri(tree.toString())
        crash.breadcrumb("saf", "folder picked change=$isChange")
        analytics.log(AppEvents.IMPORT_FOLDER_PICK, mapOf(AppParams.IS_CHANGE to isChange))
        return scan(ScanTrigger.FIRST_PICK)
    }

    /** Scans the saved folder; app start / pull-to-refresh / manual. */
    suspend fun scan(trigger: ScanTrigger): ScanState = scanMutex.withLock {
        withContext(io) { scanLocked(trigger) }
    }

    /** App start: rescan only when a folder is set and its permission still holds. */
    suspend fun rescanOnStart() {
        if (settingsStore.folderUri.first() == null) return
        scan(ScanTrigger.APP_START)
    }

    private suspend fun scanLocked(trigger: ScanTrigger): ScanState {
        val treeString = settingsStore.folderUri.first() ?: return ScanState.Idle
        val tree = Uri.parse(treeString)
        if (resolver.persistedUriPermissions.none { it.uri == tree && it.isReadPermission }) {
            analytics.log(AppEvents.PERMISSION_DENIED, mapOf(AppParams.KIND to "folder"))
            analytics.log(AppEvents.IMPORT_SCAN_FAIL, mapOf(AppParams.REASON to "permission_lost"))
            crash.breadcrumb("saf", "permission lost")
            return ScanState.Failed("permission_lost").also { _scanState.value = it }
        }
        analytics.log(AppEvents.IMPORT_SCAN_START, mapOf(AppParams.TRIGGER to trigger.wire))
        crash.breadcrumb("import", "scan start ${trigger.wire}")
        _scanState.value = ScanState.Scanning(0)
        val start = System.currentTimeMillis()
        return try {
            val items = mutableListOf<ScanItem>()
            var checked = 0
            var added = 0
            val seen = mutableSetOf<String>()
            withTimeout(SCAN_TIMEOUT_MS) {
                walk(tree, DocumentsContract.getTreeDocumentId(tree)) { doc ->
                    checked++
                    if (checked % 10 == 0) _scanState.value = ScanState.Scanning(checked)
                    val format = GameFormat.fromFileName(doc.name)
                    if (format == null) {
                        val ext = doc.name.substringAfterLast('.', "").lowercase()
                        if (ext in NEAR_MISS) items += ScanItem(doc.label, ext, doc.size, false, "not_game")
                        return@walk
                    }
                    seen += doc.uri.toString()
                    when (val r = addOrRefresh(doc.uri, doc.name, doc.size, format, treeString)) {
                        AddResult.Added -> { added++; items += ScanItem(doc.label, format.ext, doc.size, true, null) }
                        AddResult.Existing -> items += ScanItem(doc.label, format.ext, doc.size, true, null)
                        is AddResult.Rejected -> items += ScanItem(doc.label, format.ext, doc.size, false, r.reason)
                    }
                }
            }
            // Files from this tree that disappeared are hidden, not deleted (saves stay).
            gameDao.all().filter { it.sourceTree == treeString }.forEach { g ->
                val present = g.uri in seen
                if (g.available != present) gameDao.setAvailable(g.id, present)
            }
            val result = ScanResult(
                added = added,
                skipped = items.count { !it.added },
                totalFiles = checked,
                items = items.sortedWith(compareBy({ !it.added }, { it.name.lowercase() })),
                durationMs = System.currentTimeMillis() - start,
            )
            analytics.log(
                AppEvents.IMPORT_SCAN_SUCCESS,
                mapOf(
                    AppParams.ADDED to result.added,
                    AppParams.SKIPPED to result.skipped,
                    AppParams.TOTAL_FILES to result.totalFiles,
                    AppParams.DURATION_MS to result.durationMs,
                ),
            )
            if (added > 0) maybeFirstGameAdded()
            ScanState.Done(result).also { _scanState.value = it }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            analytics.log(AppEvents.IMPORT_SCAN_FAIL, mapOf(AppParams.REASON to "timeout"))
            crashReporter.recordNonFatal("import_scan", e, mapOf("reason" to "timeout"))
            ScanState.Failed("timeout").also { _scanState.value = it }
        } catch (e: SecurityException) {
            analytics.log(AppEvents.IMPORT_SCAN_FAIL, mapOf(AppParams.REASON to "permission_lost"))
            crashReporter.recordNonFatal("import_scan", e, mapOf("reason" to "permission_lost"))
            ScanState.Failed("permission_lost").also { _scanState.value = it }
        } catch (e: Exception) {
            analytics.log(AppEvents.IMPORT_SCAN_FAIL, mapOf(AppParams.REASON to "io_error"))
            crashReporter.recordNonFatal("import_scan", e, mapOf("reason" to "io_error"))
            ScanState.Failed("io_error").also { _scanState.value = it }
        }
    }

    fun resetScanState() {
        _scanState.value = ScanState.Idle
    }

    /** F03: one file from OpenDocument (e.g. homebrew in Downloads). */
    suspend fun addSingleFile(uri: Uri): Result<GameEntity> = withContext(io) {
        runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val (name, size) = queryNameAndSize(uri)
        val format = GameFormat.fromFileName(name)
            ?: return@withContext Result.failure(NotAGameFileException("unsupported extension"))
        when (val r = addOrRefresh(uri, name, size, format, null)) {
            is AddResult.Rejected -> Result.failure(NotAGameFileException(r.reason))
            else -> {
                analytics.log(AppEvents.IMPORT_FILE_ADD, mapOf(AppParams.FORMAT to format.ext))
                if (r == AddResult.Added) maybeFirstGameAdded()
                Result.success(gameDao.byUri(uri.toString())!!)
            }
        }
    }

    suspend fun setFavorite(id: Long, on: Boolean) {
        gameDao.setFavorite(id, on)
        analytics.log(AppEvents.GAME_FAVORITE, mapOf(AppParams.ON to on))
    }

    suspend fun rename(id: Long, title: String) = gameDao.rename(id, title.trim().ifBlank { null })

    /** Removes from the library only — the file and its saves on disk are untouched. */
    suspend fun remove(id: Long) {
        gameDao.delete(id)
        saveSlotDao.deleteForGame(id)
    }

    /** Human-readable folder name for the Import folder card. */
    fun folderDisplayName(tree: String?): String? {
        tree ?: return null
        val docId = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(tree)) }.getOrNull() ?: return null
        val volume = docId.substringBefore(':').let { if (it.equals("primary", true)) "Internal storage" else it }
        val path = docId.substringAfter(':', "").trim('/')
        return if (path.isBlank()) volume else "$volume › ${path.replace("/", " › ")}"
    }

    // --- internals ---------------------------------------------------------------------

    private sealed interface AddResult {
        data object Added : AddResult
        data object Existing : AddResult
        data class Rejected(val reason: String) : AddResult
    }

    private suspend fun addOrRefresh(uri: Uri, name: String, size: Long, format: GameFormat, tree: String?): AddResult {
        val existing = gameDao.byUri(uri.toString())
        if (existing != null) {
            if (!existing.available) gameDao.setAvailable(existing.id, true)
            return AddResult.Existing
        }
        val info = try {
            readInfo(uri, format)
        } catch (e: NotAGameFileException) {
            return AddResult.Rejected("not_game")
        } catch (e: Exception) {
            crashReporter.recordNonFatal("disc_reader", e, mapOf("format" to format.ext))
            return AddResult.Rejected("unreadable")
        }
        val hash = info.discId?.let(GameHash::ofDiscId) ?: GameHash.ofContent(size, info.head)
        val cover = info.disc.icon0?.let { bytes ->
            runCatching {
                val dir = File(context.filesDir, "covers").apply { mkdirs() }
                File(dir, "$hash.png").apply { writeBytes(bytes) }.absolutePath
            }.getOrNull()
        }
        val title = info.disc.title?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.')
        val id = gameDao.insert(
            GameEntity(
                uri = uri.toString(),
                fileName = name,
                format = format.ext,
                sizeBytes = size,
                title = title,
                discId = info.discId,
                gameHash = hash,
                coverPath = cover,
                isHomebrew = info.disc.isHomebrew,
                sourceTree = tree,
            ),
        )
        return if (id > 0) AddResult.Added else AddResult.Existing
    }

    private class ReadInfo(val disc: DiscInfo, val head: ByteArray) {
        val discId: String? get() = disc.discId?.takeIf { it.isNotBlank() }
    }

    private fun readInfo(uri: Uri, format: GameFormat): ReadInfo {
        val pfd = resolver.openFileDescriptor(uri, "r") ?: throw NotAGameFileException("cannot open")
        pfd.use {
            FileInputStream(it.fileDescriptor).channel.use { ch ->
                val src = ChannelSource(ch)
                val head = src.read(0, 64 * 1024)
                return ReadInfo(DiscReader.read(format, src), head)
            }
        }
    }

    private fun queryNameAndSize(uri: Uri): Pair<String, Long> {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0).orEmpty() to (if (c.isNull(1)) 0L else c.getLong(1))
        }
        return (uri.lastPathSegment ?: "game") to 0L
    }

    private data class Doc(val uri: Uri, val name: String, val size: Long, val parent: String?) {
        val label: String get() = if (parent != null && name.equals("EBOOT.PBP", true)) "$parent/$name" else name
    }

    /** Recursive DocumentsContract walk (much faster than DocumentFile). */
    private suspend fun walk(tree: Uri, docId: String, depth: Int = 0, parentName: String? = null, onFile: suspend (Doc) -> Unit) {
        if (depth > MAX_DEPTH) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val dirs = mutableListOf<Pair<String, String>>()
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1).orEmpty()
                val mime = c.getString(2)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (!name.startsWith(".")) dirs += id to name
                } else {
                    val size = if (c.isNull(3)) 0L else c.getLong(3)
                    onFile(Doc(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, size, parentName))
                }
            }
        }
        dirs.forEach { (id, name) -> walk(tree, id, depth + 1, name, onFile) }
    }

    private suspend fun maybeFirstGameAdded() {
        if (settingsStore.markFirstGameAdded()) {
            analytics.log(AppEvents.FIRST_GAME_ADDED, mapOf(AppParams.SECONDS_SINCE_INSTALL to settingsStore.secondsSinceInstall()))
        }
    }

    private companion object {
        const val SCAN_TIMEOUT_MS = 5 * 60_000L
        const val MAX_DEPTH = 8
        /** Files worth reporting as skipped (others are silently ignored). */
        val NEAR_MISS = setOf("zip", "7z", "rar", "bin", "img", "chd", "cue", "elf", "prx", "pbp~", "isz", "nrg", "mdf")
    }
}

/** [RandomSource] over a FileChannel (positional reads, thread-safe). */
class ChannelSource(private val ch: FileChannel) : RandomSource {
    override val size: Long = ch.size()
    override fun read(position: Long, length: Int): ByteArray {
        if (position >= size) return ByteArray(0)
        val n = minOf(length.toLong(), size - position).toInt()
        val buf = ByteBuffer.allocate(n)
        var pos = position
        while (buf.hasRemaining()) {
            val r = ch.read(buf, pos)
            if (r <= 0) break
            pos += r
        }
        return buf.array().copyOf(buf.position())
    }
}
