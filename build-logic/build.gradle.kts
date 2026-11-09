// Convention plugins (DESIGN.md §5): `verysmarttv.android.lib` for :core and :testkit,
// `verysmarttv.android.app` for the three apps. Precompiled Kotlin DSL scripts under
// src/main/kotlin, with the pure helpers (AppVersion, ReleaseSigning) next to them so they can be
// unit-tested here without an Android build.
plugins {
    `kotlin-dsl`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(libs.android.gradlePlugin)
    testImplementation(libs.junit4)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
