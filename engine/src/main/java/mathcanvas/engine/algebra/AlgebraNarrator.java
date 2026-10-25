package mathcanvas.engine.algebra;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Narrator;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Replay;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Step;
import mathcanvas.engine.rule.StepText;
import mathcanvas.engine.rule.Trace;

/**
 * The narrator of {@code expand} and {@code factor} ({@link #RULE_SETS}):
 *
 * <ul>
 *   <li>consecutive {@code expand.distribute} rewrites on one chain of nested products are one
 *       step, {@code expand.step.multiply-out} of the outermost product; any other expand rewrite
 *       is a step with its rule's text;
 *   <li>consecutive {@code factor.rational-root} rewrites are one step, {@code
 *       factor.step.rational-roots} listing the roots, with the root of the linear quotient the
 *       last one left; any other factor event is a step with its own text.
 * </ul>
 *
 * @param texts the texts
 */
public record AlgebraNarrator(StepText texts) implements Narrator {

  /** The rule sets this narrator owns. */
  public static final Set<String> RULE_SETS = Set.of("expand", "factor");

  /** The bundle keys this narrator adds to the rules' and events' own. */
  public static final Set<String> KEYS =
      Set.of("expand.step.multiply-out", "factor.step.rational-roots");

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
      if (is(first, "expand", "distribute")) {
        while (j < rs.size()
            && is(rs.get(j), "expand", "distribute")
            && Replay.related(first.path(), rs.get(j).path())) {
          j++;
        }
        if (j > i + 1) {
          Path outer =
              rs.subList(i, j).stream()
                  .map(Rewrite::path)
                  .min(Comparator.comparingInt(p -> p.indices().size()))
                  .orElseThrow();
          Expr product =
              Replay.at(replay.before(i), outer)
                  .map(e -> Canonical.of(e, ctx.precision()))
                  .orElse(first.before());
          Expr expanded =
              Replay.at(replay.after(j - 1), outer)
                  .map(e -> Canonical.of(e, ctx.precision()))
                  .orElse(rs.get(j - 1).after());
          text = texts.format("expand.step.multiply-out", List.of(), product, expanded, "");
        } else {
          text = texts.of(first);
        }
      } else if (is(first, "factor", "rational-root")) {
        while (j < rs.size() && is(rs.get(j), "factor", "rational-root")) {
          j++;
        }
        List<String> roots = new ArrayList<>();
        for (Rewrite r : rs.subList(i, j)) {
          roots.add(texts.printer().apply(r.bindings().get("r_")));
        }
        linearRoot(rs.get(j - 1)).ifPresent(r -> roots.add(texts.printer().apply(r)));
        if (roots.size() > 1) {
          text =
              texts.format(
                  "factor.step.rational-roots",
                  List.of(),
                  first.before(),
                  rs.get(j - 1).after(),
                  texts.join("step.list", roots));
        } else {
          text = texts.of(first);
        }
      } else {
        text = texts.of(first);
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

  /**
   * The root of the linear quotient the last rational root left, which the search does not record
   * as a root of its own: the one factor new in its after besides the factor it found, when that is
   * linear in one variable.
   */
  private static Optional<Expr> linearRoot(Rewrite last) {
    List<Expr> added = factors(last.after());
    for (Expr f : factors(last.before())) {
      added.remove(f);
    }
    added.remove(last.bindings().get("f_"));
    if (added.size() != 1) {
      return Optional.empty();
    }
    Polynomial p = Polynomial.of(added.getFirst());
    if (p.variables().size() != 1 || p.degree() != 1) {
      return Optional.empty();
    }
    return Optional.of(
        Canonical.of(
            Product.of(
                Num.MINUS_ONE,
                Num.of(p.coefficient(0)),
                Power.of(Num.of(p.coefficient(1)), Num.MINUS_ONE))));
  }

  /** The factors of a product, a power's base for each power; any other expression alone. */
  private static List<Expr> factors(Expr e) {
    List<Expr> out = new ArrayList<>();
    for (Expr f : e instanceof Product p ? p.factors() : List.of(e)) {
      out.add(f instanceof Power w ? w.base() : f);
    }
    return out;
  }

  private static boolean is(Rewrite r, String ruleSet, String rule) {
    return r.ruleSet().equals(ruleSet) && r.rule().equals(rule);
  }
}
