package mathcanvas.canvas.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.util.List;
import java.util.Map;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.InequalityRegion;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Path;
import mathcanvas.canvas.model.Polyline;
import mathcanvas.canvas.model.Segment;
import mathcanvas.canvas.model.ShadedArea;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Text;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.Stroke;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

class CanvasContentTest {

  /** The dark palette of canvas-view.css, which the view resolves at run time. */
  private static final CanvasTheme DARK =
      new CanvasTheme(
          Rgba.rgb(0x1c1c1e),
          Rgba.rgb(0xc7c7cc),
          Rgba.rgb(0x3a3a3c),
          Rgba.rgb(0x2a2a2c),
          Rgba.rgb(0xc7c7cc),
          List.of(
              Rgba.rgb(0x4ea3f0),
              Rgba.rgb(0xff6b6b),
              Rgba.rgb(0x51cf66),
              Rgba.rgb(0xb197fc),
              Rgba.rgb(0xffa94d),
              Rgba.rgb(0x3bc9db),
              Rgba.rgb(0xc9a27e),
              Rgba.rgb(0xf783ac)));

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  private static Polyline square() {
    return Polyline.unparametrised(new double[] {0, 1, 1, 0, 0}, new double[] {0, 0, 1, 1, 0});
  }

  /**
   * Points 1 (slot 2, labelled), 2 (slot 5, hidden); a dashed segment 3 between them (slot 3); a
   * text 4 (slot 7); regions 5 (strict, slot 1) and 6 (not strict, slot 0, width 3).
   */
  private static CanvasState scene() {
    CanvasState s =
        CanvasState.EMPTY
            .add(i -> new FreePoint(i, new Style(2, 2, false, true, true), 1, 2))
            .add(i -> new FreePoint(i, new Style(5, 2, false, false, true), 3, 4))
            .add(i -> new Segment(i, new Style(3, 2, true, true, false), id(1), id(2)))
            .add(i -> new Text(i, new Style(7, 2, false, true, false), 0, 0, "note"))
            .add(
                i ->
                    new InequalityRegion(i, new Style(1, 2, false, true, false), relation("y < x")))
            .add(
                i ->
                    new InequalityRegion(
                        i, new Style(0, 3, false, true, false), relation("y >= x")));
    Map<ObjectId, Geometry> g =
        Map.of(
            id(1), new Geometry.Point(1, 2),
            id(2), new Geometry.Point(3, 4),
            id(3),
                new Geometry.Curve(
                    Path.POLYLINE_ONLY,
                    List.of(
                        Polyline.unparametrised(new double[] {1, 3}, new double[] {2, 4}),
                        Polyline.unparametrised(new double[] {5}, new double[] {6}))),
            id(4), new Geometry.Point(0, 0),
            id(5), new Geometry.Region(List.of(square())),
            id(6), new Geometry.Region(List.of(square())));
    return new CanvasState(s.objects(), g, s.nextId());
  }

  private static Relation relation(String text) {
    return (Relation) Parser.parseExpression(text, Names.NONE);
  }

  @Test
  void slotsTakeTheThemesSeriesColourInBothThemes() {
    for (CanvasTheme theme : List.of(CanvasTheme.LIGHT, DARK)) {
      CanvasContent.Built b = CanvasContent.build(scene(), Map.of(id(1), "A"), theme);
      PlotContent c = b.content();
      List<Rgba> s = theme.series();
      assertThat(c.points())
          .singleElement()
          .satisfies(m -> assertThat(m.colour()).isEqualTo(s.get(2)));
      assertThat(c.curves().get(0).stroke()).isEqualTo(Stroke.dashed(s.get(3), 2, 3 * 2, 2 * 2));
      assertThat(c.fills().get(0).colour()).isEqualTo(s.get(1).withOpacity(0.25));
      assertThat(c.curves().get(1).stroke()).isEqualTo(Stroke.dashed(s.get(1), 2, 6, 4));
      assertThat(c.fills().get(1).colour()).isEqualTo(s.get(0).withOpacity(0.25));
      assertThat(c.curves().get(2).stroke()).isEqualTo(Stroke.solid(s.get(0), 3));
      assertThat(c.labels())
          .extracting(PlotContent.Label::text, l -> l.style().colour())
          .containsExactly(tuple("A", s.get(2)), tuple("note", s.get(7)));
    }
  }

  @Test
  void aHiddenObjectIsNeitherDrawnNorHit() {
    CanvasContent.Built b = CanvasContent.build(scene(), Map.of(id(2), "B"), CanvasTheme.LIGHT);
    assertThat(b.items()).doesNotContainValue(id(2));
    assertThat(b.content().points()).hasSize(1);
    assertThat(b.content().labels()).extracting(PlotContent.Label::text).containsExactly("note");
    assertThat(b.items())
        .containsExactlyInAnyOrderEntriesOf(Map.of(0, id(3), 1, id(5), 2, id(6), 3, id(1)));
    assertThat(b.content().index().size()).isGreaterThan(0);
  }

  @Test
  void aCurvesBranchesAreOnePolylineBrokenByGaps() {
    PlotContent.Curve c =
        CanvasContent.build(scene(), Map.of(), CanvasTheme.LIGHT).content().curves().getFirst();
    assertThat(c.xs()).containsExactly(1, 3, Double.NaN, 5, Double.NaN);
    assertThat(c.ys()).containsExactly(2, 4, Double.NaN, 6, Double.NaN);
  }

  @Test
  void aValuedRegionIsFilledStrokedHitAndWrittenAtItsCentroid() {
    CanvasState s =
        CanvasState.EMPTY
            .add(
                i ->
                    new FunctionGraph(
                        i,
                        Style.DEFAULT,
                        Parser.parseExpression("x", Names.NONE),
                        mathcanvas.engine.expr.Symbol.of("x"),
                        FunctionGraph.ALL))
            .add(
                i ->
                    new ShadedArea(
                        i,
                        new Style(4, 2, false, true, false),
                        id(1),
                        java.util.Optional.empty(),
                        Parser.parseExpression("0", Names.NONE),
                        Parser.parseExpression("1", Names.NONE)));
    CanvasState drawn =
        new CanvasState(
            s.objects(),
            Map.of(id(2), new Geometry.ValuedRegion(List.of(square()), 1)),
            s.nextId());
    CanvasContent.Built b = CanvasContent.build(drawn, Map.of(id(2), "A = 1"), CanvasTheme.LIGHT);
    Rgba colour = CanvasTheme.LIGHT.series().get(4);
    assertThat(b.content().fills())
        .singleElement()
        .satisfies(f -> assertThat(f.colour()).isEqualTo(colour.withOpacity(0.25)));
    assertThat(b.items()).containsValue(id(2));
    assertThat(b.content().labels())
        .singleElement()
        .satisfies(
            l -> {
              assertThat(l.text()).isEqualTo("A = 1");
              // the theme's text on a box of its background, to read over the fill
              assertThat(l.style().colour()).isEqualTo(CanvasTheme.LIGHT.text());
              assertThat(l.style().background())
                  .contains(CanvasTheme.LIGHT.background().withOpacity(0.75));
              assertThat(l.x()).isCloseTo(0.5, org.assertj.core.api.Assertions.within(1e-12));
              assertThat(l.y()).isCloseTo(0.5, org.assertj.core.api.Assertions.within(1e-12));
            });
    // rectangles on both sides of the axis: the centroid by unsigned area
    Polyline below =
        Polyline.unparametrised(new double[] {1, 2, 2, 1, 1}, new double[] {0, 0, -1, -1, 0});
    assertThat(CanvasContent.centroid(List.of(square(), below))).containsExactly(1.0, 0.0);
  }

  @Test
  void anAreaBetweenCrossingCurvesIsLabelledInsideIt() {
    var sampler =
        new mathcanvas.canvas.sample.Samplers()
            .over(
                mathcanvas.engine.eval.Environment.EMPTY, mathcanvas.engine.eval.Settings.DEFAULT);
    mathcanvas.engine.expr.Symbol x = mathcanvas.engine.expr.Symbol.of("x");
    String[][] cases = {{"x", "x^2 - 2", "-2", "3"}, {"x", "x^3", "-1", "1"}};
    for (String[] c : cases) {
      CanvasState s =
          CanvasState.EMPTY
              .add(
                  i ->
                      new FunctionGraph(
                          i,
                          Style.DEFAULT,
                          Parser.parseExpression(c[0], Names.NONE),
                          x,
                          FunctionGraph.ALL))
              .add(
                  i ->
                      new FunctionGraph(
                          i,
                          Style.DEFAULT,
                          Parser.parseExpression(c[1], Names.NONE),
                          x,
                          FunctionGraph.ALL))
              .add(
                  i ->
                      new ShadedArea(
                          i,
                          Style.DEFAULT,
                          id(1),
                          java.util.Optional.of(id(2)),
                          Parser.parseExpression(c[2], Names.NONE),
                          Parser.parseExpression(c[3], Names.NONE)))
              .recomputeAll(
                  sampler,
                  new mathcanvas.canvas.model.Window(-5, 5, -5, 5),
                  mathcanvas.canvas.model.Recomputes.standard(),
                  mathcanvas.engine.Cancellation.NEVER);
      Geometry.ValuedRegion r = (Geometry.ValuedRegion) s.geometryOf(id(3));
      double[] at = CanvasContent.centroid(r.rings());
      double lo = Double.parseDouble(c[2]);
      double hi = Double.parseDouble(c[3]);
      // the shoelace of one self-crossing ring put it at y = −10, or at ±10¹⁴ for x³
      assertThat(at[0]).as("%s against %s", c[0], c[1]).isBetween(lo, hi);
      assertThat(at[1]).as("%s against %s", c[0], c[1]).isBetween(-2.0, 7.0);
    }
  }
}
