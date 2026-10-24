package mathcanvas.engine.rule;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A named, ordered set of rules; the order is the priority order.
 *
 * @param name the name, in kebab-case; the trace and the narration bundle use it
 * @param rules the rules, with unique names
 */
public record RuleSet(String name, List<Rule> rules) {

  /**
   * Checks the names and copies the rules.
   *
   * @throws NullPointerException if the name or a rule is null
   * @throws IllegalArgumentException if the name is not kebab-case or two rules share a name
   */
  public RuleSet {
    Rule.checkName(name);
    rules = List.copyOf(rules);
    Set<String> names = new HashSet<>();
    for (Rule r : rules) {
      if (!names.add(r.name())) {
        throw new IllegalArgumentException("rule set " + name + " has two rules " + r.name());
      }
    }
  }
}
