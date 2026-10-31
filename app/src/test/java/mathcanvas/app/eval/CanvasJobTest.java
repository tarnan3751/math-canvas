package mathcanvas.app.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import mathcanvas.app.document.Cell;
import mathcanvas.app.document.Command;
import mathcanvas.app.document.DocumentState;
import mathcanvas.app.document.Output;
import mathcanvas.app.document.Result;
import mathcanvas.canvas.model.CriticalPoint;
import mathcanvas.canvas.model.Feature;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParamKind;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.Polyline;
import mathcanvas.canvas.model.RiemannSum;
import mathcanvas.canvas.model.ShadedArea;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Style;
import mathcanvas.engine.compile.Quadrature;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

/** The canvas recomputed on the worker: the generation rule, cancellation, start-up, cycles. */
class CanvasJobTest {

  /** An executor that queues its tasks until the test runs them. */
  private static final class Queue implements Executor {
    final Deque<Runnable> tasks = new ArrayDeque<>();

    @Override
    public void execute(Runnable task) {
      tasks.add(task);
    }

    void runOne() {
      tasks.poll().run();
    }

    void runAll() {
      while (!tasks.isEmpty()) {
        tasks.poll().run();
      }
    }
  }

  private static final ObjectId ONE = new ObjectId(1);
  private static final ObjectId TWO = new ObjectId(2);

  private final Queue worker = new Queue();
  private final Queue ui = new Queue();
  private final TestSampling sampling = new TestSampling();
  private long now;
  private Document doc = document(DocumentState.EMPTY);

  private Document document(DocumentState state) {
    return new Document(state, worker, ui, () -> now, sampling, CanvasJob.Content.NONE);
  }

  private void settle() {
    while (!worker.tasks.isEmpty() || !ui.tasks.isEmpty()) {
      worker.runAll();
      ui.runAll();
    }
  }

  private void enter(String text) {
    long id = doc.state().cells().getLast().id();
    doc.execute(new Command.EditInput(id, text));
    doc.execute(new Command.Submit(id));
  }

  private static Command.AddObject graph(String body) {
    return new Command.AddObject(
        id ->
            new FunctionGraph(
                id,
                Style.DEFAULT,
                Parser.parseExpression(body, FUNCTIONS),
                Symbol.of("x"),
                FunctionGraph.ALL));
  }

  /** The functions the tests' cells define, so a graph parses {@code f(x)} as a call. */
  private static final Names FUNCTIONS = "f"::equals;

  private static FunctionGraph graph(ObjectId id, String body) {
    return new FunctionGraph(
        id,
        Style.DEFAULT,
        Parser.parseExpression(body, FUNCTIONS),
        Symbol.of("x"),
        FunctionGraph.ALL);
  }

  /** The graph's ordinate at the sample whose parameter is {@code x}, NaN when it has none. */
  private double yAt(ObjectId id, double x) {
    if (!(doc.state().canvas().geometryOf(id) instanceof Geometry.Curve c)) {
      return Double.NaN;
    }
    for (Polyline p : c.branches()) {
      for (int k = 0; k < p.size(); k++) {
        if (p.t(k) == x) {
          return p.y(k);
        }
      }
    }
    return Double.NaN;
  }

  private String shown(int cell) {
    Cell c = doc.state().cells().get(cell);
    if (!(c.output() instanceof Output.Done d)) {
      return c.output().getClass().getSimpleName();
    }
    return switch (d.results().getLast()) {
      case Result.Value v -> v.text();
      case Result.Defined x -> x.text();
      case Result.Failed f -> f.message();
    };
  }

  private ObjectId graphId() {
    return doc.state().canvas().objects().stream()
        .filter(o -> o instanceof FunctionGraph)
        .findFirst()
        .orElseThrow()
        .id();
  }

  private Slider slider() {
    return (Slider)
        doc.state().canvas().objects().stream()
            .filter(o -> o instanceof Slider)
            .findFirst()
            .orElseThrow();
  }

  @Test
  void aGraphReadingASliderFollowsItsDrag() {
    enter("a := slider(0, 5, 0.5)");
    doc.execute(graph("a x^2"));
    settle();
    ObjectId g = graphId();
    assertThat(yAt(g, 1)).isEqualTo(0);

    doc.execute(new Command.SetSlider(slider().id(), new BigDecimal(2)));
    settle();

    assertThat(yAt(g, 1)).isEqualTo(2);
    assertThat(yAt(g, 2)).isEqualTo(8);
    assertThat(doc.canvasFrameProperty().get().rows().get(g).text()).isEqualTo("y = a x²");
    assertThat(doc.canvasFrameProperty().get().rows().get(slider().id()).text()).isEqualTo("a = 2");
  }

  @Test
  void theFrameCarriesEachGraphsSlopeAndKeepsOneItDidNotRecompute() {
    enter("a := slider(0, 5, 0.5)");
    doc.execute(graph("a x^2"));
    doc.execute(graph("x^3"));
    settle();
    List<ObjectId> graphs =
        doc.state().canvas().objects().stream()
            .filter(o -> o instanceof FunctionGraph)
            .map(o -> o.id())
            .toList();
    CanvasFrame before = doc.canvasFrameProperty().get();
    assertThat(before.slope(graphs.get(0), 1)).isEqualTo(0);
    assertThat(before.slope(graphs.get(1), 2)).isEqualTo(12);
    assertThat(before.slope(slider().id(), 2)).isNaN();

    doc.execute(new Command.SetSlider(slider().id(), new BigDecimal(2)));
    settle();

    CanvasFrame after = doc.canvasFrameProperty().get();
    assertThat(after.slope(graphs.get(0), 1)).isEqualTo(4);
    FunctionGraph cube = (FunctionGraph) doc.state().canvas().object(graphs.get(1)).orElseThrow();
    assertThat(after.slopes().get(cube)).isSameAs(before.slopes().get(cube));
  }

  @Test
  void theDocumentIsBusyWhileACanvasRecomputeRunsAndStopEndsIt() {
    assertThat(doc.busyProperty().get()).isFalse();
    doc.execute(graph("x^2"));
    assertThat(doc.busyProperty().get()).isTrue();
    settle();
    assertThat(doc.busyProperty().get()).isFalse();

    doc.execute(new Command.ReplaceObject(graph(ONE, "x^3")));
    assertThat(doc.busyProperty().get()).isTrue();
    doc.stop();
    settle();
    assertThat(doc.busyProperty().get()).isFalse();

    // a worksheet with no canvas objects runs no canvas job
    doc.execute(new Command.RemoveObject(ONE));
    enter("1 + 1");
    settle();
    int bound = sampling.bound.get();
    enter("2 + 2");
    assertThat(worker.tasks).hasSize(1);
    settle();
    assertThat(sampling.bound.get()).isEqualTo(bound);
    assertThat(doc.canvasFrameProperty().get().state().objects()).isEmpty();
  }

  @Test
  void anObjectWhoseFirstRecomputeWasStoppedIsHeldUntilItOrWhatItReadsChanges() {
    enter("a := slider(0, 5, 1)");
    settle();
    doc.execute(graph("a x"));
    ObjectId g = graphId();
    // the job is queued; Escape before it runs
    doc.stop();
    settle();
    assertThat(doc.state().canvas().geometry()).doesNotContainKey(g);
    int jobs = sampling.bound.get();

    // unrelated changes leave it held: a keystroke, a finished evaluation, a pan
    doc.execute(new Command.EditInput(doc.state().cells().getLast().id(), "1 + 1"));
    enter("2 + 2");
    doc.setViewport(DocumentState.DEFAULT_VIEWPORT.pan(80, 0));
    settle();
    assertThat(doc.state().canvas().geometry()).doesNotContainKey(g);

    // a variable it reads resumes it
    doc.execute(new Command.SetSlider(slider().id(), new BigDecimal(2)));
    settle();
    assertThat(yAt(g, 1)).isEqualTo(2);
    assertThat(sampling.bound.get()).isGreaterThan(jobs);

    // and so does its own change, after another stop
    doc.execute(new Command.ReplaceObject(graph(g, "a x + 1")));
    doc.stop();
    settle();
    assertThat(yAt(g, 1)).isEqualTo(2);
    doc.execute(new Command.ReplaceObject(graph(g, "a x + 2")));
    settle();
    assertThat(yAt(g, 1)).isEqualTo(4);
  }

  @Test
  void theCanvasSamplerAndContentDrawAGraphReadingASlider() {
    doc =
        new Document(
            DocumentState.EMPTY,
            worker,
            ui,
            () -> now,
            CanvasJob.Sampling.of(new mathcanvas.canvas.sample.Samplers()),
            CanvasJob.Content.STANDARD);
    enter("a := slider(0, 5, 1)");
    settle();
    doc.execute(new Command.SetSlider(slider().id(), new BigDecimal(2)));
    doc.execute(graph("a x^2"));
    settle();

    Geometry.Curve c = (Geometry.Curve) doc.state().canvas().geometryOf(graphId());
    assertThat(c.branches()).isNotEmpty();
    for (Polyline p : c.branches()) {
      for (int k = 0; k < p.size(); k++) {
        assertThat(p.y(k))
            .isCloseTo(2 * p.x(k) * p.x(k), org.assertj.core.data.Offset.offset(1e-9));
      }
    }
    CanvasFrame frame = doc.canvasFrameProperty().get();
    assertThat(frame.content().curves()).isNotEmpty();
    assertThat(frame.items()).containsValue(graphId());
  }

  @Test
  void aResultAppliesOnlyToObjectsWhoseDefinitionDidNotChangeMeanwhile() {
    doc.execute(graph("x^2"));
    settle();
    assertThat(yAt(ONE, 2)).isEqualTo(4);

    doc.execute(new Command.ReplaceObject(graph(ONE, "x^3")));
    // the job for x³ is queued; the definition changes again before it runs
    doc.execute(new Command.ReplaceObject(graph(ONE, "x^4")));
    assertThat(worker.tasks).hasSize(1);
    worker.runOne();
    ui.runAll();

    assertThat(yAt(ONE, 2)).isEqualTo(4);
    settle();
    assertThat(yAt(ONE, 2)).isEqualTo(16);
  }

  @Test
  void aResultForAVariableThatMovedMeanwhileIsShownAndRecomputed() {
    enter("a := slider(0, 5, 1)");
    doc.execute(graph("a x"));
    settle();
    ObjectId g = graphId();

    doc.execute(new Command.SetSlider(slider().id(), BigDecimal.ONE));
    now += 100_000_000L;
    while (!worker.tasks.isEmpty()) {
      worker.runOne();
      if (!ui.tasks.isEmpty()) {
        break;
      }
    }
    // a second value arrives while the first is being recomputed
    doc.execute(new Command.SetSlider(slider().id(), new BigDecimal(3)));
    ui.runAll();
    assertThat(yAt(g, 1)).isEqualTo(1);
    settle();
    assertThat(yAt(g, 1)).isEqualTo(3);
  }

  @Test
  void aStoppedRecomputeLeavesTheOldGeometry() {
    doc.execute(graph("x^2"));
    settle();
    int bound = sampling.bound.get();

    doc.execute(new Command.ReplaceObject(graph(ONE, "x^3")));
    doc.stop();
    settle();

    assertThat(yAt(ONE, 2)).isEqualTo(4);
    assertThat(sampling.bound.get()).isEqualTo(bound + 1);
    // the next change recomputes
    doc.execute(new Command.ReplaceObject(graph(ONE, "x + 1")));
    settle();
    assertThat(yAt(ONE, 2)).isEqualTo(3);
  }

  @Test
  void atStartTheWholeCanvasIsRecomputedOnceAfterTheCells() {
    DocumentState loaded =
        new Command.Submit(1)
            .apply(
                new Command.EditInput(1, "f(x) := 3x")
                    .apply(graph("f(x)").apply(DocumentState.EMPTY)));
    doc = document(loaded);

    doc.start();
    settle();

    assertThat(sampling.bound.get()).isEqualTo(1);
    assertThat(yAt(ONE, 2)).isEqualTo(6);
  }

  @Test
  void aChangedDefinitionRecomputesTheObjectsThatReadIt() {
    enter("k := 2");
    enter("f(x) := k x");
    doc.execute(graph("f(x)"));
    settle();
    ObjectId g = graphId();
    assertThat(yAt(g, 1)).isEqualTo(2);

    long first = doc.state().cells().getFirst().id();
    doc.execute(new Command.EditInput(first, "k := 5"));
    doc.execute(new Command.Submit(first));
    settle();

    assertThat(yAt(g, 1)).isEqualTo(5);
  }

  @Test
  void aPointOnAGraphFollowsTheSliderAndItsReadersFollowThePoint() {
    enter("a := slider(0, 5, 1)");
    doc.execute(graph("a x"));
    settle();
    ObjectId g = graphId();
    doc.execute(
        new Command.AddObject(
            id -> new PointOnPath(id, Style.DEFAULT, g, ParamKind.X, 2, Double.NaN, Double.NaN),
            Optional.of("P")));
    enter("nth(P, 2)");
    settle();
    assertThat(shown(1)).isEqualTo("0.0");

    doc.execute(new Command.SetSlider(slider().id(), new BigDecimal(3)));
    settle();

    ObjectId p = doc.state().names().keySet().iterator().next();
    assertThat(doc.state().canvas().geometryOf(p)).isEqualTo(new Geometry.Point(2, 6));
    assertThat(shown(1)).isEqualTo("6.0");
    assertThat(doc.canvasFrameProperty().get().rows().get(p).text()).isEqualTo("P = ⟨2.0, 6.0⟩");
  }

  @Test
  void anObjectThatDependsOnItselfThroughTheWorksheetIsUndefined() {
    doc.execute(graph("f(x)"));
    doc.execute(
        new Command.AddObject(
            id -> new PointOnPath(id, Style.DEFAULT, ONE, ParamKind.X, 1, Double.NaN, Double.NaN),
            Optional.of("P")));
    enter("f(x) := nth(P, 2) + x");
    settle();

    assertThat(doc.state().canvas().geometryOf(ONE)).isEqualTo(Geometry.UNDEFINED);
    assertThat(doc.state().canvas().geometryOf(TWO)).isEqualTo(Geometry.UNDEFINED);
    assertThat(doc.canvasFrameProperty().get().rows().get(ONE).error())
        .hasValueSatisfying(m -> assertThat(m).contains("P"));
    assertThat(worker.tasks).isEmpty();
  }

  @Test
  void aNewViewportResamplesTheGraphsOverIt() {
    doc.execute(graph("x"));
    doc.execute(new Command.AddObject(id -> new FreePoint(id, Style.DEFAULT, 1, 1)));
    settle();
    assertThat(yAt(ONE, 100)).isNaN();

    doc.setViewport(DocumentState.DEFAULT_VIEWPORT.pan(-40 * 100, 0));
    settle();

    assertThat(yAt(ONE, 100)).isEqualTo(100);
    assertThat(doc.state().canvas().geometryOf(TWO)).isEqualTo(new Geometry.Point(1, 1));
    assertThat(doc.canUndoProperty().get()).isTrue();
    assertThat(List.of(doc.state().viewport().xMin())).allMatch(x -> x > 90);
  }

  @Test
  void stoppingTheStartUpEvaluationStillDrawsTheLoadedCanvas() {
    DocumentState loaded =
        new Command.Submit(1)
            .apply(
                new Command.EditInput(1, "f(x) := 3x")
                    .apply(graph("f(x)").apply(DocumentState.EMPTY)));
    doc = document(loaded);

    doc.start();
    doc.stop();
    settle();

    assertThat(doc.state().canvas().geometryOf(ONE)).isInstanceOf(Geometry.Curve.class);
  }

  @Test
  void aStoppedFirstRecomputeWaitsForItsOwnChange() {
    doc.execute(graph("x^2"));
    doc.stop();
    settle();
    assertThat(doc.state().canvas().geometry()).isEmpty();
    assertThat(worker.tasks).isEmpty();

    // another object's change leaves it held
    doc.execute(graph("x + 1"));
    settle();
    assertThat(yAt(ONE, 2)).isNaN();
    assertThat(yAt(TWO, 2)).isEqualTo(3);

    doc.execute(new Command.ReplaceObject(graph(ONE, "x^2 + 0")));
    settle();
    assertThat(yAt(ONE, 2)).isEqualTo(4);
  }

  @Test
  void aDraggedSliderMovesATrackedIntersectionAndTheJobsSettleWhenIdle() {
    enter("a := slider(0, 5, 0.5)");
    settle();
    doc.execute(graph("a x^2"));
    ObjectId g = graphId();
    doc.execute(new Command.AddObject(id -> new FreePoint(id, Style.DEFAULT, -10, 1)));
    doc.execute(new Command.AddObject(id -> new FreePoint(id, Style.DEFAULT, 10, 1)));
    ObjectId p = new ObjectId(3);
    ObjectId q = new ObjectId(4);
    doc.execute(
        new Command.AddObject(
            id ->
                new mathcanvas.canvas.model.Line(
                    id, Style.DEFAULT, new mathcanvas.canvas.model.Line.Through(p, q))));
    ObjectId line = new ObjectId(5);
    doc.execute(
        new Command.AddObject(
            id ->
                new mathcanvas.canvas.model.Intersection(
                    id, Style.DEFAULT, g, line, 1, Optional.empty())));
    ObjectId meet = new ObjectId(6);
    doc.execute(new Command.SetSlider(slider().id(), BigDecimal.ONE));
    settle();
    assertThat(doc.state().canvas().geometryOf(meet)).isEqualTo(new Geometry.Point(1, 1));

    // a drag: each frame's job runs while the next value arrives
    for (String v : List.of("1.5", "2", "2.5", "3", "4")) {
      now += 50_000_000L;
      doc.execute(new Command.SetSlider(slider().id(), new BigDecimal(v)));
      if (!worker.tasks.isEmpty()) {
        worker.runOne();
      }
    }
    settle();

    // the last value's geometry was applied, anchor and all
    Geometry.Point at = (Geometry.Point) doc.state().canvas().geometryOf(meet);
    assertThat(at.x()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-9));
    assertThat(at.y()).isCloseTo(1, org.assertj.core.data.Offset.offset(1e-9));
    assertThat(
            ((mathcanvas.canvas.model.Intersection) doc.state().canvas().object(meet).orElseThrow())
                .anchor())
        .hasValueSatisfying(
            anchor ->
                assertThat(anchor.x()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-9)));
    assertThat(doc.canvasFrameProperty().get().state()).isEqualTo(doc.state().canvas());

    // idle: no job runs once nothing changes, the job's own anchors re-dirty nothing
    int jobs = sampling.bound.get();
    settle();
    doc.execute(new Command.EditInput(doc.state().cells().getLast().id(), "typing"));
    settle();
    assertThat(worker.tasks).isEmpty();
    assertThat(sampling.bound.get()).isEqualTo(jobs);
    assertThat(doc.busyProperty().get()).isFalse();
  }

  @Test
  void anEntryInThePanelAddsAndRedefinesAnObject() {
    List<String> errors = new java.util.ArrayList<>();
    doc.submitEntry(Optional.empty(), "y = x^2", errors::add);
    settle();
    assertThat(yAt(ONE, 2)).isEqualTo(4);

    doc.submitEntry(Optional.of(ONE), "y = x^3", errors::add);
    settle();
    assertThat(yAt(ONE, 2)).isEqualTo(8);
    assertThat(doc.canvasFrameProperty().get().rows().get(ONE).text()).isEqualTo("y = x³");

    doc.submitEntry(Optional.of(ONE), "y = ", errors::add);
    settle();
    assertThat(errors).hasSize(1);
    assertThat(yAt(ONE, 2)).isEqualTo(8);

    // the object changes before the entry is read
    doc.submitEntry(Optional.of(ONE), "y = x", errors::add);
    doc.execute(new Command.ReplaceObject(graph(ONE, "x + 10")));
    settle();
    assertThat(errors).hasSize(2).last().isEqualTo("the object changed meanwhile; edit it again");
    assertThat(yAt(ONE, 2)).isEqualTo(12);

    doc.undo();
    settle();
    assertThat(yAt(ONE, 2)).isEqualTo(8);
  }

  @Test
  void stopStopsAnEntryBeingRead() {
    List<String> errors = new java.util.ArrayList<>();
    doc.submitEntry(Optional.empty(), "[expand((x + y + z + 1)^70), 0]", errors::add);

    doc.stop();
    settle();

    assertThat(doc.state().canvas().objects()).isEmpty();
    assertThat(errors).isEmpty();
  }

  @Test
  void twoQuickPointEntriesGetTwoNames() {
    List<String> errors = new java.util.ArrayList<>();
    doc.submitEntry(Optional.empty(), "[1, 2]", errors::add);
    doc.submitEntry(Optional.empty(), "[3, 4]", errors::add);
    settle();

    assertThat(errors).isEmpty();
    assertThat(doc.state().names()).containsValues("A", "B");
  }

  @Test
  void submittingAHeldObjectsRowAgainResumesIt() {
    List<String> errors = new java.util.ArrayList<>();
    doc.submitEntry(Optional.empty(), "y = x^2", errors::add);
    worker.runOne();
    ui.runAll();
    doc.stop();
    settle();
    assertThat(yAt(ONE, 2)).isNaN();

    doc.submitEntry(Optional.of(ONE), "y = x^2", errors::add);
    settle();

    assertThat(errors).isEmpty();
    assertThat(yAt(ONE, 2)).isEqualTo(4);
  }

  @Test
  void theCoordinateEntryMovesAPointAlongItsPath() {
    List<String> errors = new java.util.ArrayList<>();
    doc.execute(graph("x^2"));
    settle();
    doc.execute(
        Command.AddObject.named(
            id -> new PointOnPath(id, Style.DEFAULT, ONE, ParamKind.X, 1, 1, 1)));
    settle();
    ObjectId point = new ObjectId(2);

    doc.submitEntry(Optional.of(point), "x = 3/2", errors::add);
    settle();

    assertThat(errors).isEmpty();
    PointOnPath p = (PointOnPath) doc.state().canvas().object(point).orElseThrow();
    assertThat(p.parameter()).isEqualTo(1.5);
    assertThat(doc.state().canvas().geometryOf(point)).isEqualTo(new Geometry.Point(1.5, 2.25));

    doc.submitEntry(Optional.of(point), "b", errors::add);
    settle();
    assertThat(errors).containsExactly("a point on a path takes its parameter, a real number");

    // the path changes before the entry is read
    doc.submitEntry(Optional.of(point), "2", errors::add);
    doc.execute(new Command.ReplaceObject(graph(ONE, "x^3")));
    settle();
    assertThat(errors).hasSize(2).last().isEqualTo("the object changed meanwhile; edit it again");
    assertThat(((PointOnPath) doc.state().canvas().object(point).orElseThrow()).parameter())
        .isEqualTo(1.5);
  }

  private Geometry.Point pointOf(ObjectId id) {
    return (Geometry.Point) doc.state().canvas().geometryOf(id);
  }

  @Test
  void theIntersectionsFoundAreAddedOncePerPlace() {
    List<String> refusals = new java.util.ArrayList<>();
    List<ObjectId> added = new java.util.ArrayList<>();
    doc.execute(graph("x^2"));
    doc.execute(graph("x + 2"));
    doc.execute(graph("x^2 + 5"));
    settle();

    doc.addIntersections(ONE, new ObjectId(2), added::add, refusals::add);
    settle();

    assertThat(refusals).isEmpty();
    assertThat(doc.state().canvas().objects()).hasSize(5);
    assertThat(pointOf(new ObjectId(4)).x()).isCloseTo(-1, within(1e-9));
    assertThat(pointOf(new ObjectId(5)).x()).isCloseTo(2, within(1e-9));
    assertThat(doc.state().names()).containsValues("A", "B");
    assertThat(added).containsExactly(new ObjectId(5)); // the last one added

    doc.addIntersections(ONE, new ObjectId(2), added::add, refusals::add);
    doc.addIntersections(ONE, new ObjectId(3), added::add, refusals::add);
    settle();
    assertThat(refusals)
        .containsExactly(
            "every one found is already on the canvas", "the curves do not meet in view");

    doc.undo();
    assertThat(doc.state().canvas().objects()).hasSize(3);
  }

  @Test
  void aGraphsRootsAndExtremaAreAddedAsCriticalPoints() {
    List<String> refusals = new java.util.ArrayList<>();
    List<ObjectId> added = new java.util.ArrayList<>();
    doc.execute(graph("x^2 - 1"));
    doc.execute(graph("x^2 + 5"));
    settle();

    doc.addFeatures(ONE, List.of(Feature.ROOT), added::add, refusals::add);
    doc.addFeatures(ONE, List.of(Feature.MIN, Feature.MAX), added::add, refusals::add);
    doc.addFeatures(new ObjectId(2), List.of(Feature.ROOT), added::add, refusals::add);
    settle();

    assertThat(refusals).containsExactly("the graph has no roots in view");
    assertThat(doc.state().canvas().objects())
        .filteredOn(o -> o instanceof CriticalPoint)
        .extracting(o -> ((CriticalPoint) o).feature())
        .containsExactly(Feature.ROOT, Feature.ROOT, Feature.MIN);
    assertThat(pointOf(new ObjectId(3)).x()).isCloseTo(-1, within(1e-9));
    assertThat(pointOf(new ObjectId(4)).x()).isCloseTo(1, within(1e-9));
    assertThat(pointOf(new ObjectId(5)).y()).isCloseTo(-1, within(1e-9));
    // each family in one colour, the least-used one when it is added (the graphs have 0)
    List<Integer> colours =
        doc.state().canvas().objects().stream().map(o -> o.style().colour()).toList();
    assertThat(colours.subList(2, 5)).containsExactly(1, 1, 2);
  }

  @Test
  void aSearchIsRefusedWhenItsPathChangedAndStoppedByStop() {
    List<String> refusals = new java.util.ArrayList<>();
    List<ObjectId> added = new java.util.ArrayList<>();
    doc.execute(graph("x^2 - 1"));
    settle();

    doc.addFeatures(ONE, List.of(Feature.ROOT), added::add, refusals::add);
    doc.execute(new Command.ReplaceObject(graph(ONE, "x^2 - 4")));
    settle();
    assertThat(refusals).containsExactly("the object changed meanwhile; edit it again");

    doc.addFeatures(ONE, List.of(Feature.ROOT), added::add, refusals::add);
    doc.stop();
    settle();
    assertThat(refusals).hasSize(1);
    assertThat(doc.state().canvas().objects()).hasSize(1);
  }

  @Test
  void onlyThePlacesInsideTheVisibleWindowAreAdded() {
    List<String> refusals = new java.util.ArrayList<>();
    List<ObjectId> added = new java.util.ArrayList<>();
    // the window is 16 wide about the origin: the roots at ±10 lie outside it, those at ±1 inside
    doc.execute(graph("x^2 - 100"));
    doc.execute(graph("(x^2 - 1) (x^2 - 100)"));
    settle();

    doc.addFeatures(ONE, List.of(Feature.ROOT), added::add, refusals::add);
    doc.addFeatures(new ObjectId(2), List.of(Feature.ROOT), added::add, refusals::add);
    settle();

    assertThat(refusals).containsExactly("the graph has no roots in view");
    assertThat(doc.state().canvas().objects()).hasSize(4);
    assertThat(pointOf(new ObjectId(3)).x()).isCloseTo(-1, within(1e-9));
    assertThat(pointOf(new ObjectId(4)).x()).isCloseTo(1, within(1e-9));
  }

  @Test
  void aSearchFindingMoreThanFiftyPlacesInViewIsRefused() {
    List<String> refusals = new java.util.ArrayList<>();
    List<ObjectId> added = new java.util.ArrayList<>();
    // the real sampler: sin(20x) has 101 roots in the window, 16 wide about the origin
    doc =
        new Document(
            DocumentState.EMPTY,
            worker,
            ui,
            () -> now,
            CanvasJob.Sampling.of(new mathcanvas.canvas.sample.Samplers()),
            CanvasJob.Content.NONE);
    doc.execute(graph("sin(20 x)"));
    doc.execute(graph("sin(x)"));
    settle();

    doc.addFeatures(ONE, List.of(Feature.ROOT), added::add, refusals::add);
    doc.addFeatures(new ObjectId(2), List.of(Feature.ROOT), added::add, refusals::add);
    settle();

    assertThat(refusals).containsExactly("more than 50 places in view; zoom in");
    assertThat(doc.state().canvas().objects()).hasSize(2 + 5); // sin x: -2π, -π, 0, π, 2π
  }

  @Test
  void anAreaAndARiemannSumShowTheirValuesAndWhyNot() {
    doc.execute(graph("x^2"));
    doc.execute(graph("x"));
    doc.execute(
        new Command.AddObject(
            id ->
                new ShadedArea(
                    id,
                    Style.DEFAULT,
                    ONE,
                    Optional.empty(),
                    Parser.parseExpression("0", FUNCTIONS),
                    Parser.parseExpression("1", FUNCTIONS))));
    doc.execute(
        new Command.AddObject(
            id ->
                new RiemannSum(
                    id,
                    Style.DEFAULT,
                    TWO,
                    Parser.parseExpression("0", FUNCTIONS),
                    Parser.parseExpression("1", FUNCTIONS),
                    Parser.parseExpression("4", FUNCTIONS),
                    Quadrature.Method.LEFT)));
    doc.execute(
        new Command.AddObject(
            id ->
                new ShadedArea(
                    id,
                    Style.DEFAULT,
                    ONE,
                    Optional.of(TWO),
                    Parser.parseExpression("1", FUNCTIONS),
                    Parser.parseExpression("0", FUNCTIONS))));
    settle();
    var rows = doc.canvasFrameProperty().get().rows();
    assertThat(rows.get(new ObjectId(3)).text()).isEqualTo("A = 0.3333333333");
    assertThat(rows.get(new ObjectId(4)).text()).isEqualTo("S = 0.375 (left, n = 4)");
    assertThat(rows.get(new ObjectId(5)).text()).isEqualTo("A = undefined");
    assertThat(rows.get(new ObjectId(5)).error())
        .contains("the bounds must be numbers a < b inside the graph's domain");
    // the method changes through the command stack, one step
    RiemannSum sum = (RiemannSum) doc.state().canvas().object(new ObjectId(4)).orElseThrow();
    doc.execute(new Command.ReplaceObject(sum.withMethod(Quadrature.Method.MIDPOINT)));
    settle();
    assertThat(doc.canvasFrameProperty().get().rows().get(new ObjectId(4)).text())
        .isEqualTo("S = 0.5 (midpoint, n = 4)");
    doc.undo();
    settle();
    assertThat(doc.canvasFrameProperty().get().rows().get(new ObjectId(4)).text())
        .isEqualTo("S = 0.375 (left, n = 4)");
  }
}
