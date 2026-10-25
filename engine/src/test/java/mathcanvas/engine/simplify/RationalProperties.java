package mathcanvas.engine.simplify;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprArbitraries;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.RuleSet;
import mathcanvas.engine.testing.NumericEvaluator;
import mathcanvas.engine.testing.RandomAssumptions;
import mathcanvas.engine.testing.Soundness;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Reporter;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.statistics.Statistics;

/**
 * The rational rule set alone and in the standard list, over quotients and sums of fractions: sound
 * at random points and at points that put a symbol at a root of a factor, where a denominator
 * vanishes; never adding a pole (the simplified form is defined wherever the input is); idempotent;
 * and terminating.
 */
class RationalProperties {

  private static final List<RuleSet> RATIONAL = List.of(RuleSets.RATIONAL);

  @BeforeProperty
  void begin() {
    SoundnessChecks.begin();
  }

  @AfterProperty
  void end(Reporter reporter) {
    SoundnessChecks.end(reporter);
  }

  @Provide
  Arbitrary<Expr> exprs() {
    return Arbitraries.oneOf(
        ExprArbitraries.rationalExprs(),
        ExprArbitraries.rationalExprsWithContents(),
        ExprArbitraries.elementaryExprs());
  }

  @Provide
  Arbitrary<Assumptions> assumptions() {
    return RandomAssumptions.about(ExprArbitraries.SYMBOLS);
  }

  /** Points within no facts: half random, half with {@code x} or {@code y} at a factor's root. */
  private static List<Map<Symbol, Numeric>> points(Random random) {
    List<Map<Symbol, Numeric>> out =
        new ArrayList<>(Soundness.points(ExprArbitraries.SYMBOLS, Assumptions.EMPTY, 3, random));
    for (Map<Symbol, Numeric> p :
        Soundness.points(ExprArbitraries.SYMBOLS, Assumptions.EMPTY, 3, random)) {
      Map<Symbol, Numeric> q = new LinkedHashMap<>(p);
      List<Numeric> roots = ExprArbitraries.RATIONAL_ROOTS;
      q.put(Symbol.of("x"), roots.get(random.nextInt(roots.size())));
      if (random.nextBoolean()) {
        q.put(Symbol.of("y"), roots.get(random.nextInt(roots.size())));
      }
      out.add(q);
    }
    return out;
  }

  @Property(tries = 5000)
  void sound(@ForAll("exprs") Expr e, @ForAll("assumptions") Assumptions a, @ForAll Random random) {
    SoundnessChecks.check(RATIONAL, e, a, random);
  }

  @Property(tries = 5000)
  void soundAtTheRootsInTheStandardList(@ForAll("exprs") Expr e, @ForAll Random random) {
    SoundnessChecks.check(Simplifier.standard(), e, Assumptions.EMPTY, points(random));
  }

  @Provide
  Arbitrary<Expr> quotients() {
    return Arbitraries.oneOf(
        ExprArbitraries.rationalExprs(), ExprArbitraries.rationalExprsWithContents());
  }

  /**
   * Cancelling removes removable singularities but never adds a pole: wherever the input has an
   * exact value (the polynomial quotients at rational points, roots included), the simplified form
   * has a value. An inexact value says nothing here: the evaluator's size bounds may refuse one
   * side of an equal pair.
   */
  @Property(tries = 5000)
  void neverAddsAPole(@ForAll("quotients") Expr e, @ForAll Random random) {
    Expr before = Canonical.of(e);
    Expr after = Simplifier.simplify(before, Context.DEFAULT).expr();
    for (Map<Symbol, Numeric> point : points(random)) {
      var value = NumericEvaluator.eval(before, point, Soundness.PRECISION);
      if (value.isPresent() && value.get().isExact()) {
        assertThat(NumericEvaluator.eval(after, point, Soundness.PRECISION))
            .as("%s is defined at %s but %s is not", before, point, after)
            .isPresent();
        Statistics.label("defined input").collect(true);
      } else {
        Statistics.label("defined input").collect(false);
      }
    }
  }

  /**
   * After {@code simplify}, a quotient is in lowest terms: with its coefficient {@code P/Q}, the
   * gcd of {@code P·Π Nᵢ} and {@code Q·Π Dⱼ} is 1 in the algebra's normal form.
   */
  @Property(tries = 5000)
  void simplifiedQuotientsAreInLowestTerms(@ForAll("quotients") Expr e) {
    Expr r = Simplifier.simplify(e, Context.DEFAULT).expr();
    List<Expr> factors =
        r instanceof Product p ? p.factors() : r instanceof Power ? List.of(r) : List.of();
    List<Expr> num = new ArrayList<>();
    List<Expr> den = new ArrayList<>();
    for (Expr f : factors) {
      if (f instanceof Num n && n.value() instanceof Rational q) {
        num.add(Num.of(Int.of(q.num())));
        den.add(Num.of(Int.of(q.den())));
      } else if (f instanceof Power w
          && w.exponent() instanceof Num k
          && k.value() instanceof Int i
          && i.signum() < 0
          && !(w.base() instanceof Num)) {
        den.add(Power.of(w.base(), Num.of(i.negate())));
      } else {
        num.add(f);
      }
    }
    if (den.isEmpty()) {
      Statistics.label("a quotient").collect(false);
      return;
    }
    Polynomial n;
    Polynomial d;
    try {
      n = Polynomial.of(Product.of(Num.ONE, Product.of(num.toArray(Expr[]::new))));
      d = Polynomial.of(Product.of(Num.ONE, Product.of(den.toArray(Expr[]::new))));
    } catch (IllegalArgumentException refused) {
      Statistics.label("a quotient").collect(false);
      return;
    }
    Statistics.label("a quotient").collect(true);
    assertThat(mathcanvas.engine.algebra.Gcd.gcd(n, d))
        .as("%s → %s", e, r)
        .isEqualTo(Polynomial.ONE);
  }

  @Property(tries = 2000)
  void simplifyIsIdempotent(@ForAll("exprs") Expr e) {
    Expr once = Simplifier.simplify(e, Context.DEFAULT).expr();
    assertThat(Simplifier.simplify(once, Context.DEFAULT).expr()).as("%s", e).isEqualTo(once);
    Statistics.label("rewritten").collect(!once.equals(Canonical.of(e)));
  }

  @Property(tries = 200)
  void terminates(@ForAll("assumptions") Assumptions a, @ForAll Random random) {
    SoundnessChecks.terminates(RATIONAL, exprs(), a, random, 20);
  }

  @Property(tries = 500)
  void terminatesInTheStandardList(@ForAll("assumptions") Assumptions a, @ForAll Random random) {
    SoundnessChecks.terminates(
        Simplifier.standard(),
        Arbitraries.oneOf(exprs(), ExprArbitraries.trigExprs()),
        a,
        random,
        20);
  }
}
