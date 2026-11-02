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
 * <p>A periodic equation's solutions may be families in an integer parameter, {@code parameter}:
 * {@code 2πk + π/6} stands for every value it takes at an integer {@code k} ({@code
 * docs/language.md} §14.7).
 *
 * @param equations the equations, canonical relations {@code =}
 * @param vars the unknowns, distinct
 * @param precision the precision of the canonical form
 * @param parameter the integer parameter a general solution may use, distinct from the unknowns and
 *     occurring in none of the equations
 */
public record Solve(
    List<Expr> equations, List<Symbol> vars, MathContext precision, Symbol parameter)
    implements Operation<SetExpr> {

  /** The parameter when the caller names none. */
  public static final Symbol PARAMETER = Symbol.of("k");

  /** Copies the lists. */
  public Solve {
    equations = List.copyOf(equations);
    vars = List.copyOf(vars);
  }

  /**
   * Solving with the parameter {@link #PARAMETER}.
   *
   * @param equations the equations, canonical relations {@code =}
   * @param vars the unknowns, distinct
   * @param precision the precision of the canonical form
   */
  public Solve(List<Expr> equations, List<Symbol> vars, MathContext precision) {
    this(equations, vars, precision, PARAMETER);
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
