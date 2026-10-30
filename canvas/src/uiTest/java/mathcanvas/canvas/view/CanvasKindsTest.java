package mathcanvas.canvas.view;

import static java.util.concurrent.TimeUnit.SECONDS;
import static mathcanvas.canvas.view.UiHarness.SCREENSHOTS;
import static mathcanvas.canvas.view.UiHarness.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testfx.util.WaitForAsyncUtils.asyncFx;
import static org.testfx.util.WaitForAsyncUtils.waitFor;
import static org.testfx.util.WaitForAsyncUtils.waitForFxEvents;

import java.math.BigDecimal;
import java.util.Map;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.ImplicitCurve;
import mathcanvas.canvas.model.InequalityRegion;
import mathcanvas.canvas.model.Intersection;
import mathcanvas.canvas.model.Line;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParamKind;
import mathcanvas.canvas.model.ParametricCurve;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.PolarCurve;
import mathcanvas.canvas.model.Recomputes;
import mathcanvas.canvas.model.Segment;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Text;
import mathcanvas.canvas.model.Window;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.sample.Samplers;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Definition;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * One object of every kind, sampled by the real sampler, built into content and drawn by the view,
 * 800 by 600 pixels at 40 to a unit, in the light and the dark theme: a slider, function graphs
 * (one hidden), a parametric curve, a polar rose, an implicit hyperbola, a strict and a non-strict
 * region, a free point, a point on a graph, an intersection, a segment, a line and a text, with
 * colour slots, widths, dashes and labels. Checks that every visible object is in the content and
 * the hidden graph is neither drawn nor hit, and that a region's fill is its slot's colour over the
 * background; writes {@code canvas-kinds-light.png} and {@code canvas-kinds-dark.png}.
 */
@ExtendWith(ApplicationExtension.class)
class CanvasKindsTest {

  private static final Symbol X = Symbol.of("x");
  private static final Symbol T = Symbol.of("t");
  private static final ObjectId HIDDEN = new ObjectId(5);
  private static final int HIDDEN_SLOT = 7;

  private Stage stage;
  private StackPane root;
  private CanvasView view;

  @Start
  void start(Stage stage) {
    this.stage = stage;
    view = new CanvasView();
    root = new StackPane(view);
    stage.setTitle("Canvas kinds");
    stage.setScene(new Scene(root, 800, 600));
    stage.sizeToScene();
    stage.show();
  }

  private static Expr expr(String text) {
    return Parser.parseExpression(text, Names.NONE);
  }

  private static Style style(int slot, double width, boolean dashed) {
    return new Style(slot, width, dashed, true, true);
  }

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  /** Every kind; ids in the order added, from 1. */
  private static CanvasState scene() {
    return CanvasState.EMPTY
        .add(
            i ->
                new Slider(
                    i,
                    Style.DEFAULT,
                    "a",
                    BigDecimal.ZERO,
                    BigDecimal.valueOf(3),
                    new BigDecimal("0.1"),
                    new BigDecimal("1.5")))
        // 2: a·sin x, solid, slot 0
        .add(
            i -> new FunctionGraph(i, style(0, 2.5, false), expr("a sin(x)"), X, FunctionGraph.ALL))
        // 3: a cubic, slot 6
        .add(i -> new FunctionGraph(i, style(6, 2, false), expr("x^3/8 - x"), X, FunctionGraph.ALL))
        // 4: tan x on a domain, thin, slot 4, dashed
        .add(
            i ->
                new FunctionGraph(
                    i,
                    style(4, 1.5, true),
                    expr("tan(x)"),
                    X,
                    Interval.of(expr("2"), expr("9"), true, true)))
        // 5: hidden
        .add(
            i ->
                new FunctionGraph(
                    i,
                    new Style(HIDDEN_SLOT, 3, false, false, true),
                    expr("x^2/4 + 3"),
                    X,
                    FunctionGraph.ALL))
        // 6: an ellipse, slot 2, wide
        .add(
            i ->
                new ParametricCurve(
                    i,
                    style(2, 3, false),
                    expr("3cos(t) - 5"),
                    expr("2sin(t) + 4"),
                    T,
                    Interval.of(expr("0"), expr("2π"), true, true)))
        // 7: a rose, slot 3, dashed
        .add(
            i ->
                new PolarCurve(
                    i, style(3, 2, true), expr("2cos(3θ)"), Symbol.of("θ"), PolarCurve.TURN))
        // 8: a hyperbola, slot 1
        .add(i -> new ImplicitCurve(i, style(1, 2, false), (Relation) expr("x^2 - y^2 = 25")))
        // 9: a strict region, slot 5, its boundary dashed
        .add(i -> new InequalityRegion(i, style(5, 2, false), (Relation) expr("y < sin(x) - 5")))
        // 10: a non-strict region, slot 2
        .add(
            i ->
                new InequalityRegion(
                    i, style(2, 2, false), (Relation) expr("(x - 6)^2 + (y - 4)^2 <= 2")))
        // 11: a free point A
        .add(i -> new FreePoint(i, style(1, 2, false), -7, -2))
        // 12: P on the sine
        .add(i -> new PointOnPath(i, style(0, 2, false), id(2), ParamKind.X, 1.2, 0, 0))
        // 13: where the sine meets the cubic
        .add(i -> Intersection.of(i, style(6, 2, false), id(2), id(3), 0))
        // 14: segment AP, dashed
        .add(i -> new Segment(i, style(3, 1.5, true), id(11), id(12)))
        // 15: the line through A and the intersection, slot 4
        .add(i -> new Line(i, style(4, 1, false), new Line.Through(id(11), id(13))))
        // 16: a text
        .add(i -> new Text(i, style(6, 2, false), 3, -7, "every kind"));
  }

  private static final Map<ObjectId, String> LABELS =
      Map.of(id(11), "A", id(12), "P", id(13), "Q", id(5), "hidden");

  @Test
  void drawsEveryKindInBothThemes() throws Exception {
    waitFor(10, SECONDS, () -> waitFor(10, SECONDS, asyncFx(() -> view.frames() > 0)));
    Viewport v = waitFor(10, SECONDS, asyncFx(view::getViewport));
    Window window = new Window(v.xMin(), v.xMax(), v.yMin(), v.yMax());
    Environment env = Environment.EMPTY.with("a", new Definition.Value(expr("1.5")));
    CanvasState state =
        scene()
            .recomputeAll(
                new Samplers().over(env, Settings.DEFAULT, v.width(), v.height()),
                window,
                Recomputes.standard(),
                Cancellation.NEVER);

    show(state, "canvas-kinds-light.png", Rgba.rgb(0xffffff));
    asyncFx(() -> root.getStyleClass().add("dark"));
    settle();
    show(state, "canvas-kinds-dark.png", Rgba.rgb(0x1c1c1e));
    asyncFx(() -> root.getStyleClass().remove("dark"));
    settle();
  }

  private void show(CanvasState state, String file, Rgba background) throws Exception {
    CanvasTheme theme = waitFor(10, SECONDS, asyncFx(view::theme));
    CanvasContent.Built built = CanvasContent.build(state, LABELS, theme);
    assertThat(built.items().values())
        .containsExactlyInAnyOrder(
            id(2), id(3), id(4), id(6), id(7), id(8), id(9), id(10), id(11), id(12), id(13), id(14),
            id(15))
        .doesNotContain(HIDDEN);
    assertThat(built.content().labels())
        .extracting(PlotContent.Label::text)
        .containsExactly("A", "P", "Q", "every kind");
    asyncFx(() -> view.setContent(built.content()));
    settle();
    WritableImage image = waitFor(10, SECONDS, asyncFx(() -> stage.getScene().snapshot(null)));
    write(image, SCREENSHOTS.resolve(file));
    PixelReader pixels = image.getPixelReader();

    // the hidden graph's colour is nowhere; the sine's is
    int hidden = argb(theme.series().get(HIDDEN_SLOT));
    int sine = argb(theme.series().get(0));
    int hiddenCount = 0;
    int sineCount = 0;
    for (int y = 0; y < (int) image.getHeight(); y++) {
      for (int x = 0; x < (int) image.getWidth(); x++) {
        int p = pixels.getArgb(x, y);
        hiddenCount += p == hidden ? 1 : 0;
        sineCount += p == sine ? 1 : 0;
      }
    }
    assertThat(hiddenCount).isZero();
    assertThat(sineCount).isGreaterThan(100);

    // inside y < sin x − 5 at (−7.5, −6.75), off the grid lines: slot 5 at a quarter over the
    // background
    Rgba fill = theme.series().get(5);
    int p = pixels.getArgb(100, 570);
    assertChannel(p >> 16, fill.red(), background.red());
    assertChannel(p >> 8, fill.green(), background.green());
    assertChannel(p, fill.blue(), background.blue());
  }

  private static void assertChannel(int actual, double fill, double background) {
    double expected =
        255 * (CanvasContent.FILL_OPACITY * fill + (1 - CanvasContent.FILL_OPACITY) * background);
    assertThat((double) (actual & 0xff))
        .isCloseTo(expected, org.assertj.core.api.Assertions.within(3.0));
  }

  private static int argb(Rgba c) {
    return 0xff000000 | Integer.parseInt(c.hex().substring(1), 16);
  }

  private static void settle() {
    for (int i = 0; i < 5; i++) {
      waitForFxEvents();
    }
  }
}
