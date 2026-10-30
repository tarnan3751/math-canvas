package mathcanvas.canvas.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.CancelledException;
import mathcanvas.engine.expr.Symbol;
import org.junit.jupiter.api.Test;

class CanvasStateTest {

  private static final Style S = Style.DEFAULT;
  private static final Window W = new Window(-5, 5, -5, 5);
  private static final StubSampler SAMPLER = new StubSampler(Map.of());

  /** Records the order of the calls and gives every object a geometry of its kind. */
  private static final class Counting implements Recompute {
    final List<ObjectId> calls = new ArrayList<>();
    final List<List<ObjectId>> groups = new ArrayList<>();
    final Set<ObjectId> undefined;

    Counting(Set<ObjectId> undefined) {
      this.undefined = undefined;
    }

    @Override
    public List<Result> apply(
        List<CanvasObject> group,
        Map<ObjectId, Geometry> parents,
        Sampler sampler,
        Window window,
        Cancellation cancellation) {
      List<Result> out = new ArrayList<>();
      List<ObjectId> ids = new ArrayList<>();
      for (CanvasObject object : group) {
        calls.add(object.id());
        ids.add(object.id());
        out.add(
            new Result(
                object, undefined.contains(object.id()) ? Geometry.UNDEFINED : fitting(object)));
      }
      groups.add(ids);
      return out;
    }
  }

  /** A geometry of the object's kind. */
  static Geometry fitting(CanvasObject o) {
    return switch (o) {
      case FreePoint _, PointOnPath _, Intersection _, Text _ -> new Geometry.Point(0, 0);
      case Slider _ -> new Geometry.Value(0);
      case InequalityRegion _ -> new Geometry.Region(List.of());
      case FunctionGraph _, ParametricCurve _, PolarCurve _, ImplicitCurve _, Segment _, Line _ ->
          new Geometry.Curve(Path.POLYLINE_ONLY, List.of());
    };
  }

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  private static Slider slider(ObjectId id, String name) {
    return new Slider(id, S, name, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE);
  }

  /** A(1), B(2) free; L(3) through them; P(4) on L; slider a(5); graph f(6) reading a. */
  private static CanvasState construction() {
    return CanvasState.EMPTY
        .add(i -> new FreePoint(i, S, 0, 0))
        .add(i -> new FreePoint(i, S, 1, 1))
        .add(i -> new Line(i, S, new Line.Through(id(1), id(2))))
        .add(i -> new PointOnPath(i, S, id(3), ParamKind.LINE, 0.5, Double.NaN, Double.NaN))
        .add(i -> slider(i, "a"))
        .add(i -> new FunctionGraph(i, S, Parse.expr("a x"), Symbol.of("x"), FunctionGraph.ALL));
  }

  @Test
  void addAllocatesIdsInOrder() {
    CanvasState s = construction();
    assertThat(s.objects())
        .extracting(CanvasObject::id)
        .containsExactly(id(1), id(2), id(3), id(4), id(5), id(6));
    assertThat(s.nextObjectId()).isEqualTo(id(7));
    assertThatThrownBy(() -> s.add(i -> new FreePoint(id(1), S, 0, 0)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parentsMustExistAndBeOfTheRightKind() {
    CanvasState s = construction();
    assertThatThrownBy(() -> s.add(i -> new Segment(i, S, id(1), id(99))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing");
    assertThatThrownBy(() -> s.add(i -> new Segment(i, S, id(1), id(3))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("point");
    assertThatThrownBy(
            () -> s.add(i -> new PointOnPath(i, S, id(1), ParamKind.X, 0, Double.NaN, Double.NaN)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("path");
    assertThatThrownBy(() -> s.replace(new Text(id(1), S, 0, 0, "A")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sliderVariablesAreDistinct() {
    assertThatThrownBy(() -> construction().add(i -> slider(i, "a")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("two sliders");
  }

  @Test
  void aCycleIsReportedAndRefused() {
    CanvasState s = construction();
    PointOnPath a = new PointOnPath(id(1), S, id(3), ParamKind.LINE, 0, Double.NaN, Double.NaN);
    assertThat(s.cycleThrough(a)).contains(List.of(id(1), id(3)));
    assertThatThrownBy(() -> s.replace(a))
        .isInstanceOfSatisfying(
            CycleException.class, e -> assertThat(e.cycle()).containsExactly(id(1), id(3)));
    assertThat(s.cycleThrough(new FreePoint(id(1), S, 3, 3))).isEmpty();
  }

  @Test
  void aPointOnAGraphReadingASliderMakesNoCycle() {
    CanvasState s = construction();
    // the point on the line, redefined on the graph f, which reads a: no cycle
    assertThat(
            s.cycleThrough(
                new PointOnPath(id(4), S, id(6), ParamKind.X, 0, Double.NaN, Double.NaN)))
        .isEmpty();
  }

  @Test
  void replaceKeepsThePlaceAndAnUnknownIdIsIgnored() {
    CanvasState s = construction();
    CanvasState t = s.replace(new FreePoint(id(2), S, 4, 4));
    assertThat(t.objects().get(1)).isEqualTo(new FreePoint(id(2), S, 4, 4));
    assertThat(t.changedSince(s)).containsExactly(id(2));
    assertThat(s.replace(new FreePoint(id(42), S, 0, 0))).isSameAs(s);
  }

  @Test
  void removeTakesTheDependentsButNotTheReadersOfASlider() {
    CanvasState s =
        construction().recomputeAll(SAMPLER, W, new Counting(Set.of()), Cancellation.NEVER);
    CanvasState t = s.remove(id(2));
    assertThat(t.objects()).extracting(CanvasObject::id).containsExactly(id(1), id(5), id(6));
    assertThat(t.geometry()).containsOnlyKeys(id(1), id(5), id(6));
    assertThat(t.nextId()).isEqualTo(s.nextId());
    assertThat(s.remove(id(5)).objects()).extracting(CanvasObject::id).contains(id(6));
    assertThat(s.remove(id(42))).isSameAs(s);
  }

  @Test
  void aSliderDrivesTheObjectsThatReadItsVariable() {
    CanvasState s = construction();
    assertThat(s.readers(Set.of("a"))).containsExactly(id(6));
    Counting c = new Counting(Set.of());
    s.recompute(Set.of(id(5)), Set.of(), SAMPLER, W, c, Cancellation.NEVER);
    assertThat(c.calls).containsExactly(id(5), id(6));
    Counting d = new Counting(Set.of());
    s.recompute(Set.of(), Set.of("a"), SAMPLER, W, d, Cancellation.NEVER);
    assertThat(d.calls).containsExactly(id(6));
  }

  @Test
  void recomputeVisitsDependentsOnceAfterTheirParents() {
    Counting c = new Counting(Set.of());
    CanvasState s =
        construction().recompute(Set.of(id(1)), Set.of(), SAMPLER, W, c, Cancellation.NEVER);
    // B was never computed, so the line and the point on it are undefined, not recomputed
    assertThat(c.calls).containsExactly(id(1));
    assertThat(s.geometry()).containsOnlyKeys(id(1), id(3), id(4));
    Counting d = new Counting(Set.of());
    s.recomputeAll(SAMPLER, W, new Counting(Set.of()), Cancellation.NEVER)
        .recompute(Set.of(id(1)), Set.of(), SAMPLER, W, d, Cancellation.NEVER);
    assertThat(d.calls).containsExactly(id(1), id(3), id(4));
  }

  @Test
  void undefinedPropagatesWithoutRecomputingAndClears() {
    Counting c = new Counting(Set.of(id(1)));
    CanvasState s = construction().recomputeAll(SAMPLER, W, c, Cancellation.NEVER);
    assertThat(c.calls).doesNotContain(id(3), id(4));
    assertThat(s.geometryOf(id(3))).isEqualTo(Geometry.UNDEFINED);
    assertThat(s.geometryOf(id(4))).isEqualTo(Geometry.UNDEFINED);
    assertThat(s.geometryOf(id(2))).isEqualTo(new Geometry.Point(0, 0));
    CanvasState t =
        s.recompute(
            Set.of(id(1)), Set.of(), SAMPLER, W, new Counting(Set.of()), Cancellation.NEVER);
    assertThat(t.geometryOf(id(4))).isEqualTo(new Geometry.Point(0, 0));
  }

  @Test
  void aRecomputeMayNotChangeADefinition() {
    Recompute bad =
        (g, p, s, w, c) ->
            g.stream()
                .map(
                    o ->
                        new Recompute.Result(
                            o instanceof Line l
                                ? new Line(l.id(), S, new Line.Through(id(2), id(1)))
                                : o,
                            fitting(o)))
                .toList();
    assertThatThrownBy(() -> construction().recomputeAll(SAMPLER, W, bad, Cancellation.NEVER))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void anIntersectionFamilyIsRecomputedAsOneGroup() {
    CanvasState s =
        construction()
            .add(i -> Intersection.of(i, S, id(6), id(3), 0))
            .add(i -> new FreePoint(i, S, 0, 0))
            .add(i -> Intersection.of(i, S, id(6), id(3), 1))
            .add(i -> Intersection.of(i, S, id(3), id(6), 0))
            .recomputeAll(SAMPLER, W, new Counting(Set.of()), Cancellation.NEVER);
    Counting c = new Counting(Set.of());
    s.recompute(Set.of(id(9)), Set.of(), SAMPLER, W, c, Cancellation.NEVER);
    assertThat(c.groups).containsExactly(List.of(id(7), id(9)));
    Counting d = new Counting(Set.of());
    s.recompute(Set.of(id(5)), Set.of(), SAMPLER, W, d, Cancellation.NEVER);
    assertThat(d.groups)
        .containsExactly(List.of(id(5)), List.of(id(6)), List.of(id(7), id(9)), List.of(id(10)));
  }

  @Test
  void geometryMustFitItsObjectAndIdsStartAtOne() {
    CanvasState s = construction();
    assertThatThrownBy(
            () -> new CanvasState(s.objects(), Map.of(id(1), new Geometry.Value(1)), s.nextId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("wrong kind");
    assertThatThrownBy(
            () -> new CanvasState(s.objects(), Map.of(id(3), new Geometry.Point(0, 0)), s.nextId()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(
            new CanvasState(s.objects(), Map.of(id(3), Geometry.UNDEFINED), s.nextId())
                .geometryOf(id(3)))
        .isEqualTo(Geometry.UNDEFINED);
    assertThatThrownBy(() -> new CanvasState(List.of(), Map.of(), 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aCancelledRecomputeStops() {
    Cancellation stop =
        new Cancellation() {
          @Override
          public boolean requested() {
            return true;
          }
        };
    assertThatThrownBy(() -> construction().recomputeAll(SAMPLER, W, new Counting(Set.of()), stop))
        .isInstanceOf(CancelledException.class);
  }

  @Test
  void theNextColourIsTheLeastUsedAmongDrawnObjects() {
    CanvasState c =
        CanvasState.EMPTY.add(
            id ->
                new Slider(
                    id,
                    new Style(5, 2, false, true, false),
                    "a",
                    BigDecimal.ZERO,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    BigDecimal.ZERO));
    assertThat(c.nextColour()).isZero(); // a slider is not drawn
    c = c.add(id -> new FreePoint(id, new Style(0, 2, false, true, false), 0, 0));
    c = c.add(id -> new FreePoint(id, new Style(2, 2, false, true, false), 0, 0));
    assertThat(c.nextColour()).isEqualTo(1);
    for (int k = 0; k < Style.COLOURS; k++) {
      int colour = k;
      if (colour != 0 && colour != 2) {
        c = c.add(id -> new FreePoint(id, new Style(colour, 2, false, true, false), 0, 0));
      }
    }
    assertThat(c.nextColour()).isZero(); // every colour used once: the lowest index
  }
}
