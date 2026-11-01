package mathcanvas.engine.calculus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
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
 * split, the critical points and their kinds, or the candidates.
 *
 * @param texts the texts
 */
public record ApplicationsNarrator(StepText texts) implements Narrator {

  /** The rule sets this narrator owns. */
  public static final Set<String> RULE_SETS = Set.of(Applications.RULE_SET);

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
      steps.add(new Step(texts.of(rs.get(i), before, after), before, after, rs.subList(i, i + 1)));
    }
    return List.copyOf(steps);
  }
}
