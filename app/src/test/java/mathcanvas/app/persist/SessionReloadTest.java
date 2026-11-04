package mathcanvas.app.persist;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;
import mathcanvas.app.document.Command;
import mathcanvas.app.document.DocumentState;
import mathcanvas.app.eval.CanvasJob;
import mathcanvas.app.eval.Document;
import mathcanvas.app.json.JsonIo;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.Intersection;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.sample.Samplers;
import mathcanvas.engine.expr.Symbol;
import org.junit.jupiter.api.Test;

/**
 * A session saved and loaded into a new document: the cells evaluate, every object is recomputed on
 * the worker from what the file kept, and the geometry is what it was before the save.
 */
class SessionReloadTest {

  /** An executor that queues its tasks until the test runs them. */
  private static final class Queue implements Executor {
    final Deque<Runnable> tasks = new ArrayDeque<>();

    @Override
    public void execute(Runnable task) {
      tasks.add(task);
    }
  }

  private final Queue worker = new Queue();
  private final Queue ui = new Queue();

  private Document document(DocumentState state) {
    return new Document(
        state,
        worker,
        ui,
        () -> 0,
        CanvasJob.Sampling.of(new Samplers()),
        CanvasJob.Content.STANDARD);
  }

  private void settle() {
    while (!worker.tasks.isEmpty() || !ui.tasks.isEmpty()) {
      while (!worker.tasks.isEmpty()) {
        worker.tasks.poll().run();
      }
      while (!ui.tasks.isEmpty()) {
        ui.tasks.poll().run();
      }
    }
  }

  private static <T extends CanvasObject> T only(Document doc, Class<T> kind) {
    return doc.state().canvas().objects().stream()
        .filter(kind::isInstance)
        .map(kind::cast)
        .findFirst()
        .orElseThrow();
  }

  private static Command.AddObject graph(String body) {
    return new Command.AddObject(
        id ->
            new FunctionGraph(
                id, Style.DEFAULT, CanvasStates.expr(body), Symbol.of("x"), FunctionGraph.ALL));
  }

  @Test
  void aVersion4SessionWithNamesThatBecameKeywordsOpensAndEvaluates() throws Exception {
    SessionFile.Stored stored = SessionFile.decode(Fixtures.read("session-v4-keywords.json"));
    Document doc = document(stored.state(DocumentState.EMPTY.settings()));
    doc.start();
    settle();

    assertThat(doc.state().cells().get(3).input())
        .isEqualTo("v_print(v_end1) + v_step + v_global + endpoint + do2");
    for (var cell : doc.state().cells()) {
      if (cell.output() instanceof mathcanvas.app.document.Output.Done done) {
        assertThat(done.results())
            .as(cell.input())
            .noneMatch(r -> r instanceof mathcanvas.app.document.Result.Failed);
      }
    }
    var sum = (mathcanvas.app.document.Output.Done) doc.state().cells().get(3).output();
    assertThat(sum.results().getFirst())
        .isInstanceOfSatisfying(
            mathcanvas.app.document.Result.Value.class,
            r -> assertThat(r.text()).isEqualTo("do2 + endpoint + v_global + 10"));
    // a derivative and an integral in a renamed name keep their differential
    String[][] values = {
      {"d/dv_do (v_do^3)", "3v_do²"},
      {"d²/dv_do² (v_do^3)", "6v_do"},
      {"∫ v_global^2 dv_global", "v_global³/3"}
    };
    for (String[] v : values) {
      var cell =
          doc.state().cells().stream()
              .filter(c -> c.input().equals(v[0]))
              .findFirst()
              .orElseThrow();
      assertThat(((mathcanvas.app.document.Output.Done) cell.output()).results().getFirst())
          .as(v[0])
          .isInstanceOfSatisfying(
              mathcanvas.app.document.Result.Value.class,
              r -> assertThat(r.text()).isEqualTo(v[1]));
    }
  }

  @Test
  void aVersion3SessionWithNamesBeginningWithAnUnderscoreOpensAndEvaluates() throws Exception {
    SessionFile.Stored stored = SessionFile.decode(Fixtures.read("session-v3-underscore.json"));
    Document doc = document(stored.state(DocumentState.EMPTY.settings()));
    doc.start();
    settle();

    assertThat(doc.state().cells().getFirst().input()).isEqualTo("v_kq1 := slider(0, 1, 0.1)");
    // 2_kq was the product 2·_kq; 2v_kq1 reads as 2·v_kq1
    assertThat(doc.state().cells().get(3).input()).isEqualTo("2v_kq1 + x2_kq");
    for (var cell : doc.state().cells()) {
      if (cell.output() instanceof mathcanvas.app.document.Output.Done done) {
        assertThat(done.results())
            .as(cell.input())
            .noneMatch(r -> r instanceof mathcanvas.app.document.Result.Failed);
      }
    }
    assertThat(
            doc.state().cells().stream()
                .filter(c -> c.output() instanceof mathcanvas.app.document.Output.Done)
                .count())
        .isEqualTo(22);
    // every name that began with an underscore: __b, _1 and the slider __c
    assertThat(doc.state().cells().get(8).input()).isEqualTo("v__b + v_1 + v__c");
    // only the name is renamed: what the old lexer did not read as part of it is kept
    String[][] values = {
      {"v_t²", "9"},
      {"v_tπ", "3π"},
      {"v_π", "2π"},
      {"v_²", "4"},
      {"v_a_2 + 1", "8"},
      // the differential of an old name is renamed with it; d_y on its own is not
      {"∫ v_y^2 dv_y", "v_y³/3"},
      {"d/dv_y (v_y^2)", "2v_y"},
      {"d²/dv_y² (v_y^3)", "6v_y"},
      // a division by the name d_y is no derivative's head
      {"6/d_y", "2"}
    };
    for (String[] v : values) {
      var cell =
          doc.state().cells().stream()
              .filter(c -> c.input().equals(v[0]))
              .findFirst()
              .orElseThrow();
      assertThat(((mathcanvas.app.document.Output.Done) cell.output()).results().getFirst())
          .as(v[0])
          .isInstanceOfSatisfying(
              mathcanvas.app.document.Result.Value.class,
              r -> assertThat(r.text()).isEqualTo(v[1]));
    }
    assertThat(doc.state().canvas().objects())
        .filteredOn(o -> o instanceof Slider s && s.variable().equals("v__c"))
        .hasSize(1);
    assertThat(only(doc, Slider.class).variable()).isEqualTo("v_kq1");
    FunctionGraph graph = only(doc, FunctionGraph.class);
    assertThat(doc.state().canvas().geometryOf(graph.id())).isNotEqualTo(Geometry.UNDEFINED);
    doc.stop();
  }

  @Test
  void aSliderAndTheGraphsThatReadItReloadToTheSameGeometry() throws Exception {
    Document doc = document(DocumentState.EMPTY);
    doc.start();
    long cell = doc.state().cells().getLast().id();
    doc.execute(new Command.EditInput(cell, "a := slider(0, 5, 0.5)"));
    doc.execute(new Command.Submit(cell));
    settle();
    doc.execute(new Command.SetSlider(only(doc, Slider.class).id(), new BigDecimal("1.5")));
    doc.execute(graph("a x^2 - 2"));
    doc.execute(graph("x/4"));
    doc.execute(new Command.AddObject(id -> new FreePoint(id, Style.DEFAULT, 0.5, -1)));
    ObjectId g1 = new ObjectId(2);
    ObjectId g2 = new ObjectId(3);
    doc.execute(new Command.AddObject(id -> Intersection.of(id, Style.DEFAULT, g1, g2, 0)));
    doc.execute(new Command.AddObject(id -> Intersection.of(id, Style.DEFAULT, g1, g2, 1)));
    settle();
    DocumentState before = doc.state();
    assertThat(before.canvas().objects()).hasSize(6);
    for (CanvasObject o : before.canvas().objects()) {
      assertThat(before.canvas().geometryOf(o.id()))
          .as(o.toString())
          .isNotEqualTo(Geometry.UNDEFINED);
    }
    // the intersections were tracked: their anchors are saved with them
    assertThat(((Intersection) before.canvas().object(new ObjectId(5)).orElseThrow()).anchor())
        .isPresent();
    String text = JsonIo.text(SessionFile.encode(SessionFile.Stored.of(before)));
    doc.stop();

    SessionFile.Stored stored = SessionFile.decode(JsonIo.parse(text));
    DocumentState loaded = stored.state(before.settings());
    for (CanvasObject o : loaded.canvas().objects()) {
      assertThat(loaded.canvas().geometryOf(o.id())).isEqualTo(Geometry.UNDEFINED);
    }
    Document again = document(loaded);
    again.start();
    settle();

    assertThat(again.state().canvas().objects()).isEqualTo(before.canvas().objects());
    for (CanvasObject o : before.canvas().objects()) {
      assertThat(again.state().canvas().geometryOf(o.id()))
          .as(o.toString())
          .isEqualTo(before.canvas().geometryOf(o.id()));
    }
  }
}
