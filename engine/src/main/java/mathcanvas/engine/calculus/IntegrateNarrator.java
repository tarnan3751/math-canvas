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
 * The narrator of integration ({@link #RULE_SETS}): a {@code sum-rule} step and the linearity steps
 * right after it ({@code constant-multiple}, {@code constant}) are one step, {@code
 * integrate.step.term-by-term}; every other step, each a technique (a table entry, an identity,
 * completing the square, a substitution, by parts, the bounds) or the terminal {@code none}, is a
 * step with its rule's text, which names the integral it rewrote and what replaced it.
 *
 * @param texts the texts
 */
public record IntegrateNarrator(StepText texts) implements Narrator {

  /** The rule sets this narrator owns. */
  public static final Set<String> RULE_SETS = Set.of("integrate");

  /** The bundle keys this narrator adds to the rules' own. */
  public static final Set<String> KEYS = Set.of("integrate.step.term-by-term");

  private static final Set<String> LINEARITY = Set.of("constant-multiple", "constant");

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
      String text;
      if (r.rule().equals("sum-rule")) {
        while (j < rs.size()
            && (LINEARITY.contains(rs.get(j).rule()) || rs.get(j).rule().equals("sum-rule"))) {
          j++;
        }
        text = texts.text("integrate.step.term-by-term");
      } else {
        text = texts.of(r);
      }
      Expr before = Canonical.of(replay.before(i), ctx.precision());
      Expr after = Canonical.of(replay.boundary(j), ctx.precision());
      steps.add(new Step(text, before, after, rs.subList(i, j)));
      i = j;
    }
    return List.copyOf(steps);
  }
}
