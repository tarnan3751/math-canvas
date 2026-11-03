package mathcanvas.engine.solve;

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
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
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

  private static Expr parse(String s) {
    return Parser.parseExpression(s, Names.NONE);
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
      // a linear equation and a quadratic always eliminate; two quadratics need not
      assertThat(linear).as("%s stays", eqs).isFalse();
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
