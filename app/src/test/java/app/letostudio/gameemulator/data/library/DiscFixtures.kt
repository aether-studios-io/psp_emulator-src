package app.letostudio.gameemulator.data.library

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater

/** Builds tiny synthetic homebrew images in memory (no commercial data, nothing downloaded). */
object DiscFixtures {
    val PNG: ByteArray = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)

    fun paramSfo(values: Map<String, String>): ByteArray {
        val keys = values.keys.sorted()
        val keyTable = ByteArrayOutputStream()
        val keyOffsets = keys.map { k -> keyTable.size().also { keyTable.write(k.toByteArray()); keyTable.write(0) } }
        while (keyTable.size() % 4 != 0) keyTable.write(0)
        val dataTable = ByteArrayOutputStream()
        val entries = keys.map { k ->
            val bytes = values.getValue(k).toByteArray() + 0
            val maxLen = (bytes.size + 3) / 4 * 4
            val off = dataTable.size()
            dataTable.write(bytes)
            repeat(maxLen - bytes.size) { dataTable.write(0) }
            Triple(off, bytes.size, maxLen)
        }
        val headerSize = 20 + 16 * keys.size
        val keyStart = headerSize
        val dataStart = keyStart + keyTable.size()
        val bb = ByteBuffer.allocate(dataStart + dataTable.size()).order(ByteOrder.LITTLE_ENDIAN)
        bb.put(byteArrayOf(0, 'P'.code.toByte(), 'S'.code.toByte(), 'F'.code.toByte()))
        bb.putInt(0x0101).putInt(keyStart).putInt(dataStart).putInt(keys.size)
        keys.indices.forEach { i ->
            bb.putShort(keyOffsets[i].toShort()).putShort(0x0204).putInt(entries[i].second).putInt(entries[i].third).putInt(entries[i].first)
        }
        bb.put(keyTable.toByteArray()).put(dataTable.toByteArray())
        return bb.array()
    }

    fun pbp(sfo: ByteArray, icon: ByteArray? = PNG, data: ByteArray = ByteArray(64)): ByteArray {
        val header = 40
        val offSfo = header
        val offIcon = offSfo + sfo.size
        val offIcon1 = offIcon + (icon?.size ?: 0)
        val offData = offIcon1
        val bb = ByteBuffer.allocate(offData + data.size).order(ByteOrder.LITTLE_ENDIAN)
        bb.put(byteArrayOf(0, 'P'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte())).putInt(0x10000)
        bb.putInt(offSfo).putInt(offIcon).putInt(offIcon1).putInt(offIcon1).putInt(offIcon1).putInt(offIcon1).putInt(offData).putInt(offData + data.size)
        bb.put(sfo)
        icon?.let { bb.put(it) }
        bb.put(data)
        return bb.array()
    }

    private const val S = 2048

    private fun dirRecord(name: ByteArray, lba: Int, size: Int, dir: Boolean): ByteArray {
        val len = 33 + name.size + (if (name.size % 2 == 0) 1 else 0)
        val bb = ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN)
        bb.put(len.toByte()).put(0)
        bb.putInt(lba).putInt(Integer.reverseBytes(lba))
        bb.putInt(size).putInt(Integer.reverseBytes(size))
        bb.put(ByteArray(7))
        bb.put(if (dir) 2 else 0).put(0).put(0)
        bb.putShort(1).putShort(0x100)
        bb.put(name.size.toByte()).put(name)
        return bb.array()
    }

    /** Minimal ISO 9660: PVD at 16, root dir at 18, PSP_GAME dir at 19, SFO at 20, ICON0 at 21. */
    fun iso(sfo: ByteArray, icon: ByteArray? = PNG): ByteArray {
        val img = ByteArray(24 * S)
        val pvd = ByteBuffer.wrap(img, 16 * S, S)
        pvd.put(1).put("CD001".toByteArray()).put(1)
        System.arraycopy(dirRecord(byteArrayOf(0), 18, S, true), 0, img, 16 * S + 156, 34)
        img[17 * S] = 255.toByte(); System.arraycopy("CD001".toByteArray(), 0, img, 17 * S + 1, 5)
        var p = 18 * S
        for (r in listOf(dirRecord(byteArrayOf(0), 18, S, true), dirRecord(byteArrayOf(1), 18, S, true), dirRecord("PSP_GAME".toByteArray(), 19, S, true))) {
            System.arraycopy(r, 0, img, p, r.size); p += r.size
        }
        p = 19 * S
        val files = mutableListOf(dirRecord(byteArrayOf(0), 19, S, true), dirRecord(byteArrayOf(1), 18, S, true), dirRecord("PARAM.SFO;1".toByteArray(), 20, sfo.size, false))
        if (icon != null) files += dirRecord("ICON0.PNG;1".toByteArray(), 21, icon.size, false)
        for (r in files) { System.arraycopy(r, 0, img, p, r.size); p += r.size }
        System.arraycopy(sfo, 0, img, 20 * S, sfo.size)
        icon?.let { System.arraycopy(it, 0, img, 21 * S, it.size) }
        return img
    }

    /** CISO v1 wrapper with 2 KB blocks; even blocks deflated, odd blocks stored raw. */
    fun cso(iso: ByteArray): ByteArray {
        val block = S
        val blocks = (iso.size + block - 1) / block
        val payload = ByteArrayOutputStream()
        val index = IntArray(blocks + 1)
        val headerSize = 24 + (blocks + 1) * 4
        for (b in 0 until blocks) {
            index[b] = headerSize + payload.size()
            val raw = iso.copyOfRange(b * block, minOf(iso.size, (b + 1) * block))
            if (b % 2 == 1) {
                index[b] = index[b] or Int.MIN_VALUE
                payload.write(raw)
            } else {
                val d = Deflater(9, true)
                d.setInput(raw); d.finish()
                val buf = ByteArray(block * 2)
                val n = d.deflate(buf); d.end()
                payload.write(buf, 0, n)
            }
        }
        index[blocks] = headerSize + payload.size()
        val bb = ByteBuffer.allocate(headerSize + payload.size()).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("CISO".toByteArray()).putInt(24).putLong(iso.size.toLong()).putInt(block).put(1).put(0).putShort(0)
        index.forEach { bb.putInt(it) }
        bb.put(payload.toByteArray())
        return bb.array()
    }
}
