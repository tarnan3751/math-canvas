plugins {
    id("mathcanvas.java-conventions")
    id("mathcanvas.javafx-conventions")
    application
    alias(libs.plugins.jlink)
}

dependencies { implementation(project(":canvas")) }

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
        if (onMac) {
            imageOptions = listOf("--mac-package-identifier", "io.github.tarnan3751.mathcanvas")
        }
    }
}
