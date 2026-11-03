package mathcanvas.engine.eval;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.algebra.AlgebraNarrator;
import mathcanvas.engine.calculus.ApplicationsNarrator;
import mathcanvas.engine.calculus.DiffNarrator;
import mathcanvas.engine.calculus.IntegrateNarrator;
import mathcanvas.engine.calculus.LimitNarrator;
import mathcanvas.engine.calculus.SeriesNarrator;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.linalg.LinalgNarrator;
import mathcanvas.engine.numbertheory.NumberTheoryNarrator;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.DefaultNarrator;
import mathcanvas.engine.rule.Narrator;
import mathcanvas.engine.rule.Replay;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Step;
import mathcanvas.engine.rule.StepText;
import mathcanvas.engine.rule.Trace;
import mathcanvas.engine.simplify.SimplifyNarrator;
import mathcanvas.engine.solve.SolveNarrator;
import mathcanvas.engine.stat.StatisticsNarrator;
import mathcanvas.engine.units.UnitsNarrator;

/**
 * The steps of a statement: its trace split into maximal runs of rewrites that one narrator owns,
 * by their rule sets ({@link SimplifyNarrator}, {@link AlgebraNarrator}, {@link DiffNarrator},
 * {@link IntegrateNarrator}, {@link ApplicationsNarrator}, {@link SolveNarrator}, {@link
 * NumberTheoryNarrator}; {@link DefaultNarrator} for a run nobody owns), each run narrated by its
 * owner and the steps concatenated.
 *
 * <p>Every step stands for a contiguous run of the trace's rewrites, and together they are all of
 * them, in order. A step's before and after are the whole expression, canonical, at the boundaries
 * around its run ({@link Replay#boundary}): the rewriter's own normalised trees, so each step
 * starts exactly where the one before it ended.
 */
public final class Narration {

  private Narration() {}

  /**
   * The steps of an evaluation's trace, the texts in the engine's bundle printed in Unicode.
   *
   * @param result the evaluation's result
   * @param ctx the context it ran in (its precision canonicalises the steps' expressions)
   * @return the steps; none when the trace is {@link Trace#NONE}
   */
  public static List<Step> steps(Evaluator.Result result, Context ctx) {
    return steps(result.trace(), ctx, StepText.standard());
  }

  /**
   * The steps of a trace.
   *
   * @param trace the trace
   * @param ctx the context it ran in
   * @param texts the texts and their printer
   * @return the steps; none when the trace is {@link Trace#NONE}
   */
  public static List<Step> steps(Trace trace, Context ctx, StepText texts) {
    List<Rewrite> rs = trace.rewrites();
    if (rs.isEmpty()) {
      return List.of();
    }
    Replay replay = new Replay(trace);
    List<Step> out = new ArrayList<>();
    int i = 0;
    while (i < rs.size()) {
      String owner = owner(rs.get(i).ruleSet());
      int j = i + 1;
      while (j < rs.size() && owner(rs.get(j).ruleSet()).equals(owner)) {
        j++;
      }
      for (Step s : narrator(owner, texts).narrate(sub(trace, replay, i, j), ctx)) {
        int first = index(rs, s, i);
        out.add(step(s.text(), s.rewrites(), first, first + s.rewrites().size(), replay, ctx));
      }
      i = j;
    }
    return List.copyOf(out);
  }

  private static int index(List<Rewrite> rs, Step s, int from) {
    for (int k = from; k < rs.size(); k++) {
      if (rs.get(k) == s.rewrites().getFirst()) {
        return k;
      }
    }
    throw new IllegalStateException("a step's rewrite is not in its trace");
  }

  private static Step step(
      String text, List<Rewrite> rewrites, int from, int to, Replay replay, Context ctx) {
    return new Step(
        text,
        Canonical.of(replay.before(from), ctx.precision()),
        Canonical.of(replay.boundary(to), ctx.precision()),
        rewrites);
  }

  /** The trace of rewrites {@code from} to {@code to}, starting from the whole expression then. */
  private static Trace sub(Trace trace, Replay replay, int from, int to) {
    List<Trace.Normalisation> ns = new ArrayList<>();
    for (Trace.Normalisation n : trace.normalisations()) {
      if (n.afterRewrites() > from && n.afterRewrites() < to) {
        ns.add(new Trace.Normalisation(n.afterRewrites() - from, n.path(), n.node()));
      }
    }
    return new Trace(
        replay.before(from), replay.boundary(to), trace.rewrites().subList(from, to), ns);
  }

  private static String owner(String ruleSet) {
    if (SimplifyNarrator.RULE_SETS.contains(ruleSet)) {
      return "simplify";
    }
    if (AlgebraNarrator.RULE_SETS.contains(ruleSet)) {
      return "algebra";
    }
    if (DiffNarrator.RULE_SETS.contains(ruleSet)) {
      return "diff";
    }
    if (IntegrateNarrator.RULE_SETS.contains(ruleSet)) {
      return "integrate";
    }
    if (ApplicationsNarrator.RULE_SETS.contains(ruleSet)) {
      return "applications";
    }
    if (SolveNarrator.RULE_SETS.contains(ruleSet)) {
      return "solve";
    }
    if (SeriesNarrator.RULE_SETS.contains(ruleSet)) {
      return "series";
    }
    if (LinalgNarrator.RULE_SETS.contains(ruleSet)) {
      return "linalg";
    }
    if (LimitNarrator.RULE_SETS.contains(ruleSet)) {
      return "limit";
    }
    if (NumberTheoryNarrator.RULE_SETS.contains(ruleSet)) {
      return "numbertheory";
    }
    if (UnitsNarrator.RULE_SETS.contains(ruleSet)) {
      return "units";
    }
    if (StatisticsNarrator.RULE_SETS.contains(ruleSet)) {
      return "statistics";
    }
    return "default";
  }

  private static Narrator narrator(String owner, StepText texts) {
    return switch (owner) {
      case "simplify" -> new SimplifyNarrator(texts);
      case "algebra" -> new AlgebraNarrator(texts);
      case "diff" -> new DiffNarrator(texts);
      case "integrate" -> new IntegrateNarrator(texts);
      case "applications" -> new ApplicationsNarrator(texts);
      case "solve" -> new SolveNarrator(texts);
      case "series" -> new SeriesNarrator(texts);
      case "limit" -> new LimitNarrator(texts);
      case "linalg" -> new LinalgNarrator(texts);
      case "numbertheory" -> new NumberTheoryNarrator(texts);
      case "units" -> new UnitsNarrator(texts);
      case "statistics" -> new StatisticsNarrator(texts);
      default -> new DefaultNarrator(texts.bundle(), texts.printer());
    };
  }
}
