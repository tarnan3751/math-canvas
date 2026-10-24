package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

/**
 * Vector and matrix arithmetic against the element-wise definitions, computed here on the tower's
 * numbers: sums, scalar multiples, dot and cross products, matrix products and powers.
 */
class LinearAlgebraProperties {

  static final java.math.MathContext EXACT = java.math.MathContext.UNLIMITED;

  static Numeric number(Random random) {
    return Rational.of(random.nextInt(21) - 10, random.nextInt(3) + 1);
  }

  static Numeric[][] matrix(Random random, int rows, int columns) {
    Numeric[][] m = new Numeric[rows][columns];
    for (int i = 0; i < rows; i++) {
      for (int j = 0; j < columns; j++) {
        m[i][j] = number(random);
      }
    }
    return m;
  }

  static Expr vector(Numeric[] v) {
    List<Expr> items = new ArrayList<>();
    for (Numeric x : v) {
      items.add(new Num(x));
    }
    return new Vector(items);
  }

  static Expr matrix(Numeric[][] m) {
    List<List<Expr>> rows = new ArrayList<>();
    for (Numeric[] row : m) {
      List<Expr> r = new ArrayList<>();
      for (Numeric x : row) {
        r.add(new Num(x));
      }
      rows.add(r);
    }
    return new Matrix(rows);
  }

  static Numeric[][] times(Numeric[][] a, Numeric[][] b) {
    Numeric[][] c = new Numeric[a.length][b[0].length];
    for (int i = 0; i < a.length; i++) {
      for (int j = 0; j < b[0].length; j++) {
        Numeric s = mathcanvas.engine.number.Int.ZERO;
        for (int k = 0; k < b.length; k++) {
          s = s.add(a[i][k].multiply(b[k][j], EXACT), EXACT);
        }
        c[i][j] = s;
      }
    }
    return c;
  }

  static Expr eval(Expr e) {
    return Evaluator.evaluate(e, Environment.EMPTY, false).value();
  }

  @Property(tries = 300)
  void vectorSumsAndMultiplesAreElementWise(@ForAll Random random) {
    int n = random.nextInt(4) + 1;
    Numeric[] u = matrix(random, 1, n)[0];
    Numeric[] v = matrix(random, 1, n)[0];
    Numeric c = number(random);
    Numeric[] expected = new Numeric[n];
    for (int k = 0; k < n; k++) {
      expected[k] = c.multiply(u[k], EXACT).add(v[k], EXACT);
    }
    assertThat(eval(Sum.of(Product.of(new Num(c), vector(u)), vector(v))))
        .isEqualTo(vector(expected));
  }

  @Property(tries = 300)
  void dotAndCrossProducts(@ForAll Random random) {
    Numeric[] u = matrix(random, 1, 3)[0];
    Numeric[] v = matrix(random, 1, 3)[0];
    Numeric dot = mathcanvas.engine.number.Int.ZERO;
    for (int k = 0; k < 3; k++) {
      dot = dot.add(u[k].multiply(v[k], EXACT), EXACT);
    }
    assertThat(eval(Product.of(vector(u), vector(v)))).isEqualTo(new Num(dot));
    Numeric[] cross = new Numeric[3];
    for (int k = 0; k < 3; k++) {
      int a = (k + 1) % 3;
      int b = (k + 2) % 3;
      cross[k] = u[a].multiply(v[b], EXACT).subtract(u[b].multiply(v[a], EXACT), EXACT);
    }
    Expr w = eval(Apply.of(Symbol.of("cross"), vector(u), vector(v)));
    assertThat(w).isEqualTo(vector(cross));
    assertThat(eval(Product.of(w, vector(u)))).isEqualTo(Num.ZERO);
  }

  @Property(tries = 300)
  void matrixProductsAndPowers(@ForAll Random random) {
    int p = random.nextInt(3) + 1;
    int q = random.nextInt(3) + 1;
    int r = random.nextInt(3) + 1;
    Numeric[][] a = matrix(random, p, q);
    Numeric[][] b = matrix(random, q, r);
    assertThat(eval(Product.of(matrix(a), matrix(b)))).isEqualTo(matrix(times(a, b)));
    Numeric[][] v = matrix(random, q, 1);
    Numeric[] column = new Numeric[q];
    for (int k = 0; k < q; k++) {
      column[k] = v[k][0];
    }
    Numeric[][] av = times(a, v);
    Numeric[] expected = new Numeric[p];
    for (int k = 0; k < p; k++) {
      expected[k] = av[k][0];
    }
    assertThat(eval(Product.of(matrix(a), vector(column)))).isEqualTo(vector(expected));
    Numeric[][] s = matrix(random, p, p);
    int n = random.nextInt(6) + 1;
    Numeric[][] power = s;
    for (int k = 1; k < n; k++) {
      power = times(power, s);
    }
    assertThat(eval(Power.of(matrix(s), Num.of(n)))).isEqualTo(matrix(power));
  }
}
