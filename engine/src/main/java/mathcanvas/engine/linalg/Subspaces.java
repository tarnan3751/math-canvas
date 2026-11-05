package mathcanvas.engine.linalg;

import static mathcanvas.engine.linalg.Recorder.values;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.parse.Brackets;

/**
 * What the reduced row echelon form reads off: the rank (the pivot count, {@code rank.count}), a
 * basis of the null space (one vector per free column, {@code nullspace.basis}), a basis of the
 * column space (the pivot columns of the matrix itself, {@code colspace.pivots}) and the solutions
 * of {@code A x = b} ({@code linsolve.augment}, the row operations, then {@code linsolve.unique},
 * {@code linsolve.general} with free parameters {@code t₁, t₂, …}, or {@code linsolve.inconsistent}
 * with the empty set). A list of vectors of one length is a matrix by the bracket rule, so a basis
 * of two or more vectors is the matrix whose rows they are.
 */
final class Subspaces {

  private Subspaces() {}

  static Expr rank(Matrix m, Entries en, Recorder steps) {
    RowReduction.Reduced r = RowReduction.reduce(Entries.rows(m), columns(m), en, steps);
    Expr value = Num.of(r.pivots().size());
    steps.step("rank", "count", value, conditions(r));
    return value;
  }

  static Expr rref(Matrix m, Entries en, Recorder steps) {
    RowReduction.Reduced r = RowReduction.reduce(Entries.rows(m), columns(m), en, steps);
    Expr value = en.matrix(r.rows());
    steps.step(RowReduction.RULE_SET, "done", value, values());
    return value;
  }

  static Expr nullspace(Matrix m, Entries en, Recorder steps) {
    Kernel k = kernel(m, en, steps);
    Expr value = en.canon(Brackets.of(k.basis().stream().map(v -> (Expr) Vector.of(v)).toList()));
    steps.step("nullspace", "basis", value, conditions(k.conditions()));
    return value;
  }

  /**
   * A basis of the null space and the conditions its symbolic pivots assume.
   *
   * @param basis one vector per free column, with 1 there
   * @param conditions the conditions {@code p ≠ 0}
   */
  record Kernel(List<List<Expr>> basis, List<Expr> conditions) {}

  /** The null space of {@code m}, its row operations recorded. */
  static Kernel kernel(Matrix m, Entries en, Recorder steps) {
    int n = columns(m);
    RowReduction.Reduced r = RowReduction.reduce(Entries.rows(m), n, en, steps);
    List<List<Expr>> basis = new ArrayList<>();
    for (int f = 0; f < n; f++) {
      if (r.pivots().contains(f)) {
        continue;
      }
      List<Expr> v = new ArrayList<>();
      for (int j = 0; j < n; j++) {
        v.add(j == f ? Num.ONE : Num.ZERO);
      }
      for (int k = 0; k < r.pivots().size(); k++) {
        v.set(r.pivots().get(k), en.neg(r.rows().get(k).get(f)));
      }
      basis.add(v);
    }
    return new Kernel(basis, r.conditions());
  }

  static Expr colspace(Matrix m, Entries en, Recorder steps) {
    RowReduction.Reduced r = RowReduction.reduce(Entries.rows(m), columns(m), en, steps);
    List<Expr> basis = new ArrayList<>();
    for (int c : r.pivots()) {
      List<Expr> column = new ArrayList<>();
      for (List<Expr> row : m.rows()) {
        column.add(row.get(c));
      }
      basis.add(Vector.of(column));
    }
    Expr value = en.canon(Brackets.of(basis));
    steps.step(
        "colspace",
        "pivots",
        value,
        values("columns", SetExpr.of(r.pivots().stream().map(c -> (Expr) Num.of(c + 1)).toList())));
    return value;
  }

  /** The message for arguments of the wrong shapes. */
  static final String LINSOLVE_SHAPES = "`linsolve` needs a matrix and a vector of matching height";

  static Expr linsolve(Matrix a, Expr b, Entries en, Recorder steps) {
    int m = a.rows().size();
    int n = columns(a);
    List<List<Expr>> right;
    if (b instanceof Vector v && v.items().size() == m) {
      right = new ArrayList<>();
      for (Expr e : v.items()) {
        right.add(List.of(e));
      }
    } else if (b instanceof Matrix bm && bm.rows().size() == m) {
      right = bm.rows();
    } else {
      throw new IllegalArgumentException(LINSOLVE_SHAPES);
    }
    int q = right.getFirst().size();
    List<List<Expr>> aug = Entries.rows(a);
    for (int i = 0; i < m; i++) {
      aug.get(i).addAll(right.get(i));
    }
    steps.step("linsolve", "augment", en.matrix(aug), values());
    RowReduction.Reduced r = RowReduction.reduce(aug, n, en, steps);
    int rank = r.pivots().size();
    List<Expr> unless = new ArrayList<>();
    for (int i = rank; i < m; i++) {
      for (int j = n; j < n + q; j++) {
        Expr rhs = r.rows().get(i).get(j);
        Tri zero = en.isZero(rhs);
        if (zero == Tri.FALSE) {
          return inconsistent(steps, List.of(), r);
        }
        if (zero == Tri.UNKNOWN) {
          // the system has no solution unless the right side vanishes
          unless.add(new Relation(Relation.Op.EQ, rhs, Num.ZERO));
        }
      }
    }
    if (!unless.isEmpty()) {
      return inconsistent(steps, unless, r);
    }
    List<Integer> free = new ArrayList<>();
    for (int j = 0; j < n; j++) {
      if (!r.pivots().contains(j)) {
        free.add(j);
      }
    }
    List<Expr> parameters = new ArrayList<>();
    List<List<Expr>> columns = new ArrayList<>();
    int next = 1;
    for (int k = 0; k < q; k++) {
      Expr[] x = new Expr[n];
      for (int f : free) {
        Symbol t;
        do {
          t = Symbol.of("t_" + next++);
        } while (occurs(a, t) || occurs(b, t));
        parameters.add(t);
        x[f] = t;
      }
      for (int i = rank - 1; i >= 0; i--) {
        List<Expr> row = r.rows().get(i);
        Expr value = row.get(n + k);
        for (int f : free) {
          value = en.sub(value, en.mul(row.get(f), x[f]));
        }
        x[r.pivots().get(i)] = value;
      }
      columns.add(List.of(x));
    }
    Entries.count(columns);
    Expr value;
    if (b instanceof Vector) {
      value = Vector.of(columns.getFirst());
    } else {
      List<List<Expr>> rows = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        List<Expr> row = new ArrayList<>();
        for (List<Expr> column : columns) {
          row.add(column.get(i));
        }
        rows.add(row);
      }
      value = Matrix.of(rows);
    }
    TreeMap<String, Expr> vs = conditions(r);
    if (!parameters.isEmpty()) {
      vs.put("free", SetExpr.of(parameters));
    }
    steps.step("linsolve", parameters.isEmpty() ? "unique" : "general", value, vs);
    return steps.current();
  }

  private static Expr inconsistent(Recorder steps, List<Expr> unless, RowReduction.Reduced r) {
    TreeMap<String, Expr> vs = conditions(r);
    if (!unless.isEmpty()) {
      vs.put("unless", SetExpr.of(unless));
    }
    steps.step("linsolve", "inconsistent", SetExpr.of(), vs);
    return steps.current();
  }

  static boolean occurs(Expr e, Symbol s) {
    if (e.equals(s)) {
      return true;
    }
    for (Expr op : Operands.of(e)) {
      if (occurs(op, s)) {
        return true;
      }
    }
    return false;
  }

  private static TreeMap<String, Expr> conditions(RowReduction.Reduced r) {
    return conditions(r.conditions());
  }

  /** The value {@code conditions}, the set of them, left out when there is none. */
  static TreeMap<String, Expr> conditions(List<Expr> conditions) {
    return values("conditions", conditions.isEmpty() ? null : SetExpr.of(conditions));
  }

  static int columns(Matrix m) {
    return m.rows().getFirst().size();
  }
}
