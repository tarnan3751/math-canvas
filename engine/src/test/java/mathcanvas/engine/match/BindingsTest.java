package mathcanvas.engine.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import org.junit.jupiter.api.Test;

class BindingsTest {

  private static final Symbol A = Symbol.of("a");
  private static final Symbol B = Symbol.of("b");
  private static final Symbol C = Symbol.of("c");
  private static final Symbol F = Symbol.of("f");

  private static Symbol v(String name) {
    return Symbol.of(name);
  }

  private static Bindings match(Expr pattern, Expr subject) {
    return Matcher.match(Pattern.of(pattern), Canonical.of(subject)).orElseThrow();
  }

  @Test
  void variablesAreReplaced() {
    Bindings b = match(Product.of(v("x_"), v("y_")), Product.of(A, B));
    assertThat(b.substituteInto(Sum.of(v("x_"), Product.of(Num.of(2), v("y_")))))
        .isEqualTo(Sum.of(A, Product.of(Num.of(2), B)));
    assertThat(b.substituteInto(Apply.of(F, v("x_"), C))).isEqualTo(Apply.of(F, A, C));
  }

  @Test
  void sequencesSpliceIntoTheirOwnKindAndStandCombinedElsewhere() {
    Bindings sum = match(Sum.of(v("x_"), v("r___")), Sum.of(A, B, C));
    assertThat(sum.substituteInto(Sum.of(v("r___"), Num.of(1)))).isEqualTo(Sum.of(B, C, Num.of(1)));
    assertThat(sum.substituteInto(Product.of(Num.of(2), v("r___"))))
        .isEqualTo(Product.of(Num.of(2), Sum.of(B, C)));
    assertThat(sum.substituteInto(Apply.of(F, v("r___")))).isEqualTo(Apply.of(F, Sum.of(B, C)));
    Bindings empty = match(Sum.of(v("x_"), v("r___")), A);
    assertThat(empty.substituteInto(Sum.of(v("x_"), v("r___")))).isEqualTo(A);
    assertThat(empty.substituteInto(Product.of(v("r___"), v("x_"))))
        .isEqualTo(Product.of(Num.ZERO, A));
    Bindings product = match(Product.of(v("x_"), v("r___")), Product.of(A, B, C));
    assertThat(product.substituteInto(Product.of(v("r___"), v("r___"))))
        .isEqualTo(Product.of(B, C, B, C));
    Bindings args = match(Apply.of(F, v("x_"), v("r___")), Apply.of(F, A, B, C));
    assertThat(args.substituteInto(Apply.of(Symbol.of("g"), v("r___"), v("x_"))))
        .isEqualTo(Apply.of(Symbol.of("g"), B, C, A));
    assertThat(args.get("r___")).isEqualTo(new ListExpr(List.of(B, C)));
  }

  @Test
  void substitutionErrors() {
    Bindings b = match(v("x_"), Sum.of(A, B));
    assertThatThrownBy(() -> b.substituteInto(v("y_")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> b.substituteInto(Apply.of(v("x_"), A)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> b.substituteInto(Derivative.of(A, v("x_"), 1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> b.sequence("x_")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void equalityDoesNotDependOnTheOrderOfBinding() {
    Bindings one =
        Bindings.EMPTY
            .with(new Bindings.Binding("y_", B, null, null))
            .with(new Bindings.Binding("x_", A, null, null));
    Bindings two =
        Bindings.EMPTY
            .with(new Bindings.Binding("x_", A, null, null))
            .with(new Bindings.Binding("y_", B, null, null));
    assertThat(one).isEqualTo(two);
    assertThat(one.names()).containsExactly("x_", "y_");
    assertThatThrownBy(() -> one.with(new Bindings.Binding("x_", B, null, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
