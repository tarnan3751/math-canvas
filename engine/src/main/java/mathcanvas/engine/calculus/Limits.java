package mathcanvas.engine.calculus;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Limit;
import mathcanvas.engine.expr.Limit.Direction;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.provider.Providers;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;
import mathcanvas.engine.simplify.Simplifier;

/**
 * Limits: the operation {@link mathcanvas.engine.provider.Limit} through the context's providers,
 * and, when every provider declines, the numeric probe, which never gives a value: it records an
 * estimate in the trace, and the limit stays unevaluated.
 */
public final class Limits {

  /** The rule set of the steps a limit records. */
  public static final String RULE_SET = "limit";

  /** The rule set of a provider's answer that recorded no steps. */
  public static final String PROVIDER = "provider";

  /** The most digits the probe's estimate is agreed to. */
  static final int PROBE_DIGITS = 12;

  private Limits() {}

  /**
   * The limit of {@code f} as {@code x} approaches {@code point}.
   *
   * @param f the expression, evaluated
   * @param x the variable
   * @param point the point, evaluated: finite, {@code ∞} or {@code -∞}
   * @param direction the side; {@code BOTH} at {@code ±∞}
   * @param ctx the context: the facts of the point (those about {@code x} do not hold in {@code
   *     f}), precision, trace, cap, numerics, providers
   * @return the limit, or the {@code Limit} node when no method decides, with the trace
   * @throws IllegalArgumentException if a side is given at an infinite point
   */
  public static Rewriter.Result limit(
      Expr f, Symbol x, Expr point, Direction direction, Context ctx) {
    MathContext mc = ctx.precision();
    Expr body = Signs.canon(f, ctx);
    Expr p = Signs.canon(point, ctx);
    if (LimitMethods.infinite(p) && direction != Direction.BOTH) {
      throw new IllegalArgumentException("a limit at ∞ or -∞ has no side");
    }
    if (body.equals(Constant.UNDEFINED) || !Signs.finite(p) && !LimitMethods.infinite(p)) {
      return new Rewriter.Result(Constant.UNDEFINED, Trace.NONE);
    }
    Limit given = Limit.of(body, x, p, direction);
    if (!Signs.scalar(body) || !Signs.scalar(p)) {
      // a limit of a vector, a set or a truth value, or at such a point, is not taken
      return new Rewriter.Result(given, Trace.NONE);
    }
    // the body simplified first, without the facts about x (the rewriter drops them in a body)
    Rewriter.Result simplified = Simplifier.simplify(given, ctx);
    if (!(simplified.expr() instanceof Limit node)) {
      return simplified;
    }
    body = node.expr();
    List<Rewrite> rewrites = new ArrayList<>();
    Steps sink =
        ctx.traceEnabled()
            ? new Steps() {
              @Override
              public void record(String rule, Expr before, Expr after, Bindings bindings) {
                record(rule, before, after, bindings, new TreeMap<>());
              }

              @Override
              public void record(
                  String rule,
                  Expr before,
                  Expr after,
                  Bindings bindings,
                  SortedMap<String, Expr> values) {
                rewrites.add(
                    new Rewrite(before, after, RULE_SET, rule, Path.ROOT, bindings, values));
              }
            }
            : Steps.NONE;
    Optional<Providers.Outcome<Expr>> outcome =
        ctx.providers()
            .attempt(new mathcanvas.engine.provider.Limit(body, x, p, direction, mc), ctx, sink);
    if (outcome.isPresent()) {
      Expr result = outcome.get().value();
      Expr reached = rewrites.isEmpty() ? node : rewrites.getLast().after();
      if (ctx.traceEnabled() && !reached.equals(result)) {
        rewrites.add(
            new Rewrite(
                reached, result, PROVIDER, outcome.get().provider(), Path.ROOT, Bindings.EMPTY));
      }
      Trace trace =
          ctx.traceEnabled()
              ? Trace.concat(simplified.trace(), new Trace(node, result, rewrites, List.of()))
              : Trace.NONE;
      return new Rewriter.Result(result, trace);
    }
    if (!ctx.traceEnabled()) {
      return new Rewriter.Result(node, Trace.NONE);
    }
    Optional<Expr> estimate = probe(body, x, p, direction, ctx);
    TreeMap<String, Expr> values = new TreeMap<>();
    estimate.ifPresent(
        v -> {
          values.put("estimate", v);
          values.put("digits", Num.of(digits(ctx)));
        });
    // a note: the limit stays as it is
    Rewrite last =
        new Rewrite(
            node,
            node,
            RULE_SET,
            estimate.isPresent() ? "numeric" : "none",
            Path.ROOT,
            Bindings.EMPTY,
            values);
    return new Rewriter.Result(
        node, Trace.concat(simplified.trace(), new Trace(node, node, List.of(last), List.of())));
  }

  private static int digits(Context ctx) {
    return Math.min(ctx.precision().getPrecision(), PROBE_DIGITS);
  }

  /**
   * The numeric probe: the values at points approaching the limit's point in a geometric sequence
   * from each side asked for, accepted when the last half of each sequence agrees to the digits and
   * the sides agree.
   *
   * @return the estimate, rounded to the digits, or empty when it does not settle
   */
  static Optional<Expr> probe(Expr f, Symbol x, Expr a, Direction direction, Context ctx) {
    if (Signs.hasSymbol(a) || !(Signs.finite(a) || LimitMethods.infinite(a))) {
      return Optional.empty();
    }
    int d = digits(ctx);
    MathContext work = new MathContext(3 * d + 20);
    List<Integer> sides =
        LimitMethods.infinite(a)
            ? List.of(LimitMethods.infSign(a))
            : switch (direction) {
              case RIGHT -> List.of(1);
              case LEFT -> List.of(-1);
              case BOTH -> List.of(1, -1);
            };
    BigDecimal tolerance = BigDecimal.ONE.movePointLeft(d);
    BigDecimal last = null;
    for (int s : sides) {
      List<BigDecimal> values = new ArrayList<>();
      for (int j = 0; j < 10; j++) {
        ctx.cancellation().check();
        Expr step = Power.of(Num.of(10), Num.of(d + 2 + j));
        Expr point =
            LimitMethods.infinite(a)
                ? Product.of(Num.of(s), step)
                : Sum.of(a, Product.of(Num.of(s), Power.of(step, Num.MINUS_ONE)));
        Optional<Expr> at = Subst.of(f, x, point, work);
        if (at.isEmpty()) {
          return Optional.empty();
        }
        Optional<Numeric> v = ctx.numerics().value(at.get(), work);
        if (v.isEmpty() || !v.get().im().isZero()) {
          return Optional.empty();
        }
        values.add(v.get().re().toReal(work).value());
      }
      BigDecimal end = values.getLast();
      BigDecimal scale = end.abs().max(BigDecimal.ONE).multiply(tolerance);
      for (BigDecimal v : values.subList(5, 10)) {
        if (v.subtract(end).abs().compareTo(scale) > 0) {
          return Optional.empty();
        }
      }
      if (last != null && last.subtract(end).abs().compareTo(scale) > 0) {
        return Optional.empty();
      }
      last = end;
    }
    // an estimate below the digits' reach is 0
    BigDecimal rounded =
        last.abs().compareTo(tolerance) < 0 ? BigDecimal.ZERO : last.round(new MathContext(d));
    RealNumeric r = rounded.signum() == 0 ? Int.ZERO : Real.of(rounded);
    return Optional.of(Num.of(r));
  }
}
