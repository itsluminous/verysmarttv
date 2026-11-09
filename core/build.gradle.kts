// :core - the provider contracts the launcher and its companions compile against (DESIGN.md §12),
// and the small shared helpers Phase 1 brings (glass drawables, D-pad keys, byte formatting).
// Java only: the launcher pulls no Kotlin stdlib (ADR-004). The only dependency is the AndroidX
// nullability annotations, so Kotlin callers (:livescore) see real types; they cost nothing at runtime.
plugins {
    id("verysmarttv.android.lib")
}

android {
    namespace = "com.itsluminous.verysmarttv.core"
}

dependencies {
    api(libs.androidx.annotation)
    testImplementation(libs.bundles.jvm.test)
}
