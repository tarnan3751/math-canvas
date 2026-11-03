package mathcanvas.canvas.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.canvas.geom.Ticks;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.hit.Hit;
import mathcanvas.canvas.model.BarChart;
import mathcanvas.canvas.model.BoxPlot;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.Histogram;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ScatterPlot;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Summary;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

/** How the statistical plots are drawn and hit: bars, boxes and markers. */
class StatisticsContentTest {

  private static final CanvasTheme THEME = CanvasTheme.LIGHT;
  private static final Rgba COLOUR = THEME.series().get(0);
  private static final Viewport VIEW = new Viewport(-1, 9, -5, 5, 500, 500, false);

  private static Expr expr(String text) {
    return Parser.parseExpression(text, Names.NONE);
  }

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  private static CanvasContent.Built built(CanvasState s, Map<ObjectId, Geometry> g) {
    return CanvasContent.build(new CanvasState(s.objects(), g, s.nextId()), Map.of(), THEME);
  }

  @Test
  void aHistogramsBarsAreHalfFilledWithASolidEdgeEachBarHitInside() {
    CanvasState s =
        CanvasState.EMPTY.add(
            i -> new Histogram(i, Style.DEFAULT, expr("[1]"), Optional.empty(), Optional.empty()));
    Geometry.Bins g =
        new Geometry.Bins(
            List.of(new Geometry.Bar(0, 2, 3), new Geometry.Bar(2, 4, 0)),
            2,
            Summary.of(new double[] {1, 1, 1}));
    CanvasContent.Built b = built(s, Map.of(id(1), g));
    PlotContent c = b.content();
    assertThat(c.fills()).hasSize(1);
    assertThat(c.fills().getFirst().colour()).isEqualTo(COLOUR.withOpacity(0.5));
    assertThat(c.curves()).hasSize(1);
    assertThat(c.curves().getFirst().stroke().colour()).isEqualTo(COLOUR);
    assertThat(c.curves().getFirst().stroke().dash()).isEmpty();
    assertThat(c.curves().getFirst().ys())
        .containsExactly(0, 0, 3, 3, 0, Double.NaN, 0, 0, 0, 0, 0, Double.NaN);
    // the outlines one item, each bar an area after it
    assertThat(c.areas())
        .containsExactly(new PlotContent.Area(0, 2, 0, 3, 0), new PlotContent.Area(2, 4, 0, 0, 0));
    assertThat(b.items())
        .containsOnly(Map.entry(0, id(1)), Map.entry(1, id(1)), Map.entry(2, id(1)));
    // a bar's edge is hit, and its middle, far from any edge
    Optional<Hit> hit = c.index().nearest(0.02, 1.5, VIEW, 6);
    assertThat(hit).isPresent();
    Hit middle = c.index().nearest(1, 1.5, VIEW, 6).orElseThrow();
    assertThat(middle.kind()).isEqualTo(Hit.Kind.AREA);
    assertThat(b.items().get(middle.item())).isEqualTo(id(1));
    // above the bar is nothing
    assertThat(c.index().nearest(1, 4, VIEW, 6)).isEmpty();
  }

  @Test
  void aBarChartsCategoriesAreWrittenUnderItsBars() {
    CanvasState s =
        CanvasState.EMPTY.add(i -> new BarChart(i, Style.DEFAULT, List.of("a", "b"), expr("v")));
    Geometry.Bars g =
        new Geometry.Bars(
            List.of(new Geometry.Bar(0.6, 1.4, 2), new Geometry.Bar(1.6, 2.4, 5)),
            List.of("a", "b"));
    PlotContent c = built(s, Map.of(id(1), g)).content();
    assertThat(c.labels()).extracting(PlotContent.Label::text).containsExactly("a", "b");
    assertThat(c.labels().get(1).x()).isEqualTo(2);
    assertThat(c.labels().get(1).y()).isEqualTo(0);
    assertThat(c.labels().get(1).dy()).isPositive();
    // a wide bar is hit in its middle
    assertThat(c.index().nearest(2, 2.5, VIEW, 6).map(Hit::kind)).contains(Hit.Kind.AREA);
    assertThat(c.index().nearest(2, 2.5, VIEW, 6).map(Hit::item)).contains(2);
  }

  @Test
  void aBoxPlotsBoxIsAFixedNumberOfPixelsThickAndHitAnywhereInside() {
    CanvasState s =
        CanvasState.EMPTY.add(i -> new BoxPlot(i, Style.DEFAULT, expr("d"), BoxPlot.DEFAULT_Y));
    Summary summary = Summary.of(new double[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 30});
    Geometry.Box g = new Geometry.Box(summary, 1, 9, List.of(30.0), 2);
    CanvasContent.Built b = built(s, Map.of(id(1), g));
    PlotContent c = b.content();
    // the box, the median, the whiskers' ends
    assertThat(c.boxes()).hasSize(4);
    PlotContent.Box box = c.boxes().getFirst();
    assertThat(box.x0()).isEqualTo(3);
    assertThat(box.x1()).isEqualTo(8);
    assertThat(box.half()).isEqualTo(CanvasContent.BOX_HALF);
    assertThat(box.fill()).contains(COLOUR.withOpacity(0.5));
    assertThat(c.boxes().get(1).x0()).isEqualTo(5.5);
    // the whiskers a hit item, the outlier a point, the box an area as thick as it is drawn
    assertThat(c.curves()).hasSize(1);
    assertThat(c.curves().getFirst().xs()).containsExactly(1, 3, Double.NaN, 8, 9);
    assertThat(c.points()).hasSize(1);
    assertThat(c.areas()).containsExactly(new PlotContent.Area(3, 8, 2, 2, CanvasContent.BOX_HALF));
    assertThat(b.items()).containsOnlyKeys(0, 1, 2).containsValues(id(1));
    // 50 pixels a unit up: 0.18 units, 9 pixels, above the middle is inside the box
    Hit inside = c.index().nearest(4, 2.18, VIEW, 6).orElseThrow();
    assertThat(inside.kind()).isEqualTo(Hit.Kind.AREA);
    assertThat(inside.item()).isEqualTo(2);
    assertThat(inside.distance()).isZero();
    assertThat(c.index().nearest(4, 2.4, VIEW, 6)).isEmpty();
    assertThat(c.index().nearest(30, 2, new Viewport(-1, 31, -5, 5, 500, 500, false), 6))
        .map(Hit::item)
        .contains(1);

    // in pixels whatever the zoom: 20 pixels thick on a view of 50 pixels a unit and of 5
    String near = Export.svg(VIEW, THEME, c, Ticks.estimate(12));
    assertThat(near).contains("M200 160L450 160L450 140L200 140L200 160");
    String far =
        Export.svg(new Viewport(-1, 9, -50, 50, 500, 500, false), THEME, c, Ticks.estimate(12));
    assertThat(far).contains("M200 250L450 250L450 230L200 230L200 250");
  }

  @Test
  void aScatterPlotIsAMarkerPerPointEachAHitItem() {
    CanvasState s =
        CanvasState.EMPTY.add(
            i -> new ScatterPlot(i, Style.DEFAULT, new ScatterPlot.Pairs(expr("m"))));
    Summary one = Summary.of(new double[] {1, 2});
    Geometry.Scatter g =
        new Geometry.Scatter(List.of(new Geometry.Point(1, 1), new Geometry.Point(2, 4)), one, one);
    CanvasContent.Built b = built(s, Map.of(id(1), g));
    assertThat(b.content().points()).hasSize(2);
    assertThat(b.content().points().getFirst().colour()).isEqualTo(COLOUR);
    assertThat(b.items()).containsOnlyKeys(0, 1).containsValues(id(1));
    assertThat(b.content().index().nearest(2, 4, VIEW, 6).map(Hit::item)).contains(1);
  }
}
