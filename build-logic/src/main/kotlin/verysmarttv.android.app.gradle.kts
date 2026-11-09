// Convention plugin for the application modules (:launcher, :traktshelf, :livescore):
// `plugins { id("verysmarttv.android.app") }`, then the module sets `android.namespace` and
// `android.defaultConfig.applicationId`. Everything the three apps must share lives here:
// SDK levels, Java 17, one version from `-PversionName`, one release key, R8 with resource
// shrinking, the lint gate, and the APK file name the release workflow collects.
plugins {
    id("com.android.application")
}

val appVersion = AppVersion.from(providers.gradleProperty("versionName").orNull)

val signingEnv =
    listOf(
        ReleaseSigning.ENV_STORE_FILE,
        ReleaseSigning.ENV_STORE_PASSWORD,
        ReleaseSigning.ENV_KEY_ALIAS,
        ReleaseSigning.ENV_KEY_PASSWORD,
    ).associateWith { providers.environmentVariable(it).orNull.orEmpty() }

val releaseSigning = ReleaseSigning.load(rootProject.file("release.properties"), signingEnv, rootDir)

if (releaseSigning == null) {
    logger.warn(
        "${project.path}: no release.properties and no KEYSTORE_* environment: release builds are DEBUG-SIGNED " +
            "(fine for local testing; release.yml refuses to publish them). See docs/BUILDING.md, Signing.",
    )
} else {
    logger.lifecycle("${project.path}: release signing from ${releaseSigning.second.name.lowercase()}")
}

android {
    compileSdk = AndroidDefaults.COMPILE_SDK

    defaultConfig {
        minSdk = AndroidDefaults.MIN_SDK
        targetSdk = AndroidDefaults.TARGET_SDK
        versionCode = appVersion.code
        versionName = appVersion.name
        testInstrumentationRunner = AndroidDefaults.TEST_RUNNER
    }

    signingConfigs {
        releaseSigning?.first?.let { signing ->
            create("release") {
                storeFile = signing.storeFile
                storePassword = signing.storePassword
                keyAlias = signing.keyAlias
                keyPassword = signing.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (releaseSigning != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = false
        }
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/LICENSE*", "META-INF/*.version")
    }

    lint {
        applyConventions(project)
    }
}

// One universal APK per build type, no ABI splits (no native code). The release APK is named
// `verysmarttv-<module>-<version>.apk` (`verysmarttv-launcher-1.2.3.apk`): the file name release.yml
// collects and the GitHub release carries (DESIGN.md §15.2).
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { it.outputFileName.set("verysmarttv-${project.name}-${appVersion.name}.apk") }
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
