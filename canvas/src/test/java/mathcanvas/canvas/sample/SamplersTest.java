package mathcanvas.canvas.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Random;
import mathcanvas.canvas.model.ImplicitCurve;
import mathcanvas.canvas.model.Parametrisation;
import mathcanvas.canvas.model.Polyline;
import mathcanvas.canvas.model.Sample;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.Window;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.CancelledException;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.eval.Definition;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SamplersTest {

  static final Symbol X = Symbol.of("x");
  static final Symbol T = Symbol.of("t");
  static final Window W = new Window(-10, 10, -10, 10);

  static Expr expr(String text) {
    return Parser.parseExpression(text, Names.NONE);
  }

  static Sampler sampler(Environment env) {
    return new Samplers().over(env, Settings.DEFAULT);
  }

  static List<Polyline> graph(Sampler s, String body, double lo, double hi, Window w) {
    return s.sample(new Parametrisation.Graph(expr(body), X), lo, hi, w, Cancellation.NEVER);
  }

  static Environment env(String name, String value) {
    return Environment.EMPTY.with(name, new Definition.Value(expr(value)));
  }

  @Test
  void aGraphRunsFromEndToEndOnTheCurve() {
    List<Polyline> out = graph(sampler(Environment.EMPTY), "x^2 - 3", -2, 3, W);
    assertThat(out).hasSize(1);
    Polyline p = out.getFirst();
    assertThat(p.t(0)).isEqualTo(-2);
    assertThat(p.t(p.size() - 1)).isEqualTo(3);
    for (int k = 0; k < p.size(); k++) {
      assertThat(p.x(k)).isEqualTo(p.t(k));
      assertThat(p.y(k)).isCloseTo(p.x(k) * p.x(k) - 3, within(1e-12));
      if (k > 0) {
        assertThat(p.t(k)).isGreaterThan(p.t(k - 1));
      }
    }
    assertThat(p.size()).isGreaterThanOrEqualTo(2 * Adaptive.INITIAL - 1);
  }

  @Test
  void aCurvedGraphIsRefinedWhereItBends() {
    Polyline flat = graph(sampler(Environment.EMPTY), "x/2 + 1", -10, 10, W).getFirst();
    Polyline bent = graph(sampler(Environment.EMPTY), "sin(5x)", -10, 10, W).getFirst();
    assertThat(flat.size()).isEqualTo(2 * Adaptive.INITIAL - 1);
    assertThat(bent.size()).isGreaterThan(flat.size());
  }

  @Test
  void theEnvironmentIsSubstitutedAndTheObjectsVariableShadowsIt() {
    Sampler s = sampler(env("a", "2").with("x", new Definition.Value(expr("5"))));
    Polyline p = graph(s, "a x", -1, 1, W).getFirst();
    assertThat(p.y(0)).isEqualTo(-2);
    assertThat(p.y(p.size() - 1)).isEqualTo(2);
    assertThat(s.value(expr("a + x"), Map.of(X, 1.0))).isEqualTo(3);
    assertThat(s.value(expr("a + x"), Map.of())).isEqualTo(7);
  }

  @Test
  void samplersOfOneDocumentShareTheMemo() {
    Samplers samplers = new Samplers();
    Sampler two = samplers.over(env("a", "2"), Settings.DEFAULT);
    graph(two, "x^2", -1, 1, W);
    graph(two, "a x", -1, 1, W);
    assertThat(samplers.size()).isEqualTo(2);
    Sampler three = samplers.over(env("a", "3"), Settings.DEFAULT);
    graph(three, "x^2", -1, 1, W);
    assertThat(samplers.size()).isEqualTo(2);
    graph(three, "a x", -1, 1, W);
    assertThat(samplers.size()).isEqualTo(3);
  }

  @Test
  void theMemoIsBounded() {
    Samplers samplers = new Samplers();
    Sampler s = samplers.over(Environment.EMPTY, Settings.DEFAULT);
    for (int k = 0; k < Samplers.CAPACITY + 40; k++) {
      s.value(expr("x + " + k), Map.of(X, 0.0));
    }
    assertThat(samplers.size()).isEqualTo(Samplers.CAPACITY);
  }

  @Test
  void whatCannotBeEvaluatedOrCompiledIsUndefined() {
    Sampler s = sampler(Environment.EMPTY);
    assertThat(graph(s, "f(x)", -1, 1, W)).isEmpty();
    assertThat(graph(s, "x + b", -1, 1, W)).isEmpty();
    assertThat(s.value(expr("[1, 2]"), Map.of())).isNaN();
    assertThat(s.point(new Parametrisation.Graph(expr("x + b"), X), 0)).isEqualTo(Sample.UNDEFINED);
    assertThat(
            s.contour((Relation) expr("x + b = 0"), new Window(-1, 1, -1, 1), Cancellation.NEVER))
        .isEmpty();
  }

  @Test
  void aGraphStopsAtTheDomainsEdge() {
    List<Polyline> out = graph(sampler(Environment.EMPTY), "sqrt(x)", -1, 1, W);
    assertThat(out).hasSize(1);
    Polyline p = out.getFirst();
    assertThat(p.x(0)).isBetween(0.0, 2.0 / (1 << Adaptive.DEPTH));
    assertThat(p.x(p.size() - 1)).isEqualTo(1);
  }

  @Test
  void aPoleSplitsTheGraph() {
    List<Polyline> out = graph(sampler(Environment.EMPTY), "1/(x - 0.3)", -10, 10, W);
    assertThat(out).hasSize(2);
    assertThat(out.get(0).x(out.get(0).size() - 1)).isLessThan(0.3);
    assertThat(out.get(1).x(0)).isGreaterThan(0.3);
  }

  /** Whether any segment of the polylines joins the two sides of x = a. */
  static boolean joinsAcross(List<Polyline> out, double a) {
    for (Polyline p : out) {
      for (int k = 1; k < p.size(); k++) {
        if ((p.x(k - 1) - a) * (p.x(k) - a) <= 0) {
          return true;
        }
      }
    }
    return false;
  }

  @Test
  void aPoleIsCutHoweverFarTheWindowReaches() {
    // the review's cases: 1/(x − a) in ±50 and ±100, joined at the nominal 10⁴-pixel test
    Random random = new Random(3);
    for (double half : new double[] {50, 100}) {
      Window w = new Window(-half, half, -half, half);
      for (int trial = 0; trial < 100; trial++) {
        double a = Math.round((random.nextDouble() * half - half / 2) * 1e6) / 1e6;
        String body = "1/(x - " + BigDecimal.valueOf(a).toPlainString() + ")";
        List<Polyline> out = graph(sampler(Environment.EMPTY), body, -2 * half, 2 * half, w);
        assertThat(joinsAcross(out, a)).as(body).isFalse();
      }
    }
    Polyline wide =
        graph(sampler(Environment.EMPTY), "tan(x)", -200, 200, new Window(-100, 100, -100, 100))
            .getFirst();
    assertThat(wide.x(wide.size() - 1)).isLessThan(-Math.PI * 63.5 + 0.01);
  }

  @Test
  void aSmallPoleIsCut() {
    // the review's case: 0.01/(x − a) in ±10, one unbroken polyline before
    Random random = new Random(7);
    Window w = new Window(-10, 10, -10, 10);
    for (int trial = 0; trial < 400; trial++) {
      double a = Math.round((random.nextDouble() * 10 - 5) * 1e6) / 1e6;
      String body = "0.01/(x - " + BigDecimal.valueOf(a).toPlainString() + ")";
      List<Polyline> out = graph(sampler(Environment.EMPTY), body, -20, 20, w);
      assertThat(out).as(body).hasSize(2);
      assertThat(joinsAcross(out, a)).as(body).isFalse();
    }
  }

  /**
   * Continuous curves, steep, nearly flat, kinked or jumping by less than a window's height, are
   * never cut as if at a pole: a continuous curve's half jumps about half as much as its parent.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "x^3 - x",
        "sin(50x)",
        "10^6 x",
        "x/1000",
        "tanh(1000x)",
        "atan(10^5 x)",
        "exp(x)",
        "0.001 sin(x)",
        "abs(x)",
        "floor(x)"
      })
  void aContinuousCurveIsNotCut(String body) {
    for (double half : new double[] {1, 10, 100}) {
      Window w = new Window(-half, half, -half, half);
      assertThat(graph(sampler(Environment.EMPTY), body, -2 * half, 2 * half, w))
          .as("%s in ±%s", body, half)
          .hasSize(1);
    }
  }

  @Test
  void aSteepLineIsNotTakenForAPole() {
    assertThat(graph(sampler(Environment.EMPTY), "10^6 x", -10, 10, W)).hasSize(1);
  }

  @Test
  void samplesFarOutsideTheWindowAreCut() {
    Window w = new Window(-1, 1, -1, 1);
    List<Polyline> out = graph(sampler(Environment.EMPTY), "x^2", -1.5, 1.5, w);
    assertThat(out).hasSize(1);
    Polyline p = out.getFirst();
    // y = x² leaves the widened window (up to 2) at |x| = √2; one sample beyond is kept
    assertThat(p.x(0)).isBetween(-1.5, -Math.sqrt(2) + 0.01);
    assertThat(p.x(1)).isGreaterThan(-Math.sqrt(2));
    assertThat(p.x(p.size() - 2)).isLessThan(Math.sqrt(2));
  }

  @Test
  void degreesAreHonoured() {
    Sampler s = new Samplers().over(Environment.EMPTY, new Settings(34, AngleMode.DEGREES));
    assertThat(s.point(new Parametrisation.Graph(expr("sin(x)"), X), 90).y()).isEqualTo(1);
    Sample polar = s.point(new Parametrisation.Polar(expr("2"), X), 90);
    assertThat(polar.x()).isCloseTo(0, within(1e-15));
    assertThat(polar.y()).isEqualTo(2);
  }

  @Test
  void aParametricCircleAndAPolarRoseAreClosed() {
    Sampler s = sampler(Environment.EMPTY);
    List<Polyline> circle =
        s.sample(
            new Parametrisation.Parametric(expr("cos(t)"), expr("sin(t)"), T),
            0,
            2 * Math.PI,
            W,
            Cancellation.NEVER);
    assertThat(circle).hasSize(1);
    assertClosed(circle.getFirst());
    List<Polyline> rose =
        s.sample(new Parametrisation.Polar(expr("cos(3t)"), T), 0, Math.PI, W, Cancellation.NEVER);
    assertThat(rose).hasSize(1);
    assertClosed(rose.getFirst());
  }

  static void assertClosed(Polyline p) {
    assertThat(p.x(p.size() - 1)).isCloseTo(p.x(0), within(1e-9));
    assertThat(p.y(p.size() - 1)).isCloseTo(p.y(0), within(1e-9));
  }

  @Test
  void aRequestToStopIsHonoured() {
    Sampler s = sampler(Environment.EMPTY);
    Cancellation stop = () -> true;
    assertThatThrownBy(
            () -> s.sample(new Parametrisation.Graph(expr("sin(x)"), X), -10, 10, W, stop))
        .isInstanceOf(CancelledException.class);
    assertThatThrownBy(() -> s.contour((Relation) expr("x^2 + y^2 = 1"), W, stop))
        .isInstanceOf(CancelledException.class);
  }

  @Test
  void aPointAndAValueStopWithTheTokenTheSamplerIsBoundTo() {
    long deadline = System.nanoTime() + 300_000_000L;
    Cancellation late = () -> System.nanoTime() > deadline;
    Sampler s = new Samplers().over(Environment.EMPTY, Settings.DEFAULT, 1000, 1000, late);
    Parametrisation.Graph slow =
        new Parametrisation.Graph(expr("expand((x + p + q + r + 1)^30)"), X);

    long start = System.nanoTime();
    assertThatThrownBy(() -> s.point(slow, 1.0)).isInstanceOf(CancelledException.class);
    // it stops, not how fast: a bound a loaded machine keeps
    assertThat((System.nanoTime() - start) / 1e6).isLessThan(5000);

    Cancellation stopped = () -> true;
    Sampler t = new Samplers().over(Environment.EMPTY, Settings.DEFAULT, 1000, 1000, stopped);
    assertThatThrownBy(() -> t.value(expr("sin(1)"), Map.of()))
        .isInstanceOf(CancelledException.class);
    assertThatThrownBy(
            () -> t.sample(new Parametrisation.Graph(expr("x"), X), -1, 1, W, Cancellation.NEVER))
        .isInstanceOf(CancelledException.class);
  }

  @Test
  void theUnitCircleIsOneClosedContourOnTheCircle() {
    Window w = new Window(-2, 2, -2, 2);
    double cell = w.width() / MarchingSquares.CELLS;
    List<Polyline> out =
        sampler(Environment.EMPTY).contour((Relation) expr("x^2 + y^2 = 1"), w, Cancellation.NEVER);
    assertThat(out).hasSize(1);
    Polyline p = out.getFirst();
    assertThat(p.size()).isGreaterThan(100);
    assertThat(p.x(p.size() - 1)).isEqualTo(p.x(0));
    assertThat(p.y(p.size() - 1)).isEqualTo(p.y(0));
    for (int k = 0; k < p.size(); k++) {
      assertThat(Math.hypot(p.x(k), p.y(k))).isCloseTo(1, within(2 * cell));
      assertThat(p.t(k)).isNaN();
    }
  }

  @Test
  void aContourDoesNotJoinAcrossAPole() {
    List<Polyline> out =
        sampler(Environment.EMPTY)
            .contour(
                (Relation) expr("y = 1/(x - 0.01)"), new Window(-2, 2, -2, 2), Cancellation.NEVER);
    assertThat(out).isNotEmpty();
    for (Polyline p : out) {
      for (int k = 1; k < p.size(); k++) {
        assertThat((p.x(k - 1) - 0.01) * (p.x(k) - 0.01)).isGreaterThan(0);
      }
    }
  }

  @Test
  void theInsideOfTheUnitCircleHasAreaPi() {
    List<Polyline> rings =
        sampler(Environment.EMPTY)
            .region((Relation) expr("x^2 + y^2 < 1"), new Window(-2, 2, -2, 2), Cancellation.NEVER);
    assertThat(rings).hasSize(1);
    assertThat(area(rings)).isCloseTo(Math.PI, within(0.03 * Math.PI));
  }

  @Test
  void aRegionReachingTheWindowsEdgeIsClosedAlongIt() {
    // y > x over [-2, 2]² is sampled over the window widened by half: [-4, 4]², area 32
    List<Polyline> rings =
        sampler(Environment.EMPTY)
            .region((Relation) expr("y > x"), new Window(-2, 2, -2, 2), Cancellation.NEVER);
    assertThat(rings).hasSize(1);
    assertThat(area(rings)).isCloseTo(32, within(1e-9));
  }

  @Test
  void aRegionWithAHoleIsTwoRingsOfOppositeTurn() {
    List<Polyline> rings =
        sampler(Environment.EMPTY)
            .region(
                (Relation) expr("x^2 + y^2 >= 1"), new Window(-2, 2, -2, 2), Cancellation.NEVER);
    assertThat(rings).hasSize(2);
    assertThat(area(rings)).isCloseTo(64 - Math.PI, within(0.03 * Math.PI));
  }

  /** The signed area of rings, inside on the left. */
  static double area(List<Polyline> rings) {
    double sum = 0;
    for (Polyline r : rings) {
      assertThat(r.x(r.size() - 1)).isEqualTo(r.x(0));
      assertThat(r.y(r.size() - 1)).isEqualTo(r.y(0));
      for (int k = 1; k < r.size(); k++) {
        sum += r.x(k - 1) * r.y(k) - r.x(k) * r.y(k - 1);
      }
    }
    return sum / 2;
  }

  @Test
  void theSamplersVariablesAreThePlanes() {
    assertThat(List.of(ImplicitCurve.X, ImplicitCurve.Y)).containsExactly(X, Symbol.of("y"));
  }
}
