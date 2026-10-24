package mathcanvas.engine.expr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.expr.Piecewise.Piece;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import org.junit.jupiter.api.Test;

class ExprTest {

  private static final Symbol X = Symbol.of("x");
  private static final Symbol Y = Symbol.of("y");

  @Test
  void numbersCompareByTheirValueInTheTower() {
    assertThat(Num.of(1)).isEqualTo(Num.ONE).isEqualTo(Num.of(Int.ONE));
    assertThat(Num.of(Rational.of(2, 2))).isEqualTo(Num.ONE);
    assertThat(Num.of(Real.of("1.0"))).isNotEqualTo(Num.ONE);
    assertThat(Num.of(Real.of("1.0"))).isEqualTo(Num.of(Real.of("1.00")));
    assertThatThrownBy(() -> Num.of(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void atomsAreValidated() {
    assertThatThrownBy(() -> Symbol.of("")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Symbol.of(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> Constant.of(null)).isInstanceOf(NullPointerException.class);
    assertThat(Constant.of(Constant.Kind.PI)).isEqualTo(Constant.PI);
    assertThat(Bool.of(true)).isEqualTo(Bool.TRUE);
  }

  @Test
  void sumsAndProductsNeedTwoOperandsAndAreRaw() {
    assertThatThrownBy(() -> Sum.of(X)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Product.of(List.of())).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Sum.of(X, null)).isInstanceOf(NullPointerException.class);
    // Raw: nothing is flattened, sorted or collected.
    assertThat(Sum.of(Y, X, X).terms()).containsExactly(Y, X, X);
    assertThat(Product.of(Product.of(X, Y), X).factors()).hasSize(2);
  }

  @Test
  void operandListsAreImmutableCopies() {
    List<Expr> terms = new ArrayList<>(List.of(X, Y));
    Sum sum = Sum.of(terms);
    terms.add(X);
    assertThat(sum.terms()).hasSize(2);
    assertThatThrownBy(() -> sum.terms().add(X)).isInstanceOf(UnsupportedOperationException.class);
    assertThat(Sum.of(X, Y)).isEqualTo(Sum.of(List.of(X, Y))).hasSameHashCodeAs(Sum.of(X, Y));
  }

  @Test
  void collectionsAreValidated() {
    assertThatThrownBy(() -> Matrix.of(List.of())).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Matrix.of(List.of(List.of())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Matrix.of(List.of(List.of(X, Y), List.of(X))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(Matrix.of(List.of(List.of(X), List.of(Y))).rows()).hasSize(2);
    assertThatThrownBy(Vector::of).isInstanceOf(IllegalArgumentException.class);
    assertThat(ListExpr.of().items()).isEmpty();
    assertThat(SetExpr.of(X, X).items()).containsExactly(X, X);
  }

  @Test
  void lambdaParametersAreDistinct() {
    assertThatThrownBy(() -> Lambda.of(List.of(), X)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Lambda.of(List.of(X, X), X))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(Lambda.of(List.of(X, Y), X).params()).containsExactly(X, Y);
    assertThat(Lambda.of(X, X)).isNotEqualTo(Lambda.of(Y, Y));
  }

  @Test
  void unevaluatedFormsAreValidated() {
    assertThatThrownBy(() -> Derivative.of(X, X, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Integral(X, X, Num.ZERO, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Integral.definite(X, X, null, Num.ONE))
        .isInstanceOf(NullPointerException.class);
    assertThat(Integral.indefinite(X, X).isDefinite()).isFalse();
    assertThat(Integral.definite(X, X, Num.ZERO, Num.ONE).isDefinite()).isTrue();
    assertThat(Integral.indefinite(X, X)).isEqualTo(new Integral(X, X, null, null));
    assertThatThrownBy(() -> Piecewise.of(List.of(), Constant.UNDEFINED))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Limit.of(X, X, Num.ZERO, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void debugForms() {
    assertThat(Num.of(Rational.of(1, 2))).hasToString("Num 1/2");
    assertThat(Num.of(Complex.I)).hasToString("Num i");
    assertThat(Constant.PI).hasToString("Constant PI");
    assertThat(Bool.FALSE).hasToString("Bool false");
    assertThat(X).hasToString("Symbol x");
    assertThat(Sum.of(Num.ONE, X)).hasToString("Sum[Num 1, Symbol x]");
    assertThat(Product.of(Num.MINUS_ONE, X)).hasToString("Product[Num -1, Symbol x]");
    assertThat(Power.of(X, Num.of(2))).hasToString("Power[Symbol x; Num 2]");
    assertThat(Apply.of(Symbol.of("sin"), X)).hasToString("Apply[sin; Symbol x]");
    assertThat(Apply.of(Symbol.of("assumptions"))).hasToString("Apply[assumptions; ]");
    assertThat(Lambda.of(List.of(X, Y), X)).hasToString("Lambda[x, y; Symbol x]");
    assertThat(Relation.of(Relation.Op.LE, X, Num.ONE))
        .hasToString("Relation[LE; Symbol x, Num 1]");
    assertThat(ListExpr.of()).hasToString("ListExpr[]");
    assertThat(Vector.of(X, Y)).hasToString("Vector[Symbol x, Symbol y]");
    assertThat(SetExpr.of(Num.ONE)).hasToString("SetExpr[Num 1]");
    assertThat(Matrix.of(List.of(List.of(Num.ONE, X), List.of(Y, Num.ZERO))))
        .hasToString("Matrix[[Num 1, Symbol x], [Symbol y, Num 0]]");
    assertThat(Interval.of(Num.ZERO, Constant.INFINITY, true, false))
        .hasToString("Interval[Num 0, Constant INFINITY; closed, open]");
    assertThat(Piecewise.of(List.of(new Piece(X, Bool.TRUE)), Constant.UNDEFINED))
        .hasToString("Piecewise[Piece[Symbol x; Bool true]; Constant UNDEFINED]");
    assertThat(Derivative.of(X, X, 2)).hasToString("Derivative[Symbol x; x; 2]");
    assertThat(Integral.indefinite(X, X)).hasToString("Integral[Symbol x; x]");
    assertThat(Integral.definite(X, X, Num.ZERO, Num.ONE))
        .hasToString("Integral[Symbol x; x; Num 0, Num 1]");
    assertThat(Limit.of(X, X, Num.ZERO, Limit.Direction.RIGHT))
        .hasToString("Limit[Symbol x; x; Num 0; RIGHT]");
    assertThat(BigSum.of(X, X, Num.ONE, Y)).hasToString("BigSum[Symbol x; x; Num 1, Symbol y]");
    assertThat(BigProduct.of(X, X, Num.ONE, Y))
        .hasToString("BigProduct[Symbol x; x; Num 1, Symbol y]");
  }
}
