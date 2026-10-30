package mathcanvas.app.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ParamKind;
import mathcanvas.canvas.model.Parametrisation;
import mathcanvas.canvas.model.Path;
import mathcanvas.canvas.model.Polyline;
import org.junit.jupiter.api.Test;

class PathProjectionTest {

  private static final Path LINE =
      new Path.Parametrised(new Parametrisation.Linear(0, 0, 1, 0), -10, 10, ParamKind.LINE);

  @Test
  void theParameterIsInterpolatedOnTheNearestSegment() {
    Geometry.Curve c =
        new Geometry.Curve(
            LINE,
            List.of(
                new Polyline(new double[] {0, 1}, new double[] {0, 2}, new double[] {0, 0}),
                new Polyline(new double[] {5, 6}, new double[] {10, 12}, new double[] {0, 0})));

    PathProjection.Projection p = PathProjection.onto(c, 11.5, 3).orElseThrow();

    assertThat(p.kind()).isEqualTo(ParamKind.LINE);
    assertThat(p.t()).isCloseTo(5.75, within(1e-12));
    assertThat(p.x()).isCloseTo(11.5, within(1e-12));
    assertThat(p.y()).isZero();
  }

  @Test
  void aPathWithNoParameterGivesNone() {
    Geometry.Curve c =
        new Geometry.Curve(
            Path.POLYLINE_ONLY,
            List.of(Polyline.unparametrised(new double[] {0, 0}, new double[] {-1, 1})));

    PathProjection.Projection p = PathProjection.onto(c, 3, 0.5).orElseThrow();

    assertThat(p.kind()).isEqualTo(ParamKind.NONE);
    assertThat(p.t()).isNaN();
    assertThat(p.x()).isZero();
    assertThat(p.y()).isCloseTo(0.5, within(1e-12));
  }

  @Test
  void aCurveWithNoSampleHasNoProjection() {
    assertThat(PathProjection.onto(new Geometry.Curve(LINE, List.of()), 0, 0)).isEmpty();
  }
}
