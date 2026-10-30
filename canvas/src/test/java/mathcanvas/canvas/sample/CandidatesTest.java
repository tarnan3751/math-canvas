package mathcanvas.canvas.sample;

import static mathcanvas.canvas.sample.SamplersTest.expr;
import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.canvas.model.Candidates;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.Feature;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Recomputes;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Window;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Settings;
import org.junit.jupiter.api.Test;

/** The counts a tool creates members by, with the real sampler. */
class CandidatesTest {

  private static final Window W = new Window(-4, 4, -4, 4);
  private static final Sampler S = new Samplers().over(Environment.EMPTY, Settings.DEFAULT);

  private static Geometry.Curve[] graphs(String... bodies) {
    CanvasState s = CanvasState.EMPTY;
    for (String b : bodies) {
      s =
          s.add(
              i -> new FunctionGraph(i, Style.DEFAULT, expr(b), SamplersTest.X, FunctionGraph.ALL));
    }
    s = s.recomputeAll(S, W, Recomputes.standard(), Cancellation.NEVER);
    Geometry.Curve[] out = new Geometry.Curve[bodies.length];
    for (int k = 0; k < bodies.length; k++) {
      out[k] = (Geometry.Curve) s.geometryOf(new ObjectId(k + 1));
    }
    return out;
  }

  @Test
  void countsTheIntersectionsOfTwoGraphs() {
    Geometry.Curve[] g = graphs("x^2", "x + 2", "x^2 + 1");
    assertThat(Candidates.intersections(g[0], g[1], S, W, Cancellation.NEVER))
        .containsExactly(0, 1);
    assertThat(Candidates.intersections(g[0], g[2], S, W, Cancellation.NEVER)).isEmpty();
  }

  @Test
  void countsAGraphsFeatures() {
    Geometry.Curve g = graphs("x^3 - x")[0];
    assertThat(Candidates.features(g, Feature.ROOT, S, W, Cancellation.NEVER))
        .containsExactly(0, 1, 2);
    assertThat(Candidates.features(g, Feature.MIN, S, W, Cancellation.NEVER)).containsExactly(0);
    assertThat(Candidates.features(g, Feature.MAX, S, W, Cancellation.NEVER)).containsExactly(0);
    assertThat(Candidates.features(graphs("x^2")[0], Feature.ROOT, S, W, Cancellation.NEVER))
        .containsExactly(0);
  }

  @Test
  void onlyThePlacesInsideTheWindowCount() {
    // sampled beyond the window (-4 to 4): the roots at ±2π are found there but not counted
    Geometry.Curve g = graphs("sin(x)")[0];
    assertThat(Candidates.features(g, Feature.ROOT, S, W, Cancellation.NEVER))
        .hasSize(3)
        .containsExactly(1, 2, 3);
  }
}
