plugins {
    id("mathcanvas.java-conventions")
    id("mathcanvas.javafx-conventions")
    id("mathcanvas.javafx-free")
    id("mathcanvas.ui-test-conventions")
}

dependencies {
    api(project(":render"))
    implementation(libs.commons.geometry.euclidean)
}

// The viewport, the painter and its SVG backend, hit testing, the object model, the sampler, the
// tools and the 3D view's camera and scene are pure Java (CLAUDE.md, **Module boundaries**): their
// tests need no display.
javafxFree {
    packages =
        listOf("geom", "paint", "hit", "model", "sample", "tool", "space").map {
            "mathcanvas.canvas.$it"
        }
}
