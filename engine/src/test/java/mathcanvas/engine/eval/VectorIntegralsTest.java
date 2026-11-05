package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.rule.Rewriter;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

/**
 * Line and surface integrals ({@code calculus.VectorIntegrals}): the textbook values, a gradient
 * field's line integral is the potential's difference, the flux of {@code [x, y, z]} through the
 * unit sphere is {@code 4π}, and the field's coordinates are bound.
 */
class VectorIntegralsTest {

  private static final String SPHERE =
      "[sin(u) cos(v), sin(u) sin(v), cos(u)], [u, 0, π], [v, 0, 2π]";

  @Test
  void lineIntegralsOfScalarAndVectorFields() {
    assertThat(shown("lineintegral(1, [cos(t), sin(t)], t, 0, 2π)")).isEqualTo("2π");
    assertThat(shown("lineintegral(1, [cos(t), sin(t), t], t, 0, 2π)")).isEqualTo("2π √2");
    assertThat(shown("lineintegral(x^2 + y^2, [t, 2t], t, 0, 1)")).isEqualTo("5√5/3");
    assertThat(shown("lineintegral([-y, x], [cos(t), sin(t)], t, 0, 2π)")).isEqualTo("2π");
    assertThat(shown("lineintegral([y z, x z, x y], [t, t^2, t^3], t, 0, 1)")).isEqualTo("1");
  }

  @Test
  void surfaceIntegralsAndTheFluxThroughTheSphere() {
    assertThat(shown("surfaceintegral([x, y, z], " + SPHERE + ")")).isEqualTo("4π");
    assertThat(shown("surfaceintegral(z, [u, v, 1 - u - v], [u, 0, 1 - v], [v, 0, 1])"))
        .isEqualTo("√3/6");
    // the area of the sphere: |sin u| stays exactly, the nested quadrature gives 4π
    assertThat(Run.value("surfaceintegral(1, " + SPHERE + ")")).isInstanceOf(Integral.class);
    assertThat(shown("numeric(surfaceintegral(1, " + SPHERE + "), 10)")).isEqualTo("12.56637061");
  }

  @Test
  void theCoordinatesAreBoundInTheField() {
    assertThat(shown("x := 5\nlineintegral(x, [t, 0], t, 0, 1)")).isEqualTo("1/2");
    assertThat(shown("subs(lineintegral(x, [t, 0], t, 0, 1), x = 5)")).isEqualTo("1/2");
    assertThat(shown("subs(lineintegral(a x, [t, 0], t, 0, 1), a = 2)")).isEqualTo("1");
  }

  @Test
  void aParameterTheFieldNamesIsNotCapturedAndItsRangeIsAFact() {
    // review-28: the field's t is a symbol from outside, not the parameter
    assertThat(shown("lineintegral(t, [cos t, sin t], t, 0, 2π)")).isEqualTo("2πt");
    assertThat(shown("t := 5\nlineintegral(t, [cos t, sin t], t, 0, 2π)")).isEqualTo("10π");
    assertThat(shown("surfaceintegral(u, [u, v, 0], [u, 0, 1], [v, 0, 1])")).isEqualTo("u");
    // r in [0, 1]: |n| = √(r²) is r
    assertThat(shown("surfaceintegral(1, [r cos(θ), r sin(θ), 0], [r, 0, 1], [θ, 0, 2π])"))
        .isEqualTo("π");
  }

  @Test
  void aScalarFieldIgnoresTheOrientationAndAVectorFieldKeepsIt() {
    assertThat(shown("lineintegral(1, [cos(t), sin(t)], t, 2π, 0)")).isEqualTo("2π");
    assertThat(shown("lineintegral([-y, x], [cos(t), sin(t)], t, 2π, 0)")).isEqualTo("-2π");
    assertThat(shown("surfaceintegral(1, [u, v, 0], [u, 1, 0], [v, 0, 2])")).isEqualTo("2");
  }

  @Test
  void wrongShapesAreErrors() {
    assertThat(Run.error("lineintegral(z, [t, t], t, 0, 1)"))
        .isEqualTo("the field uses z on a plane path");
    assertThat(Run.error("lineintegral(1, [t], t, 0, 1)"))
        .isEqualTo("`lineintegral` needs a path [x(t), y(t)] or [x(t), y(t), z(t)]");
    assertThat(Run.error("lineintegral([x, y, 1], [t, t], t, 0, 1)"))
        .isEqualTo("`lineintegral` needs a field of as many components as the path");
    assertThat(Run.error("surfaceintegral(1, [u, v], [u, 0, 1], [v, 0, 1])"))
        .isEqualTo("`surfaceintegral` needs a surface [x(u, v), y(u, v), z(u, v)]");
    assertThat(Run.error("surfaceintegral(1, [u, v, 0], u, [v, 0, 1])"))
        .isEqualTo("`surfaceintegral` needs square brackets [u, a, b] and [v, c, d]");
  }

  @Test
  void theStepsChain() {
    Outcome.Value v = (Outcome.Value) Run.last("steps(surfaceintegral([x, y, z], " + SPHERE + "))");
    TraceChecks.assertChain(new Rewriter.Result(v.value(), v.trace()));
    assertThat(v.trace().rewrites().getFirst().rule()).isEqualTo("surfaceintegral.normal");
    Outcome.Value w =
        (Outcome.Value) Run.last("steps(lineintegral([2x y, x^2], [t, t^2], t, 0, 1))");
    TraceChecks.assertChain(new Rewriter.Result(w.value(), w.trace()));
  }

  /** A gradient field's line integral along any path is the potential's difference. */
  @Property(tries = 40)
  void theLineIntegralOfAGradientIsThePotentialsDifference(@ForAll long seed) {
    Random r = new Random(seed);
    String phi =
        "("
            + (r.nextInt(7) - 3)
            + ") x^2 y + ("
            + (r.nextInt(7) - 3)
            + ") y^3 + ("
            + (r.nextInt(7) - 3)
            + ") x z + x";
    String path =
        "[t^"
            + (1 + r.nextInt(3))
            + ", "
            + (r.nextInt(5) - 2)
            + " t + 1, t^2 - "
            + r.nextInt(3)
            + "]";
    int b = 1 + r.nextInt(2);
    String line =
        "expand(lineintegral(grad(" + phi + ", [x, y, z]), " + path + ", t, 0, " + b + "))";
    String difference =
        "expand(subs(subs("
            + phi
            + ", x = nth("
            + path
            + ", 1), y = nth("
            + path
            + ", 2),"
            + " z = nth("
            + path
            + ", 3)), t = "
            + b
            + ") - subs(subs("
            + phi
            + ", x = nth("
            + path
            + ", 1), y = nth("
            + path
            + ", 2), z = nth("
            + path
            + ", 3)), t = 0))";
    assertThat(shown(line)).as("%s along %s", phi, path).isEqualTo(shown(difference));
  }
}
