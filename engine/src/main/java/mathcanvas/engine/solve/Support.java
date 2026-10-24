package mathcanvas.engine.solve;

import java.math.MathContext;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.simplify.Simplifier;

/** Small helpers the solving methods share. */
final class Support {

  private static final MathContext EXACT = MathContext.UNLIMITED;

  private Support() {}

  /** A method that does not apply: the reason is the name of the step that says why. */
  static final class Declined extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** The step's name. */
    final String reason;

    /** The values its narration prints. */
    final transient Bindings bindings;

    Declined(String reason, Bindings bindings) {
      super(reason, null, false, false);
      this.reason = reason;
      this.bindings = bindings;
    }

    Declined(String reason) {
      this(reason, Bindings.EMPTY);
    }
  }

  static Expr canon(Expr e, Context ctx) {
    return Canonical.of(e, ctx.precision());
  }

  /** The standard simplifier's result, untraced. */
  static Expr simplify(Expr e, Context ctx) {
    return Simplifier.simplify(e, ctx.withTrace(false)).expr();
  }

  /** The canonical equation {@code left = right}. */
  static Expr equation(Expr left, Expr right, Context ctx) {
    return canon(new Relation(Relation.Op.EQ, left, right), ctx);
  }

  /** The canonical set of {@code items}. */
  static Expr set(List<Expr> items, Context ctx) {
    return canon(new SetExpr(items), ctx);
  }

  /** Whether the symbol {@code x} occurs anywhere in {@code e}. */
  static boolean contains(Expr e, Symbol x) {
    Deque<Expr> todo = new ArrayDeque<>();
    todo.push(e);
    while (!todo.isEmpty()) {
      Expr n = todo.pop();
      if (n.equals(x)) {
        return true;
      }
      for (Expr op : Operands.of(n)) {
        todo.push(op);
      }
    }
    return false;
  }

  /** Whether {@code e} has no symbol in it (the heads of applications aside). */
  static boolean isConstant(Expr e) {
    Deque<Expr> todo = new ArrayDeque<>();
    todo.push(e);
    while (!todo.isEmpty()) {
      Expr n = todo.pop();
      if (n instanceof Symbol) {
        return false;
      }
      for (Expr op : Operands.of(n)) {
        todo.push(op);
      }
    }
    return true;
  }

  /** Whether {@code e} holds an inexact number. */
  static boolean hasInexact(Expr e) {
    Deque<Expr> todo = new ArrayDeque<>();
    todo.push(e);
    while (!todo.isEmpty()) {
      Expr n = todo.pop();
      if (n instanceof Num num && !num.value().isExact()) {
        return true;
      }
      for (Expr op : Operands.of(n)) {
        todo.push(op);
      }
    }
    return false;
  }

  // Exact rational arithmetic.

  static RealNumeric q(long n) {
    return Int.of(n);
  }

  static RealNumeric add(RealNumeric a, RealNumeric b) {
    return (RealNumeric) a.add(b, EXACT);
  }

  static RealNumeric sub(RealNumeric a, RealNumeric b) {
    return (RealNumeric) a.subtract(b, EXACT);
  }

  static RealNumeric mul(RealNumeric a, RealNumeric b) {
    return (RealNumeric) a.multiply(b, EXACT);
  }

  static RealNumeric div(RealNumeric a, RealNumeric b) {
    return (RealNumeric) a.divide(b, EXACT);
  }

  static RealNumeric pow(RealNumeric a, int n) {
    return (RealNumeric) a.pow(n, EXACT);
  }

  static Num num(RealNumeric r) {
    return Num.of(r);
  }
}
