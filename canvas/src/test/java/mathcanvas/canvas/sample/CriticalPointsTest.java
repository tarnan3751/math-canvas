package mathcanvas.canvas.sample;

import static mathcanvas.canvas.sample.SamplersTest.expr;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Set;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.CriticalPoint;
import mathcanvas.canvas.model.Feature;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Recomputes;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Window;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Settings;
import org.junit.jupiter.api.Test;

/**
 * A graph's roots and extrema tracked as families through a slider's change, with the real sampler:
 * identity by id, undefined while a feature is gone, re-attached in order when it returns.
 */
class CriticalPointsTest {

  private static final Style S = Style.DEFAULT;
  private static final Window W = new Window(-3, 3, -3, 3);
  private static final ObjectId GRAPH = new ObjectId(1);

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  /** A graph reading {@code a} (#1), then critical points of it (#2 on) of the given features. */
  private static CanvasState scene(String body, Feature... features) {
    CanvasState s =
        CanvasState.EMPTY.add(
            i -> new FunctionGraph(i, S, expr(body), SamplersTest.X, FunctionGraph.ALL));
    int[] index = new int[Feature.values().length];
    for (Feature f : features) {
      int k = index[f.ordinal()]++;
      s = s.add(i -> CriticalPoint.of(i, S, GRAPH, f, k));
    }
    return s;
  }

  private static CanvasState at(CanvasState s, String a) {
    return s.recompute(
        Set.of(),
        Set.of("a"),
        new Samplers().over(SamplersTest.env("a", a), Settings.DEFAULT),
        W,
        Recomputes.standard(),
        Cancellation.NEVER);
  }

  private static CanvasState first(CanvasState s, String a) {
    return s.recomputeAll(
        new Samplers().over(SamplersTest.env("a", a), Settings.DEFAULT),
        W,
        Recomputes.standard(),
        Cancellation.NEVER);
  }

  private static double x(CanvasState s, long id) {
    return ((Geometry.Point) s.geometryOf(id(id))).x();
  }

  private static boolean undefined(CanvasState s, long id) {
    return s.geometryOf(id(id)) instanceof Geometry.Undefined;
  }

  @Test
  void rootsVanishingUnderASliderBecomeUndefinedAndReAttachInOrder() {
    CanvasState s = first(scene("x^3 - a x", Feature.ROOT, Feature.ROOT, Feature.ROOT), "1");
    assertThat(x(s, 2)).isCloseTo(-1, within(1e-10));
    assertThat(x(s, 3)).isCloseTo(0, within(1e-10));
    assertThat(x(s, 4)).isCloseTo(1, within(1e-10));

    for (String a : new String[] {"0.5", "0.1", "0", "-0.5", "-1"}) {
      s = at(s, a);
    }
    assertThat(undefined(s, 2)).isTrue();
    assertThat(x(s, 3)).isCloseTo(0, within(1e-10));
    assertThat(undefined(s, 4)).isTrue();
    assertThat(s.object(id(2)).map(o -> ((CriticalPoint) o).anchor().isPresent())).contains(true);

    for (String a : new String[] {"-0.5", "0", "0.1", "0.5", "1"}) {
      s = at(s, a);
    }
    assertThat(x(s, 2)).isCloseTo(-1, within(1e-10));
    assertThat(x(s, 3)).isCloseTo(0, within(1e-10));
    assertThat(x(s, 4)).isCloseTo(1, within(1e-10));
  }

  @Test
  void twoRootsMergingAtATangencyAndSeparatingKeepTheirOrder() {
    CanvasState s = first(scene("x^2 - a", Feature.ROOT, Feature.ROOT), "1");
    assertThat(x(s, 2)).isCloseTo(-1, within(1e-10));
    assertThat(x(s, 3)).isCloseTo(1, within(1e-10));

    s = at(s, "0.25");
    s = at(s, "0");
    // the touching root at 0 is one multiple root, shared
    assertThat(x(s, 2)).isCloseTo(0, within(1e-5));
    assertThat(x(s, 3)).isCloseTo(0, within(1e-5));
    s = at(s, "-0.5");
    assertThat(undefined(s, 2)).isTrue();
    assertThat(undefined(s, 3)).isTrue();

    s = at(s, "0.25");
    assertThat(x(s, 2)).isCloseTo(-0.5, within(1e-10));
    assertThat(x(s, 3)).isCloseTo(0.5, within(1e-10));
  }

  @Test
  void aMinimumAndAMaximumAnnihilatingAtAnInflectionReturnInPlace() {
    CanvasState s = first(scene("x^3 - a x", Feature.MAX, Feature.MIN), "3");
    assertThat(x(s, 2)).isCloseTo(-1, within(1e-10));
    assertThat(x(s, 3)).isCloseTo(1, within(1e-10));

    for (String a : new String[] {"1", "0.1", "0", "-1"}) {
      s = at(s, a);
    }
    assertThat(undefined(s, 2)).isTrue();
    assertThat(undefined(s, 3)).isTrue();

    for (String a : new String[] {"0", "0.1", "1", "3"}) {
      s = at(s, a);
    }
    assertThat(x(s, 2)).isCloseTo(-1, within(1e-10));
    assertThat(x(s, 3)).isCloseTo(1, within(1e-10));
  }

  @Test
  void theFamiliesOfOneGraphAreOnePerFeature() {
    CanvasState s =
        first(scene("x^3 - a x", Feature.ROOT, Feature.MIN, Feature.MAX, Feature.ROOT), "3");
    // the same index 0 in three families: three different points
    assertThat(x(s, 2)).isCloseTo(-Math.sqrt(3), within(1e-10));
    assertThat(x(s, 3)).isCloseTo(1, within(1e-10));
    assertThat(x(s, 4)).isCloseTo(-1, within(1e-10));
    assertThat(x(s, 5)).isCloseTo(0, within(1e-10));
    assertThat(((Geometry.Point) s.geometryOf(id(3))).y()).isCloseTo(-2, within(1e-9));
  }

  @Test
  void aCriticalPointNeedsAFunctionGraph() {
    CanvasState s = CanvasState.EMPTY.add(i -> new mathcanvas.canvas.model.FreePoint(i, S, 0, 0));
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> s.add(i -> CriticalPoint.of(i, S, id(1), Feature.ROOT, 0)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** The state resampled over another window, as a pan or a zoom does. */
  private static CanvasState over(CanvasState s, Window w) {
    return s.resample(
        new Samplers().over(SamplersTest.env("a", "0"), Settings.DEFAULT),
        w,
        Recomputes.standard(),
        Cancellation.NEVER);
  }

  private static double anchorX(CanvasState s, long id) {
    return ((CriticalPoint) s.object(id(id)).orElseThrow()).anchor().orElseThrow().x();
  }

  @Test
  void aPanLeavesAPlaceThatLeftAndKeepsTheOthersInPlace() {
    // sin x sampled from -6 to 6 (the window ±3 widened by half): roots at -π, 0 and π
    CanvasState s = first(scene("sin(x)", Feature.ROOT, Feature.ROOT, Feature.ROOT), "0");
    assertThat(x(s, 2)).isCloseTo(-Math.PI, within(1e-10));
    assertThat(x(s, 3)).isCloseTo(0, within(1e-10));
    assertThat(x(s, 4)).isCloseTo(Math.PI, within(1e-10));

    // panned right by 3: sampled from -3 to 9, roots at 0, π and 2π
    s = over(s, new Window(0, 6, -3, 3));

    assertThat(undefined(s, 2)).isTrue();
    assertThat(anchorX(s, 2)).isCloseTo(-Math.PI, within(1e-10));
    assertThat(x(s, 3)).isCloseTo(0, within(1e-10));
    assertThat(x(s, 4)).isCloseTo(Math.PI, within(1e-10));

    // panned back: the member returns to its place
    s = over(s, W);
    assertThat(x(s, 2)).isCloseTo(-Math.PI, within(1e-10));
  }

  @Test
  void aTranslatingSliderMovesEachMemberByItsStep() {
    CanvasState s = first(scene("sin(x - a)", Feature.ROOT, Feature.ROOT, Feature.ROOT), "0");
    double[] was = {x(s, 2), x(s, 3), x(s, 4)};
    for (int step = 1; step <= 8; step++) {
      s = at(s, String.valueOf(step * 0.5));
      for (int m = 0; m < 3; m++) {
        if (s.geometryOf(id(2 + m)) instanceof Geometry.Point p) {
          assertThat(p.x() - was[m])
              .as("member %d at step %d", 2 + m, step)
              .isCloseTo(0.5, within(1e-9));
          was[m] = p.x();
        }
      }
    }
    // the member at π + a has left the range (6) on the way; the other two moved all the way
    assertThat(undefined(s, 4)).isTrue();
    assertThat(x(s, 2)).isCloseTo(-Math.PI + 4, within(1e-10));
    assertThat(x(s, 3)).isCloseTo(4, within(1e-10));
  }

  @Test
  void aZoomOutKeepsEveryMemberAndTheNewPlacesAreNew() {
    CanvasState s = first(scene("sin(x)", Feature.ROOT, Feature.ROOT, Feature.ROOT), "0");
    // zoomed out by 4: sampled from -24 to 24, with roots well inside it beside the members'
    s = over(s, new Window(-12, 12, -12, 12));
    assertThat(x(s, 2)).isCloseTo(-Math.PI, within(1e-10));
    assertThat(x(s, 3)).isCloseTo(0, within(1e-10));
    assertThat(x(s, 4)).isCloseTo(Math.PI, within(1e-10));
  }

  @Test
  void aZoomInLeavesAMemberPastTheRangeAndItReturnsOnZoomingOut() {
    CanvasState s = first(scene("sin(x)", Feature.ROOT, Feature.ROOT, Feature.ROOT), "0");
    // zoomed in about 1: sampled from -0.5 to 2.5, holding only the root at 0
    s = over(s, new Window(0.5, 1.5, 0.5, 1.5));
    assertThat(undefined(s, 2)).isTrue();
    assertThat(x(s, 3)).isCloseTo(0, within(1e-10));
    assertThat(undefined(s, 4)).isTrue();
    assertThat(anchorX(s, 4)).isCloseTo(Math.PI, within(1e-10));

    s = over(s, W);
    assertThat(x(s, 2)).isCloseTo(-Math.PI, within(1e-10));
    assertThat(x(s, 3)).isCloseTo(0, within(1e-10));
    assertThat(x(s, 4)).isCloseTo(Math.PI, within(1e-10));
  }

  @Test
  void aStaleAnchorNeverTakesAPlaceAnotherMemberTracked() {
    // sin(x − a), a up by 0.5 to 6 and back: at a = 3 the member at π + a goes undefined as its
    // root
    // leaves the range, and at a = 5.5 its stale anchor (5.64) is nearer the root the middle member
    // tracked (5.5, from 5.0) than that member is; the tracked member keeps it
    CanvasState s = first(scene("sin(x - a)", Feature.ROOT, Feature.ROOT, Feature.ROOT), "0");
    double[] start = {x(s, 2), x(s, 3), x(s, 4)};
    double[] was = start.clone();
    boolean[] defined = {true, true, true};
    java.util.List<Double> as = new java.util.ArrayList<>();
    for (int k = 1; k <= 12; k++) {
      as.add(k * 0.5);
    }
    for (int k = 11; k >= 0; k--) {
      as.add(k * 0.5);
    }
    double a = 0;
    for (double next : as) {
      double step = next - a;
      a = next;
      s = at(s, String.valueOf(a));
      for (int m = 0; m < 3; m++) {
        // a member that was defined keeps its place while the place stays inside the range ±6
        boolean stays = defined[m] && Math.abs(was[m] + step) < 5.9;
        if (s.geometryOf(id(2 + m)) instanceof Geometry.Point p) {
          if (defined[m]) {
            assertThat(p.x() - was[m])
                .as("member %d at a=%s", 2 + m, a)
                .isCloseTo(step, within(1e-9));
          }
          was[m] = p.x();
          defined[m] = true;
        } else {
          assertThat(stays).as("member %d lost at a=%s", 2 + m, a).isFalse();
          defined[m] = false;
        }
      }
    }
    for (int m = 0; m < 3; m++) {
      assertThat(x(s, 2 + m)).isCloseTo(start[m], within(1e-10));
    }
  }

  @Test
  void oneMaximumInViewDoesNotJumpAPeriod() {
    // sin x: the maxima at -3π/2 and π/2 lie in the range ±6; panned right by one a frame to
    // offset 8 (range 2 to 14, holding only 5π/2) and back, the member at π/2 goes undefined when
    // its maximum leaves instead of jumping 2π to the next one, and both come back to their own
    CanvasState s = first(scene("sin(x)", Feature.MAX, Feature.MAX), "0");
    double one = x(s, 2);
    double two = x(s, 3);
    assertThat(one).isCloseTo(-3 * Math.PI / 2, within(1e-9));
    assertThat(two).isCloseTo(Math.PI / 2, within(1e-9));
    java.util.List<Integer> offsets = new java.util.ArrayList<>();
    for (int k = 1; k <= 8; k++) {
      offsets.add(k);
    }
    for (int k = 7; k >= 0; k--) {
      offsets.add(k);
    }
    for (int offset : offsets) {
      s = over(s, new Window(-3 + offset, 3 + offset, -3, 3));
      for (long m = 2; m <= 3; m++) {
        if (s.geometryOf(id(m)) instanceof Geometry.Point p) {
          double own = m == 2 ? one : two;
          assertThat(p.x()).as("member %d at offset %d", m, offset).isCloseTo(own, within(1e-9));
        }
      }
    }
    assertThat(x(s, 2)).isCloseTo(one, within(1e-9));
    assertThat(x(s, 3)).isCloseTo(two, within(1e-9));
  }
}
