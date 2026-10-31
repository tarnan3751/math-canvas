package mathcanvas.engine.calculus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Narrator;
import mathcanvas.engine.rule.Replay;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Step;
import mathcanvas.engine.rule.StepText;
import mathcanvas.engine.rule.Trace;

/**
 * The narrator of limits ({@link #RULE_SETS}): one step per rewrite. A method's step is its
 * derivation, the lines a textbook shows one after another, each with the text of its kind ({@code
 * limit.<rule>} for a method's conclusion, {@code limit.step.<name>} for the lines that are not a
 * method's), joined by {@code limit.step.then}; an event ({@code none}, {@code numeric}) has its
 * own text.
 *
 * @param texts the texts
 */
public record LimitNarrator(StepText texts) implements Narrator {

  /** The rule sets this narrator owns. */
  public static final Set<String> RULE_SETS = Set.of(Limits.RULE_SET);

  /** The kinds of line that are not a rule's, each with a key {@code limit.step.<kind>}. */
  static final Set<String> LINES =
      Set.of(
          "substitution-where",
          "pole-sides",
          "sides-differ",
          "combine",
          "squeeze-between",
          "exponential-value");

  /** The bundle keys this narrator adds to the rules' own. */
  public static final Set<String> KEYS = keys();

  private static Set<String> keys() {
    Set<String> out = new java.util.TreeSet<>();
    for (String l : LINES) {
      out.add("limit.step." + l);
    }
    out.add("limit.step.then");
    return Set.copyOf(out);
  }

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
          r.values().get("derivation") instanceof ListExpr lines && !lines.items().isEmpty()
              ? derivation(lines, r)
              : texts.of(r);
      steps.add(new Step(text, before, after, List.of(r)));
    }
    return List.copyOf(steps);
  }

  /** A value printed; a set of conditions as its members in a list. */
  private String printed(Expr arg) {
    if (arg instanceof SetExpr set && !set.items().isEmpty()) {
      return texts.join("step.list", set.items().stream().map(texts.printer()).toList());
    }
    return texts.printer().apply(arg);
  }

  private String derivation(ListExpr lines, Rewrite r) {
    List<String> out = new ArrayList<>();
    for (Expr item : lines.items()) {
      Apply line = (Apply) item;
      String kind = line.head().name();
      String key = LINES.contains(kind) ? "limit.step." + kind : "limit." + kind;
      List<String> printed = new ArrayList<>();
      for (Expr arg : line.args()) {
        printed.add(printed(arg));
      }
      out.add(texts.text(key, printed.toArray(String[]::new)));
    }
    String text = texts.join("limit.step.then", out);
    Apply first = (Apply) lines.items().getFirst();
    String kind = first.head().name();
    String pattern =
        texts.bundle().getString(LINES.contains(kind) ? "limit.step." + kind : "limit." + kind);
    // a sentence that opens with the text's own word, not with an expression, takes a capital
    return pattern.startsWith("{")
        ? text
        : Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }
}
