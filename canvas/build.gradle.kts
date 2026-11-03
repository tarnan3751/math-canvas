plugins {
    id("mathcanvas.java-conventions")
    id("mathcanvas.javafx-conventions")
    id("mathcanvas.ui-test-conventions")
}

dependencies {
    api(project(":render"))
    implementation(libs.commons.geometry.euclidean)
}

// Commons Geometry and Commons Numbers are automatic modules (their manifests name them), and
// requiring them is what this module means to do.
tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-Xlint:-requires-automatic") }

// The viewport, the painter and its SVG backend, hit testing, the object model, the sampler, the
// tools and the 3D view's camera and scene are pure Java (CLAUDE.md, **Module boundaries**): their tests need no display. Only the sources
// are checked, since the module itself resolves JavaFX; tools/gates.sh greps the same packages.
val checkJavafxFree =
    tasks.register<JavafxFreeCheck>("checkJavafxFree") {
        group = "verification"
        description = "Fails if the geom, paint, hit, model, sample, tool or space packages reference JavaFX."
        for (set in listOf("main", "test")) {
            for (pkg in listOf("geom", "paint", "hit", "model",
                "sample",
                "tool",
                // Phase 7d: the 3D view's camera, projection and scene.
                "space",
            )) {
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
