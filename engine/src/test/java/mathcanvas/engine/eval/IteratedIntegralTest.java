package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Rewriter;
import org.junit.jupiter.api.Test;

/**
 * Iterated integrals: {@code integrate(f, [x, a, b], [y, c, d], …)} is the nested integrals, the
 * first bracket innermost, evaluated inside out with an {@code integrate.iterated} step per inner
 * level.
 */
class IteratedIntegralTest {

  @Test
  void theBracketFormIsTheNestedIntegrals() {
    assertThat(Run.value("integrate(x y, [x, 0, 1], [y, 0, 2])"))
        .isEqualTo(Run.value("∫[0, 2] ∫[0, 1] x y dx dy"));
    assertThat(shown("integrate(x^2 + y, [x, 0, y], [y, 0, 1])")).isEqualTo("5/12");
    assertThat(shown("integrate(x y z, [x, 0, 1], [y, 0, 2], [z, 0, 3])")).isEqualTo("9/2");
    assertThat(shown("integrate(r, [r, 0, 2], [θ, 0, 2π])")).isEqualTo("4π");
    assertThat(shown("integrate(x, [x, 0, 1])")).isEqualTo("1/2");
    assertThatThrownBy(() -> Parser.parseExpression("integrate(x, x, 0)", Names.NONE))
        .hasMessage("integrate takes 2 or 4 arguments, or an integrand followed by brackets");
  }

  @Test
  void eachInnerLevelIsAStep() {
    assertThat(NarrationTest.narrated("steps(∫[0,1] ∫[0,x] x y dy dx)").getFirst())
        .startsWith("Integrate the inner integral first: ∫[0, x] x y dy = x³/2");
    Outcome.Value v =
        (Outcome.Value) Run.last("steps(integrate(x y z, [x, 0, 1], [y, 0, 2], [z, 0, 3]))");
    TraceChecks.assertChain(new Rewriter.Result(v.value(), v.trace()));
    assertThat(v.trace().rewrites().stream().filter(r -> r.rule().equals("iterated")).count())
        .isEqualTo(2);
  }

  @Test
  void anOuterRangeIsAFactOfTheInnerLevelsAndEqualBoundsAreZero() {
    assertThat(shown("integrate(sqrt(y^2), [x, 0, 1], [y, 0, 2])")).isEqualTo("2");
    // the brackets are read in order: the outer bound x is a free x
    assertThat(shown("integrate(x y, [x, 0, 1], [y, 0, x])")).isEqualTo("x²/4");
    assertThat(shown("integrate(e^(x^2), x, 0, 0)")).isEqualTo("0");
    assertThat(shown("integrate(e^(x^2), [x, 0, 0])")).isEqualTo("0");
  }

  @Test
  void aLevelWithNoClosedFormStays() {
    assertThat(Run.value("∫[0,1] ∫[0,1] e^(-x^2 y) dx dy")).isInstanceOf(Integral.class);
  }
}
