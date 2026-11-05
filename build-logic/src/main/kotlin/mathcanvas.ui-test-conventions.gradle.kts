// UI tests (TestFX) for a presentation module (canvas, app). They live in their own source set and
// run only through the uiTest task, which opens real windows: the task is not part of check or
// build, so a normal build stays headless, but check compiles the UI tests, so a compile error in
// one fails the build and the gate. They run on the classpath, like the unit tests, and see the
// main classes and everything the unit tests see. Within one build the modules' uiTest tasks take
// the display one at a time (UiDisplay); tools/ui_tests.sh runs them under a machine-wide lock and
// passes its --tests pattern as -PuiTestFilter. The classes tagged benchmark run only with
// -Pbenchmarks. Screenshots go to the module's build/ui-screenshots/.

plugins { java }

val libs = versionCatalogs.named("libs")

val uiTest =
    sourceSets.create("uiTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }

configurations.named(uiTest.implementationConfigurationName) {
    extendsFrom(configurations.testImplementation.get())
}

configurations.named(uiTest.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
    "uiTestImplementation"(libs.findLibrary("testfx-core").get())
    "uiTestImplementation"(libs.findLibrary("testfx-junit5").get())
}

val uiTestFilter = providers.gradleProperty("uiTestFilter")
val benchmarks = providers.gradleProperty("benchmarks")

val uiDisplay =
    gradle.sharedServices.registerIfAbsent("uiDisplay", UiDisplay::class) {
        maxParallelUsages = 1
    }

tasks.register<Test>("uiTest") {
    description = "Runs the TestFX UI tests, which open windows on the display."
    group = "verification"
    testClassesDirs = uiTest.output.classesDirs
    classpath = uiTest.runtimeClasspath
    // On the classpath JavaFX is in the unnamed module, which loads its native libraries.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    usesService(uiDisplay)
    // The classes tagged benchmark (app's startup and cell latency) time rather than test, so an
    // ordinary run leaves them out; -Pbenchmarks, which tools/measure.sh passes, runs them.
    useJUnitPlatform { if (!benchmarks.isPresent) excludeTags("benchmark") }
    // One filter for every module's uiTest: a module with no matching test runs none.
    if (uiTestFilter.isPresent) {
        filter {
            includeTestsMatching(uiTestFilter.get())
            isFailOnNoMatchingTests = false
        }
    }
    // The point of a run is the window and the screenshots, so it is never up to date and
    // never served from the build cache.
    doNotTrackState("UI tests open a window and write screenshots on every run")
}

tasks.named("check") { dependsOn(uiTest.classesTaskName) }
