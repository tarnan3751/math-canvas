package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The counting functions ({@code docs/language.md} §14.23) through the evaluator: values, the
 * conventions at negative and out-of-range arguments, the caps, and the calls left.
 */
class CombinatoricsFunctionsTest {

  @Test
  void chooseAndArrangements() {
    assertThat(shown("choose(5, 2)")).isEqualTo("10");
    assertThat(shown("choose(n, 2)")).isEqualTo("binomial(n, 2)");
    assertThat(shown("permutations(5, 2)")).isEqualTo("20");
    assertThat(shown("permutations(5, 0)")).isEqualTo("1");
    assertThat(shown("permutations(5, 6)")).isEqualTo("0");
    assertThat(shown("permutations(5, -1)")).isEqualTo("0");
    assertThat(shown("permutations(-2, 2)")).isEqualTo("6");
    assertThat(shown("permutations(n, 2)")).isEqualTo("permutations(n, 2)");
    assertThat(shown("multinomial(4, [2, 1, 1])")).isEqualTo("12");
    assertThat(shown("multinomial(4, [2, 1])")).isEqualTo("0");
    assertThat(shown("multinomial(0, [])")).isEqualTo("1");
    assertThat(shown("multinomial(3, [4, -1])")).isEqualTo("0");
    assertThat(error("permutations(5/2, 1)")).isEqualTo("permutations takes integers");
  }

  @Test
  void sequences() {
    assertThat(shown("fibonacci(0)")).isEqualTo("0");
    assertThat(shown("fibonacci(10)")).isEqualTo("55");
    assertThat(shown("fibonacci(-8)")).isEqualTo("-21");
    assertThat(shown("fibonacci(-7)")).isEqualTo("13");
    assertThat(shown("fibonacci(100)")).isEqualTo("354224848179261915075");
    assertThat(shown("lucas(0)")).isEqualTo("2");
    assertThat(shown("lucas(10)")).isEqualTo("123");
    assertThat(shown("lucas(-5)")).isEqualTo("-11");
    assertThat(shown("catalan(5)")).isEqualTo("42");
    assertThat(shown("catalan(0)")).isEqualTo("1");
    assertThat(shown("catalan(-1)")).isEqualTo("0");
    assertThat(shown("bell(5)")).isEqualTo("52");
    assertThat(shown("bell(0)")).isEqualTo("1");
    assertThat(shown("stirling2(5, 2)")).isEqualTo("15");
    assertThat(shown("stirling2(0, 0)")).isEqualTo("1");
    assertThat(shown("stirling2(5, 0)")).isEqualTo("0");
    assertThat(shown("stirling2(3, 5)")).isEqualTo("0");
    assertThat(shown("partitions(100)")).isEqualTo("190569292");
    assertThat(shown("partitions(0)")).isEqualTo("1");
    assertThat(shown("partitions(-3)")).isEqualTo("0");
    assertThat(shown("fibonacci(n)")).isEqualTo("fibonacci(n)");
    assertThat(error("lucas(1.5)")).isEqualTo("lucas takes an integer");
  }

  @Test
  void beyondTheCapsTheCallStays() {
    assertThat(shown("bell(2001)")).isEqualTo("bell(2001)");
    assertThat(shown("stirling2(2001, 3)")).isEqualTo("stirling2(2001, 3)");
    assertThat(shown("partitions(10001)")).isEqualTo("partitions(10001)");
    assertThat(shown("fibonacci(10^7)")).isEqualTo("fibonacci(10000000)");
    assertThat(shown("permutations(10^6, 10^6)")).isEqualTo("permutations(1000000, 1000000)");
  }

  @Test
  void identities() {
    // Σ S(n, k) over k is B(n); L(n) = F(n − 1) + F(n + 1); C(n) = binomial(2n, n)/(n + 1)
    assertThat(shown("sum(stirling2(7, k), k, 0, 7)")).isEqualTo(shown("bell(7)"));
    assertThat(shown("lucas(20) - fibonacci(19) - fibonacci(21)")).isEqualTo("0");
    assertThat(shown("catalan(12) - binomial(24, 12)/13")).isEqualTo("0");
  }
}
