plugins {
    id("mathcanvas.java-conventions")
    id("mathcanvas.javafx-free")
}

dependencies {
    implementation(libs.hipparchus.core)
    implementation(libs.hipparchus.ode)
}

// The project version reaches the code through this resource (Version.current()).
tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("**/version.properties") { expand("version" to version) }
}
