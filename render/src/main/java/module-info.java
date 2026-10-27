/** Typesetting: the engine's layout tree to JavaFX nodes, and the bundled math font. */
module mathcanvas.render {
  requires transitive mathcanvas.engine;
  requires transitive javafx.graphics;

  exports mathcanvas.render;
  exports mathcanvas.render.font;
  exports mathcanvas.render.typeset;
}
