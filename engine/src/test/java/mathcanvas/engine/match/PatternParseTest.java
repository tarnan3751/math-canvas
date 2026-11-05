package mathcanvas.engine.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.ParseException;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

/** Patterns written as text: the matcher's fixed cases, through the parser. */
class PatternParseTest {

  private static Symbol v(String name) {
    return Symbol.of(name);
  }

  private static Map<String, Expr> first(Pattern p, String subject) {
    Expr s = Canonical.of(Parser.parseExpression(subject, Names.NONE));
    return Matcher.match(p, s).map(MatcherTest::m).orElse(null);
  }

  private static Expr e(String source) {
    return Canonical.of(Parser.parseExpression(source, Names.NONE));
  }

  @Test
  void parseCompilesWhatOfCompiles() {
    assertThat(Pattern.parse("a_ * x_^n_"))
        .isEqualTo(Pattern.of(Product.of(v("a_"), Power.of(v("x_"), v("n_")))));
    // sqrt parses to a power of one half.
    assertThat(Pattern.parse("sqrt(x_^2)"))
        .isEqualTo(Pattern.of(Exprs.sqrt(Power.of(v("x_"), Num.of(2)))));
    assertThat(Pattern.parse("x_ - 1")).isEqualTo(Pattern.of(Exprs.sub(v("x_"), Num.ONE)));
    assertThat(Pattern.parse("log(a_ rest__)"))
        .isEqualTo(Pattern.of(Apply.of(Symbol.of("log"), Product.of(v("a_"), v("rest__")))));
    assertThat(Pattern.parse("x_ + rest___").variables()).containsExactly("rest___", "x_");
  }

  @Test
  void fixedCasesThroughTheParser() {
    Pattern xy = Pattern.parse("x_ + y_");
    assertThat(first(xy, "b + a")).isEqualTo(Map.of("x_", e("a"), "y_", e("b")));
    assertThat(first(xy, "a + b + c")).isNull();
    assertThat(first(Pattern.parse("x_ + rest___"), "a"))
        .isEqualTo(Map.of("x_", e("a"), "rest___", e("0")));
    assertThat(first(Pattern.parse("x_ + rest__"), "a + b + c"))
        .isEqualTo(Map.of("x_", e("a"), "rest__", e("b + c")));
    Pattern axn = Pattern.parse("a_ x_^n_").optional("a_", Num.ONE).optional("n_", Num.ONE);
    assertThat(first(axn, "x")).isEqualTo(Map.of("a_", e("1"), "x_", e("x"), "n_", e("1")));
    assertThat(first(axn, "3x^2")).isEqualTo(Map.of("a_", e("3"), "x_", e("x"), "n_", e("2")));
    Pattern linear =
        Pattern.parse("(a_ + b_ x_)^m_")
            .optional("a_", Num.ZERO)
            .optional("b_", Num.ONE)
            .optional("m_", Num.ONE)
            .where("x_", s -> s.equals(Symbol.of("y")));
    assertThat(first(linear, "(1 + 2y)^3"))
        .isEqualTo(Map.of("a_", e("1"), "b_", e("2"), "x_", e("y"), "m_", e("3")));
    assertThat(first(linear, "(1 + y + 2x)^3")).isNull();
    Pattern args = Pattern.parse("max(x_, rest___)");
    assertThat(first(args, "max(a, b, c)").get("x_")).isEqualTo(e("a"));
    assertThat(first(Pattern.parse("sin(x_)^2 + cos(x_)^2"), "sin(t)^2 + cos(t)^2"))
        .isEqualTo(Map.of("x_", e("t")));
    assertThat(first(Pattern.parse("sin(x_)^2 + cos(x_)^2"), "sin(t)^2 + cos(u)^2")).isNull();
  }

  @Test
  void aNameThatIsNotAFunctionMultiplies() {
    // f is not a builtin, so f(x_) is f·x_ (docs/language.md §11.3); a variable head needs
    // Pattern.of.
    assertThat(Pattern.parse("f(x_)")).isEqualTo(Pattern.of(Product.of(v("f"), v("x_"))));
    assertThat(first(Pattern.parse("f(x_)"), "f y")).isEqualTo(Map.of("x_", e("y")));
  }

  @Test
  void errors() {
    assertThatThrownBy(() -> Pattern.parse("x_ +")).isInstanceOf(ParseException.class);
    for (String bad : List.of("x___^2", "a___ + b___", "(r___ = 0)")) {
      assertThatThrownBy(() -> Pattern.parse(bad))
          .as(bad)
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThat(Pattern.parse("2 + 3")).isEqualTo(Pattern.of(Sum.of(Num.of(2), Num.of(3))));
  }
}
