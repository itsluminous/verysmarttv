import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ReleaseSigningTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val env =
        mapOf(
            ReleaseSigning.ENV_STORE_FILE to "ci.jks",
            ReleaseSigning.ENV_STORE_PASSWORD to "env-store",
            ReleaseSigning.ENV_KEY_ALIAS to "verysmarttv",
            ReleaseSigning.ENV_KEY_PASSWORD to "env-key",
        )

    private fun propertiesFile(storeFile: String = "release.jks"): File =
        tmp.root.resolve("release.properties").apply {
            writeText(
                """
                storeFile=$storeFile
                storePassword=file-store
                keyAlias=verysmarttv
                keyPassword=file-key
                """.trimIndent(),
            )
        }

    @Test
    fun `nothing configured means debug signing`() {
        assertNull(ReleaseSigning.load(tmp.root.resolve("release.properties"), emptyMap(), tmp.root))
        assertNull(ReleaseSigning.load(tmp.root.resolve("release.properties"), env.mapValues { "" }, tmp.root))
    }

    @Test
    fun `release properties win over the environment and resolve a relative keystore against the file`() {
        tmp.newFile("release.jks")
        val (signing, source) = ReleaseSigning.load(propertiesFile(), env, tmp.newFolder("elsewhere"))!!
        assertEquals(ReleaseSigning.Source.PROPERTIES, source)
        assertEquals(tmp.root.resolve("release.jks"), signing.storeFile)
        assertEquals("file-store", signing.storePassword)
        assertEquals("verysmarttv", signing.keyAlias)
        assertEquals("file-key", signing.keyPassword)
    }

    @Test
    fun `an absolute keystore path in the file is used as is`() {
        val jks = tmp.newFile("abs.jks")
        val (signing, _) = ReleaseSigning.load(propertiesFile(jks.absolutePath), emptyMap(), tmp.root)!!
        assertEquals(jks.absoluteFile, signing.storeFile)
    }

    @Test
    fun `the environment is used when there is no properties file`() {
        val work = tmp.newFolder("work")
        work.resolve("ci.jks").writeBytes(byteArrayOf(1))
        val (signing, source) = ReleaseSigning.load(tmp.root.resolve("release.properties"), env, work)!!
        assertEquals(ReleaseSigning.Source.ENVIRONMENT, source)
        assertEquals(work.resolve("ci.jks"), signing.storeFile)
        assertEquals("env-store", signing.storePassword)
        assertEquals("env-key", signing.keyPassword)
    }

    @Test
    fun `a half-filled source stops the build and names what is missing`() {
        val partial = env - ReleaseSigning.ENV_KEY_PASSWORD
        val e = assertThrows(IllegalStateException::class.java) { ReleaseSigning.load(tmp.root.resolve("none"), partial, tmp.root) }
        assertTrue(e.message!!, ReleaseSigning.ENV_KEY_PASSWORD in e.message!!)
    }

    @Test
    fun `a keystore that does not exist stops the build`() {
        val e = assertThrows(IllegalStateException::class.java) { ReleaseSigning.load(propertiesFile("missing.jks"), emptyMap(), tmp.root) }
        assertTrue(e.message!!, "missing.jks" in e.message!!)
        val e2 = assertThrows(IllegalStateException::class.java) { ReleaseSigning.load(tmp.root.resolve("none"), env, tmp.root) }
        assertTrue(e2.message!!, "ci.jks" in e2.message!!)
    }

    @Test
    fun `passwords never appear in an error message`() {
        val e = assertThrows(IllegalStateException::class.java) { ReleaseSigning.load(tmp.root.resolve("none"), env, tmp.root) }
        assertTrue(e.message!!, "env-store" !in e.message!! && "env-key" !in e.message!!)
    }
}
