package mathcanvas.engine.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.algebra.Factoring;
import mathcanvas.engine.algebra.Factorization;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.algebra.PolynomialArbitraries;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

/**
 * The provider interface is a real seam (PLAN Phase 1 acceptance): a second, deliberately trivial
 * gcd provider registered ahead of the default receives the calls it answers, and the results of
 * both normalise to the same canonical forms.
 */
class ProviderSeamTest {

  /**
   * Answers only when an input is a non-zero constant, where the gcd is a constant: it returns -7,
   * a correct answer up to a rational factor that only the normalisation makes the normal form.
   */
  record Trivial(AtomicInteger calls) implements Provider<Gcd, Polynomial> {

    @Override
    public String name() {
      return "trivial";
    }

    @Override
    public Optional<Polynomial> apply(Gcd input, Context ctx, Steps steps) {
      if (constant(input.a()) || constant(input.b())) {
        calls.incrementAndGet();
        return Optional.of(Polynomial.constant(Int.of(-7)));
      }
      return Optional.empty();
    }

    private static boolean constant(Polynomial p) {
      return p.isConstant() && !p.isZero();
    }
  }

  private static Expr e(String s) {
    return Canonical.of(Parser.parseExpression(s, Names.NONE));
  }

  private static Context withTrivial(AtomicInteger calls) {
    Providers p = Providers.defaults().register(Gcd.class, new Trivial(calls), 0);
    return Context.DEFAULT.withProviders(p).withTrace(true);
  }

  @Test
  void theEngineRoutesToTheProviderRegisteredFirst() {
    AtomicInteger calls = new AtomicInteger();
    Context ctx = withTrivial(calls);
    assertThat(ctx.providers().names(Gcd.class)).containsExactly("trivial", "default");

    Rewriter.Result r = Algebra.gcd(List.of(e("6"), e("4x + 2")), ctx).orElseThrow();
    assertThat(calls.get()).isEqualTo(1);
    assertThat(r.expr()).isEqualTo(e("2"));
    assertThat(r.trace().rewrites()).extracting(Rewrite::rule).containsExactly("trivial");

    Rewriter.Result s = Algebra.gcd(List.of(e("x^2 - 1"), e("x + 1")), ctx).orElseThrow();
    assertThat(calls.get()).isEqualTo(1);
    assertThat(s.expr()).isEqualTo(e("x + 1"));
    assertThat(s.trace().rewrites()).extracting(Rewrite::rule).containsExactly("default");
    assertThat(s.trace().rewrites()).allMatch(w -> w.ruleSet().equals(Algebra.PROVIDER));
    TraceChecks.assertChain(r);
    TraceChecks.assertChain(s);
  }

  /** A factor provider that records no steps, as a foreign library would. */
  record Silent() implements Provider<Factor, Factorization> {

    @Override
    public String name() {
      return "silent";
    }

    @Override
    public Optional<Factorization> apply(Factor input, Context ctx, Steps steps) {
      return Optional.of(Factoring.factor(input.p(), Steps.NONE));
    }
  }

  @Test
  void aProviderWithoutStepsIsOneRewriteNamedAfterIt() {
    Context ctx =
        Context.DEFAULT
            .withProviders(Providers.defaults().register(Factor.class, new Silent(), 0))
            .withTrace(true);
    Rewriter.Result r = Algebra.factor(e("x^2 - 1"), ctx).orElseThrow();
    TraceChecks.assertChain(r);
    assertThat(r.expr()).isEqualTo(e("(x - 1)(x + 1)"));
    assertThat(r.trace().rewrites()).hasSize(1);
    assertThat(r.trace().rewrites().getFirst().ruleSet()).isEqualTo(Algebra.PROVIDER);
    assertThat(r.trace().rewrites().getFirst().rule()).isEqualTo("silent");
    assertThat(r.trace().rewrites().getFirst().before()).isEqualTo(e("x^2 - 1"));
    Rewriter.Result own =
        Algebra.factor(e("x^2 - 1"), Context.DEFAULT.withTrace(true)).orElseThrow();
    assertThat(own.trace().rewrites()).extracting(Rewrite::ruleSet).containsOnly("factor");
  }

  @Test
  void theRegistryIsAValue() {
    Providers base = Providers.defaults();
    Providers more = base.register(Gcd.class, new Trivial(new AtomicInteger()));
    assertThat(base.names(Gcd.class)).containsExactly("default");
    assertThat(more.names(Gcd.class)).containsExactly("default", "trivial");
    assertThat(more.unregister(Gcd.class, "trivial").names(Gcd.class)).containsExactly("default");
    assertThatThrownBy(() -> more.register(Gcd.class, new Trivial(new AtomicInteger())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> base.register(Gcd.class, new Trivial(new AtomicInteger()), 2))
        .isInstanceOf(IndexOutOfBoundsException.class);
    Context none = Context.DEFAULT.withProviders(Providers.EMPTY);
    assertThatThrownBy(() -> Algebra.gcd(List.of(e("x"), e("y")), none))
        .isInstanceOf(IllegalStateException.class);
    assertThat(Context.DEFAULT.providers()).isEqualTo(Providers.defaults());
  }

  @Provide
  Arbitrary<Polynomial> polys() {
    return PolynomialArbitraries.polynomials(2, 3, 4);
  }

  /** Where the trivial provider answers, its result and the default's are one canonical form. */
  @Property(tries = 500)
  void bothProvidersNormaliseToTheSameForm(
      @ForAll("polys") Polynomial a, @ForAll("polys") Polynomial b) {
    AtomicInteger calls = new AtomicInteger();
    Polynomial c =
        a.isZero() ? Polynomial.constant(Int.of(3)) : Polynomial.constant(a.leadingCoefficient());
    List<Expr> args = List.of(c.toExpr(), b.toExpr());
    Expr trivial = Algebra.gcd(args, withTrivial(calls)).orElseThrow().expr();
    Expr standard = Algebra.gcd(args, Context.DEFAULT).orElseThrow().expr();
    assertThat(calls.get()).isEqualTo(1);
    assertThat(trivial).isEqualTo(standard);
  }
}
