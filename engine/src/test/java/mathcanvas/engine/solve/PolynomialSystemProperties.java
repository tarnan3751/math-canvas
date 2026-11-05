package mathcanvas.engine.solve;

import static mathcanvas.engine.testing.Inputs.parse;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Random;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.simplify.Simplifier;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.statistics.Statistics;
import org.junit.jupiter.api.Test;

/**
 * Small polynomial systems by elimination ({@link PolynomialSystems}), built from a known solution:
 * a random linear equation and a random quadratic one, or two quadratics, through a planted point
 * with integer coordinates. The planted point is among the solutions, every solution satisfies each
 * equation exactly, and a linear equation with a quadratic one is always solved (two quadratics
 * that neither factor nor have an unknown of degree 1 with a constant coefficient stay).
 */
class PolynomialSystemProperties {

  private static final Symbol X = Symbol.of("x");
  private static final Symbol Y = Symbol.of("y");

  private static int small(Random r) {
    return r.nextInt(7) - 3;
  }

  /** A random polynomial in x and y of degree at most {@code degree}, never constant. */
  private static String polynomial(Random r, int degree) {
    StringBuilder s = new StringBuilder();
    s.append(1 + r.nextInt(3)).append(" x");
    if (degree == 2) {
      s.append(" + (").append(1 + r.nextInt(3)).append(") y^2");
      s.append(" + (").append(small(r)).append(") x^2");
      s.append(" + (").append(small(r)).append(") x y");
    }
    s.append(" + (").append(small(r)).append(") y");
    return s.toString();
  }

  /** {@code p = p(a, b)}. */
  private static Expr through(String p, int a, int b) {
    Expr at = parse(p.replace("x", "(" + a + ")").replace("y", "(" + b + ")"));
    return Canonical.of(Relation.of(Relation.Op.EQ, parse(p), at));
  }

  @Property(tries = 150)
  void plantedSolutionsAreFoundAndEverySolutionSatisfiesTheSystem(@ForAll long seed) {
    Random r = new Random(seed);
    int a = small(r);
    int b = small(r);
    boolean linear = r.nextBoolean();
    String first = polynomial(r, linear ? 1 : 2);
    String second = polynomial(r, 2);
    List<Expr> eqs = List.of(through(first, a, b), through(second, a, b));
    Expr equations = Canonical.of(new SetExpr(eqs));
    Rewriter.Result result =
        Solver.solve(equations, ListExpr.of(X, Y), Context.DEFAULT.withTrace(true));
    TraceChecks.assertChain(result);
    if (result.expr() instanceof Apply) {
      // a linear equation and a quadratic eliminate unless the quadratic is a consequence of the
      // line (its restriction to the line vanishes identically), which leaves a free unknown that
      // the solver rightly declines; two quadratics need not eliminate at all
      if (linear) {
        assertThat(vanishesOnTheLine(first, second, a, b)).as("%s stays", eqs).isTrue();
        Statistics.label("outcome").collect("degenerate");
        return;
      }
      Statistics.label("outcome").collect("stays");
      return;
    }
    Statistics.label("outcome").collect("solved");
    SetExpr solutions = (SetExpr) result.expr();
    Expr planted =
        Canonical.of(
            ListExpr.of(
                Relation.of(Relation.Op.EQ, X, Num.of(a)),
                Relation.of(Relation.Op.EQ, Y, Num.of(b))));
    assertThat(solutions.items()).as("%s", eqs).contains(planted);
    for (Expr solution : solutions.items()) {
      Map<Symbol, Expr> values =
          Map.of(X, value((ListExpr) solution, 0), Y, value((ListExpr) solution, 1));
      for (Expr eq : eqs) {
        Relation rel = (Relation) eq;
        Expr d = Scan.replace(Scan.replace(rel.left(), X, values.get(X)), Y, values.get(Y));
        Expr e = Scan.replace(Scan.replace(rel.right(), X, values.get(X)), Y, values.get(Y));
        Expr residual = Simplifier.simplify(Canonical.of(Exprs.sub(d, e)), Context.DEFAULT).expr();
        assertThat(residual).as("%s at %s", eq, solution).isEqualTo(Num.ZERO);
      }
    }
  }

  /**
   * Whether the quadratic {@code second}, taken through {@code (a, b)}, vanishes identically on the
   * line {@code first = first(a, b)}: a quadratic that vanishes at three distinct points of a line
   * vanishes on the whole line.
   */
  private static boolean vanishesOnTheLine(String first, String second, int a, int b) {
    Expr line = parse(first);
    Expr c = at(line, 1, 0);
    Expr d = at(line, 0, 1);
    Expr quadratic = parse(second);
    Expr value = at(quadratic, a, b);
    for (int k = 1; k <= 3; k++) {
      Expr y = Num.of(b + k);
      // x on the line: c x + d y = c a + d b
      Expr x =
          Simplifier.simplify(
                  Canonical.of(
                      Exprs.div(
                          Exprs.sub(
                              Sum.of(Product.of(c, Num.of(a)), Product.of(d, Num.of(b))),
                              Product.of(d, y)),
                          c)),
                  Context.DEFAULT)
              .expr();
      Expr residual =
          Simplifier.simplify(
                  Canonical.of(Exprs.sub(Scan.replace(Scan.replace(quadratic, X, x), Y, y), value)),
                  Context.DEFAULT)
              .expr();
      if (!residual.equals(Num.ZERO)) {
        return false;
      }
    }
    return true;
  }

  private static Expr at(Expr p, int x, int y) {
    return Simplifier.simplify(
            Canonical.of(Scan.replace(Scan.replace(p, X, Num.of(x)), Y, Num.of(y))),
            Context.DEFAULT)
        .expr();
  }

  @Test
  void aQuadraticThatIsAConsequenceOfTheLineLeavesTheSystem() {
    // on x + y = 1 the quadratic x + y² − x² − y = 0 vanishes identically, so the system has a
    // line of solutions and the solver declines rather than inventing one
    List<Expr> eqs =
        List.of(
            through("1 x + (1) y", 1, 0),
            through("1 x + (1) y^2 + (-1) x^2 + (0) x y + (-1) y", 1, 0));
    Rewriter.Result result =
        Solver.solve(Canonical.of(new SetExpr(eqs)), ListExpr.of(X, Y), Context.DEFAULT);
    assertThat(result.expr()).isInstanceOf(Apply.class);
    assertThat(
            vanishesOnTheLine("1 x + (1) y", "1 x + (1) y^2 + (-1) x^2 + (0) x y + (-1) y", 1, 0))
        .isTrue();
    assertThat(vanishesOnTheLine("1 x + (1) y", "1 x + (1) y^2 + (1) x^2 + (0) x y + (0) y", 1, 0))
        .isFalse();
  }

  private static Expr value(ListExpr solution, int k) {
    return ((Relation) solution.items().get(k)).right();
  }

  @Test
  void theLagrangeSystemOfXyOnTheCircleHasFourPoints() {
    Expr equations = parse("{y = 2λ x, x = 2λ y, x^2 + y^2 = 1}");
    Rewriter.Result r =
        Solver.solve(
            Canonical.of(equations),
            ListExpr.of(X, Y, Symbol.of("λ")),
            Context.DEFAULT.withTrace(true));
    TraceChecks.assertChain(r);
    assertThat(((SetExpr) r.expr()).items()).hasSize(4);
  }

  @Test
  void anUnderdeterminedSystemStays() {
    Rewriter.Result r =
        Solver.solve(
            Canonical.of(parse("{x^2 + y = 3, 2x^2 + 2y = 6}")),
            ListExpr.of(X, Y),
            Context.DEFAULT.withTrace(true));
    assertThat(r.expr()).isInstanceOf(Apply.class);
  }
}
