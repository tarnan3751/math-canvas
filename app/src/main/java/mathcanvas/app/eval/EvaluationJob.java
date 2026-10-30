package mathcanvas.app.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import mathcanvas.app.document.Output;
import mathcanvas.app.document.Result;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.CancelledException;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Narration;
import mathcanvas.engine.eval.Outcome;
import mathcanvas.engine.eval.Session;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.parse.ParseException;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.parse.Position;
import mathcanvas.engine.parse.Script;
import mathcanvas.engine.parse.Statement;
import mathcanvas.engine.print.Display;
import mathcanvas.engine.print.Format;
import mathcanvas.engine.print.LayoutPrinter;
import mathcanvas.engine.print.Printer;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Step;
import mathcanvas.engine.rule.StepText;
import mathcanvas.engine.rule.Trace;

/**
 * One evaluation of a run of cells, in order, on the worker: each cell's text is parsed with the
 * names the cells before it defined, and its statements run in one engine {@link Session} built on
 * the environment before the first cell, past failures, as the CLI's REPL runs a unit. Each value
 * is printed here, on the worker, to its text and its layout tree (none over {@link
 * Typeset#MAX_TYPESET_NODES} nodes), so the FX thread only typesets and shows them. Each finished
 * cell is reported at once, with the environment after it.
 *
 * <p>When the cancellation is requested (or the thread interrupted), the cell being evaluated and
 * every later one are reported {@link Output.Cancelled}: nothing of the stopped cell stays, and the
 * cells before it keep their outputs and environments, which the next job starts from. A {@link
 * CancelledException} from rendering, which runs outside the session, counts the same.
 */
public final class EvaluationJob {

  private static final Printer PRINTER = UnicodePrinter.INSTANCE;
  private static final LayoutPrinter LAYOUT = LayoutPrinter.INSTANCE;
  private static final StepText TEXTS =
      new StepText(StepText.standard().bundle(), PRINTER::display);

  /**
   * A cell to evaluate.
   *
   * @param id the cell's id
   * @param text its text
   */
  public record Input(long id, String text) {

    /**
     * Checks the text.
     *
     * @throws NullPointerException if it is null
     */
    public Input {
      Objects.requireNonNull(text, "text");
    }
  }

  /** Receives each cell's output as it is known. */
  @FunctionalInterface
  public interface Sink {

    /**
     * A cell's output.
     *
     * @param id the cell
     * @param output its output: {@link Output.Done} or {@link Output.Cancelled}
     */
    void cell(long id, Output output);
  }

  private EvaluationJob() {}

  /**
   * Evaluates cells in order.
   *
   * @param cells the cells
   * @param before the environment before the first
   * @param cancellation the request to stop
   * @param tracing whether every value is traced, so it shows its steps, as {@code steps(…)} does
   *     one
   * @param format the format values and steps are printed in; echoes are printed as typed
   * @param sink told of each cell's output, in order, on this thread
   * @return whether every cell finished; false when the job was cancelled
   */
  public static boolean run(
      List<Input> cells,
      Environment before,
      Cancellation cancellation,
      boolean tracing,
      Format format,
      Sink sink) {
    Printers printers = new Printers(PRINTER.withFormat(format), LAYOUT.withFormat(format), format);
    Session session = new Session(before);
    session.setTracing(tracing);
    for (int k = 0; k < cells.size(); k++) {
      Output out;
      try {
        cancellation.check();
        out = cell(cells.get(k).text(), session, cancellation, printers);
      } catch (CancelledException e) {
        out = Output.Cancelled.INSTANCE;
      }
      if (out instanceof Output.Cancelled) {
        for (int j = k; j < cells.size(); j++) {
          sink.cell(cells.get(j).id(), Output.Cancelled.INSTANCE);
        }
        return false;
      }
      sink.cell(cells.get(k).id(), out);
    }
    return true;
  }

  /** The printers of one job, in its format. */
  private record Printers(Printer text, LayoutPrinter layout, Format format) {}

  private static Output cell(
      String text, Session session, Cancellation cancellation, Printers printers) {
    String[] lines = text.split("\r\n|\r|\n", -1);
    Environment start = session.environment();
    Script script;
    try {
      script = Parser.parseScript(text, session.names());
    } catch (ParseException e) {
      Position at = new Position(e.line(), e.column(), e.offset());
      return new Output.Done(
          List.of(failed(e.getMessage(), at, lines)), start, Inspections.of(start, cancellation));
    }
    List<Result> results = new ArrayList<>();
    for (Statement s : script.statements()) {
      Result r =
          switch (session.run(s, cancellation)) {
            case Outcome.Value v -> value(v, session.environment(), cancellation, printers);
            case Outcome.Defined d ->
                new Result.Defined(
                    d.echo(), PRINTER.echo(d.echo()), Typeset.echo(LAYOUT, d.echo()));
            case Outcome.Failed f ->
                failed(
                    f.error().getMessage(),
                    script.positionOf(f.error().node()).orElse(s.start()),
                    lines);
            case Outcome.Cancelled _ -> null;
          };
      if (r == null) {
        return Output.Cancelled.INSTANCE;
      }
      results.add(r);
    }
    return new Output.Done(
        results, session.environment(), Inspections.of(session.environment(), cancellation));
  }

  /**
   * A value with its text, its layout and its steps, printed here on the worker. In the exact
   * format both print the display tree, which the cell keeps for copying a selection; another
   * format prepares the value first (decimals, mixed numbers), so its display is printed whole and
   * the value itself is kept.
   */
  private static Result value(
      Outcome.Value v, Environment env, Cancellation cancellation, Printers printers) {
    if (v.quiet()) {
      return new Result.Value(
          v.value(), v.trace(), true, "", v.value(), null, List.of(), printers.format());
    }
    List<Result.Step> steps = steps(v.trace(), env, cancellation, printers);
    if (printers.format().equals(Format.EXACT)) {
      Expr shown = Display.of(v.value(), cancellation);
      return new Result.Value(
          v.value(),
          v.trace(),
          false,
          PRINTER.print(shown),
          shown,
          Typeset.capped(LAYOUT.print(shown)),
          steps,
          Format.EXACT);
    }
    return new Result.Value(
        v.value(),
        v.trace(),
        false,
        printers.text().display(v.value(), cancellation),
        v.value(),
        Typeset.capped(printers.layout().display(v.value(), cancellation)),
        steps,
        printers.format());
  }

  /**
   * The curated steps of a trace, narrated and printed here on the worker; narration stops only on
   * an interrupt, and a {@code CancelledException} from it cancels the cell like any other.
   */
  private static List<Result.Step> steps(
      Trace trace, Environment env, Cancellation cancellation, Printers printers) {
    if (trace == Trace.NONE) {
      return List.of();
    }
    Context ctx =
        new Context(
                env.assumptions(),
                env.settings().mathContext(),
                false,
                Context.DEFAULT.rewriteCap())
            .withCancellation(cancellation);
    List<Result.Step> out = new ArrayList<>();
    for (Step s : Narration.steps(trace, ctx, TEXTS)) {
      cancellation.check();
      out.add(
          new Result.Step(
              s.text(),
              printers.text().display(s.after(), cancellation),
              Typeset.capped(printers.layout().display(s.after(), cancellation))));
    }
    return out;
  }

  private static Result failed(String message, Position at, String[] lines) {
    String line = at.line() >= 1 && at.line() <= lines.length ? lines[at.line() - 1] : "";
    return new Result.Failed(message, at, line);
  }
}
