plugins {
    id("mathcanvas.java-conventions")
    id("mathcanvas.javafx-conventions")
    application
    alias(libs.plugins.jlink)
}

dependencies {
    implementation(project(":canvas"))
    implementation(libs.jackson.core)
}

// UI tests (TestFX) live in their own source set and run only through the uiTest task, which
// opens real windows: the task is not part of check or build, so a normal build stays headless,
// but check compiles the UI tests, so a compile error in one fails the build and the gate. They
// run on the classpath, like the unit tests, and see the main classes and everything the unit
// tests see. tools/ui_tests.sh runs them under a machine-wide lock.
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
    "uiTestImplementation"(libs.testfx.core)
    "uiTestImplementation"(libs.testfx.junit5)
}

// The data directory the UI tests read from mathcanvas.dataDir, each test starting the app on a
// directory of its own under it; tools/ui_tests.sh passes a scratch one with -PuiTestDataDir=<dir>.
val uiTestDataDir =
    providers
        .gradleProperty("uiTestDataDir")
        .orElse(layout.buildDirectory.dir("ui-test-data").map { it.asFile.absolutePath })

tasks.register<Test>("uiTest") {
    description = "Runs the TestFX UI tests, which open windows on the display."
    group = "verification"
    testClassesDirs = uiTest.output.classesDirs
    classpath = uiTest.runtimeClasspath
    // On the classpath JavaFX is in the unnamed module, which loads its native libraries.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("mathcanvas.dataDir", uiTestDataDir.get())
    // The point of a run is the window and the screenshots, so it is never up to date and
    // never served from the build cache.
    doNotTrackState("UI tests open a window and write screenshots on every run")
}

tasks.named("check") { dependsOn(uiTest.classesTaskName) }

application {
    mainModule = "mathcanvas.app"
    mainClass = "mathcanvas.app.Main"
    // JavaFX loads its native libraries through System.load, a restricted method in JDK 25.
    applicationDefaultJvmArgs = listOf("--enable-native-access=javafx.graphics")
}

// Package version rule (jpackage, JDK 25): macOS rejects an app version whose first component
// is 0, and Windows needs two to four numeric components with major and minor at most 255. So
// the package version is the project version with any -qualifier stripped (0.1.0-SNAPSHOT ->
// 0.1.0), and on macOS only, while the major is 0, the bundle version is 1.<minor>.<patch>.
// The real version is always in the window title.
val osName = providers.systemProperty("os.name").get().lowercase()
val releaseVersion = project.version.toString().substringBefore('-')
val versionParts = releaseVersion.split('.')
check(versionParts.size == 3 && versionParts.all { part -> part.toIntOrNull() != null }) {
    "version must be major.minor.patch[-qualifier], got ${project.version}"
}
val (major, minor, patch) = versionParts
val onMac = osName.startsWith("mac")
val packageVersion = if (onMac && major == "0") "1.$minor.$patch" else releaseVersion

// The licence, the notice and the licence texts it names ship beside the application through
// jpackage's --app-content, which copies each path under its own name into the image's content
// directory: lib/ on Linux, the installation directory on Windows, Contents/ in the macOS bundle.
// codesign, which jpackage runs even unsigned (ad hoc), rejects files directly in Contents/, so on
// macOS they are staged in a directory named Resources, which merges into Contents/Resources. So
// an installed copy holds LICENSE, NOTICE and legal/ side by side, as NOTICE describes.
val legalFiles = listOf("LICENSE", "NOTICE", "legal").map { rootProject.file(it) }
val macAppContent = layout.buildDirectory.dir("app-content/Resources")
val stageAppContent =
    tasks.register<Sync>("stageAppContent") {
        from(rootProject.file("LICENSE"), rootProject.file("NOTICE"))
        from(rootProject.file("legal")) { into("legal") }
        into(macAppContent)
    }
val appContent =
    if (onMac) listOf(macAppContent.get().asFile) else legalFiles

jlink {
    options = listOf("--strip-debug", "--no-header-files", "--no-man-pages", "--compress", "zip-6")
    launcher {
        name = "math-canvas"
        jvmArgs = listOf("--enable-native-access=javafx.graphics")
    }
    jpackage {
        imageName = "math-canvas"
        installerName = "math-canvas"
        appVersion = packageVersion
        vendor = "Tahseen Arnan"
        installerType =
            when {
                onMac -> "dmg"
                osName.startsWith("windows") -> "msi"
                else -> "deb"
            }
        val contentOption =
            listOf("--app-content", appContent.joinToString(",") { it.absolutePath })
        imageOptions =
            if (onMac) {
                contentOption +
                    listOf("--mac-package-identifier", "io.github.tarnan3751.mathcanvas")
            } else {
                contentOption
            }
    }
}

// The image is rebuilt when a licence text changes.
tasks.named("jpackageImage") {
    inputs.files(legalFiles).withPropertyName("legalFiles")
    if (onMac) {
        dependsOn(stageAppContent)
    }
}
