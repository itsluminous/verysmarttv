/**
 * The one `versionName` / `versionCode` pair shared by the three apps (DESIGN.md §15.4, ADR-023).
 *
 * The release workflow passes the tag without its leading `v` (`./gradlew assembleRelease
 * -PversionName=1.2.3`). [code] is `major * 10000 + minor * 100 + patch` (`1.2.3` -> 10203), so each
 * part must stay below 100. A pre-release suffix such as `-rc1` is ignored for the code: `1.0.0-rc1`
 * and `1.0.0` share 10000 and the final build is installed over the candidate with `-r`. A build
 * without the property is `0.0.0-dev` with code 1, so a dev build never outranks an installed release.
 */
data class AppVersion(
    val name: String,
    val code: Int,
    /** Whether [name] came from `-PversionName` (a release) rather than the dev default. */
    val fromProperty: Boolean,
) {
    companion object {
        const val DEV_NAME = "0.0.0-dev"

        private val semver = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-[0-9A-Za-z.]+)?$""")

        /**
         * Parses the `versionName` Gradle property. `null` or blank means a dev build.
         *
         * @throws IllegalArgumentException when the value is not `X.Y.Z` or `X.Y.Z-suffix`, or a part is 100 or more.
         */
        fun from(property: String?): AppVersion {
            val trimmed = property?.trim()?.takeIf { it.isNotEmpty() }
            if (trimmed == null) return AppVersion(DEV_NAME, 1, fromProperty = false)
            val match =
                semver.matchEntire(trimmed)
                    ?: throw IllegalArgumentException(
                        "versionName '$trimmed' must look like X.Y.Z or X.Y.Z-suffix (the release tag without its leading 'v')",
                    )
            val (major, minor, patch) = match.destructured.toList().map(String::toInt)
            require(major < 100 && minor < 100 && patch < 100) {
                "versionName '$trimmed': each part must be below 100 so versionCode stays unambiguous"
            }
            val code = major * 10000 + minor * 100 + patch
            return AppVersion(name = trimmed, code = maxOf(code, 1), fromProperty = true)
        }
    }
}
