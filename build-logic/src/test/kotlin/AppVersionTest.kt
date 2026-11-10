import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun `no property is the dev build with code 1`() {
        for (property in listOf(null, "", "   ")) {
            val v = AppVersion.from(property)
            assertEquals(AppVersion.DEV_NAME, v.name)
            assertEquals(1, v.code)
            assertFalse(v.fromProperty)
        }
    }

    @Test
    fun `code is the epoch plus major times 10000 plus minor times 100 plus patch`() {
        assertEquals(1_010_203, AppVersion.from("1.2.3").code)
        assertEquals(1_010_000, AppVersion.from("1.0.0").code)
        assertEquals(1_000_100, AppVersion.from("0.1.0").code)
        assertEquals(1_999_999, AppVersion.from("99.99.99").code)
        assertTrue(AppVersion.from("1.2.3").fromProperty)
    }

    @Test
    fun `the renumbered first release outranks the candidates published before the epoch`() {
        // 1.0.0-rc1, rc2 and rc3 shipped with code 10000 (ADR-047): a TV with one of them must take 0.1.0-rc1.
        assertTrue(AppVersion.from("0.1.0-rc1").code > AppVersion.LAST_PRE_EPOCH_CODE)
        assertTrue(AppVersion.from("0.0.1").code > AppVersion.LAST_PRE_EPOCH_CODE)
        // And the order of versions is the order of codes from here on.
        val codes = listOf("0.1.0-rc1", "0.1.0", "0.1.1", "0.2.0", "1.0.0", "1.0.1").map { AppVersion.from(it).code }
        assertEquals(codes.sorted(), codes)
        assertEquals(codes[0], codes[1])   // the candidate shares the final's code
        assertTrue(codes.zipWithNext().drop(1).all { (a, b) -> a < b })
    }

    @Test
    fun `a zero version still gets a code Android accepts`() {
        assertEquals(AppVersion.EPOCH, AppVersion.from("0.0.0").code)
        assertTrue(AppVersion.from("0.0.0").code > 1)
    }

    @Test
    fun `a pre-release suffix keeps the name and shares the final code`() {
        val rc = AppVersion.from("0.1.0-rc1")
        assertEquals("0.1.0-rc1", rc.name)
        assertEquals(AppVersion.from("0.1.0").code, rc.code)
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("1.2.3", AppVersion.from(" 1.2.3 ").name)
    }

    @Test
    fun `two-part, prefixed and malformed names are refused`() {
        for (bad in listOf("1.2", "v1.2.3", "1.2.3.4", "1.2.x", "1.2.3-", "1.2.3 beta")) {
            assertThrows(bad, IllegalArgumentException::class.java) { AppVersion.from(bad) }
        }
    }

    @Test
    fun `a part of 100 or more is refused`() {
        for (bad in listOf("100.0.0", "1.100.0", "1.0.100")) {
            assertThrows(bad, IllegalArgumentException::class.java) { AppVersion.from(bad) }
        }
    }
}
