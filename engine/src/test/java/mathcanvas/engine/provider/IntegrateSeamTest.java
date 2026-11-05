package mathcanvas.engine.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.calculus.Calculus;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.testing.Contexts;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

/**
 * The integrate operation is a real seam: a deliberately trivial provider that integrates
 * polynomials in the variable only, term by term and raw, registered ahead of the default, receives
 * them, the rest falls through, and the two providers' answers are the same once normalised and
 * simplified.
 */
class IntegrateSeamTest {

  /** Integrates a polynomial in the variable alone: {@code Σ c_k x^(k+1)/(k+1)}, raw. */
  record PolynomialOnly(AtomicInteger calls) implements Provider<Integrate, Expr> {

    @Override
    public String name() {
      return "polynomial-only";
    }

    @Override
    public Optional<Expr> apply(Integrate input, Context ctx, Steps steps) {
      Symbol x = input.var();
      if (input.isDefinite()) {
        return Optional.empty();
      }
      Optional<Polynomial> p;
      try {
        p = Polynomial.of(input.integrand(), List.of(x));
      } catch (IllegalArgumentException refused) {
        return Optional.empty();
      }
      if (p.isEmpty() || p.get().isZero()) {
        return Optional.empty();
      }
      calls.incrementAndGet();
      List<Expr> terms = new ArrayList<>();
      for (int k = 0; k <= p.get().degree(x); k++) {
        terms.add(
            Product.of(
                Num.of(p.get().coefficient(k)),
                Power.of(x, Num.of(k + 1)),
                Exprs.inv(Num.of(Int.of(k + 1)))));
      }
      return Optional.of(terms.size() == 1 ? terms.getFirst() : Sum.of(terms));
    }
  }

  private static Rewriter.Result integrate(String f, Context ctx) {
    return Calculus.integrate(
        Canonical.of(Parser.parseExpression(f, Names.NONE)), Symbol.of("x"), null, null, ctx);
  }

  private static Context withPolynomialOnly(AtomicInteger calls) {
    return Context.DEFAULT
        .withProviders(Providers.defaults().register(Integrate.class, new PolynomialOnly(calls), 0))
        .withTrace(true);
  }

  @Test
  void theEngineRoutesToTheProviderRegisteredFirst() {
    AtomicInteger calls = new AtomicInteger();
    Context ctx = withPolynomialOnly(calls);
    assertThat(ctx.providers().names(Integrate.class))
        .containsExactly("polynomial-only", "default");

    Rewriter.Result r = integrate("3x^2 + 1", ctx);
    assertThat(calls.get()).isEqualTo(1);
    assertThat(r.expr()).isEqualTo(Canonical.of(Parser.parseExpression("x^3 + x", Names.NONE)));
    assertThat(r.trace().rewrites())
        .extracting(Rewrite::ruleSet)
        .containsExactly(Calculus.PROVIDER);
    assertThat(r.trace().rewrites().getFirst().rule()).isEqualTo("polynomial-only");
    TraceChecks.assertChain(r);

    Rewriter.Result s = integrate("sin(x)", ctx);
    assertThat(calls.get()).isEqualTo(1);
    assertThat(s.expr()).isEqualTo(Canonical.of(Parser.parseExpression("-cos(x)", Names.NONE)));
    assertThat(s.trace().rewrites()).extracting(Rewrite::ruleSet).containsOnly(Calculus.INTEGRATE);
  }

  @Test
  void whenEveryProviderDeclinesTheIntegralStays() {
    Context ctx =
        Context.DEFAULT
            .withProviders(
                Contexts.unregister(Providers.defaults(), Integrate.class, "default")
                    .register(Integrate.class, new PolynomialOnly(new AtomicInteger())))
            .withTrace(true);
    Rewriter.Result r = integrate("sin(x)", ctx);
    assertThat(r.expr())
        .isEqualTo(
            Integral.indefinite(
                Canonical.of(Parser.parseExpression("sin(x)", Names.NONE)), Symbol.of("x")));
    assertThat(r.trace().rewrites()).extracting(Rewrite::rule).containsExactly("none");
    TraceChecks.assertChain(new Rewriter.Result(r.expr(), r.trace()));
  }

  @Property(tries = 300)
  void bothProvidersGiveTheSameValue(
      @ForAll @IntRange(min = -20, max = 20) int a,
      @ForAll @IntRange(min = -20, max = 20) int b,
      @ForAll @IntRange(min = -20, max = 20) int c,
      @ForAll @IntRange(min = 1, max = 9) int d) {
    if (a == 0 && b == 0 && c == 0) {
      return;
    }
    String f = a + "x^3 + " + b + "x/" + d + " + " + c;
    AtomicInteger calls = new AtomicInteger();
    Expr trivial = integrate(f, withPolynomialOnly(calls)).expr();
    Expr own = integrate(f, Context.DEFAULT).expr();
    assertThat(calls.get()).isEqualTo(1);
    assertThat(trivial).isEqualTo(own);
  }
}
