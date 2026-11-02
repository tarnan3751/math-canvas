package mathcanvas.app.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.app.document.DocumentSettings;
import mathcanvas.canvas.model.Angle;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.Circle;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.Line;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParamKind;
import mathcanvas.canvas.model.Parametrisation;
import mathcanvas.canvas.model.Path;
import mathcanvas.canvas.model.Polygon;
import mathcanvas.canvas.model.Ray;
import mathcanvas.canvas.model.Style;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.expr.Num;
import org.junit.jupiter.api.Test;

/** The rows of the constructions: equations from their geometry, words for the rest. */
class AlgebraRowsTest {

  private static final Style S = Style.DEFAULT;
  private static final ObjectId A = new ObjectId(1);
  private static final ObjectId B = new ObjectId(2);
  private static final ObjectId C = new ObjectId(3);
  private static final ObjectId O = new ObjectId(4);
  private static final Map<ObjectId, String> NAMES = Map.of(A, "A", B, "B", C, "C");

  private static String row(CanvasObject o, Geometry g, AngleMode mode) {
    return new AlgebraRows()
        .of(
            o,
            CanvasState.EMPTY,
            NAMES,
            g,
            Optional.empty(),
            Coordinates.format(DocumentSettings.DEFAULT),
            mode,
            Cancellation.NEVER)
        .text();
  }

  private static Geometry.Curve curve(Parametrisation p, double hi, ParamKind kind) {
    return new Geometry.Curve(new Path.Parametrised(p, 0, hi, kind), List.of());
  }

  @Test
  void aLineIsItsEquation() {
    Line l = new Line(O, S, new Line.Through(A, B));
    assertThat(row(l, curve(new Parametrisation.Linear(1, 3, 2, 4), 1, ParamKind.LINE), null))
        .isEqualTo("y = 2x + 1");
    assertThat(row(l, curve(new Parametrisation.Linear(3, 1, 0, -2), 1, ParamKind.LINE), null))
        .isEqualTo("x = 3");
    assertThat(row(l, curve(new Parametrisation.Linear(0, -1, 1, -0.5), 1, ParamKind.LINE), null))
        .isEqualTo("y = -0.5x - 1");
    assertThat(row(l, curve(new Parametrisation.Linear(5, 2, 1, 0), 1, ParamKind.LINE), null))
        .isEqualTo("y = 2");
    assertThat(row(l, curve(new Parametrisation.Linear(0, 0, 1, -1), 1, ParamKind.LINE), null))
        .isEqualTo("y = -x");
    assertThat(row(l, curve(new Parametrisation.Linear(0, 4, -1, 3), 1, ParamKind.LINE), null))
        .isEqualTo("y = -3x + 4");
    assertThat(row(l, Geometry.UNDEFINED, null)).isEqualTo("undefined");
  }

  @Test
  void aCircleIsItsEquation() {
    Circle c = new Circle(O, S, new Circle.CenterPoint(A, B));
    assertThat(
            row(
                c,
                curve(new Parametrisation.Circular(1, 2, 3, 0, 1), 2 * Math.PI, ParamKind.ANGLE),
                null))
        .isEqualTo("(x - 1)² + (y - 2)² = 9");
    assertThat(
            row(
                c,
                curve(new Parametrisation.Circular(0, -2, 1.5, 0, 1), 2 * Math.PI, ParamKind.ANGLE),
                null))
        .isEqualTo("x² + (y + 2)² = 2.25");
    // the centre and radius of a circle through points a rounding away from whole numbers
    assertThat(
            row(
                c,
                curve(
                    new Parametrisation.Circular(
                        1.9999999999999998, 1.0000000000000002, Math.sqrt(5.000000000000001), 0, 1),
                    2 * Math.PI,
                    ParamKind.ANGLE),
                null))
        .isEqualTo("(x - 2)² + (y - 1)² = 5");
    assertThat(
            row(
                c,
                curve(
                    new Parametrisation.Circular(1.0 / 3, 0, 1, 0, 1),
                    2 * Math.PI,
                    ParamKind.ANGLE),
                null))
        .isEqualTo("(x - 0.3333333333)² + y² = 1");
  }

  @Test
  void aRayAPolygonAndAnAngleAreWords() {
    assertThat(row(new Ray(O, S, A, B), Geometry.UNDEFINED, null)).isEqualTo("ray AB");
    Polygon p = new Polygon(O, S, new Polygon.Vertices(List.of(A, B, C)));
    Geometry g =
        curve(
            new Parametrisation.Polygonal(new double[] {0, 4, 0}, new double[] {0, 0, 3}),
            3,
            ParamKind.PERIMETER);
    assertThat(row(p, g, null)).isEqualTo("polygon ABC, area 6, perimeter 12");
    assertThat(row(p, Geometry.UNDEFINED, null)).isEqualTo("polygon ABC = undefined");
    Polygon r = new Polygon(O, S, new Polygon.Regular(A, B, Num.of(4)));
    Geometry square =
        curve(
            new Parametrisation.Polygonal(new double[] {0, 1, 1, 0}, new double[] {0, 0, 1, 1}),
            4,
            ParamKind.PERIMETER);
    assertThat(row(r, square, null)).isEqualTo("regular polygon AB, n = 4, area 1, perimeter 4");
    Angle a = new Angle(O, S, B, A, C);
    Geometry right = new Geometry.Angle(new Geometry.Point(0, 0), 0, Math.PI / 2);
    assertThat(row(a, right, AngleMode.DEGREES)).isEqualTo("∠BAC = 90°");
    assertThat(row(a, right, AngleMode.GRADIANS)).isEqualTo("∠BAC = 100 gon");
    assertThat(row(a, right, AngleMode.RADIANS)).isEqualTo("∠BAC = 1.570796327");
    assertThat(row(a, Geometry.UNDEFINED, AngleMode.DEGREES)).isEqualTo("∠BAC = undefined");
    assertThat(
            AlgebraRows.canvasLabel(
                a, right, Coordinates.format(DocumentSettings.DEFAULT), AngleMode.DEGREES))
        .contains("90°");
  }
}
