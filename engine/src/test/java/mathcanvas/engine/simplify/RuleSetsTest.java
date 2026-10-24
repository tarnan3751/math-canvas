package mathcanvas.engine.simplify;

import static mathcanvas.engine.simplify.RuleCases.parse;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.DefaultNarrator;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;
import mathcanvas.engine.rule.Step;
import org.junit.jupiter.api.Test;

/** The standard list: its order, its names and the narration of every rule. */
class RuleSetsTest {

  private static final ResourceBundle BUNDLE =
      ResourceBundle.getBundle(DefaultNarrator.BUNDLE, Locale.ROOT);

  @Test
  void theStandardListInOrder() {
    assertThat(Simplifier.standard()).containsExactly(RuleSets.BASIC, RuleSets.EXPLOG);
    assertThat(Simplifier.standard()).extracting(RuleSet::name).containsExactly("basic", "exp-log");
  }

  @Test
  void ruleNamesAreUniqueAcrossTheList() {
    Set<String> names = new HashSet<>();
    for (RuleSet set : Simplifier.standard()) {
      for (Rule r : set.rules()) {
        assertThat(names.add(r.name())).as(r.name()).isTrue();
      }
    }
  }

  @Test
  void everyRuleIsNarratedWithItsOwnVariables() {
    Pattern argument = Pattern.compile("\\{(\\d+)}");
    for (RuleSet set : Simplifier.standard()) {
      for (Rule r : set.rules()) {
        String key = set.name() + "." + r.name();
        assertThat(BUNDLE.containsKey(key)).as(key).isTrue();
        Matcher m = argument.matcher(BUNDLE.getString(key));
        while (m.find()) {
          assertThat(Integer.parseInt(m.group(1)))
              .as(key)
              .isLessThan(r.pattern().variables().size());
        }
      }
    }
  }

  @Test
  void aTraceIsNarratedFromTheBundle() {
    List<Step> steps =
        new DefaultNarrator()
            .narrate(
                Simplifier.simplify(parse("abs(-3y)"), Context.DEFAULT.withTrace(true)).trace(),
                Context.DEFAULT);
    assertThat(steps)
        .extracting(Step::text)
        .containsExactly(
            "The absolute value of a product is the product of the absolute values: |-3y| ="
                + " |-3| |y|",
            "-3 ≤ 0, so |-3| = 3");
  }
}
