package mathcanvas.engine.algebra;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;

/** The algebra's operations on expressions, as the language's commands call them. */
public final class Algebra {

  private Algebra() {}

  /**
   * Expands {@code e}: the rule set {@link ExpandRules#RULES} to its fixpoint.
   *
   * @param e the expression
   * @param ctx the context; with the trace on, every distribution and power is recorded
   * @return the expanded expression and its trace
   */
  public static Rewriter.Result expand(Expr e, Context ctx) {
    return Rewriter.rewrite(e, ExpandRules.RULES, ctx);
  }

  /**
   * Collects the expansion of {@code e} by powers of {@code v}: {@code Σ c_k·v^k}, each coefficient
   * a canonical expression in the other kernels. {@code v} may be any kernel ({@code sin(x)}); a
   * negative or fractional power of {@code v} is a kernel of its own and stays in a coefficient.
   *
   * @param e the expression
   * @param v the kernel to collect by
   * @param ctx the context
   * @return the collected expression; the expansion itself when {@code v} does not occur in it as a
   *     variable or it is not a polynomial in scalar kernels
   */
  public static Expr collect(Expr e, Expr v, Context ctx) {
    Expr expanded = expand(e, ctx.withTrace(false)).expr();
    Expr kernel = Canonical.of(v, ctx.precision());
    Polynomial p;
    try {
      p = Polynomial.of(expanded);
    } catch (IllegalArgumentException | ArithmeticException | AlgebraLimitException refused) {
      return expanded;
    }
    if (!p.variables().contains(kernel)) {
      return expanded;
    }
    // one pass over the terms, grouped by the exponents of the kernel that occur
    int i = p.variables().indexOf(kernel);
    SortedMap<Integer, Map<Monomial, RealNumeric>> groups =
        new TreeMap<>(Comparator.reverseOrder());
    for (Polynomial.Term t : p.terms()) {
      int[] x = t.monomial().exponents();
      int k = x[i];
      x[i] = 0;
      groups
          .computeIfAbsent(k, unused -> new TreeMap<>(Monomial.ORDER))
          .put(new Monomial(x), t.coefficient());
    }
    List<Expr> terms = new ArrayList<>();
    for (Map.Entry<Integer, Map<Monomial, RealNumeric>> g : groups.entrySet()) {
      int k = g.getKey();
      Expr c = Polynomial.build(p.variables(), g.getValue()).raw();
      terms.add(k == 0 ? c : Product.of(c, k == 1 ? kernel : Power.of(kernel, Num.of(k))));
    }
    return Canonical.of(terms.size() == 1 ? terms.getFirst() : Sum.of(terms), ctx.precision());
  }
}
