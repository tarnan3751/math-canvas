package mathcanvas.engine.linalg;

import static mathcanvas.engine.linalg.LinalgCases.multiply;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.eval.NumericForm;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.statistics.Statistics;

/**
 * Every reported eigenpair satisfies {@code A v = λ v}: exactly for matrices built as {@code P D
 * P⁻¹} with rational eigenvalues, and for random 2×2 and 3×3 integer matrices exactly when the
 * entries cancel to zero and otherwise at 40 digits (a cosine of a third of an angle, Cardano's
 * radicals). The characteristic polynomial evaluated at its matrix is zero (Cayley–Hamilton), and
 * {@code P⁻¹ A P = D} for every diagonalization.
 */
class EigenProperties {

  private static final Entries EN = new Entries(Context.DEFAULT);

  private static Rewriter.Result traced(String name, Expr... args) {
    Rewriter.Result r = Linalg.apply(new Apply(Symbol.of(name), List.of(args)), LinalgCases.TRACED);
    TraceChecks.assertChain(r);
    return r;
  }

  /** {@code P D P⁻¹} for a random invertible {@code P} and small integer eigenvalues. */
  private static Matrix similar(Random random, int n) {
    while (true) {
      Matrix p = LinalgCases.random(random, n, n);
      if (LinalgCases.value("det", p).equals(Num.ZERO)) {
        continue;
      }
      List<List<Expr>> d = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        List<Expr> row = new ArrayList<>();
        for (int j = 0; j < n; j++) {
          row.add(i == j ? Num.of(random.nextInt(5) - 2) : Num.ZERO);
        }
        d.add(row);
      }
      Matrix inverse = (Matrix) Linalg.inverse(p, Context.DEFAULT);
      return Matrix.of(multiply(multiply(p.rows(), d), inverse.rows()));
    }
  }

  private static Matrix integers(Random random, int n) {
    List<List<Expr>> rows = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      List<Expr> row = new ArrayList<>();
      for (int j = 0; j < n; j++) {
        row.add(Num.of(random.nextInt(9) - 4));
      }
      rows.add(row);
    }
    return Matrix.of(rows);
  }

  /** Whether an entry is zero exactly, or else within {@code 10^-30} at 40 digits. */
  private static boolean zero(Expr e, boolean exactly) {
    if (EN.isZero(e) == Tri.TRUE) {
      return true;
    }
    if (exactly) {
      return false;
    }
    Expr n = NumericForm.numeric(e, 40, Cancellation.NEVER);
    if (!(n instanceof Num num)) {
      return false;
    }
    Numeric v = num.value();
    BigDecimal bound = new BigDecimal("1e-30");
    return v.re().abs().compareTo(new mathcanvas.engine.number.Real(bound)) < 0
        && v.im().abs().compareTo(new mathcanvas.engine.number.Real(bound)) < 0;
  }

  private static void eigenpairsHold(Matrix a, boolean exactly) {
    Expr pairs = traced("eigenvectors", a).expr();
    if (pairs instanceof Apply) {
      Statistics.label("outcome").collect("stays");
      return;
    }
    Statistics.label("outcome").collect("pairs");
    int count = 0;
    for (Expr pair : ((ListExpr) pairs).items()) {
      List<Expr> items = ((ListExpr) pair).items();
      Expr lambda = items.get(0);
      Matrix basis = (Matrix) items.get(1);
      for (List<Expr> v : basis.rows()) {
        count++;
        List<List<Expr>> column = v.stream().map(e -> List.of(e)).toList();
        List<List<Expr>> av = multiply(a.rows(), column);
        for (int i = 0; i < v.size(); i++) {
          Expr residual = EN.sub(av.get(i).getFirst(), EN.mul(lambda, v.get(i)));
          assertThat(zero(residual, exactly))
              .as("(A − %s I) %s, row %d, of %s: %s", lambda, v, i, a, residual)
              .isTrue();
        }
      }
    }
    assertThat(count).isPositive();
  }

  /** At least this share of the tries must answer: a call that stays proves nothing. */
  private static void mostlyAnswered(String answered, double percent) {
    Statistics.label("outcome").coverage(c -> c.check(answered).percentage(p -> p >= percent));
  }

  @Property(tries = 100, seed = "5301")
  void eigenpairsOfMatricesWithRationalEigenvalues(@ForAll Random random) {
    Matrix a = similar(random, 1 + random.nextInt(4));
    eigenpairsHold(a, true);
    // every such matrix splits over ℚ, so none may stay
    mostlyAnswered("pairs", 100);
    // the multiplicities add up to the order
    Expr values = traced("eigenvalues", a).expr();
    int size = values instanceof Vector v ? v.items().size() : 0;
    assertThat(size).isEqualTo(a.rows().size());
  }

  @Property(tries = 100, seed = "5302")
  void eigenpairsOfRandomSmallMatrices(@ForAll Random random) {
    eigenpairsHold(integers(random, 2 + random.nextInt(2)), false);
    mostlyAnswered("pairs", 95);
  }

  @Property(tries = 100, seed = "5303")
  void cayleyHamilton(@ForAll Random random) {
    Matrix a = LinalgCases.random(random, 1 + random.nextInt(5), 1);
    int n = a.rows().size();
    a = LinalgCases.random(random, n, n);
    List<Expr> c = CharPoly.coefficients(a.rows(), EN);
    // Horner: ((I·A + c₁I)·A + c₂I)…
    List<List<Expr>> acc = LinalgCases.identity(n);
    for (int k = 1; k <= n; k++) {
      acc = multiply(acc, a.rows());
      for (int i = 0; i < n; i++) {
        List<Expr> row = new ArrayList<>(acc.get(i));
        row.set(i, EN.add(row.get(i), c.get(k)));
        acc.set(i, row);
      }
    }
    for (List<Expr> row : acc) {
      assertThat(row).allSatisfy(e -> assertThat(e).isEqualTo(Num.ZERO));
    }
  }

  @Property(tries = 100, seed = "5304")
  void aDiagonalizationDiagonalizes(@ForAll Random random) {
    Matrix a = random.nextBoolean() ? similar(random, 1 + random.nextInt(4)) : integers(random, 2);
    Expr out = traced("diagonalize", a).expr();
    if (out instanceof Apply) {
      Statistics.label("outcome").collect("stays");
      return;
    }
    Statistics.label("outcome").collect("diagonalized");
    mostlyAnswered("diagonalized", 90);
    Matrix p = (Matrix) ((ListExpr) out).items().get(0);
    Matrix d = (Matrix) ((ListExpr) out).items().get(1);
    // A P = P D, which is P⁻¹ A P = D for the invertible P
    List<List<Expr>> ap = multiply(a.rows(), p.rows());
    List<List<Expr>> pd = multiply(p.rows(), d.rows());
    for (int i = 0; i < ap.size(); i++) {
      for (int j = 0; j < ap.size(); j++) {
        assertThat(zero(EN.sub(ap.get(i).get(j), pd.get(i).get(j)), false))
            .as("A P = P D at %d, %d for %s", i, j, a)
            .isTrue();
      }
    }
    assertThat(LinalgCases.value("rank", p)).isEqualTo(Num.of(p.rows().size()));
  }
}
