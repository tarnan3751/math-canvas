package mathcanvas.engine.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Lambda;
import mathcanvas.engine.expr.Limit;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Piecewise;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import org.junit.jupiter.api.Test;

class OperandsTest {

  private static final Symbol X = Symbol.of("x");
  private static final Symbol Y = Symbol.of("y");
  private static final Num ONE = Num.ONE;

  static final List<Expr> EVERY_KIND =
      List.of(
          ONE,
          Constant.PI,
          X,
          Sum.of(X, ONE),
          Power.of(X, ONE),
          Apply.of(Symbol.of("f"), X, Y),
          Lambda.of(List.of(X, Y), Sum.of(X, Y)),
          Relation.of(Relation.Op.LT, X, ONE),
          ListExpr.of(X),
          Vector.of(X, Y),
          SetExpr.of(X),
          Matrix.of(List.of(List.of(X, Y), List.of(ONE, X))),
          new Interval(X, Y, true, false),
          Piecewise.of(List.of(new Piecewise.Piece(X, Y)), ONE),
          Derivative.of(X, X, 2),
          Integral.indefinite(X, X),
          Integral.definite(X, X, ONE, Y),
          Limit.of(X, X, ONE, Limit.Direction.LEFT),
          BigSum.of(X, X, ONE, Y),
          BigProduct.of(X, X, ONE, Y));

  @Test
  void rebuildWithTheSameOperandsGivesTheSameNode() {
    for (Expr e : EVERY_KIND) {
      assertThat(Operands.rebuild(e, Operands.of(e))).as(e.toString()).isEqualTo(e);
      assertThat(Operands.sameShape(e, e)).isTrue();
    }
  }

  @Test
  void indexScheme() {
    assertThat(Operands.of(Matrix.of(List.of(List.of(X, Y), List.of(ONE, X)))))
        .containsExactly(X, Y, ONE, X);
    assertThat(Operands.of(Piecewise.of(List.of(new Piecewise.Piece(X, Y)), ONE)))
        .containsExactly(X, Y, ONE);
    assertThat(Operands.of(Lambda.of(List.of(X, Y), ONE))).containsExactly(X, Y, ONE);
    assertThat(Operands.of(Apply.of(Symbol.of("f"), ONE))).containsExactly(ONE);
  }

  @Test
  void boundPositions() {
    List<Integer> bound = new ArrayList<>();
    Expr lambda = Lambda.of(List.of(X, Y), ONE);
    for (int i = 0; i < 3; i++) {
      if (Operands.isBound(lambda, i)) {
        bound.add(i);
      }
    }
    assertThat(bound).containsExactly(0, 1);
    assertThat(Operands.isBound(Derivative.of(X, X, 1), 1)).isTrue();
    assertThat(Operands.isBound(Integral.definite(X, X, ONE, Y), 1)).isTrue();
    assertThat(Operands.isBound(Limit.of(X, X, ONE, Limit.Direction.BOTH), 0)).isFalse();
    assertThat(Operands.isBound(Sum.of(X, Y), 1)).isFalse();
    assertThatThrownBy(() -> Operands.rebuild(Derivative.of(X, X, 1), List.of(X, ONE)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Operands.rebuild(Sum.of(X, Y), List.of(X)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shapes() {
    assertThat(
            Operands.sameShape(
                Relation.of(Relation.Op.LT, X, ONE), Relation.of(Relation.Op.GT, X, ONE)))
        .isFalse();
    assertThat(Operands.sameShape(Derivative.of(X, X, 1), Derivative.of(Y, Y, 1))).isTrue();
    assertThat(Operands.sameShape(Integral.indefinite(X, X), Integral.definite(X, X, ONE, Y)))
        .isFalse();
    assertThat(Operands.sameShape(Vector.of(X), Vector.of(X, Y))).isFalse();
  }
}
