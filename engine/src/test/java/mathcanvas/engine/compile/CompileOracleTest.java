package mathcanvas.engine.compile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.compile.CompileOracle.Outcome;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

/** The oracle's outcomes where a compiled value overflows. */
class CompileOracleTest {

  private static final Symbol X = Symbol.of("x");
  private static final Settings DEGREES = Settings.DEFAULT.withAngleMode(AngleMode.DEGREES);

  private static Outcome at(String text, Settings settings, double x) {
    Expr e = Canonical.of(Parser.parseExpression(text, Names.NONE));
    return CompileOracle.check(
        e, List.of(X), settings, Compiled.compile(e, List.of(X), settings), new double[] {x});
  }

  /**
   * The product passes the double range between its factors, cosh(270)³ before the division by sinh
   * 270, while its value, 7.444e240, is in range: no evidence, skipped as an ill-conditioned point
   * (the degrees property met it).
   */
  @Test
  void anOverflowBetweenTheFactorsIsIllConditioned() {
    assertThat(at("27·12^(1/3)·cosh(x)·(2cosh(x)^2 - 2)·x^2/sinh(x)", DEGREES, 270))
        .isEqualTo(Outcome.ILL_CONDITIONED);
  }

  /** An infinite compiled value whose exact value is past the double range still agrees. */
  @Test
  void anInfinityPastTheRangeIsOutOfRange() {
    assertThat(at("cosh(x)^3", DEGREES, 270)).isEqualTo(Outcome.OUT_OF_RANGE);
  }

  /** In range throughout, the same product agrees. */
  @Test
  void theProductInRangeAgrees() {
    assertThat(at("27·12^(1/3)·cosh(x)·(2cosh(x)^2 - 2)·x^2/sinh(x)", DEGREES, 90))
        .isEqualTo(Outcome.AGREE);
  }
}
