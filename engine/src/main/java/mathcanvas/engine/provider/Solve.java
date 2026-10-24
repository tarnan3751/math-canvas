package mathcanvas.engine.provider;

import java.math.MathContext;
import java.util.List;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Symbol;

/**
 * Solving equations for unknowns. A result is correct when it is the set of solutions: values for
 * one unknown, lists of equations {@code x = v} for several. It is normalised to its canonical form
 * at the precision: each solution canonical, the set sorted and deduplicated.
 *
 * @param equations the equations, canonical relations {@code =}
 * @param vars the unknowns, distinct
 * @param precision the precision of the canonical form
 */
public record Solve(List<Expr> equations, List<Symbol> vars, MathContext precision)
    implements Operation<SetExpr> {

  /** Copies the lists. */
  public Solve {
    equations = List.copyOf(equations);
    vars = List.copyOf(vars);
  }

  @Override
  public Class<SetExpr> outputType() {
    return SetExpr.class;
  }

  @Override
  public SetExpr normalise(SetExpr result) {
    return (SetExpr) Canonical.of(result, precision);
  }
}
