package mathcanvas.engine.solve;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.simplify.Simplifier;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;

/**
 * Every solution of a random linear system satisfies each equation exactly, and an empty answer is
 * an inconsistent system by an independent rank count.
 */
class SystemProperties {

  private static final Symbol A = Symbol.of("a");
  private static final List<Symbol> NAMES =
      List.of(Symbol.of("x"), Symbol.of("y"), Symbol.of("z"), Symbol.of("w"));

  /** A system: its coefficient rows, each with the right side last. */
  record System(List<List<Expr>> rows, int unknowns) {}

  @Provide
  Arbitrary<System> systems() {
    return Arbitraries.randomValue(SystemProperties::system);
  }

  static System system(Random random) {
    int n = 1 + random.nextInt(4);
    int m = 1 + random.nextInt(5);
    boolean parameter = random.nextInt(4) == 0;
    List<List<Expr>> rows = new ArrayList<>();
    for (int i = 0; i < m; i++) {
      List<Expr> row = new ArrayList<>();
      for (int j = 0; j <= n; j++) {
        row.add(Num.of(random.nextInt(3) == 0 ? 0 : random.nextInt(7) - 3));
      }
      rows.add(row);
    }
    if (m > 1 && random.nextInt(3) == 0) {
      // a dependent row: a multiple of the first, sometimes with another right side
      List<Expr> dependent = new ArrayList<>();
      for (int j = 0; j <= n; j++) {
        dependent.add(Canonical.of(Product.of(Num.of(2), rows.getFirst().get(j))));
      }
      if (random.nextBoolean()) {
        dependent.set(n, Num.of(random.nextInt(5)));
      }
      rows.set(m - 1, dependent);
    }
    if (parameter) {
      rows.get(random.nextInt(m)).set(random.nextInt(n + 1), A);
    }
    return new System(rows, n);
  }

  private static List<Expr> equations(System s) {
    List<Expr> eqs = new ArrayList<>();
    for (List<Expr> row : s.rows()) {
      List<Expr> terms = new ArrayList<>();
      terms.add(Num.ZERO);
      for (int j = 0; j < s.unknowns(); j++) {
        terms.add(Product.of(row.get(j), NAMES.get(j)));
      }
      eqs.add(Canonical.of(new Relation(Relation.Op.EQ, Sum.of(terms), row.get(s.unknowns()))));
    }
    return eqs;
  }

  @Property(tries = 2000)
  void solutionsSatisfyEveryEquation(@ForAll("systems") System s) {
    List<Expr> eqs = equations(s);
    List<Symbol> vars = NAMES.subList(0, s.unknowns());
    Expr equations = Canonical.of(new SetExpr(eqs));
    Expr variables = vars.size() == 1 ? vars.getFirst() : new Vector(List.copyOf(vars));
    Rewriter.Result r = Solver.solve(equations, variables, Context.DEFAULT.withTrace(true));
    TraceChecks.assertChain(r);
    if (r.expr() instanceof mathcanvas.engine.expr.Apply) {
      // every value, or undecided: the equations do not involve the unknowns
      for (Symbol v : vars) {
        assertThat(SolveCases.mentions(equations, v)).as("%s", eqs).isFalse();
      }
      Statistics.label("outcome").collect("every value or undecided");
      return;
    }
    assertThat(r.expr()).as("%s", eqs).isInstanceOf(SetExpr.class);
    SetExpr result = (SetExpr) r.expr();
    boolean hasParameter = s.rows().stream().anyMatch(row -> row.contains(A));
    if (result.items().isEmpty()) {
      Statistics.label("outcome").collect("none");
      // generically inconsistent: so at a sample value of the parameter
      Map<Symbol, Expr> at = Map.of(A, Num.of(Rational.of(37, 11)));
      assertThat(rank(s, at, false)).as("%s", eqs).isLessThan(rank(s, at, true));
      return;
    }
    Statistics.label("outcome").collect(hasParameter ? "solved, parameter" : "solved");
    assertThat(result.items()).hasSize(vars.size() == 1 ? result.items().size() : 1);
    List<Map<Symbol, Expr>> solutions = new ArrayList<>();
    if (vars.size() == 1) {
      for (Expr v : result.items()) {
        solutions.add(Map.of(vars.getFirst(), v));
      }
    } else {
      Map<Symbol, Expr> values = new HashMap<>();
      for (Expr e : ((ListExpr) result.items().getFirst()).items()) {
        Relation eq = (Relation) e;
        values.put((Symbol) eq.left(), eq.right());
      }
      solutions.add(values);
    }
    for (Map<Symbol, Expr> values : solutions) {
      for (Expr eq : eqs) {
        if (!(eq instanceof Relation rel)) {
          assertThat(eq).isEqualTo(mathcanvas.engine.expr.Bool.TRUE);
          continue;
        }
        Expr difference =
            Canonical.of(
                Sum.of(
                    subs(rel.left(), values),
                    Product.of(Num.MINUS_ONE, subs(rel.right(), values))));
        Expr zero =
            Simplifier.simplify(Algebra.cancel(difference, Context.DEFAULT), Context.DEFAULT)
                .expr();
        assertThat(zero).as("%s at %s", eq, values).isEqualTo(Num.ZERO);
      }
    }
  }

  private static Expr subs(Expr e, Map<Symbol, Expr> values) {
    if (e instanceof Symbol sym && values.containsKey(sym)) {
      return values.get(sym);
    }
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    List<Expr> out = new ArrayList<>();
    for (Expr op : ops) {
      out.add(subs(op, values));
    }
    return Operands.rebuild(e, out);
  }

  /** The rank of the coefficients (or of the augmented matrix) over ℚ, the parameter given. */
  private static int rank(System s, Map<Symbol, Expr> at, boolean augmented) {
    int cols = augmented ? s.unknowns() + 1 : s.unknowns();
    List<RealNumeric[]> rows = new ArrayList<>();
    for (List<Expr> row : s.rows()) {
      RealNumeric[] r = new RealNumeric[cols];
      for (int j = 0; j < cols; j++) {
        r[j] = (RealNumeric) ((Num) Canonical.of(subs(row.get(j), at))).value();
      }
      rows.add(r);
    }
    MathContext exact = MathContext.UNLIMITED;
    int rank = 0;
    for (int c = 0; c < cols && rank < rows.size(); c++) {
      int p = -1;
      for (int i = rank; i < rows.size(); i++) {
        if (!rows.get(i)[c].isZero()) {
          p = i;
          break;
        }
      }
      if (p < 0) {
        continue;
      }
      RealNumeric[] t = rows.get(rank);
      rows.set(rank, rows.get(p));
      rows.set(p, t);
      RealNumeric[] pivot = rows.get(rank);
      for (int i = rank + 1; i < rows.size(); i++) {
        RealNumeric f = (RealNumeric) rows.get(i)[c].divide(pivot[c], exact);
        for (int j = c; j < cols; j++) {
          rows.get(i)[j] =
              (RealNumeric) rows.get(i)[j].subtract(f.multiply(pivot[j], exact), exact);
        }
      }
      rank++;
    }
    return rank;
  }
}
