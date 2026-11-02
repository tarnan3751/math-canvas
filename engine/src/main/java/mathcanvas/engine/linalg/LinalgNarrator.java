package mathcanvas.engine.linalg;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Narrator;
import mathcanvas.engine.rule.Replay;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Step;
import mathcanvas.engine.rule.StepText;
import mathcanvas.engine.rule.Trace;

/**
 * The narrator of the linear-algebra operations ({@link #RULE_SETS}): one step per step of the
 * operation, each with its own text {@code <rule set>.<rule>}. A row operation {@code add} reads
 * {@code R₂ ← R₂ − 3R₁}, or {@code R₂ ← R₂ + 3R₁} ({@code rref.step.plus}) when the multiple's
 * numeric coefficient is negative ({@code R₃ ← R₃ + R₂/x}); a step that assumes a symbolic entry is
 * not zero says so after its text ({@code linalg.step.assuming}), and a generic answer the
 * condition under which it fails ({@code linalg.step.unless}).
 *
 * @param texts the texts
 */
public record LinalgNarrator(StepText texts) implements Narrator {

  /** The rule sets this narrator owns. */
  public static final Set<String> RULE_SETS = Linalg.RULE_SETS;

  /** The keys of the texts this narrator adds to its rules'. */
  public static final Set<String> KEYS =
      Set.of("rref.step.plus", "linalg.step.assuming", "linalg.step.unless");

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
      TreeMap<String, Expr> values = new TreeMap<>(r.values());
      if (key.equals("rref.add")) {
        Expr term = Canonical.of(Product.of(values.get("k"), values.get("j")), ctx.precision());
        if (negative(term)) {
          key = "rref.step.plus";
          term = Canonical.of(Product.of(Num.MINUS_ONE, term), ctx.precision());
        }
        values.put("term", term);
      }
      String text = texts.format(key, List.of(), before, after, "", values);
      text = note(text, "conditions", "linalg.step.assuming", values);
      text = note(text, "unless", "linalg.step.unless", values);
      steps.add(new Step(text, before, after, rs.subList(i, i + 1)));
    }
    return List.copyOf(steps);
  }

  /** Whether a canonical term's numeric coefficient is negative: {@code -3R₁}, {@code -R₂/x}. */
  private static boolean negative(Expr term) {
    Expr coefficient = term instanceof Product p ? p.factors().getFirst() : term;
    return coefficient instanceof Num n && n.value() instanceof RealNumeric q && q.signum() < 0;
  }

  private String note(String text, String name, String key, TreeMap<String, Expr> values) {
    if (!(values.get(name) instanceof SetExpr s) || s.items().isEmpty()) {
      return text;
    }
    // one condition reads without the set's braces
    TreeMap<String, Expr> shown = new TreeMap<>(values);
    shown.put(name, s.items().size() == 1 ? s.items().getFirst() : s);
    String extra = texts.format(key, List.of(), s, s, "", shown);
    return texts.text("step.sentences", text, extra);
  }
}
