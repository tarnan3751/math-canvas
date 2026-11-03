package mathcanvas.canvas.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.canvas.geom.Ticks;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.ContourPlot;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Polyline;
import mathcanvas.canvas.model.SlopeField;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.VectorField;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

/** How fields and contour plots are drawn: marks by the ramp, heads, levels and their labels. */
class FieldContentTest {

  private static final CanvasTheme THEME = CanvasTheme.LIGHT;

  private static Expr expr(String text) {
    return Parser.parseExpression(text, Names.NONE);
  }

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  private static Geometry.Mark mark(double x, double weight) {
    return new Geometry.Mark(new Geometry.Point(x, 0), new Geometry.Point(x + 1, 0), weight);
  }

  private static CanvasContent.Built built(CanvasState s, Map<ObjectId, Geometry> g) {
    return CanvasContent.build(new CanvasState(s.objects(), g, s.nextId()), Map.of(), THEME);
  }

  @Test
  void aVectorFieldsArrowsAreDrawnInStepsOfItsRampWithHeads() {
    CanvasState s =
        CanvasState.EMPTY.add(i -> new VectorField(i, Style.DEFAULT, expr("x"), expr("y"), 20));
    Geometry.Field f =
        new Geometry.Field(
            List.of(mark(0, 0), mark(2, 1), mark(4, 1), mark(6, 0.5)), true, Optional.empty());
    CanvasContent.Built b = built(s, Map.of(id(1), f));
    PlotContent c = b.content();
    // three steps of the ramp used, each one polyline of marks broken by gaps, a hit item each
    assertThat(c.curves()).hasSize(3);
    assertThat(b.items()).containsOnlyKeys(0, 1, 2).containsValues(id(1));
    Rgba colour = THEME.series().get(0);
    assertThat(c.curves().get(2).stroke().colour()).isEqualTo(colour);
    assertThat(c.curves().get(0).stroke().colour())
        .isEqualTo(CanvasContent.ramp(colour, THEME.background(), 0));
    assertThat(c.curves().get(0).stroke().colour()).isNotEqualTo(colour);
    assertThat(c.curves().get(2).xs()).containsExactly(2, 3, Double.NaN, 4, 5, Double.NaN);
    // a head per arrow at its end, longer at the cap
    assertThat(c.heads()).hasSize(4);
    assertThat(c.heads())
        .extracting(PlotContent.Head::length)
        .containsExactlyInAnyOrder(3.0, 6.0, 6.0, 4.5);

    // exported with its heads, filled triangles through Arrowhead, after the background's fill
    String svg =
        Export.svg(new Viewport(-1, 8, -2, 2, 450, 200, false), THEME, c, Ticks.estimate(12));
    assertThat(svg.split("Z\" fill=\"", -1)).hasSize(6);

    // zoomed out to a pixel a unit, each head at most 0.4 of its one-pixel shaft
    String far =
        Export.svg(
            new Viewport(-100, 350, -100, 100, 450, 200, false), THEME, c, Ticks.estimate(12));
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile(
                "<path d=\"M([-0-9.]+) [-0-9.]+L([-0-9.]+) [-0-9.]+L[^\"]*\" fill")
            .matcher(far);
    int heads = 0;
    while (m.find()) {
      double length = Math.abs(Double.parseDouble(m.group(1)) - Double.parseDouble(m.group(2)));
      if (length < 5) {
        heads++;
        assertThat(length).isLessThanOrEqualTo(0.4 + 0.01);
      }
    }
    assertThat(heads).isEqualTo(4);
  }

  @Test
  void aSlopeFieldsSegmentsAreOnePolylineWithNoHeads() {
    CanvasState s = CanvasState.EMPTY.add(i -> new SlopeField(i, Style.DEFAULT, expr("x"), 20));
    Geometry.Field f =
        new Geometry.Field(List.of(mark(0, 1), mark(2, 1)), false, Optional.of(expr("x")));
    PlotContent c = built(s, Map.of(id(1), f)).content();
    assertThat(c.curves()).hasSize(1);
    assertThat(c.heads()).isEmpty();
  }

  @Test
  void aContourPlotsLevelsAreRampedAndLabelledWithTheirValues() {
    CanvasState s =
        CanvasState.EMPTY.add(
            i ->
                new ContourPlot(
                    i, new Style(3, 2, false, true, false), expr("x"), new ContourPlot.Count(3)));
    Polyline line = Polyline.unparametrised(new double[] {0, 1}, new double[] {0, 1});
    Geometry.Contours g =
        new Geometry.Contours(
            List.of(
                new Geometry.Level(-0.5, List.of(line), Optional.of(new Geometry.Point(0, 0))),
                new Geometry.Level(1234567, List.of(line), Optional.empty()),
                new Geometry.Level(2.0 / 3, List.of(), Optional.of(new Geometry.Point(1, 1)))));
    CanvasContent.Built b = built(s, Map.of(id(1), g));
    PlotContent c = b.content();
    assertThat(c.curves()).hasSize(2);
    Rgba colour = THEME.series().get(3);
    assertThat(c.curves().get(0).stroke().colour())
        .isEqualTo(CanvasContent.ramp(colour, THEME.background(), 0));
    assertThat(c.curves().get(1).stroke().colour())
        .isEqualTo(CanvasContent.ramp(colour, THEME.background(), 0.5));
    assertThat(c.labels()).extracting(PlotContent.Label::text).containsExactly("−0.5", "0.6667");
    assertThat(CanvasContent.levelText(1234567)).isEqualTo("1.235E+6");
    assertThat(CanvasContent.levelText(0)).isEqualTo("0");
    assertThat(CanvasContent.levelText(12)).isEqualTo("12");
  }
}
