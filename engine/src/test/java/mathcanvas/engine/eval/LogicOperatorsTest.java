package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.AsciiPrinter;
import mathcanvas.engine.print.LatexPrinter;
import mathcanvas.engine.print.UnicodePrinter;
import org.junit.jupiter.api.Test;

/**
 * The boolean operators ({@code docs/language.md} §8.4) and the rule set {@code logic} through the
 * evaluator: the identities, relations as operands, the errors, the printers and the steps.
 */
class LogicOperatorsTest {

  @Test
  void constantsAndIdentities() {
    assertThat(shown("true and false")).isEqualTo("false");
    assertThat(shown("true or false")).isEqualTo("true");
    assertThat(shown("true xor true")).isEqualTo("false");
    assertThat(shown("not true")).isEqualTo("false");
    assertThat(shown("false implies p")).isEqualTo("true");
    assertThat(shown("p and true")).isEqualTo("p");
    assertThat(shown("p or false")).isEqualTo("p");
    assertThat(shown("p and not p")).isEqualTo("false");
    assertThat(shown("p or not p")).isEqualTo("true");
    assertThat(shown("p and (p or q)")).isEqualTo("p");
    assertThat(shown("p or (p and q)")).isEqualTo("p");
    assertThat(shown("not not p")).isEqualTo("p");
    assertThat(shown("not (p and q)")).isEqualTo("¬p ∨ ¬q");
    assertThat(shown("not (p or q)")).isEqualTo("¬p ∧ ¬q");
    assertThat(shown("not (p implies q)")).isEqualTo("¬q ∧ p");
    assertThat(shown("true xor p")).isEqualTo("¬p");
    assertThat(shown("p xor p")).isEqualTo("false");
    assertThat(shown("p implies p")).isEqualTo("true");
    assertThat(shown("q and p")).isEqualTo("p ∧ q");
    assertThat(shown("p ∧ q ∧ p")).isEqualTo("p ∧ q");
  }

  @Test
  void relationsAsOperands() {
    assertThat(shown("1 < 2 and 3 > 4")).isEqualTo("false");
    assertThat(shown("x > 0 and x < 1")).isEqualTo("x < 1 ∧ x > 0");
    assertThat(shown("not (x < 1)")).isEqualTo("x ≥ 1");
    assertThat(shown("not (x = 1)")).isEqualTo("x ≠ 1");
    assertThat(shown("assume(z ∈ ℂ); not (z < 1)")).isEqualTo("¬(z < 1)");
    assertThat(shown("2 ∈ {1, 2} and p")).isEqualTo("p");
    assertThat(shown("p = true")).isEqualTo("p = true");
    assertThat(shown("true = false")).isEqualTo("false");
  }

  @Test
  void theErrors() {
    assertThat(error("1 and p")).isEqualTo("`and` works on values that are true or false");
    assertThat(error("not [1, 2]")).isEqualTo("`not` works on values that are true or false");
    assertThat(error("sin(x) and p")).isEqualTo("`and` works on values that are true or false");
    assertThat(error("(x mod 2) or p")).isEqualTo("`or` works on values that are true or false");
    assertThat(error("not floor(x)")).isEqualTo("`not` works on values that are true or false");
    assertThat(shown("isprime(n) and p")).isEqualTo("isprime(n) ∧ p");
    assertThat(shown("f(x) := x > 0; f(t) and p")).isEqualTo("p ∧ t > 0");
    assertThat(error("2 ∈ (p and q)"))
        .isEqualTo("`∈` needs a set or a number domain on its right, as in `2 ∈ {1, 2}`");
    assertThat(error("(p and q) + 1"))
        .isEqualTo("true and false cannot be added, multiplied or raised to powers");
    assertThat(error("true < false")).isEqualTo("true and false cannot be compared with < or >");
  }

  @Test
  void thePrinters() {
    var c = Canonical.of(Parser.parseExpression("not p and (q or r) implies s", n -> false));
    assertThat(UnicodePrinter.INSTANCE.display(c)).isEqualTo("¬p ∧ (q ∨ r) ⟹ s");
    assertThat(AsciiPrinter.INSTANCE.display(c)).isEqualTo("not p and (q or r) implies s");
    assertThat(LatexPrinter.INSTANCE.display(c))
        .isEqualTo("(\\lnot p \\land (q \\lor r)) \\implies s");
    var r = Canonical.of(Parser.parseExpression("(p ⟹ q) ⟹ r", n -> false));
    assertThat(UnicodePrinter.INSTANCE.display(r)).isEqualTo("(p ⟹ q) ⟹ r");
    var n = Canonical.of(Parser.parseExpression("not (p xor q)", n2 -> false));
    assertThat(AsciiPrinter.INSTANCE.display(n)).isEqualTo("not (p xor q)");
    var x = Canonical.of(Parser.parseExpression("x = (not p)", n2 -> false));
    assertThat(UnicodePrinter.INSTANCE.display(x)).isEqualTo("x = (¬p)");
  }

  @Test
  void theSteps() {
    List<String> steps = NarrationTest.narrated("steps(not (p and q))");
    assertThat(steps).hasSize(1);
    assertThat(steps.getFirst()).startsWith("De Morgan’s law");
  }
}
