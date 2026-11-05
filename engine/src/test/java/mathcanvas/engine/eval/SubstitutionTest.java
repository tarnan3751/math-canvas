package mathcanvas.engine.eval;

import static mathcanvas.engine.testing.Inputs.parse;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.TreeMap;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import org.junit.jupiter.api.Test;

class SubstitutionTest {

  static Expr sub(String text, String name, Expr value) {
    TreeMap<String, Expr> map = new TreeMap<>();
    map.put(name, value);
    return Substitution.apply(parse(text), map);
  }

  @Test
  void freeOccurrencesAreReplaced() {
    assertThat(sub("x^2 + x y", "x", Num.of(2))).isEqualTo(parse("2^2 + 2 y"));
  }

  @Test
  void aBinderKeepsItsVariable() {
    assertThat(sub("sum(x k, k, 1, k)", "k", Num.of(5))).isEqualTo(parse("sum(x k, k, 1, 5)"));
    assertThat(sub("x -> x + y", "x", Num.of(5))).isEqualTo(parse("x -> x + y"));
    assertThat(sub("d/dx x y", "x", Num.of(5))).isEqualTo(parse("d/dx x y"));
    assertThat(sub("solve(x^2 = a, x)", "x", Num.of(5))).isEqualTo(parse("solve(x^2 = a, x)"));
  }

  @Test
  void aCapturingBinderIsRenamed() {
    assertThat(sub("sum(x k, k, 1, 3)", "x", Symbol.of("k")))
        .isEqualTo(parse("sum(k k_1, k_1, 1, 3)"));
    assertThat(sub("t -> t + x", "x", Symbol.of("t"))).isEqualTo(parse("t_1 -> t_1 + t"));
    assertThat(sub("sum(x k_1 k, k, 1, 3)", "x", Symbol.of("k")))
        .isEqualTo(parse("sum(k k_1 k_2, k_2, 1, 3)"));
    assertThat(sub("solve(x + y = a, [x, y])", "a", parse("x y")))
        .isEqualTo(parse("solve(x_1 + y_1 = x y, [x_1, y_1])"));
  }

  @Test
  void anUnchangedSubtreeKeepsItsIdentity() {
    Expr e = parse("sin(y) + x");
    TreeMap<String, Expr> map = new TreeMap<>();
    map.put("x", Num.of(1));
    Expr out = Substitution.apply(e, map);
    assertThat(mathcanvas.engine.match.Operands.of(out).getFirst())
        .isSameAs(mathcanvas.engine.match.Operands.of(e).getFirst());
    assertThat(Substitution.apply(e, new TreeMap<>())).isSameAs(e);
  }
}
