package mathcanvas.app.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import mathcanvas.app.document.Cell;
import mathcanvas.app.document.Command;
import mathcanvas.app.document.DocumentState;
import mathcanvas.app.document.Output;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Parametrisation;
import mathcanvas.canvas.model.Polyline;
import mathcanvas.canvas.model.Sample;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Window;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Stop against a slow first recompute, on a real worker thread: the stopped object is held, so
 * neither a keystroke nor a finished evaluation brings the slow recompute back.
 */
class CanvasStopTest {

  private final LinkedBlockingQueue<Runnable> ui = new LinkedBlockingQueue<>();
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Document doc =
      new Document(
          DocumentState.EMPTY,
          worker,
          ui::add,
          System::nanoTime,
          (env, settings, pixels) -> slow(),
          CanvasJob.Content.NONE);

  @AfterEach
  void shutDown() {
    worker.shutdownNow();
  }

  /** A sampler whose sampling takes a minute unless it is stopped. */
  private static Sampler slow() {
    return new Sampler() {
      @Override
      public double value(Expr e, Map<Symbol, Double> locals) {
        return 0;
      }

      @Override
      public Sample point(Parametrisation.Expressed p, double t) {
        return new Sample(t, t);
      }

      @Override
      public List<Polyline> sample(
          Parametrisation.Expressed p, double lo, double hi, Window w, Cancellation c) {
        long end = System.nanoTime() + 60_000_000_000L;
        while (System.nanoTime() < end) {
          c.check();
          Thread.onSpinWait();
        }
        return List.of();
      }

      @Override
      public List<Polyline> contour(Relation eq, Window w, Cancellation c) {
        return List.of();
      }

      @Override
      public List<Polyline> region(Relation eq, Window w, Cancellation c) {
        return List.of();
      }
    };
  }

  /** Runs what the worker hands back until the condition holds or the time is up. */
  private void pump(long millis, BooleanSupplier until) throws InterruptedException {
    long end = System.currentTimeMillis() + millis;
    while (System.currentTimeMillis() < end && !until.getAsBoolean()) {
      Runnable r = ui.poll(10, TimeUnit.MILLISECONDS);
      if (r != null) {
        r.run();
      }
    }
  }

  private boolean lastDone() {
    List<Cell> cells = doc.state().cells();
    return cells.get(cells.size() - 2).output() instanceof Output.Done;
  }

  private void enter(String text) {
    long id = doc.state().cells().getLast().id();
    doc.execute(new Command.EditInput(id, text));
    doc.execute(new Command.Submit(id));
  }

  private void addSlowGraph() throws InterruptedException {
    doc.execute(
        new Command.AddObject(
            id ->
                new FunctionGraph(
                    id,
                    Style.DEFAULT,
                    Parser.parseExpression("x", Names.NONE),
                    Symbol.of("x"),
                    FunctionGraph.ALL)));
    pump(300, () -> false);
    assertThat(doc.busyProperty().get()).isTrue();
  }

  @Test
  void escapeStopsASlowRecompute() throws InterruptedException {
    addSlowGraph();
    enter("1 + 1");

    // Escape stops both: the slow recompute and the cell waiting behind it
    doc.stop();
    pump(5000, () -> !doc.busyProperty().get());
    assertThat(doc.busyProperty().get()).isFalse();
    assertThat(doc.state().cells().getFirst().output()).isInstanceOf(Output.Cancelled.class);

    enter("2 + 2");
    pump(5000, this::lastDone);
    assertThat(lastDone()).isTrue();
  }

  @Test
  void afterEscapeTheSlowRecomputeComesBack() throws InterruptedException {
    addSlowGraph();

    doc.stop();
    pump(1000, () -> false);
    assertThat(doc.busyProperty().get()).isFalse();

    // a keystroke does not bring the held recompute back
    long id = doc.state().cells().getLast().id();
    doc.execute(new Command.EditInput(id, "1 + 1"));
    pump(500, () -> false);
    assertThat(doc.busyProperty().get()).isFalse();

    // nor does a finished evaluation
    doc.execute(new Command.Submit(id));
    pump(5000, this::lastDone);
    assertThat(lastDone()).isTrue();
    pump(300, () -> false);
    assertThat(doc.busyProperty().get()).isFalse();
    assertThat(doc.state().canvas().geometry()).isEmpty();
  }
}
