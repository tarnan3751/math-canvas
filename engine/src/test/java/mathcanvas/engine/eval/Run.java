package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.UnicodePrinter;

/** Runs scripts through a session for the tests: the last outcome, printed or as a value. */
final class Run {

  private Run() {}

  /** Runs a script in a new session in the given settings and returns the last outcome. */
  static Outcome last(String script, Settings settings) {
    Session s = new Session(Environment.EMPTY.withSettings(settings));
    List<Outcome> outcomes = s.run(Parser.parseScript(script, s.names()));
    return outcomes.getLast();
  }

  /** The last outcome of a script in the default settings. */
  static Outcome last(String script) {
    return last(script, Settings.DEFAULT);
  }

  /** The value of the last statement, which must not fail. */
  static Expr value(String script, Settings settings) {
    Outcome o = last(script, settings);
    if (o instanceof Outcome.Failed f) {
      throw new AssertionError(script + " failed: " + f.error().getMessage(), f.error());
    }
    assertThat(o).as(script).isInstanceOf(Outcome.Value.class);
    return ((Outcome.Value) o).value();
  }

  /** The value of the last statement in the default settings. */
  static Expr value(String script) {
    return value(script, Settings.DEFAULT);
  }

  /** The value printed in Unicode display form. */
  static String shown(String script, Settings settings) {
    return UnicodePrinter.INSTANCE.display(value(script, settings));
  }

  /** The value printed in Unicode display form, default settings. */
  static String shown(String script) {
    return shown(script, Settings.DEFAULT);
  }

  /** The error message of the last statement, which must fail. */
  static String error(String script) {
    Outcome o = last(script);
    assertThat(o).as(script).isInstanceOf(Outcome.Failed.class);
    return ((Outcome.Failed) o).error().getMessage();
  }

  /** The canonical form of an expression parsed in the empty environment. */
  static Expr canonical(String text) {
    return Canonical.of(Parser.parseExpression(text, Names.NONE));
  }

  /** Degrees, otherwise the defaults. */
  static final Settings DEGREES = Settings.DEFAULT.withAngleMode(AngleMode.DEGREES);

  /** Gradians, otherwise the defaults. */
  static final Settings GRADIANS = Settings.DEFAULT.withAngleMode(AngleMode.GRADIANS);

  /** Whether a value holds an integral. */
  static boolean hasIntegral(Expr e) {
    if (e instanceof mathcanvas.engine.expr.Integral) {
      return true;
    }
    for (Expr o : mathcanvas.engine.match.Operands.of(e)) {
      if (hasIntegral(o)) {
        return true;
      }
    }
    return false;
  }
}
