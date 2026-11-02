plugins {
    id("mathcanvas.java-conventions")
    id("mathcanvas.javafx-free")
}

dependencies { implementation(libs.hipparchus.core) }

// Hipparchus is an automatic module (named by build-logic's AutomaticModuleName transform), and
// requiring one is what this module means to do.
tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-Xlint:-requires-automatic") }

// The project version reaches the code through this resource (Version.current()).
tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("**/version.properties") { expand("version" to version) }
}
