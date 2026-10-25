package mathcanvas.engine.rule;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.match.Pattern;

/**
 * A named rule: <i>pattern → replacement, if condition</i>, the conditions being the pattern's.
 *
 * @param name the name, in kebab-case, unique within its rule set; the trace and the narration
 *     bundle use it
 * @param pattern the pattern
 * @param replacement the right-hand side
 */
public record Rule(String name, Pattern pattern, Replacement replacement) {

  /**
   * Checks the rule.
   *
   * @throws NullPointerException if an argument is null
   * @throws IllegalArgumentException if the name is not kebab-case, or a template uses a variable
   *     the pattern does not bind
   */
  public Rule {
    checkName(name);
    Objects.requireNonNull(pattern, "pattern");
    Objects.requireNonNull(replacement, "replacement");
    if (replacement instanceof Replacement.Template t) {
      SortedSet<String> used = new TreeSet<>();
      variables(t.template(), used);
      used.removeAll(pattern.variables());
      if (!used.isEmpty()) {
        throw new IllegalArgumentException(
            "rule " + name + ": the template uses unbound variables " + used);
      }
    }
  }

  /**
   * Returns the rule {@code pattern → template}.
   *
   * @param name the name
   * @param pattern the pattern
   * @param template the template
   * @return the rule
   */
  public static Rule of(String name, Pattern pattern, Expr template) {
    return new Rule(name, pattern, new Replacement.Template(template));
  }

  /**
   * Returns the rule whose right-hand side {@code function} computes.
   *
   * @param name the name
   * @param pattern the pattern
   * @param function the computation; empty when the rule does not apply after all
   * @return the rule
   */
  public static Rule computed(
      String name, Pattern pattern, Function<Match, Optional<Expr>> function) {
    return new Rule(name, pattern, new Replacement.Computed(function));
  }

  /**
   * Returns the rule whose right-hand side {@code function} computes, with values named for its
   * narration ({@link Replacement.Valued}).
   *
   * @param name the name
   * @param pattern the pattern
   * @param values the names of the values the function may return
   * @param function the computation; empty when the rule does not apply after all
   * @return the rule
   * @throws IllegalArgumentException if a value name is not a lowercase word or is reserved
   */
  public static Rule valued(
      String name,
      Pattern pattern,
      Set<String> values,
      Function<Match, Optional<Replacement.Valued.Result>> function) {
    return new Rule(name, pattern, new Replacement.Valued(new TreeSet<>(values), function));
  }

  static void checkName(String name) {
    Objects.requireNonNull(name, "name");
    if (!name.matches("[a-z0-9]+(-[a-z0-9]+)*")) {
      throw new IllegalArgumentException("a name is kebab-case: " + name);
    }
  }

  private static void variables(Expr e, SortedSet<String> out) {
    if (e instanceof Symbol s && Pattern.isVariable(s.name())) {
      out.add(s.name());
    }
    if (e instanceof Apply a && Pattern.isVariable(a.head().name())) {
      out.add(a.head().name());
    }
    for (Expr o : Operands.of(e)) {
      variables(o, out);
    }
  }
}
