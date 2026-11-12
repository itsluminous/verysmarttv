/**
 * SDK levels shared by every module (DESIGN.md §5.1, ADR-003). One place, so the three apps and the
 * two libraries can never drift: a provider contract compiled against one API and read against
 * another is the bug this avoids.
 */
object AndroidDefaults {
    /** Installed locally and in CI; the AndroidX versions the launcher needs compile against it. */
    const val COMPILE_SDK = 36

    /**
     * Behaviour parity with the carried launcher. 35 brings edge-to-edge and tighter service and
     * intent rules that a launcher with accessibility and notification-listener services has not
     * been tested against; re-evaluated on a real Android 14 TV in Phase 6.
     */
    const val TARGET_SDK = 34

    /** Android 8.0: the oldest TVs the debloat presets cover. */
    const val MIN_SDK = 26

    /** The runner every module's instrumentation tests use. */
    const val TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner"

    /** The runner fails any one instrumentation test still running after this (`timeout_msec`, ADR-052). */
    const val TEST_TIMEOUT_MS = 180_000L
}
