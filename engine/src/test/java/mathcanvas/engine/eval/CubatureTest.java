package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.MathContext;
import java.util.List;
import java.util.Random;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.compile.Compiled;
import mathcanvas.engine.compile.Quadrature;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Rewrite;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.statistics.Statistics;
import org.junit.jupiter.api.Test;

/**
 * The numeric path of an iterated integral (nested Gauss–Kronrod in {@code double}), and the
 * acceptance criterion of Phase 7: symbolic multiple integrals agree with nested quadrature within
 * {@code 1e-8}.
 */
class CubatureTest {

  private static double number(Expr e) {
    return ((RealNumeric) ((Num) e).value()).toReal(MathContext.DECIMAL64).value().doubleValue();
  }

  @Test
  void anIteratedIntegralWithNoClosedFormIsNumericToTwelveDigitsAtMost() {
    Outcome.Value v = (Outcome.Value) Run.last("numeric(∫[0,1] ∫[0,1] e^(-x^2 y) dx dy, 10)");
    assertThat(Run.shown("numeric(∫[0,1] ∫[0,1] e^(-x^2 y) dx dy, 10)")).isEqualTo("0.8615277068");
    assertThat(v.numeric()).isTrue();
    // the triple integral of e^(-x²-y²-z²) over the unit cube is (∫[0,1] e^(-x²) dx)³
    assertThat(
            Run.shown(
                "numeric(integrate(e^(-x^2 - y^2 - z^2), [x, 0, 1], [y, 0, 1], [z, 0, 1]), 12)"))
        .isEqualTo("0.416538385887");
    Outcome.Value capped =
        (Outcome.Value) Run.last("steps(numeric(integrate(1, [x, 0, sqrt(1 - y^2)], [y, -1, 1])))");
    Rewrite step = capped.trace().rewrites().getLast();
    assertThat(step.rule()).isEqualTo("cubature-capped");
    assertThat(Run.shown("numeric(integrate(1, [x, 0, sqrt(1 - y^2)], [y, -1, 1]), 20)"))
        .isEqualTo("1.57079632679");
  }

  @Test
  void aSingularOrInfiniteIteratedIntegralStays() {
    assertThat(Run.value("numeric(∫[0,1] ∫[0,1] 1/(x - y) dx dy, 8)")).isInstanceOf(Integral.class);
    assertThat(Run.value("numeric(∫[0,∞] ∫[0,1] e^(-x y^2) dx dy, 8)"))
        .isInstanceOf(Integral.class);
  }

  @Test
  void anIntegralPastTheSampleBudgetIsLeft() {
    // review-28: 3 min 24 s before the budget; the budget leaves it within seconds
    Outcome.Value v =
        (Outcome.Value)
            Run.last(
                "steps(numeric(integrate(abs(x - y)^(1/3) abs(y - z)^(1/3) abs(x - z)^(1/3),"
                    + " [x, 0, 1], [y, 0, 1], [z, 0, 1]), 12))");
    assertThat(v.value()).isInstanceOf(Integral.class);
    assertThat(v.trace().rewrites().getLast().rule()).isEqualTo("none");
  }

  private static final List<String> INTEGRANDS =
      List.of("x^2 y", "x + y^2", "x y^3 - 2x", "e^(x + y)", "sin(x) cos(y)", "x e^y", "y/(1 + x)");

  /** Symbolic iterated integrals agree with nested Gauss–Kronrod within 1e-8 (Phase 7). */
  @Property(tries = 60)
  void symbolicMultipleIntegralsAgreeWithNestedQuadrature(@ForAll long seed) {
    Random r = new Random(seed);
    String f = INTEGRANDS.get(r.nextInt(INTEGRANDS.size()));
    int c = r.nextInt(3);
    int d = c + 1 + r.nextInt(3);
    boolean variable = r.nextBoolean();
    String inner = variable ? "y" : Integer.toString(1 + r.nextInt(2));
    // the exact path alone: a value no quadrature gave, or a stayed integral
    Outcome.Value closed =
        (Outcome.Value)
            Run.last("integrate(" + f + ", [x, 0, " + inner + "], [y, " + c + ", " + d + "])");
    assertThat(closed.numeric()).isFalse();
    if (Run.hasIntegral(closed.value())) {
      Statistics.label("outcome").collect("stays");
      return;
    }
    Expr exact =
        Run.value(
            "numeric("
                + mathcanvas.engine.print.AsciiPrinter.INSTANCE.display(closed.value())
                + ", 20)");
    Statistics.label("outcome").collect("symbolic");
    Symbol x = Symbol.of("x");
    Symbol y = Symbol.of("y");
    Compiled g =
        Compiled.compile(
            Parser.parseExpression(f, Names.NONE),
            List.of(x, y),
            Settings.DEFAULT,
            Cancellation.NEVER);
    double[] point = new double[2];
    Quadrature.Estimate outer =
        Quadrature.integrate(
            yy -> {
              point[1] = yy;
              double upper = variable ? yy : Double.parseDouble(inner);
              return Quadrature.integrate(
                      xx -> {
                        point[0] = xx;
                        return g.eval(point);
                      },
                      0,
                      upper,
                      1e-12,
                      Cancellation.NEVER)
                  .value();
            },
            c,
            d,
            1e-10,
            Cancellation.NEVER);
    assertThat(outer.converged()).isTrue();
    double value = number(exact);
    assertThat(Math.abs(value - outer.value()))
        .as("∫∫ %s: %s against %s", f, value, outer.value())
        .isLessThanOrEqualTo(1e-8 * Math.max(1, Math.abs(value)));
  }
}
