// UI tests (TestFX) for a presentation module (canvas, app). They live in their own source set and
// run only through the uiTest task, which opens real windows: the task is not part of check or
// build, so a normal build stays headless, but check compiles the UI tests, so a compile error in
// one fails the build and the gate. They run on the classpath, like the unit tests, and see the
// main classes and everything the unit tests see. tools/ui_tests.sh runs them under a machine-wide
// lock.

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

tasks.register<Test>("uiTest") {
    description = "Runs the TestFX UI tests, which open windows on the display."
    group = "verification"
    testClassesDirs = uiTest.output.classesDirs
    classpath = uiTest.runtimeClasspath
    // On the classpath JavaFX is in the unnamed module, which loads its native libraries.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // The point of a run is the window and the screenshots, so it is never up to date and
    // never served from the build cache.
    doNotTrackState("UI tests open a window and write screenshots on every run")
}

tasks.named("check") { dependsOn(uiTest.classesTaskName) }
