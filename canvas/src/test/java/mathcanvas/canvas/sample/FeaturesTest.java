package mathcanvas.canvas.sample;

import static mathcanvas.canvas.sample.SamplersTest.X;
import static mathcanvas.canvas.sample.SamplersTest.expr;
import static mathcanvas.canvas.sample.SamplersTest.graph;
import static mathcanvas.canvas.sample.SamplersTest.sampler;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import mathcanvas.canvas.model.Feature;
import mathcanvas.canvas.model.Features;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ParamKind;
import mathcanvas.canvas.model.Parametrisation;
import mathcanvas.canvas.model.Path;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.Window;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Environment;
import org.junit.jupiter.api.Test;

/** Roots and extrema of sampled graphs, refined with the real sampler. */
class FeaturesTest {

  private static final Sampler S = sampler(Environment.EMPTY);

  private static Geometry.Curve curve(String body, double lo, double hi) {
    Window w = new Window(lo, hi, -4, 4);
    return new Geometry.Curve(
        new Path.Parametrised(new Parametrisation.Graph(expr(body), X), lo, hi, ParamKind.X),
        graph(S, body, lo, hi, w));
  }

  private static List<Double> found(String body, double lo, double hi, Feature feature) {
    return Features.of(curve(body, lo, hi), feature, S, Cancellation.NEVER).stream()
        .map(Features.Found::t)
        .toList();
  }

  @Test
  void theRootsOfACubicAreFoundToTheRefinement() {
    List<Double> roots = found("x^3 - x", -2, 2, Feature.ROOT);
    assertThat(roots).hasSize(3);
    assertThat(roots.get(0)).isCloseTo(-1, within(1e-10));
    assertThat(roots.get(1)).isCloseTo(0, within(1e-10));
    assertThat(roots.get(2)).isCloseTo(1, within(1e-10));
  }

  @Test
  void theExtremaOfTheSineAreItsQuarterTurns() {
    List<Double> max = found("sin(x)", 0, 2 * Math.PI, Feature.MAX);
    List<Double> min = found("sin(x)", 0, 2 * Math.PI, Feature.MIN);
    assertThat(max).hasSize(1);
    assertThat(max.getFirst()).isCloseTo(Math.PI / 2, within(1e-10));
    assertThat(min).hasSize(1);
    assertThat(min.getFirst()).isCloseTo(3 * Math.PI / 2, within(1e-10));
  }

  @Test
  void anExtremumIsRefinedToTheRelativePrecision() {
    List<Double> min = found("(x - 1000.3)^2 + 1", 990, 1010, Feature.MIN);
    assertThat(min).hasSize(1);
    assertThat(min.getFirst()).isCloseTo(1000.3, within(1000.3 * 1e-12 * 2));
  }

  @Test
  void aTouchingRootIsOneRoot() {
    assertThat(found("x^2", -1.3, 2, Feature.ROOT))
        .singleElement()
        .satisfies(t -> assertThat(t).isCloseTo(0, within(1e-6)));
    assertThat(found("-(x - 0.5)^2", -1.3, 2, Feature.ROOT))
        .singleElement()
        .satisfies(t -> assertThat(t).isCloseTo(0.5, within(1e-6)));
  }

  @Test
  void twoNearRootsAreTwoNotThree() {
    assertThat(found("x^2 - 1/10^6", -1.3, 2, Feature.ROOT))
        .hasSize(2)
        .allSatisfy(t -> assertThat(Math.abs(t)).isCloseTo(1e-3, within(1e-12)));
  }

  @Test
  void aPoleIsNoRoot() {
    assertThat(found("1/x", -2.1, 2, Feature.ROOT)).isEmpty();
    assertThat(found("tan(x)", -3, 3, Feature.ROOT))
        .hasSize(1)
        .first()
        .satisfies(t -> assertThat(t).isCloseTo(0, within(1e-12)));
  }

  @Test
  void aCornerIsAnExtremumAndAnEndIsNot() {
    assertThat(found("abs(x - 0.25)", -1.3, 2, Feature.MIN))
        .singleElement()
        .satisfies(t -> assertThat(t).isCloseTo(0.25, within(1e-9)));
    assertThat(found("x", -1, 1, Feature.MIN)).isEmpty();
    assertThat(found("x", -1, 1, Feature.MAX)).isEmpty();
  }

  @Test
  void onlyAGraphHasFeatures() {
    Geometry.Curve none = new Geometry.Curve(Path.POLYLINE_ONLY, List.of());
    assertThat(Features.of(none, Feature.ROOT, S, Cancellation.NEVER)).isEmpty();
  }

  @Test
  void anIntervalOfZerosIsNoRoot() {
    assertThat(found("0 x", -1.3, 2, Feature.ROOT)).isEmpty();
    assertThat(found("floor(x)", -1.3, 2, Feature.ROOT)).isEmpty();
    assertThat(found("max(0, x^2 - 1)", -2.3, 2, Feature.ROOT)).isEmpty();
    // a flat zero piece and one isolated root beside it
    assertThat(found("(x + abs(x))/2 (x - 3)", -2.3, 4.2, Feature.ROOT))
        .singleElement()
        .satisfies(t -> assertThat(t).isCloseTo(3, within(1e-10)));
  }
}
