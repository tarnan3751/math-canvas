package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static mathcanvas.engine.eval.Run.value;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.AsciiPrinter;
import mathcanvas.engine.print.LatexPrinter;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Step;
import org.junit.jupiter.api.Test;

/**
 * Primality, the primes, {@code ifactor} and the divisor functions ({@code docs/language.md}
 * §14.23) through the evaluator: values, edge cases, the call left, errors, {@code ifactor}'s
 * display-only product and its steps.
 */
class NumberTheoryFunctionsTest {

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
  void primality() {
    assertThat(value("isprime(97)")).isEqualTo(Bool.TRUE);
    assertThat(value("isprime(1)")).isEqualTo(Bool.FALSE);
    assertThat(value("isprime(0)")).isEqualTo(Bool.FALSE);
    assertThat(value("isprime(-7)")).isEqualTo(Bool.FALSE);
    assertThat(value("isprime(561)")).isEqualTo(Bool.FALSE);
    assertThat(value("isprime(2^127 - 1)")).isEqualTo(Bool.TRUE);
    assertThat(shown("isprime(n)")).isEqualTo("isprime(n)");
    assertThat(error("isprime(1/2)")).isEqualTo("isprime takes an integer");
    assertThat(error("isprime(7.0)")).isEqualTo("isprime takes an integer");
  }

  @Test
  void thePrimesAround() {
    assertThat(shown("nextprime(100)")).isEqualTo("101");
    assertThat(shown("nextprime(-5)")).isEqualTo("2");
    assertThat(shown("prevprime(100)")).isEqualTo("97");
    assertThat(shown("prevprime(2)")).isEqualTo("undefined");
    assertThat(shown("primes(10, 30)")).isEqualTo("⟨11, 13, 17, 19, 23, 29⟩");
    assertThat(shown("primes(14, 16)")).isEqualTo("[]");
    assertThat(shown("primes(30, 10)")).isEqualTo("[]");
    assertThat(shown("length(primes(1, 1000))")).isEqualTo("168");
    assertThat(error("primes(0, 10^7)"))
        .isEqualTo("primes takes an interval of at most 1000000 integers");
    assertThat(error("primes(1, 5/2)")).isEqualTo("primes takes integers");
  }

  @Test
  void theFactorisation() {
    assertThat(shown("ifactor(360)")).isEqualTo("2³ · 3² · 5");
    assertThat(shown("ifactor(-12)")).isEqualTo("-2² · 3");
    assertThat(shown("ifactor(0)")).isEqualTo("0");
    assertThat(shown("ifactor(-1)")).isEqualTo("-1");
    assertThat(shown("ifactor(97)")).isEqualTo("97");
    assertThat(shown("ifactor(-97)")).isEqualTo("-97");
    assertThat(shown("ifactor(2^5)")).isEqualTo("2⁵");
    assertThat(shown("ifactor(2^64 + 1)")).isEqualTo("274177 · 67280421310721");
    assertThat(shown("ifactor(x)")).isEqualTo("ifactor(x)");
    assertThat(error("ifactor(12/35)")).isEqualTo("ifactor takes an integer");
    assertThat(AsciiPrinter.INSTANCE.display(value("ifactor(-360)"))).isEqualTo("-2^3*3^2*5");
    assertThat(LatexPrinter.INSTANCE.display(value("ifactor(360)")))
        .isEqualTo("2^{3} \\cdot 3^{2} \\cdot 5");
  }

  @Test
  void theProductIsKeptOnlyForDisplay() {
    assertThat(value("ifactor(360)")).isInstanceOf(Apply.class);
    assertThat(value("ifactor(360) + 1")).isEqualTo(Num.of(361));
    assertThat(value("ifactor(360) = 360")).isEqualTo(Bool.TRUE);
    assertThat(shown("2 ifactor(12)")).isEqualTo("24");
    assertThat(shown("[ifactor(12), ifactor(18)]")).isEqualTo("⟨2² · 3, 2 · 3²⟩");
    assertThat(shown("[ifactor(12), 1] + [1, 1]")).isEqualTo("⟨13, 2⟩");
    assertThat(shown("numeric(ifactor(360))")).isEqualTo("360.0");
    assertThat(shown("exact(ifactor(360))")).isEqualTo("360");
    assertThat(shown("sin(ifactor(12))")).isEqualTo("sin(12)");
    assertThat(shown("f(x) := x + 1\nf(ifactor(12))")).isEqualTo("13");
    assertThat(shown("isprime(ifactor(12))")).isEqualTo("false");
  }

  @Test
  void theDivisorFunctions() {
    assertThat(shown("divisors(12)")).isEqualTo("⟨1, 2, 3, 4, 6, 12⟩");
    assertThat(shown("divisors(-12)")).isEqualTo("⟨1, 2, 3, 4, 6, 12⟩");
    assertThat(shown("divisors(1)")).isEqualTo("⟨1⟩");
    assertThat(shown("divisors(0)")).isEqualTo("undefined");
    assertThat(shown("numdivisors(360)")).isEqualTo("24");
    assertThat(shown("numdivisors(1)")).isEqualTo("1");
    assertThat(shown("sigma(12)")).isEqualTo("28");
    assertThat(shown("sigma(-12)")).isEqualTo("28");
    assertThat(shown("totient(36)")).isEqualTo("12");
    assertThat(shown("totient(1)")).isEqualTo("1");
    assertThat(shown("totient(97)")).isEqualTo("96");
    assertThat(shown("mobius(30)")).isEqualTo("-1");
    assertThat(shown("mobius(12)")).isEqualTo("0");
    assertThat(shown("mobius(1)")).isEqualTo("1");
    assertThat(shown("mobius(0)")).isEqualTo("undefined");
    assertThat(shown("totient(n)")).isEqualTo("totient(n)");
    assertThat(error("divisors(210^20)")).isEqualTo("divisors lists at most 100000 divisors");
  }

  @Test
  void theSteps() {
    assertThat(steps("steps(ifactor(360))"))
        .containsExactly(
            "Trial division by the primes up to 10⁴ finds 2, 3 and leaves 5, which is prime."
                + " ifactor(360) = 2³ · 3² · 5");
    assertThat(steps("steps(ifactor(1000000016000000063))"))
        .containsExactly(
            "Pollard’s rho method (Brent’s variant) finds the factor 1000000007."
                + " ifactor(1000000016000000063) = 1000000007 · 1000000009");
    assertThat(steps("steps(ifactor(97))"))
        .containsExactly("97 is prime, so it is its own factorisation");
    assertThat(steps("steps(ifactor(2 nextprime(10^30)))").getLast())
        .startsWith("Trial division by the primes up to 10⁴ finds 2.")
        .endsWith(
            "1000000000000000000000000000057 passes the Baillie–PSW test, which no known"
                + " composite passes, though that is not a proof that it is prime");
  }

  @Test
  void aHardCompositeWithNoSmallFactorStays() {
    String n = "241191224237888947190361313462598123228390025041668594610227";
    assertThat(shown("ifactor(" + n + ")")).isEqualTo("ifactor(" + n + ")");
    assertThat(steps("steps(ifactor(" + n + "))"))
        .containsExactly(
            "No factor of the composite part "
                + n
                + " is found within the work allowed, so ifactor("
                + n
                + ") is left as it is");
  }

  @Test
  void anUnfinishedFactorisationIsNarrated() {
    Apply call = Apply.of(mathcanvas.engine.expr.Symbol.of("ifactor"), Num.of(10));
    java.util.TreeMap<String, mathcanvas.engine.expr.Expr> values = new java.util.TreeMap<>();
    values.put("found", mathcanvas.engine.expr.Vector.of(Num.of(2)));
    values.put("left", mathcanvas.engine.expr.Vector.of(Num.of(5)));
    mathcanvas.engine.rule.Rewrite r =
        new mathcanvas.engine.rule.Rewrite(
            call,
            call,
            "ifactor",
            "unfinished",
            mathcanvas.engine.rule.Path.ROOT,
            mathcanvas.engine.match.Bindings.EMPTY,
            values);
    mathcanvas.engine.rule.Trace trace =
        new mathcanvas.engine.rule.Trace(call, call, List.of(r), List.of());
    assertThat(Narration.steps(trace, Context.DEFAULT, mathcanvas.engine.rule.StepText.standard()))
        .extracting(Step::text)
        .containsExactly(
            "The primes found are 2. No factor of the composite part 5 is found within the work"
                + " allowed, so ifactor(10) is left as it is");
  }
}
