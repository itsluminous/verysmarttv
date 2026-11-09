// :traktshelf - com.itsluminous.traktshelf, the Trakt companion (DESIGN.md §5). Java; networking is
// HttpURLConnection, JSON is org.json, the periodic sync is JobScheduler: the one library is
// RecyclerView for the poster rows. It publishes HeroContract's provider for the launcher's carousel.
plugins {
    id("verysmarttv.android.app")
}

// API keys (docs/BUILDING.md, "Local API keys"): the environment first, then `secrets.env` at the repo
// root (git-ignored, KEY=value lines). Without either the app builds with empty keys and its sign-in
// screen says so; nothing crashes. The values are never printed, only the names found.
val apiKeyNames = listOf("TRAKT_CLIENT_ID", "TMDB_API_KEY", "OMDB_API_KEY")
val secretsFile = rootProject.file("secrets.env")
val secretsFromFile: Map<String, String> =
    if (secretsFile.isFile) {
        secretsFile.readLines().mapNotNull { line ->
            val m = Regex("""^([A-Z0-9_]+)=(.*)$""").matchEntire(line.trim()) ?: return@mapNotNull null
            m.groupValues[1] to m.groupValues[2].trim().removeSurrounding("\"").removeSurrounding("'")
        }.toMap()
    } else {
        emptyMap()
    }
val apiKeys = apiKeyNames.associateWith { name ->
    providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() } ?: secretsFromFile[name].orEmpty()
}
val found = apiKeys.filterValues { it.isNotEmpty() }.keys
if (found.isEmpty()) {
    logger.warn("${project.path}: no API keys in the environment or ${secretsFile.name}; building with empty keys (sign-in will say so)")
} else {
    logger.lifecycle("${project.path}: API keys from ${if (secretsFile.isFile) secretsFile.name + " / " else ""}environment: ${found.sorted().joinToString()}")
}

fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.itsluminous.traktshelf"
    defaultConfig {
        applicationId = "com.itsluminous.traktshelf"
        apiKeys.forEach { (name, value) -> buildConfigField("String", name, quoted(value)) }
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.recyclerview)
    testImplementation(libs.bundles.jvm.test)
}
