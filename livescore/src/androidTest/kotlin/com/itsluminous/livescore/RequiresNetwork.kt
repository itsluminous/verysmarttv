package com.itsluminous.livescore

/**
 * Marks an instrumentation test that reaches the public internet (BWF's site, say). The default connected run leaves
 * these out through the runner's `notAnnotation` filter, so CI never waits on a host it may not reach (ADR-052);
 * `./gradlew :livescore:connectedDebugAndroidTest -PincludeNetworkTests` runs them (docs/BUILDING.md).
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresNetwork
