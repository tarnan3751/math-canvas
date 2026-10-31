package mathcanvas.engine.calculus;

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
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.provider.Providers;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;
import mathcanvas.engine.simplify.Simplifier;

/**
 * Limits: the operation {@link mathcanvas.engine.provider.Limit} through the context's providers;
 * when every provider declines, the limit stays unevaluated.
 */
public final class Limits {

  /** The rule set of the steps a limit records. */
  public static final String RULE_SET = "limit";

  /** The rule set of a provider's answer that recorded no steps. */
  public static final String PROVIDER = "provider";

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
    // a note: the limit stays as it is
    Rewrite last = new Rewrite(node, node, RULE_SET, "none", Path.ROOT, Bindings.EMPTY);
    return new Rewriter.Result(
        node, Trace.concat(simplified.trace(), new Trace(node, node, List.of(last), List.of())));
  }
}
