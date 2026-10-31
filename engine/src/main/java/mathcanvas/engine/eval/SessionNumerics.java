package mathcanvas.engine.eval;

import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.builtin.Builtin;
import mathcanvas.engine.builtin.Builtins;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.rule.Numerics;

/**
 * The numeric values the evaluator supplies to the operations: the {@code numeric} pass ({@link
 * NumericForm}), every function of the language through the inexact path, in radians; and the
 * builtin functions of numbers evaluated as evaluation does ({@link Functions}), in radians.
 *
 * @param halfTurn the session's half turn in its angle unit, 0 in radians ({@link
 *     AngleMode#halfTurn})
 * @param cancellation the statement's request to stop
 */
record SessionNumerics(int halfTurn, Cancellation cancellation) implements Numerics {

  @Override
  public Expr exact(Expr e, MathContext mc) {
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    List<Expr> out = new ArrayList<>(ops.size());
    for (Expr o : ops) {
      out.add(exact(o, mc));
    }
    Expr rebuilt = Canonical.of(Operands.rebuild(e, out), mc, cancellation);
    if (rebuilt instanceof Apply a
        && Builtins.lookup(a.head().name())
            .map(b -> b.kind() == Builtin.Kind.FUNCTION && b.accepts(a.args().size()))
            .orElse(false)
        && a.args().stream().allMatch(arg -> arg instanceof Num)) {
      try {
        return Functions.apply(a.head(), a.args(), mc, AngleMode.RADIANS, a, cancellation);
      } catch (EvaluationException refused) {
        return rebuilt;
      }
    }
    return rebuilt;
  }

  @Override
  public Optional<Numeric> value(Expr e, MathContext mc) {
    try {
      return NumericForm.numeric(e, mc.getPrecision(), e, cancellation) instanceof Num n
          ? Optional.of(n.value())
          : Optional.empty();
    } catch (EvaluationException | ArithmeticException outOfRange) {
      return Optional.empty();
    }
  }
}
