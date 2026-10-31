package mathcanvas.canvas.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mathcanvas.canvas.hit.Hit;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.Line;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.Recomputes;
import mathcanvas.canvas.model.RiemannSum;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.ShadedArea;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Window;
import mathcanvas.canvas.sample.Samplers;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.compile.Quadrature;
import mathcanvas.engine.eval.Definition;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

/**
 * The calculus tools over scripted clicks and typed numbers, on a canvas recomputed with the real
 * sampler: the area, the Riemann sum, the secant and the Taylor polynomial, what each adds, and
 * what that draws.
 */
class CalculusToolsTest {

  private static final Window W = new Window(-4, 4, -4, 4);
  private static final Symbol X = Symbol.of("x");
  private static final ObjectId ONE = new ObjectId(1);
  private static final ObjectId TWO = new ObjectId(2);

  private static Expr expr(String text) {
    return Parser.parseExpression(text, Names.NONE);
  }

  private static FunctionGraph graph(ObjectId id, String body) {
    return new FunctionGraph(id, Style.DEFAULT, expr(body), X, FunctionGraph.ALL);
  }

  /** The sampler of an environment where the sliders' variables have the given values. */
  private static Sampler sampler(String... definitions) {
    Environment env = Environment.EMPTY;
    for (int k = 0; k < definitions.length; k += 2) {
      env = env.with(definitions[k], new Definition.Value(expr(definitions[k + 1])));
    }
    return new Samplers().over(env, Settings.DEFAULT);
  }

  private static CanvasState recomputed(CanvasState c, Sampler s) {
    return c.recomputeAll(s, W, Recomputes.standard(), Cancellation.NEVER);
  }

  /** {@code x²} (#1) and {@code x} (#2). */
  private static CanvasState scene() {
    return recomputed(
        CanvasState.EMPTY.add(id -> graph(id, "x^2")).add(id -> graph(id, "x")), sampler());
  }

  private static Scene scene(CanvasState c) {
    return new Scene(c, W, Scene.Slopes.NONE);
  }

  private static Pointer onCurve(CanvasState c, ObjectId id, double x) {
    double y = Picks.ordinate(c, id, x);
    Hit hit = new Hit(0, Hit.Kind.POLYLINE, 0, 0, x, y, 1);
    return new Pointer(x, y, Optional.of(new Pointer.Picked(id, hit)));
  }

  private static Pointer free(double x, double y) {
    return Pointer.at(x, y);
  }

  private static CanvasState apply(CanvasState c, Edit e) {
    CanvasState out = c;
    for (CanvasObject o : ((Edit.Add) e).made(c.nextObjectId())) {
      out = out.add(id -> o);
    }
    return out;
  }

  @Test
  void theAreaToolTakesAGraphThenTwoBounds() {
    CanvasState c = scene();
    AreaTool t = new AreaTool();
    List<Edit> edits = new ArrayList<>();
    assertThat(t.press(scene(c), free(0, 3), edits::add)).isFalse();
    assertThat(t.press(scene(c), onCurve(c, ONE, 1.5), edits::add)).isTrue();
    assertThat(t.wanted()).contains("a");
    t.press(scene(c), free(0, -3), edits::add);
    assertThat(t.wanted()).contains("b");
    assertThat(edits).isEmpty();
    t.press(scene(c), free(1.0001, -3), edits::add);
    assertThat(t.inProgress()).isFalse();
    ShadedArea a = (ShadedArea) ((Edit.Add) edits.getFirst()).made(c.nextObjectId()).getFirst();
    assertThat(a.upper()).isEqualTo(ONE);
    assertThat(a.lower()).isEmpty();
    // the click is kept as a decimal rounded to the window's thousandth
    assertThat(a.to()).isEqualTo(new Num(Real.of(new BigDecimal("1.000"))));
    CanvasState drawn = recomputed(apply(c, edits.getFirst()), sampler());
    assertThat(((Geometry.ValuedRegion) drawn.geometryOf(a.id())).value())
        .isCloseTo(1 / 3.0, within(1e-10));
  }

  @Test
  void aSecondGraphBeforeTheBoundsMakesAnAreaBetweenThem() {
    CanvasState c = scene();
    AreaTool t = new AreaTool();
    List<Edit> edits = new ArrayList<>();
    t.press(scene(c), onCurve(c, TWO, 0.5), edits::add);
    t.press(scene(c), onCurve(c, ONE, 0.5), edits::add);
    t.typed(expr("0"), 0, edits::add);
    t.typed(expr("b"), Double.NaN, edits::add);
    ShadedArea a = (ShadedArea) ((Edit.Add) edits.getFirst()).made(c.nextObjectId()).getFirst();
    assertThat(a.upper()).isEqualTo(TWO);
    assertThat(a.lower()).contains(ONE);
    assertThat(a.to()).isEqualTo(Symbol.of("b"));
    CanvasState drawn = recomputed(apply(c, edits.getFirst()), sampler("b", "1"));
    assertThat(((Geometry.ValuedRegion) drawn.geometryOf(a.id())).value())
        .isCloseTo(1 / 6.0, within(1e-10));
  }

  @Test
  void escapeAbandonsAnArea() {
    CanvasState c = scene();
    AreaTool t = new AreaTool();
    List<Edit> edits = new ArrayList<>();
    t.press(scene(c), onCurve(c, ONE, 1), edits::add);
    t.press(scene(c), free(0, 0), edits::add);
    t.cancel();
    assertThat(t.inProgress()).isFalse();
    assertThat(t.wanted()).isEmpty();
    assertThat(edits).isEmpty();
  }

  @Test
  void theRiemannToolAddsTheSliderNOnceAndTheSum() {
    CanvasState c = scene();
    RiemannTool t = new RiemannTool();
    List<Edit> edits = new ArrayList<>();
    t.press(scene(c), onCurve(c, TWO, 0.5), edits::add);
    t.typed(expr("0"), 0, edits::add);
    t.typed(expr("1"), 1, edits::add);
    List<CanvasObject> made = ((Edit.Add) edits.getFirst()).made(c.nextObjectId());
    assertThat(made).hasSize(2);
    Slider n = (Slider) made.get(0);
    assertThat(n.variable()).isEqualTo("n");
    assertThat(n.min()).isEqualByComparingTo("1");
    assertThat(n.max()).isEqualByComparingTo("100");
    RiemannSum s = (RiemannSum) made.get(1);
    assertThat(s.method()).isEqualTo(Quadrature.Method.LEFT);
    assertThat(s.n()).isEqualTo(Symbol.of("n"));
    CanvasState drawn = recomputed(apply(c, edits.getFirst()), sampler("n", "4"));
    assertThat(((Geometry.ValuedRegion) drawn.geometryOf(s.id())).value())
        .isCloseTo(3 / 8.0, within(1e-15));
    // a second sum reads the same slider
    CanvasState with = apply(c, edits.getFirst());
    t.press(scene(with), onCurve(c, ONE, 0.5), edits::add);
    t.typed(expr("0"), 0, edits::add);
    t.typed(expr("2"), 2, edits::add);
    assertThat(((Edit.Add) edits.get(1)).made(with.nextObjectId()))
        .singleElement()
        .isInstanceOf(RiemannSum.class);
  }

  @Test
  void theSecantToolAddsItsSlidersPointsAndLines() {
    CanvasState c = scene();
    SecantTool t = new SecantTool();
    List<Edit> edits = new ArrayList<>();
    assertThat(t.press(scene(c), free(0, 3), edits::add)).isFalse();
    assertThat(t.press(scene(c), onCurve(c, ONE, 1), edits::add)).isTrue();
    List<CanvasObject> made = ((Edit.Add) edits.getFirst()).made(c.nextObjectId());
    assertThat(made)
        .extracting(o -> o.getClass().getSimpleName())
        .containsExactly("Slider", "Slider", "PointOnPath", "PointOnPath", "Line", "Line");
    Slider a = (Slider) made.get(0);
    Slider h = (Slider) made.get(1);
    assertThat(a.variable()).isEqualTo("a");
    assertThat(a.value()).isEqualByComparingTo("1.00");
    assertThat(h.variable()).isEqualTo("h");
    assertThat(h.min()).isEqualByComparingTo("0.01");
    assertThat(h.max()).isEqualByComparingTo("2");
    assertThat(((PointOnPath) made.get(3)).at()).contains(expr("a + h"));
    assertThat(((Line) made.get(5)).style().dashed()).isTrue();
    // with a = 1: the secant to 1 + h has slope 2 + h, the tangent 2
    CanvasState drawn = recomputed(apply(c, edits.getFirst()), sampler("a", "1", "h", "0.5"));
    assertThat(drawn.geometryOf(made.get(3).id())).isEqualTo(new Geometry.Point(1.5, 2.25));
    assertThat(slope(drawn, made.get(4).id())).isCloseTo(2.5, within(1e-9));
    assertThat(slope(drawn, made.get(5).id())).isCloseTo(2, within(1e-9));
    CanvasState closer = recomputed(apply(c, edits.getFirst()), sampler("a", "1", "h", "0.01"));
    assertThat(slope(closer, made.get(4).id())).isCloseTo(2.01, within(1e-9));
  }

  private static double slope(CanvasState c, ObjectId line) {
    Geometry.Curve g = (Geometry.Curve) c.geometryOf(line);
    var l =
        (mathcanvas.canvas.model.Parametrisation.Linear)
            ((mathcanvas.canvas.model.Path.Parametrised) g.path()).parametrisation();
    return l.dy() / l.dx();
  }

  @Test
  void theTaylorToolAddsTheOrderSliderAndThePolynomialsGraph() {
    CanvasState c = scene();
    TaylorTool t = new TaylorTool();
    List<Edit> edits = new ArrayList<>();
    t.press(scene(c), onCurve(c, ONE, 1), edits::add);
    assertThat(t.wanted()).contains("a");
    // the app hands the tool the typed text canonical
    Expr half = mathcanvas.engine.expr.Canonical.of(expr("1/2"));
    t.typed(half, 0.5, edits::add);
    List<CanvasObject> made = ((Edit.Add) edits.getFirst()).made(c.nextObjectId());
    assertThat(((Slider) made.get(0)).variable()).isEqualTo("n");
    FunctionGraph p = (FunctionGraph) made.get(1);
    assertThat(p.body())
        .isEqualTo(
            mathcanvas.engine.expr.Canonical.of(
                Apply.of(Symbol.of("taylor"), expr("x^2"), X, half, Symbol.of("n"))));
    assertThat(p.reads()).contains("n");
  }
}
