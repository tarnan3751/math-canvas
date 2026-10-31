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
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.statistics.Statistics;

/**
 * The oracle over products of large hyperbolic factors divided by one of them, {@code c·u·(u^m +
 * d)/v} with {@code d ≠ 0}: the value is near {@code c·u^m}, in range, while the factors before the
 * division can pass the double range. Such a point agrees or is skipped as ill-conditioned; it
 * never fails.
 */
class CompileOracleProperties {

  private static final Symbol X = Symbol.of("x");

  @Property(tries = 500)
  void anOverflowBetweenLargeHyperbolicFactorsIsNoFailure(
      @ForAll boolean coshFirst,
      @ForAll @IntRange(min = 1, max = 3) int m,
      @ForAll @IntRange(min = -5, max = 5) int d,
      @ForAll @IntRange(min = 1, max = 30) int c,
      @ForAll @DoubleRange(min = 0.75, max = 1.0) double fraction,
      @ForAll boolean degrees) {
    // With d = 0 the product is u^(m+1)/v, whose power is past the range itself.
    Assume.that(d != 0);
    String u = coshFirst ? "cosh(x)" : "sinh(x)";
    String v = coshFirst ? "sinh(x)" : "cosh(x)";
    Expr e =
        Canonical.of(
            Parser.parseExpression(
                c + "·" + u + "·(" + u + "^" + m + " + " + d + ")/" + v, Names.NONE));
    // u^m stays below the double range, near it; a hyperbolic function's argument is plain in
    // every angle mode.
    double x = fraction * 700 / m;
    Settings settings =
        Settings.DEFAULT.withAngleMode(degrees ? AngleMode.DEGREES : AngleMode.RADIANS);
    Outcome outcome =
        CompileOracle.check(
            e, List.of(X), settings, Compiled.compile(e, List.of(X), settings), new double[] {x});
    Statistics.label("outcome").collect(outcome);
    assertThat(outcome).isIn(Outcome.AGREE, Outcome.ILL_CONDITIONED);
  }
}
