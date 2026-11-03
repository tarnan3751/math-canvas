package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.TreeMap;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.UnicodePrinter;
import org.junit.jupiter.api.Test;

/** The evaluator's entry with a frame of locals, which a program's statements are evaluated in. */
class LocalsTest {

  private static Expr parse(String text) {
    return Parser.parseExpression(text, Names.NONE);
  }

  private static String shown(Environment env, String text, String... locals) {
    TreeMap<String, Expr> values = new TreeMap<>();
    for (int k = 0; k < locals.length; k += 2) {
      values.put(locals[k], Evaluator.evaluate(parse(locals[k + 1]), env, false).value());
    }
    Expr v =
        Evaluator.evaluate(parse(text), env, values, Set.of(), false, Cancellation.NEVER).value();
    return UnicodePrinter.INSTANCE.display(v);
  }

  @Test
  void aLocalIsItsValue() {
    assertThat(shown(Environment.EMPTY, "r k", "r", "6", "k", "4")).isEqualTo("24");
    assertThat(shown(Environment.EMPTY, "x^2 + y", "x", "3")).isEqualTo("y + 9");
  }

  @Test
  void aLocalIsNeverEvaluatedAgain() {
    Environment degrees =
        Environment.EMPTY.withSettings(Settings.DEFAULT.withAngleMode(AngleMode.DEGREES));
    // asin(t) in degrees is converted once, when the local is assigned
    assertThat(shown(degrees, "a", "a", "asin(t)")).isEqualTo(shown(degrees, "asin(t)"));
  }

  @Test
  void definitionsDoNotSeeTheLocals() {
    Environment env = Environment.EMPTY.with("b", new Definition.Value(parse("2x")));
    assertThat(shown(env, "b + x", "x", "5")).isEqualTo("2x + 5");
  }

  @Test
  void anUnassignedLocalCannotBeRead() {
    assertThatThrownBy(
            () ->
                Evaluator.evaluate(
                    parse("r + 1"),
                    Environment.EMPTY,
                    new TreeMap<>(),
                    Set.of("r"),
                    false,
                    Cancellation.NEVER))
        .isInstanceOf(EvaluationException.class)
        .hasMessage("`r` is read before it is assigned");
  }
}
