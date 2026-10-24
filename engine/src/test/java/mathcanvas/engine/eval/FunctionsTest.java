package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.DEGREES;
import static mathcanvas.engine.eval.Run.GRADIANS;
import static mathcanvas.engine.eval.Run.canonical;
import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static mathcanvas.engine.eval.Run.value;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.number.Real;
import org.junit.jupiter.api.Test;

class FunctionsTest {

  /** The decimal value of a result, which must be a real decimal. */
  static BigDecimal decimal(String script, Settings settings) {
    Expr v = value(script, settings);
    assertThat(v)
        .as(script)
        .isInstanceOfSatisfying(Num.class, n -> assertThat(n.value()).isInstanceOf(Real.class));
    return ((Real) ((Num) v).value()).value();
  }

  static BigDecimal decimal(String script) {
    return decimal(script, Settings.DEFAULT);
  }

  static void close(BigDecimal actual, String expected) {
    assertThat(actual.subtract(new BigDecimal(expected)).abs())
        .isLessThanOrEqualTo(new BigDecimal("1e-32"));
  }

  @Test
  void exactArgumentsGiveExactResultsOnly() {
    assertThat(value("abs(-3)")).isEqualTo(Num.of(3));
    assertThat(value("abs(3 + 4i)")).isEqualTo(Num.of(5));
    assertThat(shown("abs(1 + i)")).isEqualTo("√2");
    assertThat(value("sign(-7/2)")).isEqualTo(Num.of(-1));
    assertThat(value("floor(7/2)")).isEqualTo(Num.of(3));
    assertThat(value("ceil(7/2)")).isEqualTo(Num.of(4));
    assertThat(value("round(-5/2)")).isEqualTo(Num.of(-3));
    assertThat(value("trunc(-7/2)")).isEqualTo(Num.of(-3));
    assertThat(value("mod(-7, 3)")).isEqualTo(Num.of(2));
    assertThat(value("mod(7/2, 2)")).isEqualTo(canonical("3/2"));
    assertThat(value("gcd(12, 18, 8)")).isEqualTo(Num.of(2));
    assertThat(value("lcm(4, 6)")).isEqualTo(Num.of(12));
    assertThat(value("5!")).isEqualTo(Num.of(120));
    assertThat(value("binomial(5, 2)")).isEqualTo(Num.of(10));
    assertThat(value("binomial(-3, 2)")).isEqualTo(Num.of(6));
    assertThat(value("min(3, 1, 2)")).isEqualTo(Num.of(1));
    assertThat(value("max(1/2, 1/3)")).isEqualTo(canonical("1/2"));
    assertThat(value("re(3 + 4i)")).isEqualTo(Num.of(3));
    assertThat(value("im(3 + 4i)")).isEqualTo(Num.of(4));
    assertThat(value("conj(3 + 4i)")).isEqualTo(canonical("3 - 4i"));
    assertThat(value("arg(-2)")).isEqualTo(Constant.PI);
    assertThat(value("arg(2)")).isEqualTo(Num.ZERO);
    assertThat(value("numerator(6/4)")).isEqualTo(Num.of(3));
    assertThat(value("denominator(6/4)")).isEqualTo(Num.of(2));
    assertThat(value("cbrt(-8)")).isEqualTo(Num.of(-2));
    assertThat(value("root(16, 4)")).isEqualTo(Num.of(2));
    assertThat(value("sqrt(9/4)")).isEqualTo(canonical("3/2"));
  }

  @Test
  void exactArgumentsWithoutAnExactResultStay() {
    for (String stays :
        new String[] {
          "sin(1)",
          "exp(2)",
          "log(8, 2)",
          "cbrt(2)",
          "root(2, 3)",
          "acos(0)",
          "arg(1 + i)",
          "gcd(1/2, 2)",
          "factorial(1/2)",
          "factorial(100000)",
          "sign(i)",
          "floor(i)"
        }) {
      assertThat(value(stays)).as(stays).isEqualTo(canonical(stays));
    }
  }

  @Test
  void theValuesAtZeroAndOne() {
    assertThat(value("sin(0)")).isEqualTo(Num.ZERO);
    assertThat(value("tan(0)")).isEqualTo(Num.ZERO);
    assertThat(value("cos(0)")).isEqualTo(Num.ONE);
    assertThat(value("sec(0)")).isEqualTo(Num.ONE);
    assertThat(value("cot(0)")).isEqualTo(Constant.UNDEFINED);
    assertThat(value("csc(0)")).isEqualTo(Constant.UNDEFINED);
    assertThat(value("sinh(0) + tanh(0) + asinh(0) + atanh(0) + asin(0) + atan(0)"))
        .isEqualTo(Num.ZERO);
    assertThat(value("cosh(0) + sech(0)")).isEqualTo(Num.of(2));
    assertThat(value("coth(0)")).isEqualTo(Constant.UNDEFINED);
    assertThat(value("acos(1) + acosh(1)")).isEqualTo(Num.ZERO);
    assertThat(value("exp(0)")).isEqualTo(Num.ONE);
    assertThat(value("log(1) + ln(1) + log10(1) + log2(1)")).isEqualTo(Num.ZERO);
    assertThat(value("log(5, 5)")).isEqualTo(Num.ONE);
    assertThat(value("atan2(1, 0)")).isEqualTo(canonical("pi/2"));
    assertThat(value("atan2(-1, 0)")).isEqualTo(canonical("-pi/2"));
    assertThat(value("atan2(0, -1)")).isEqualTo(Constant.PI);
    assertThat(value("atan2(0, 1)")).isEqualTo(Num.ZERO);
  }

  @Test
  void domainErrorsAreUndefined() {
    for (String undefined :
        new String[] {
          "mod(1, 0)", "factorial(-1)", "root(-4, 2)", "log(0)", "ln(0)", "log(2, 1)",
          "log(0, 2)", "atan2(0, 0)", "log(0.0)", "atanh(1.0)", "cot(0.0)", "asec(0.0)",
          "mod(1.5, 0)", "sin(undefined)"
        }) {
      assertThat(value(undefined)).as(undefined).isEqualTo(Constant.UNDEFINED);
    }
  }

  @Test
  void inexactArgumentsEvaluateAtThePrecision() {
    close(decimal("sin(0.5)"), "0.4794255386042030002732879352155713880818");
    close(decimal("cot(1.0)"), "0.6420926159343307030064199865942656202302");
    close(decimal("asinh(0.5)"), "0.4812118250596034474977589134243684231352");
    close(decimal("atanh(0.5)"), "0.5493061443340548456976226184612628376494");
    close(decimal("acoth(2.0)"), "0.5493061443340548456976226184612628376494");
    close(decimal("atan2(1.0, -1.0)"), "2.356194490192344928846982537459627163148");
    close(decimal("log(8.0, 2)"), "3");
    close(decimal("log10(1000.0)"), "3");
    close(decimal("exp(1.0)"), "2.718281828459045235360287471352662497757");
    assertThat(decimal("asinh(1e-30)")).isEqualByComparingTo("1e-30");
    assertThat(decimal("round(-2.5)")).isEqualByComparingTo("-3");
    assertThat(decimal("sign(-2.5)")).isEqualByComparingTo("-1");
    assertThat(decimal("factorial(5.0)")).isEqualByComparingTo("120");
    assertThat(decimal("max(1, 1.0)")).isEqualByComparingTo("1");
    assertThat(decimal("sin(0.5)").precision()).isEqualTo(34);
    assertThat(decimal("sin(0.5)", Settings.DEFAULT.withPrecision(10)))
        .isEqualByComparingTo("0.4794255386");
  }

  @Test
  void inexactModIsExactThenRounded() {
    assertThat(decimal("mod(1e100, 3.0)")).isEqualByComparingTo("1");
    assertThat(decimal("mod(-1e100, 3.0)")).isEqualByComparingTo("2");
    assertThat(decimal("mod(7.5, 2)")).isEqualByComparingTo("1.5");
    assertThat(decimal("mod(1e30, 0.7)")).isBetween(BigDecimal.ZERO, new BigDecimal("0.7"));
  }

  @Test
  void integerValuedDecimalsInIntegerFunctions() {
    assertThat(decimal("binomial(5.0, 2)")).isEqualByComparingTo("10");
    assertThat(decimal("gcd(4.0, 6)")).isEqualByComparingTo("2");
    assertThat(decimal("lcm(4.0, 6)")).isEqualByComparingTo("12");
    assertThat(value("gcd(4.5, 6)")).isEqualTo(canonical("gcd(4.5, 6)"));
    assertThat(value("factorial(1e6)")).isEqualTo(canonical("factorial(1e6)"));
    assertThat(value("numerator(0.5)")).isEqualTo(canonical("numerator(0.5)"));
  }

  @Test
  void reciprocalInverseHyperbolicsKeepTheirDigits() {
    // atanh(y) and asinh(y) differ from y = 1/(3·10^20) only far below the 34th digit.
    assertThat(decimal("acoth(3e20)"))
        .isEqualByComparingTo("3.333333333333333333333333333333333e-21");
    assertThat(decimal("acsch(3e20)"))
        .isEqualByComparingTo("3.333333333333333333333333333333333e-21");
  }

  @Test
  void aRealArgumentOutsideTheDomainTakesThePrincipalValue() {
    String[][] table = {
      {"atanh(2.0)", "0.5493061443340548456976226184612629 - 1.570796326794896619231321691639751i"},
      {
        "atanh(-2.0)",
        "-0.5493061443340548456976226184612629 + 1.570796326794896619231321691639751i"
      },
      {"acoth(0.5)", "0.5493061443340548456976226184612629 - 1.570796326794896619231321691639751i"},
      {
        "acoth(-0.5)",
        "-0.5493061443340548456976226184612629 + 1.570796326794896619231321691639751i"
      },
      {"acosh(0.5)", "1.047197551196597746154214461093168i"},
      {"acosh(-2.0)", "1.316957896924816708625046347307968 + 3.141592653589793238462643383279503i"},
      {"asech(2.0)", "1.047197551196597746154214461093168i"},
      {"asin(2.0)", "1.570796326794896619231321691639751 - 1.316957896924816708625046347307968i"},
      {"acos(2.0)", "1.316957896924816708625046347307968i"},
      {"log(-1.0)", "3.141592653589793238462643383279503i"},
    };
    for (String[] row : table) {
      assertThat(shown(row[0])).as(row[0]).isEqualTo(row[1]);
    }
  }

  @Test
  void zerosAreAcceptedOnlyWhereTheValueIsZero() {
    assertThat(decimal("acosh(1." + "0".repeat(139) + "1)"))
        .isEqualByComparingTo("1.414213562373095048801688724209698e-70");
    assertThat(decimal("asech(0." + "9".repeat(140) + ")"))
        .isEqualByComparingTo("1.414213562373095048801688724209698e-70");
    assertThat(decimal("acosh(1.0)")).isEqualByComparingTo("0");
    assertThat(decimal("atanh(0.0)")).isEqualByComparingTo("0");
  }

  @Test
  void acoshOfARealInTheUnitIntervalIsImaginary() {
    assertThat(shown("acosh(0.5)")).isEqualTo("1.047197551196597746154214461093168i");
  }

  @Test
  void outOfRangeIsAnError() {
    assertThat(error("sin(1e10000)")).isEqualTo("number out of range at this precision");
    assertThat(error("exp(1e20)")).isEqualTo("number out of range at this precision");
  }

  @Test
  void minAndMaxOrderRealsOnly() {
    assertThat(error("min(1, i)")).isEqualTo("complex numbers are not ordered");
  }

  // §14.2: angle mode.

  @Test
  void degreesConvertPlainNumbersOnce() {
    assertThat(value("sin(30)", DEGREES)).isEqualTo(canonical("sin(pi/6)"));
    assertThat(value("sin(30°)", DEGREES)).isEqualTo(canonical("sin(pi/6)"));
    assertThat(value("sin(pi)", DEGREES)).isEqualTo(canonical("sin(pi)"));
    assertThat(value("sin(15 + 30°)", DEGREES)).isEqualTo(canonical("sin(15 + pi/6)"));
    assertThat(value("a := 30; sin(a)", DEGREES)).isEqualTo(canonical("sin(pi/6)"));
    assertThat(value("sin(0)", DEGREES)).isEqualTo(Num.ZERO);
    assertThat(value("sin(30)")).isEqualTo(canonical("sin(30)"));
    assertThat(value("sin(50)", GRADIANS)).isEqualTo(canonical("sin(pi/4)"));
  }

  @Test
  void substitutedValuesAreNotEvaluatedAgain() {
    assertThat(shown("f(t) := t; f(asin(1/2))", DEGREES)).isEqualTo("180 asin(1/2)/π");
    assertThat(shown("subs(t, t = asin(1/2))", DEGREES)).isEqualTo("180 asin(1/2)/π");
    assertThat(shown("f(t) := 2t; f(sin(30))", DEGREES)).isEqualTo("2 sin(π/6)");
  }

  @Test
  void inverseFunctionsAnswerInTheMode() {
    assertThat(value("asin(1/2)", DEGREES)).isEqualTo(canonical("180 asin(1/2)/pi"));
    assertThat(value("asin(1/2)")).isEqualTo(canonical("asin(1/2)"));
    assertThat(value("atan2(1, 0)", DEGREES)).isEqualTo(Num.of(90));
    assertThat(value("atan2(1, 0)", GRADIANS)).isEqualTo(Num.of(100));
    assertThat(decimal("asin(0.5)", DEGREES)).isEqualByComparingTo("30");
    close(decimal("sin(30.0)", DEGREES), "0.5");
    close(decimal("cos(100.0)", GRADIANS), "0");
  }

  @Test
  void numericValuesInDegrees() {
    close(decimal("numeric(sin(30))", DEGREES), "0.5");
    assertThat(decimal("numeric(asin(1/2))", DEGREES)).isEqualByComparingTo("30");
  }
}
