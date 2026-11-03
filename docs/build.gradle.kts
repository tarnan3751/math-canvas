// The documentation site generator (mathcanvas.docs.SiteGenerator): `site` writes the site from
// README.md, docs/language.md, docs/reference/ and docs/images/, with the UI tests' screenshots
// from app/build/ui-screenshots/ when a UI test run has left them, into build/site/ at the root.
// The tests build it from the same sources and check every page parses and every internal link
// and anchor resolves, so ./gradlew build checks the site without writing it.

plugins { id("mathcanvas.java-conventions") }

dependencies {
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.heading.anchor)
}

val repository = rootProject.layout.projectDirectory
val sources =
    files(
        repository.file("README.md"),
        layout.projectDirectory.file("language.md"),
        layout.projectDirectory.dir("reference"),
        layout.projectDirectory.dir("images"),
    )
val screenshots = repository.dir("app/build/ui-screenshots")
val siteDir = rootProject.layout.buildDirectory.dir("site")

tasks.register<JavaExec>("site") {
    description = "Builds the documentation site into build/site."
    group = "documentation"
    // The jar, not the classes and resources directories: on the module path a resources
    // directory would not belong to the module, so its stylesheet and script would not be found.
    classpath = files(tasks.jar) + configurations.runtimeClasspath.get()
    mainModule = "mathcanvas.docs"
    mainClass = "mathcanvas.docs.SiteGenerator"
    inputs.files(sources).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("sources")
    inputs
        .files(fileTree(screenshots) { include("*.png") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("screenshots")
    inputs.property("version", project.version.toString())
    outputs.dir(siteDir)
    args(
        "--root",
        repository.asFile.absolutePath,
        "--out",
        siteDir.get().asFile.absolutePath,
        "--version",
        project.version.toString(),
        "--screenshots",
        screenshots.asFile.absolutePath,
    )
}

tasks.named<Test>("test") {
    inputs.files(sources).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("sources")
    systemProperty("mathcanvas.repository", repository.asFile.absolutePath)
}
