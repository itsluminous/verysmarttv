/**
 * The one `versionName` / `versionCode` pair shared by the three apps (DESIGN.md §15.4, ADR-023, ADR-047).
 *
 * The release workflow passes the tag without its leading `v` (`./gradlew assembleRelease
 * -PversionName=0.1.0-rc1`). [code] is [EPOCH]` + major * 10000 + minor * 100 + patch` (`0.1.0` -> 1000100,
 * `1.2.3` -> 1010203), so each part must stay below 100. The epoch is there because the first three
 * candidates were published as `1.0.0-rc1..rc3` with code 10000 before the owner renumbered the first
 * release to 0.1.0 (ADR-047): every code from here on is above 10000, so a TV with one of those candidates
 * installed upgrades to `0.1.0-rc1` and everything after it, and the order of versions stays the order
 * of codes within the epoch. A pre-release suffix such as `-rc1` is ignored for the code: `0.1.0-rc1` and
 * `0.1.0` share 1000100 and the final build is installed over the candidate with `-r`. A build without the
 * property is `0.0.0-dev` with code 1, so a dev build never outranks an installed release.
 */
data class AppVersion(
    val name: String,
    val code: Int,
    /** Whether [name] came from `-PversionName` (a release) rather than the dev default. */
    val fromProperty: Boolean,
) {
    companion object {
        const val DEV_NAME = "0.0.0-dev"

        /** Added to every release code, so the renumbered 0.x line outranks the 1.0.0-rc1..rc3 codes (10000) already on TVs. */
        const val EPOCH = 1_000_000

        /** The highest code published before the epoch (1.0.0-rc3); every release code must exceed it. */
        const val LAST_PRE_EPOCH_CODE = 10_000

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
            val code = EPOCH + major * 10000 + minor * 100 + patch
            return AppVersion(name = trimmed, code = code, fromProperty = true)
        }
    }
}
