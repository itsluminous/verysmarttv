// Convention plugin for the library modules (:core, :testkit): `plugins { id("verysmarttv.android.lib") }`.
// SDK levels and Java version come from AndroidDefaults so they match the apps; Robolectric needs
// the Android resources on the unit-test classpath; lint is the shared hard gate.
plugins {
    id("com.android.library")
}

android {
    compileSdk = AndroidDefaults.COMPILE_SDK

    defaultConfig {
        minSdk = AndroidDefaults.MIN_SDK
        testInstrumentationRunner = AndroidDefaults.TEST_RUNNER
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        targetSdk = AndroidDefaults.TARGET_SDK
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = false
        }
    }

    lint {
        applyConventions(project)
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
