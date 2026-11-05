plugins {
    id("mathcanvas.java-conventions")
    id("mathcanvas.javafx-conventions")
    id("mathcanvas.javafx-free")
}

dependencies { api(project(":engine")) }

// The font reader and the typesetter are pure Java (CLAUDE.md, **Module boundaries**): their
// tests need no display, and the canvas's Painter reads the box tree.
javafxFree { packages = listOf("mathcanvas.render.font", "mathcanvas.render.typeset") }
