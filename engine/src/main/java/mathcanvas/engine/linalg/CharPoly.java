package mathcanvas.engine.linalg;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;

/**
 * The characteristic polynomial {@code det(xI − A)} by Berkowitz's algorithm (Berkowitz, "On
 * computing the determinant in small parallel time using a small number of processors", Inf. Proc.
 * Letters 18 (1984)), which needs no division, so its coefficients are exact for any entries: with
 * {@code A = [[a, R], [C, A₁]]}, the coefficient vector of {@code A} is the lower-triangular
 * Toeplitz matrix of {@code 1, −a, −R·C, −R·A₁·C, …} times that of {@code A₁}.
 */
final class CharPoly {

  private CharPoly() {}

  /**
   * The coefficients of {@code det(xI − A)}, from {@code x^n} (which is 1) down to the constant.
   */
  static List<Expr> coefficients(List<List<Expr>> a, Entries en) {
    int n = a.size();
    if (n == 1) {
      return List.of(Num.ONE, en.neg(a.getFirst().getFirst()));
    }
    Expr top = a.getFirst().getFirst();
    List<Expr> r = a.getFirst().subList(1, n);
    List<Expr> c = new ArrayList<>();
    List<List<Expr>> sub = new ArrayList<>();
    for (List<Expr> row : a.subList(1, n)) {
      c.add(row.getFirst());
      sub.add(row.subList(1, n));
    }
    // the first column of the Toeplitz matrix: 1, −a, −R·C, −R·A₁·C, …, n + 1 entries
    List<Expr> column = new ArrayList<>(List.of(Num.ONE, en.neg(top)));
    List<Expr> v = c;
    for (int k = 0; k < n - 1; k++) {
      column.add(en.neg(dot(r, v, en)));
      if (k < n - 2) {
        v = times(sub, v, en);
      }
    }
    List<Expr> inner = coefficients(sub, en);
    List<Expr> out = new ArrayList<>();
    for (int i = 0; i <= n; i++) {
      Expr total = Num.ZERO;
      for (int j = 0; j <= Math.min(i, n - 1); j++) {
        total = en.add(total, en.mul(column.get(i - j), inner.get(j)));
      }
      out.add(total);
    }
    return out;
  }

  private static Expr dot(List<Expr> u, List<Expr> v, Entries en) {
    Expr total = Num.ZERO;
    for (int k = 0; k < u.size(); k++) {
      total = en.add(total, en.mul(u.get(k), v.get(k)));
    }
    return total;
  }

  private static List<Expr> times(List<List<Expr>> m, List<Expr> v, Entries en) {
    List<Expr> out = new ArrayList<>();
    for (List<Expr> row : m) {
      out.add(dot(row, v, en));
    }
    return out;
  }
}
