package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Elementary;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.testing.NumericEvaluator;
import mathcanvas.engine.testing.Soundness;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.statistics.Statistics;

/**
 * The builtin functions on numbers and {@code numeric} against the test-only {@link
 * NumericEvaluator}, and {@code exact} against {@code numeric}. {@code numeric} is compared where
 * the value is well conditioned: where the evaluator's values at 60 and 80 digits agree to 30.
 */
class NumericProperties {

  static final List<Symbol> SYMBOLS = List.of(Symbol.of("x"), Symbol.of("y"));

  /** The functions {@link NumericEvaluator} knows, of one argument. */
  static final List<String> FUNCTIONS =
      List.of(
          "abs", "sqrt", "cbrt", "exp", "log", "ln", "log10", "log2", "sin", "cos", "tan", "sinh",
          "cosh", "tanh", "asin", "acos", "atan");

  /** The functions that make values too large to sample when nested. */
  static final List<String> GROWING = List.of("exp", "sinh", "cosh");

  /** Expressions of the known functions over small numbers, decimals, x, y and π, depth ≤ 3. */
  @Provide
  Arbitrary<Expr> calls() {
    return Arbitraries.randomValue(random -> expr(random, random.nextInt(4)));
  }

  static Expr expr(Random random, int depth) {
    if (depth == 0) {
      return switch (random.nextInt(8)) {
        case 0, 1 -> Num.of(random.nextInt(7) - 3);
        case 2 -> Num.of(Rational.of(random.nextInt(9) - 4, random.nextInt(4) + 1));
        case 3 -> new Num(Real.of(BigDecimal.valueOf(random.nextInt(2001) - 1000, 3)));
        case 4 -> Constant.PI;
        default -> SYMBOLS.get(random.nextInt(2));
      };
    }
    Expr a = expr(random, depth - 1);
    return switch (random.nextInt(5)) {
      case 0 -> Sum.of(a, expr(random, random.nextInt(depth)));
      case 1 -> Product.of(a, expr(random, random.nextInt(depth)));
      case 2 -> Power.of(a, Num.of(random.nextInt(5) - 2));
      default -> {
        String f = FUNCTIONS.get(random.nextInt(FUNCTIONS.size()));
        // The fast-growing functions take a leaf, so that no sample point asks for exp(10^8).
        yield Apply.of(Symbol.of(f), GROWING.contains(f) ? expr(random, 0) : a);
      }
    };
  }

  @Property(tries = 2000)
  void builtinEvaluationIsSound(@ForAll("calls") Expr e, @ForAll Random random) {
    Expr before = Canonical.of(e);
    Expr after;
    try {
      after = Evaluator.evaluate(e, Environment.EMPTY, false).value();
    } catch (EvaluationException outOfRange) {
      Statistics.label("evaluated").collect(false);
      return;
    }
    Statistics.label("evaluated").collect(true);
    Statistics.label("changed").collect(!after.equals(before));
    for (Map<Symbol, Numeric> point : Soundness.points(SYMBOLS, Assumptions.EMPTY, 4, random)) {
      try {
        Soundness.check(before, after, List.of(point));
      } catch (AssertionError disagreement) {
        // The simplifier's exact values (tan(π) is 0) meet the input's rounding noise: a point
        // where either side is empty away from cuts (a root of a zero within rounding, sqrt(tan(π))
        // computed near 10^-17) is no evidence, as in the rule sets' rechecks.
        MathContext recheck = new MathContext(100);
        if (NumericEvaluator.evalAwayFromCuts(before, point, recheck).isPresent()
            && NumericEvaluator.evalAwayFromCuts(after, point, recheck).isPresent()) {
          throw disagreement;
        }
        Statistics.label("within rounding of a cut or a zero").collect(true);
      }
    }
  }

  @Property(tries = 2000)
  void numericAgreesWithTheNumericEvaluator(@ForAll("calls") Expr e, @ForAll Random random) {
    TreeMap<String, Expr> substitution = new TreeMap<>();
    Map<Symbol, Numeric> point = Soundness.points(SYMBOLS, Assumptions.EMPTY, 1, random).getFirst();
    for (Map.Entry<Symbol, Numeric> entry : point.entrySet()) {
      substitution.put(entry.getKey().name(), new Num(entry.getValue()));
    }
    Expr at = Substitution.apply(e, substitution);
    Optional<Numeric> expected = NumericEvaluator.eval(Canonical.of(e), point, new MathContext(60));
    Expr actual;
    try {
      actual =
          Evaluator.evaluate(Apply.of(Symbol.of("numeric"), at), Environment.EMPTY, false).value();
    } catch (EvaluationException outOfRange) {
      Statistics.label("compared").collect("out of range");
      return;
    }
    Optional<Numeric> coarse = NumericEvaluator.eval(Canonical.of(e), point, new MathContext(44));
    Optional<Numeric> finer = NumericEvaluator.eval(Canonical.of(e), point, new MathContext(80));
    if (illConditioned(coarse, finer, "1e-20") || illConditioned(expected, finer, "1e-30")) {
      // Ill-conditioned at this point (tan at a pole, cancellation): no precision is enough.
      Statistics.label("compared").collect("ill-conditioned");
      return;
    }
    if (expected.isEmpty() || !(actual instanceof Num n)) {
      Statistics.label("compared").collect(expected.isEmpty() ? "undefined" : "not a number");
      return;
    }
    Statistics.label("compared").collect("compared");
    Numeric difference = n.value().subtract(expected.get(), new MathContext(60));
    BigDecimal error = modulus(difference);
    BigDecimal scale = modulus(expected.get());
    assertThat(error)
        .as("numeric(%s) = %s, expected %s", at, n.value(), expected.get())
        .isLessThanOrEqualTo(scale.multiply(new BigDecimal("1e-15")).add(new BigDecimal("1e-20")));
  }

  @Property(tries = 1000)
  void exactUndoesNumericOnTerminatingRationals(
      @ForAll @IntRange(min = -1_000_000, max = 1_000_000) int numerator,
      @ForAll @IntRange(min = 0, max = 10) int twos,
      @ForAll @IntRange(min = 0, max = 10) int fives) {
    BigInteger denominator = BigInteger.TWO.pow(twos).multiply(BigInteger.valueOf(5).pow(fives));
    Expr q = new Num(Rational.of(BigInteger.valueOf(numerator), denominator));
    Expr n =
        Evaluator.evaluate(Apply.of(Symbol.of("numeric"), q), Environment.EMPTY, false).value();
    assertThat(n).isInstanceOfSatisfying(Num.class, x -> assertThat(x.value().isExact()).isFalse());
    Expr back =
        Evaluator.evaluate(Apply.of(Symbol.of("exact"), n), Environment.EMPTY, false).value();
    assertThat(back).isEqualTo(q);
  }

  /** Whether two evaluations at different precisions disagree beyond a relative tolerance. */
  private static boolean illConditioned(
      Optional<Numeric> a, Optional<Numeric> b, String tolerance) {
    if (a.isEmpty() || b.isEmpty()) {
      return a.isPresent() != b.isPresent();
    }
    BigDecimal difference = modulus(a.get().subtract(b.get(), new MathContext(80)));
    return difference.compareTo(modulus(b.get()).multiply(new BigDecimal(tolerance))) > 0;
  }

  private static BigDecimal modulus(Numeric v) {
    return v.modulus(new MathContext(60)).toReal(new MathContext(60)).value().abs();
  }

  /**
   * The inverse hyperbolic functions at 34 digits against their logarithmic formulas at 150 digits
   * or more (enough for the argument's digits and its exponent), within one unit in the last place,
   * at full-precision arguments near the points of cancellation and of extreme size: {@code ±(1 ±
   * d·10^-k)} and {@code ±d·10^±k} for a 34-digit {@code d} and {@code k} up to 200.
   */
  @Property(tries = 3000)
  void inverseHyperbolicFunctionsAreFaithful(
      @ForAll("inverseHyperbolic") String name, @ForAll("arguments") BigDecimal x) {
    // The formulas cancel near 1 and near 0 or infinity: enough digits for the argument's own and
    // for its distance from those points.
    int exponent = Math.abs(x.precision() - x.scale() - 1);
    MathContext fine = new MathContext(Math.max(150, Math.max(x.precision() + 80, exponent + 150)));
    Optional<Numeric> reference = reference(name, x, fine);
    if (reference.isEmpty() || !(reference.get() instanceof Real expected)) {
      Statistics.label("compared").collect(false);
      return;
    }
    Expr call = Apply.of(Symbol.of(name), new Num(new Real(x)));
    Expr actual = Evaluator.evaluate(call, Environment.EMPTY, false).value();
    assertThat(actual).as("%s(%s)", name, x).isInstanceOf(Num.class);
    Real got = (Real) ((Num) actual).value();
    Statistics.label("compared").collect(true);
    BigDecimal ulp = got.value().round(new MathContext(34)).ulp();
    assertThat(got.value().subtract(expected.value()).abs())
        .as("%s(%s) = %s, expected %s", name, x, got.value(), expected.value())
        .isLessThanOrEqualTo(ulp);
  }

  @Provide
  Arbitrary<String> inverseHyperbolic() {
    return Arbitraries.of("asinh", "acosh", "atanh", "acoth", "asech", "acsch");
  }

  @Provide
  Arbitrary<BigDecimal> arguments() {
    return Arbitraries.randomValue(
        random -> {
          BigDecimal d = new BigDecimal(new BigInteger(112, random)).movePointLeft(33);
          d = d.round(new MathContext(34));
          int k = random.nextInt(201);
          BigDecimal x =
              random.nextBoolean()
                  ? BigDecimal.ONE.add(
                      d.movePointLeft(k + 1)
                          .multiply(BigDecimal.valueOf(random.nextBoolean() ? 1 : -1)))
                  : d.movePointRight(random.nextBoolean() ? k : -k);
          return random.nextBoolean() ? x : x.negate();
        });
  }

  /**
   * The real value by its logarithmic formula, at {@code mc} with the differences with 1 formed
   * exactly, or empty outside the real domain.
   */
  private static Optional<Numeric> reference(String name, BigDecimal x, MathContext mc) {
    BigDecimal one = BigDecimal.ONE;
    int sign = x.signum();
    if (sign == 0) {
      return Optional.empty();
    }
    BigDecimal a = x.abs();
    BigDecimal value;
    switch (name) {
      case "asinh" -> value = ln(a.add(sqrt(a.multiply(a).add(one), mc)), mc);
      case "acsch" -> value = ln(one.add(sqrt(one.add(a.multiply(a)), mc)).divide(a, mc), mc);
      case "acosh" -> {
        if (x.compareTo(one) < 0) {
          return Optional.empty();
        }
        value = ln(x.add(sqrt(x.add(one), mc).multiply(sqrt(x.subtract(one), mc), mc)), mc);
        sign = 1;
      }
      case "asech" -> {
        if (x.signum() <= 0 || x.compareTo(one) > 0) {
          return Optional.empty();
        }
        value = ln(one.add(sqrt(one.subtract(x.multiply(x)), mc)).divide(x, mc), mc);
        sign = 1;
      }
      case "atanh" -> {
        if (a.compareTo(one) >= 0) {
          return Optional.empty();
        }
        value = ln(one.add(a).divide(one.subtract(a), mc), mc).divide(BigDecimal.TWO, mc);
      }
      default -> {
        if (a.compareTo(one) <= 0) {
          return Optional.empty();
        }
        value = ln(a.add(one).divide(a.subtract(one), mc), mc).divide(BigDecimal.TWO, mc);
      }
    }
    return Optional.of(new Real(sign < 0 ? value.negate() : value));
  }

  private static BigDecimal sqrt(BigDecimal v, MathContext mc) {
    return v.sqrt(mc);
  }

  private static BigDecimal ln(BigDecimal v, MathContext mc) {
    return ((Real) Elementary.log(new Real(v), mc)).value();
  }

  /**
   * A real argument outside an inverse hyperbolic function's real domain gives the function's
   * principal formula evaluated through the tower's complex {@code log} and {@code sqrt} at {@code
   * Complex(x, 0)} (counterclockwise continuity), within one unit in the last place of the larger
   * part.
   */
  @Property(tries = 1500)
  void outsideTheirDomainsTheInverseHyperbolicsAreTheirPrincipalFormulas(
      @ForAll("outsideDomain") String name, @ForAll Random random) {
    BigDecimal x = outside(name, random);
    Expr call = Apply.of(Symbol.of(name), new Num(new Real(x)));
    Expr actual = Evaluator.evaluate(call, Environment.EMPTY, false).value();
    assertThat(actual).as("%s(%s)", name, x).isInstanceOf(Num.class);
    Numeric got = ((Num) actual).value();
    MathContext fine = new MathContext(80);
    Numeric expected = principal(name, new Complex(new Real(x), new Real(BigDecimal.ZERO)), fine);
    BigDecimal larger =
        expected.re().abs().toReal(fine).value().max(expected.im().abs().toReal(fine).value());
    BigDecimal ulp = larger.round(new MathContext(34)).ulp();
    assertThat(got.re().subtract(expected.re(), fine).modulus(fine).toReal(fine).value())
        .as("re %s(%s) = %s, expected %s", name, x, got, expected)
        .isLessThanOrEqualTo(ulp);
    assertThat(got.im().subtract(expected.im(), fine).modulus(fine).toReal(fine).value())
        .as("im %s(%s) = %s, expected %s", name, x, got, expected)
        .isLessThanOrEqualTo(ulp);
  }

  @Provide
  Arbitrary<String> outsideDomain() {
    return Arbitraries.of("acosh", "atanh", "acoth", "asech");
  }

  /** A 34-digit real outside the function's real domain, of either sign where both occur. */
  private static BigDecimal outside(String name, Random random) {
    BigDecimal d = new BigDecimal(new BigInteger(112, random)).movePointLeft(33);
    d = d.round(new MathContext(34)).add(BigDecimal.ONE.movePointLeft(3)); // in (0.001, 5.2)
    BigDecimal big = BigDecimal.ONE.add(d); // > 1
    BigDecimal small = BigDecimal.ONE.divide(big, new MathContext(34)); // in (0, 1)
    boolean negative = random.nextBoolean();
    return switch (name) {
      case "atanh" -> negative ? big.negate() : big;
      case "acoth" -> negative ? small.negate() : small;
      case "acosh" -> negative ? big.negate() : small; // below 1: x < -1 or 0 < x < 1
      default -> negative ? small.negate() : big; // asech: x < 0 or x > 1
    };
  }

  /** The principal formula of each function, through the tower's complex log and sqrt. */
  private static Numeric principal(String name, Numeric z, MathContext mc) {
    Numeric one = Int.ONE;
    return switch (name) {
      case "atanh" -> atanhOf(z, mc);
      case "acoth" -> atanhOf(one.divide(z, mc), mc);
      case "acosh" -> acoshOf(z, mc);
      default -> acoshOf(one.divide(z, mc), mc);
    };
  }

  private static Numeric atanhOf(Numeric z, MathContext mc) {
    Numeric one = Int.ONE;
    return Elementary.log(one.add(z, mc), mc)
        .subtract(Elementary.log(one.subtract(z, mc), mc), mc)
        .divide(Int.TWO, mc);
  }

  private static Numeric acoshOf(Numeric z, MathContext mc) {
    Numeric one = Int.ONE;
    Numeric root = z.subtract(one, mc).sqrt(mc).multiply(z.add(one, mc).sqrt(mc), mc);
    return Elementary.log(z.add(root, mc), mc);
  }
}
