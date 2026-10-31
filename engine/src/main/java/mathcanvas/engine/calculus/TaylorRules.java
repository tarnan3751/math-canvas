package mathcanvas.engine.calculus;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Match;
import mathcanvas.engine.rule.Replacement;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;

/**
 * The rule set {@code taylor}: one rule, {@code polynomial}, that rewrites a call {@code taylor(f,
 * x, a, n)} with a non-negative integer order to {@code Σ f⁽ᵏ⁾(a)(x − a)ᵏ/k!} for {@code k = 0 …
 * n}, the derivatives taken by {@link Calculus#differentiate} and evaluated exactly at {@code a}.
 * Its values are the derivatives at the point, for the narration.
 *
 * <p>The polynomial is the Taylor polynomial only where {@code f} is {@code n} times differentiable
 * at {@code a}, and the derivative rules ignore jumps ({@code |x|′} is {@code sign(x)}), so each
 * derivative below the order must pass the strict {@link Continuity} certificate at the point (it
 * is smooth there) and the last one the lax one (it is continuous there). A derivative that fails,
 * that is undefined at the point or that could not be computed throws {@link TaylorException}
 * naming its order.
 */
final class TaylorRules {

  private TaylorRules() {}

  static final RuleSet RULES =
      new RuleSet(
          "taylor",
          List.of(
              Rule.valued(
                  "polynomial",
                  Pattern.parse("t_").where("t_", TaylorRules::applies),
                  Set.of("derivatives", "point"),
                  TaylorRules::polynomial)));

  /** A call {@code taylor(f, x, a, n)} with a symbol {@code x} and an integer {@code n ≥ 0}. */
  private static boolean applies(Expr e) {
    return e instanceof Apply a
        && a.head().name().equals("taylor")
        && a.args().size() == 4
        && a.args().get(1) instanceof Symbol
        && a.args().get(3) instanceof Num n
        && n.value() instanceof Int k
        && k.signum() >= 0
        && k.value().bitLength() < 31
        && Signs.finite(a.args().get(2))
        && Signs.scalar(a.args().get(2))
        && Signs.scalar(a.args().get(0));
  }

  private static Optional<Replacement.Valued.Result> polynomial(Match m) {
    Apply call = (Apply) m.bindings().get("t_");
    Expr f = call.args().get(0);
    Symbol x = (Symbol) call.args().get(1);
    Expr a = call.args().get(2);
    if (!Free.of(a, x)) {
      return Optional.empty();
    }
    int n = ((Int) ((Num) call.args().get(3)).value()).value().intValue();
    Context outer = m.context().withTrace(false);
    Context inner = outer.withAssumptions(outer.assumptions().without(x));
    List<Expr> values = new ArrayList<>();
    List<Expr> terms = new ArrayList<>();
    Expr shift = Sum.of(x, Product.of(Num.MINUS_ONE, a));
    Expr d = f;
    BigInteger factorial = BigInteger.ONE;
    for (int k = 0; k <= n; k++) {
      outer.cancellation().check();
      if (k > 0) {
        d = Calculus.differentiate(d, x, 1, inner).expr();
        factorial = factorial.multiply(BigInteger.valueOf(k));
      }
      if (stuck(d)) {
        throw new TaylorException(k, "the derivative of order " + k + " could not be computed");
      }
      int order = k;
      Expr value = Subst.value(d, x, a, outer).orElseThrow(() -> undefined(order, x, a));
      if (!Signs.finite(value)) {
        throw undefined(k, x, a);
      }
      if (Continuity.at(d, x, a, outer, k < n).isEmpty()) {
        // defined at the point, but not shown smooth there: the certificate is conservative, so
        // the next derivative is the one not shown continuous
        throw unshown(k < n ? k + 1 : k, x, a);
      }
      values.add(value);
      terms.add(
          Product.of(
              value, Num.of(Rational.of(BigInteger.ONE, factorial)), Power.of(shift, Num.of(k))));
    }
    Map<String, Expr> named = new TreeMap<>();
    named.put("derivatives", new ListExpr(values));
    named.put("point", Relation.of(Relation.Op.EQ, x, a));
    Expr sum = terms.size() == 1 ? terms.getFirst() : Sum.of(terms);
    return Optional.of(new Replacement.Valued.Result(sum, new TreeMap<>(named)));
  }

  private static TaylorException unshown(int order, Symbol x, Expr a) {
    return new TaylorException(
        order,
        "the derivative of order "
            + order
            + " could not be shown continuous at "
            + UnicodePrinter.INSTANCE.display(Relation.of(Relation.Op.EQ, x, a)));
  }

  private static TaylorException undefined(int order, Symbol x, Expr a) {
    return new TaylorException(
        order,
        "the derivative of order "
            + order
            + " is undefined at "
            + UnicodePrinter.INSTANCE.display(Relation.of(Relation.Op.EQ, x, a)));
  }

  /** Whether a derivative was left unevaluated somewhere in {@code e}. */
  private static boolean stuck(Expr e) {
    if (e instanceof Derivative) {
      return true;
    }
    for (Expr o : Operands.of(e)) {
      if (stuck(o)) {
        return true;
      }
    }
    return false;
  }
}
