plugins {
    id("mathcanvas.java-conventions")
    id("mathcanvas.javafx-conventions")
    id("mathcanvas.ui-test-conventions")
}

dependencies { api(project(":render")) }

// The viewport, the painter and its SVG backend, hit testing and the object model are pure Java
// (CLAUDE.md, **Module boundaries**): their tests need no display. Only the sources are checked,
// since the module itself resolves JavaFX; tools/gates.sh greps the same packages.
val checkJavafxFree =
    tasks.register<JavafxFreeCheck>("checkJavafxFree") {
        group = "verification"
        description = "Fails if the geom, paint, hit or model packages reference JavaFX."
        for (set in listOf("main", "test")) {
            for (pkg in listOf("geom", "paint", "hit", "model")) {
                sources.from(
                    fileTree("src/$set/java/mathcanvas/canvas/$pkg") { include("**/*.java") }
                )
            }
        }
        resolvedModules = emptyMap()
        projectDirectory = layout.projectDirectory
        result = layout.buildDirectory.file("javafx-free/result.txt")
    }

tasks.named("check") { dependsOn(checkJavafxFree) }
