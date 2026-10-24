package mathcanvas.engine.solve;

import java.util.Optional;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.provider.Provider;
import mathcanvas.engine.provider.Solve;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;

/**
 * The engine's own solver, registered as the {@code default} provider of {@link Solve}: the methods
 * of {@link Solver}, recording their steps. When no method applies it records the step that says
 * why, from the state reached to the unevaluated call, and declines.
 */
public record SolveProvider() implements Provider<Solve, SetExpr> {

  @Override
  public String name() {
    return "default";
  }

  @Override
  public Optional<SetExpr> apply(Solve input, Context ctx, Steps steps) {
    return Solver.methods(input.equations(), input.vars(), ctx, steps);
  }
}
