package mathcanvas.engine.calculus;

import static mathcanvas.engine.calculus.DiffCases.canonical;
import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.engine.expr.Symbol;
import org.junit.jupiter.api.Test;

class FreeTest {

  private static final Symbol X = Symbol.of("x");

  private static boolean free(String e) {
    return Free.of(canonical(e), X);
  }

  @Test
  void symbolsAndArithmetic() {
    assertThat(free("2 a + pi^y")).isTrue();
    assertThat(free("sin(a) + x")).isFalse();
    assertThat(free("y = 1")).isTrue();
    assertThat(free("[a, x]")).isFalse();
  }

  @Test
  void bindersHideTheirVariableInTheirBodyOnly() {
    assertThat(free("sum(x^2, x, 1, n)")).isTrue();
    assertThat(free("sum(x^2, x, 1, x)")).isFalse();
    assertThat(free("sum(k x, k, 1, n)")).isFalse();
    assertThat(free("product(x, x, 1, 3)")).isTrue();
    assertThat(free("limit(sin(x)/x, x, 0)")).isTrue();
    assertThat(free("limit(sin(t)/t, t, x)")).isFalse();
    assertThat(free("integrate(x^2, x, 0, 1)")).isTrue();
    assertThat(free("integrate(t, t, 0, x)")).isFalse();
    assertThat(free("(x -> x^2)")).isTrue();
    assertThat(free("(t -> t x)")).isFalse();
  }

  @Test
  void aDerivativeAndAnIndefiniteIntegralKeepTheirVariableFree() {
    assertThat(free("diff(factorial(x), x)")).isFalse();
    assertThat(free("diff(factorial(y), y)")).isTrue();
    assertThat(free("integrate(1, x)")).isFalse();
    assertThat(free("integrate(t, t)")).isTrue();
    assertThat(free("integrate(x t, t)")).isFalse();
  }

  @Test
  void aCommandCallWithItsOwnVariableIsFreeOnlyWithoutTheName() {
    assertThat(free("solve(t^2 = a, t)")).isTrue();
    assertThat(free("solve(x^2 = a, x)")).isFalse();
    assertThat(free("solve(t^2 = x, t)")).isFalse();
  }
}
