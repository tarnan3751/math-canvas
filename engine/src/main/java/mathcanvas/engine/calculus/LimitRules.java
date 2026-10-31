package mathcanvas.engine.calculus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import mathcanvas.engine.algebra.AlgebraLimitException;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Limit;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.rule.Replacement;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;

/**
 * The rule set {@code limit}: every rule rewrites a {@code Limit} node to its value, by one method
 * of {@link LimitMethods}, or does not apply. The rules are in the order the methods are tried:
 * substitution where the expression is continuous, a pole of a rational function, the limit laws,
 * the standard limits, the exponential form of a power, one side of a jump, cancelling or combining
 * fractions, the conjugate of a square-root difference, the leading term at infinity, a bounded
 * factor times one that tends to 0, an oscillation, the point at infinity brought to {@code 0⁺},
 * L'Hôpital's rule, the series expansion, and the two sides of a two-sided limit compared. Each
 * rule's value {@code derivation} is the list of lines the narration shows.
 *
 * <p>The set exists at every level of {@link LimitMethods} ({@link #at}): the methods that decide
 * by the limit of another expression are absent at the last level, and L'Hôpital's rule beyond its
 * third application, which bounds every chain. {@link RuleSets#LIMIT} is level 0.
 */
final class LimitRules {

  private LimitRules() {}

  /** A method with its rule's name and whether it decides by another limit at the next level. */
  record Entry(String name, LimitMethods.Method method, boolean transforms) {}

  private static final List<Entry> ENTRIES =
      List.of(
          new Entry("substitution", LimitMethods::substitution, false),
          new Entry("pole", LimitMethods::pole, false),
          new Entry("limit-laws", LimitMethods::laws, false),
          new Entry(
              "sine-over-argument",
              LimitMethods.table("sine-over-argument", "sin(u_)/u_", Num.ZERO, Num.ONE),
              false),
          new Entry(
              "one-minus-cosine",
              LimitMethods.table("one-minus-cosine", "(1 - cos(u_))/u_", Num.ZERO, Num.ZERO),
              false),
          new Entry(
              "exponential-minus-one",
              LimitMethods.table("exponential-minus-one", "(e^u_ - 1)/u_", Num.ZERO, Num.ONE),
              false),
          new Entry(
              "compound-interest",
              LimitMethods.table(
                  "compound-interest", "(1 + 1/u_)^u_", Constant.INFINITY, Constant.E),
              false),
          new Entry(
              "root-of-one-plus",
              LimitMethods.table("root-of-one-plus", "(1 + u_)^(1/u_)", Num.ZERO, Constant.E),
              false),
          new Entry("exponential-form", LimitMethods::exponentialForm, true),
          new Entry("one-sided", LimitMethods::oneSided, true),
          new Entry("cancel", LimitMethods::cancel, true),
          new Entry("conjugate", LimitMethods::conjugate, true),
          new Entry("leading-term", LimitMethods::leadingTerm, false),
          new Entry("squeeze", LimitMethods::squeeze, false),
          new Entry("oscillation", LimitMethods::oscillation, false),
          new Entry("reciprocal-point", LimitMethods::reciprocalPoint, true),
          new Entry("lhopital", LimitMethods::lhopital, true),
          new Entry("series", LimitMethods::series, false),
          new Entry("two-sided", LimitMethods::twoSided, true));

  private static final List<List<Entry>> BY_LEVEL = byLevel();

  private static final List<RuleSet> SETS = sets();

  private static List<List<Entry>> byLevel() {
    List<List<Entry>> out = new ArrayList<>();
    for (int level = 0; level <= LimitMethods.MAX_LEVEL; level++) {
      List<Entry> entries = new ArrayList<>();
      for (Entry e : ENTRIES) {
        if (e.transforms() && level == LimitMethods.MAX_LEVEL) {
          continue;
        }
        if (e.name().equals("lhopital") && level >= LimitMethods.MAX_LHOPITAL) {
          continue;
        }
        entries.add(e);
      }
      out.add(List.copyOf(entries));
    }
    return List.copyOf(out);
  }

  private static List<RuleSet> sets() {
    List<RuleSet> out = new ArrayList<>();
    for (int level = 0; level <= LimitMethods.MAX_LEVEL; level++) {
      List<Rule> rules = new ArrayList<>();
      for (Entry e : BY_LEVEL.get(level)) {
        rules.add(rule(e, level));
      }
      out.add(new RuleSet("limit", rules));
    }
    return List.copyOf(out);
  }

  /** The methods of a level, in order. */
  static List<Entry> methods(int level) {
    return BY_LEVEL.get(level);
  }

  /**
   * The rule set at a level.
   *
   * @param level from 0 to {@link LimitMethods#MAX_LEVEL}
   * @return its rules
   */
  static RuleSet at(int level) {
    return SETS.get(level);
  }

  private static Rule rule(Entry e, int level) {
    return Rule.valued(
        e.name(),
        Pattern.parse("l_").where("l_", x -> x instanceof Limit),
        Set.of("derivation"),
        m -> {
          Limit l = (Limit) m.bindings().get("l_");
          Optional<LimitMethods.Found> found;
          try {
            found = e.method().apply(new LimitMethods(m.context()), l, level);
          } catch (AlgebraLimitException | ArithmeticException | IllegalArgumentException refused) {
            return Optional.empty();
          }
          if (found.isEmpty()) {
            return Optional.empty();
          }
          TreeMap<String, Expr> values = new TreeMap<>();
          values.put("derivation", found.get().derivation());
          return Optional.of(new Replacement.Valued.Result(found.get().value(), values));
        });
  }
}
