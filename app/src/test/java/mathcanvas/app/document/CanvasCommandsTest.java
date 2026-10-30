package mathcanvas.app.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import mathcanvas.canvas.model.Anchor;
import mathcanvas.canvas.model.Approach;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.CycleException;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.Intersection;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParamKind;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.Segment;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Style;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

class CanvasCommandsTest {

  private static final ObjectId ONE = new ObjectId(1);
  private static final ObjectId TWO = new ObjectId(2);
  private static final ObjectId THREE = new ObjectId(3);

  private static Command.AddObject point(double x, double y, String name) {
    return new Command.AddObject(
        id -> new FreePoint(id, Style.DEFAULT, x, y), Optional.ofNullable(name));
  }

  private static Command.AddObject graph(String body) {
    return new Command.AddObject(
        id ->
            new FunctionGraph(
                id,
                Style.DEFAULT,
                Parser.parseExpression(body, Names.NONE),
                Symbol.of("x"),
                FunctionGraph.ALL));
  }

  private static Command.AddObject slider(String name) {
    return new Command.AddObject(
        id ->
            new Slider(
                id,
                Style.DEFAULT,
                name,
                BigDecimal.ZERO,
                new BigDecimal(5),
                new BigDecimal("0.1"),
                BigDecimal.ZERO));
  }

  private static DocumentState run(DocumentState s, Command... commands) {
    for (Command c : commands) {
      s = c.apply(s);
    }
    return s;
  }

  @Test
  void addingGivesTheNextIdAndTheName() {
    DocumentState s = run(DocumentState.EMPTY, point(1, 2, "A"), point(3, 4, null));

    assertThat(s.canvas().objects())
        .containsExactly(
            new FreePoint(ONE, Style.DEFAULT, 1, 2), new FreePoint(TWO, Style.DEFAULT, 3, 4));
    assertThat(s.canvas().nextId()).isEqualTo(3);
    assertThat(s.names()).containsExactly(Map.entry(ONE, "A"));
  }

  @Test
  void aNameIsAVariableNameGivenOnceToAPoint() {
    DocumentState a = run(DocumentState.EMPTY, point(0, 0, "A"));

    assertThatThrownBy(() -> point(1, 1, "A").apply(a))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> point(1, 1, "sin").apply(a))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> point(1, 1, "a b").apply(a))
        .isInstanceOf(IllegalArgumentException.class);
    DocumentState withSlider = run(DocumentState.EMPTY, slider("a"));
    assertThatThrownBy(() -> point(1, 1, "a").apply(withSlider))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new Command.AddObject(
                        id ->
                            new FunctionGraph(
                                id,
                                Style.DEFAULT,
                                Symbol.of("x"),
                                Symbol.of("x"),
                                FunctionGraph.ALL),
                        Optional.of("f"))
                    .apply(DocumentState.EMPTY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void removingCascadesAndDropsTheNames() {
    DocumentState s =
        run(
            DocumentState.EMPTY,
            graph("x^2"),
            new Command.AddObject(
                id ->
                    new PointOnPath(id, Style.DEFAULT, ONE, ParamKind.X, 1, Double.NaN, Double.NaN),
                Optional.of("P")),
            point(5, 5, "Q"));

    DocumentState after = new Command.RemoveObject(ONE).apply(s);

    assertThat(after.canvas().objects()).extracting(o -> o.id()).containsExactly(THREE);
    assertThat(after.names()).containsExactly(Map.entry(THREE, "Q"));
    assertThat(after.canvas().nextId()).isEqualTo(4);
    assertThat(new Command.RemoveObject(ONE).apply(after)).isSameAs(after);
  }

  @Test
  void movingAFreePointAndAPointOnAPath() {
    DocumentState s =
        run(
            DocumentState.EMPTY,
            graph("x^2"),
            new Command.AddObject(
                id ->
                    new PointOnPath(
                        id, Style.DEFAULT, ONE, ParamKind.X, 1, Double.NaN, Double.NaN)),
            point(0, 0, "A"));

    DocumentState moved =
        run(s, new Command.MovePoint(THREE, 2, -1), new Command.MovePoint(TWO, 2, 4, 2));

    assertThat(moved.canvas().object(THREE)).contains(new FreePoint(THREE, Style.DEFAULT, 2, -1));
    assertThat(moved.canvas().object(TWO))
        .contains(new PointOnPath(TWO, Style.DEFAULT, ONE, ParamKind.X, 2, 2, 4));
    // a graph is not a point: nothing to move
    assertThat(new Command.MovePoint(ONE, 1, 1).apply(s)).isSameAs(s);
    assertThat(new Command.MovePoint(new ObjectId(9), 1, 1).apply(s)).isSameAs(s);
  }

  @Test
  void aSliderSnapsToItsStepsWithinItsRange() {
    DocumentState s = run(DocumentState.EMPTY, slider("a"));

    assertThat(value(new Command.SetSlider(ONE, new BigDecimal("2.349")).apply(s)))
        .isEqualByComparingTo("2.3");
    assertThat(value(new Command.SetSlider(ONE, new BigDecimal("2.35")).apply(s)))
        .isEqualByComparingTo("2.4");
    assertThat(value(new Command.SetSlider(ONE, new BigDecimal("-3")).apply(s)))
        .isEqualByComparingTo("0");
    assertThat(value(new Command.SetSlider(ONE, new BigDecimal("7")).apply(s)))
        .isEqualByComparingTo("5");
  }

  private static BigDecimal value(DocumentState s) {
    return ((Slider) s.canvas().object(ONE).orElseThrow()).value();
  }

  @Test
  void aStyleChangeKeepsTheDefinition() {
    DocumentState s = run(DocumentState.EMPTY, graph("x^2"));
    Style red = new Style(3, 4, true, false, true);

    DocumentState after = new Command.SetStyle(ONE, red).apply(s);

    assertThat(after.canvas().object(ONE).orElseThrow().style()).isEqualTo(red);
    assertThat(after.canvas().object(ONE).orElseThrow().withStyle(Style.DEFAULT))
        .isEqualTo(s.canvas().object(ONE).orElseThrow());
  }

  @Test
  void aCycleIsRefusedWithItsIds() {
    // a segment from A to B, and a point on the segment
    DocumentState s =
        run(
            DocumentState.EMPTY,
            point(0, 0, "A"),
            point(1, 0, "B"),
            new Command.AddObject(id -> new Segment(id, Style.DEFAULT, ONE, TWO)),
            new Command.AddObject(
                id ->
                    new PointOnPath(
                        id, Style.DEFAULT, THREE, ParamKind.LINE, 0.5, Double.NaN, Double.NaN)));
    ObjectId four = new ObjectId(4);

    // the segment redefined to start at the point on it
    Command loop = new Command.ReplaceObject(new Segment(THREE, Style.DEFAULT, four, TWO));

    assertThatThrownBy(() -> loop.apply(s))
        .isInstanceOfSatisfying(
            CycleException.class, e -> assertThat(e.cycle()).containsExactly(THREE, four));
  }

  @Test
  void undoRestoresObjectsAnchorsNamesAndTheNextId() {
    AtomicLong now = new AtomicLong();
    CommandStack stack = new CommandStack(DocumentState.EMPTY, now::get);
    stack.execute(graph("x^2"));
    stack.execute(graph("x + 1"));
    Anchor anchor =
        new Anchor(1.618, 2.618, 1.618, 1.618, false, Optional.of(new Approach(1.5, 1.5, 2.25)));
    stack.execute(
        new Command.AddObject(
            id -> new Intersection(id, Style.DEFAULT, ONE, TWO, 0, Optional.of(anchor)),
            Optional.of("I")));
    DocumentState before = stack.current();
    // a recompute moves the anchor, as derived output
    stack.amend(
        s ->
            s.withCanvas(
                new CanvasState(
                    List.of(
                        s.canvas().objects().get(0),
                        s.canvas().objects().get(1),
                        new Intersection(
                            THREE,
                            Style.DEFAULT,
                            ONE,
                            TWO,
                            0,
                            Optional.of(
                                new Anchor(
                                    1.7,
                                    2.7,
                                    1.7,
                                    1.7,
                                    false,
                                    Optional.of(new Approach(1.6, 1.6, 2.6)))))),
                    Map.of(THREE, new Geometry.Point(1.7, 2.7)),
                    s.canvas().nextId())));
    DocumentState amended = stack.current();

    stack.execute(new Command.RemoveObject(ONE));
    assertThat(stack.current().canvas().objects()).hasSize(1);
    assertThat(stack.current().names()).isEmpty();

    stack.undo();
    assertThat(stack.current()).isEqualTo(amended);
    assertThat(stack.current().names()).containsExactly(Map.entry(THREE, "I"));
    assertThat(stack.current().canvas().nextId()).isEqualTo(4);
    stack.undo();
    assertThat(stack.current().canvas())
        .isEqualTo(run(DocumentState.EMPTY, graph("x^2"), graph("x + 1")).canvas());
    stack.redo();
    // the step's end is the state as the recompute left it
    assertThat(stack.current()).isEqualTo(amended).isNotEqualTo(before);
  }

  @Test
  void aDragOfOnePointOrSliderMergesWithinTheInterval() {
    AtomicLong now = new AtomicLong();
    CommandStack stack = new CommandStack(DocumentState.EMPTY, now::get);
    stack.execute(point(0, 0, "A"));
    stack.execute(slider("a"));
    DocumentState start = stack.current();
    for (int k = 1; k <= 20; k++) {
      now.addAndGet(50_000_000L);
      stack.execute(new Command.MovePoint(ONE, k, k));
    }
    stack.undo();
    assertThat(stack.current()).isEqualTo(start);
    stack.redo();

    now.addAndGet(2_000_000_000L);
    stack.execute(new Command.SetSlider(TWO, new BigDecimal(1)));
    now.addAndGet(100_000_000L);
    stack.execute(new Command.SetSlider(TWO, new BigDecimal(2)));
    now.addAndGet(100_000_000L);
    stack.execute(new Command.MovePoint(ONE, 0, 0));
    stack.undo();
    assertThat(value(stack.current().canvas().object(TWO).map(o -> (Slider) o).orElseThrow()))
        .isEqualByComparingTo("2");
    stack.undo();
    assertThat(stack.current().canvas().object(ONE))
        .contains(new FreePoint(ONE, Style.DEFAULT, 20, 20));
    assertThat(value(stack.current().canvas().object(TWO).map(o -> (Slider) o).orElseThrow()))
        .isEqualByComparingTo("0");
  }

  private static BigDecimal value(Slider s) {
    return s.value();
  }

  @Test
  void theCellCommandsKeepTheCanvas() {
    DocumentState s =
        run(DocumentState.EMPTY, point(1, 1, "A"))
            .withViewport(DocumentState.DEFAULT_VIEWPORT.pan(10, 0));

    DocumentState after =
        run(
            s,
            new Command.EditInput(1, "A"),
            new Command.Submit(1),
            new Command.Append("2"),
            new Command.DeleteCell(1),
            new Command.SetSettings(
                new DocumentSettings(
                    20, mathcanvas.engine.eval.AngleMode.DEGREES, NumberFormat.DECIMAL, 5)));

    assertThat(after.canvas()).isEqualTo(s.canvas());
    assertThat(after.names()).isEqualTo(s.names());
    assertThat(after.viewport()).isEqualTo(s.viewport());
  }

  @Test
  void objectsAddedInTurnTakeTheLeastUsedSeriesColour() {
    // a slider is not drawn, so the first graph beside it takes the first colour
    DocumentState s = run(DocumentState.EMPTY, slider("a"));
    s = run(s, Command.AddObject.inTurn(id -> new FreePoint(id, Style.DEFAULT, 1, 0)));
    s = run(s, Command.AddObject.named(id -> new FreePoint(id, Style.DEFAULT, 0, 0)));
    s = run(s, Command.AddObject.inTurn(id -> new FreePoint(id, Style.DEFAULT, 2, 0)));
    assertThat(s.canvas().objects().stream().map(o -> o.style().colour()))
        .containsExactly(0, 0, 1, 2);
    assertThat(s.names().values()).containsExactly("A");

    // a deleted object's colour is the first taken again
    ObjectId second = s.canvas().objects().get(2).id();
    s = run(s, new Command.RemoveObject(second));
    s = run(s, Command.AddObject.inTurn(id -> new FreePoint(id, Style.DEFAULT, 3, 0)));
    assertThat(s.canvas().objects().getLast().style().colour()).isEqualTo(1);

    // with every colour used once, the next wraps to the first
    for (int k = 3; k < Style.COLOURS; k++) {
      s = run(s, Command.AddObject.inTurn(id -> new FreePoint(id, Style.DEFAULT, 0, 0)));
    }
    assertThat(s.canvas().objects().stream().map(o -> o.style().colour()).skip(1).sorted())
        .containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
    s = run(s, Command.AddObject.inTurn(id -> new FreePoint(id, Style.DEFAULT, 0, 0)));
    assertThat(s.canvas().objects().getLast().style().colour()).isZero();

    // an undo and a redo give the same colour
    AtomicLong now = new AtomicLong();
    CommandStack stack = new CommandStack(s, now::get);
    stack.execute(Command.AddObject.named(id -> new FreePoint(id, Style.DEFAULT, 0, 0)));
    int colour = stack.current().canvas().objects().getLast().style().colour();
    assertThat(colour).isEqualTo(1);
    stack.undo();
    stack.redo();
    assertThat(stack.current().canvas().objects().getLast().style().colour()).isEqualTo(colour);
  }
}
