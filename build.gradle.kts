// Root build script: no plugins of its own. Modules apply the convention plugins from build-logic
// (`verysmarttv.android.lib`, `verysmarttv.android.app`); this file wires the two repository-wide
// gates CI calls by name so the workflows never need individual task names (DESIGN.md §15.1).

/**
 * Every gate CI runs for a push or pull request, in one task: debug assembly, JVM unit tests and
 * Android Lint of every module, the build-logic unit tests, and the file-length gate.
 * `release.yml` runs it too before assembling signed APKs. Keep docs/BUILDING.md in step when a gate
 * is added.
 */
tasks.register("ci") {
    group = "verification"
    description = "Runs every CI gate: assembleDebug, testDebugUnitTest and lintDebug per module, build-logic tests, fileLength."
    dependsOn(subprojects.map { "${it.path}:assembleDebug" })
    dependsOn(subprojects.map { "${it.path}:testDebugUnitTest" })
    dependsOn(subprojects.map { "${it.path}:lintDebug" })
    dependsOn(gradle.includedBuild("build-logic").task(":test"))
    dependsOn("fileLength")
}

// ---------------------------------------------------------------------------------------------
// File-length gate (DESIGN.md §8 rule 3, ADR-007). The launcher is being decomposed from one
// 4,973-line activity into classes that each own their state; a file over the limit is a sign
// that a split was skipped. The gate is repository-wide so a companion cannot grow the same way.

/** Longest source file the gate accepts, in lines. */
val maxSourceLines = 1_200

/** Source extensions the gate reads: the two module languages, the convention plugins and the setup tool. */
val gatedExtensions = setOf("java", "kt", "kts", "py")

/**
 * Files the gate skips, relative to the repository root. Phase 1 carried the fork's 4,973-line activity as
 * one file on purpose (a faithful move, DESIGN.md §20 row 1); Phase 2 decomposes it into the §8 classes and
 * removes this entry. Nothing else may be added here: split the file instead.
 */
val fileLengthAllowlist = setOf("launcher/src/main/java/com/itsluminous/verysmarttv/home/HomeActivity.java")

tasks.register("fileLength") {
    group = "verification"
    description = "Fails if any source file (java, kt, kts, py) under a module's or build-logic's src/ exceeds $maxSourceLines lines."
    // Plain locals only: the configuration cache cannot serialize a lambda that captures the script object.
    val root: File = rootDir
    val limit = maxSourceLines
    val extensions = gatedExtensions.toSet()
    val allowlist = fileLengthAllowlist.toSet()
    val sources =
        fileTree(root) {
            include("*/src/**")
            include("build-logic/src/**")
            include("setup/**")
            exclude("**/build/**", "**/.venv/**", "**/.gradle/**")
        }.filter { it.isFile && it.extension in extensions && it.relativeTo(root).invariantSeparatorsPath !in allowlist }
    inputs.files(sources).withPropertyName("sources")
    doLast {
        val over =
            sources.files
                .map { file -> file to file.useLines(Charsets.UTF_8) { lines -> lines.count() } }
                .filter { (_, lines) -> lines > limit }
                .sortedByDescending { (_, lines) -> lines }
        if (over.isNotEmpty()) {
            throw GradleException(
                "fileLength: ${over.size} source file(s) exceed $limit lines; split them (DESIGN.md §8):\n" +
                    over.joinToString("\n") { (file, lines) -> "  ${file.relativeTo(root)}: $lines lines" },
            )
        }
        logger.lifecycle("fileLength: ${sources.files.size} source files, none over $limit lines (${allowlist.size} allowlisted until Phase 2)")
    }
}
