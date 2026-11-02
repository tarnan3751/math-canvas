package mathcanvas.canvas.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import mathcanvas.canvas.model.Arrow;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Projection;
import mathcanvas.canvas.model.Recomputes;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.VectorSum;
import mathcanvas.canvas.model.Window;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Settings;
import org.junit.jupiter.api.Test;

/** An arrow bound to a worksheet variable, through the real sampler. */
class VectorKindsTest {

  private static final Style S = Style.DEFAULT;
  private static final Window W = new Window(-10, 10, -10, 10);

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  private static Sampler sampler(String u) {
    return new Samplers().over(SamplersTest.env("u", u), Settings.DEFAULT);
  }

  private static Geometry.Point p(double x, double y) {
    return new Geometry.Point(x, y);
  }

  /** A(1) at (1, 1); u(2) bound from the origin; w(3) from A to the origin; u + w (4); proj (5). */
  private static CanvasState figure() {
    return CanvasState.EMPTY
        .add(i -> new FreePoint(i, S, 1, 1))
        .add(i -> new Arrow(i, S, Optional.empty(), new Arrow.Bound("u")))
        .add(i -> new Arrow(i, S, Optional.of(id(1)), new Arrow.At(Optional.empty())))
        .add(i -> new VectorSum(i, S, id(2), id(3)))
        .add(i -> new Projection(i, S, id(2), id(3)));
  }

  private static CanvasState changed(CanvasState s, String u) {
    return s.recompute(
        Set.of(), Set.of("u"), sampler(u), W, Recomputes.standard(), Cancellation.NEVER);
  }

  @Test
  void aBoundArrowFollowsItsVariable() {
    CanvasState s =
        figure().recomputeAll(sampler("[3, 2]"), W, Recomputes.standard(), Cancellation.NEVER);
    assertThat(s.geometryOf(id(2)))
        .isEqualTo(new Geometry.Arrow(p(0, 0), p(3, 2), java.util.List.of()));
    assertThat(((Geometry.Arrow) s.geometryOf(id(4))).head()).isEqualTo(p(2, 1));
    s = changed(s, "⟨1/2, -4⟩");
    assertThat(((Geometry.Arrow) s.geometryOf(id(2))).head()).isEqualTo(p(0.5, -4));
    assertThat(((Geometry.Arrow) s.geometryOf(id(4))).head()).isEqualTo(p(-0.5, -5));
    // (1/2, -4) onto (-1, -1): 3.5/2 (-1, -1) from A
    assertThat(((Geometry.Arrow) s.geometryOf(id(5))).head()).isEqualTo(p(1 - 1.75, 1 - 1.75));
  }

  @Test
  void aBoundArrowIsAtItsTail() {
    CanvasState s =
        figure()
            .replace(new Arrow(id(2), S, Optional.of(id(1)), new Arrow.Bound("u")))
            .recomputeAll(sampler("[3, 2]"), W, Recomputes.standard(), Cancellation.NEVER);
    assertThat(((Geometry.Arrow) s.geometryOf(id(2))).tail()).isEqualTo(p(1, 1));
    assertThat(((Geometry.Arrow) s.geometryOf(id(2))).head()).isEqualTo(p(4, 3));
  }

  @Test
  void aValueThatIsNotATwoVectorLeavesTheArrowUndefinedUntilItIsAgain() {
    CanvasState s =
        figure().recomputeAll(sampler("[3, 2]"), W, Recomputes.standard(), Cancellation.NEVER);
    for (String not : new String[] {"5", "[1, 2, 3]", "[x, 1]", "[i, 1]", "{1, 2}"}) {
      CanvasState t = changed(s, not);
      assertThat(t.geometryOf(id(2))).as(not).isEqualTo(Geometry.UNDEFINED);
      assertThat(t.geometryOf(id(4))).as(not).isEqualTo(Geometry.UNDEFINED);
      assertThat(t.geometryOf(id(5))).as(not).isEqualTo(Geometry.UNDEFINED);
      assertThat(t.geometryOf(id(3))).as(not).isInstanceOf(Geometry.Arrow.class);
    }
    CanvasState back = changed(changed(s, "5"), "[0, 1]");
    assertThat(((Geometry.Arrow) back.geometryOf(id(2))).head()).isEqualTo(p(0, 1));
  }

  @Test
  void anUndefinedVariableLeavesTheArrowUndefined() {
    CanvasState s =
        figure()
            .recomputeAll(
                new Samplers().over(mathcanvas.engine.eval.Environment.EMPTY, Settings.DEFAULT),
                W,
                Recomputes.standard(),
                Cancellation.NEVER);
    assertThat(s.geometryOf(id(2))).isEqualTo(Geometry.UNDEFINED);
  }
}
