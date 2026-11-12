// :testkit:hero - the stand-in for the Trakt companion on a test emulator (DESIGN.md §14.3, ADR-037).
// It installs AS com.itsluminous.traktshelf: the launcher's carousel opens a title through the fixed
// component HeroContract.DETAILS_ACTIVITY and reads the fixed authority HeroContract.AUTHORITY, and
// only one installed app may own either, so a fake under another id could neither answer the provider
// beside the real companion nor receive the intent. Installing it replaces the companion's debug build
// (same debug key, so `adb install -r` keeps the companion's data); `./gradlew :traktshelf:installDebug`
// puts the real one back. Debug only: never released, never signed with the release key.
plugins {
    id("verysmarttv.android.app")
}

android {
    namespace = "com.itsluminous.verysmarttv.testkit.hero"
    defaultConfig {
        applicationId = "com.itsluminous.traktshelf"
    }
}

dependencies {
    implementation(project(":testkit"))
}

// In a whole-project connected run this install must wait for the real companion's own suite: that suite installs
// and, at its end, uninstalls the same package (ADR-052). On its own (`:launcher:connectedDebugAndroidTest`) nothing waits.
tasks.matching { it.name == "installDebug" }.configureEach {
    mustRunAfter(":traktshelf:connectedDebugAndroidTest")
}
