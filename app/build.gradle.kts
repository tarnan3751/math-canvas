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

// The Help window's entries, generated from the language reference (build-logic's GenerateHelp) into
// a resource directory of their own, so the reference is the help's one source.
val generateHelp =
    tasks.register<GenerateHelp>("generateHelp") {
        description = "Generates help.json from docs/language.md."
        language = rootProject.layout.projectDirectory.file("docs/language.md")
        resource = "mathcanvas/app/help.json"
        outputDirectory = layout.buildDirectory.dir("generated/help")
    }

sourceSets.main { resources.srcDir(generateHelp) }

// The repository whose latest GitHub Release the opt-in update check reads (UpdateCheck).
tasks.processResources {
    val repository = "tarnan3751/math-canvas"
    inputs.property("repository", repository)
    filesMatching("**/app.properties") { expand("repository" to repository) }
}

// The UI tests (mathcanvas.ui-test-conventions) read their data directory from
// mathcanvas.dataDir, each test starting the app on a directory of its own under it;
// tools/ui_tests.sh passes a scratch one with -PuiTestDataDir=<dir>.
val uiTestDataDir =
    providers
        .gradleProperty("uiTestDataDir")
        .orElse(layout.buildDirectory.dir("ui-test-data").map { it.asFile.absolutePath })

tasks.named<Test>("uiTest") { systemProperty("mathcanvas.dataDir", uiTestDataDir.get()) }

// The language setting's default is the system's (Language.SYSTEM), so the tests that read the
// bundles pin the JVM's locale to English and do not depend on the machine's.
tasks.withType<Test>().configureEach { jvmArgs("-Duser.language=en", "-Duser.country=US") }

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

// The installers' metadata beyond the name, version and vendor: a one-line description, the
// category (macOS: public.app-category.education in the bundle's Info.plist; Linux: the .deb's
// Section; Windows has none), and the licence file.
val appDescription =
    "A math workspace: exact symbolic computation, graphing and interactive geometry"
val licenseFile = rootProject.file("LICENSE")

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
        val descriptionOption = listOf("--description", appDescription)
        imageOptions =
            if (onMac) {
                contentOption +
                    descriptionOption +
                    listOf(
                        "--mac-package-identifier",
                        "io.github.tarnan3751.mathcanvas",
                        "--mac-app-category",
                        "education",
                    )
            } else {
                contentOption + descriptionOption
            }
        // The installer shows the licence before it installs (the .dmg and the .msi) or ships it
        // as the package's copyright file (the .deb, in the Debian section math). The .msi adds a
        // Start menu entry in a math-canvas folder and a desktop shortcut, the .deb a desktop entry
        // in the menus' Education, Science and Math categories.
        installerOptions =
            descriptionOption +
                listOf("--license-file", licenseFile.absolutePath) +
                when {
                    osName.startsWith("windows") ->
                        listOf("--win-menu", "--win-menu-group", "math-canvas", "--win-shortcut")
                    osName.startsWith("linux") ->
                        listOf(
                            "--linux-app-category",
                            "math",
                            "--linux-shortcut",
                            "--linux-menu-group",
                            "Education;Science;Math",
                        )
                    else -> listOf()
                }
    }
}

// The image is rebuilt when a licence text changes, and the installer when the licence does.
tasks.named("jpackageImage") {
    inputs.files(legalFiles).withPropertyName("legalFiles")
    if (onMac) {
        dependsOn(stageAppContent)
    }
}

tasks.named("jpackage") { inputs.file(licenseFile).withPropertyName("licenseFile") }
