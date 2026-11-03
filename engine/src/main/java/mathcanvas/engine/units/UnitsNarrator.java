package mathcanvas.engine.units;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Narrator;
import mathcanvas.engine.rule.Replay;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Step;
import mathcanvas.engine.rule.StepText;
import mathcanvas.engine.rule.Trace;

/**
 * The narrator of {@code convert} ({@link #RULE_SETS}): a conversion is one step, its rewrites'
 * texts joined as sentences: {@code 1 km = 1000 m. 1 h = 3600 s. So 5 km/h = 5000/3600 m/s = 25/18
 * m/s}. A last rewrite that gives the quotient before it is reduced (the values {@code num}, {@code
 * den} and {@code target}) is told by its key with {@code -quotient} appended ({@link #KEYS}).
 *
 * @param texts the texts
 */
public record UnitsNarrator(StepText texts) implements Narrator {

  /** The rule sets this narrator owns. */
  public static final Set<String> RULE_SETS = Set.of(Convert.RULE_SET);

  /** The keys of the texts that show the quotient before it is reduced. */
  public static final Set<String> KEYS =
      Set.of("convert.to-target-quotient", "convert.to-defined-target-quotient");

  @Override
  public List<Step> narrate(Trace trace, Context ctx) {
    List<Rewrite> rs = trace.rewrites();
    if (rs.isEmpty()) {
      return List.of();
    }
    List<String> sentences = new ArrayList<>();
    for (Rewrite r : rs) {
      String quotient = r.ruleSet() + "." + r.rule() + "-quotient";
      sentences.add(
          r.values().containsKey("num") && KEYS.contains(quotient)
              ? texts.format(quotient, List.of(), r.before(), r.after(), "", r.values())
              : texts.of(r));
    }
    Replay replay = new Replay(trace);
    return List.of(
        new Step(
            texts.join("step.sentences", sentences),
            Canonical.of(replay.before(0), ctx.precision()),
            Canonical.of(replay.boundary(rs.size()), ctx.precision()),
            rs));
  }
}
