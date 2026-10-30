package mathcanvas.canvas.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Symbol;
import org.junit.jupiter.api.Test;

/**
 * The degenerate cases of path parameters and intersection continuity (PLAN.md §7, Phase 3: "Points
 * on a curve stay on it under drag. Intersection identity survives dragging through tangency").
 * Each case moves a parent frame by frame and asserts, by object id, that each tracked point is
 * where its own branch is (identity), and that it moves between frames no further than that branch
 * does (continuity).
 */
class DegenerateCasesTest {

  private static final Style S = Style.DEFAULT;
  private static final Window W = new Window(-3, 3, -3, 3);
  private static final Symbol X = Symbol.of("x");
  private static final Symbol T = Symbol.of("t");

  /**
   * How near a point must be to its branch: a double root is located only to about the square root
   * of the double precision (a tangency's position is found to about 1e-7 here), so the tolerance
   * is above that; distinct roots in these cases are at least a step of the sweep apart.
   */
  private static final double EPS = 1e-6;

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  /** The values from {@code from} to {@code to} in steps of 1/20, exact at every twentieth. */
  private static List<Double> sweep(int from, int to) {
    List<Double> out = new ArrayList<>();
    for (int k = from; from <= to ? k <= to : k >= to; k += from <= to ? 1 : -1) {
      out.add(k / 20.0);
    }
    return out;
  }

  private static List<Double> concat(List<Double> a, List<Double> b) {
    List<Double> out = new ArrayList<>(a);
    out.addAll(b.subList(1, b.size()));
    return out;
  }

  private static Geometry.Point at(CanvasState s, long id) {
    return s.geometryOf(id(id)) instanceof Geometry.Point p ? p : null;
  }

  /** Asserts a point is where expected (or undefined when expected is null). */
  private static void expect(CanvasState s, long id, double[] expected, String frame) {
    Geometry.Point p = at(s, id);
    if (expected == null) {
      assertThat(p).as("%s: #%d undefined", frame, id).isNull();
    } else {
      assertThat(p).as("%s: #%d defined", frame, id).isNotNull();
      assertThat(p.x()).as("%s: #%d x", frame, id).isCloseTo(expected[0], within(EPS));
      assertThat(p.y()).as("%s: #%d y", frame, id).isCloseTo(expected[1], within(EPS));
    }
  }

  /** Asserts the jump between frames is within the expected branch's own movement. */
  private static void continuous(
      CanvasState before, CanvasState after, long id, double[] e0, double[] e1, String frame) {
    Geometry.Point p = at(before, id);
    Geometry.Point q = at(after, id);
    if (p == null || q == null || e0 == null || e1 == null) {
      return;
    }
    double jump = Math.hypot(q.x() - p.x(), q.y() - p.y());
    double branch = Math.hypot(e1[0] - e0[0], e1[1] - e0[1]);
    assertThat(jump).as("%s: #%d jump", frame, id).isLessThanOrEqualTo(branch + 2 * EPS);
  }

  private static CanvasState moveLine(CanvasState s, double y, StubSampler sampler) {
    return s.replace(new FreePoint(id(1), S, -1, y))
        .replace(new FreePoint(id(2), S, 1, y))
        .recompute(
            Set.of(id(1), id(2)), Set.of(), sampler, W, Recomputes.standard(), Cancellation.NEVER);
  }

  /** A horizontal line through points 1 and 2 (object 3), a curve 4, intersections 5 and 6. */
  private static CanvasState lineAnd(CanvasObject curve, double y, StubSampler sampler) {
    return CanvasState.EMPTY
        .add(i -> new FreePoint(i, S, -1, y))
        .add(i -> new FreePoint(i, S, 1, y))
        .add(i -> new Line(i, S, new Line.Through(id(1), id(2))))
        .add(i -> curve)
        .add(i -> Intersection.of(i, S, id(4), id(3), 0))
        .add(i -> Intersection.of(i, S, id(4), id(3), 1))
        .recomputeAll(sampler, W, Recomputes.standard(), Cancellation.NEVER);
  }

  @Test
  void aPointOnASineStaysOnItAsASliderSweepsTheAmplitudeThroughZero() {
    StubSampler sampler = new StubSampler(Map.of("a", 1.0));
    Slider slider =
        new Slider(
            id(1),
            S,
            "a",
            new BigDecimal("-2"),
            new BigDecimal("2"),
            new BigDecimal("0.05"),
            BigDecimal.ONE);
    CanvasState s =
        CanvasState.EMPTY
            .add(i -> slider)
            .add(i -> new FunctionGraph(i, S, Parse.expr("a sin(x)"), X, FunctionGraph.ALL))
            .add(i -> new PointOnPath(i, S, id(2), ParamKind.X, 1, Double.NaN, Double.NaN))
            .recomputeAll(sampler, W, Recomputes.standard(), Cancellation.NEVER);
    double[] last = {1, Math.sin(1)};
    for (double a : sweep(20, -40)) {
      StubSampler now = sampler.with("a", a);
      CanvasState next =
          s.replace(slider.withValue(BigDecimal.valueOf(a)))
              .recompute(
                  Set.of(id(1)), Set.of(), now, W, Recomputes.standard(), Cancellation.NEVER);
      double[] e = {1, a * Math.sin(1)};
      expect(next, 3, e, "a=" + a);
      assertThat(at(next, 3).y()).isCloseTo(a * Math.sin(1), within(1e-12));
      continuous(s, next, 3, last, e, "a=" + a);
      assertThat(((PointOnPath) next.object(id(3)).orElseThrow()).parameter()).isEqualTo(1);
      s = next;
      last = e;
    }
  }

  @Test
  void roundsMergeAtTangencyAndSeparateKeepingTheirSides() {
    // y = x² and y = s²: roots ±|s|, a double root at s = 0, and the roots separate again
    StubSampler sampler = new StubSampler(Map.of());
    CanvasState s =
        lineAnd(new FunctionGraph(id(4), S, Parse.expr("x^2"), X, FunctionGraph.ALL), 1, sampler);
    double[][] last = {{-1, 1}, {1, 1}};
    for (double v : sweep(20, -20)) {
      CanvasState next = moveLine(s, v * v, sampler);
      double[][] e = {{-Math.abs(v), v * v}, {Math.abs(v), v * v}};
      for (int k = 0; k < 2; k++) {
        expect(next, 5 + k, e[k], "s=" + v);
        continuous(s, next, 5 + k, last[k], e[k], "s=" + v);
      }
      s = next;
      last = e;
    }
  }

  @Test
  void intersectionsVanishPastTangencyAndReattachWhereTheyVanished() {
    // y = x² and y = c: two roots, a tangency at c = 0, none below, and back
    StubSampler sampler = new StubSampler(Map.of());
    CanvasState s =
        lineAnd(new FunctionGraph(id(4), S, Parse.expr("x^2"), X, FunctionGraph.ALL), 1, sampler);
    double[][] last = {{-1, 1}, {1, 1}};
    for (double c : concat(sweep(20, -20), sweep(-20, 20))) {
      CanvasState next = moveLine(s, c, sampler);
      double r = c >= 0 ? Math.sqrt(c) : Double.NaN;
      double[][] e = c >= 0 ? new double[][] {{-r, c}, {r, c}} : new double[][] {null, null};
      for (int k = 0; k < 2; k++) {
        expect(next, 5 + k, e[k], "c=" + c);
        continuous(s, next, 5 + k, last[k], e[k], "c=" + c);
      }
      s = next;
      last = e;
    }
  }

  @Test
  void framesThatSkipTheTangencyReattachToTheNearest() {
    StubSampler sampler = new StubSampler(Map.of());
    CanvasState s =
        lineAnd(new FunctionGraph(id(4), S, Parse.expr("x^2"), X, FunctionGraph.ALL), 1, sampler);
    for (double c : List.of(0.5, 0.2, -0.1, -0.4, -0.1, 0.2, 0.5)) {
      s = moveLine(s, c, sampler);
      double r = Math.sqrt(c);
      expect(s, 5, c > 0 ? new double[] {-r, c} : null, "c=" + c);
      expect(s, 6, c > 0 ? new double[] {r, c} : null, "c=" + c);
    }
  }

  @Test
  void aLineThroughACircleGoesFromTwoIntersectionsToTangencyToNoneAndBack() {
    StubSampler sampler = new StubSampler(Map.of());
    ParametricCurve circle =
        new ParametricCurve(
            id(4), S, Parse.expr("cos(t)"), Parse.expr("sin(t)"), T, Parse.interval("0", "2 pi"));
    CanvasState s = lineAnd(circle, 0, sampler);
    // ordered by t on the circle: index 0 is the right-hand root (t = asin c), 1 the left
    double[][] last = {{1, 0}, {-1, 0}};
    for (double c : concat(sweep(0, 30), sweep(30, 0))) {
      CanvasState next = moveLine(s, c, sampler);
      double r = c <= 1 ? Math.sqrt(1 - c * c) : Double.NaN;
      double[][] e = c <= 1 ? new double[][] {{r, c}, {-r, c}} : new double[][] {null, null};
      for (int k = 0; k < 2; k++) {
        expect(next, 5 + k, e[k], "c=" + c);
        continuous(s, next, 5 + k, last[k], e[k], "c=" + c);
      }
      s = next;
      last = e;
    }
  }

  @Test
  void intersectionsKeepTheirIdentityWhenTheirOrderSwaps() {
    // an ellipse rotated through a full turn against the x-axis: its two crossings are (±r, 0);
    // their order by the ellipse's parameter swaps, and each keeps its own side
    StubSampler sampler = new StubSampler(Map.of("w", 0.0));
    Slider w =
        new Slider(
            id(1),
            S,
            "w",
            BigDecimal.ZERO,
            BigDecimal.valueOf(7),
            new BigDecimal("0.01"),
            BigDecimal.ZERO);
    CanvasState s =
        CanvasState.EMPTY
            .add(i -> w)
            .add(
                i ->
                    new ParametricCurve(
                        i,
                        S,
                        Parse.expr("2 cos(t) cos(w) - sin(t) sin(w)"),
                        Parse.expr("2 cos(t) sin(w) + sin(t) cos(w)"),
                        T,
                        Parse.interval("0", "2 pi")))
            .add(i -> new FreePoint(i, S, 0, 0))
            .add(i -> new FreePoint(i, S, 1, 0))
            .add(i -> new Line(i, S, new Line.Through(id(3), id(4))))
            .add(i -> Intersection.of(i, S, id(2), id(5), 0))
            .add(i -> Intersection.of(i, S, id(2), id(5), 1))
            .recomputeAll(sampler, W, Recomputes.standard(), Cancellation.NEVER);
    expect(s, 6, new double[] {2, 0}, "w=0");
    expect(s, 7, new double[] {-2, 0}, "w=0");
    boolean swapped = false;
    double[][] last = {{2, 0}, {-2, 0}};
    for (int k = 1; k <= 72; k++) {
      double a = 2 * Math.PI * k / 72;
      CanvasState next =
          s.replace(w.withValue(new BigDecimal(a).round(new java.math.MathContext(17))))
              .recompute(
                  Set.of(id(1)),
                  Set.of(),
                  sampler.with("w", a),
                  W,
                  Recomputes.standard(),
                  Cancellation.NEVER);
      double r = 2 / Math.sqrt(Math.cos(a) * Math.cos(a) + 4 * Math.sin(a) * Math.sin(a));
      double[][] e = {{r, 0}, {-r, 0}};
      for (int j = 0; j < 2; j++) {
        expect(next, 6 + j, e[j], "w=" + a);
        continuous(s, next, 6 + j, last[j], e[j], "w=" + a);
      }
      swapped |= ((Intersection) next.object(id(6)).orElseThrow()).index() == 1;
      s = next;
      last = e;
    }
    assertThat(swapped).as("the order swapped at some frame").isTrue();
  }

  @Test
  void aPointOnACurveWhoseDomainShrinksPastItIsUndefinedThenBack() {
    StubSampler sampler = new StubSampler(Map.of("b", 1.0));
    CanvasState s =
        CanvasState.EMPTY
            .add(
                i ->
                    new ParametricCurve(
                        i, S, Parse.expr("t"), Parse.expr("t^2"), T, Parse.interval("0", "b")))
            .add(i -> new PointOnPath(i, S, id(1), ParamKind.T, 0.8, Double.NaN, Double.NaN))
            .add(i -> new FreePoint(i, S, 2, 0))
            .add(i -> new Segment(i, S, id(2), id(3)))
            .recomputeAll(sampler, W, Recomputes.standard(), Cancellation.NEVER);
    for (double b : concat(sweep(20, 10), sweep(10, 20))) {
      s =
          s.recompute(
              Set.of(),
              Set.of("b"),
              sampler.with("b", b),
              W,
              Recomputes.standard(),
              Cancellation.NEVER);
      boolean on = b >= 0.8;
      expect(s, 2, on ? new double[] {0.8, 0.64} : null, "b=" + b);
      assertThat(s.geometryOf(id(4)) instanceof Geometry.Curve)
          .as("segment at b=%s", b)
          .isEqualTo(on);
      assertThat(((PointOnPath) s.object(id(2)).orElseThrow()).parameter()).isEqualTo(0.8);
    }
  }

  /** A line through points 1 and 2 (object 3), a curve 4, then {@code count} intersections. */
  private static CanvasState family(
      CanvasObject curve, double[] a, double[] b, int count, StubSampler sampler) {
    CanvasState s =
        CanvasState.EMPTY
            .add(i -> new FreePoint(i, S, a[0], a[1]))
            .add(i -> new FreePoint(i, S, b[0], b[1]))
            .add(i -> new Line(i, S, new Line.Through(id(1), id(2))))
            .add(i -> curve);
    for (int k = 0; k < count; k++) {
      int index = k;
      s = s.add(i -> Intersection.of(i, S, id(4), id(3), index));
    }
    return s.recomputeAll(sampler, W, Recomputes.standard(), Cancellation.NEVER);
  }

  private static CanvasState moveTo(CanvasState s, double[] a, double[] b, StubSampler sampler) {
    return s.replace(new FreePoint(id(1), S, a[0], a[1]))
        .replace(new FreePoint(id(2), S, b[0], b[1]))
        .recompute(
            Set.of(id(1), id(2)), Set.of(), sampler, W, Recomputes.standard(), Cancellation.NEVER);
  }

  @Test
  void anAsymmetricTangencySeparatesEachToItsOwnSide() {
    // y = x² and y = x + c: roots (1 ∓ √(1 + 4c))/2, a tangency at c = −1/4 whose two roots
    // leave it at unequal distances from the anchor
    StubSampler sampler = new StubSampler(Map.of());
    CanvasObject parabola = new FunctionGraph(id(4), S, Parse.expr("x^2"), X, FunctionGraph.ALL);
    CanvasState s = family(parabola, new double[] {0, 1}, new double[] {1, 2}, 2, sampler);
    for (double c : List.of(1.0, 0.5, 0.0, -0.2, -0.25, -0.2, 0.0, 0.5)) {
      s = moveTo(s, new double[] {0, c}, new double[] {1, 1 + c}, sampler);
      double r = Math.sqrt(Math.max(0, 1 + 4 * c));
      double lo = (1 - r) / 2;
      double hi = (1 + r) / 2;
      expect(s, 5, new double[] {lo, lo * lo}, "c=" + c);
      expect(s, 6, new double[] {hi, hi * hi}, "c=" + c);
    }
  }

  @Test
  void aRotatingLineVanishesAtOneTangencyAndReturnsAtAnother() {
    // y = m x − 1 about (0, −1) against y = x²: tangent at m = 2 (x = 1), no intersection for
    // |m| < 2, tangent again at m = −2 (x = −1); both return, distinct, by the side they came from
    StubSampler sampler = new StubSampler(Map.of());
    CanvasObject parabola = new FunctionGraph(id(4), S, Parse.expr("x^2"), X, FunctionGraph.ALL);
    CanvasState s = family(parabola, new double[] {0, -1}, new double[] {1, 2}, 2, sampler);
    for (int k = 6; k >= -5; k--) {
      double m = k / 2.0;
      s = moveTo(s, new double[] {0, -1}, new double[] {1, m - 1}, sampler);
      double disc = m * m - 4;
      if (disc < 0) {
        expect(s, 5, null, "m=" + m);
        expect(s, 6, null, "m=" + m);
        continue;
      }
      double r = Math.sqrt(disc);
      double lo = (m - r) / 2;
      double hi = (m + r) / 2;
      expect(s, 5, new double[] {lo, lo * lo}, "m=" + m);
      expect(s, 6, new double[] {hi, hi * hi}, "m=" + m);
    }
    expect(s, 5, new double[] {-2, 4}, "m=-2.5");
    expect(s, 6, new double[] {-0.5, 0.25}, "m=-2.5");
  }

  @Test
  void aCircleKeepsEachIntersectionOnItsSideAcrossTheSeam() {
    // the unit circle and y = c through c = 0.5, −0.5, −1 (tangent), −1.5 (none), 0: the root
    // that started on the right comes back on the right, though it now sorts first at t = 0
    StubSampler sampler = new StubSampler(Map.of());
    ParametricCurve circle =
        new ParametricCurve(
            id(4), S, Parse.expr("cos(t)"), Parse.expr("sin(t)"), T, Parse.interval("0", "2 pi"));
    CanvasState s = family(circle, new double[] {-1, 0.5}, new double[] {1, 0.5}, 2, sampler);
    for (double c : List.of(0.5, -0.5, -1.0, -1.5, 0.0)) {
      s = moveTo(s, new double[] {-1, c}, new double[] {1, c}, sampler);
      double r = Math.abs(c) <= 1 ? Math.sqrt(1 - c * c) : Double.NaN;
      expect(s, 5, Double.isNaN(r) ? null : new double[] {r, c}, "c=" + c);
      expect(s, 6, Double.isNaN(r) ? null : new double[] {-r, c}, "c=" + c);
    }
  }

  @Test
  void twoRootsMergingAtTheSeamLeaveItOnTheirOwnSides() {
    // the unit circle and x = c: roots at t = ±acos c, which meet at t = 0 ≡ 2π when c = 1
    StubSampler sampler = new StubSampler(Map.of());
    ParametricCurve circle =
        new ParametricCurve(
            id(4), S, Parse.expr("cos(t)"), Parse.expr("sin(t)"), T, Parse.interval("0", "2 pi"));
    CanvasState s = family(circle, new double[] {0.5, -1}, new double[] {0.5, 1}, 2, sampler);
    for (double c : List.of(0.5, 0.8, 1.0, 0.8, 0.5, 1.0, 1.2, 0.5)) {
      s = moveTo(s, new double[] {c, -1}, new double[] {c, 1}, sampler);
      double r = c <= 1 ? Math.sqrt(1 - c * c) : Double.NaN;
      expect(s, 5, Double.isNaN(r) ? null : new double[] {c, r}, "c=" + c);
      expect(s, 6, Double.isNaN(r) ? null : new double[] {c, -r}, "c=" + c);
    }
  }

  @Test
  void aFamilyOfThreeKeepsItsMembersApart() {
    // y = x³ − x and y = c: three roots for |c| < 2/(3√3); the two left ones vanish above it
    StubSampler sampler = new StubSampler(Map.of());
    CanvasObject cubic = new FunctionGraph(id(4), S, Parse.expr("x^3 - x"), X, FunctionGraph.ALL);
    CanvasState s = family(cubic, new double[] {-1, 0}, new double[] {1, 0}, 3, sampler);
    double[] cs = {0, -0.1, -0.2, -0.3, -0.38, -0.5, -0.38, -0.3, -0.1, 0, 0.2};
    for (double c : cs) {
      s = moveTo(s, new double[] {-1, c}, new double[] {1, c}, sampler);
      double[] roots = cubicRoots(c);
      if (roots.length == 3) {
        for (int k = 0; k < 3; k++) {
          expect(s, 5 + k, new double[] {roots[k], c}, "c=" + c);
        }
      } else {
        expect(s, 5, new double[] {roots[0], c}, "c=" + c);
        expect(s, 6, null, "c=" + c);
        expect(s, 7, null, "c=" + c);
      }
    }
  }

  /** The real roots of x³ − x − c, ascending. */
  private static double[] cubicRoots(double c) {
    double limit = 2 / (3 * Math.sqrt(3));
    if (Math.abs(c) < limit) {
      double[] out = new double[3];
      double phi = Math.acos(c / limit);
      for (int k = 0; k < 3; k++) {
        out[k] = 2 / Math.sqrt(3) * Math.cos((phi - 2 * Math.PI * k) / 3);
      }
      java.util.Arrays.sort(out);
      return out;
    }
    double x = c > 0 ? 1.5 : -1.5;
    for (int k = 0; k < 60; k++) {
      x -= (x * x * x - x - c) / (3 * x * x - 1);
    }
    return new double[] {x};
  }

  /** Two graphs 1 and 2 and {@code count} intersections of them, 3 onwards. */
  private static CanvasState graphs(String f, String g, int count, StubSampler sampler) {
    CanvasState s =
        CanvasState.EMPTY
            .add(i -> new FunctionGraph(i, S, Parse.expr(f), X, FunctionGraph.ALL))
            .add(i -> new FunctionGraph(i, S, Parse.expr(g), X, FunctionGraph.ALL));
    for (int k = 0; k < count; k++) {
      int index = k;
      s = s.add(i -> Intersection.of(i, S, id(1), id(2), index));
    }
    return s.recomputeAll(sampler, W, Recomputes.standard(), Cancellation.NEVER);
  }

  /** The roots of (x − h)² = c + q x², ascending. */
  private static double[] shiftedRoots(double h, double c, double q) {
    double a = 1 - q;
    double b = -2 * h;
    double cc = h * h - c;
    double r = Math.sqrt(b * b - 4 * a * cc);
    double lo = (-b - r) / (2 * a);
    double hi = (-b + r) / (2 * a);
    return new double[] {Math.min(lo, hi), Math.max(lo, hi)};
  }

  private void dragShifted(String g, double c, double q, double[] hs) {
    StubSampler sampler = new StubSampler(Map.of("h", 0.0));
    CanvasState s = graphs("(x - h)^2", g, 2, sampler);
    for (double h : hs) {
      sampler = sampler.with("h", h);
      s = s.recompute(Set.of(), Set.of("h"), sampler, W, Recomputes.standard(), Cancellation.NEVER);
      double[] r = shiftedRoots(h, c, q);
      expect(s, 3, new double[] {r[0], (r[0] - h) * (r[0] - h)}, "h=" + h);
      expect(s, 4, new double[] {r[1], (r[1] - h) * (r[1] - h)}, "h=" + h);
    }
  }

  @Test
  void twoRootsThatMovePastBothAnchorsInOneFrameKeepTheirOrder() {
    // case A: (x − h)² against 0.01 + x²/10, h from 0 to 0.3 in one frame, and back
    dragShifted("0.01 + x^2/10", 0.01, 0.1, new double[] {0.3});
    dragShifted("0.01 + x^2/10", 0.01, 0.1, new double[] {-0.3});
    dragShifted("0.01 + x^2/10", 0.01, 0.1, new double[] {0.15, 0.3});
  }

  @Test
  void aDragNearATangencyKeepsTheOrderInEveryFrame() {
    // case B: against 0.0001 + x²/10, h stepping ±0.05 for ten frames
    double[] up = new double[10];
    double[] down = new double[10];
    for (int k = 1; k <= 10; k++) {
      up[k - 1] = k * 0.05;
      down[k - 1] = -k * 0.05;
    }
    dragShifted("0.0001 + x^2/10", 0.0001, 0.1, up);
    dragShifted("0.0001 + x^2/10", 0.0001, 0.1, down);
    // case C: against the horizontal 0.0001, where the two matchings cost the same
    dragShifted("0.0001", 0.0001, 0, up);
    dragShifted("0.0001", 0.0001, 0, down);
  }

  @Test
  void aClusterWithFewerCandidatesKeepsTheOneOnItsSide() {
    // a slope-1 segment from x = 0.3 to x = 2 against y = x²: through the tangency at x = 1/2
    // only the right-hand root lies on the segment, and the member that came from the right
    // keeps it, whichever was drawn first
    for (boolean swapped : new boolean[] {false, true}) {
      StubSampler sampler = new StubSampler(Map.of());
      CanvasState s =
          CanvasState.EMPTY
              .add(i -> new FreePoint(i, S, 0.3, 0.3 - 0.24))
              .add(i -> new FreePoint(i, S, 2, 2 - 0.24))
              .add(i -> new Segment(i, S, id(1), id(2)))
              .add(i -> new FunctionGraph(i, S, Parse.expr("x^2"), X, FunctionGraph.ALL))
              .add(i -> Intersection.of(i, S, id(3), id(4), swapped ? 1 : 0))
              .add(i -> Intersection.of(i, S, id(3), id(4), swapped ? 0 : 1))
              .recomputeAll(sampler, W, Recomputes.standard(), Cancellation.NEVER);
      long left = swapped ? 6 : 5;
      long right = swapped ? 5 : 6;
      for (double c : List.of(-0.24, -0.25, -0.2)) {
        s = moveTo(s, new double[] {0.3, 0.3 + c}, new double[] {2, 2 + c}, sampler);
        double r = Math.sqrt(1 + 4 * c);
        double lo = (1 - r) / 2;
        double hi = (1 + r) / 2;
        expect(s, right, new double[] {hi, hi * hi}, "c=" + c + " swapped=" + swapped);
        expect(
            s,
            left,
            lo >= 0.3 ? new double[] {lo, lo * lo} : null,
            "c=" + c + " swapped=" + swapped);
      }
    }
  }

  @Test
  void aFamilyOfThreeLeavesATangencyBySide() {
    // y = x³ − x and y = c through the tangency at c = 2/(3√3): the two left members share it,
    // vanish above it, and return each to its own side; at c = 0.5 only the right root is left
    StubSampler sampler = new StubSampler(Map.of());
    CanvasObject cubic = new FunctionGraph(id(4), S, Parse.expr("x^3 - x"), X, FunctionGraph.ALL);
    CanvasState s = family(cubic, new double[] {-1, 0}, new double[] {1, 0}, 3, sampler);
    double t = 2 / (3 * Math.sqrt(3));
    for (double c : new double[] {0, 0.2, t, 0.5, t, 0.2, 0}) {
      s = moveTo(s, new double[] {-1, c}, new double[] {1, c}, sampler);
      double[] roots = cubicRoots(c);
      if (c == t) {
        expect(s, 5, new double[] {-1 / Math.sqrt(3), c}, "c=" + c);
        expect(s, 6, new double[] {-1 / Math.sqrt(3), c}, "c=" + c);
        expect(s, 7, new double[] {2 / Math.sqrt(3), c}, "c=" + c);
      } else if (roots.length == 3) {
        for (int k = 0; k < 3; k++) {
          expect(s, 5 + k, new double[] {roots[k], c}, "c=" + c);
        }
      } else {
        expect(s, 5, null, "c=" + c);
        expect(s, 6, null, "c=" + c);
        expect(s, 7, new double[] {roots[0], c}, "c=" + c);
      }
    }
  }

  @Test
  void aTripleRootIsOneSharedTangency() {
    // y = x³ and y = c: one real root, a triple one at c = 0; a family of three keeps one member
    // on the root throughout and the others only share the triple root
    StubSampler sampler = new StubSampler(Map.of());
    CanvasObject cubic = new FunctionGraph(id(4), S, Parse.expr("x^3"), X, FunctionGraph.ALL);
    CanvasState s = family(cubic, new double[] {-1, -0.5}, new double[] {1, -0.5}, 3, sampler);
    expect(s, 5, new double[] {Math.cbrt(-0.5), -0.5}, "start");
    expect(s, 6, null, "start");
    expect(s, 7, null, "start");
    for (double c : new double[] {-0.2, 0, 0.2, 0.5}) {
      s = moveTo(s, new double[] {-1, c}, new double[] {1, c}, sampler);
      if (c == 0) {
        // a triple root is located only to about the cube root of the tolerance, and shared
        for (long m = 5; m <= 7; m++) {
          assertThat(at(s, m)).as("#%d at the triple root", m).isNotNull();
          assertThat(at(s, m).x()).isCloseTo(0, within(1e-3));
        }
      } else {
        expect(s, 5, new double[] {Math.cbrt(c), c}, "c=" + c);
        expect(s, 6, null, "c=" + c);
        expect(s, 7, null, "c=" + c);
      }
    }
  }

  @Test
  void aTripleRootReachedASecondTimeIsStillOne() {
    // x³ − a x against y = 0, three members: roots −√a, 0, √a, a triple root at a = 0, and one
    // root for a < 0, held by the middle member; each comes back to its own root
    StubSampler sampler = new StubSampler(Map.of("a", 1.0));
    CanvasState s = graphs("x^3 - a x", "0", 3, sampler);
    for (double a : new double[] {1, 0.25, 0.01, 0, 0.25, 1, 0, -1, 0, 1}) {
      sampler = sampler.with("a", a);
      s = s.recompute(Set.of(), Set.of("a"), sampler, W, Recomputes.standard(), Cancellation.NEVER);
      if (a > 0) {
        double r = Math.sqrt(a);
        expect(s, 3, new double[] {-r, 0}, "a=" + a);
        expect(s, 4, new double[] {0, 0}, "a=" + a);
        expect(s, 5, new double[] {r, 0}, "a=" + a);
      } else if (a == 0) {
        for (long m = 3; m <= 5; m++) {
          assertThat(at(s, m)).as("a=0 #%d", m).isNotNull();
          assertThat(at(s, m).x()).isCloseTo(0, within(1e-3));
        }
      } else {
        expect(s, 3, null, "a=" + a);
        expect(s, 4, new double[] {0, 0}, "a=" + a);
        expect(s, 5, null, "a=" + a);
      }
      Geometry.Curve f = (Geometry.Curve) s.geometryOf(id(1));
      Geometry.Curve g = (Geometry.Curve) s.geometryOf(id(2));
      int roots = a > 0 ? 3 : 1;
      assertThat(
              Intersections.candidates(f, g, List.of(), sampler, W.diagonal(), Cancellation.NEVER)
                  .orElseThrow())
          .as("a=%s", a)
          .hasSize(roots);
    }
  }

  @Test
  void aDoubleRootBesideASimpleOneStaysOneCandidate() {
    // x² (x − d) against y = 0: a double root at 0 beside a simple one at d; within a sampling
    // step they are found as one (a documented limit), and never as two phantoms
    StubSampler sampler = new StubSampler(Map.of("d", 0.5));
    CanvasState s = graphs("x^2 (x - d)", "0", 2, sampler);
    for (double d :
        new double[] {0.1, 0.01, 1e-3, 1e-4, 1e-5, 1e-4, 1e-3, 0.1, -0.1, -1e-4, 1e-4}) {
      sampler = sampler.with("d", d);
      s = s.recompute(Set.of(), Set.of("d"), sampler, W, Recomputes.standard(), Cancellation.NEVER);
      Geometry.Curve f = (Geometry.Curve) s.geometryOf(id(1));
      Geometry.Curve g = (Geometry.Curve) s.geometryOf(id(2));
      int expected = Math.abs(d) >= 0.01 ? 2 : 1;
      List<Anchor> anchors = new ArrayList<>();
      for (long m = 3; m <= 4; m++) {
        ((Intersection) s.object(id(m)).orElseThrow()).anchor().ifPresent(anchors::add);
      }
      assertThat(
              Intersections.candidates(f, g, anchors, sampler, W.diagonal(), Cancellation.NEVER)
                  .orElseThrow())
          .as("d=%s", d)
          .hasSize(expected);
    }
    sampler = sampler.with("d", 0.5);
    s = s.recompute(Set.of(), Set.of("d"), sampler, W, Recomputes.standard(), Cancellation.NEVER);
    expect(s, 3, new double[] {0, 0}, "d=0.5");
    expect(s, 4, new double[] {0.5, 0}, "d=0.5");
  }

  @Test
  void aRootBesideAMultipleOneIsFoundFromItsApproach() {
    // x³ − a x − b x² against y = 0: at a = 0 a double root at 0 beside b; at a = 0.01 a root
    // splits off at −0.019, closer than a sampling step, and the member that came from there finds
    // it again
    StubSampler sampler = new StubSampler(Map.of("a", 1.0, "b", 0.0));
    CanvasState s = graphs("x^3 - a x - b x^2", "0", 3, sampler);
    double[][] frames = {{0.01, 0}, {0, 0}, {0, 0.5}, {0.01, 0.5}};
    for (double[] fr : frames) {
      sampler = sampler.with("a", fr[0]).with("b", fr[1]);
      s =
          s.recompute(
              Set.of(), Set.of("a", "b"), sampler, W, Recomputes.standard(), Cancellation.NEVER);
    }
    double r = Math.sqrt(0.25 + 0.04);
    expect(s, 3, new double[] {(0.5 - r) / 2, 0}, "a=0.01 b=0.5");
    expect(s, 4, new double[] {0, 0}, "a=0.01 b=0.5");
    expect(s, 5, new double[] {(0.5 + r) / 2, 0}, "a=0.01 b=0.5");
  }
}
