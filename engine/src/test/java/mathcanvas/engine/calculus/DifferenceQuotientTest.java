package mathcanvas.engine.calculus;

import static mathcanvas.engine.testing.Inputs.parse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Relation;
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
  void aCrossingOfALogarithmsCutIsSkipped() {
    // At x = −7 the base a^x is a negative real, so (a^x)^tanh(e x) sits on the principal
    // logarithm's cut and x₀ ± h lie on its two sides: the 1i landing gate's failure.
    Expr f = parse("(a^x)^tanh(e x)");
    Expr d = Calculus.differentiate(f, X, 1, Context.DEFAULT).expr();
    Map<Symbol, Numeric> point =
        Map.of(X, Rational.of(-7, 1), Symbol.of("a"), Rational.of(-116, 7));
    assertThat(DifferenceQuotient.check(f, d, X, point, Assumptions.EMPTY.facts(X)))
        .isEqualTo(DifferenceQuotient.Outcome.ON_A_CUT);
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

  @Test
  void aPointOutsideTheFunctionsDomainIsSkipped() {
    // acsch(a·x·e) has no value at a = 0, but its derivatives do: the second carries sign(a),
    // which is 0 there, while the first's extension -1/(x√(e²a²x² + 1)) has slope 1/x², so the
    // second-derivative property compared them there (the 1j landing gate's failure).
    Symbol a = Symbol.of("a");
    Expr e = parse("acsch(a x e)");
    Context ctx =
        Context.DEFAULT.withAssumptions(Assumptions.EMPTY.with((Relation) parse("a >= 0")));
    Expr first = Calculus.differentiate(e, X, 1, ctx).expr();
    Expr second = Calculus.differentiate(e, X, 2, ctx).expr();
    Map<Symbol, Numeric> atZero = Map.of(X, Rational.of(37, 1), a, Rational.of(0, 1));
    assertThat(DifferenceQuotient.defined(e, atZero)).isFalse();
    assertThatThrownBy(
            () -> DifferenceQuotient.check(first, second, X, atZero, Assumptions.EMPTY.facts(X)))
        .isInstanceOf(AssertionError.class);
    Map<Symbol, Numeric> inside = Map.of(X, Rational.of(37, 1), a, Rational.of(1, 2));
    assertThat(DifferenceQuotient.defined(e, inside)).isTrue();
    assertThat(DifferenceQuotient.check(first, second, X, inside, Assumptions.EMPTY.facts(X)))
        .isEqualTo(DifferenceQuotient.Outcome.COMPARED);
  }
}
