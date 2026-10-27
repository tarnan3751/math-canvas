plugins {
    id("mathcanvas.java-conventions")
    id("mathcanvas.javafx-conventions")
}

dependencies { api(project(":engine")) }

// The font reader and the typesetter are pure Java (CLAUDE.md, **Module boundaries**): their
// tests need no display, and the canvas's Painter will read the box tree. Only the sources are
// checked, since the module itself resolves JavaFX; tools/gates.sh greps the same packages.
val checkJavafxFree =
    tasks.register<JavafxFreeCheck>("checkJavafxFree") {
        group = "verification"
        description = "Fails if the font or typeset packages reference JavaFX."
        for (set in listOf("main", "test")) {
            for (pkg in listOf("font", "typeset")) {
                sources.from(
                    fileTree("src/$set/java/mathcanvas/render/$pkg") { include("**/*.java") }
                )
            }
        }
        resolvedModules = emptyMap()
        projectDirectory = layout.projectDirectory
        result = layout.buildDirectory.file("javafx-free/result.txt")
    }

tasks.named("check") { dependsOn(checkJavafxFree) }
