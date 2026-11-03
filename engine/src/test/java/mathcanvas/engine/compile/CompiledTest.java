package mathcanvas.engine.compile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import mathcanvas.engine.CancelledException;
import mathcanvas.engine.compile.UnsupportedException.Kind;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.eval.Definition;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Evaluator;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Piecewise;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

/** The semantics table of {@code engine.md} (<b>Compilation</b>) as cases, edge by edge. */
class CompiledTest {

  static final Symbol X = Symbol.of("x");
  static final Symbol Y = Symbol.of("y");
  static final Settings RADIANS = Settings.DEFAULT;
  static final Settings DEGREES = Settings.DEFAULT.withAngleMode(AngleMode.DEGREES);
  static final Settings GRADIANS = Settings.DEFAULT.withAngleMode(AngleMode.GRADIANS);

  /** The value of {@code text} with {@code x} free, in {@code settings}. */
  static Expr value(String text, Settings settings) {
    Environment env = Environment.EMPTY.withSettings(settings);
    return Evaluator.evaluate(Parser.parseExpression(text, env.names()), env, false).value();
  }

  static Compiled compile(String text, Settings settings) {
    return Compiled.compile(value(text, settings), List.of(X), settings);
  }

  static double at(String text, double x) {
    return compile(text, RADIANS).eval(x);
  }

  static double at(String text, double x, double y) {
    return Compiled.compile(value(text, RADIANS), List.of(X, Y), RADIANS).eval(x, y);
  }

  static double deg(String text, double x) {
    return compile(text, DEGREES).eval(x);
  }

  /** The engine's value of {@code text} at {@code x} (exactly the double), at 40 digits. */
  static double engine(String text, double x) {
    Environment env =
        Environment.EMPTY.with("x", new Definition.Value(new Num(Real.of(new BigDecimal(x)))));
    Expr v =
        Evaluator.evaluate(
                Parser.parseExpression("numeric(" + text + ", 40)", env.names()), env, false)
            .value();
    return ((RealNumeric) ((Num) v).value()).toDouble();
  }

  /** Agrees with the engine within a few ulps at {@code x}. */
  static void agrees(String text, double x) {
    double expected = engine(text, x);
    assertThat(at(text, x))
        .as("%s at %s", text, x)
        .isCloseTo(expected, within(1e-14 * Math.abs(expected)));
  }

  @Test
  void statisticsFunctions() {
    assertThat(at("erf(x)", 1)).isCloseTo(0.8427007929497149, within(1e-15));
    assertThat(at("gamma(x)", 5)).isCloseTo(24, within(1e-12));
    assertThat(at("gamma(x)", -2)).isNaN();
    assertThat(at("beta(x, 1/2)", 1 / 2.0)).isCloseTo(Math.PI, within(1e-14));
    // the closed form of the normal density, the call of the χ² density
    assertThat(at("normalpdf(x)", 0)).isCloseTo(1 / Math.sqrt(2 * Math.PI), within(1e-16));
    assertThat(at("chi2pdf(x, 2)", 2)).isCloseTo(Math.exp(-1) / 2, within(1e-16));
    assertThat(at("chi2pdf(x, 2)", -1)).isEqualTo(0);
    assertThat(at("chi2pdf(x, 1)", 0)).isNaN();
    assertThat(at("normalcdf(-inf, x)", 1.96)).isCloseTo(0.9750021048517795, within(1e-15));
    assertThat(at("normalcdf(x, inf)", 6)).isCloseTo(9.865876450376981e-10, within(1e-23));
    assertThat(at("tcdf(x, -x, 5)", 2)).isCloseTo(-0.8980605211701418, within(1e-15));
    assertThat(at("unifpdf(x, 0, 4)", 5)).isEqualTo(0);
    agrees("tpdf(x, 7/2)", 0.75);
    agrees("fpdf(x, 3, 5)", 1.25);
    agrees("chi2cdf(0, x, 4)", 3.0);
  }

  @Test
  void arithmetic() {
    assertThat(at("x^2 + 3x", 2)).isEqualTo(10);
    assertThat(at("x - y", 5, 3)).isEqualTo(2);
    assertThat(at("-x", 2)).isEqualTo(-2);
    assertThat(at("2 x y / 3", 3, 2)).isCloseTo(4, within(1e-15));
    assertThat(at("1/x", 0)).isNaN();
    assertThat(at("1/(x - 1)", 1)).isNaN();
    assertThat(at("x/y", 0, 0)).isNaN();
    assertThat(at("1/0 + x", 1)).isNaN();
  }

  @Test
  void powers() {
    assertThat(at("x^(1/3)", 8)).isCloseTo(2, within(1e-15));
    assertThat(at("x^(1/3)", -8)).as("the principal value is complex").isNaN();
    assertThat(at("x^(1/2)", -1)).isNaN();
    assertThat(at("x^(-1/2)", 4)).isEqualTo(0.5);
    assertThat(at("x^(-1/2)", 0)).isNaN();
    assertThat(at("x^(-2)", 0)).isNaN();
    assertThat(at("x^5", -2)).isEqualTo(-32);
    assertThat(at("x^pi", -1)).isNaN();
    assertThat(at("x^y", 0, 0)).isEqualTo(1);
    assertThat(at("x^y", 0, 2)).isEqualTo(0);
    assertThat(at("x^y", 0, -1)).isNaN();
    assertThat(at("x^y", -8, 2)).isEqualTo(64);
    assertThat(at("x^y", -8, 0.5)).isNaN();
    assertThat(at("x^y", Double.NaN, 0)).isNaN();
    assertThat(at("e^x", 1)).isCloseTo(Math.E, within(1e-15));
    assertThat(at("2^x", 10)).isEqualTo(1024);
    assertThat(at("exp(x)", 800)).as("overflow").isEqualTo(Double.POSITIVE_INFINITY);
    assertThat(at("exp(x)", -800)).as("underflow").isEqualTo(0);
  }

  @Test
  void integerExponentsBeyondAnIntKeepTheirParity() {
    assertThat(at("x^(2^60 + 1)", -1)).isEqualTo(-1);
    assertThat(at("x^(2^60)", -1)).isEqualTo(1);
    assertThat(at("x^(2^60 + 1)", 1)).isEqualTo(1);
    assertThat(at("x^(2^60 + 1)", 0)).isEqualTo(0);
    assertThat(at("x^(2^60 + 1)", -2)).isEqualTo(Double.NEGATIVE_INFINITY);
    assertThat(at("x^(2^60 + 1)", 0.5)).isEqualTo(0);
    assertThat(at("x^(-2^60 - 1)", 0)).isNaN();
    assertThat(at("x^(-2^60 - 1)", -1)).isEqualTo(-1);
    assertThat(at("x^(10^400)", 1)).as("an exponent beyond the double range").isEqualTo(1);
    assertThat(at("x^(10^400)", -1)).isEqualTo(1);
    assertThat(at("x^(10^400 + 1)", -1)).isEqualTo(-1);
    assertThat(at("x^(10^400 + 1)", 2)).isEqualTo(Double.POSITIVE_INFINITY);
    assertThat(at("x^(10^400 + 1)", -0.5)).isEqualTo(-0.0);
  }

  @Test
  void constantsBeyondTheTowersRangeAreEvaluatedInDouble() {
    assertThat(at("e^(10^30) + x", 1)).isEqualTo(Double.POSITIVE_INFINITY);
    assertThat(at("e^(-10^30) + x", 1)).isEqualTo(1);
    assertThat(at("x - e^(10^30)", 1)).isEqualTo(Double.NEGATIVE_INFINITY);
  }

  @Test
  void complexIntermediatesAreNaN() {
    assertThat(at("sqrt(x) sqrt(y)", -1, -1)).isNaN();
    assertThat(at("sqrt(x)^2", -4)).as("the canonical form makes it x").isEqualTo(-4);
  }

  @Test
  void roots() {
    assertThat(at("sqrt(x)", -1)).isNaN();
    assertThat(at("sqrt(x)", 0)).isEqualTo(0);
    assertThat(at("cbrt(x)", -8)).isEqualTo(-2);
    assertThat(at("root(x, 3)", -8)).isEqualTo(-2);
    assertThat(at("root(x, 4)", 16)).isCloseTo(2, within(1e-15));
    assertThat(at("root(x, 4)", -16)).isNaN();
    assertThat(at("root(x, 5)", -32)).isCloseTo(-2, within(1e-15));
    assertThat(at("root(x, 6)", 0)).isEqualTo(0);
    agrees("root(x, 7)", 1e-300);
  }

  @Test
  void rounding() {
    assertThat(at("abs(x)", -3)).isEqualTo(3);
    assertThat(at("sign(x)", -3)).isEqualTo(-1);
    assertThat(at("sign(x)", 0)).isEqualTo(0);
    assertThat(at("sign(x)", 0.5)).isEqualTo(1);
    assertThat(at("floor(x)", -2.5)).isEqualTo(-3);
    assertThat(at("ceil(x)", -2.5)).isEqualTo(-2);
    assertThat(at("trunc(x)", -2.7)).isEqualTo(-2);
    assertThat(at("trunc(x)", 2.7)).isEqualTo(2);
    assertThat(at("round(x)", -2.5)).isEqualTo(-3);
    assertThat(at("round(x)", 2.5)).isEqualTo(3);
    assertThat(at("round(x)", -0.5)).isEqualTo(-1);
    assertThat(at("round(x)", 0.49999999999999994)).isEqualTo(0);
    assertThat(at("round(x)", 1e300)).isEqualTo(1e300);
  }

  @Test
  void modulo() {
    assertThat(at("mod(x, y)", -7, 3)).isEqualTo(2);
    assertThat(at("mod(x, y)", 7, -3)).isEqualTo(-2);
    assertThat(at("mod(x, y)", 6, 3)).isEqualTo(0);
    assertThat(at("mod(x, y)", -7.5, 2)).isEqualTo(0.5);
    assertThat(at("mod(x, y)", 1, 0)).isNaN();
  }

  @Test
  void minAndMax() {
    assertThat(at("min(x, 2, -3)", 5)).isEqualTo(-3);
    assertThat(at("max(x, 2, -3)", 5)).isEqualTo(5);
    assertThat(at("min(x, sqrt(x))", -1)).isNaN();
  }

  @Test
  void integerFunctions() {
    // Evaluation takes the algebra's gcd of symbols (gcd(x, y) is 1), so the calls are built raw.
    Compiled gcd = Compiled.compile(Apply.of(Symbol.of("gcd"), X, Y), List.of(X, Y), RADIANS);
    assertThat(gcd.eval(12, 18)).isEqualTo(6);
    assertThat(gcd.eval(-12, 18)).isEqualTo(6);
    assertThat(gcd.eval(12.5, 18)).isNaN();
    assertThat(gcd.eval(0, 0)).isEqualTo(0);
    Compiled lcm = Compiled.compile(Apply.of(Symbol.of("lcm"), X, Y), List.of(X, Y), RADIANS);
    assertThat(lcm.eval(4, 6)).isEqualTo(12);
    assertThat(lcm.eval(3, 0)).isEqualTo(0);
    assertThat(lcm.eval(3, 0.5)).isNaN();
    assertThat(at("factorial(x)", 0)).isEqualTo(1);
    assertThat(at("factorial(x)", 5)).isEqualTo(120);
    assertThat(at("factorial(x)", 170)).isEqualTo(new BigDecimal(factorial(170)).doubleValue());
    assertThat(at("factorial(x)", 171)).isEqualTo(Double.POSITIVE_INFINITY);
    assertThat(at("factorial(x)", -1)).isNaN();
    assertThat(at("factorial(x)", 2.5)).as("the engine has no Gamma").isNaN();
    assertThat(at("binomial(x, y)", 5, 2)).isEqualTo(10);
    assertThat(at("binomial(x, y)", 5, 7)).isEqualTo(0);
    assertThat(at("binomial(x, y)", 5, -1)).isEqualTo(0);
    assertThat(at("binomial(x, y)", -3, 2)).isEqualTo(6);
    assertThat(at("binomial(x, y)", -3, 3)).isEqualTo(-10);
    assertThat(at("binomial(x, y)", 5.5, 2)).isNaN();
    double c = at("binomial(x, y)", 1000, 500);
    double exact =
        new BigDecimal(factorial(1000)).divide(new BigDecimal(factorial(500).pow(2))).doubleValue();
    assertThat(c).isCloseTo(exact, within(1e-12 * exact));
    assertThat(at("binomial(x, y)", 1e15, 5e14)).isEqualTo(Double.POSITIVE_INFINITY);
    for (int[] nk : new int[][] {{1028, 514}, {1029, 514}}) {
      double value =
          new BigDecimal(factorial(nk[0]))
              .divide(new BigDecimal(factorial(nk[1]).multiply(factorial(nk[0] - nk[1]))))
              .doubleValue();
      assertThat(at("binomial(x, y)", nk[0], nk[1]))
          .as("binomial(%d, %d) near the top of the range", nk[0], nk[1])
          .isCloseTo(value, within(1e-12 * value));
    }
  }

  private static BigInteger factorial(int n) {
    BigInteger f = BigInteger.ONE;
    for (int k = 2; k <= n; k++) {
      f = f.multiply(BigInteger.valueOf(k));
    }
    return f;
  }

  @Test
  void logarithms() {
    assertThat(at("ln(x)", 0)).isNaN();
    assertThat(at("ln(x)", -1)).isNaN();
    assertThat(at("log(x)", Math.E)).isEqualTo(1);
    assertThat(at("log(x, 2)", 8)).isCloseTo(3, within(1e-15));
    assertThat(at("log(8, x)", 1)).isNaN();
    assertThat(at("log(8, x)", 0)).isNaN();
    assertThat(at("log(8, x)", -2)).isNaN();
    assertThat(at("log(x, y)", 0, 2)).isNaN();
    assertThat(at("log10(x)", 1000)).isCloseTo(3, within(1e-15));
    assertThat(at("log10(x)", 0)).isNaN();
    assertThat(at("log2(x)", 8)).isCloseTo(3, within(1e-15));
    assertThat(at("log2(x)", 0)).isNaN();
  }

  @Test
  void circularInRadians() {
    assertThat(at("sin(x)", 1)).isEqualTo(Math.sin(1));
    assertThat(at("cos(x)", 1)).isEqualTo(Math.cos(1));
    assertThat(at("tan(x)", 1)).isEqualTo(Math.tan(1));
    assertThat(at("cot(x)", 0)).isNaN();
    assertThat(at("csc(x)", 0)).isNaN();
    assertThat(at("sec(x)", 0)).isEqualTo(1);
    agrees("cot(x)", 1e-10);
    agrees("sin(x)", 1e22);
  }

  @Test
  void inverseCircular() {
    assertThat(at("asin(x)", 2)).isNaN();
    assertThat(at("acos(x)", -1)).isEqualTo(Math.PI);
    assertThat(at("acot(x)", 0)).isEqualTo(Math.PI / 2);
    assertThat(at("acot(x)", -1)).isEqualTo(-Math.PI / 4);
    assertThat(at("asec(x)", 0.5)).isNaN();
    assertThat(at("asec(x)", 0)).isNaN();
    assertThat(at("asec(x)", -1)).isEqualTo(Math.PI);
    assertThat(at("asec(x)", 1)).isEqualTo(0);
    assertThat(at("acsc(x)", 1)).isEqualTo(Math.PI / 2);
    assertThat(at("acsc(x)", 0.5)).isNaN();
    for (double x : new double[] {2, -2, 1 + 1e-10, -(1 + 1e-10), 1.5, 1e200, -3}) {
      agrees("asec(x)", x);
      agrees("acsc(x)", x);
      agrees("acot(x)", x);
    }
    assertThat(at("atan2(x, y)", 0, 0)).isNaN();
    assertThat(at("atan2(x, y)", 0, -1)).isEqualTo(Math.PI);
    assertThat(at("atan2(x, y)", -0.0, -1)).isEqualTo(Math.PI);
    assertThat(at("atan2(x, y)", 1, 0)).isEqualTo(Math.PI / 2);
    assertThat(at("atan2(x, y)", 1, -0.0)).isEqualTo(Math.PI / 2);
  }

  @Test
  void hyperbolic() {
    assertThat(at("coth(x)", 0)).isNaN();
    assertThat(at("csch(x)", 0)).isNaN();
    assertThat(at("sech(x)", 0)).isEqualTo(1);
    assertThat(at("sech(x)", 1000)).isEqualTo(0);
    agrees("coth(x)", 1e-8);
    agrees("csch(x)", -3);
  }

  @Test
  void inverseHyperbolic() {
    assertThat(at("acosh(x)", 0.5)).isNaN();
    assertThat(at("acosh(x)", 1)).isEqualTo(0);
    assertThat(at("atanh(x)", 1)).isNaN();
    assertThat(at("atanh(x)", -1)).isNaN();
    assertThat(at("atanh(x)", 2)).isNaN();
    assertThat(at("acoth(x)", 1)).isNaN();
    assertThat(at("acoth(x)", 0.5)).isNaN();
    assertThat(at("asech(x)", 0)).isNaN();
    assertThat(at("asech(x)", 1)).isEqualTo(0);
    assertThat(at("asech(x)", 1.5)).isNaN();
    assertThat(at("asech(x)", -0.5)).isNaN();
    assertThat(at("acsch(x)", 0)).isNaN();
    for (double x : new double[] {1e-20, -0.3, 1.5, -2.5, 3e5, -1e300, 1e-310, -4.9e-324, 1e-9}) {
      agrees("asinh(x)", x);
      agrees("acsch(x)", x);
    }
    for (double x : new double[] {1 + 1e-12, 1.5, 2.5, 3e5, 1e300}) {
      agrees("acosh(x)", x);
      agrees("acoth(x)", x);
      agrees("acoth(x)", -x);
    }
    for (double x : new double[] {1e-20, 0.3, -0.7, 1 - 1e-12, -(1 - 1e-12)}) {
      agrees("atanh(x)", x);
    }
    for (double x : new double[] {4.9e-324, 1e-310, 1e-9, 1e-200, 0.3, 0.5, 0.75, 1 - 1e-12}) {
      agrees("asech(x)", x);
    }
  }

  @Test
  void partsOfARealValue() {
    assertThat(at("re(x)", -2)).isEqualTo(-2);
    assertThat(at("conj(x)", -2)).isEqualTo(-2);
    assertThat(at("im(x)", -2)).isEqualTo(0);
    assertThat(at("im(sqrt(x))", -1)).isNaN();
    assertThat(at("arg(x)", -2)).isEqualTo(Math.PI);
    assertThat(at("arg(x)", 0)).isEqualTo(0);
    assertThat(deg("arg(x)", -2)).as("arg is in radians in every mode").isEqualTo(Math.PI);
  }

  @Test
  void constantsFoldToTheNearestDouble() {
    assertThat(at("x + pi", 0)).isEqualTo(Math.PI);
    assertThat(at("x + e", 0)).isEqualTo(Math.E);
    assertThat(at("x + sqrt(2)", 0)).isEqualTo(Math.sqrt(2));
    assertThat(at("x + 1/3", 0)).isEqualTo(1.0 / 3);
    assertThat(at("x sin(pi/7)", 1)).isEqualTo(engine("sin(pi/7)", 0));
  }

  @Test
  void degreesConvertPlainArguments() {
    assertThat(deg("sin(x)", 30)).isCloseTo(0.5, within(1e-15));
    assertThat(deg("sin(x)", 1e10)).isCloseTo(Math.sin(Math.toRadians(280)), within(1e-15));
    assertThat(deg("sin(x)", -90)).isEqualTo(-1);
    assertThat(deg("asin(x)", 0.5)).isCloseTo(30, within(1e-13));
    assertThat(deg("acot(x)", 0)).isEqualTo(90);
    assertThat(compile("atan2(x, 1)", DEGREES).eval(1)).isCloseTo(45, within(1e-13));
    assertThat(deg("sin(sin(x))", 30))
        .as("an inner value is a plain number")
        .isCloseTo(Math.sin(Math.toRadians(0.5)), within(1e-15));
    assertThat(deg("sin(x + pi/7)", 30))
        .as("an argument holding π is read as radians")
        .isCloseTo(Math.sin(30 + Math.PI / 7), within(1e-15));
    assertThat(deg("sin(sqrt(2) x)", 1)).isCloseTo(Math.sin(Math.sqrt(2)), within(1e-15));
    assertThat(deg("sin(e^x)", 1)).isCloseTo(Math.sin(Math.E), within(1e-15));
    assertThat(deg("asin(x + pi/7)", 0.1)).isCloseTo(Math.asin(0.1 + Math.PI / 7), within(1e-15));
    assertThat(compile("sin(x)", GRADIANS).eval(50)).isCloseTo(Math.sqrt(0.5), within(1e-15));
    assertThat(compile("sin(x)", GRADIANS).eval(100)).isEqualTo(1);
  }

  @Test
  void quarterTurnsAreExact() {
    assertThat(deg("sin(x)", 180)).isEqualTo(0);
    assertThat(deg("cos(x)", 90)).isEqualTo(0);
    assertThat(deg("cos(x)", 180)).isEqualTo(-1);
    assertThat(deg("tan(x)", 45)).isCloseTo(1, within(1e-15));
    assertThat(deg("tan(x)", 90)).isNaN();
    assertThat(deg("tan(x)", -270)).isNaN();
    assertThat(deg("tan(x)", 180)).isEqualTo(0);
    assertThat(deg("cot(x)", 90)).isEqualTo(0);
    assertThat(deg("cot(x)", 0)).isNaN();
    assertThat(deg("cot(x)", 180)).isNaN();
    assertThat(deg("sec(x)", 90)).isNaN();
    assertThat(deg("sec(x)", 360)).isEqualTo(1);
    assertThat(deg("csc(x)", 180)).isNaN();
    assertThat(deg("csc(x)", 270)).isEqualTo(-1);
    assertThat(compile("tan(x)", GRADIANS).eval(100)).isNaN();
  }

  @Test
  void refusals() {
    assertRefused(Apply.of(Symbol.of("f"), X), Kind.FUNCTION, "f");
    assertRefused(Apply.of(Symbol.of("numerator"), X), Kind.FUNCTION, "numerator");
    assertRefused(Apply.of(Symbol.of("solve"), X, X), Kind.FUNCTION, "solve");
    assertRefused(value("root(x, y)", RADIANS), Kind.FUNCTION, "root");
    assertRefused(Apply.of(Symbol.of("root"), X, Num.of(0)), Kind.FUNCTION, "root");
    assertRefused(value("x + i", RADIANS), Kind.COMPLEX, "i");
    assertRefused(value("x + sqrt(-2)", RADIANS), Kind.COMPLEX, "i*sqrt(2)");
    assertRefused(value("x + inf", RADIANS), Kind.FORM, "infinity");
    assertThatThrownBy(
            () -> Compiled.compile(value("x + sin(e^(10^30)) + i", RADIANS), List.of(X), RADIANS))
        .isInstanceOfSatisfying(
            UnsupportedException.class, u -> assertThat(u.kind()).isEqualTo(Kind.COMPLEX));
    assertRefused(
        value("sin(e^(10^30)) + x", RADIANS), Kind.FORM, "sin(e^1000000000000000000000000000000)");
    assertThatThrownBy(() -> Compiled.compile(value("x + inf", RADIANS), List.of(X), RADIANS))
        .hasMessage("cannot plot infinity");
    assertRefused(value("a x", RADIANS), Kind.FREE_SYMBOL, "a");
    assertRefused(Relation.of(Relation.Op.LT, X, Num.ONE), Kind.FORM, "relation");
    assertRefused(ListExpr.of(X), Kind.FORM, "list");
    assertRefused(Vector.of(X), Kind.FORM, "vector");
    assertRefused(
        Piecewise.of(List.of(new Piecewise.Piece(X, Relation.of(Relation.Op.LT, X, Num.ONE))), X),
        Kind.FORM,
        "piecewise expression");
  }

  private static void assertRefused(Expr e, Kind kind, String subject) {
    assertThatThrownBy(() -> Compiled.compile(e, List.of(X, Y), RADIANS))
        .isInstanceOfSatisfying(
            UnsupportedException.class,
            u -> {
              assertThat(u.kind()).isEqualTo(kind);
              assertThat(u.subject()).isEqualTo(subject);
            });
  }

  @Test
  void undefinedIsNaNEverywhere() {
    Compiled c = Compiled.compile(value("1/0", RADIANS), List.of(X), RADIANS);
    assertThat(c.eval(1)).isNaN();
  }

  @Test
  void argumentsAreTakenInOrder() {
    Compiled c = Compiled.compile(value("x - y", RADIANS), List.of(Y, X), RADIANS);
    assertThat(c.arguments()).containsExactly(Y, X);
    assertThat(c.eval(1, 5)).isEqualTo(4);
    Compiled none = Compiled.compile(value("2 + 3", RADIANS), List.of(), RADIANS);
    assertThat(none.eval()).isEqualTo(5);
  }

  @Test
  void aWrongCountOfValuesOrARepeatedArgumentIsAnError() {
    Compiled c = compile("x", RADIANS);
    assertThatThrownBy(() -> c.eval(1, 2)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Compiled.compile(X, List.of(X, X), RADIANS))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void compilationStopsOnRequest() {
    Expr e = value("sin(x) + cos(x)^2", RADIANS);
    assertThatThrownBy(() -> Compiled.compile(e, List.of(X), RADIANS, () -> true))
        .isInstanceOf(CancelledException.class);
  }
}
