package mathcanvas.canvas.hit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import mathcanvas.canvas.geom.Viewport;
import org.junit.jupiter.api.Test;

class HitIndexTest {

  // 10 pixels per unit on both axes.
  private static final Viewport SQUARE = new Viewport(-10, 10, -10, 10, 200, 200, true);

  @Test
  void projectsOntoTheNearestSegment() {
    HitIndex index =
        HitIndex.builder().polyline(7, new double[] {0, 2, 4}, new double[] {0, 0, 2}, 3).build();
    Hit hit = index.nearest(1.5, 0.3, SQUARE, 6).orElseThrow();
    assertThat(hit.item()).isEqualTo(7);
    assertThat(hit.kind()).isEqualTo(Hit.Kind.POLYLINE);
    assertThat(hit.segment()).isEqualTo(0);
    assertThat(hit.t()).isCloseTo(0.75, within(1e-12));
    assertThat(hit.x()).isCloseTo(1.5, within(1e-12));
    assertThat(hit.y()).isCloseTo(0, within(1e-12));
    assertThat(hit.distance()).isCloseTo(3, within(1e-9));
    assertThat(index.nearest(1.5, 0.7, SQUARE, 6)).isEmpty();
  }

  @Test
  void measuresInPixelsOnEachAxis() {
    // x: 10 px per unit; y: 100 px per unit.
    Viewport stretched = new Viewport(-10, 10, -1, 1, 200, 200, false);
    HitIndex index =
        HitIndex.builder()
            .polyline(0, new double[] {-5, 5}, new double[] {0, 0}, 2)
            .polyline(1, new double[] {0.4, 0.4}, new double[] {-1, 1}, 2)
            .build();
    // 0.05 units above the horizontal line is 5 px; 0.4 units left of the vertical one is 4 px.
    Hit hit = index.nearest(0, 0.05, stretched, 6).orElseThrow();
    assertThat(hit.item()).isEqualTo(1);
    assertThat(hit.distance()).isCloseTo(4, within(1e-9));
  }

  @Test
  void aPointWinsOverANearerCurve() {
    HitIndex index =
        HitIndex.builder()
            .polyline(0, new double[] {-5, 5}, new double[] {0, 0}, 2)
            .point(1, 0, 0.5)
            .build();
    Hit hit = index.nearest(0, 0.1, SQUARE, 6).orElseThrow();
    assertThat(hit.kind()).isEqualTo(Hit.Kind.POINT);
    assertThat(hit.item()).isEqualTo(1);
    assertThat(hit.segment()).isEqualTo(-1);
    assertThat(hit.distance()).isCloseTo(4, within(1e-9));
    assertThat(index.nearest(3, 0.1, SQUARE, 6).orElseThrow().kind()).isEqualTo(Hit.Kind.POLYLINE);
  }

  @Test
  void gapsAreNotSegments() {
    HitIndex index =
        HitIndex.builder()
            .polyline(0, new double[] {0, 1, 2, 3}, new double[] {0, Double.NaN, 0, 0}, 4)
            .build();
    assertThat(index.size()).isEqualTo(1);
    assertThat(index.nearest(0.5, 0, SQUARE, 6)).isEmpty();
    assertThat(index.nearest(2.5, 0, SQUARE, 6).orElseThrow().segment()).isEqualTo(2);
  }

  @Test
  void tiesGoToTheLowerItemThenSegment() {
    HitIndex index =
        HitIndex.builder()
            .polyline(3, new double[] {-1, 1}, new double[] {0.2, 0.2}, 2)
            .polyline(2, new double[] {-1, 1}, new double[] {-0.2, -0.2}, 2)
            .build();
    assertThat(index.nearest(0, 0, SQUARE, 6).orElseThrow().item()).isEqualTo(2);
  }

  @Test
  void aSegmentWhosePixelsOverflowIsNoHit() {
    // From -1e308 to 1e308 the pixel extent is infinite and the projection not a number.
    HitIndex index =
        HitIndex.builder()
            .polyline(0, new double[] {0, 0}, new double[] {-1e308, 1e308}, 2)
            .polyline(1, new double[] {0.3, 0.3}, new double[] {-1, 1}, 2)
            .build();
    assertThat(index.nearest(-5, 0, SQUARE, 6)).isEmpty();
    Hit hit = index.nearest(0.1, 0, SQUARE, 6).orElseThrow();
    assertThat(hit.item()).isEqualTo(1);
    assertThat(hit.distance()).isCloseTo(2, within(1e-9));
  }

  @Test
  void theEmptyIndexHitsNothing() {
    assertThat(HitIndex.EMPTY.size()).isZero();
    assertThat(HitIndex.EMPTY.nearest(0, 0, SQUARE, 6)).isEmpty();
  }

  @Test
  void aLargeIndexAgreesWithTheScan() {
    HitIndex.Builder b = HitIndex.builder();
    int n = 2000;
    for (int k = 0; k < 10; k++) {
      double[] xs = new double[n];
      double[] ys = new double[n];
      for (int i = 0; i < n; i++) {
        xs[i] = -10 + 20.0 * i / (n - 1);
        ys[i] = Math.sin(xs[i] + k) * (1 + k / 3.0);
      }
      b.polyline(k, xs, ys, n);
    }
    HitIndex index = b.build();
    assertThat(index.size()).isEqualTo(10 * (n - 1));
    for (double x = -10; x <= 10; x += 0.37) {
      for (double y = -4; y <= 4; y += 0.29) {
        assertThat(index.nearest(x, y, SQUARE, 6)).isEqualTo(index.nearestByScan(x, y, SQUARE, 6));
      }
    }
  }
}
