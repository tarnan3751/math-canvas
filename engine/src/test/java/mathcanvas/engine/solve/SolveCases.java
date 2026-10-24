package mathcanvas.engine.solve;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.simplify.Simplifier;
import mathcanvas.engine.testing.Soundness;

/** Runs {@code solve(…)} text through the solver directly, and checks solutions. */
final class SolveCases {

  static final Symbol X = Symbol.of("x");

  private static final ResourceBundle NARRATION =
      ResourceBundle.getBundle("mathcanvas.engine.rule.narration", Locale.ROOT);

  private SolveCases() {}

  /** Solves the call written as text, traced, in {@code ctx}. */
  static Rewriter.Result run(String call, Context ctx) {
    Apply a = (Apply) Parser.parseExpression(call, Names.NONE);
    Rewriter.Result r =
        Solver.solve(Canonical.of(a.args().get(0)), a.args().get(1), ctx.withTrace(true));
    TraceChecks.assertChain(r);
    for (Rewrite w : r.trace().rewrites()) {
      assertThat(NARRATION.containsKey(w.ruleSet() + "." + w.rule()))
          .as("narration of %s.%s", w.ruleSet(), w.rule())
          .isTrue();
    }
    return r;
  }

  static Rewriter.Result run(String call) {
    return run(call, Context.DEFAULT);
  }

  /** The result in Unicode display form. */
  static String shown(String call) {
    return UnicodePrinter.INSTANCE.display(run(call).expr());
  }

  /** The names of the trace's steps, in order. */
  static List<String> steps(String call) {
    return steps(call, Context.DEFAULT);
  }

  /** The names of the trace's steps in {@code ctx}, in order. */
  static List<String> steps(String call, Context ctx) {
    List<String> out = new ArrayList<>();
    for (Rewrite w : run(call, ctx).trace().rewrites()) {
      assertThat(w.ruleSet()).isEqualTo(Solver.RULE_SET);
      out.add(w.rule());
    }
    return out;
  }

  /** {@code Σ c_k·rᵏ} over the terms of one sign of {@code p}: positive or negative. */
  static Expr signedPart(Polynomial p, Expr r, boolean positive) {
    List<Expr> terms = new ArrayList<>();
    terms.add(Num.ZERO);
    for (Polynomial.Term t : p.terms()) {
      if ((t.coefficient().signum() > 0) != positive) {
        continue;
      }
      List<Expr> factors = new ArrayList<>();
      factors.add(Num.of(positive ? t.coefficient() : t.coefficient().negate()));
      for (int i = 0; i < p.variables().size(); i++) {
        int k = t.monomial().exponent(i);
        if (k > 0) {
          Expr v = p.variables().get(i).equals(X) ? r : p.variables().get(i);
          factors.add(Power.of(v, Num.of(k)));
        }
      }
      terms.add(factors.size() == 1 ? factors.getFirst() : Product.of(factors));
    }
    return terms.size() == 1 ? terms.getFirst() : Sum.of(terms);
  }

  /**
   * Asserts that every solution of {@code p = 0} in {@code s} makes it hold: exactly (expanded and
   * simplified to 0) when the solution has square roots only, and otherwise through {@link
   * Soundness#check} on the positive and negative parts, at points for the other symbols.
   *
   * @return how many solutions were checked exactly
   */
  static int assertSolutions(Polynomial p, SetExpr s, Random random) {
    int exact = 0;
    Set<Symbol> others = new TreeSet<>((a, b) -> a.name().compareTo(b.name()));
    for (Expr v : p.variables()) {
      if (v instanceof Symbol sym && !sym.equals(X)) {
        others.add(sym);
      }
    }
    List<Map<Symbol, Numeric>> points = Soundness.points(others, Assumptions.EMPTY, 4, random);
    for (Expr r : s.items()) {
      if (squareRootsOnly(r)) {
        Expr value =
            Canonical.of(
                Sum.of(signedPart(p, r, true), Product.of(Num.MINUS_ONE, signedPart(p, r, false))));
        Expr expanded = Algebra.expand(value, Context.DEFAULT).expr();
        Expr zero = Simplifier.simplify(expanded, Context.DEFAULT).expr();
        if (zero.equals(Num.ZERO)) {
          exact++;
          continue;
        }
      }
      int compared =
          Soundness.check(
              Canonical.of(signedPart(p, r, true)), Canonical.of(signedPart(p, r, false)), points);
      assertThat(compared).as("%s at %s", p, r).isPositive();
    }
    return exact;
  }

  /** Whether the symbol {@code v} occurs in {@code e}. */
  static boolean mentions(Expr e, Symbol v) {
    if (e.equals(v)) {
      return true;
    }
    for (Expr op : Operands.of(e)) {
      if (mentions(op, v)) {
        return true;
      }
    }
    return false;
  }

  /** Whether {@code e} has no function and no power other than integers and square roots. */
  static boolean squareRootsOnly(Expr e) {
    if (e instanceof Apply) {
      return false;
    }
    if (e instanceof Power w
        && w.exponent() instanceof Num n
        && n.value() instanceof RealNumeric q
        && !q.isInteger()
        && !q.abs().equals(mathcanvas.engine.number.Rational.of(1, 2))) {
      return false;
    }
    for (Expr op : Operands.of(e)) {
      if (!squareRootsOnly(op)) {
        return false;
      }
    }
    return true;
  }
}
