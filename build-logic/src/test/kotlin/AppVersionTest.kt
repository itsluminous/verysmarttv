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
    fun `code is major times 10000 plus minor times 100 plus patch`() {
        assertEquals(10203, AppVersion.from("1.2.3").code)
        assertEquals(10000, AppVersion.from("1.0.0").code)
        assertEquals(999999, AppVersion.from("99.99.99").code)
        assertTrue(AppVersion.from("1.2.3").fromProperty)
    }

    @Test
    fun `a zero version still gets code 1 because Android rejects 0`() {
        assertEquals(1, AppVersion.from("0.0.0").code)
    }

    @Test
    fun `a pre-release suffix keeps the name and shares the final code`() {
        val rc = AppVersion.from("1.0.0-rc1")
        assertEquals("1.0.0-rc1", rc.name)
        assertEquals(AppVersion.from("1.0.0").code, rc.code)
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
