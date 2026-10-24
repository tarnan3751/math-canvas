package mathcanvas.engine.simplify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;
import mathcanvas.engine.rule.Trace;

/**
 * The standard simplifier: the list of rule sets every value is simplified with, applied as one
 * combined rule set through the {@link Rewriter}, so the rules of all the sets share one fixpoint
 * and one rewrite cap.
 *
 * <p>The combined set tries the rules in list order, each set's rules in its own order, so a rule
 * name must be unique across the list. Each rewrite in the trace names the set its rule came from,
 * as the narration bundle's keys need. The standard list is {@link RuleSets#BASIC}, {@link
 * RuleSets#EXPLOG}, {@link RuleSets#RADICAL}, {@link RuleSets#TRIG}.
 */
public final class Simplifier {

  /** The combined rule set's name, which a {@code RewriteLimitException} reports. */
  public static final String COMBINED = "standard";

  private static final List<RuleSet> STANDARD =
      List.of(RuleSets.BASIC, RuleSets.EXPLOG, RuleSets.RADICAL, RuleSets.TRIG);

  private Simplifier() {}

  /**
   * The standard list, in priority order.
   *
   * @return the rule sets
   */
  public static List<RuleSet> standard() {
    return STANDARD;
  }

  /**
   * Simplifies {@code e} with the standard list.
   *
   * @param e the expression
   * @param context the assumptions, precision, trace switch and cap
   * @return the canonical result and its trace
   * @throws mathcanvas.engine.rule.RewriteLimitException if more rewrites than the cap are needed
   */
  public static Rewriter.Result simplify(Expr e, Context context) {
    return simplify(e, STANDARD, context);
  }

  /**
   * Simplifies {@code e} with a list of rule sets combined into one.
   *
   * @param e the expression
   * @param ruleSets the rule sets, in priority order
   * @param context the assumptions, precision, trace switch and cap
   * @return the canonical result and its trace
   * @throws IllegalArgumentException if two rules of the list share a name
   * @throws mathcanvas.engine.rule.RewriteLimitException if more rewrites than the cap are needed
   */
  public static Rewriter.Result simplify(Expr e, List<RuleSet> ruleSets, Context context) {
    List<Rule> rules = new ArrayList<>();
    Map<String, String> setOf = new TreeMap<>();
    for (RuleSet set : ruleSets) {
      for (Rule rule : set.rules()) {
        rules.add(rule);
        setOf.put(rule.name(), set.name());
      }
    }
    Rewriter.Result result = Rewriter.rewrite(e, new RuleSet(COMBINED, rules), context);
    Trace t = result.trace();
    if (t.rewrites().isEmpty()) {
      return result;
    }
    List<Rewrite> named = new ArrayList<>(t.rewrites().size());
    for (Rewrite r : t.rewrites()) {
      named.add(
          new Rewrite(
              r.before(), r.after(), setOf.get(r.rule()), r.rule(), r.path(), r.bindings()));
    }
    return new Rewriter.Result(
        result.expr(), new Trace(t.initial(), t.result(), named, t.normalisations()));
  }
}
