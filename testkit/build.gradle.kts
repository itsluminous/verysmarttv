// :testkit - fakes shared by every module's tests (DESIGN.md §14.1): in-process stand-ins for the
// two companion providers, so the launcher's Robolectric and Espresso tests run without traktshelf
// or livescore installed. Consumed as testImplementation / androidTestImplementation only; a
// consumer registers the providers itself (Robolectric.setupContentProvider, or a <provider> in its
// androidTest manifest), so this library declares none.
plugins {
    id("verysmarttv.android.lib")
}

android {
    namespace = "com.itsluminous.verysmarttv.testkit"
}

dependencies {
    api(project(":core"))
    testImplementation(libs.bundles.jvm.test)
}
