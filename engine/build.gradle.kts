plugins {
    id("mathcanvas.java-conventions")
    id("mathcanvas.javafx-free")
}

// The project version reaches the code through this resource (Version.current()).
tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("**/version.properties") { expand("version" to version) }
}
