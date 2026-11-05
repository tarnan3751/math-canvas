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
 * statistics ({@link Descriptive}), the probability distributions ({@link Distributions}) and
 * regression ({@link Regression}). An argument that may still stand for data or a number (it holds
 * a symbol) leaves the call as it is; an argument of the wrong kind is an {@link
 * IllegalArgumentException} with the language's message. A value the {@link
 * mathcanvas.engine.provider.Distribute} provider gave is numeric: the outcome says so, and its
 * step is {@code numeric.distribution}.
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
    out.addAll(Regression.NAMES);
    out.addAll(Tests.FUNCTIONS);
    out.addAll(Intervals.NAMES);
    out.remove("tinterval2");
    return Set.copyOf(out);
  }

  /** The tests that are commands: their trailing words are read as written. */
  public static final Set<String> COMMANDS = Tests.COMMANDS;

  /** The alternatives of a test, as written: {@code two}, {@code less}, {@code greater}. */
  public static final Set<String> ALTERNATIVES = Tests.ALTERNATIVES;

  /** The rule sets of the narrated steps, which {@link StatisticsNarrator} owns. */
  public static final Set<String> RULE_SETS = Set.of(Regression.RULE_SET, Tests.RULE_SET);

  /** Every narrated step, {@code <rule set>.<rule>}. */
  public static final Set<String> STEPS = steps();

  private static Set<String> steps() {
    Set<String> out = new TreeSet<>();
    Regression.STEPS.forEach(s -> out.add(Regression.RULE_SET + "." + s));
    Tests.STEPS.forEach(s -> out.add(Tests.RULE_SET + "." + s));
    Intervals.STEPS.forEach(s -> out.add(Tests.RULE_SET + "." + s));
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
    if (Tests.FUNCTIONS.contains(name) || Intervals.NAMES.contains(name)) {
      return test(call, null, false, ctx);
    }
    if (Regression.NAMES.contains(name)) {
      return Regression.apply(new Apply(call.head(), args), ctx)
          .map(r -> new Outcome(r.result(), r.numeric()))
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

  /**
   * Evaluates a hypothesis test whose arguments are evaluated, its trailing words read.
   *
   * @param call the call, without the words
   * @param alternative {@code two}, {@code less} or {@code greater}, or null for the default
   * @param pooled whether the word {@code pooled} was given
   * @param ctx the assumptions, precision, trace, numerics, providers and cancellation
   * @return the value, its steps, and that it is numeric; the call itself when it stays
   * @throws IllegalArgumentException with the language's message for a wrong argument
   */
  public static Outcome test(Apply call, String alternative, boolean pooled, Context ctx) {
    if (Intervals.NAMES.contains(call.head().name())) {
      if (alternative != null) {
        throw new IllegalArgumentException("`" + call.head().name() + "` takes no alternative");
      }
      return Intervals.apply(call, pooled, ctx)
          .map(r -> new Outcome(r, true))
          .orElseGet(
              () ->
                  new Outcome(
                      new Rewriter.Result(
                          Canonical.of(call, ctx.precision(), ctx.cancellation()), Trace.NONE),
                      false));
    }
    return Tests.apply(call, alternative, pooled, ctx)
        .map(v -> new Outcome(v.result(), true))
        .orElseGet(
            () ->
                new Outcome(
                    new Rewriter.Result(
                        Canonical.of(call, ctx.precision(), ctx.cancellation()), Trace.NONE),
                    false));
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
