package mathcanvas.engine.linalg;

import static mathcanvas.engine.linalg.Recorder.values;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;

/**
 * Determinants, by cofactor expansion along the first row or by fraction-free elimination (Bareiss,
 * "Sylvester's identity and multistep integer-preserving Gaussian elimination", Math. Comp. 22
 * (1968)). Cofactor expansion shows the minors as {@code det} calls, then their values ({@code
 * det.cofactor}, {@code det.minors}); a 1×1 or 2×2 matrix is one step, {@code det.formula}.
 * Elimination records each row swap and each row's update ({@code det.swap}, {@code det.eliminate},
 * a symbolic pivot's condition with it), and the value is the last pivot with the sign of the swaps
 * ({@code det.pivots}); a column with no pivot gives 0 ({@code det.zero-column}).
 */
final class Determinants {

  /** The rule set of the steps. */
  static final String RULE_SET = "det";

  /** The largest matrix expanded by cofactors. */
  static final int MAX_COFACTOR = 8;

  /** The largest matrix the default method expands by cofactors. */
  static final int DEFAULT_COFACTOR = 3;

  private Determinants() {}

  /** The method of {@code det(M, method)}. */
  enum Method {
    COFACTOR,
    ELIMINATION
  }

  /** The determinant of a square matrix, with its steps. */
  static Expr det(Matrix m, Method method, Entries en, Recorder steps) {
    int n = m.rows().size();
    if (n <= 2) {
      Expr value = small(Entries.rows(m), en);
      steps.step(RULE_SET, "formula", value, values());
      return value;
    }
    if (method == Method.COFACTOR) {
      if (n > MAX_COFACTOR) {
        throw new IllegalArgumentException(
            "cofactor expansion is limited to " + MAX_COFACTOR + "×" + MAX_COFACTOR);
      }
      return cofactor(m, en, steps);
    }
    return elimination(Entries.rows(m), en, steps);
  }

  /** The determinant, without steps: cofactors up to 3×3, elimination above. */
  static Expr value(List<List<Expr>> a, Entries en) {
    int n = a.size();
    if (n <= 2) {
      return small(a, en);
    }
    if (n == 3) {
      return expansion(a, en);
    }
    return bareiss(a, en, null);
  }

  private static Expr small(List<List<Expr>> a, Entries en) {
    if (a.size() == 1) {
      return a.getFirst().getFirst();
    }
    return en.sub(
        en.mul(a.get(0).get(0), a.get(1).get(1)), en.mul(a.get(0).get(1), a.get(1).get(0)));
  }

  private static Expr cofactor(Matrix m, Entries en, Recorder steps) {
    List<List<Expr>> a = Entries.rows(m);
    List<Expr> terms = new ArrayList<>();
    for (int j = 0; j < a.size(); j++) {
      Expr entry = a.getFirst().get(j);
      if (en.isZero(entry) == Tri.TRUE) {
        continue;
      }
      Expr minor = en.matrix(minor(a, j));
      Expr sign = j % 2 == 0 ? Num.ONE : Num.MINUS_ONE;
      terms.add(Product.of(sign, entry, new Apply(Symbol.of("det"), List.of(minor))));
    }
    Expr expanded =
        terms.isEmpty() ? Num.ZERO : terms.size() == 1 ? terms.getFirst() : Sum.of(terms);
    steps.step(RULE_SET, "cofactor", expanded, values());
    Expr value = expansion(a, en);
    steps.step(RULE_SET, "minors", value, values());
    return value;
  }

  /** The cofactor expansion along the first row, each minor's determinant by the same rule. */
  private static Expr expansion(List<List<Expr>> a, Entries en) {
    if (a.size() <= 2) {
      return small(a, en);
    }
    Expr total = Num.ZERO;
    for (int j = 0; j < a.size(); j++) {
      Expr entry = a.getFirst().get(j);
      if (en.isZero(entry) == Tri.TRUE) {
        continue;
      }
      Expr term = en.mul(entry, expansion(minor(a, j), en));
      total = j % 2 == 0 ? en.add(total, term) : en.sub(total, term);
    }
    return total;
  }

  /** The matrix without the first row and column {@code j}. */
  private static List<List<Expr>> minor(List<List<Expr>> a, int j) {
    List<List<Expr>> out = new ArrayList<>();
    for (List<Expr> row : a.subList(1, a.size())) {
      List<Expr> r = new ArrayList<>(row);
      r.remove(j);
      out.add(r);
    }
    return out;
  }

  private static Expr elimination(List<List<Expr>> a, Entries en, Recorder steps) {
    return bareiss(a, en, steps);
  }

  /**
   * Bareiss elimination in place: each row below the pivot becomes {@code (p·row − e·pivot row) /
   * p_prev}, the division exact; the determinant is the last pivot, negated for an odd number of
   * swaps. With {@code steps} null nothing is recorded.
   */
  private static Expr bareiss(List<List<Expr>> a, Entries en, Recorder steps) {
    int n = a.size();
    Expr previous = Num.ONE;
    int swaps = 0;
    for (int k = 0; k < n - 1; k++) {
      int p = -1;
      for (int i = k; i < n; i++) {
        if (en.isZero(a.get(i).get(k)) != Tri.TRUE) {
          p = i;
          break;
        }
      }
      if (p < 0) {
        if (steps != null) {
          steps.step(RULE_SET, "zero-column", Num.ZERO, values("j", Num.of(k + 1)));
        }
        return Num.ZERO;
      }
      if (p != k) {
        List<Expr> t = a.get(k);
        a.set(k, a.get(p));
        a.set(p, t);
        swaps++;
        if (steps != null) {
          steps.step(
              RULE_SET,
              "swap",
              en.matrix(a),
              values("i", RowReduction.row(k), "j", RowReduction.row(p)));
        }
      }
      Expr pivot = a.get(k).get(k);
      Relation condition = en.nonZero(pivot);
      for (int i = k + 1; i < n; i++) {
        List<Expr> row = a.get(i);
        Expr e = row.get(k);
        for (int j = k + 1; j < n; j++) {
          Expr cross = en.sub(en.mul(pivot, row.get(j)), en.mul(e, a.get(k).get(j)));
          row.set(j, en.div(cross, previous));
        }
        row.set(k, Num.ZERO);
        Entries.count(a);
        if (steps != null) {
          steps.step(
              RULE_SET,
              "eliminate",
              en.matrix(a),
              values(
                  "i",
                  RowReduction.row(i),
                  "j",
                  RowReduction.row(k),
                  "p",
                  pivot,
                  "q",
                  previous,
                  "conditions",
                  condition == null ? null : SetExpr.of(condition)));
        }
      }
      previous = pivot;
    }
    Expr last = a.get(n - 1).get(n - 1);
    Expr value = swaps % 2 == 0 ? last : en.neg(last);
    if (steps != null) {
      steps.step(RULE_SET, "pivots", value, values("s", Num.of(swaps), "p", last));
    }
    return value;
  }
}
