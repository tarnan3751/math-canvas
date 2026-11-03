package mathcanvas.engine.stat;

import java.util.List;
import java.util.Set;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;

/**
 * The statistics builtins ({@code docs/language.md} §14.25) on evaluated arguments: the descriptive
 * statistics ({@link Descriptive}). An argument that may still stand for data or a number (it holds
 * a symbol) leaves the call as it is; an argument of the wrong kind is an {@link
 * IllegalArgumentException} with the language's message.
 */
public final class Statistics {

  /** The functions this class evaluates. */
  public static final Set<String> FUNCTIONS = Set.copyOf(Descriptive.NAMES);

  /**
   * A value and whether it is numeric.
   *
   * @param result the value and its steps
   * @param numeric whether the value is numeric
   */
  public record Outcome(Rewriter.Result result, boolean numeric) {}

  private Statistics() {}

  /**
   * Evaluates a call whose arguments are evaluated.
   *
   * @param call the call
   * @param ctx the assumptions, precision, trace, numerics, providers and cancellation
   * @return the value, its steps and whether it is numeric; the call itself when it stays
   * @throws IllegalArgumentException with the language's message for a wrong argument
   */
  public static Outcome apply(Apply call, Context ctx) {
    String name = call.head().name();
    List<Expr> args = call.args();
    Expr value =
        Descriptive.apply(name, args, ctx)
            .orElseGet(() -> Canonical.of(call, ctx.precision(), ctx.cancellation()));
    return new Outcome(new Rewriter.Result(value, Trace.NONE), false);
  }
}
