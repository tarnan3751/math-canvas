package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/**
 * Programs computing known functions agree with the engine's own builtins at random inputs ({@code
 * docs/language.md} §19): factorial by a loop and by recursion, Fibonacci by a loop and by an if
 * expression, and the gcd by Euclid's algorithm.
 */
class ProgramProperties {

  private static final String LOOP_FACTORIAL =
      "function fact(n)\n r := 1\n for k in 1..n do r := r k end\n return r\nend\n";

  private static final String RECURSIVE_FACTORIAL =
      "function rfact(n)\n if n = 0 then return 1 end\n return n rfact(n - 1)\nend\n";

  private static final String LOOP_FIBONACCI =
      """
      function fib(n)
        a := 0; b := 1
        for k in 1..n
          t := a + b
          a := b
          b := t
        end
        return a
      end
      """;

  private static final String EXPRESSION_FIBONACCI =
      "efib(n) := if n < 2 then n else efib(n - 1) + efib(n - 2)\n";

  private static final String EUCLID =
      """
      function euclid(a, b)
        a := abs(a); b := abs(b)
        while b ≠ 0
          t := a mod b
          a := b
          b := t
        end
        return a
      end
      """;

  @Property(tries = 40)
  void factorial(@ForAll @IntRange(min = 0, max = 60) int n) {
    assertThat(Run.value(LOOP_FACTORIAL + "fact(" + n + ")")).isEqualTo(Run.value(n + "!"));
    assertThat(Run.value(RECURSIVE_FACTORIAL + "rfact(" + n + ")")).isEqualTo(Run.value(n + "!"));
  }

  @Property(tries = 40)
  void fibonacci(@ForAll @IntRange(min = 0, max = 80) int n) {
    assertThat(Run.value(LOOP_FIBONACCI + "fib(" + n + ")"))
        .isEqualTo(Run.value("fibonacci(" + n + ")"));
  }

  @Property(tries = 15)
  void fibonacciByAnIfExpression(@ForAll @IntRange(min = 0, max = 12) int n) {
    assertThat(Run.value(EXPRESSION_FIBONACCI + "efib(" + n + ")"))
        .isEqualTo(Run.value("fibonacci(" + n + ")"));
  }

  @Property(tries = 60)
  void euclid(
      @ForAll @IntRange(min = -100000, max = 100000) int a,
      @ForAll @IntRange(min = -100000, max = 100000) int b) {
    assertThat(Run.value(EUCLID + "euclid(" + a + ", " + b + ")"))
        .isEqualTo(Run.value("gcd(" + a + ", " + b + ")"));
  }
}
