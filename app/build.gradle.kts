plugins {
    id("mathcanvas.java-conventions")
    id("mathcanvas.javafx-conventions")
    id("mathcanvas.ui-test-conventions")
    application
    alias(libs.plugins.jlink)
}

dependencies {
    implementation(project(":canvas"))
    implementation(libs.jackson.core)
}

// The UI tests (mathcanvas.ui-test-conventions) read their data directory from
// mathcanvas.dataDir, each test starting the app on a directory of its own under it;
// tools/ui_tests.sh passes a scratch one with -PuiTestDataDir=<dir>.
val uiTestDataDir =
    providers
        .gradleProperty("uiTestDataDir")
        .orElse(layout.buildDirectory.dir("ui-test-data").map { it.asFile.absolutePath })

tasks.named<Test>("uiTest") { systemProperty("mathcanvas.dataDir", uiTestDataDir.get()) }

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
