package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Step;
import org.junit.jupiter.api.Test;

/**
 * {@code powmod}, {@code invmod}, {@code crt} and {@code diophantine} ({@code docs/language.md}
 * §14.23) through the evaluator: values, edge cases, errors, the parameter and the steps.
 */
class ModularFunctionsTest {

  private static Outcome.Value run(String script) {
    Session s = new Session(Environment.EMPTY);
    return (Outcome.Value) s.run(Parser.parseScript(script, s.names())).getLast();
  }

  private static List<String> steps(String script) {
    Outcome.Value v = run(script);
    TraceChecks.assertChain(new Rewriter.Result(v.value(), v.trace()));
    return Narration.steps(new Evaluator.Result(v.value(), v.trace()), Context.DEFAULT).stream()
        .map(Step::text)
        .toList();
  }

  @Test
  void powers() {
    assertThat(shown("powmod(3, 100, 7)")).isEqualTo("4");
    assertThat(shown("powmod(2, -1, 7)")).isEqualTo("4");
    assertThat(shown("powmod(-2, 3, 7)")).isEqualTo("6");
    assertThat(shown("powmod(5, 0, 7)")).isEqualTo("1");
    assertThat(shown("powmod(6, 3, 4)")).isEqualTo("0");
    assertThat(shown("powmod(2, 10, 1)")).isEqualTo("0");
    assertThat(shown("powmod(0, 0, 7)")).isEqualTo("undefined");
    assertThat(shown("powmod(0, 0, 1)")).isEqualTo("0");
    // the zero test is on a itself: 5^0 = 1, though 5 ≡ 0 (mod 5)
    assertThat(shown("powmod(5, 0, 5)")).isEqualTo("1");
    assertThat(shown("powmod(2, -1, 4)")).isEqualTo("undefined");
    assertThat(shown("powmod(a, 2, 7)")).isEqualTo("powmod(a, 2, 7)");
    assertThat(shown("powmod(2, 10^100, 10^9 + 7)")).isEqualTo("314344290");
    assertThat(error("powmod(2, 3, 0)")).isEqualTo("`powmod`'s modulus must be a positive integer");
    assertThat(error("powmod(2, 1/2, 5)")).isEqualTo("powmod takes integers");
  }

  @Test
  void inverses() {
    assertThat(shown("invmod(17, 43)")).isEqualTo("38");
    assertThat(shown("invmod(-17, 43)")).isEqualTo("5");
    assertThat(shown("invmod(6, 9)")).isEqualTo("undefined");
    assertThat(shown("invmod(5, 1)")).isEqualTo("0");
    assertThat(error("invmod(5, -3)")).isEqualTo("`invmod`'s modulus must be a positive integer");
  }

  @Test
  void congruences() {
    assertThat(shown("crt([2, 3, 2], [3, 5, 7])")).isEqualTo("23");
    assertThat(shown("crt([1, 3], [4, 6])")).isEqualTo("9");
    assertThat(shown("crt([1, 2], [4, 6])")).isEqualTo("undefined");
    assertThat(shown("crt([-1], [5])")).isEqualTo("4");
    assertThat(shown("crt([a, 1], [3, 5])")).isEqualTo("crt(⟨a, 1⟩, ⟨3, 5⟩)");
    assertThat(error("crt([1, 2], [3])"))
        .isEqualTo("`crt` takes a list of residues and a list of moduli of the same length");
    assertThat(error("crt([], [])")).isEqualTo("`crt` needs at least one congruence");
    assertThat(error("crt([1], [0])")).isEqualTo("`crt`'s moduli must be positive integers");
    assertThat(error("crt(1, 2)")).isEqualTo("`crt` takes a list of residues and a list of moduli");
  }

  @Test
  void linearDiophantineEquations() {
    Outcome.Value v = run("diophantine(3x + 5y = 7, [x, y])");
    assertThat(mathcanvas.engine.print.UnicodePrinter.INSTANCE.display(v.value()))
        .isEqualTo("{[x = 5k + 4, y = -3k - 1]}");
    assertThat(v.parameters().items()).hasSize(1);
    assertThat(shown("diophantine(4x + 6y = 7, [x, y])")).isEqualTo("{}");
    assertThat(shown("diophantine(4x - 6y = 2, [x, y])")).isEqualTo("{[x = 3k + 2, y = 2k + 1]}");
    assertThat(shown("diophantine(x/2 + y/3 = 1, [x, y])")).isEqualTo("{[x = 2k, y = -3k + 3]}");
    assertThat(shown("diophantine(3y = 6, [x, y])")).isEqualTo("{[x = k, y = 2]}");
    assertThat(shown("diophantine(3y = 7, [x, y])")).isEqualTo("{}");
    assertThat(shown("diophantine(0 = 0, [x, y])")).isEqualTo("diophantine(0 = 0, ⟨x, y⟩)");
    assertThat(shown("diophantine(0 = 1, [x, y])")).isEqualTo("{}");
    assertThat(shown("diophantine(a x + y = 1, [x, y])"))
        .isEqualTo("diophantine(a x + y = 1, ⟨x, y⟩)");
    assertThat(shown("subs(diophantine(3x + 5y = 7, [x, y]), k = 0)"))
        .isEqualTo("{[x = 4, y = -1]}");
    assertThat(error("diophantine(x^2 + y = 1, [x, y])"))
        .isEqualTo("`diophantine` takes a linear equation a x + b y = c");
    assertThat(error("diophantine(x + y = 1, x)"))
        .isEqualTo(
            "`diophantine` solves for two unknowns, as in `diophantine(3x + 5y = 1, [x, y])`");
  }

  @Test
  void theSteps() {
    assertThat(steps("steps(powmod(3, 13, 7))"))
        .containsExactly(
            "The exponent 13 is 1101₂ in binary: square and multiply from its lowest bit. The bit"
                + " is 1: multiply the result by 3, ≡ 3 (mod 7): 3 powmod(3, 12, 7)",
            "The bit is 0: square the base, 3² ≡ 2 (mod 7), and halve the exponent: 3 powmod(2,"
                + " 6, 7)",
            "The bit is 0: square the base, 2² ≡ 4 (mod 7), and halve the exponent: 3 powmod(4,"
                + " 3, 7)",
            "The bit is 1: multiply the result by 4, ≡ 5 (mod 7): 5 powmod(4, 2, 7)",
            "The bit is 0: square the base, 4² ≡ 2 (mod 7), and halve the exponent: 5 powmod(2,"
                + " 1, 7)",
            "The bit is 1: multiply the result by 2, ≡ 3 (mod 7): 3");
    assertThat(steps("steps(invmod(17, 43))"))
        .containsExactly(
            "The extended Euclidean algorithm on 43 and 17, rows of remainder, quotient and"
                + " coefficient of 17: [[17, 2, 1], [9, 1, -2], [8, 1, 3], [1, 8, -5]]; so -5·17 ≡ 1 (mod"
                + " 43), and 17⁻¹ ≡ -5: -5 mod 43",
            "Reduce modulo 43: 38");
    assertThat(steps("steps(invmod(6, 9))"))
        .containsExactly("gcd(6, 9) = 3 ≠ 1, so 6 has no inverse modulo 9");
    assertThat(steps("steps(crt([2, 3, 2], [3, 5, 7]))"))
        .containsExactly(
            "Merge x ≡ 2 (mod 3) and x ≡ 3 (mod 5): x = 2 + 3t with 3t ≡ 1 (mod 5) gives t ≡ 2"
                + " (mod 5), so x ≡ 8 (mod 15): crt(⟨8, 2⟩, ⟨15, 7⟩)",
            "Merge x ≡ 8 (mod 15) and x ≡ 2 (mod 7): x = 8 + 15t with 15t ≡ 1 (mod 7) gives t ≡ 1"
                + " (mod 7), so x ≡ 23 (mod 105): crt(⟨23⟩, ⟨105⟩)",
            "The solution is the residue modulo 105: 23");
    assertThat(steps("steps(crt([1, 2], [4, 6]))"))
        .containsExactly(
            "x ≡ 1 (mod 4) and x ≡ 2 (mod 6) disagree modulo gcd(4, 6) = 2, so there is no"
                + " solution");
    assertThat(steps("steps(diophantine(3x + 5y = 7, [x, y]))"))
        .containsExactly(
            "gcd(3, 5) = 1 divides 7, so there are integer solutions, and the extended Euclidean"
                + " algorithm gives one: {[x = 4, y = -1]}",
            "Adding k times (5, -3), which leaves the left side unchanged, gives them all, k any"
                + " integer: {[x = 5k + 4, y = -3k - 1]}");
  }
}
