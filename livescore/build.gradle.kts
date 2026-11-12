// :livescore - com.itsluminous.livescore, the live-score companion (DESIGN.md §13). Kotlin, compiled by
// AGP's built-in Kotlin support (no KGP, §5.1), which adds the matching kotlin-stdlib itself; stdlib
// only: HttpURLConnection, android.util.JsonReader streaming parsers, one Executor and a Handler
// (ADR-005). No other library: every list on its screens is under 40 rows, so plain LinearLayouts in
// a ScrollView carry them. It publishes ScoreContract's provider for the launcher's score pill (§12.2).
plugins {
    id("verysmarttv.android.app")
}

android {
    namespace = "com.itsluminous.livescore"
    defaultConfig {
        applicationId = "com.itsluminous.livescore"
        // Tests marked @RequiresNetwork (BwfRecordTest: the real BWF site through the hidden WebView) are left out of
        // a connected run unless `-PincludeNetworkTests` is passed; CI never passes it (ADR-052, docs/BUILDING.md).
        if (!providers.gradleProperty("includeNetworkTests").isPresent) {
            testInstrumentationRunnerArguments["notAnnotation"] = "com.itsluminous.livescore.RequiresNetwork"
        }
    }
}

dependencies {
    implementation(project(":core"))
    testImplementation(libs.bundles.jvm.test)
    androidTestImplementation(libs.bundles.android.test)
}
