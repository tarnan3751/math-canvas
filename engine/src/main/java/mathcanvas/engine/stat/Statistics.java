package mathcanvas.engine.stat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;

/**
 * The statistics builtins ({@code docs/language.md} §14.25) on evaluated arguments: the descriptive
 * statistics ({@link Descriptive}) and the probability distributions ({@link Distributions}). An
 * argument that may still stand for data or a number (it holds a symbol) leaves the call as it is;
 * an argument of the wrong kind is an {@link IllegalArgumentException} with the language's message.
 * A value the {@link mathcanvas.engine.provider.Distribute} provider gave is numeric: the outcome
 * says so, and its step is {@code numeric.distribution}.
 */
public final class Statistics {

  /** The functions this class evaluates. */
  public static final Set<String> FUNCTIONS = names();

  /** The rule set of a numeric value's step. */
  public static final String NUMERIC_SET = "numeric";

  /** The numeric value's step, narrated by {@code numeric.distribution}. */
  public static final String NUMERIC_STEP = "distribution";

  /**
   * The step of a call the provider gave no value for, narrated by {@code
   * numeric.distribution-none}.
   */
  public static final String NUMERIC_NONE = "distribution-none";

  private static Set<String> names() {
    Set<String> out = new TreeSet<>(Descriptive.NAMES);
    out.addAll(Distributions.NAMES);
    return Set.copyOf(out);
  }

  /**
   * A value and whether the provider gave it.
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
    return apply(call, ctx, false);
  }

  /**
   * Evaluates a call whose arguments are evaluated, a discrete law past its exact bound through the
   * provider when {@code force} is set (under {@code numeric} or a decimal format).
   *
   * @param call the call
   * @param ctx the assumptions, precision, trace, numerics, providers and cancellation
   * @param force whether a discrete law past its exact bound is evaluated numerically
   * @return the value, its steps and whether it is numeric; the call itself when it stays
   * @throws IllegalArgumentException with the language's message for a wrong argument
   */
  public static Outcome apply(Apply call, Context ctx, boolean force) {
    String name = call.head().name();
    List<Expr> args = call.args();
    Expr raw = Canonical.of(call, ctx.precision(), ctx.cancellation());
    if (Descriptive.NAMES.contains(name)) {
      return Descriptive.apply(name, args, ctx)
          .map(v -> new Outcome(new Rewriter.Result(v, Trace.NONE), false))
          .orElseGet(() -> new Outcome(new Rewriter.Result(raw, Trace.NONE), false));
    }
    Optional<Distributions.Value> v;
    try {
      v = Distributions.apply(name, args, ctx, force);
    } catch (Distributions.NoConvergence none) {
      Trace note =
          ctx.traceEnabled()
              ? new Trace(
                  raw,
                  raw,
                  List.of(
                      new Rewrite(raw, raw, NUMERIC_SET, NUMERIC_NONE, Path.ROOT, Bindings.EMPTY)),
                  List.of())
              : Trace.NONE;
      return new Outcome(new Rewriter.Result(raw, note), false);
    }
    if (v.isEmpty()) {
      return new Outcome(new Rewriter.Result(raw, Trace.NONE), false);
    }
    Expr value = v.get().value();
    if (!v.get().numeric()) {
      return new Outcome(new Rewriter.Result(value, Trace.NONE), false);
    }
    return new Outcome(new Rewriter.Result(value, step(raw, value, ctx)), true);
  }

  /**
   * The numeric value of a call over numbers that evaluation left (a discrete law past its exact
   * bound), through the provider, for {@code numeric}.
   *
   * @param call the call, its arguments numbers
   * @param ctx the precision, numerics and cancellation
   * @return the value, or empty when there is none to give
   * @throws IllegalArgumentException with the language's message for a wrong argument
   */
  public static Optional<Expr> numeric(Apply call, Context ctx) {
    String name = call.head().name();
    if (!Distributions.NAMES.contains(name)) {
      return Optional.empty();
    }
    try {
      return Distributions.apply(name, call.args(), ctx, true).map(Distributions.Value::value);
    } catch (Distributions.NoConvergence none) {
      return Optional.empty();
    }
  }

  private static Trace step(Expr before, Expr after, Context ctx) {
    if (!ctx.traceEnabled()) {
      return Trace.NONE;
    }
    Bindings digits =
        new Bindings(
            List.of(
                new Bindings.Binding(
                    "digits_",
                    Num.of(Math.min(ctx.precision().getPrecision(), Distributions.DIGITS)),
                    null,
                    null)));
    Rewrite r = new Rewrite(before, after, NUMERIC_SET, NUMERIC_STEP, Path.ROOT, digits);
    return new Trace(before, after, List.of(r), List.of());
  }
}
