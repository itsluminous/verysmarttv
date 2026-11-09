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
    androidTestImplementation(libs.bundles.android.test)
}
