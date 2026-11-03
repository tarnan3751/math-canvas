package mathcanvas.engine.stat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
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
 * The narrator of the statistics' steps ({@link #RULE_SETS}): one step per step of the regression
 * or the test, each with its own text {@code <rule set>.<rule>}, filled with the values the step
 * recorded. The linear algebra's steps a regression splices in are narrated by their own narrator.
 *
 * @param texts the texts
 */
public record StatisticsNarrator(StepText texts) implements Narrator {

  /** The rule sets this narrator owns. */
  public static final Set<String> RULE_SETS = Statistics.RULE_SETS;

  @Override
  public List<Step> narrate(Trace trace, Context ctx) {
    List<Rewrite> rs = trace.rewrites();
    if (rs.isEmpty()) {
      return List.of();
    }
    Replay replay = new Replay(trace);
    List<Step> steps = new ArrayList<>();
    for (int i = 0; i < rs.size(); i++) {
      Rewrite r = rs.get(i);
      Expr before = Canonical.of(replay.before(i), ctx.precision());
      Expr after = Canonical.of(replay.boundary(i + 1), ctx.precision());
      String key = r.ruleSet() + "." + r.rule();
      String text = texts.format(key, List.of(), before, after, "", new TreeMap<>(r.values()));
      steps.add(new Step(text, before, after, rs.subList(i, i + 1)));
    }
    return List.copyOf(steps);
  }
}
