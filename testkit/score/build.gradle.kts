// :testkit:score - the stand-in for the live-score companion on a test emulator (DESIGN.md §14.3, ADR-037).
// It installs AS com.itsluminous.livescore: the launcher's pill reads the fixed authority ScoreContract.AUTHORITY
// and opens the fixed component ScoreContract.SCOREBOARD_ACTIVITY, and only one installed app may own either, so a
// fake under another id could neither answer the provider beside the real companion nor receive the intent.
// Installing it replaces the companion's debug build (same debug key, so `adb install -r` keeps the companion's
// data); `./gradlew :livescore:installDebug` puts the real one back. Debug only: never released.
plugins {
    id("verysmarttv.android.app")
}

android {
    namespace = "com.itsluminous.verysmarttv.testkit.score"
    defaultConfig {
        applicationId = "com.itsluminous.livescore"
    }
}

dependencies {
    implementation(project(":testkit"))
}
