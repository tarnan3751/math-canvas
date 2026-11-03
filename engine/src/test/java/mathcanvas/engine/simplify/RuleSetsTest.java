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
import mathcanvas.engine.rule.Replacement;
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
    assertThat(Simplifier.standard())
        .containsExactly(
            RuleSets.UNITS,
            RuleSets.BASIC,
            RuleSets.RATIONAL,
            RuleSets.EXPLOG,
            RuleSets.RADICAL,
            RuleSets.TRIG,
            RuleSets.LOGIC,
            RuleSets.SPECIAL);
    assertThat(Simplifier.standard())
        .extracting(RuleSet::name)
        .containsExactly(
            "units", "basic", "rational", "exp-log", "radical", "trig", "logic", "special");
  }

  @Test
  void theListForExpandedFormsKeepsTheStandardOrder() {
    assertThat(Simplifier.standard()).containsSubsequence(Simplifier.forExpandedForms());
    // The rational and units sets would undo an expanded form; the others leave it.
    assertThat(Simplifier.forExpandedForms())
        .containsExactly(
            RuleSets.BASIC,
            RuleSets.EXPLOG,
            RuleSets.RADICAL,
            RuleSets.TRIG,
            RuleSets.LOGIC,
            RuleSets.SPECIAL);
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
  void everyNamedPlaceholderIsAValueTheRuleNames() {
    Pattern named = Pattern.compile("\\{([a-z][a-z0-9]*)}");
    for (RuleSet set : Simplifier.standard()) {
      for (Rule r : set.rules()) {
        String key = set.name() + "." + r.name();
        Set<String> allowed = new HashSet<>(Replacement.Valued.RESERVED);
        if (r.replacement() instanceof Replacement.Valued v) {
          allowed.addAll(v.names());
        }
        Matcher m = named.matcher(BUNDLE.getString(key));
        while (m.find()) {
          assertThat(allowed).as(key).contains(m.group(1));
        }
      }
    }
  }

  @Test
  void aTraceIsNarratedFromTheBundle() {
    List<Step> steps =
        new DefaultNarrator()
            .narrate(
                Simplifier.simplify(parse("sqrt(12) + log(1/2)"), Context.DEFAULT.withTrace(true))
                    .trace(),
                Context.DEFAULT);
    assertThat(steps)
        .extracting(Step::text)
        .containsExactly(
            "Take the perfect power out of the radicand: √12 = 2√3",
            "The logarithm of a reciprocal: log(1/2) = -log(2)");
  }

  @Test
  void aTrigonometricTraceIsNarratedFromTheBundle() {
    List<Step> steps =
        new DefaultNarrator()
            .narrate(
                Simplifier.simplify(
                        parse("sin(pi/6) + sin(x)^2 + cos(x)^2"), Context.DEFAULT.withTrace(true))
                    .trace(),
                Context.DEFAULT);
    assertThat(steps)
        .extracting(Step::text)
        .containsExactly(
            "Reduce the angle and take the exact value: sin(π/6) = 1/2",
            "sin²(x) + cos²(x) = 1: cos(x)² + sin(x)² + 1/2 = 3/2");
  }
}
