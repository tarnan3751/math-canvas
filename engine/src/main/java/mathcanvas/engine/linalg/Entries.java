package mathcanvas.engine.linalg;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.eval.Evaluator;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.RewriteLimitException;
import mathcanvas.engine.simplify.Simplifier;

/**
 * The field arithmetic of matrix entries: numbers on the tower's fast path, anything else through
 * {@link Algebra#cancel}, so a symbolic entry stays a rational function in its kernels in lowest
 * terms. An entry is zero when it is the number 0 after cancelling (which folds {@code (√2)² − 2}),
 * when the assumptions say so, or when the simplifier takes it to 0 ({@code |b|² − b²} for a real
 * {@code b}); a value hidden among independent kernels is not seen, as in the linear systems of the
 * solver. A decimal matrix's null space may take a number below a tolerance as zero.
 */
final class Entries {

  private final Context ctx;

  /** Below this magnitude a number is zero; null for exact zero tests. */
  private final RealNumeric tolerance;

  Entries(Context ctx) {
    this(ctx, null);
  }

  /** Entries whose numbers below {@code tolerance} in both parts are zero. */
  Entries(Context ctx, RealNumeric tolerance) {
    this.ctx = ctx;
    this.tolerance = tolerance;
  }

  Context context() {
    return ctx;
  }

  Expr add(Expr a, Expr b) {
    if (a instanceof Num x && b instanceof Num y) {
      return number(Num.of(x.value().add(y.value(), ctx.precision())));
    }
    return cancel(Sum.of(a, b));
  }

  Expr sub(Expr a, Expr b) {
    return add(a, neg(b));
  }

  Expr neg(Expr a) {
    if (a instanceof Num x) {
      return number(Num.of(x.value().negate()));
    }
    return cancel(Exprs.neg(a));
  }

  Expr mul(Expr a, Expr b) {
    if (a instanceof Num x && b instanceof Num y) {
      return number(Num.of(x.value().multiply(y.value(), ctx.precision())));
    }
    return cancel(Product.of(a, b));
  }

  /** {@code a / b}, for {@code b} not known to be zero. */
  Expr div(Expr a, Expr b) {
    if (a instanceof Num x && b instanceof Num y) {
      return number(Num.of(x.value().divide(y.value(), ctx.precision())));
    }
    return cancel(Exprs.div(a, b));
  }

  /** Whether an entry is zero: by its value, after cancelling, or by the assumptions. */
  Tri isZero(Expr e) {
    ctx.cancellation().check();
    if (!(e instanceof Num)) {
      e = cancel(e);
    }
    if (e instanceof Num n) {
      if (tolerance != null
          && n.value().re().abs().compareTo(tolerance) <= 0
          && n.value().im().abs().compareTo(tolerance) <= 0) {
        return Tri.TRUE;
      }
      return n.value().isZero() ? Tri.TRUE : Tri.FALSE;
    }
    if (ctx.assumptions().isZero(e) == Tri.TRUE || simplifiesToZero(e)) {
      return Tri.TRUE;
    }
    return ctx.assumptions().isNonZero(e) == Tri.TRUE ? Tri.FALSE : Tri.UNKNOWN;
  }

  /**
   * Whether the simplifier takes {@code e} to 0, which sees what cancelling alone does not, such as
   * {@code |b|² − b²} for a real {@code b}; a simplification past its cap decides nothing.
   */
  private boolean simplifiesToZero(Expr e) {
    try {
      return Simplifier.simplify(e, ctx.withTrace(false)).expr().equals(Num.ZERO);
    } catch (RewriteLimitException tooLong) {
      return false;
    }
  }

  /** The condition {@code e ≠ 0} a division by a symbolic entry assumes, or null. */
  Relation nonZero(Expr e) {
    if (e instanceof Num || isZero(e) == Tri.FALSE) {
      return null;
    }
    return new Relation(Relation.Op.NE, e, Num.ZERO);
  }

  Expr canon(Expr e) {
    return Canonical.of(e, ctx.precision(), ctx.cancellation());
  }

  /** {@code e} canonical and cancelled: a rational function of its kernels in lowest terms. */
  Expr cancel(Expr e) {
    ctx.cancellation().check();
    Expr c = canon(e);
    return c instanceof Num ? c : built(Algebra.cancel(c, ctx));
  }

  /** A symbolic entry, refused past {@link Evaluator#MAX_NODES} nodes. */
  private static Expr built(Expr e) {
    nodes(e, Evaluator.MAX_NODES);
    return e;
  }

  /**
   * Counts the nodes of the rows, per occurrence as the evaluator does, entry by entry: past {@link
   * Evaluator#MAX_NODES} the matrix is "result too large", refused as soon as the count passes the
   * cap rather than when the value is finished.
   *
   * @throws IllegalArgumentException "result too large"
   */
  static void count(List<List<Expr>> rows) {
    long left = Evaluator.MAX_NODES;
    for (List<Expr> row : rows) {
      for (Expr e : row) {
        left -= nodes(e, left);
      }
    }
  }

  /** The nodes of {@code e}; "result too large" when they pass {@code limit}. */
  private static long nodes(Expr e, long limit) {
    long count = 0;
    Deque<Expr> todo = new ArrayDeque<>();
    todo.push(e);
    while (!todo.isEmpty()) {
      if (++count > limit) {
        throw new IllegalArgumentException(TOO_LARGE);
      }
      for (Expr op : Operands.of(todo.pop())) {
        todo.push(op);
      }
    }
    return count;
  }

  /** The message for a matrix past the size cap. */
  static final String TOO_LARGE = "result too large";

  private Expr number(Num n) {
    return canon(n);
  }

  /** The rows of a matrix, as mutable lists. */
  static List<List<Expr>> rows(Matrix m) {
    List<List<Expr>> out = new ArrayList<>();
    for (List<Expr> row : m.rows()) {
      out.add(new ArrayList<>(row));
    }
    return out;
  }

  /** The canonical matrix of these rows. */
  Expr matrix(List<List<Expr>> rows) {
    return canon(Matrix.of(rows));
  }
}
