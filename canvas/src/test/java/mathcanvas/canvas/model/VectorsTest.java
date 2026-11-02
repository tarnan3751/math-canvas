package mathcanvas.canvas.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import mathcanvas.engine.Cancellation;
import org.junit.jupiter.api.Test;

class VectorsTest {

  private static final Style S = Style.DEFAULT;
  private static final Window W = new Window(-10, 10, -10, 10);
  private static final Sampler SAMPLER = new StubSampler(Map.of());

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  private static Geometry.Arrow arrow(CanvasState s, long id) {
    return (Geometry.Arrow) s.geometryOf(id(id));
  }

  private static Geometry.Point p(double x, double y) {
    return new Geometry.Point(x, y);
  }

  /**
   * O(1) at the origin, A(2) at (3, 2), B(3) at (4, -1); u(4) from O to A, v(5) from O to B, u + v
   * (6), proj_v u (7).
   */
  private static CanvasState figure() {
    return CanvasState.EMPTY
        .add(i -> new FreePoint(i, S, 0, 0))
        .add(i -> new FreePoint(i, S, 3, 2))
        .add(i -> new FreePoint(i, S, 4, -1))
        .add(i -> new Arrow(i, S, Optional.of(id(1)), new Arrow.At(Optional.of(id(2)))))
        .add(i -> new Arrow(i, S, Optional.of(id(1)), new Arrow.At(Optional.of(id(3)))))
        .add(i -> new VectorSum(i, S, id(4), id(5)))
        .add(i -> new Projection(i, S, id(4), id(5)))
        .recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
  }

  private static CanvasState move(CanvasState s, long point, double x, double y) {
    FreePoint p = (FreePoint) s.object(id(point)).orElseThrow();
    return s.replace(p.at(x, y))
        .recompute(
            Set.of(id(point)), Set.of(), SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
  }

  @Test
  void theKindsTakeTheirParentsAndReadTheirVariable() {
    Arrow bound = new Arrow(id(3), S, Optional.empty(), new Arrow.Bound("u"));
    assertThat(bound.parents()).isEmpty();
    assertThat(bound.reads()).containsExactly("u");
    Arrow points = new Arrow(id(3), S, Optional.of(id(1)), new Arrow.At(Optional.of(id(2))));
    assertThat(points.parents()).containsExactly(id(1), id(2));
    assertThat(points.reads()).isEmpty();
    assertThat(new Arrow(id(3), S, Optional.empty(), new Arrow.At(Optional.of(id(2)))).parents())
        .containsExactly(id(2));
    assertThat(new VectorSum(id(3), S, id(1), id(2)).parents()).containsExactly(id(1), id(2));
    assertThat(new Projection(id(3), S, id(1), id(2)).parents()).containsExactly(id(1), id(2));
    assertThat(bound.isVector()).isTrue();
    assertThat(bound.isPoint() || bound.isPath()).isFalse();
  }

  @Test
  void theRecordsRefuseOnePointOrOneVectorTwice() {
    assertThatThrownBy(
            () -> new Arrow(id(3), S, Optional.of(id(1)), new Arrow.At(Optional.of(id(1)))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new VectorSum(id(3), S, id(1), id(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Projection(id(3), S, id(1), id(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Arrow.Bound("sin")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Arrow.Bound("u v")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theStateRefusesParentsOfTheWrongKind() {
    CanvasState points =
        CanvasState.EMPTY
            .add(i -> new FreePoint(i, S, 0, 0))
            .add(i -> new FreePoint(i, S, 1, 0))
            .add(i -> new Arrow(i, S, Optional.of(id(1)), new Arrow.At(Optional.of(id(2)))));
    assertThatThrownBy(() -> points.add(i -> new VectorSum(i, S, id(1), id(3))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("needs a vector");
    assertThatThrownBy(
            () ->
                points.add(
                    i -> new Arrow(i, S, Optional.of(id(3)), new Arrow.At(Optional.of(id(1))))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("needs a point");
  }

  @Test
  void anArrowRunsFromItsTailToItsHeadOrTheOrigin() {
    CanvasState s = figure();
    assertThat(arrow(s, 4)).isEqualTo(new Geometry.Arrow(p(0, 0), p(3, 2), List.of()));
    CanvasState origin =
        CanvasState.EMPTY
            .add(i -> new FreePoint(i, S, 2, 5))
            .add(i -> new Arrow(i, S, Optional.of(id(1)), new Arrow.At(Optional.empty())))
            .recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
    assertThat(arrow(origin, 2)).isEqualTo(new Geometry.Arrow(p(2, 5), p(0, 0), List.of()));
    assertThat(arrow(origin, 2).dx()).isEqualTo(-2);
    assertThat(arrow(origin, 2).dy()).isEqualTo(-5);
  }

  @Test
  void theSumIsDrawnTipToTailWithItsParallelogram() {
    Geometry.Arrow sum = arrow(figure(), 6);
    assertThat(sum.tail()).isEqualTo(p(0, 0));
    assertThat(sum.head()).isEqualTo(p(7, 1));
    // v translated to u's head as an arrow, and the side from v's head dashed; v itself is drawn
    // from the common tail
    assertThat(sum.guides())
        .containsExactly(
            new Geometry.Guide(p(3, 2), p(7, 1), true),
            new Geometry.Guide(p(4, -1), p(7, 1), false));
  }

  @Test
  void theSumFromAnotherTailDrawsBothSidesOfV() {
    CanvasState s = move(figure(), 3, 4, -1);
    // v from A instead: u from O to A, v from A to B
    s =
        s.replace(new Arrow(id(5), S, Optional.of(id(2)), new Arrow.At(Optional.of(id(3)))))
            .recompute(
                Set.of(id(5)), Set.of(), SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
    Geometry.Arrow sum = arrow(s, 6);
    assertThat(sum.head()).isEqualTo(p(4, -1));
    assertThat(sum.guides())
        .containsExactly(
            new Geometry.Guide(p(3, 2), p(4, -1), true),
            new Geometry.Guide(p(1, -3), p(4, -1), false),
            new Geometry.Guide(p(0, 0), p(1, -3), false));
  }

  @Test
  void theProjectionLiesAlongVWithItsPerpendicular() {
    Geometry.Arrow proj = arrow(figure(), 7);
    // u·v = 10, v·v = 17
    assertThat(proj.tail()).isEqualTo(p(0, 0));
    assertThat(proj.head().x()).isCloseTo(40.0 / 17, within(1e-15));
    assertThat(proj.head().y()).isCloseTo(-10.0 / 17, within(1e-15));
    assertThat(proj.guides()).hasSize(1);
    Geometry.Guide drop = proj.guides().getFirst();
    assertThat(drop.from()).isEqualTo(p(3, 2));
    assertThat(drop.to()).isEqualTo(proj.head());
    assertThat(drop.arrow()).isFalse();
    // the drop is perpendicular to v
    double dx = drop.to().x() - drop.from().x();
    double dy = drop.to().y() - drop.from().y();
    assertThat(dx * 4 + dy * -1).isCloseTo(0, within(1e-12));
    Geometry.Arrow u = arrow(figure(), 4);
    Geometry.Arrow v = arrow(figure(), 5);
    assertThat(u.componentAlong(v)).isCloseTo(10 / Math.sqrt(17), within(1e-15));
  }

  @Test
  void dragAPointAndTheFigureFollows() {
    CanvasState s = move(figure(), 2, 1, 1);
    assertThat(arrow(s, 4).head()).isEqualTo(p(1, 1));
    assertThat(arrow(s, 6).head()).isEqualTo(p(5, 0));
    // u·v = 3, v·v = 17
    assertThat(arrow(s, 7).head().x()).isCloseTo(12.0 / 17, within(1e-15));
    s = move(s, 1, 1, 0);
    // every arrow from (1, 0): u = (0, 1), v = (3, -1)
    assertThat(arrow(s, 4).tail()).isEqualTo(p(1, 0));
    assertThat(arrow(s, 6).tail()).isEqualTo(p(1, 0));
    assertThat(arrow(s, 6).head()).isEqualTo(p(4, 0));
    assertThat(arrow(s, 7).tail()).isEqualTo(p(1, 0));
    assertThat(arrow(s, 7).head().x()).isCloseTo(1 - 3.0 / 10, within(1e-15));
  }

  @Test
  void aProjectionOntoTheZeroVectorIsUndefinedAndComesBack() {
    CanvasState s = move(figure(), 3, 0, 0);
    assertThat(s.geometryOf(id(7))).isEqualTo(Geometry.UNDEFINED);
    assertThat(arrow(s, 6).head()).isEqualTo(p(3, 2));
    assertThat(arrow(s, 5).componentAlong(arrow(s, 5))).isNaN();
    s = move(s, 3, 4, -1);
    assertThat(arrow(s, 7).head().x()).isCloseTo(40.0 / 17, within(1e-15));
  }

  @Test
  void sumsAndProjectionsAreVectorsInTurn() {
    CanvasState s =
        figure()
            .add(i -> new Projection(i, S, id(6), id(4)))
            .recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
    // (7, 1) onto (3, 2): 23/13 (3, 2)
    assertThat(arrow(s, 8).head().x()).isCloseTo(69.0 / 13, within(1e-14));
    assertThat(arrow(s, 8).head().y()).isCloseTo(46.0 / 13, within(1e-14));
  }

  @Test
  void anUndefinedPartLeavesTheVectorsUndefined() {
    CanvasState s =
        CanvasState.EMPTY
            .add(i -> new FreePoint(i, S, 1, 1))
            .add(i -> new Arrow(i, S, Optional.empty(), new Arrow.Bound("u")))
            .add(i -> new Arrow(i, S, Optional.empty(), new Arrow.At(Optional.of(id(1)))))
            .add(i -> new VectorSum(i, S, id(2), id(3)))
            .recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
    // the stub sampler knows no vectors: u is not a 2-vector
    assertThat(s.geometryOf(id(2))).isEqualTo(Geometry.UNDEFINED);
    assertThat(s.geometryOf(id(4))).isEqualTo(Geometry.UNDEFINED);
    assertThat(s.geometryOf(id(3))).isInstanceOf(Geometry.Arrow.class);
  }
}
