package mathcanvas.engine.parse;

import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Symbol;
import org.junit.jupiter.api.Test;

class TypedNodesTest {

  static final Symbol X = Symbol.of("x");
  static final Expr SQUARE = Power.of(X, Num.of(2));

  @Test
  void callsWhoseArgumentsFitAreTyped() {
    assertThat(TypedNodes.of(Apply.of(Symbol.of("diff"), SQUARE, X, Num.of(2))))
        .isEqualTo(Derivative.of(SQUARE, X, 2));
    assertThat(TypedNodes.of(Apply.of(Symbol.of("integrate"), SQUARE, X)))
        .isEqualTo(Integral.indefinite(SQUARE, X));
    assertThat(TypedNodes.of(Apply.of(Symbol.of("diff"), SQUARE, X, Num.of(2))))
        .isEqualTo(Parser.parseExpression("diff(x^2, x, 2)", Names.NONE));
  }

  @Test
  void otherCallsStayAsTheyAre() {
    Apply order = Apply.of(Symbol.of("diff"), SQUARE, X, Symbol.of("n"));
    assertThat(TypedNodes.of(order)).isSameAs(order);
    Apply zero = Apply.of(Symbol.of("diff"), SQUARE, X, Num.of(0));
    assertThat(TypedNodes.of(zero)).isSameAs(zero);
    Apply sided = Apply.of(Symbol.of("limit"), SQUARE, X, Num.of(0), Symbol.of("left"));
    assertThat(TypedNodes.of(sided)).isSameAs(sided);
    Apply sin = Apply.of(Symbol.of("sin"), X);
    assertThat(TypedNodes.of(sin)).isSameAs(sin);
  }
}
