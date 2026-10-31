package mathcanvas.engine.calculus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Narrator;
import mathcanvas.engine.rule.Replay;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Step;
import mathcanvas.engine.rule.StepText;
import mathcanvas.engine.rule.Trace;

/**
 * The narrator of Taylor polynomials, sums and products ({@link #RULE_SETS}): one step per rewrite.
 * A Taylor polynomial's step lists the derivatives at the point, each with its order ({@code
 * taylor.step.derivative}), then the polynomial; every other rewrite has its rule's text.
 *
 * @param texts the texts
 */
public record SeriesNarrator(StepText texts) implements Narrator {

  /** The rule sets this narrator owns. */
  public static final Set<String> RULE_SETS = Set.of("taylor");

  /** The bundle keys this narrator adds to the rules' own. */
  public static final Set<String> KEYS = Set.of("taylor.step.derivative");

  @Override
  public List<Step> narrate(Trace trace, Context ctx) {
    List<Rewrite> rs = trace.rewrites();
    Replay replay = new Replay(trace);
    List<Step> steps = new ArrayList<>();
    for (int i = 0; i < rs.size(); i++) {
      Rewrite r = rs.get(i);
      Expr before = Canonical.of(replay.before(i), ctx.precision());
      Expr after = Canonical.of(replay.boundary(i + 1), ctx.precision());
      String text =
          r.ruleSet().equals("taylor") && r.values().get("derivatives") instanceof ListExpr ds
              ? taylor(r, ds)
              : texts.of(r);
      steps.add(new Step(text, before, after, List.of(r)));
    }
    return List.copyOf(steps);
  }

  private String taylor(Rewrite r, ListExpr ds) {
    List<String> items = new ArrayList<>();
    for (int k = 0; k < ds.items().size(); k++) {
      items.add(
          texts.format(
              "taylor.step.derivative",
              List.of(Num.of(k), ds.items().get(k)),
              r.before(),
              r.after(),
              ""));
    }
    return texts.format(
        "taylor.polynomial",
        List.of(),
        r.before(),
        r.after(),
        texts.join("step.list", items),
        r.values());
  }
}
