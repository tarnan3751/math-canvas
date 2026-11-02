/** The coordinate plane, the canvas object model and its painting. */
module mathcanvas.canvas {
  requires transitive mathcanvas.render;
  requires org.apache.commons.geometry.core;
  requires org.apache.commons.geometry.euclidean;
  requires org.apache.commons.numbers.core;

  exports mathcanvas.canvas.geom;
  exports mathcanvas.canvas.hit;
  exports mathcanvas.canvas.model;
  exports mathcanvas.canvas.paint;
  exports mathcanvas.canvas.sample;
  exports mathcanvas.canvas.tool;
  exports mathcanvas.canvas.view;
}
