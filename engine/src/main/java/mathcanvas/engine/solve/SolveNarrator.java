package mathcanvas.engine.solve;

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
 * The narrator of solving ({@link #RULE_SETS}): one step per event of the solver, as it records
 * them, each with its own text: for a linear equation {@code collect}, {@code isolate}, {@code
 * divide}; for a quadratic {@code factor}, or {@code discriminant} and {@code formula} as one step,
 * {@code solve.step.quadratic-formula}, one method with the discriminant in it; and {@code roots},
 * whose after is the solutions already simplified (the solver simplifies them itself, so no
 * simplifier step follows).
 *
 * @param texts the texts
 */
public record SolveNarrator(StepText texts) implements Narrator {

  /** The rule sets this narrator owns. */
  public static final Set<String> RULE_SETS = Set.of("solve");

  /** The bundle keys this narrator adds to the events' own. */
  public static final Set<String> KEYS = Set.of("solve.step.quadratic-formula");

  @Override
  public List<Step> narrate(Trace trace, Context ctx) {
    List<Rewrite> rs = trace.rewrites();
    if (rs.isEmpty()) {
      return List.of();
    }
    Replay replay = new Replay(trace);
    List<Step> steps = new ArrayList<>();
    int i = 0;
    while (i < rs.size()) {
      Rewrite r = rs.get(i);
      int j = i + 1;
      if (r.rule().equals("discriminant") && j < rs.size() && rs.get(j).rule().equals("formula")) {
        j++;
      }
      Expr before = Canonical.of(replay.before(i), ctx.precision());
      Expr after = Canonical.of(replay.boundary(j), ctx.precision());
      String text =
          j > i + 1
              ? texts.format(
                  "solve.step.quadratic-formula",
                  List.of(r.bindings().get("d_")),
                  before,
                  after,
                  "")
              : texts.of(r);
      steps.add(new Step(text, before, after, rs.subList(i, j)));
      i = j;
    }
    return List.copyOf(steps);
  }
}
