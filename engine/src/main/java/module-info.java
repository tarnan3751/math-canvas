/** The symbolic engine: pure Java, exact, and free of any JavaFX reference. */
module mathcanvas.engine {
  exports mathcanvas.engine;
  exports mathcanvas.engine.algebra;
  exports mathcanvas.engine.assume;
  exports mathcanvas.engine.builtin;
  exports mathcanvas.engine.calculus;
  exports mathcanvas.engine.compile;
  exports mathcanvas.engine.eval;
  exports mathcanvas.engine.linalg;
  exports mathcanvas.engine.expr;
  exports mathcanvas.engine.geometry;
  exports mathcanvas.engine.linalg.numeric;
  exports mathcanvas.engine.match;
  exports mathcanvas.engine.number;
  exports mathcanvas.engine.parse;
  exports mathcanvas.engine.print;
  exports mathcanvas.engine.print.layout;
  exports mathcanvas.engine.provider;
  exports mathcanvas.engine.rule;
  exports mathcanvas.engine.simplify;
  exports mathcanvas.engine.solve;

  requires hipparchus.core;
}
