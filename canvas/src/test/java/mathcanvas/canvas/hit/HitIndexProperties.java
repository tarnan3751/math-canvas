package mathcanvas.canvas.hit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import mathcanvas.canvas.geom.Viewport;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;

/**
 * Hit testing over random scenes: up to twelve random walks of up to 600 points with gaps and
 * occasional far jumps (an asymptote's segments), up to twenty points, viewports of any aspect, and
 * queries both at random and next to an entry. The packed tree finds exactly what a scan of every
 * entry finds (the pruning), and both agree with an oracle written independently here, a plain
 * point-to-segment distance in pixel space with the same priority (the metric).
 */
class HitIndexProperties {

  /** A scene: the index, and the same entries as the oracle sees them. */
  private record Scene(
      HitIndex index, List<double[]> segments, List<double[]> points, double[][] anchors) {}

  private static Scene scene(Random random, int polylines, int points) {
    HitIndex.Builder b = HitIndex.builder();
    List<double[]> segments = new ArrayList<>();
    List<double[]> pointList = new ArrayList<>();
    List<double[]> anchors = new ArrayList<>();
    for (int k = 0; k < polylines; k++) {
      int n = 1 + random.nextInt(600);
      double[] xs = new double[n];
      double[] ys = new double[n];
      double x = random.nextGaussian() * 5;
      double y = random.nextGaussian() * 5;
      for (int i = 0; i < n; i++) {
        double r = random.nextDouble();
        if (r < 0.02) {
          xs[i] = Double.NaN;
          ys[i] = y;
          continue;
        }
        x += random.nextGaussian() * 0.2;
        y += r < 0.04 ? random.nextGaussian() * 1e9 : random.nextGaussian() * 0.2;
        xs[i] = x;
        ys[i] = y;
        anchors.add(new double[] {x, y});
      }
      b.polyline(k, xs, ys, n);
      for (int i = 0; i + 1 < n; i++) {
        if (!Double.isNaN(xs[i]) && !Double.isNaN(xs[i + 1])) {
          segments.add(new double[] {k, i, xs[i], ys[i], xs[i + 1], ys[i + 1]});
        }
      }
    }
    for (int k = 0; k < points; k++) {
      double x = random.nextGaussian() * 5;
      double y = random.nextGaussian() * 5;
      b.point(polylines + k, x, y);
      pointList.add(new double[] {polylines + k, x, y});
      anchors.add(new double[] {x, y});
    }
    return new Scene(b.build(), segments, pointList, anchors.toArray(double[][]::new));
  }

  /**
   * How far the two computations may differ for a segment: a few ulps of the pixel lengths
   * involved, which for an asymptote's segment 10¹⁰ pixels long is a few millionths of a pixel.
   */
  private static double rounding(double[] s, double qx, double qy, double sx, double sy) {
    double extent =
        Math.hypot((s[4] - s[2]) * sx, (s[5] - s[3]) * sy)
            + Math.hypot((qx - s[2]) * sx, (qy - s[3]) * sy);
    return 1e-9 + 1e-14 * extent;
  }

  /**
   * The distance in pixels from a query to a segment, by the textbook clamped projection, in pixel
   * coordinates centred on the query (a walk that jumped to 10⁹ stays there, and absolute pixel
   * coordinates of 10¹¹ would cost the oracle its precision).
   */
  private static double segmentDistance(double[] s, double qx, double qy, double sx, double sy) {
    double ax = (s[2] - qx) * sx;
    double ay = (s[3] - qy) * sy;
    double bx = (s[4] - qx) * sx;
    double by = (s[5] - qy) * sy;
    double abx = bx - ax;
    double aby = by - ay;
    double denominator = abx * abx + aby * aby;
    double t = denominator == 0 ? 0 : -(ax * abx + ay * aby) / denominator;
    t = Math.max(0, Math.min(1, t));
    double cx = ax + t * abx;
    double cy = ay + t * aby;
    return Math.sqrt(cx * cx + cy * cy);
  }

  @Property(tries = 300)
  void theTreeAgreesWithTheScanAndTheOracle(
      @ForAll long seed,
      @ForAll @IntRange(min = 0, max = 12) int polylines,
      @ForAll @IntRange(min = 0, max = 20) int points,
      @ForAll @DoubleRange(min = 0.05, max = 20) double aspect,
      @ForAll @DoubleRange(min = 2, max = 30) double tolerance) {
    Random random = new Random(seed);
    Scene scene = scene(random, polylines, points);
    Viewport v = new Viewport(-10, 10, -10 * aspect, 10 * aspect, 800, 600, false);
    double sx = v.pixelsPerUnitX();
    double sy = v.pixelsPerUnitY();
    for (int q = 0; q < 200; q++) {
      double qx;
      double qy;
      if (scene.anchors().length > 0 && q % 2 == 0) {
        double[] a = scene.anchors()[random.nextInt(scene.anchors().length)];
        qx = a[0] + random.nextGaussian() * tolerance / sx;
        qy = a[1] + random.nextGaussian() * tolerance / sy;
      } else {
        qx = random.nextGaussian() * 8;
        qy = random.nextGaussian() * 8 * aspect;
      }
      Optional<Hit> hit = scene.index().nearest(qx, qy, v, tolerance);
      assertThat(hit).isEqualTo(scene.index().nearestByScan(qx, qy, v, tolerance));

      // The oracle: the nearest point within the tolerance, else the nearest segment.
      double bestPoint = Double.POSITIVE_INFINITY;
      for (double[] p : scene.points()) {
        bestPoint = Math.min(bestPoint, Math.hypot((qx - p[1]) * sx, (qy - p[2]) * sy));
      }
      double bestSegment = Double.POSITIVE_INFINITY;
      double bestRounding = 0;
      for (double[] s : scene.segments()) {
        double d = segmentDistance(s, qx, qy, sx, sy);
        if (d < bestSegment) {
          bestSegment = d;
          bestRounding = rounding(s, qx, qy, sx, sy);
        }
      }
      double slack = 1e-9 * Math.max(1, tolerance);
      if (bestPoint <= tolerance - slack) {
        assertThat(hit).isPresent();
        assertThat(hit.get().kind()).isEqualTo(Hit.Kind.POINT);
        assertThat(hit.get().distance()).isCloseTo(bestPoint, within(slack));
      } else if (bestPoint > tolerance + slack && bestSegment <= tolerance - bestRounding) {
        assertThat(hit).isPresent();
        Hit h = hit.get();
        assertThat(h.kind()).isEqualTo(Hit.Kind.POLYLINE);
        // The reported segment is at that distance, and the reported point lies on it at t.
        double[] s =
            scene.segments().stream()
                .filter(e -> e[0] == h.item() && e[1] == h.segment())
                .findFirst()
                .orElseThrow();
        double hitRounding = rounding(s, qx, qy, sx, sy);
        assertThat(segmentDistance(s, qx, qy, sx, sy)).isCloseTo(h.distance(), within(hitRounding));
        assertThat(h.distance()).isCloseTo(bestSegment, within(hitRounding + bestRounding));
        assertThat(h.x())
            .isCloseTo(s[2] + h.t() * (s[4] - s[2]), within(1e-9 + 1e-12 * Math.abs(s[4] - s[2])));
        assertThat(h.y())
            .isCloseTo(s[3] + h.t() * (s[5] - s[3]), within(1e-9 + 1e-12 * Math.abs(s[5] - s[3])));
      } else if (bestPoint > tolerance + slack && bestSegment > tolerance + bestRounding) {
        assertThat(hit).isEmpty();
      }
    }
  }
}
