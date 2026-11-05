package mathcanvas.engine.match;

import static mathcanvas.engine.testing.Inputs.parse;
import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import org.junit.jupiter.api.Test;

class OccurrenceTest {

  @Test
  void aSymbolOccursBoundOrFree() {
    assertThat(Occurrence.occurs(parse("x^2 + y"), Symbol.of("x"))).isTrue();
    assertThat(Occurrence.occurs(parse("sum(k, k, 1, n)"), Symbol.of("k"))).isTrue();
    assertThat(Occurrence.occurs(parse("x^2 + y"), Symbol.of("z"))).isFalse();
  }

  @Test
  void anApplicationsHeadIsMentionedButDoesNotOccur() {
    Expr e = Sum.of(Apply.of(Symbol.of("y"), Symbol.of("x")), Num.ONE);
    assertThat(Occurrence.occurs(e, Symbol.of("y"))).isFalse();
    assertThat(Occurrence.mentions(e, Symbol.of("y"))).isTrue();
    assertThat(Occurrence.mentions(e, Symbol.of("x"))).isTrue();
    assertThat(Occurrence.names(e)).containsExactly("x", "y");
  }

  @Test
  void freshStartsAtTheBaseAndAvoidsEveryTree() {
    assertThat(Occurrence.fresh("t", parse("x + y"))).isEqualTo(Symbol.of("t"));
    assertThat(Occurrence.fresh("t", parse("t + t_1"), Symbol.of("t_2")))
        .isEqualTo(Symbol.of("t_3"));
    Expr e = Apply.of(Symbol.of("u"), Symbol.of("x"));
    assertThat(Occurrence.fresh("u", e)).isEqualTo(Symbol.of("u"));
    assertThat(Occurrence.fresh("u", s -> Occurrence.mentions(e, s))).isEqualTo(Symbol.of("u_1"));
  }

  @Test
  void subscriptedNeverReturnsTheBase() {
    Expr e = parse("k_1 + k_2");
    assertThat(Occurrence.subscripted("k", s -> Occurrence.occurs(e, s)))
        .isEqualTo(Symbol.of("k_3"));
    assertThat(Occurrence.subscripted("k", s -> false)).isEqualTo(Symbol.of("k_1"));
  }
}
