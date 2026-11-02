package mathcanvas.engine.linalg;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.algebra.Division;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.RealNumeric;

/**
 * The field {@code ℚ[x]/(q)} of an irreducible {@code q}: its elements are the polynomials of
 * degree below {@code q}'s, and the root of {@code q} is {@code x}. The null space of {@code A −
 * xI} over it holds, at once and exactly, the eigenvectors of every conjugate root of {@code q}, so
 * no zero test ever meets a radical or a cosine of a third of an angle; the inverse is by the
 * extended Euclidean algorithm.
 */
final class Modular {

  private final Polynomial q;
  private final Cancellation cancellation;

  Modular(Polynomial q, Cancellation cancellation) {
    this.q = q;
    this.cancellation = cancellation;
  }

  Polynomial reduce(Polynomial p) {
    return Division.divide(p, q).remainder();
  }

  Polynomial mul(Polynomial a, Polynomial b) {
    return reduce(a.multiply(b));
  }

  Polynomial inverse(Polynomial a) {
    Polynomial r0 = q;
    Polynomial r1 = reduce(a);
    Polynomial s0 = Polynomial.ZERO;
    Polynomial s1 = Polynomial.ONE;
    while (!r1.isZero()) {
      Division.Result d = Division.divide(r0, r1);
      Polynomial s = s0.subtract(d.quotient().multiply(s1));
      r0 = r1;
      r1 = d.remainder();
      s0 = s1;
      s1 = s;
    }
    // r0 is the gcd, a non-zero constant since q is irreducible and a is not 0 modulo q
    RealNumeric c = r0.leadingCoefficient();
    return reduce(s0.multiply((RealNumeric) Int.ONE.divide(c, java.math.MathContext.UNLIMITED)));
  }

  /**
   * A basis of the null space of {@code a} over the field by Gauss–Jordan elimination, each vector
   * with 1 at its free column.
   */
  List<List<Polynomial>> nullspace(List<List<Polynomial>> a) {
    int m = a.size();
    int n = a.getFirst().size();
    List<List<Polynomial>> rows = new ArrayList<>();
    for (List<Polynomial> row : a) {
      List<Polynomial> r = new ArrayList<>();
      for (Polynomial p : row) {
        r.add(reduce(p));
      }
      rows.add(r);
    }
    List<Integer> pivots = new ArrayList<>();
    int r = 0;
    for (int c = 0; c < n && r < m; c++) {
      int p = -1;
      for (int k = r; k < m; k++) {
        if (!rows.get(k).get(c).isZero()) {
          p = k;
          break;
        }
      }
      if (p < 0) {
        continue;
      }
      List<Polynomial> t = rows.get(r);
      rows.set(r, rows.get(p));
      rows.set(p, t);
      Polynomial inv = inverse(rows.get(r).get(c));
      List<Polynomial> pivot = rows.get(r);
      for (int j = 0; j < n; j++) {
        pivot.set(j, mul(pivot.get(j), inv));
      }
      for (int k = 0; k < m; k++) {
        Polynomial e = rows.get(k).get(c);
        if (k == r || e.isZero()) {
          continue;
        }
        cancellation.check();
        List<Polynomial> target = rows.get(k);
        for (int j = 0; j < n; j++) {
          target.set(j, reduce(target.get(j).subtract(e.multiply(pivot.get(j)))));
        }
      }
      pivots.add(c);
      r++;
    }
    List<List<Polynomial>> basis = new ArrayList<>();
    for (int f = 0; f < n; f++) {
      if (pivots.contains(f)) {
        continue;
      }
      List<Polynomial> v = new ArrayList<>();
      for (int j = 0; j < n; j++) {
        v.add(j == f ? Polynomial.ONE : Polynomial.ZERO);
      }
      for (int k = 0; k < pivots.size(); k++) {
        v.set(pivots.get(k), rows.get(k).get(f).negate());
      }
      basis.add(v);
    }
    return basis;
  }
}
