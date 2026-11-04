package mathcanvas.canvas.geom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;

/**
 * The viewport's maps over random windows from 1e-6 to 1e9 units wide, centred anywhere within 1e6
 * of their span, locked or not: screen and world round trip, a zoom keeps its fixed point, a locked
 * viewport stays square through pans and zooms, and pans compose.
 */
class ViewportProperties {

  @Provide
  Arbitrary<Viewport> viewports() {
    Arbitrary<Double> spanExponent = Arbitraries.doubles().between(-6, 9);
    Arbitrary<Double> aspect = Arbitraries.doubles().between(0.1, 10);
    Arbitrary<Double> offset = Arbitraries.doubles().between(-1e6, 1e6);
    Arbitrary<Double> size = Arbitraries.doubles().between(50, 4000);
    return Combinators.combine(
            spanExponent, aspect, offset, offset, size, size, Arbitraries.of(true, false))
        .as(
            (e, a, ox, oy, w, h, locked) -> {
              double span = Math.pow(10, e);
              double cx = ox * span;
              double cy = oy * span;
              return new Viewport(
                  cx - span / 2, cx + span / 2, cy - span * a / 2, cy + span * a / 2, w, h, locked);
            });
  }

  @Property
  void screenAndWorldRoundTrip(
      @ForAll("viewports") Viewport v,
      @ForAll @DoubleRange(min = 0, max = 1) double fx,
      @ForAll @DoubleRange(min = 0, max = 1) double fy) {
    double sx = fx * v.width();
    double sy = fy * v.height();
    assertThat(v.toScreenX(v.toWorldX(sx))).isCloseTo(sx, within(1e-6 * v.width()));
    assertThat(v.toScreenY(v.toWorldY(sy))).isCloseTo(sy, within(1e-6 * v.height()));
  }

  @Property
  void zoomKeepsItsFixedPoint(
      @ForAll("viewports") Viewport v,
      @ForAll @DoubleRange(min = 0.2, max = 5) double factor,
      @ForAll @DoubleRange(min = 0, max = 1) double fx,
      @ForAll @DoubleRange(min = 0, max = 1) double fy) {
    double sx = fx * v.width();
    double sy = fy * v.height();
    Viewport z = v.zoom(factor, sx, sy);
    assertThat(z.toScreenX(v.toWorldX(sx))).isCloseTo(sx, within(1e-6 * v.width()));
    assertThat(z.toScreenY(v.toWorldY(sy))).isCloseTo(sy, within(1e-6 * v.height()));
    assertThat(z.aspectLocked()).isEqualTo(v.aspectLocked());
  }

  @Property
  void aLockedViewportStaysSquare(
      @ForAll("viewports") Viewport v,
      @ForAll @DoubleRange(min = 0.2, max = 5) double factor,
      @ForAll @DoubleRange(min = -500, max = 500) double dx,
      @ForAll @DoubleRange(min = -500, max = 500) double dy) {
    Viewport locked = v.withAspectLocked(true);
    for (Viewport w :
        new Viewport[] {
          locked,
          locked.pan(dx, dy),
          locked.zoom(factor, locked.width() / 3, locked.height() / 4),
          locked.resize(locked.width() * 1.5, locked.height() / 2)
        }) {
      // Square to the precision the bounds allow: a few ulps of the largest bound per span.
      double largest =
          Math.max(
              Math.max(Math.abs(w.xMin()), Math.abs(w.xMax())),
              Math.max(Math.abs(w.yMin()), Math.abs(w.yMax())));
      double span = Math.min(w.xMax() - w.xMin(), w.yMax() - w.yMin());
      double tolerance = 1e-12 + 8 * Math.ulp(largest) / span;
      assertThat(w.pixelsPerUnitX() / w.pixelsPerUnitY()).isCloseTo(1, within(tolerance));
    }
  }

  /**
   * Two pans land where one pan by their sum does, to rounding. A locked viewport adds the
   * constructor's re-squaring: a pan moves the bounds of the other axis by a sum that rounds its
   * span by up to an ulp of its bounds, which the constructor answers by widening this axis about
   * its centre by the same relative amount, and the pans that follow move by the widened span. Two
   * pans and one round differently, so the windows differ by that relative amount (four ulps of the
   * other axis's largest bound over its span, for the three roundings and their sum) of this axis's
   * span and of the distance moved.
   */
  @Property
  void pansCompose(
      @ForAll("viewports") Viewport v,
      @ForAll @DoubleRange(min = -500, max = 500) double a,
      @ForAll @DoubleRange(min = -500, max = 500) double b) {
    Viewport twice = v.pan(a, b).pan(b, a);
    Viewport once = v.pan(a + b, a + b);
    double spanX = v.xMax() - v.xMin();
    double spanY = v.yMax() - v.yMin();
    double moved = 1 + (Math.abs(a) + Math.abs(b)) / Math.min(v.width(), v.height());
    double tolerance = 1e-9 * spanX + 1e-9 * Math.abs(v.xMin());
    double toleranceY = 1e-9 * spanY + 1e-9 * Math.abs(v.yMin());
    if (v.aspectLocked()) {
      tolerance +=
          4
              * Math.ulp(largest(v.yMin(), v.yMax(), once.yMin(), once.yMax()))
              / spanY
              * spanX
              * moved;
      toleranceY +=
          4
              * Math.ulp(largest(v.xMin(), v.xMax(), once.xMin(), once.xMax()))
              / spanX
              * spanY
              * moved;
    }
    assertThat(twice.xMin()).isCloseTo(once.xMin(), within(tolerance));
    assertThat(twice.yMax()).isCloseTo(once.yMax(), within(toleranceY));
  }

  /** The largest magnitude among the bounds. */
  private static double largest(double... bounds) {
    double out = 0;
    for (double b : bounds) {
      out = Math.max(out, Math.abs(b));
    }
    return out;
  }
}
