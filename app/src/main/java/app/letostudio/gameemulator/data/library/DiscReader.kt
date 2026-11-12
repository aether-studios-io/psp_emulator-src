package app.letostudio.gameemulator.data.library

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

/** Positional reads over a game file (FileChannel on device, ByteArray in tests). */
interface RandomSource {
    val size: Long

    /** Reads up to [length] bytes at [position]; returns fewer only at end of file. */
    fun read(position: Long, length: Int): ByteArray
}

class ByteArraySource(private val bytes: ByteArray) : RandomSource {
    override val size: Long get() = bytes.size.toLong()
    override fun read(position: Long, length: Int): ByteArray {
        if (position >= bytes.size) return ByteArray(0)
        val end = minOf(bytes.size.toLong(), position + length).toInt()
        return bytes.copyOfRange(position.toInt(), end)
    }
}

enum class GameFormat(val ext: String) {
    ISO("iso"), CSO("cso"), PBP("pbp");

    companion object {
        fun fromFileName(name: String): GameFormat? = when (name.substringAfterLast('.', "").lowercase()) {
            "iso" -> ISO
            "cso" -> CSO
            "pbp" -> PBP
            else -> null
        }
    }
}

/** What F05 extracts: title, disc id, category and the ICON0 bytes. */
data class DiscInfo(
    val title: String?,
    val discId: String?,
    val category: String?,
    val icon0: ByteArray?,
) {
    /** Homebrew heuristic: no retail-style disc id (ABCD12345) in PARAM.SFO. */
    val isHomebrew: Boolean
        get() = discId == null || !RETAIL_ID.matches(discId)

    private companion object {
        val RETAIL_ID = Regex("^[A-Z]{4}\\d{5}$")
    }
}

class NotAGameFileException(message: String) : Exception(message)

/**
 * Reads PARAM.SFO + ICON0.PNG from ISO (ISO 9660, `PSP_GAME/`), CSO (CISO v1/v2 block
 * container, raw-deflate blocks) and PBP (EBOOT container) files. No native code; works on
 * any [RandomSource]. Throws [NotAGameFileException] for files that are not game images.
 */
object DiscReader {

    fun read(format: GameFormat, source: RandomSource): DiscInfo = when (format) {
        GameFormat.PBP -> readPbp(source)
        GameFormat.ISO -> readIso(source)
        GameFormat.CSO -> readIso(CsoSource(source))
    }

    // --- PBP ----------------------------------------------------------------------------

    private fun readPbp(src: RandomSource): DiscInfo {
        val head = src.read(0, 40)
        if (head.size < 40 || head[0] != 0.toByte() || head[1] != 'P'.code.toByte() ||
            head[2] != 'B'.code.toByte() || head[3] != 'P'.code.toByte()
        ) {
            throw NotAGameFileException("missing PBP magic")
        }
        val bb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
        val offsets = IntArray(8) { bb.getInt(8 + it * 4) }
        val sfoOff = offsets[0].toLong() and 0xFFFFFFFFL
        val iconOff = offsets[1].toLong() and 0xFFFFFFFFL
        val icon1Off = offsets[2].toLong() and 0xFFFFFFFFL
        if (sfoOff >= iconOff || iconOff > src.size) throw NotAGameFileException("bad PBP offsets")
        val sfo = ParamSfo.parse(src.read(sfoOff, (iconOff - sfoOff).toInt().coerceAtMost(MAX_SFO)))
        val iconLen = (icon1Off - iconOff).toInt()
        val icon = if (iconLen in 8..MAX_ICON) src.read(iconOff, iconLen).takeIf(::isPng) else null
        return DiscInfo(sfo["TITLE"], sfo["DISC_ID"], sfo["CATEGORY"], icon)
    }

    // --- ISO 9660 -----------------------------------------------------------------------

    private const val SECTOR = 2048
    private const val MAX_SFO = 64 * 1024
    private const val MAX_ICON = 512 * 1024

    private data class DirEntry(val name: String, val lba: Long, val length: Int, val isDir: Boolean)

    private fun readIso(src: RandomSource): DiscInfo {
        val pvd = src.read(16L * SECTOR, SECTOR)
        if (pvd.size < SECTOR || pvd[0] != 1.toByte() || String(pvd, 1, 5, Charsets.US_ASCII) != "CD001") {
            throw NotAGameFileException("no ISO 9660 volume descriptor")
        }
        val root = parseRecord(pvd, 156) ?: throw NotAGameFileException("no root record")
        val pspGame = listDir(src, root).firstOrNull { it.isDir && it.name.equals("PSP_GAME", true) }
            ?: throw NotAGameFileException("no PSP_GAME directory")
        val files = listDir(src, pspGame)
        val sfoEntry = files.firstOrNull { !it.isDir && it.name.equals("PARAM.SFO", true) }
            ?: throw NotAGameFileException("no PARAM.SFO")
        val sfo = ParamSfo.parse(src.read(sfoEntry.lba * SECTOR, sfoEntry.length.coerceAtMost(MAX_SFO)))
        val icon = files.firstOrNull { !it.isDir && it.name.equals("ICON0.PNG", true) }
            ?.takeIf { it.length in 8..MAX_ICON }
            ?.let { src.read(it.lba * SECTOR, it.length) }
            ?.takeIf(::isPng)
        return DiscInfo(sfo["TITLE"], sfo["DISC_ID"], sfo["CATEGORY"], icon)
    }

    private fun listDir(src: RandomSource, dir: DirEntry): List<DirEntry> {
        val data = src.read(dir.lba * SECTOR, dir.length.coerceAtMost(256 * SECTOR))
        val out = mutableListOf<DirEntry>()
        var pos = 0
        while (pos < data.size) {
            val len = data[pos].toInt() and 0xFF
            if (len == 0) {
                // Records never straddle sectors: skip padding to the next sector.
                pos = (pos / SECTOR + 1) * SECTOR
                continue
            }
            parseRecord(data, pos)?.let { if (it.name != "." && it.name != "..") out += it }
            pos += len
        }
        return out
    }

    private fun parseRecord(buf: ByteArray, at: Int): DirEntry? {
        if (at + 33 > buf.size) return null
        val len = buf[at].toInt() and 0xFF
        if (len < 34 || at + len > buf.size) return null
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        val lba = bb.getInt(at + 2).toLong() and 0xFFFFFFFFL
        val size = bb.getInt(at + 10)
        val flags = buf[at + 25].toInt()
        val nameLen = buf[at + 32].toInt() and 0xFF
        val raw = buf.copyOfRange(at + 33, minOf(at + 33 + nameLen, buf.size))
        val name = when {
            nameLen == 1 && raw[0] == 0.toByte() -> "."
            nameLen == 1 && raw[0] == 1.toByte() -> ".."
            else -> String(raw, Charsets.US_ASCII).substringBefore(';')
        }
        return DirEntry(name, lba, size, flags and 0x02 != 0)
    }

    private fun isPng(b: ByteArray) =
        b.size >= 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte()
}

/** Decompressing view of a CSO (CISO) container as a plain ISO. */
class CsoSource(private val src: RandomSource) : RandomSource {
    private val blockSize: Int
    private val totalBytes: Long
    private val align: Int
    private val index: LongArray

    init {
        val h = src.read(0, 24)
        if (h.size < 24 || String(h, 0, 4, Charsets.US_ASCII) != "CISO") throw NotAGameFileException("missing CISO magic")
        val bb = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
        totalBytes = bb.getLong(8)
        blockSize = bb.getInt(16)
        align = h[21].toInt() and 0xFF
        if (blockSize <= 0 || totalBytes <= 0) throw NotAGameFileException("bad CISO header")
        val blocks = ((totalBytes + blockSize - 1) / blockSize).toInt()
        val raw = src.read(24, (blocks + 1) * 4)
        val ib = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        index = LongArray(blocks + 1) { ib.getInt(it * 4).toLong() and 0xFFFFFFFFL }
    }

    override val size: Long get() = totalBytes

    private fun block(n: Int): ByteArray {
        val entry = index[n]
        val plain = entry and 0x80000000L != 0L
        val start = (entry and 0x7FFFFFFFL) shl align
        val end = (index[n + 1] and 0x7FFFFFFFL) shl align
        val comp = src.read(start, (end - start).toInt().coerceAtLeast(0))
        if (plain) return comp.copyOf(blockSize)
        val inflater = Inflater(true)
        return try {
            inflater.setInput(comp)
            val out = ByteArray(blockSize)
            var off = 0
            while (off < blockSize && !inflater.finished()) {
                val n2 = inflater.inflate(out, off, blockSize - off)
                if (n2 == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                off += n2
            }
            out
        } finally {
            inflater.end()
        }
    }

    override fun read(position: Long, length: Int): ByteArray {
        if (position >= totalBytes) return ByteArray(0)
        val end = minOf(totalBytes, position + length)
        val out = ByteArrayOutputStream((end - position).toInt())
        var pos = position
        while (pos < end) {
            val b = (pos / blockSize).toInt()
            val data = block(b)
            val inBlock = (pos % blockSize).toInt()
            val n = minOf(blockSize - inBlock, (end - pos).toInt())
            out.write(data, inBlock, n)
            pos += n
        }
        return out.toByteArray()
    }
}

/** PARAM.SFO key/value table. */
object ParamSfo {
    fun parse(data: ByteArray): Map<String, String> {
        if (data.size < 20 || data[0] != 0.toByte() || data[1] != 'P'.code.toByte() ||
            data[2] != 'S'.code.toByte() || data[3] != 'F'.code.toByte()
        ) {
            throw NotAGameFileException("missing PSF magic")
        }
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val keyTable = bb.getInt(8)
        val dataTable = bb.getInt(12)
        val count = bb.getInt(16)
        val out = mutableMapOf<String, String>()
        for (i in 0 until count.coerceAtMost(256)) {
            val e = 20 + i * 16
            if (e + 16 > data.size) break
            val keyOff = bb.getShort(e).toInt() and 0xFFFF
            val fmt = bb.getShort(e + 2).toInt() and 0xFFFF
            val len = bb.getInt(e + 4)
            val dataOff = bb.getInt(e + 12)
            val key = cString(data, keyTable + keyOff) ?: continue
            val at = dataTable + dataOff
            if (at < 0 || at >= data.size) continue
            when (fmt) {
                0x0204, 0x0004 -> {
                    val end = minOf(data.size, at + len)
                    out[key] = String(data, at, end - at, Charsets.UTF_8).trimEnd('\u0000').trim()
                }
                0x0404 -> if (at + 4 <= data.size) out[key] = bb.getInt(at).toString()
            }
        }
        return out
    }

    private fun cString(b: ByteArray, at: Int): String? {
        if (at < 0 || at >= b.size) return null
        var end = at
        while (end < b.size && b[end] != 0.toByte()) end++
        return String(b, at, end - at, Charsets.US_ASCII)
    }
}
