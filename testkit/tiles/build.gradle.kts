// :testkit:tiles - the seventh TV app on a test emulator (DESIGN.md §14.3). The launcher's instrumentation APK
// carries six leanback tiles of its own (Tile 1 to Tile 6); this package is Tile 7, and `pm enable` / `pm
// disable-user` on it is how a test has six or seven apps installed and enabled. A package of its own because
// Android 14's shell may only switch whole packages, not one alias of the test APK. Debug only, never released.
plugins {
    id("verysmarttv.android.app")
}

android {
    namespace = "com.itsluminous.verysmarttv.testkit.tiles"
    defaultConfig {
        applicationId = "com.itsluminous.verysmarttv.testkit.tiles"
    }
}

dependencies {
    implementation(libs.androidx.annotation)   // @Nullable on the one override, as lint asks of every module
}
