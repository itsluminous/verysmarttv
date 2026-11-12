// :launcher - com.itsluminous.verysmarttv, the home screen (DESIGN.md §5). Plain Java, no INTERNET
// permission; it reads the companions through :core's provider contracts. Everything the three apps
// share (SDK levels, version, signing, R8, lint) comes from the convention plugin.
plugins {
    id("verysmarttv.android.app")
}

android {
    namespace = "com.itsluminous.verysmarttv"
    defaultConfig {
        applicationId = "com.itsluminous.verysmarttv"
        // Runs after the last test of a connected run: enables the emulator's stock launcher again if HomeSmokeTest
        // disabled it for the run (support/StockLauncher, ADR-052).
        testInstrumentationRunnerArguments["listener"] = "com.itsluminous.verysmarttv.support.RunEndListener"
    }
    packaging {
        // Kotlin metadata pulled in by AndroidX's dependencies. The launcher is plain Java and never reads it;
        // leaving it out saves about 14 KB of every install (carried from the fork's build).
        resources.excludes += setOf("kotlin/**", "DebugProbesKt.bin")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.dynamicanimation)
    testImplementation(libs.bundles.jvm.test)
    testImplementation(project(":testkit"))          // FakeScoreProvider: the pill's reader against a scripted /pill row
    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(project(":testkit"))   // HeroFixture: the titles the companion stand-in publishes
}

// The connected suite needs three test apps on the emulator first (DESIGN.md §14.3): the two companion stand-ins
// (CarouselFocusTest, HomeFocusTest; ADR-037), which take the companions' packages, so the real traktshelf and
// livescore debug builds are replaced until `./gradlew :traktshelf:installDebug :livescore:installDebug` puts them
// back (their data stays); and Tile 7, the seventh TV app the focus tests switch on and off. Tiles 1 to 6 ride in the
// launcher's own test APK, which Gradle installs anyway.
//
// One emulator, one suite at a time (ADR-052). `org.gradle.parallel` would otherwise start this suite while a
// companion's own connected suite is still running: the two install the same package (the stand-in and the real
// companion are both com.itsluminous.livescore), each install kills the other's instrumentation, and the companion's
// run uninstalls the package at its end, so this suite loses its provider half way through. The stand-in installs
// are ordered after the companions' suites too (testkit/score, testkit/hero). `mustRunAfter` orders only when both
// tasks are in the same invocation: `./gradlew :launcher:connectedDebugAndroidTest` on its own is unchanged.
tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach {
    dependsOn(":testkit:hero:installDebug", ":testkit:score:installDebug", ":testkit:tiles:installDebug")
    mustRunAfter(":livescore:connectedDebugAndroidTest", ":traktshelf:connectedDebugAndroidTest")
}
