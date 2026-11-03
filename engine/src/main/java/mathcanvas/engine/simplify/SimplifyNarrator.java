package mathcanvas.engine.simplify;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Narrator;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Replay;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Step;
import mathcanvas.engine.rule.StepText;
import mathcanvas.engine.rule.Trace;

/**
 * The narrator of the simplifier's rule sets ({@link #RULE_SETS}). Its steps, in order:
 *
 * <ul>
 *   <li>a rewrite whose rule opens an <b>idea</b> ({@link #IDEAS}) takes the rewrites after it that
 *       continue the idea at the same place or below it, as one step: the first rule's text,
 *       showing where the idea led ({@code √8} through {@code 2^(3/2)} to {@code 2√2});
 *   <li>consecutive rewrites of one rule on subexpressions apart from each other are one step: the
 *       first one's text, then {@code simplify.step.many} listing the others;
 *   <li>any other rewrite is a step of its own, with its rule's text.
 * </ul>
 *
 * <p>A content step of {@code rational} names the numeric factor {@code p/q} it records as {@code
 * gcd}: {@code p} is the integer factor cancelled and {@code q} the multiplier that clears the
 * fractions. With {@code q = 1} it keeps its rule's text; otherwise it says that the numerator and
 * the denominator are multiplied by {@code q} ({@code rational.step.clear-fractions}), and then,
 * when {@code p > 1}, that {@code p} cancels ({@code rational.step.clear-fractions-and-cancel}).
 *
 * <p>A normalisation is no rewrite: it is part of the step before it, whose after shows it.
 *
 * @param texts the texts
 */
public record SimplifyNarrator(StepText texts) implements Narrator {

  /** The rule sets this narrator owns. */
  public static final Set<String> RULE_SETS =
      Set.of("basic", "rational", "exp-log", "radical", "trig", "logic");

  /** The rules that open an idea, each with the rules that continue it. */
  public static final Map<String, Set<String>> IDEAS =
      Map.of(
          "exp-log.log-of-product",
          Set.of("exp-log.log-of-power", "exp-log.log-of-even-power", "exp-log.log-of-reciprocal"),
          "exp-log.log10-to-base",
          Set.of("exp-log.log-base-exact"),
          "exp-log.log2-to-base",
          Set.of("exp-log.log-base-exact"),
          "radical.reduce-radicand-power",
          Set.of("radical.split-integer-exponent", "radical.extract-perfect-power"));

  /** The bundle keys this narrator adds to the rules' own. */
  public static final Set<String> KEYS =
      Set.of(
          "simplify.step.many",
          "simplify.step.equation",
          "rational.step.clear-fractions",
          "rational.step.clear-fractions-and-cancel");

  /** The rational rules whose value {@code gcd} is a numeric content {@code p/q}. */
  private static final Set<String> CONTENT_RULES =
      Set.of("rational.cancel-common-content", "rational.cancel-denominator-content");

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
      Set<String> continues = IDEAS.get(key(first));
      while (continues != null
          && j < rs.size()
          && continues.contains(key(rs.get(j)))
          && Replay.related(first.path(), rs.get(j).path())) {
        j++;
      }
      String text;
      if (j > i + 1) {
        Expr to =
            Replay.at(replay.after(j - 1), first.path())
                .map(e -> Canonical.of(e, ctx.precision()))
                .orElse(rs.get(j - 1).after());
        text = texts.of(first, first.before(), to);
      } else {
        List<Path> places = new ArrayList<>(List.of(first.path()));
        while (j < rs.size() && key(rs.get(j)).equals(key(first)) && apart(places, rs.get(j))) {
          places.add(rs.get(j).path());
          j++;
        }
        text = text(first);
        if (j > i + 1) {
          List<String> others = new ArrayList<>();
          for (Rewrite r : rs.subList(i + 1, j)) {
            others.add(
                texts.text(
                    "simplify.step.equation",
                    texts.printer().apply(r.before()),
                    texts.printer().apply(r.after())));
          }
          text = texts.text("simplify.step.many", text, texts.join("step.list", others));
        }
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

  /** The text of one rewrite: its rule's, or for a content step, the case of its content. */
  private String text(Rewrite r) {
    if (CONTENT_RULES.contains(key(r))
        && r.values().get("gcd") instanceof Num n
        && n.value() instanceof Rational q) {
      Num clear = Num.of(Int.of(q.den()));
      return q.num().equals(BigInteger.ONE)
          ? texts.format("rational.step.clear-fractions", List.of(clear), r.before(), r.after(), "")
          : texts.format(
              "rational.step.clear-fractions-and-cancel",
              List.of(clear, Num.of(Int.of(q.num()))),
              r.before(),
              r.after(),
              "");
    }
    return texts.of(r);
  }

  private static String key(Rewrite r) {
    return r.ruleSet() + "." + r.rule();
  }

  private static boolean apart(List<Path> places, Rewrite r) {
    for (Path p : places) {
      if (Replay.related(p, r.path())) {
        return false;
      }
    }
    return true;
  }
}
