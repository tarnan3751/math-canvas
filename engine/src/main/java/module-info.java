/** The symbolic engine: pure Java, exact, and free of any JavaFX reference. */
module mathcanvas.engine {
  exports mathcanvas.engine;
  exports mathcanvas.engine.algebra;
  exports mathcanvas.engine.assume;
  exports mathcanvas.engine.builtin;
  exports mathcanvas.engine.calculus;
  exports mathcanvas.engine.compile;
  exports mathcanvas.engine.discrete;
  exports mathcanvas.engine.eval;
  exports mathcanvas.engine.linalg;
  exports mathcanvas.engine.expr;
  exports mathcanvas.engine.geometry;
  exports mathcanvas.engine.linalg.numeric;
  exports mathcanvas.engine.match;
  exports mathcanvas.engine.number;
  exports mathcanvas.engine.numbertheory;
  // differential equations
  exports mathcanvas.engine.ode;
  exports mathcanvas.engine.ode.numeric;
  exports mathcanvas.engine.parse;
  exports mathcanvas.engine.print;
  exports mathcanvas.engine.print.layout;
  exports mathcanvas.engine.provider;
  exports mathcanvas.engine.rule;
  // programs
  exports mathcanvas.engine.script;
  exports mathcanvas.engine.simplify;
  exports mathcanvas.engine.solve;
  // statistics
  exports mathcanvas.engine.stat;
  exports mathcanvas.engine.stat.numeric;

  requires hipparchus.core;
  // the numeric integrator of odesolve
  requires hipparchus.ode;
}
