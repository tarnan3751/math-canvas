package mathcanvas.engine.linalg;

import static mathcanvas.engine.linalg.LinalgCases.multiply;
import static mathcanvas.engine.linalg.LinalgCases.random;
import static mathcanvas.engine.linalg.LinalgCases.value;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.statistics.Statistics;

/**
 * The exact operations against their defining identities over random rational matrices: {@code A ·
 * A⁻¹ = I}, the reduced form idempotent and the rank its pivot count, every solution of {@code A x
 * = b} substituting back, Gram–Schmidt orthogonal and spanning what it was given, and the two
 * determinant methods agreeing.
 */
class LinalgProperties {

  private static Rewriter.Result traced(String name, Expr... args) {
    Rewriter.Result r = Linalg.apply(new Apply(Symbol.of(name), List.of(args)), LinalgCases.TRACED);
    TraceChecks.assertChain(r);
    return r;
  }

  @Property(tries = 300, seed = "5101")
  void aMatrixTimesItsInverseIsTheIdentity(@ForAll Random random) {
    int n = 1 + random.nextInt(6);
    Matrix a = random(random, n, n);
    Expr det = value("det", a);
    if (det.equals(Num.ZERO)) {
      Statistics.label("singular").collect(true);
      assertThatThrownBy(() -> traced("inverse", a)).hasMessage("the matrix is singular");
      return;
    }
    Statistics.label("singular").collect(false);
    Matrix inverse = (Matrix) traced("inverse", a).expr();
    LinalgCases.isIdentity(multiply(a.rows(), inverse.rows()));
    LinalgCases.isIdentity(multiply(inverse.rows(), a.rows()));
  }

  @Property(tries = 300, seed = "5102")
  void theReducedFormIsIdempotentAndTheRankItsPivots(@ForAll Random random) {
    Matrix a = dependent(random, 1 + random.nextInt(5), 1 + random.nextInt(5));
    Matrix reduced = (Matrix) traced("rref", a).expr();
    assertThat(value("rref", reduced)).isEqualTo(reduced);
    long pivots =
        reduced.rows().stream()
            .filter(row -> row.stream().anyMatch(e -> !e.equals(Num.ZERO)))
            .count();
    assertThat(traced("rank", a).expr()).isEqualTo(Num.of(pivots));
    // the row rank is the column rank
    assertThat(value("rank", value("transpose", a))).isEqualTo(Num.of(pivots));
    // rank plus nullity is the number of columns
    Expr basis = value("nullspace", a);
    int nullity = basis instanceof Matrix m ? m.rows().size() : 0;
    assertThat(pivots + nullity).isEqualTo(a.rows().getFirst().size());
  }

  @Property(tries = 300, seed = "5103")
  void everySolutionSubstitutesBack(@ForAll Random random) {
    int m = 1 + random.nextInt(4);
    int n = 1 + random.nextInt(4);
    Matrix a = dependent(random, m, n);
    List<Expr> b = new ArrayList<>();
    if (random.nextBoolean()) {
      Matrix x0 = random(random, n, 1);
      for (List<Expr> row : multiply(a.rows(), x0.rows())) {
        b.add(row.getFirst());
      }
    } else {
      b.addAll(random(random, 1, m).rows().getFirst());
    }
    Expr x = traced("linsolve", a, Vector.of(b)).expr();
    if (x instanceof SetExpr none) {
      assertThat(none.items()).isEmpty();
      Statistics.label("outcome").collect("inconsistent");
      List<List<Expr>> augmented = new ArrayList<>();
      for (int i = 0; i < m; i++) {
        List<Expr> row = new ArrayList<>(a.rows().get(i));
        row.add(b.get(i));
        augmented.add(row);
      }
      Num rank = (Num) value("rank", a);
      Num wider = (Num) value("rank", Matrix.of(augmented));
      assertThat(((mathcanvas.engine.number.Int) rank.value()).value())
          .isLessThan(((mathcanvas.engine.number.Int) wider.value()).value());
      return;
    }
    Statistics.label("outcome").collect(x.toString().contains("t_") ? "general" : "unique");
    List<Expr> xs = ((Vector) x).items();
    for (int i = 0; i < m; i++) {
      List<Expr> terms = new ArrayList<>();
      for (int j = 0; j < n; j++) {
        terms.add(mathcanvas.engine.expr.Product.of(a.rows().get(i).get(j), xs.get(j)));
      }
      terms.add(mathcanvas.engine.expr.Product.of(Num.MINUS_ONE, b.get(i)));
      Expr residual =
          Algebra.cancel(
              mathcanvas.engine.expr.Canonical.of(mathcanvas.engine.expr.Sum.of(terms)),
              Context.DEFAULT);
      assertThat(residual).as("row %d of %s x = %s at %s", i, a, b, x).isEqualTo(Num.ZERO);
    }
  }

  @Property(tries = 300, seed = "5104")
  void gramSchmidtIsOrthogonalAndKeepsTheSpan(@ForAll Random random) {
    Matrix vs = dependent(random, 1 + random.nextInt(4), 1 + random.nextInt(4));
    Expr out = traced("gramschmidt", vs).expr();
    List<List<Expr>> ws = out instanceof Matrix m ? m.rows() : List.of();
    Entries en = new Entries(Context.DEFAULT);
    for (int p = 0; p < ws.size(); p++) {
      for (int q = p + 1; q < ws.size(); q++) {
        assertThat(GramSchmidt.inner(ws.get(p), ws.get(q), en)).isEqualTo(Num.ZERO);
      }
    }
    Expr rank = value("rank", vs);
    assertThat(Num.of(ws.size())).isEqualTo(rank);
    if (!ws.isEmpty()) {
      List<List<Expr>> both = new ArrayList<>(vs.rows());
      both.addAll(ws);
      assertThat(value("rank", Matrix.of(both))).isEqualTo(rank);
    }
  }

  @Property(tries = 200, seed = "5105")
  void theDeterminantMethodsAgree(@ForAll Random random) {
    int n = 1 + random.nextInt(6);
    Matrix a = random(random, n, n);
    Expr cofactor = traced("det", a, Symbol.of("cofactor")).expr();
    assertThat(traced("det", a, Symbol.of("elimination")).expr()).isEqualTo(cofactor);
    if (!cofactor.equals(Num.ZERO)) {
      Expr inverse = value("det", value("inverse", a));
      assertThat(new Entries(Context.DEFAULT).mul(cofactor, inverse)).isEqualTo(Num.ONE);
    }
  }

  /** A random matrix whose rows are now and then combinations of the rows before. */
  private static Matrix dependent(Random random, int rows, int columns) {
    Entries en = new Entries(Context.DEFAULT);
    List<List<Expr>> out = new ArrayList<>(random(random, rows, columns).rows());
    for (int i = 1; i < rows; i++) {
      if (random.nextInt(3) == 0) {
        int k = random.nextInt(i);
        Expr c = Num.of(random.nextInt(5) - 2);
        List<Expr> row = new ArrayList<>();
        for (Expr e : out.get(k)) {
          row.add(en.mul(c, e));
        }
        out.set(i, row);
      }
    }
    return Matrix.of(out);
  }
}
