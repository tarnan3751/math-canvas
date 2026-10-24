package mathcanvas.engine.algebra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
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
   * Asserts that every rewrite changes its node, that its before is the node at its path in the
   * tree as it stood (the previous root with the normalisations before it applied), that its after
   * sits at its path in the root after it, and that the last root is the result.
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
      assertThat(w.after())
          .as("rewrite %d (%s) changes its node", i, w.rule())
          .isNotEqualTo(w.before());
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
