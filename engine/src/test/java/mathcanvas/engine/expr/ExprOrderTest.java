package mathcanvas.engine.expr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import org.junit.jupiter.api.Test;

/** Cohen's examples (§3.1) and the mixed-kind rules, each as an ascending pair. */
class ExprOrderTest {

  private static final Symbol A = Symbol.of("a");
  private static final Symbol B = Symbol.of("b");
  private static final Symbol C = Symbol.of("c");
  private static final Symbol D = Symbol.of("d");
  private static final Symbol X = Symbol.of("x");
  private static final Symbol Y = Symbol.of("y");
  private static final Symbol F = Symbol.of("f");
  private static final Symbol G = Symbol.of("g");

  private static Num n(long k) {
    return Num.of(k);
  }

  private static Power pow(Expr b, Expr e) {
    return Power.of(b, e);
  }

  private static void ascending(Expr... exprs) {
    for (int i = 0; i < exprs.length; i++) {
      for (int j = 0; j < exprs.length; j++) {
        int order = ExprOrder.INSTANCE.compare(exprs[i], exprs[j]);
        assertThat(Integer.signum(order))
            .as("%s vs %s", exprs[i], exprs[j])
            .isEqualTo(Integer.compare(i, j));
      }
    }
  }

  @Test
  void numbers() {
    ascending(
        n(-1),
        Num.of(Rational.of(1, 2)),
        n(1),
        Num.of(Real.of("1.0")),
        n(2),
        Num.of(Complex.of(n(-5).value().re(), n(1).value().re())),
        Num.of(Complex.I),
        Num.of(Complex.of(n(1).value().re(), n(1).value().re())));
  }

  @Test
  void numbersComeFirst() {
    ascending(n(2), Constant.PI, X);
    for (Expr e :
        List.of(pow(X, n(2)), Product.of(n(2), X), Sum.of(n(1), X), Apply.of(F, X), Bool.FALSE)) {
      ascending(n(100), e);
    }
  }

  @Test
  void productsAndSumsCompareFromTheLastOperand() {
    ascending(Sum.of(A, B), Sum.of(A, C));
    ascending(Sum.of(A, C, D), Sum.of(B, C, D));
    ascending(Sum.of(C, D), Sum.of(B, C, D));
    ascending(Product.of(A, B), Product.of(A, C));
    ascending(Product.of(C, D), Product.of(B, C, D));
  }

  @Test
  void powersCompareByBaseThenExponent() {
    ascending(pow(Sum.of(n(1), X), n(3)), pow(Sum.of(n(1), Y), n(2)));
    ascending(pow(X, n(2)), pow(X, n(3)));
    ascending(pow(X, Num.of(Rational.of(1, 2))), X, pow(X, n(1)), pow(X, n(2)));
  }

  @Test
  void applicationsByNameThenArguments() {
    ascending(Apply.of(F, X), Apply.of(G, X));
    ascending(Apply.of(F, X), Apply.of(F, X, Y));
    ascending(Apply.of(F, X), Apply.of(F, Y));
    ascending(F, Apply.of(F, X), G);
  }

  @Test
  void cohensMixedRules() {
    // O-8: a product against a non-product is compared as against a one-factor product.
    ascending(Product.of(A, pow(X, n(2))), pow(X, n(3)));
    ascending(X, Product.of(n(2), X), Product.of(n(3), X));
    ascending(Y, Product.of(X, Y));
    ascending(pow(X, n(2)), Product.of(X, Y));
    // O-9: a power against a non-power is compared as against a power with exponent 1.
    ascending(pow(Sum.of(n(1), X), n(3)), Sum.of(n(1), Y));
    ascending(X, pow(X, n(2)));
    // O-10: a sum against a non-sum is compared as against a one-term sum.
    ascending(Sum.of(n(1), X), Y);
    ascending(X, Sum.of(n(1), X));
    // The examples in the brief: x, 2x, x^2, x·y.
    ascending(X, Product.of(n(2), X), pow(X, n(2)), Y, Product.of(X, Y));
    ascending(Constant.PI, Product.of(n(2), Constant.PI), X);
  }

  @Test
  void otherKindsComeAfterInAFixedOrder() {
    Expr[] kinds = {
      Constant.PI,
      Constant.UNDEFINED,
      Constant.INTEGERS,
      X,
      Derivative.of(X, X, 1),
      Integral.indefinite(X, X),
      Integral.definite(X, X, n(0), n(1)),
      Limit.of(X, X, n(0), Limit.Direction.BOTH),
      BigSum.of(X, X, n(1), Y),
      BigProduct.of(X, X, n(1), Y),
      Lambda.of(X, X),
      Relation.of(Relation.Op.EQ, X, n(1)),
      Relation.of(Relation.Op.IN, X, Constant.INTEGERS),
      Interval.of(n(0), n(1), false, true),
      Interval.of(n(0), n(1), true, false),
      Vector.of(X),
      Matrix.of(List.of(List.of(X))),
      ListExpr.of(),
      ListExpr.of(X),
      SetExpr.of(X),
      Bool.FALSE,
      Bool.TRUE
    };
    ascending(kinds);
  }

  /** A fixed list sorts to a literal sequence: the order uses no hash and no identity. */
  @Test
  void sortIsDeterministic() {
    List<Expr> exprs =
        new ArrayList<>(
            List.of(
                Sum.of(n(1), X),
                Bool.TRUE,
                Y,
                Product.of(n(2), X),
                pow(X, n(2)),
                Apply.of(F, X),
                Num.of(Complex.I),
                Constant.E,
                n(3),
                X,
                Product.of(X, Y),
                SetExpr.of(n(1))));
    exprs.sort(ExprOrder.INSTANCE);
    assertThat(exprs.stream().map(Expr::toString))
        .containsExactly(
            "Num 3",
            "Num i",
            "Constant E",
            "Apply[f; Symbol x]",
            "Symbol x",
            "Product[Num 2, Symbol x]",
            "Power[Symbol x; Num 2]",
            "Sum[Num 1, Symbol x]",
            "Symbol y",
            "Product[Symbol x, Symbol y]",
            "SetExpr[Num 1]",
            "Bool true");
  }
}
