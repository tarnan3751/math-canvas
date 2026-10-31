package mathcanvas.canvas.tool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Line;
import mathcanvas.canvas.model.ParamKind;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;

/**
 * The secant tool: a click on a function graph adds, as one step, the sliders {@code a} (at the
 * click, from 10 below it to 10 above, step 0.01) and {@code h} (0.01 to 2, step 0.01, at 1) when
 * the canvas has none, a point on the graph at {@code a} and one at {@code a + h}, the secant line
 * through them, and the tangent at the first, dashed: dragging {@code h} toward 0 turns the secant
 * into the tangent.
 */
public final class SecantTool implements Tool {

  /** The variable of the first point's abscissa. */
  public static final String AT = "a";

  /** The variable of the step to the second point. */
  public static final String STEP = "h";

  private static final BigDecimal HUNDREDTH = new BigDecimal("0.01");

  private Scene scene;
  private Snap snap;

  /** Makes the secant tool. */
  public SecantTool() {}

  @Override
  public boolean press(Scene scene, Pointer pointer, Consumer<Edit> edits) {
    move(scene, pointer);
    Optional<FunctionGraph> g = Picks.graph(scene, snap);
    if (g.isEmpty()) {
      return false;
    }
    edits.accept(new Edit.Add(news(scene, g.get(), Picks.abscissa(snap))));
    return true;
  }

  private static List<Edit.New> news(Scene scene, FunctionGraph g, double x) {
    List<Edit.New> out = new ArrayList<>();
    if (Picks.slider(scene.canvas(), AT).isEmpty()) {
      BigDecimal at = new BigDecimal(x).setScale(2, RoundingMode.HALF_EVEN);
      out.add(
          new Edit.New(
              ids ->
                  new Slider(
                      ids.getLast(),
                      Style.DEFAULT,
                      AT,
                      at.subtract(BigDecimal.TEN),
                      at.add(BigDecimal.TEN),
                      HUNDREDTH,
                      at),
              false));
    }
    if (Picks.slider(scene.canvas(), STEP).isEmpty()) {
      out.add(
          new Edit.New(
              ids ->
                  new Slider(
                      ids.getLast(),
                      Style.DEFAULT,
                      STEP,
                      HUNDREDTH,
                      BigDecimal.TWO,
                      HUNDREDTH,
                      BigDecimal.ONE),
              false));
    }
    Expr a = Symbol.of(AT);
    Expr ah = Sum.of(a, Symbol.of(STEP));
    // the indexes of the two points and the lines within this addition
    int p = out.size();
    out.add(new Edit.New(ids -> point(ids.getLast(), g, a), true));
    out.add(new Edit.New(ids -> point(ids.getLast(), g, ah), true));
    out.add(
        new Edit.New(
            ids ->
                new Line(
                    ids.getLast(), Style.DEFAULT, new Line.Through(ids.get(p), ids.get(p + 1))),
            false));
    out.add(
        new Edit.New(
            ids ->
                new Line(
                    ids.getLast(),
                    new Style(0, Style.DEFAULT.width(), true, true, false),
                    new Line.PointSlope(
                        ids.get(p), Derivative.of(g.body(), g.variable(), 1), g.variable())),
            false));
    return out;
  }

  private static PointOnPath point(mathcanvas.canvas.model.ObjectId id, FunctionGraph g, Expr at) {
    return new PointOnPath(
        id, Style.DEFAULT, g.id(), ParamKind.X, 0, Double.NaN, Double.NaN, Optional.of(at));
  }

  @Override
  public void drag(Scene scene, Pointer pointer, Consumer<Edit> edits) {}

  @Override
  public void release(Scene scene, Pointer pointer, Consumer<Edit> edits) {}

  @Override
  public void move(Scene scene, Pointer pointer) {
    this.scene = scene;
    this.snap = Snap.of(scene, pointer);
  }

  @Override
  public void cancel() {
    scene = null;
    snap = null;
  }

  @Override
  public boolean inProgress() {
    return false;
  }

  @Override
  public void paint(Painter painter, CanvasTheme theme) {
    if (scene == null || snap == null) {
      return;
    }
    Picks.graph(scene, snap)
        .ifPresent(
            g -> {
              Preview.curve(painter, theme, scene.canvas(), g.id());
              Preview.snap(painter, theme, snap);
            });
  }
}
