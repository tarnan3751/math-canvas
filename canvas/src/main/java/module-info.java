/** The coordinate plane, the canvas object model and its painting. */
module mathcanvas.canvas {
  requires transitive mathcanvas.render;

  exports mathcanvas.canvas.geom;
  exports mathcanvas.canvas.hit;
  exports mathcanvas.canvas.paint;
  exports mathcanvas.canvas.view;
}
