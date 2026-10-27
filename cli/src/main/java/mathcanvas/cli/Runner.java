package mathcanvas.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Narration;
import mathcanvas.engine.eval.Outcome;
import mathcanvas.engine.eval.Session;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.parse.ParseException;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.parse.Position;
import mathcanvas.engine.parse.Script;
import mathcanvas.engine.parse.Statement;
import mathcanvas.engine.print.Printer;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Step;
import mathcanvas.engine.rule.StepText;
import mathcanvas.engine.rule.Trace;

/**
 * Runs units of source through one session and turns each statement's outcome into the lines the
 * CLI prints: a value through the printer's display form, a definition's echo, the narration steps
 * of a traced value, or a failure with its position. It prints nothing itself, so the script
 * runner, the REPL and the corpus checker share it.
 */
final class Runner {

  /** The longest display an error message shows before it is cut. */
  static final int MAX_SHOWN = 200;

  /**
   * What one statement gave, or the parse error of a unit.
   *
   * @param line the line the statement starts on, or the parse error's line, from 1
   * @param lines what it prints on standard output: narration steps (indented) and its value
   * @param failure its error, or null
   */
  record Result(int line, List<String> lines, Failure failure) {}

  /**
   * An error and where it is.
   *
   * @param line the line, from 1
   * @param column the column, from 1, in code points
   * @param message the message
   * @param source the text of that line
   */
  record Failure(int line, int column, String message, String source) {

    /**
     * The error as the CLI reports it: {@code <name>:<line>:<column>: <message>}, the source line,
     * and a caret under the column, each line ended.
     *
     * @param name the input's name: a file, {@code <stdin>} or {@code <eval>}
     * @return the text
     */
    String format(String name) {
      StringBuilder caret = new StringBuilder();
      int[] cps = source.codePoints().toArray();
      for (int k = 0; k < column - 1; k++) {
        caret.append(k < cps.length && cps[k] == '\t' ? '\t' : ' ');
      }
      return name + ":" + line + ":" + column + ": " + message + "\n" + source + "\n" + caret
          + "^\n";
    }
  }

  /** The CLI's own texts. */
  private static final ResourceBundle MESSAGES =
      ResourceBundle.getBundle("mathcanvas.cli.messages", Locale.ROOT);

  private final Session session = new Session();
  private final Printer printer;
  private final StepText texts;

  /**
   * A runner on a new session.
   *
   * @param printer the output printer
   * @param precision the precision in digits
   * @param angle the angle mode
   * @param steps whether every evaluation is traced and its steps printed
   */
  Runner(Printer printer, int precision, AngleMode angle, boolean steps) {
    this.printer = printer;
    this.texts = new StepText(StepText.standard().bundle(), printer::display);
    session.setPrecision(precision);
    session.setAngleMode(angle);
    session.setTracing(steps);
  }

  /**
   * Runs a unit: parses it whole, then runs its statements in order. A parse error runs none of
   * them and is the only result.
   *
   * @param unit the unit
   * @param stop whether to stop at the first failing statement
   * @return one result per statement run
   */
  List<Result> run(Units.Unit unit, boolean stop) {
    String[] lines = unit.text().split("\r\n|\r|\n", -1);
    Script script;
    try {
      script = Parser.parseScript(unit.text(), session.names());
    } catch (ParseException e) {
      return List.of(
          new Result(
              unit.line() - 1 + e.line(), List.of(), failure(unit, lines, e, e.getMessage())));
    }
    List<Result> results = new ArrayList<>();
    for (Statement s : script.statements()) {
      int line = unit.line() - 1 + s.start().line();
      Result r =
          switch (session.run(s)) {
            case Outcome.Value v -> new Result(line, v.quiet() ? List.of() : valueLines(v), null);
            case Outcome.Defined d ->
                new Result(line, d.echo().quiet() ? List.of() : echo(d), null);
            case Outcome.Failed f -> {
              Position at = script.positionOf(f.error().node()).orElse(s.start());
              String message = shorten(f.error().getMessage());
              yield new Result(line, List.of(), failure(unit, lines, at, message));
            }
            // the CLI never cancels; reported like a failure should a session ever give it
            case Outcome.Cancelled _ ->
                new Result(line, List.of(), failure(unit, lines, s.start(), "statement cancelled"));
          };
      results.add(r);
      if (stop && r.failure() != null) {
        break;
      }
    }
    return results;
  }

  private static Failure failure(Units.Unit unit, String[] lines, ParseException e, String m) {
    return failure(unit, lines, new Position(e.line(), e.column(), e.offset()), m);
  }

  private static Failure failure(Units.Unit unit, String[] lines, Position at, String message) {
    String source = at.line() <= lines.length ? lines[at.line() - 1] : "";
    return new Failure(unit.line() - 1 + at.line(), at.column(), message, source);
  }

  /**
   * The lines a value prints: its curated narration steps when it was traced (a line saying there
   * are none when it rewrote nothing), then its display.
   */
  List<String> valueLines(Outcome.Value v) {
    List<String> out = new ArrayList<>();
    if (v.trace() != Trace.NONE) {
      Environment env = session.environment();
      Context ctx =
          new Context(
              env.assumptions(), env.settings().mathContext(), false, Context.DEFAULT.rewriteCap());
      List<Step> steps = Narration.steps(v.trace(), ctx, texts);
      if (steps.isEmpty()) {
        out.add("  " + MESSAGES.getString("steps.none"));
      }
      for (int k = 0; k < steps.size(); k++) {
        out.add("  step " + (k + 1) + ": " + steps.get(k).text());
        out.add("  = " + printer.display(steps.get(k).after()));
      }
    }
    out.add(printer.display(v.value()));
    return out;
  }

  private List<String> echo(Outcome.Defined d) {
    Statement.Definition def = d.echo();
    Expr head = def.params().isEmpty() ? def.name() : Apply.of(def.name(), def.params());
    return List.of(printer.print(head) + " := " + printer.print(def.body()));
  }

  /**
   * Cuts the display an evaluation message embeds after its first {@code ": "} to {@link
   * #MAX_SHOWN} characters (code points), followed by {@code …}, so that a failure on a huge value
   * does not flood the terminal.
   *
   * @param message the engine's message
   * @return the message to print
   */
  static String shorten(String message) {
    int colon = message.indexOf(": ");
    if (colon < 0) {
      return message;
    }
    String shown = message.substring(colon + 2);
    if (shown.codePointCount(0, shown.length()) <= MAX_SHOWN) {
      return message;
    }
    return message.substring(0, colon + 2)
        + shown.substring(0, shown.offsetByCodePoints(0, MAX_SHOWN))
        + "…";
  }
}
