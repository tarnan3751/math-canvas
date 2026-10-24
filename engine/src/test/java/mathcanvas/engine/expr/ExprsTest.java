package mathcanvas.engine.expr;

import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.engine.number.Rational;
import org.junit.jupiter.api.Test;

class ExprsTest {

  private static final Symbol A = Symbol.of("a");
  private static final Symbol B = Symbol.of("b");

  @Test
  void buildsTheRawShapes() {
    assertThat(Exprs.neg(A)).isEqualTo(Product.of(Num.MINUS_ONE, A));
    assertThat(Exprs.sub(A, B)).isEqualTo(Sum.of(A, Product.of(Num.MINUS_ONE, B)));
    assertThat(Exprs.inv(A)).isEqualTo(Power.of(A, Num.MINUS_ONE));
    assertThat(Exprs.div(A, B)).isEqualTo(Product.of(A, Power.of(B, Num.MINUS_ONE)));
    assertThat(Exprs.sqrt(A)).isEqualTo(Power.of(A, Num.of(Rational.of(1, 2))));
  }
}
