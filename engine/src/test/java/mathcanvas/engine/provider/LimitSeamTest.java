package mathcanvas.engine.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import mathcanvas.engine.calculus.Limits;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Limit.Direction;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import org.junit.jupiter.api.Test;

/**
 * The limit operation is a real seam: a deliberately trivial provider that answers limits at
 * infinity of a reciprocal only, {@code 0}, registered ahead of the default, receives them, the
 * rest falls through, and every provider declining leaves the {@code Limit} node with its step.
 */
class LimitSeamTest {

  private static final Symbol X = Symbol.of("x");

  /** Answers {@code 1/x} at {@code ∞}: 0. */
  record ReciprocalOnly(AtomicInteger calls) implements Provider<Limit, Expr> {

    @Override
    public String name() {
      return "reciprocal-only";
    }

    @Override
    public Optional<Expr> apply(Limit input, Context ctx, Steps steps) {
      if (!input.point().equals(Constant.INFINITY)
          || !input.expr().equals(Canonical.of(Parser.parseExpression("1/x", Names.NONE)))) {
        return Optional.empty();
      }
      calls.incrementAndGet();
      return Optional.of(Num.ZERO);
    }
  }

  private static Rewriter.Result limit(String f, Expr point, Context ctx) {
    return Limits.limit(
        Canonical.of(Parser.parseExpression(f, Names.NONE)), X, point, Direction.BOTH, ctx);
  }

  @Test
  void theEngineRoutesToTheProviderRegisteredFirst() {
    AtomicInteger calls = new AtomicInteger();
    Context ctx =
        Context.DEFAULT
            .withProviders(Providers.defaults().register(Limit.class, new ReciprocalOnly(calls), 0))
            .withTrace(true);
    assertThat(ctx.providers().names(Limit.class)).containsExactly("reciprocal-only", "default");
    Rewriter.Result r = limit("1/x", Constant.INFINITY, ctx);
    assertThat(calls.get()).isEqualTo(1);
    assertThat(r.expr()).isEqualTo(Num.ZERO);
    assertThat(r.trace().rewrites()).extracting(Rewrite::ruleSet).containsExactly("provider");
    assertThat(r.trace().rewrites()).extracting(Rewrite::rule).containsExactly("reciprocal-only");

    Rewriter.Result s = limit("x^2", Num.of(3), ctx);
    assertThat(calls.get()).isEqualTo(1);
    assertThat(s.expr()).isEqualTo(Num.of(9));
    assertThat(s.trace().rewrites()).extracting(Rewrite::rule).containsExactly("substitution");
  }

  @Test
  void bothProvidersGiveTheSameLimit() {
    Context ctx =
        Context.DEFAULT.withProviders(
            Providers.defaults().register(Limit.class, new ReciprocalOnly(new AtomicInteger()), 0));
    assertThat(limit("1/x", Constant.INFINITY, ctx).expr())
        .isEqualTo(limit("1/x", Constant.INFINITY, Context.DEFAULT).expr());
  }

  @Test
  void whenEveryProviderDeclinesTheLimitStays() {
    Context ctx =
        Context.DEFAULT
            .withProviders(
                Providers.defaults()
                    .unregister(Limit.class, "default")
                    .register(Limit.class, new ReciprocalOnly(new AtomicInteger())))
            .withTrace(true);
    Rewriter.Result r = limit("x^2", Num.of(3), ctx);
    assertThat(r.expr()).isInstanceOf(mathcanvas.engine.expr.Limit.class);
    assertThat(r.trace().rewrites()).extracting(Rewrite::rule).containsExactly("none");
  }
}
