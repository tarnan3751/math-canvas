package mathcanvas.app.eval;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import mathcanvas.app.document.Output;
import mathcanvas.app.document.Result;
import mathcanvas.app.document.SliderSpec;
import mathcanvas.app.text.Messages;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.CancelledException;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.EvaluationException;
import mathcanvas.engine.eval.Evaluator;
import mathcanvas.engine.eval.Narration;
import mathcanvas.engine.eval.Outcome;
import mathcanvas.engine.eval.Session;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
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
import mathcanvas.engine.print.layout.Layout;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Step;
import mathcanvas.engine.rule.StepText;
import mathcanvas.engine.rule.Trace;
import mathcanvas.render.typeset.Box;

/**
 * One evaluation of a run of cells, in order, on the worker: each cell's text is parsed with the
 * names the cells before it defined, and its statements run in an engine {@link Session} built on
 * the environment the cell before it stored, with the canvas's variables bound over it, past
 * failures, as the CLI's REPL runs a unit. Each value is printed here, on the worker, to its text
 * and its layout tree, and typeset to a box tree (none over the typesetting cap, {@link Typeset}),
 * so the FX thread only shows them. Each finished cell is reported at once, with the environment
 * after it.
 *
 * <p>When the cancellation is requested (or the thread interrupted), the cell being evaluated and
 * every later one are reported {@link Output.Cancelled}: nothing of the stopped cell stays, and the
 * cells before it keep their outputs and environments, which the next job starts from. A {@link
 * CancelledException} from rendering, which runs outside the session, counts the same.
 */
public final class EvaluationJob {

  /** The head of a slider statement's right side. */
  static final String SLIDER = "slider";

  private static final Printer PRINTER = UnicodePrinter.INSTANCE;
  private static final LayoutPrinter LAYOUT = LayoutPrinter.INSTANCE;
  private static final StepText TEXTS =
      new StepText(StepText.standard().bundle(), PRINTER::display);

  /**
   * A cell to evaluate.
   *
   * @param id the cell's id
   * @param text its text
   * @param before the environment before it, as the cell before it stored it, when that cell is not
   *     the previous one of the run; empty to go on from the previous one
   * @param submitted whether the user submitted it, so its slider statements create or update their
   *     sliders; a cell run again for another reason only reads them
   */
  public record Input(long id, String text, Optional<Environment> before, boolean submitted) {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if one is null
     */
    public Input {
      Objects.requireNonNull(text, "text");
      Objects.requireNonNull(before, "before");
    }

    /**
     * A cell the user submitted, going on from the previous one of the run.
     *
     * @param id the cell's id
     * @param text its text
     */
    public Input(long id, String text) {
      this(id, text, Optional.empty(), true);
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
   * Evaluates cells in order, with no canvas variables.
   *
   * @param cells the cells
   * @param before the environment before the first
   * @param cancellation the request to stop
   * @param tracing whether every value is traced, so it shows its steps, as {@code steps(…)} does
   *     one
   * @param format the format values and steps are printed in; echoes are printed as typed
   * @param inspections the document's memo of the inspector's rows, touched only on this thread
   * @param sink told of each cell's output, in order, on this thread
   * @return whether every cell finished; false when the job was cancelled
   */
  public static boolean run(
      List<Input> cells,
      Environment before,
      Cancellation cancellation,
      boolean tracing,
      Format format,
      Inspections inspections,
      Sink sink) {
    return run(
        cells, before, CanvasBindings.NONE, cancellation, tracing, format, inspections, sink);
  }

  /**
   * Evaluates cells in order. Each cell starts from the environment the cell before it stored (the
   * previous cell of the run's, unless its input names another), with the canvas's variables bound
   * over it ({@link CanvasBindings#overlay}), and stores its own without them. A statement {@code
   * name := slider(min, max, step)} defines a slider rather than reaching the engine: the three are
   * evaluated to exact decimals, the name is bound to the slider's current value (its minimum when
   * it is new, else its value snapped into the new range), the cell echoes the statement as typed,
   * and its output lists the slider for the document to create or update.
   *
   * @param cells the cells
   * @param before the environment before the first, as stored, when its input names none
   * @param canvas the canvas's variables
   * @param cancellation the request to stop
   * @param tracing whether every value is traced
   * @param format the format values and steps are printed in
   * @param inspections the document's memo of the inspector's rows, touched only on this thread
   * @param sink told of each cell's output, in order, on this thread
   * @return whether every cell finished; false when the job was cancelled
   */
  public static boolean run(
      List<Input> cells,
      Environment before,
      CanvasBindings canvas,
      Cancellation cancellation,
      boolean tracing,
      Format format,
      Inspections inspections,
      Sink sink) {
    Printers printers = new Printers(PRINTER.withFormat(format), LAYOUT.withFormat(format), format);
    Environment stored = before;
    CanvasBindings live = canvas;
    for (int k = 0; k < cells.size(); k++) {
      Input in = cells.get(k);
      stored = in.before().orElse(stored);
      Output out;
      try {
        cancellation.check();
        out =
            cell(
                in.text(),
                in.submitted(),
                stored,
                live,
                tracing,
                cancellation,
                printers,
                inspections);
      } catch (CancelledException e) {
        out = Output.Cancelled.INSTANCE;
      }
      if (out instanceof Output.Cancelled) {
        for (int j = k; j < cells.size(); j++) {
          sink.cell(cells.get(j).id(), Output.Cancelled.INSTANCE);
        }
        return false;
      }
      Output.Done done = (Output.Done) out;
      stored = done.after();
      // the sliders a cell defines are bound for the cells after it, before the document has them
      for (SliderSpec spec : done.sliders()) {
        live = live.with(spec);
      }
      sink.cell(in.id(), out);
    }
    return true;
  }

  /** The printers of one job, in its format. */
  private record Printers(Printer text, LayoutPrinter layout, Format format) {}

  private static Output cell(
      String text,
      boolean submitted,
      Environment stored,
      CanvasBindings canvas,
      boolean tracing,
      Cancellation cancellation,
      Printers printers,
      Inspections inspections) {
    String[] lines = text.split("\r\n|\r|\n", -1);
    Environment start = canvas.overlay(stored);
    // the canvas variables the session holds by the overlay or a slider statement, not by a
    // definition of the cell's own: left out of the environment the cell stores
    Set<String> overlaid = new HashSet<>(canvas.values().keySet());
    overlaid.removeIf(n -> stored.definition(n).isPresent());
    Session session = new Session(start);
    session.setTracing(tracing);
    Script script;
    try {
      script = Parser.parseScript(text, session.names());
    } catch (ParseException e) {
      Position at = new Position(e.line(), e.column(), e.offset());
      return new Output.Done(
          List.of(failed(e.getMessage(), at, lines)), stored, inspections.of(stored, cancellation));
    }
    List<Result> results = new ArrayList<>();
    Set<String> reads = new HashSet<>();
    boolean assumes = false;
    List<SliderSpec> sliders = new ArrayList<>();
    List<String> help = new ArrayList<>();
    for (Statement s : script.statements()) {
      Result r;
      if (s instanceof Statement.Definition d
          && d.params().isEmpty()
          && d.body() instanceof Apply call
          && call.head().name().equals(SLIDER)) {
        r =
            slider(
                d,
                call,
                submitted,
                session,
                canvas,
                script,
                lines,
                cancellation,
                sliders,
                overlaid);
      } else {
        if (s instanceof Statement.Expression x) {
          reads.addAll(ReadNames.closure(ReadNames.of(x.expr()), session.environment()));
          assumes |= x.expr() instanceof Apply a && a.head().name().equals("assume");
        }
        r =
            switch (session.run(s, cancellation)) {
              case Outcome.Value v -> value(v, session.environment(), cancellation, printers);
              case Outcome.Defined d -> {
                overlaid.remove(d.echo().name().name());
                yield defined(d, cancellation);
              }
              case Outcome.Failed f ->
                  failed(
                      f.error().getMessage(),
                      script.positionOf(f.error().node()).orElse(s.start()),
                      lines);
              case Outcome.Cancelled _ -> null;
            };
        if (s instanceof Statement.Expression x
            && x.expr() instanceof Apply a
            && a.head().name().equals("clear")
            && a.args().getFirst() instanceof Symbol name) {
          overlaid.remove(name.name());
        }
      }
      if (r == null) {
        return Output.Cancelled.INSTANCE;
      }
      if (s instanceof Statement.Expression x
          && x.expr() instanceof Apply a
          && a.head().name().equals("help")
          && r instanceof Result.Value v
          && v.value() instanceof Symbol name) {
        help.add(name.name());
      }
      results.add(r);
    }
    Environment after = session.environment();
    for (String n : overlaid) {
      after = after.without(n);
    }
    return new Output.Done(
        results, after, inspections.of(after, cancellation), reads, assumes, sliders, help);
  }

  /**
   * A slider statement, {@code name := slider(min, max, step)}: the three evaluated here to exact
   * decimals, the name bound to the slider's value, the slider added to the cell's list.
   */
  private static Result slider(
      Statement.Definition d,
      Apply call,
      boolean submitted,
      Session session,
      CanvasBindings canvas,
      Script script,
      String[] lines,
      Cancellation cancellation,
      List<SliderSpec> sliders,
      Set<String> overlaid) {
    String name = d.name().name();
    if (CanvasBindings.isReserved(name)) {
      return failed(Messages.text("name.reserved", name), d.start(), lines);
    }
    if (canvas.points().contains(name)) {
      return failed(Messages.text("slider.pointName", name), d.start(), lines);
    }
    BigDecimal[] bounds = new BigDecimal[3];
    for (int k = 0; k < 3; k++) {
      Expr arg = call.args().get(k);
      Position at = script.positionOf(arg).orElse(d.start());
      Set<String> read = ReadNames.closure(ReadNames.of(arg), session.environment());
      read.retainAll(canvas.names());
      if (!read.isEmpty()) {
        return failed(Messages.text("slider.canvasBound"), at, lines);
      }
      Expr value;
      try {
        value = Evaluator.evaluate(arg, session.environment(), false, cancellation).value();
      } catch (EvaluationException e) {
        return failed(e.getMessage(), script.positionOf(e.node()).orElse(at), lines);
      }
      Optional<BigDecimal> exact = CanvasBindings.decimal(value);
      if (exact.isEmpty()) {
        return failed(Messages.text("slider.notDecimal"), at, lines);
      }
      bounds[k] = exact.get();
    }
    if (bounds[0].compareTo(bounds[1]) >= 0) {
      return failed(Messages.text("slider.emptyRange"), d.start(), lines);
    }
    if (bounds[2].signum() <= 0) {
      return failed(
          Messages.text("slider.step"),
          script.positionOf(call.args().get(2)).orElse(d.start()),
          lines);
    }
    SliderSpec spec = new SliderSpec(name, bounds[0], bounds[1], bounds[2]);
    boolean exists = canvas.sliders().containsKey(name);
    // only a submission makes or changes the slider; a run for another reason reads the slider
    // there is, or binds the minimum as a plain value when there is none (it was deleted)
    BigDecimal value =
        submitted
            ? canvas.sliderFor(spec).value()
            : exists ? canvas.sliders().get(name).value() : spec.min();
    Outcome bound =
        session.run(
            new Statement.Definition(
                d.name(), List.of(), CanvasBindings.exact(value), d.quiet(), d.start()),
            cancellation);
    if (bound instanceof Outcome.Cancelled) {
      return null;
    }
    if (submitted || exists) {
      overlaid.add(name);
    } else {
      overlaid.remove(name);
    }
    if (submitted) {
      sliders.add(spec);
    }
    return defined(new Outcome.Defined(d), cancellation);
  }

  /**
   * The note of a value's general-solution parameters, {@code k ∈ ℤ}, and constants of integration,
   * {@code C₁ ∈ ℝ}, printed here on the worker; empty when it has none.
   */
  private static String note(Outcome.Value v, Printers printers, Cancellation cancellation) {
    List<String> parts = new ArrayList<>();
    for (Expr p : v.parameters().items()) {
      parts.add(printers.text().display(p, cancellation));
    }
    return String.join(", ", parts);
  }

  /**
   * Each relation of a value's parameter note typeset on the worker, as the value is, so that its
   * {@code k} is set like the value's; none when one is too large to typeset.
   */
  private static List<Box> noteBoxes(
      Outcome.Value v, Printers printers, Cancellation cancellation) {
    List<Box> out = new ArrayList<>();
    for (Expr p : v.parameters().items()) {
      Layout layout = Typeset.capped(printers.layout().display(p, cancellation));
      if (layout == null) {
        return List.of();
      }
      out.add(Typeset.box(layout, Typeset.RESULT_SIZE, cancellation));
    }
    return out;
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
          v.value(),
          v.trace(),
          true,
          "",
          v.value(),
          null,
          null,
          List.of(),
          printers.format(),
          v.numeric(),
          note(v, printers, cancellation),
          noteBoxes(v, printers, cancellation));
    }
    List<Result.Step> steps = steps(v.trace(), env, cancellation, printers);
    if (printers.format().equals(Format.EXACT)) {
      Expr shown = Display.of(v.value(), cancellation);
      Layout layout = Typeset.capped(LAYOUT.print(shown, cancellation));
      return new Result.Value(
          v.value(),
          v.trace(),
          false,
          PRINTER.print(shown, cancellation),
          shown,
          layout,
          Typeset.box(layout, Typeset.RESULT_SIZE, cancellation),
          steps,
          Format.EXACT,
          v.numeric(),
          note(v, printers, cancellation),
          noteBoxes(v, printers, cancellation));
    }
    Layout layout = Typeset.capped(printers.layout().display(v.value(), cancellation));
    return new Result.Value(
        v.value(),
        v.trace(),
        false,
        printers.text().display(v.value(), cancellation),
        v.value(),
        layout,
        Typeset.box(layout, Typeset.RESULT_SIZE, cancellation),
        steps,
        printers.format(),
        v.numeric(),
        note(v, printers, cancellation),
        noteBoxes(v, printers, cancellation));
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
      out.add(step(s, cancellation, printers));
    }
    return out;
  }

  private static Result.Step step(Step s, Cancellation cancellation, Printers printers) {
    Layout layout = Typeset.capped(printers.layout().display(s.after(), cancellation));
    return new Result.Step(
        s.text(),
        printers.text().display(s.after(), cancellation),
        layout,
        Typeset.box(layout, Typeset.STEP_SIZE, cancellation));
  }

  private static Result defined(Outcome.Defined d, Cancellation cancellation) {
    Layout layout = Typeset.echo(LAYOUT, d.echo());
    return new Result.Defined(
        d.echo(),
        PRINTER.echo(d.echo()),
        layout,
        Typeset.box(layout, Typeset.RESULT_SIZE, cancellation));
  }

  private static Result failed(String message, Position at, String[] lines) {
    String line = at.line() >= 1 && at.line() <= lines.length ? lines[at.line() - 1] : "";
    return new Result.Failed(message, at, line);
  }
}
