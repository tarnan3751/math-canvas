package mathcanvas.engine.calculus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Narrator;
import mathcanvas.engine.rule.Replay;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Step;
import mathcanvas.engine.rule.StepText;
import mathcanvas.engine.rule.Trace;

/**
 * The narrator of differentiation ({@link #RULE_SETS}):
 *
 * <ul>
 *   <li>a {@code sum-rule} rewrite and the linearity rewrites right after it ({@code sum-rule},
 *       {@code constant-multiple}, {@code constant}, with the {@code variable} rewrites among them)
 *       are one step, {@code diff.step.linearity} when a {@code constant} rewrite dropped a
 *       constant term among them, {@code diff.step.term-by-term} otherwise;
 *   <li>a {@code chain-rule} rewrite takes every rewrite after it below its place, the inner
 *       derivative's, as one step, {@code diff.step.chain}, naming the outer and the inner
 *       derivative, then listing the inner rewrites' own steps on the same line; when the inner
 *       derivative needs a chain rule of its own, the outer one is a step of its own with its
 *       rule's text, so the inner chain rule is a step and no line nests without bound;
 *   <li>any other rewrite (the power, product and quotient rules, a function's derivative) is a
 *       step with its rule's text, the {@code variable} rewrites right after it and below its place
 *       folded in, its {@code {after}} showing where they led.
 * </ul>
 *
 * @param texts the texts
 */
public record DiffNarrator(StepText texts) implements Narrator {

  /** The rule sets this narrator owns. */
  public static final Set<String> RULE_SETS = Set.of("diff");

  /** The bundle keys this narrator adds to the rules' own. */
  public static final Set<String> KEYS =
      Set.of("diff.step.linearity", "diff.step.term-by-term", "diff.step.chain");

  private static final Set<String> LINEARITY = Set.of("sum-rule", "constant-multiple", "constant");

  private static final String VARIABLE = "variable";

  private static final String CHAIN = "chain-rule";

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
      Rewrite first = rs.get(i);
      int j = i + 1;
      String text;
      if (first.rule().equals("sum-rule")) {
        while (j < rs.size()
            && (LINEARITY.contains(rs.get(j).rule()) || rs.get(j).rule().equals(VARIABLE))) {
          j++;
        }
        boolean dropped = rs.subList(i + 1, j).stream().anyMatch(r -> r.rule().equals("constant"));
        text =
            texts.format(
                dropped ? "diff.step.linearity" : "diff.step.term-by-term",
                List.of(),
                first.before(),
                at(replay, j - 1, first, ctx),
                "");
      } else if (first.rule().equals(CHAIN)) {
        while (j < rs.size() && below(first, rs.get(j))) {
          j++;
        }
        Expr result = at(replay, j - 1, first, ctx);
        if (rs.subList(i + 1, j).stream().anyMatch(r -> r.rule().equals(CHAIN))) {
          j = i + 1;
          text = texts.of(first);
        } else {
          text = chain(first, result, ctx).orElseGet(() -> texts.of(first, first.before(), result));
        }
        if (j > i + 1) {
          List<String> inner = new ArrayList<>();
          for (Step s :
              narrate(
                  new Trace(
                      replay.before(i + 1),
                      replay.after(j - 1),
                      rs.subList(i + 1, j),
                      normalisations(trace, i + 1, j)),
                  ctx)) {
            inner.add(s.text());
          }
          inner.addFirst(text);
          text = texts.join("step.sentences", inner);
        }
      } else {
        while (j < rs.size() && rs.get(j).rule().equals(VARIABLE) && below(first, rs.get(j))) {
          j++;
        }
        text =
            texts.of(
                first, first.before(), j > i + 1 ? at(replay, j - 1, first, ctx) : first.after());
      }
      steps.add(
          new Step(
              text,
              Canonical.of(replay.before(i), ctx.precision()),
              Canonical.of(replay.boundary(j), ctx.precision()),
              rs.subList(i, j)));
      i = j;
    }
    return List.copyOf(steps);
  }

  /** The trace's normalisations between rewrites {@code from} and {@code to}, renumbered. */
  private static List<Trace.Normalisation> normalisations(Trace trace, int from, int to) {
    List<Trace.Normalisation> ns = new ArrayList<>();
    for (Trace.Normalisation n : trace.normalisations()) {
      if (n.afterRewrites() > from && n.afterRewrites() < to) {
        ns.add(new Trace.Normalisation(n.afterRewrites() - from, n.path(), n.node()));
      }
    }
    return ns;
  }

  /** Whether {@code r} rewrote at or below the place of {@code chain}. */
  private static boolean below(Rewrite chain, Rewrite r) {
    List<Integer> p = chain.path().indices();
    List<Integer> q = r.path().indices();
    return q.size() >= p.size() && q.subList(0, p.size()).equals(p);
  }

  /** The subexpression at the place of {@code r} after rewrite {@code k}, canonical. */
  private static Expr at(Replay replay, int k, Rewrite r, Context ctx) {
    return Replay.at(replay.after(k), r.path())
        .map(e -> Canonical.of(e, ctx.precision()))
        .orElse(r.after());
  }

  /**
   * The chain rule's text: its rewrite gave the outer derivative times the inner derivative still
   * to take, and {@code result} is where the inner rewrites led.
   */
  private Optional<String> chain(Rewrite r, Expr result, Context ctx) {
    if (!(r.after() instanceof Product p)) {
      return Optional.empty();
    }
    List<Expr> outer = new ArrayList<>();
    int derivatives = 0;
    for (Expr f : p.factors()) {
      if (f instanceof Derivative) {
        derivatives++;
      } else {
        outer.add(f);
      }
    }
    if (derivatives != 1 || outer.isEmpty()) {
      return Optional.empty();
    }
    Expr outerDerivative =
        Canonical.of(outer.size() == 1 ? outer.getFirst() : new Product(outer), ctx.precision());
    Expr inner =
        Canonical.of(Product.of(result, Power.of(outerDerivative, Num.MINUS_ONE)), ctx.precision());
    return Optional.of(
        texts.format(
            "diff.step.chain",
            List.of(r.bindings().get("h_"), r.bindings().get("u_"), outerDerivative, inner),
            r.before(),
            result,
            ""));
  }
}
