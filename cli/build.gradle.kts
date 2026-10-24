plugins {
    id("mathcanvas.java-conventions")
    id("mathcanvas.javafx-free")
    application
}

dependencies { implementation(project(":engine")) }

application {
    mainModule = "mathcanvas.cli"
    mainClass = "mathcanvas.cli.Main"
    applicationName = "mathcanvas-cli"
}

// The REPL reads standard input, which Gradle does not pass to `run` by default.
tasks.named<JavaExec>("run") { standardInput = System.`in` }

// CorpusTest checks every golden corpus file in-process; the files are an input of the tests.
val corpus = rootProject.layout.projectDirectory.dir("engine/src/test/corpus")

tasks.named<Test>("test") {
    inputs.dir(corpus).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("mathcanvas.corpus", corpus.asFile.absolutePath)
}
