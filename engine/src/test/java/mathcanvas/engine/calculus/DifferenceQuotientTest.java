package mathcanvas.engine.calculus;

import static mathcanvas.engine.calculus.DiffCases.parse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.rule.Context;
import org.junit.jupiter.api.Test;

class DifferenceQuotientTest {

  private static final Symbol X = Symbol.of("x");

  @Test
  void aSmoothFunctionIsCompared() {
    Expr f = parse("sin(x^2)");
    Expr d = Calculus.differentiate(f, X, 1, Context.DEFAULT).expr();
    assertThat(
            DifferenceQuotient.check(
                f, d, X, Map.of(X, Rational.of(3, 2)), Assumptions.EMPTY.facts(X)))
        .isEqualTo(DifferenceQuotient.Outcome.COMPARED);
  }

  @Test
  void anExponentBeyondTheBoundIsRefused() {
    // (πx²)^(−2x) at x = −133/2 is about 10⁵⁵²: (−1) to that power aliases at every step.
    Expr f = parse("(-1)^((pi x^2)^(-2x))");
    Map<Symbol, Numeric> point = Map.of(X, Rational.of(-133, 2));
    assertThat(DifferenceQuotient.exponentsBounded(f, point)).isFalse();
    Expr d = Calculus.differentiate(f, X, 1, Context.DEFAULT).expr();
    assertThatThrownBy(() -> DifferenceQuotient.check(f, d, X, point, Assumptions.EMPTY.facts(X)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(DifferenceQuotient.exponentsBounded(f, Map.of(X, Rational.of(1, 2)))).isTrue();
  }
}
