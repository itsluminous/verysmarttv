import com.android.build.api.dsl.Lint
import org.gradle.api.Project

/**
 * Android Lint settings shared by the library and the application plugins (DESIGN.md §5.1).
 *
 * Warnings are errors in every module. A module that carries code with known warnings (the
 * launcher and traktshelf in Phase 1) commits a `lint-baseline.xml` next to its build file: when
 * that file exists it freezes the current findings so only new ones fail. New modules have no
 * baseline. `config/lint/lint.xml` at the root holds the few severity overrides, each with a reason.
 */
fun Lint.applyConventions(project: Project) {
    abortOnError = true
    warningsAsErrors = true
    checkAllWarnings = true
    checkReleaseBuilds = false // lintDebug (what `ci` runs) covers the same sources
    lintConfig = project.rootProject.file("config/lint/lint.xml")
    project.file("lint-baseline.xml").takeIf { it.isFile }?.let { baseline = it }
    xmlReport = true
    htmlReport = true
    sarifReport = true
    textReport = false
}
