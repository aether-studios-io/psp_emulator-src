package app.letostudio.gameemulator.data.library

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscReaderTest {
    private val sfo = DiscFixtures.paramSfo(mapOf("TITLE" to "Cube Demo", "DISC_ID" to "HBRW00001", "CATEGORY" to "MG"))

    @Test
    fun `PBP yields title, disc id, category and icon`() {
        val info = DiscReader.read(GameFormat.PBP, ByteArraySource(DiscFixtures.pbp(sfo)))
        assertEquals("Cube Demo", info.title)
        assertEquals("HBRW00001", info.discId)
        assertEquals("MG", info.category)
        assertArrayEquals(DiscFixtures.PNG, info.icon0)
    }

    @Test
    fun `PBP without icon has null icon`() {
        val info = DiscReader.read(GameFormat.PBP, ByteArraySource(DiscFixtures.pbp(sfo, icon = null)))
        assertNull(info.icon0)
    }

    @Test
    fun `ISO reads PSP_GAME PARAM SFO and ICON0`() {
        val info = DiscReader.read(GameFormat.ISO, ByteArraySource(DiscFixtures.iso(sfo)))
        assertEquals("Cube Demo", info.title)
        assertArrayEquals(DiscFixtures.PNG, info.icon0)
    }

    @Test
    fun `CSO decompresses mixed deflated and stored blocks`() {
        val iso = DiscFixtures.iso(sfo)
        val cso = DiscFixtures.cso(iso)
        val src = CsoSource(ByteArraySource(cso))
        assertEquals(iso.size.toLong(), src.size)
        assertArrayEquals(iso.copyOfRange(30_000, 40_000), src.read(30_000, 10_000))
        val info = DiscReader.read(GameFormat.CSO, ByteArraySource(cso))
        assertEquals("HBRW00001", info.discId)
    }

    @Test(expected = NotAGameFileException::class)
    fun `zip renamed to iso is rejected`() {
        DiscReader.read(GameFormat.ISO, ByteArraySource(ByteArray(40_000) { 'P'.code.toByte() }))
    }

    @Test(expected = NotAGameFileException::class)
    fun `garbage pbp is rejected`() {
        DiscReader.read(GameFormat.PBP, ByteArraySource("not a pbp at all, just text".toByteArray()))
    }

    @Test
    fun `homebrew heuristic uses retail-style disc ids`() {
        assertTrue(DiscInfo("x", null, "MG", null).isHomebrew)
        assertTrue(DiscInfo("x", "HB0001", "MG", null).isHomebrew)
        assertFalse(DiscInfo("x", "ABCD12345", "UG", null).isHomebrew)
    }

    @Test
    fun `format from file name`() {
        assertEquals(GameFormat.ISO, GameFormat.fromFileName("a.ISO"))
        assertEquals(GameFormat.PBP, GameFormat.fromFileName("EBOOT.PBP"))
        assertEquals(GameFormat.CSO, GameFormat.fromFileName("x.cso"))
        assertNull(GameFormat.fromFileName("backup.zip"))
    }
}
