package mathcanvas.engine.linalg;

import static mathcanvas.engine.linalg.Recorder.values;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;

/**
 * Inverses: a 1×1 or 2×2 matrix by the adjugate over the determinant ({@code inverse.adjugate}), a
 * larger one by Gauss–Jordan elimination on {@code [A | I]} ({@code inverse.augment}, the row
 * operations of {@link RowReduction}, then {@code inverse.read}, the right half). A matrix known to
 * be singular is the error "the matrix is singular"; a symbolic determinant that is not known to be
 * zero is assumed not to be, and the adjugate or the reading step says {@code det ≠ 0}.
 */
final class Inverses {

  /** The rule set of the steps. */
  static final String RULE_SET = "inverse";

  /** The message for a singular matrix. */
  static final String SINGULAR = "the matrix is singular";

  private Inverses() {}

  /** The inverse of a square matrix, with its steps. */
  static Expr inverse(Matrix m, Entries en, Recorder steps) {
    int n = m.rows().size();
    List<List<Expr>> a = Entries.rows(m);
    if (n <= 2) {
      Expr det = Determinants.value(a, en);
      if (en.isZero(det) == Tri.TRUE) {
        throw new IllegalArgumentException(SINGULAR);
      }
      List<List<Expr>> adj =
          n == 1
              ? List.of(List.of(Num.ONE))
              : List.of(
                  List.of(a.get(1).get(1), en.neg(a.get(0).get(1))),
                  List.of(en.neg(a.get(1).get(0)), a.get(0).get(0)));
      List<List<Expr>> out = new ArrayList<>();
      for (List<Expr> row : adj) {
        List<Expr> r = new ArrayList<>();
        for (Expr e : row) {
          r.add(en.div(e, det));
        }
        out.add(r);
      }
      Entries.count(out);
      Expr value = en.matrix(out);
      Relation condition = en.nonZero(det);
      steps.step(
          RULE_SET,
          "adjugate",
          value,
          values("d", det, "conditions", condition == null ? null : SetExpr.of(condition)));
      return value;
    }
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        a.get(i).add(i == j ? Num.ONE : Num.ZERO);
      }
    }
    steps.step(RULE_SET, "augment", en.matrix(a), values());
    RowReduction.Reduced r = RowReduction.reduce(a, n, en, steps);
    if (r.pivots().size() < n) {
      throw new IllegalArgumentException(SINGULAR);
    }
    List<List<Expr>> out = new ArrayList<>();
    for (List<Expr> row : r.rows()) {
      out.add(new ArrayList<>(row.subList(n, 2 * n)));
    }
    Entries.count(out);
    Expr value = en.matrix(out);
    // a symbolic pivot assumed the elimination could go on; what the inverse needs is det ≠ 0
    Relation condition =
        r.conditions().isEmpty() ? null : en.nonZero(Determinants.value(Entries.rows(m), en));
    steps.step(
        RULE_SET,
        "read",
        value,
        values("conditions", condition == null ? null : SetExpr.of(condition)));
    return value;
  }
}
