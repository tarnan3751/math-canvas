package mathcanvas.engine.linalg;

import static mathcanvas.engine.linalg.Recorder.values;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Symbol;

/**
 * Reduced row echelon form by textbook Gauss–Jordan elimination, deterministic so that step counts
 * match work done by hand: for each column in order, the pivot is the first row at or below the
 * current one whose entry is not known to be zero ({@code swap} when it is not the current row),
 * the row is scaled to pivot 1 ({@code scale}, skipped when the pivot is already 1), then every
 * other row with an entry in the column not known to be zero is cleared ({@code add}, one step per
 * row). There is no pivoting by magnitude. A symbolic pivot records the condition that it is not
 * zero on its {@code scale} step. Each step carries the matrix after the operation, in the rule set
 * {@code rref}.
 */
final class RowReduction {

  /** The rule set of the row operations. */
  static final String RULE_SET = "rref";

  /**
   * The reduced rows and what the reduction found.
   *
   * @param rows the reduced rows
   * @param pivots the pivot columns, in order
   * @param conditions the conditions {@code p ≠ 0} the symbolic pivots assume
   */
  record Reduced(List<List<Expr>> rows, List<Integer> pivots, List<Expr> conditions) {}

  private RowReduction() {}

  /**
   * Reduces {@code a} in place, pivoting in its first {@code columns} columns only (the left block
   * of an augmented matrix).
   */
  static Reduced reduce(List<List<Expr>> a, int columns, Entries en, Recorder steps) {
    int m = a.size();
    int width = a.getFirst().size();
    List<Integer> pivots = new ArrayList<>();
    List<Expr> conditions = new ArrayList<>();
    int r = 0;
    for (int c = 0; c < columns && r < m; c++) {
      int p = -1;
      for (int k = r; k < m; k++) {
        if (en.isZero(a.get(k).get(c)) != Tri.TRUE) {
          p = k;
          break;
        }
      }
      if (p < 0) {
        continue;
      }
      if (p != r) {
        List<Expr> t = a.get(r);
        a.set(r, a.get(p));
        a.set(p, t);
        steps.step(RULE_SET, "swap", en.matrix(a), values("i", row(r), "j", row(p)));
      }
      Expr pivot = a.get(r).get(c);
      Relation condition = en.nonZero(pivot);
      if (condition != null) {
        conditions.add(condition);
      }
      if (!pivot.equals(Num.ONE)) {
        List<Expr> scaled = a.get(r);
        for (int j = 0; j < width; j++) {
          if (j == c) {
            scaled.set(j, Num.ONE);
          } else if (!scaled.get(j).equals(Num.ZERO)) {
            scaled.set(j, en.div(scaled.get(j), pivot));
          }
        }
        Entries.count(a);
        steps.step(
            RULE_SET,
            "scale",
            en.matrix(a),
            values(
                "i",
                row(r),
                "k",
                pivot,
                "conditions",
                condition == null ? null : SetExpr.of(condition)));
      }
      for (int k = 0; k < m; k++) {
        Expr e = a.get(k).get(c);
        if (k == r || en.isZero(e) == Tri.TRUE) {
          continue;
        }
        List<Expr> target = a.get(k);
        for (int j = 0; j < width; j++) {
          Expr source = a.get(r).get(j);
          if (j == c) {
            target.set(j, Num.ZERO);
          } else if (!source.equals(Num.ZERO)) {
            // an exact zero in the pivot row leaves the entry exact
            target.set(j, en.sub(target.get(j), en.mul(e, source)));
          }
        }
        Entries.count(a);
        steps.step(RULE_SET, "add", en.matrix(a), values("i", row(k), "j", row(r), "k", e));
      }
      pivots.add(c);
      r++;
    }
    return new Reduced(a, List.copyOf(pivots), List.copyOf(conditions));
  }

  /** The row label {@code R_i} (printed {@code Rᵢ}) of the 0-based row {@code i}. */
  static Symbol row(int i) {
    return Symbol.of("R_" + (i + 1));
  }
}
