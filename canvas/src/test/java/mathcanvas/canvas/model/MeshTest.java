package mathcanvas.canvas.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Num;
import org.junit.jupiter.api.Test;

/** The surface kind's record and the mesh's checks and bounds. */
class MeshTest {

  @Test
  void aSurfaceTakesOneOfTheFourSizesAndReadsItsFreeNames() {
    assertThatThrownBy(
            () ->
                new Surface(
                    new ObjectId(1),
                    Style.DEFAULT,
                    Parse.expr("x y"),
                    Surface.DEFAULT_DOMAIN,
                    Surface.DEFAULT_DOMAIN,
                    60))
        .hasMessageContaining("50, 100, 150 or 200");
    Surface s =
        new Surface(
            new ObjectId(1),
            Style.DEFAULT,
            Parse.expr("a x + y"),
            Interval.of(Num.ZERO, Parse.expr("b"), true, true),
            Surface.DEFAULT_DOMAIN,
            200);
    assertThat(s.reads()).containsExactlyInAnyOrder("a", "b");
    assertThat(s.withN(50).n()).isEqualTo(50);
    assertThat(s.parents()).isEmpty();
  }

  @Test
  void aMeshHasItsVertexCount() {
    assertThatThrownBy(() -> new Geometry.Mesh(2, 2, new float[8], new float[9], new float[9]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Geometry.Mesh(0, 2, new float[3], new float[3], new float[3]))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aSpikeIsCutFromTheHeightRange() {
    int n = 20;
    int size = (n + 1) * (n + 1);
    float[] xs = new float[size];
    float[] ys = new float[size];
    float[] zs = new float[size];
    for (int k = 0; k < size; k++) {
      xs[k] = k % (n + 1);
      ys[k] = k / (n + 1);
      zs[k] = (float) (k % 7) / 6;
    }
    zs[200] = 1e6f;
    zs[201] = Float.NaN;
    double[] b = new Geometry.Mesh(n, n, xs, ys, zs).bounds().orElseThrow();
    assertThat(b[4]).isEqualTo(0);
    assertThat(b[5]).isCloseTo(1.1, within(1e-6));
    zs[200] = 3;
    double[] mild = new Geometry.Mesh(n, n, xs, ys, zs).bounds().orElseThrow();
    assertThat(mild[5]).isEqualTo(3);
    assertThat(mild[4]).isEqualTo(0);
  }

  @Test
  void theStubSamplersGridIsTheSurface() {
    CanvasState s =
        CanvasState.EMPTY
            .add(
                i ->
                    new Surface(
                        i,
                        Style.DEFAULT,
                        Parse.expr("x^2 - y^2"),
                        Surface.DEFAULT_DOMAIN,
                        Surface.DEFAULT_DOMAIN,
                        50))
            .recomputeAll(
                new StubSampler(Map.of()),
                new Window(-1, 1, -1, 1),
                Recomputes.standard(),
                Cancellation.NEVER);
    Geometry.Mesh m = (Geometry.Mesh) s.geometryOf(new ObjectId(1));
    assertThat(m.zs()[0]).isEqualTo(0f);
    assertThat(m.zs()[50]).isEqualTo(0f);
    assertThat(m.zs()[25]).isCloseTo(-25f, within(1e-5f));
    assertThat(m.bounds().orElseThrow()).containsExactly(-5, 5, -5, 5, -25, 25);
  }
}
