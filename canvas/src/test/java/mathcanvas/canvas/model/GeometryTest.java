package mathcanvas.canvas.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Symbol;
import org.junit.jupiter.api.Test;

class GeometryTest {

  @Test
  void polylinesCompareByValueAndCopyTheirInput() {
    double[] xs = {0, 1};
    Polyline a = new Polyline(new double[] {0, 1}, xs, new double[] {2, 3});
    Polyline b = new Polyline(new double[] {0, 1}, new double[] {0, 1}, new double[] {2, 3});
    xs[0] = 9;
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a.x(0)).isZero();
    assertThat(a.xs()).isSameAs(a.xs()).containsExactly(0, 1);
    assertThat(a.ys()).containsExactly(2, 3);
    assertThat(Polyline.unparametrised(new double[] {1}, new double[] {1}))
        .isEqualTo(Polyline.unparametrised(new double[] {1}, new double[] {1}));
  }

  @Test
  void polylinesRefuseGapsAndMismatchedArrays() {
    assertThatThrownBy(
            () -> new Polyline(new double[] {0}, new double[] {Double.NaN}, new double[] {0}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Polyline(new double[] {0}, new double[] {0, 1}, new double[] {0}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Polyline(new double[0], new double[0], new double[0]))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void windowsMeasureAndWiden() {
    Window w = new Window(-2, 2, -1, 2);
    assertThat(w.diagonal()).isEqualTo(5);
    assertThat(w.widened(0.5)).isEqualTo(new Window(-4, 4, -2.5, 3.5));
    assertThatThrownBy(() -> new Window(1, 1, 0, 1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aParametrisedPathKnowsItsDomain() {
    Path.Parametrised p =
        new Path.Parametrised(new Parametrisation.Linear(0, 0, 1, 1), 0, 1, ParamKind.LINE);
    assertThat(p.contains(0)).isTrue();
    assertThat(p.contains(1.0000001)).isFalse();
    assertThat(p.contains(Double.NaN)).isFalse();
    assertThatThrownBy(
            () ->
                new Path.Parametrised(new Parametrisation.Linear(0, 0, 1, 1), 1, 0, ParamKind.LINE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new Path.Parametrised(new Parametrisation.Linear(0, 0, 1, 1), 0, 1, ParamKind.NONE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new Parametrisation.Linear(1, 2, 3, 4).at(2)).isEqualTo(new Sample(7, 10));
  }

  @Test
  void undefinedIsOneValue() {
    assertThat(Geometry.UNDEFINED).isEqualTo(new Geometry.Undefined());
    assertThatThrownBy(() -> new Geometry.Point(Double.NaN, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(Sample.UNDEFINED.isFinite()).isFalse();
  }

  @Test
  void theStubSamplerSplitsAtGaps() {
    StubSampler s = new StubSampler(Map.of("a", 2.0), 4, List.of(), List.of());
    Symbol x = Symbol.of("x");
    List<Polyline> out =
        s.sample(new Parametrisation.Graph(Parse.expr("a sqrt(x)"), x), -1, 1, Cancellation.NEVER);
    assertThat(out).hasSize(1);
    assertThat(out.getFirst().size()).isEqualTo(3);
    assertThat(out.getFirst().y(2)).isEqualTo(2);
    assertThat(s.point(new Parametrisation.Polar(Parse.expr("2"), x), Math.PI / 2).y())
        .isEqualTo(2);
  }
}
