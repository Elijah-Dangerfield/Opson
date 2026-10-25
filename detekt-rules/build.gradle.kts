plugins {
    alias(libs.plugins.kotlinJvm)
}

// Custom detekt rules live in their own JVM module: detekt discovers them via
// the ServiceLoader on the `detektPlugins` classpath, so the rules must be a
// standalone jar — they can't live inside `build-logic` (an included build) or
// a multiplatform module. Wired into the build by the root build.gradle.kts
// detekt block.
//
// No `detekt-test` here: dev.detekt:detekt-test declares a runtime dependency on
// a `detekt-api` test-fixtures jar that isn't published to Maven Central for any
// 2.0.0 alpha through alpha.6 (404), so it can't resolve. `RuleHarness` in the
// test source set stands in for its `lint()` helper — it parses a snippet into a
// KtFile and calls `Rule.visitFile`, which is all detekt-test's helper does for a
// rule that needs no type resolution. Swap it out once an alpha publishes its
// fixtures.
//
// Pin detekt to alpha.6 or later. On 2.0.0-alpha.5 a custom rule can silently
// fail to dispatch: the build passes, detekt reports success, and the rule never
// runs — so a clean run is indistinguishable from a broken rule. If a new rule
// appears to do nothing, suspect the detekt version before the rule.
//
// The other way a new or edited rule silently does nothing: **the Gradle daemon
// caches the ruleset ClassLoader by classpath path, not by jar contents.** Change
// a rule class in this module and the daemon keeps serving the classloader it
// built from the previous jar at the same path, so detekt keeps running the code
// you just replaced. The build passes, `--rerun-tasks` doesn't help, and detekt
// reports the old findings. `./gradlew --stop` first, then run.
//
// Either way, don't trust a clean run to mean the rule works. Prove dispatch by
// making the rule report unconditionally, confirm the flood, then revert.
kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.detekt.api)

    // Not compileOnly for tests: the harness needs detekt-api and the Kotlin
    // compiler PSI it pulls in on the runtime classpath to parse a snippet.
    testImplementation(libs.detekt.api)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
}
