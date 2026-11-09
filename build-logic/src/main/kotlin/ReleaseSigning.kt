import java.io.File
import java.util.Properties

/**
 * Release signing material for the three apps (DESIGN.md §15.3, ADR-021): one keystore, alias
 * `verysmarttv`, read from `release.properties` next to `settings.gradle.kts` when that file exists,
 * else from the environment (`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, what
 * release.yml exports), else absent, in which case the app plugin signs release builds with the debug
 * key and says so. The properties file and the keystore are git-ignored; nothing here is ever printed.
 */
data class ReleaseSigning(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
) {
    /** How the material was found, for the one-line build message. */
    enum class Source { PROPERTIES, ENVIRONMENT }

    companion object {
        /** Keys of `release.properties`, in the order the file documents them. */
        const val PROP_STORE_FILE = "storeFile"
        const val PROP_STORE_PASSWORD = "storePassword"
        const val PROP_KEY_ALIAS = "keyAlias"
        const val PROP_KEY_PASSWORD = "keyPassword"

        /** Environment variable names, the GitHub secret names release.yml maps onto them. */
        const val ENV_STORE_FILE = "KEYSTORE_FILE"
        const val ENV_STORE_PASSWORD = "KEYSTORE_PASSWORD"
        const val ENV_KEY_ALIAS = "KEY_ALIAS"
        const val ENV_KEY_PASSWORD = "KEY_PASSWORD"

        /**
         * Resolves the signing material, or `null` when neither source is complete.
         *
         * @param propertiesFile `release.properties`; a relative `storeFile` in it is resolved against its folder.
         * @param env the environment to read when the file is absent; a relative `KEYSTORE_FILE` is resolved against [workDir].
         * @param workDir the directory a relative `KEYSTORE_FILE` is relative to (the root project directory).
         * @throws IllegalStateException when a source names a keystore that is not a file, or is only half filled in:
         *   a typo must stop the build rather than quietly ship a debug-signed release.
         */
        fun load(propertiesFile: File, env: Map<String, String>, workDir: File): Pair<ReleaseSigning, Source>? {
            if (propertiesFile.isFile) {
                val props = Properties().also { p -> propertiesFile.inputStream().use(p::load) }
                val keys = listOf(PROP_STORE_FILE, PROP_STORE_PASSWORD, PROP_KEY_ALIAS, PROP_KEY_PASSWORD)
                val values = keys.map { props.getProperty(it)?.trim().orEmpty() }
                return build(keys, values, propertiesFile.absoluteFile.parentFile, propertiesFile.name) to Source.PROPERTIES
            }
            val keys = listOf(ENV_STORE_FILE, ENV_STORE_PASSWORD, ENV_KEY_ALIAS, ENV_KEY_PASSWORD)
            val values = keys.map { env[it]?.trim().orEmpty() }
            if (values.all(String::isEmpty)) return null
            return build(keys, values, workDir, "the environment") to Source.ENVIRONMENT
        }

        /** [keys] and [values] are parallel, in the order store file, store password, key alias, key password. */
        private fun build(keys: List<String>, values: List<String>, baseDir: File, where: String): ReleaseSigning {
            val missing = keys.filterIndexed { i, _ -> values[i].isEmpty() }
            check(missing.isEmpty()) { "release signing: $where is missing ${missing.joinToString()}" }
            val (storePath, storePassword, keyAlias, keyPassword) = values
            val storeFile = File(storePath).let { if (it.isAbsolute) it else baseDir.resolve(it) }
            check(storeFile.isFile) { "release signing: $where names keystore '${storeFile.path}', which is not a file" }
            return ReleaseSigning(storeFile, storePassword, keyAlias, keyPassword)
        }
    }
}
