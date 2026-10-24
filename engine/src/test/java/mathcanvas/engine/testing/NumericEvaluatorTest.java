package mathcanvas.engine.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.engine.eval.Outcome;
import mathcanvas.engine.eval.Session;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

/**
 * The functions the numeric evaluator computes by formula agree with the evaluator's own inexact
 * values, inside and outside their real domains.
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
}
