plugins {
    id("mathcanvas.java-conventions")
    id("mathcanvas.javafx-free")
    application
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.jackson.core)
}

// help(name) prints the entry generated from the language reference, as the app's Help window
// shows it (build-logic's GenerateHelp).
val generateHelp =
    tasks.register<GenerateHelp>("generateHelp") {
        description = "Generates help.json from docs/language.md."
        language = rootProject.layout.projectDirectory.file("docs/language.md")
        resource = "mathcanvas/cli/help.json"
        outputDirectory = layout.buildDirectory.dir("generated/help")
    }

sourceSets.main { resources.srcDir(generateHelp) }

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

// The JDK tools/run_corpus.sh starts the installed CLI with: the toolchain's, whatever JDK is on
// the PATH, written to build/toolchain/java-home whenever installDist runs.
val toolchainHome =
    javaToolchains.launcherFor(java.toolchain).map {
        it.metadata.installationPath.asFile.absolutePath
    }

val writeJavaHome =
    tasks.register("writeJavaHome") {
        description = "Writes the toolchain's Java home for tools/run_corpus.sh."
        val home = toolchainHome
        val out = layout.buildDirectory.file("toolchain/java-home")
        inputs.property("home", home)
        outputs.file(out)
        doLast { out.get().asFile.writeText(home.get() + "\n") }
    }

tasks.named("installDist") { dependsOn(writeJavaHome) }
