package mathcanvas.engine.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.match.Operands;

/**
 * A trace replayed once, for narrators: the whole expression just before each rewrite (the
 * normalisations before it applied), just after it, and at each boundary between rewrites (the
 * normalisations after it applied); and the subexpression at a path.
 */
public final class Replay {

  private final List<Expr> before;
  private final List<Expr> after;
  private final Expr end;

  /**
   * Replays a trace.
   *
   * @param trace a trace that is not {@link Trace#NONE}
   */
  public Replay(Trace trace) {
    List<Rewrite> rewrites = trace.rewrites();
    before = new ArrayList<>(rewrites.size());
    after = new ArrayList<>(rewrites.size());
    Expr current = trace.initial();
    int next = 0;
    for (int i = 0; i < rewrites.size(); i++) {
      while (next < trace.normalisations().size()
          && trace.normalisations().get(next).afterRewrites() == i) {
        Trace.Normalisation n = trace.normalisations().get(next++);
        current = splice(current, n.path().indices(), 0, n.node());
      }
      before.add(current);
      Rewrite r = rewrites.get(i);
      current = splice(current, r.path().indices(), 0, r.after());
      after.add(current);
    }
    while (next < trace.normalisations().size()) {
      Trace.Normalisation n = trace.normalisations().get(next++);
      current = splice(current, n.path().indices(), 0, n.node());
    }
    end = current;
  }

  /**
   * The whole expression just before rewrite {@code i}, raw.
   *
   * @param i the rewrite's index
   * @return the expression
   */
  public Expr before(int i) {
    return before.get(i);
  }

  /**
   * The whole expression just after rewrite {@code i}, raw.
   *
   * @param i the rewrite's index
   * @return the expression
   */
  public Expr after(int i) {
    return after.get(i);
  }

  /**
   * The whole expression at a boundary between rewrites: after the first {@code to} rewrites and
   * the normalisations that follow them, the rewriter's own tree there (the {@link #before} of the
   * next rewrite, or the end of the trace after the last). A step that ends at the boundary ends on
   * it, so the next step starts from exactly its after: canonicalising the raw tree instead can
   * land on another canonical shape of the same value.
   *
   * @param to how many rewrites came before, from 1 to the number of rewrites
   * @return the expression
   */
  public Expr boundary(int to) {
    return to == after.size() ? end : before.get(to);
  }

  /**
   * The subexpression at a path.
   *
   * @param root the expression
   * @param path the path
   * @return the subexpression, or empty when the path does not address one
   */
  public static Optional<Expr> at(Expr root, Path path) {
    Expr e = root;
    for (int k : path.indices()) {
      List<Expr> ops = Operands.of(e);
      if (k >= ops.size()) {
        return Optional.empty();
      }
      e = ops.get(k);
    }
    return Optional.of(e);
  }

  /**
   * Whether one path is the other or an ancestor or descendant of it.
   *
   * @param a a path
   * @param b a path
   * @return true if one is a prefix of the other
   */
  public static boolean related(Path a, Path b) {
    List<Integer> x = a.indices();
    List<Integer> y = b.indices();
    int n = Math.min(x.size(), y.size());
    return x.subList(0, n).equals(y.subList(0, n));
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
