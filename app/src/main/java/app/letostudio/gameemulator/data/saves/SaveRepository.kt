package app.letostudio.gameemulator.data.saves

import android.content.Context
import android.graphics.Bitmap
import app.letostudio.gameemulator.analytics.Analytics
import app.letostudio.gameemulator.analytics.AppEvents
import app.letostudio.gameemulator.analytics.AppParams
import app.letostudio.gameemulator.crash.CrashContext
import app.letostudio.gameemulator.crash.CrashReporter
import app.letostudio.gameemulator.data.db.GameEntity
import app.letostudio.gameemulator.data.db.SaveSlotDao
import app.letostudio.gameemulator.data.db.SaveSlotEntity
import app.letostudio.gameemulator.di.IoDispatcher
import app.letostudio.gameemulator.monetization.ProGate
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Slot capacity for one game (F10): 3 manual slots free (+1 for 24 h via R1), unlimited Pro. */
data class SlotQuota(val used: Int, val limit: Int?) {
    val isFull: Boolean get() = limit != null && used >= limit
}

/**
 * Save states (F10): `filesDir/saves/<game_hash>/<slot>.state` (gzip) + `.png` thumbnail.
 * Writes go to a temp file first and are renamed, so a crash mid-write never corrupts a slot.
 */
@Singleton
class SaveRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: SaveSlotDao,
    private val analytics: Analytics,
    private val crashReporter: CrashReporter,
    private val crash: CrashContext,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    fun slots(gameId: Long): Flow<List<SaveSlotEntity>> = dao.observeForGame(gameId)
    suspend fun slotList(gameId: Long): List<SaveSlotEntity> = dao.forGame(gameId)
    val totalSaves: Flow<Int> = dao.observeCount()

    fun quota(game: GameEntity, slots: List<SaveSlotEntity>, isPro: Boolean, now: Long = System.currentTimeMillis()): SlotQuota {
        val used = slots.count { !it.isAuto }
        if (isPro) return SlotQuota(used, null)
        val extra = if (game.extraSlotUntil > now) 1 else 0
        return SlotQuota(used, ProGate.FREE_SAVE_SLOTS + extra)
    }

    /** Next manual slot index to write, or null when the free quota is full. */
    fun nextManualSlot(slots: List<SaveSlotEntity>, quota: SlotQuota): Int? {
        val usedIdx = slots.filter { !it.isAuto }.map { it.slotIndex }.toSet()
        if (quota.isFull) return null
        return generateSequence(1) { it + 1 }.first { it !in usedIdx }
    }

    suspend fun write(
        game: GameEntity,
        slotIndex: Int,
        state: ByteArray,
        thumb: Bitmap?,
    ): Boolean = withContext(io) {
        val auto = slotIndex == SaveSlotEntity.AUTO_SLOT
        crash.breadcrumb("save", "write start auto=$auto")
        try {
            val dir = File(context.filesDir, "saves/${game.gameHash}").apply { mkdirs() }
            val target = File(dir, "$slotIndex.state")
            val tmp = File(dir, "$slotIndex.state.tmp")
            GZIPOutputStream(tmp.outputStream().buffered()).use { it.write(state) }
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) error("rename failed")
            }
            val thumbFile = thumb?.let { bmp ->
                runCatching {
                    File(dir, "$slotIndex.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) } }
                }.getOrNull()
            }
            dao.upsert(
                SaveSlotEntity(
                    id = dao.get(game.id, slotIndex)?.id ?: 0,
                    gameId = game.id,
                    slotIndex = slotIndex,
                    statePath = target.absolutePath,
                    thumbPath = thumbFile?.absolutePath,
                    savedAt = System.currentTimeMillis(),
                ),
            )
            analytics.log(
                AppEvents.SAVE_STATE,
                mapOf(AppParams.SLOT_TYPE to if (auto) "auto" else "manual", AppParams.SLOT_INDEX to slotIndex),
            )
            crash.breadcrumb("save", "write end")
            true
        } catch (e: Exception) {
            crashReporter.recordNonFatal("save_state_write", e, mapOf("auto" to auto.toString()))
            false
        }
    }

    suspend fun read(slot: SaveSlotEntity, from: String): ByteArray? = withContext(io) {
        crash.breadcrumb("save", "read start")
        try {
            val bytes = GZIPInputStream(File(slot.statePath).inputStream().buffered()).use { it.readBytes() }
            analytics.log(
                AppEvents.LOAD_STATE,
                mapOf(AppParams.SLOT_TYPE to if (slot.isAuto) "auto" else "manual", AppParams.FROM to from),
            )
            crash.breadcrumb("save", "read end")
            bytes
        } catch (e: Exception) {
            crashReporter.recordNonFatal("save_state_read", e)
            null
        }
    }
}
