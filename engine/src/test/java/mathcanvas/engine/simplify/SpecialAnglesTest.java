package mathcanvas.engine.simplify;

import static mathcanvas.engine.simplify.RuleCases.parse;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.testing.NumericEvaluator;
import org.junit.jupiter.api.Test;

/**
 * The exact values of the trig set against the numeric evaluator at 34 digits: every circular
 * function at every multiple of {@code π/12} in two turns either way and far out, and every inverse
 * function at its table's values and their negatives.
 */
class SpecialAnglesTest {

  private static final MathContext MC = MathContext.DECIMAL128;
  private static final BigDecimal TOLERANCE = new BigDecimal("1e-30");

  private static Expr trig(String source) {
    return Rewriter.rewrite(parse(source), RuleSets.TRIG, Context.DEFAULT).expr();
  }

  private static void agrees(String source, Expr exact) {
    agrees(source, exact, MC);
  }

  /** Agreement to 30 digits, the angle evaluated at {@code mc}. */
  private static void agrees(String source, Expr exact, MathContext mc) {
    Numeric expected =
        NumericEvaluator.eval(Canonical.of(parse(source)), Map.of(), mc).orElseThrow();
    Numeric actual = NumericEvaluator.eval(exact, Map.of(), MC).orElseThrow();
    BigDecimal difference = expected.subtract(actual, MC).modulus(MC).toReal(MC).value();
    BigDecimal scale = BigDecimal.ONE.max(expected.modulus(MC).toReal(MC).value());
    assertThat(difference).as("%s = %s", source, exact).isLessThan(TOLERANCE.multiply(scale));
  }

  @Test
  void circularFunctionsAtMultiplesOfATwelfthOfPi() {
    for (String f : List.of("sin", "cos", "tan", "cot", "sec", "csc")) {
      for (long k = -24; k <= 24; k++) {
        check(f, k);
      }
      for (long k : new long[] {1_000_001, -999_997}) {
        check(f, k);
      }
      // Beyond the evaluator's angles, the value is the one at the angle reduced by two turns.
      long k = 123_456_789_012_345L;
      assertThat(trig(f + "(" + k + " pi/12)"))
          .isEqualTo(trig(f + "(" + Math.floorMod(k, 24) + " pi/12)"));
    }
  }

  private static void check(String f, long k) {
    String source = f + "(" + k + " pi/12)";
    Expr exact = trig(source);
    long r = Math.floorMod(k, 12);
    boolean pole =
        switch (f) {
          case "tan", "sec" -> r == 6;
          case "cot", "csc" -> r == 0;
          default -> false;
        };
    if (pole) {
      assertThat(exact).as(source).isEqualTo(Constant.UNDEFINED);
    } else {
      assertThat(exact.toString()).as(source).doesNotContain(f);
      // A far-out angle carries its integer digits on top of the 34 compared.
      agrees(source, exact, new MathContext(34 + Long.toString(Math.abs(k)).length()));
    }
  }

  @Test
  void inverseFunctionsAtTheirTablesValues() {
    List<String> values =
        List.of(
            "0",
            "1/2",
            "sqrt(2)/2",
            "sqrt(3)/2",
            "1",
            "(sqrt(6) - sqrt(2))/4",
            "(sqrt(6) + sqrt(2))/4",
            "2 - sqrt(3)",
            "sqrt(3)/3",
            "sqrt(3)",
            "2 + sqrt(3)",
            "2",
            "sqrt(2)",
            "2 sqrt(3)/3",
            "sqrt(6) - sqrt(2)",
            "sqrt(6) + sqrt(2)");
    int exactValues = 0;
    for (String f : List.of("asin", "acos", "atan", "acot", "asec", "acsc")) {
      for (String v : values) {
        for (String source : List.of(f + "(" + v + ")", f + "(-(" + v + "))")) {
          Expr exact = trig(source);
          if (!exact.toString().contains(f)) {
            exactValues++;
            agrees(source, exact);
          }
        }
      }
    }
    // Each table value and its negative: seven for asin and acos, six for the others.
    assertThat(exactValues).isEqualTo(2 * (7 + 7 + 6 + 6 + 6 + 6));
  }
}
