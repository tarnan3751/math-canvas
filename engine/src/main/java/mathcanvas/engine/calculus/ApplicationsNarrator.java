package mathcanvas.engine.calculus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Narrator;
import mathcanvas.engine.rule.Replay;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Step;
import mathcanvas.engine.rule.StepText;
import mathcanvas.engine.rule.Trace;

/**
 * The narrator of the applications of integration and the optimisation helpers ({@link
 * #RULE_SETS}): one step per step of the command, each with its own text {@code
 * applications.<command>.<step>}, which names the formula, the integrand, the roots and pieces of a
 * split, the critical points and their kinds, or the candidates. The classification of the critical
 * points takes the text of the kinds it found ({@link #KEYS}), so an empty set of minima, maxima or
 * points that are neither is said in words, never printed.
 *
 * @param texts the texts
 */
public record ApplicationsNarrator(StepText texts) implements Narrator {

  /** The rule sets this narrator owns. */
  public static final Set<String> RULE_SETS = Set.of(Applications.RULE_SET);

  /** The step that classifies the critical points. */
  private static final String CLASSIFY = "extrema.classify";

  /**
   * The texts of a classification with some kinds empty: {@code applications.extrema.classify.} and
   * the kinds found, joined by {@code -}; with all three found, the step's own key.
   */
  public static final Set<String> KEYS =
      Set.of(
          classify("minima-maxima"),
          classify("minima-neither"),
          classify("maxima-neither"),
          classify("minima"),
          classify("maxima"),
          classify("neither"));

  private static String classify(String kinds) {
    return Applications.RULE_SET + "." + CLASSIFY + "." + kinds;
  }

  @Override
  public List<Step> narrate(Trace trace, Context ctx) {
    List<Rewrite> rs = trace.rewrites();
    if (rs.isEmpty()) {
      return List.of();
    }
    Replay replay = new Replay(trace);
    List<Step> steps = new ArrayList<>();
    for (int i = 0; i < rs.size(); i++) {
      Expr before = Canonical.of(replay.before(i), ctx.precision());
      Expr after = Canonical.of(replay.boundary(i + 1), ctx.precision());
      steps.add(new Step(text(rs.get(i), before, after), before, after, rs.subList(i, i + 1)));
    }
    return List.copyOf(steps);
  }

  private String text(Rewrite r, Expr before, Expr after) {
    if (!r.rule().equals(CLASSIFY)) {
      return texts.of(r, before, after);
    }
    List<String> found = new ArrayList<>();
    for (String kind : List.of("minima", "maxima", "neither")) {
      if (!((SetExpr) r.values().get(kind)).items().isEmpty()) {
        found.add(kind);
      }
    }
    String key =
        found.size() == 3
            ? Applications.RULE_SET + "." + CLASSIFY
            : classify(String.join("-", found));
    return texts.format(key, List.of(), before, after, "", r.values());
  }
}
