package mathcanvas.engine.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.engine.eval.Outcome;
import mathcanvas.engine.eval.Session;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

/**
 * The functions the numeric evaluator computes by formula agree with the evaluator's own inexact
 * values, inside and outside their real domains, and with reference values; its poles, cuts and
 * zeros within rounding are empty away from cuts.
 */
class NumericEvaluatorTest {

  private static final List<String> FUNCTIONS =
      List.of(
          "cot", "sec", "csc", "acot", "asec", "acsc", "coth", "sech", "csch", "asinh", "acosh",
          "atanh", "acoth", "asech", "acsch", "floor", "ceil", "round", "trunc");

  private static final List<String> POINTS =
      List.of("0.3", "0.75", "1.5", "2.5", "-0.3", "-0.75", "-1.5", "-2.5");

  private static final BigDecimal TOLERANCE = new BigDecimal("1e-28");

  /** The session's value of {@code text}, a number. */
  private static Numeric sessionValue(String text) {
    Session s = new Session();
    Outcome o = s.run(Parser.parseScript(text, s.names())).getLast();
    assertThat(o).as(text).isInstanceOf(Outcome.Value.class);
    Expr v = ((Outcome.Value) o).value();
    assertThat(v).as(text).isInstanceOf(Num.class);
    return ((Num) v).value();
  }

  private static void agree(String text, Expr e) {
    Numeric expected = sessionValue(text);
    Optional<Numeric> actual = NumericEvaluator.eval(e, Map.of(), Soundness.PRECISION);
    assertThat(actual).as(text).isPresent();
    BigDecimal difference =
        actual
            .get()
            .subtract(expected, Soundness.PRECISION)
            .modulus(Soundness.PRECISION)
            .toReal(Soundness.PRECISION)
            .value();
    BigDecimal scale =
        expected
            .modulus(Soundness.PRECISION)
            .toReal(Soundness.PRECISION)
            .value()
            .max(BigDecimal.ONE);
    assertThat(difference)
        .as("%s: %s vs %s", text, actual.get(), expected)
        .isLessThanOrEqualTo(TOLERANCE.multiply(scale));
  }

  @Test
  void theAddedFunctionsAgreeWithTheEvaluator() {
    for (String f : FUNCTIONS) {
      for (String p : POINTS) {
        agree(f + "(" + p + ")", Apply.of(Symbol.of(f), Num.of(Real.of(p))));
      }
    }
  }

  @Test
  void atan2AgreesInEveryQuadrant() {
    for (String y : List.of("0.5", "-0.5", "0.0")) {
      for (String x : List.of("2.0", "-2.0", "0.0")) {
        if (y.equals("0.0") && x.equals("0.0")) {
          continue;
        }
        agree(
            "atan2(" + y + ", " + x + ")",
            Apply.of(Symbol.of("atan2"), Num.of(Real.of(y)), Num.of(Real.of(x))));
      }
    }
  }

  private static final MathContext MC = MathContext.DECIMAL128;

  private static Optional<Numeric> eval(String source) {
    Expr e = Canonical.of(Parser.parseExpression(source, Names.NONE));
    return NumericEvaluator.eval(e, Map.of(), MC);
  }

  private static Optional<Numeric> evalAway(String source) {
    Expr e = Canonical.of(Parser.parseExpression(source, Names.NONE));
    return NumericEvaluator.evalAwayFromCuts(e, Map.of(), MC);
  }

  private static void close(String source, String re, String im) {
    Numeric v = eval(source).orElseThrow();
    assertThat(v.re().toReal(MC).value().subtract(new BigDecimal(re)).abs())
        .as(source)
        .isLessThan(new BigDecimal("1e-30"));
    assertThat(v.im().toReal(MC).value().subtract(new BigDecimal(im)).abs())
        .as(source)
        .isLessThan(new BigDecimal("1e-30"));
  }

  @Test
  void reciprocalCircularFunctions() {
    close("cot(1)", "0.6420926159343307030064199865942656", "0");
    close("sec(1)", "1.850815717680925617911753241398650", "0");
    close("csc(1)", "1.188395105778121216261599452374551", "0");
    close("tan(1)", "1.557407724654902230506974807458360", "0");
  }

  @Test
  void inverseFunctionsAsTheEngineDefinesThem() {
    close("acot(2)", "0.4636476090008061162142562314612144", "0");
    close("acot(0)", "1.570796326794896619231321691639751", "0");
    close("asec(2)", "1.047197551196597746154214461093168", "0");
    close("acsc(2)", "0.5235987755982988730771072305465838", "0");
    close("asinh(-1/2)", "-0.4812118250596034474977589134243684", "0");
    close("atanh(1/2)", "0.5493061443340548456976226184612628", "0");
    // Counterclockwise continuity, as the engine documents it.
    close(
        "atanh(2)", "0.5493061443340548456976226184612628", "-1.570796326794896619231321691639751");
    close(
        "atanh(-2)",
        "-0.5493061443340548456976226184612628",
        "1.570796326794896619231321691639751");
  }

  @Test
  void polesAreEmpty() {
    assertThat(eval("cot(0)")).isEmpty();
    assertThat(eval("csc(0)")).isEmpty();
    assertThat(eval("acot(0)")).isPresent();
    assertThat(eval("asec(0)")).isEmpty();
  }

  @Test
  void polesWithinRoundingAreEmptyAwayFromCuts() {
    assertThat(eval("tan(3pi/2)")).isPresent();
    assertThat(evalAway("tan(3pi/2)")).isEmpty();
    assertThat(evalAway("cot(pi)")).isEmpty();
    assertThat(evalAway("sec(pi/2)")).isEmpty();
    assertThat(evalAway("csc(pi)")).isEmpty();
    assertThat(evalAway("cos(pi/2)^(-1)")).isEmpty();
    assertThat(evalAway("tan(1)")).isPresent();
    assertThat(evalAway("(1/2)^(-1)")).isPresent();
  }

  @Test
  void growthBeyondEFiftyIsNoSample() {
    assertThat(eval("cos(50i)")).isPresent();
    assertThat(eval("cos(51i)")).isEmpty();
    assertThat(eval("sin(1 + 60i)")).isEmpty();
    assertThat(eval("cosh(51)")).isEmpty();
    assertThat(eval("sinh(-51)")).isEmpty();
    assertThat(eval("cosh(51i)")).isPresent();
  }

  /**
   * A power of 0 or 1 does not grow, so the bits bound does not refuse it: {@code 0^(x²)} at {@code
   * x = 75/4} is {@code 0^(5625/16)}, 0 (the rational set's no-new-pole property failed on it,
   * where {@code (0^x)^x} gave 0), and {@code (x - 71/4)^(x²)} there is {@code 1^(5625/16)}, 1. A
   * base of -1 stays bounded: its rational powers cost a reduction that grows with the exponent.
   */
  @Test
  void powersOfZeroAndOneAreNeverTooLarge() {
    Symbol x = Symbol.of("x");
    Map<Symbol, Numeric> point = Map.of(x, Rational.of(75, 4));
    Expr e = Canonical.of(Parser.parseExpression("0^(x^2)", Names.NONE));
    assertThat(NumericEvaluator.eval(e, point, MC)).contains(Rational.of(0, 1));
    Expr one = Canonical.of(Parser.parseExpression("(x - 71/4)^(x^2)", Names.NONE));
    assertThat(NumericEvaluator.eval(one, point, MC)).contains(Int.ONE);
    Expr minusOne = Canonical.of(Parser.parseExpression("(x - 79/4)^(x^2)", Names.NONE));
    assertThat(NumericEvaluator.eval(minusOne, point, MC)).isEmpty();
  }

  /**
   * An inexact base's power is bounded by its result's size, not by the base's digits: {@code
   * (y^(1/3))^(2b)} at {@code y = 2}, {@code b = -44} is {@code 2^(-88/3)}, about {@code 1.48e-9},
   * though the base's 34 digits times 88 pass 4,000 bits (the rational set's no-new-pole property
   * failed on it). An exact base keeps the bits bound, and an inexact result past 10⁴ in decimal
   * exponent stays empty.
   */
  @Test
  void anInexactPowerIsBoundedByItsResult() {
    Symbol y = Symbol.of("y");
    Symbol b = Symbol.of("b");
    Expr e = Canonical.of(Parser.parseExpression("(y^(1/3))^(2b)", Names.NONE));
    Map<Symbol, Numeric> point = Map.of(y, Rational.of(2, 1), b, Rational.of(-44, 1));
    Numeric v = NumericEvaluator.eval(e, point, MC).orElseThrow();
    Numeric reference = eval("2^(-88/3)").orElseThrow();
    BigDecimal got = v.re().toReal(MC).value();
    BigDecimal want = reference.re().toReal(MC).value();
    assertThat(got.subtract(want).abs()).isLessThan(want.abs().multiply(TOLERANCE));
    assertThat(
            NumericEvaluator.eval(e, Map.of(y, Rational.of(2, 1), b, Rational.of(-30000, 1)), MC))
        .isEmpty();
    Expr exact = Canonical.of(Parser.parseExpression("y^b", Names.NONE));
    assertThat(
            NumericEvaluator.eval(exact, Map.of(y, Rational.of(3, 1), b, Rational.of(4000, 1)), MC))
        .isEmpty();
  }

  @Test
  void theSignOfAnInexactValueIsInexact() {
    assertThat(eval("sign(-3)").orElseThrow().isExact()).isTrue();
    assertThat(eval("sign(pi - 3)").orElseThrow().isExact()).isFalse();
  }

  @Test
  void inverseFunctionsOnTheirCutsWithinRoundingAreEmptyAwayFromCuts() {
    // cos(acos(-13/2)) is -13/2 plus noise of either sign in its imaginary part.
    assertThat(eval("acos(cos(acos(-13/2)))")).isPresent();
    assertThat(evalAway("acos(cos(acos(-13/2)))")).isEmpty();
    assertThat(evalAway("acos(-13/2)")).isPresent();
    assertThat(evalAway("atan(2i + 0.0)")).isEmpty();
    assertThat(evalAway("asec(sin(pi))")).isEmpty();
  }

  @Test
  void zerosAndRootsOfZerosWithinRoundingAreEmptyAwayFromCuts() {
    assertThat(eval("cos(pi/2)")).isPresent();
    assertThat(evalAway("cos(pi/2)")).isEmpty();
    assertThat(evalAway("cos(1)")).isPresent();
    // An inexact value within 10^(20 - 34) of zero stands for rounding noise.
    assertThat(eval("root(1.0e-30, 4)")).isPresent();
    assertThat(evalAway("root(1.0e-30, 4)")).isEmpty();
    assertThat(evalAway("root(0.5, 4)")).isPresent();
    Map<Symbol, Numeric> tiny = Map.of(Symbol.of("x"), Real.of("1.0e-30"));
    Expr cubeRoot = Canonical.of(Parser.parseExpression("x^(1/3)", Names.NONE));
    assertThat(NumericEvaluator.eval(cubeRoot, tiny, MC)).isPresent();
    assertThat(NumericEvaluator.evalAwayFromCuts(cubeRoot, tiny, MC)).isEmpty();
    Map<Symbol, Numeric> exact = Map.of(Symbol.of("x"), Rational.of(1, 1000));
    assertThat(NumericEvaluator.evalAwayFromCuts(cubeRoot, exact, MC)).isPresent();
  }

  @Test
  void theOtherHyperbolicFunctionsAndTheirInverses() {
    close("coth(1)", "1.313035285499331303636161246930848", "0");
    close("sech(1)", "0.6480542736638853995749773532261503", "0");
    close("csch(1)", "0.8509181282393215451338427632871753", "0");
    close("acosh(2)", "1.316957896924816708625046347307968", "0");
    // acosh(1/2) is π/3·i, the engine's value off the real domain.
    close("acosh(1/2)", "0", "1.047197551196597746154214461093168");
    close("acoth(2)", "0.5493061443340548456976226184612629", "0");
    close("asech(1/2)", "1.316957896924816708625046347307968", "0");
    close("acsch(2)", "0.4812118250596034474977589134243684", "0");
    assertThat(eval("coth(0)")).isEmpty();
    assertThat(eval("csch(0)")).isEmpty();
    assertThat(eval("cosh(51)")).isEmpty();
    assertThat(eval("coth(51)")).isEmpty();
  }
}
