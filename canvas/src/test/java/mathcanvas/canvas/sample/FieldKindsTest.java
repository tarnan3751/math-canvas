package mathcanvas.canvas.sample;

import static mathcanvas.canvas.sample.SamplersTest.expr;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.ContourPlot;
import mathcanvas.canvas.model.FieldGrid;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Polyline;
import mathcanvas.canvas.model.Recomputes;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.SlopeField;
import mathcanvas.canvas.model.SolutionCurve;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.VectorField;
import mathcanvas.canvas.model.Window;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Expr;
import org.junit.jupiter.api.Test;

/** Vector fields, slope fields, solution curves and contour plots with the real sampler. */
class FieldKindsTest {

  private static final Style S = Style.DEFAULT;
  private static final Window W = new Window(-5, 5, -5, 5);

  private static final Sampler SAMPLER = new Samplers().over(Environment.EMPTY, Settings.DEFAULT);

  private static CanvasState recomputed(CanvasState s, Sampler sampler, Window w) {
    return s.recomputeAll(sampler, w, Recomputes.standard(), Cancellation.NEVER);
  }

  private static Geometry geometry(CanvasState s, long id) {
    return s.geometryOf(new ObjectId(id));
  }

  @Test
  void aVectorFieldHasAnArrowPerCellScaledToTheCellUnderItsCap() {
    CanvasState s =
        recomputed(
            CanvasState.EMPTY.add(i -> new VectorField(i, S, expr("-y"), expr("x"), 20)),
            SAMPLER,
            W);
    Geometry.Field f = (Geometry.Field) geometry(s, 1);
    assertThat(f.arrows()).isTrue();
    assertThat(f.slope()).isEmpty();
    // 20 columns of 0.5 over a square window, centred on the cells: none at the origin
    assertThat(f.marks()).hasSize(400);
    double cell = 0.5;
    double longest = 0;
    for (Geometry.Mark m : f.marks()) {
      double cx = (m.from().x() + m.to().x()) / 2;
      double cy = (m.from().y() + m.to().y()) / 2;
      // centred on a cell's centre, along (−y, x)
      assertThat(Math.abs(cx / cell - Math.floor(cx / cell) - 0.5)).isLessThan(1e-9);
      double dx = m.to().x() - m.from().x();
      double dy = m.to().y() - m.from().y();
      assertThat(dx * cx + dy * cy).isCloseTo(0, within(1e-9));
      assertThat(dx * -cy + dy * cx).isPositive();
      double length = Math.hypot(dx, dy) / cell;
      assertThat(length).isLessThanOrEqualTo(0.9 + 1e-9);
      // under the cap, the length and the weight are in proportion to the magnitude
      assertThat(length).isCloseTo(0.9 * m.weight(), within(1e-9));
      longest = Math.max(longest, length);
    }
    assertThat(longest).isCloseTo(0.9, within(1e-9));
    // the cap is the 0.9 quantile: a tenth of the arrows are at it
    long capped = f.marks().stream().filter(m -> m.weight() == 1).count();
    assertThat(capped).isBetween(30L, 50L);
  }

  @Test
  void theDensitySetsTheColumnsAndAPanKeepsTheGridOnThePlane() {
    CanvasState s = CanvasState.EMPTY.add(i -> new VectorField(i, S, expr("1"), expr("x"), 10));
    assertThat(((Geometry.Field) geometry(recomputed(s, SAMPLER, W), 1)).marks()).hasSize(100);
    CanvasState dense =
        CanvasState.EMPTY.add(
            i -> new VectorField(i, S, expr("1"), expr("x"), FieldGrid.MAX_DENSITY));
    assertThat(((Geometry.Field) geometry(recomputed(dense, SAMPLER, W), 1)).marks()).hasSize(1600);
    // a wide window has half as many rows as columns, its cells still square (here a row of
    // centres falls on the window's lower edge)
    Geometry.Field wide =
        (Geometry.Field) geometry(recomputed(s, SAMPLER, new Window(-10, 10, -5, 5)), 1);
    assertThat(wide.marks()).hasSize(60);
    for (Geometry.Mark m : wide.marks()) {
      double cx = (m.from().x() + m.to().x()) / 2;
      double cy = (m.from().y() + m.to().y()) / 2;
      assertThat(Math.abs(cx / 2 - Math.floor(cx / 2) - 0.5)).isLessThan(1e-9);
      assertThat(Math.abs(cy / 2 - Math.floor(cy / 2) - 0.5)).isLessThan(1e-9);
    }

    // panned by a third of a cell, the marks are where they were, less those that left
    Geometry.Field panned =
        (Geometry.Field) geometry(recomputed(s, SAMPLER, new Window(-4.6, 5.4, -5, 5)), 1);
    for (Geometry.Mark m : panned.marks()) {
      double cx = (m.from().x() + m.to().x()) / 2;
      assertThat(Math.abs(cx - Math.floor(cx) - 0.5)).isLessThan(1e-9);
    }
  }

  @Test
  void aSlopeFieldsSegmentsRunAlongItsSlopeAtOneLength() {
    CanvasState s =
        recomputed(CanvasState.EMPTY.add(i -> new SlopeField(i, S, expr("x - y"), 20)), SAMPLER, W);
    Geometry.Field f = (Geometry.Field) geometry(s, 1);
    assertThat(f.arrows()).isFalse();
    assertThat(f.slope()).contains(expr("x - y"));
    assertThat(f.marks()).hasSize(400);
    for (Geometry.Mark m : f.marks()) {
      double cx = (m.from().x() + m.to().x()) / 2;
      double cy = (m.from().y() + m.to().y()) / 2;
      double dx = m.to().x() - m.from().x();
      double dy = m.to().y() - m.from().y();
      assertThat(dx).isPositive();
      assertThat(dy / dx).isCloseTo(cx - cy, within(1e-9));
      assertThat(Math.hypot(dx, dy)).isCloseTo(0.7 * 0.5, within(1e-12));
      assertThat(m.weight()).isEqualTo(1);
    }
    // a slope with no value at a point has no mark there
    CanvasState root =
        recomputed(
            CanvasState.EMPTY.add(i -> new SlopeField(i, S, expr("sqrt(x)"), 20)), SAMPLER, W);
    assertThat(((Geometry.Field) geometry(root, 1)).marks()).hasSize(200);
  }

  @Test
  void aSolutionCurveSolvesItsEquationThroughItsPointToTheWindowsEdge() {
    CanvasState s =
        recomputed(
            CanvasState.EMPTY
                .add(i -> new SlopeField(i, S, expr("y"), 20))
                .add(i -> new FreePoint(i, S, 0, 1))
                .add(i -> new SolutionCurve(i, S, new ObjectId(1), new ObjectId(2))),
            SAMPLER,
            W);
    Geometry.Curve c = (Geometry.Curve) geometry(s, 3);
    assertThat(c.branches()).hasSize(1);
    Polyline p = c.branches().getFirst();
    boolean through = false;
    for (int k = 0; k < p.size(); k++) {
      // y = eˣ, to the tolerance relative to the window
      assertThat(p.y(k)).isCloseTo(Math.exp(p.x(k)), within(1e-4 * Math.max(1, p.y(k))));
      through |= p.x(k) == 0 && p.y(k) == 1;
      if (k > 0) {
        assertThat(p.x(k)).isGreaterThan(p.x(k - 1));
      }
    }
    assertThat(through).isTrue();
    // from the widened window's left edge to past its top
    assertThat(p.x(0)).isCloseTo(-10, within(0.1));
    assertThat(p.y(p.size() - 1)).isGreaterThanOrEqualTo(10);
    assertThat(p.y(p.size() - 2)).isLessThan(10);
  }

  @Test
  void aSolutionStopsWhereItsSlopeHasNoValueAndFollowsItsPoint() {
    CanvasState s =
        recomputed(
            CanvasState.EMPTY
                .add(i -> new SlopeField(i, S, expr("sqrt(x)"), 20))
                .add(i -> new FreePoint(i, S, 1, 0))
                .add(i -> new SolutionCurve(i, S, new ObjectId(1), new ObjectId(2))),
            SAMPLER,
            W);
    Polyline p = ((Geometry.Curve) geometry(s, 3)).branches().getFirst();
    // the blow-up at x = 0, where √x stops being real
    assertThat(p.x(0)).isBetween(0.0, 1e-6);
    assertThat(p.y(0)).isCloseTo(-2.0 / 3, within(1e-5));

    // the point dragged, the curve recomputed through it: y = 1 + (2/3)(x^(3/2) − 8)
    CanvasState moved =
        s.replace(new FreePoint(new ObjectId(2), S, 4, 1))
            .recompute(
                Set.of(new ObjectId(2)),
                Set.of(),
                SAMPLER,
                W,
                Recomputes.standard(),
                Cancellation.NEVER);
    Polyline q = ((Geometry.Curve) geometry(moved, 3)).branches().getFirst();
    for (int k = 0; k < q.size(); k++) {
      double x = q.x(k);
      assertThat(q.y(k))
          .isCloseTo(1 + 2.0 / 3 * (Math.pow(x, 1.5) - 8), within(1e-4 * Math.max(1, q.y(k))));
    }

    // a point where the slope has no value: undefined, and defined again off it
    CanvasState off =
        s.replace(new FreePoint(new ObjectId(2), S, -1, 0))
            .recompute(
                Set.of(new ObjectId(2)),
                Set.of(),
                SAMPLER,
                W,
                Recomputes.standard(),
                Cancellation.NEVER);
    assertThat(geometry(off, 3)).isEqualTo(Geometry.UNDEFINED);
  }

  @Test
  void aCountOfLevelsSpreadsOverTheRangeInViewEachLabelledInIt() {
    CanvasState s =
        recomputed(
            CanvasState.EMPTY.add(
                i -> new ContourPlot(i, S, expr("x^2 + y^2"), new ContourPlot.Count(3))),
            SAMPLER,
            W);
    Geometry.Contours c = (Geometry.Contours) geometry(s, 1);
    assertThat(c.levels()).hasSize(3);
    double previous = Double.NEGATIVE_INFINITY;
    for (Geometry.Level l : c.levels()) {
      assertThat(l.value()).isGreaterThan(previous);
      previous = l.value();
      assertThat(l.lines()).isNotEmpty();
      for (Polyline p : l.lines()) {
        for (int k = 0; k < p.size(); k++) {
          double r2 = p.x(k) * p.x(k) + p.y(k) * p.y(k);
          assertThat(r2).isCloseTo(l.value(), within(0.01 * l.value()));
        }
      }
      Geometry.Point at = l.label().orElseThrow();
      assertThat(Math.abs(at.x())).isLessThanOrEqualTo(4.5);
      assertThat(Math.abs(at.y())).isLessThanOrEqualTo(4.5);
    }
    // min near 0 and max 50 over the grid: a quarter, a half and three quarters of the way
    assertThat(c.levels().get(1).value()).isCloseTo(25, within(0.1));
  }

  @Test
  void listedLevelsAreTheirValuesOnceEachAndFollowSliders() {
    List<Expr> values = List.of(expr("4"), expr("1"), expr("a"), expr("1"), expr("i"));
    CanvasState s =
        CanvasState.EMPTY
            .add(
                i ->
                    new Slider(
                        i, S, "a", BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE))
            .add(i -> new ContourPlot(i, S, expr("x^2 + y^2"), new ContourPlot.Values(values)));
    Sampler nine = new Samplers().over(SamplersTest.env("a", "9"), Settings.DEFAULT);
    Geometry.Contours c = (Geometry.Contours) geometry(recomputed(s, nine, W), 2);
    // 1 twice is one level; i is not a real number
    assertThat(c.levels()).extracting(Geometry.Level::value).containsExactly(1.0, 4.0, 9.0);
    // a level wholly out of view has no label
    Geometry.Contours far =
        (Geometry.Contours) geometry(recomputed(s, nine, new Window(10, 20, 10, 20)), 2);
    assertThat(far.levels()).allSatisfy(l -> assertThat(l.label()).isEmpty());
  }

  @Test
  void aConstantOrUndefinedFunctionHasNoLevels() {
    CanvasState s =
        recomputed(
            CanvasState.EMPTY
                .add(i -> new ContourPlot(i, S, expr("3"), new ContourPlot.Count(5)))
                .add(i -> new ContourPlot(i, S, expr("q(x)"), new ContourPlot.Count(5))),
            SAMPLER,
            W);
    assertThat(((Geometry.Contours) geometry(s, 1)).levels()).isEmpty();
    assertThat(((Geometry.Contours) geometry(s, 2)).levels()).isEmpty();
  }

  @Test
  void aPanResamplesTheFieldsAndTheirSolutions() {
    CanvasState s =
        recomputed(
            CanvasState.EMPTY
                .add(i -> new SlopeField(i, S, expr("x"), 20))
                .add(i -> new FreePoint(i, S, 0, 0))
                .add(i -> new SolutionCurve(i, S, new ObjectId(1), new ObjectId(2)))
                .add(i -> new ContourPlot(i, S, expr("x y"), new ContourPlot.Count(2))),
            SAMPLER,
            W);
    Window panned = new Window(20, 30, -5, 5);
    CanvasState r = s.resample(SAMPLER, panned, Recomputes.standard(), Cancellation.NEVER);
    for (Geometry.Mark m : ((Geometry.Field) geometry(r, 1)).marks()) {
      assertThat(m.from().x()).isGreaterThan(19);
    }
    assertThat(geometry(r, 3)).isNotEqualTo(geometry(s, 3));
    assertThat(geometry(r, 4)).isNotEqualTo(geometry(s, 4));
  }

  /** The solution curve of a slope field through a point, recomputed over the window. */
  private static Polyline solution(String slope, double x, double y) {
    CanvasState s =
        recomputed(
            CanvasState.EMPTY
                .add(i -> new SlopeField(i, S, expr(slope), 20))
                .add(i -> new FreePoint(i, S, x, y))
                .add(i -> new SolutionCurve(i, S, new ObjectId(1), new ObjectId(2))),
            SAMPLER,
            W);
    Geometry.Curve c = (Geometry.Curve) geometry(s, 3);
    assertThat(c.branches()).hasSize(1);
    return c.branches().getFirst();
  }

  @Test
  void aSolutionTurnsAtAVerticalTangentAndClosesItsOrbit() {
    // y' = −x/y through (0, 2): the whole circle of radius 2, closed at its start
    Polyline circle = solution("-x/y", 0, 2);
    assertThat(circle.size()).isBetween(100, 999);
    for (int k = 0; k < circle.size(); k++) {
      assertThat(Math.hypot(circle.x(k), circle.y(k))).isCloseTo(2, within(1e-4));
    }
    assertThat(circle.x(0)).isEqualTo(circle.x(circle.size() - 1));
    assertThat(circle.y(0)).isEqualTo(circle.y(circle.size() - 1));
    double least = Double.POSITIVE_INFINITY;
    double most = Double.NEGATIVE_INFINITY;
    for (int k = 0; k < circle.size(); k++) {
      least = Math.min(least, circle.y(k));
      most = Math.max(most, circle.x(k));
    }
    // round through both vertical tangents, at (±2, 0), and the bottom
    assertThat(least).isCloseTo(-2, within(1e-3));
    assertThat(most).isCloseTo(2, within(1e-3));

    // y' = 1/(y − 1) through (0, 2): the parabola (y − 1)² = 2x + 1, turning at its vertex
    // (−1/2, 1) without overshooting it, to the window's edge on both branches
    Polyline parabola = solution("1/(y - 1)", 0, 2);
    assertThat(parabola.size()).isBetween(100, 999);
    double left = Double.POSITIVE_INFINITY;
    for (int k = 0; k < parabola.size(); k++) {
      double x = parabola.x(k);
      double y = parabola.y(k);
      assertThat((y - 1) * (y - 1)).isCloseTo(2 * x + 1, within(1e-3 * Math.max(1, x)));
      left = Math.min(left, x);
    }
    assertThat(left).isCloseTo(-0.5, within(1e-4));
    assertThat(parabola.y(0)).isLessThan(1);
    assertThat(parabola.y(parabola.size() - 1)).isGreaterThan(1);
  }
}
