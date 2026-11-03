package mathcanvas.engine.algebra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;

/** The trace of an algebra operation is a chain of real rewrites that replays to the result. */
public final class TraceChecks {

  private TraceChecks() {}

  /**
   * Asserts that every rewrite changes its node (but a terminal note as the last record, or
   * followed only by the numeric path's records, {@link #terminal}), that its before is the node at
   * its path in the tree as it stood (the previous root with the normalisations before it applied),
   * that its after sits at its path in the root after it, and that the last root is the result.
   *
   * @param r a traced result
   */
  public static void assertChain(Rewriter.Result r) {
    Trace trace = r.trace();
    List<Rewrite> rewrites = trace.rewrites();
    List<Expr> roots = trace.roots();
    Expr current = trace.initial();
    int next = 0;
    for (int i = 0; i < rewrites.size(); i++) {
      while (next < trace.normalisations().size()
          && trace.normalisations().get(next).afterRewrites() == i) {
        Trace.Normalisation n = trace.normalisations().get(next++);
        current = splice(current, n.path().indices(), 0, n.node());
      }
      Rewrite w = rewrites.get(i);
      if (!(terminal(w) && onlyNumericAfter(rewrites, i))) {
        assertThat(w.after())
            .as("rewrite %d (%s) changes its node", i, w.rule())
            .isNotEqualTo(w.before());
      }
      assertThat(at(current, w.path().indices()))
          .as("rewrite %d (%s): its before is the node at its path", i, w.rule())
          .isEqualTo(w.before());
      current = roots.get(i);
      assertThat(at(current, w.path().indices()))
          .as("rewrite %d (%s): its after is at its path", i, w.rule())
          .isEqualTo(w.after());
    }
    Expr last = roots.isEmpty() ? trace.initial() : roots.getLast();
    assertThat(Canonical.of(last)).isEqualTo(r.expr());
  }

  /**
   * Whether a rewrite is a terminal note, which may leave its node as it is: {@code <set>.none}
   * (also {@code applications.<command>.none}), {@code <set>.angle-mode}, {@code
   * integrate.singular}, {@code integrate.improper}, {@code limit.numeric}, {@code numeric.digits},
   * {@code decomposition.symbolic}, or a sum's or product's {@code too-many-terms}, {@code bounds},
   * {@code budget} and {@code not-arithmetic}, or {@code dsolve.unsolved}, {@code
   * dsolve.unverified} and {@code dsolve.unmet}, {@code ifactor.unfinished}, or {@code
   * rsolve.unsolved}, {@code rsolve.unverified} and {@code rsolve.unmet}.
   */
  private static boolean terminal(Rewrite w) {
    return w.rule().equals("none")
        || w.rule().endsWith(".none")
        || w.rule().equals("angle-mode")
        || (w.ruleSet().equals("integrate") && Set.of("singular", "improper").contains(w.rule()))
        || (w.ruleSet().equals("limit") && w.rule().equals("numeric"))
        || (Set.of("sum", "product").contains(w.ruleSet())
            && Set.of("too-many-terms", "bounds", "budget", "not-arithmetic").contains(w.rule()))
        || (w.ruleSet().equals("numeric") && w.rule().equals("digits"))
        || (w.ruleSet().equals("decomposition") && w.rule().equals("symbolic"))
        || (w.ruleSet().equals("dsolve")
            && Set.of("unsolved", "unverified", "unmet").contains(w.rule()))
        || (w.ruleSet().equals("ifactor") && w.rule().equals("unfinished"))
        || (w.ruleSet().equals("rsolve")
            && Set.of("unsolved", "unverified", "unmet").contains(w.rule()));
  }

  /**
   * Whether every record after {@code i} is the numeric path's ({@code numeric.*}), which takes
   * over the integral the exact path left: a terminal note ends its operation's part of the trace.
   */
  private static boolean onlyNumericAfter(List<Rewrite> rewrites, int i) {
    for (Rewrite later : rewrites.subList(i + 1, rewrites.size())) {
      if (!later.ruleSet().equals("numeric")) {
        return false;
      }
    }
    return true;
  }

  private static Expr at(Expr e, List<Integer> path) {
    for (int k : path) {
      e = Operands.of(e).get(k);
    }
    return e;
  }

  private static Expr splice(Expr e, List<Integer> path, int depth, Expr node) {
    if (depth == path.size()) {
      return node;
    }
    List<Expr> ops = new ArrayList<>(Operands.of(e));
    int i = path.get(depth);
    ops.set(i, splice(ops.get(i), path, depth + 1, node));
    return Operands.rebuild(e, ops);
  }
}
