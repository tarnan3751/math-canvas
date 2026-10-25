package mathcanvas.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.ListResourceBundle;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.print.UnicodePrinter;
import org.junit.jupiter.api.Test;

/**
 * Values a computed replacement names for its narration: recorded on the rewrite apart from the
 * bindings, shown by {@code {name}}, checked when declared and when a text names one.
 */
class ValuedReplacementTest {

  /** {@code g(x_)} → {@code x_}, naming {@code twice} = {@code 2x_}. */
  private static final Rule HALVE =
      Rule.valued(
          "halve",
          Pattern.of(Apply.of(Symbol.of("g"), Symbol.of("x_"))),
          Set.of("twice"),
          m -> {
            Expr x = m.bindings().get("x_");
            return Optional.of(
                new Replacement.Valued.Result(
                    x,
                    new TreeMap<>(
                        Map.of("twice", mathcanvas.engine.expr.Product.of(Num.of(2), x)))));
          });

  private static final RuleSet SET = new RuleSet("demo", List.of(HALVE));

  /** The texts of the demo set. */
  public static final class Texts extends ListResourceBundle {
    @Override
    protected Object[][] getContents() {
      return new Object[][] {
        {"demo.halve", "{0} is half of {twice}: {before} = {after}"},
        {"demo.unknown", "{0} and {nope}"},
      };
    }
  }

  private static Rewrite rewrite(Expr e) {
    return Rewriter.rewrite(e, SET, Context.DEFAULT.withTrace(true)).trace().rewrites().getFirst();
  }

  @Test
  void theValuesAreRecordedApartFromTheBindings() {
    Rewrite r = rewrite(Apply.of(Symbol.of("g"), Symbol.of("y")));
    assertThat(r.bindings().names()).containsExactly("x_");
    assertThat(r.values()).containsOnlyKeys("twice");
    assertThat(r.values().get("twice"))
        .isEqualTo(mathcanvas.engine.expr.Product.of(Num.of(2), Symbol.of("y")));
  }

  @Test
  void aTextShowsAValueByNameAndKeepsTheNumbering() {
    StepText texts = new StepText(new Texts(), UnicodePrinter.INSTANCE::display);
    assertThat(texts.of(rewrite(Apply.of(Symbol.of("g"), Symbol.of("y")))))
        .isEqualTo("y is half of 2y: g(y) = y");
  }

  @Test
  void aTextNamingNoValueFails() {
    StepText texts = new StepText(new Texts(), UnicodePrinter.INSTANCE::display);
    assertThatThrownBy(() -> texts.format("demo.unknown", List.of(Symbol.of("y")), null, null, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("{nope}");
  }

  @Test
  void aValueNameIsAPlainWordThatNoPatternVariableOrPlaceholderTakes() {
    Pattern p = Pattern.parse("x_");
    for (String bad : List.of("x_", "before", "after", "list", "Gcd", "two words", "")) {
      assertThatThrownBy(() -> Rule.valued("r", p, Set.of(bad), m -> Optional.empty()))
          .as(bad)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void aValueTheRuleDoesNotDeclareIsAnError() {
    Rule sloppy =
        Rule.valued(
            "sloppy",
            Pattern.of(Apply.of(Symbol.of("g"), Symbol.of("x_"))),
            Set.of("one"),
            m ->
                Optional.of(
                    new Replacement.Valued.Result(
                        m.bindings().get("x_"), new TreeMap<>(Map.of("two", Num.of(2))))));
    assertThatThrownBy(
            () ->
                Rewriter.rewrite(
                    Apply.of(Symbol.of("g"), Symbol.of("y")),
                    new RuleSet("demo", List.of(sloppy)),
                    Context.DEFAULT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("two");
  }

  @Test
  void aValueIsRecordedCanonical() {
    Rule raw =
        Rule.valued(
            "raw",
            Pattern.of(Apply.of(Symbol.of("g"), Symbol.of("x_"))),
            Set.of("sum"),
            m -> {
              Expr x = m.bindings().get("x_");
              return Optional.of(
                  new Replacement.Valued.Result(
                      x, new TreeMap<>(Map.of("sum", mathcanvas.engine.expr.Sum.of(x, x)))));
            });
    Rewrite r =
        Rewriter.rewrite(
                Apply.of(Symbol.of("g"), Symbol.of("x")),
                new RuleSet("demo", List.of(raw)),
                Context.DEFAULT.withTrace(true))
            .trace()
            .rewrites()
            .getFirst();
    assertThat(r.values().get("sum"))
        .isEqualTo(mathcanvas.engine.expr.Product.of(Num.of(2), Symbol.of("x")));
  }
}
