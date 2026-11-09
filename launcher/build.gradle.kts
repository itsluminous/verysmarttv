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

// The connected suite needs two test apps on the emulator first (DESIGN.md §14.3): the companion stand-in
// (CarouselFocusTest, ADR-037), which takes the companion's package, so the real traktshelf debug build is replaced
// until `./gradlew :traktshelf:installDebug` puts it back (its data stays); and Tile 7, the seventh TV app the focus
// tests switch on and off. Tiles 1 to 6 ride in the launcher's own test APK, which Gradle installs anyway.
tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach {
    dependsOn(":testkit:hero:installDebug", ":testkit:tiles:installDebug")
}
