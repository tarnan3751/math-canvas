package mathcanvas.engine.rule;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.match.Operands;

/**
 * The rewrites of one operation, in order.
 *
 * <p>Each rewrite's path addresses the tree as it stood when the rule fired. The rewriter visits
 * operands before their parent and canonicalises a parent only after its operands, so in that tree
 * the ancestors are rebuilt raw around the rewritten operands, and each canonicalisation of a node
 * is recorded as a {@link Normalisation} that later paths refer to. Only the events are stored;
 * {@link #roots()} replays them to give the whole expression after each rewrite, so a trace whose
 * roots are never asked for costs only its records, and the roots share every unchanged subtree.
 * {@code Canonical.of(root)} is a root's display form.
 *
 * @param initial the canonical expression the operation started from; null in {@link #NONE}
 * @param result the result; null in {@link #NONE}
 * @param rewrites the rewrites
 * @param normalisations the canonicalisations of rebuilt nodes, between the rewrites
 */
public record Trace(
    Expr initial, Expr result, List<Rewrite> rewrites, List<Normalisation> normalisations) {

  /** The trace of an operation run without tracing: nothing recorded. */
  public static final Trace NONE = new Trace(null, null, List.of(), List.of());

  /**
   * A node canonicalised after its operands changed.
   *
   * @param afterRewrites how many rewrites came before it
   * @param path where the node is
   * @param node the canonical node that replaced it
   */
  public record Normalisation(int afterRewrites, Path path, Expr node) {}

  /** Copies the lists. */
  public Trace {
    rewrites = List.copyOf(rewrites);
    normalisations = List.copyOf(normalisations);
  }

  /**
   * Joins the traces of two operations run one after the other: the rewrites of {@code first}, then
   * those of {@code second}. A normalisation at the root between them puts {@code second}'s initial
   * expression in place, so the replay continues from exactly the tree {@code second}'s paths
   * address.
   *
   * @param first the earlier trace
   * @param second the later trace, which starts from {@code first}'s result
   * @return the joined trace
   * @throws IllegalArgumentException if either trace is {@link #NONE}, or {@code second} does not
   *     start from {@code first}'s result
   */
  public static Trace concat(Trace first, Trace second) {
    if (first.initial == null || second.initial == null) {
      throw new IllegalArgumentException("a trace recorded nothing to join");
    }
    if (!first.result.equals(second.initial)) {
      throw new IllegalArgumentException(
          "the second trace does not start from the first one's result");
    }
    int offset = first.rewrites.size();
    List<Rewrite> rewrites = new ArrayList<>(first.rewrites);
    rewrites.addAll(second.rewrites);
    List<Normalisation> normalisations = new ArrayList<>(first.normalisations);
    normalisations.add(new Normalisation(offset, Path.ROOT, second.initial));
    for (Normalisation n : second.normalisations) {
      normalisations.add(new Normalisation(n.afterRewrites() + offset, n.path(), n.node()));
    }
    return new Trace(first.initial, second.result, rewrites, normalisations);
  }

  /**
   * Returns the whole expression after each rewrite, by replaying the trace.
   *
   * @return one root per rewrite
   */
  public List<Expr> roots() {
    List<Expr> out = new ArrayList<>(rewrites.size());
    Expr current = initial;
    int next = 0;
    for (int i = 0; i < rewrites.size(); i++) {
      while (next < normalisations.size() && normalisations.get(next).afterRewrites() == i) {
        current =
            splice(
                current,
                normalisations.get(next).path().indices(),
                0,
                normalisations.get(next).node());
        next++;
      }
      Rewrite r = rewrites.get(i);
      current = splice(current, r.path().indices(), 0, r.after());
      out.add(current);
    }
    return List.copyOf(out);
  }

  /**
   * Returns the whole expression after rewrite {@code i}; iterate with {@link #roots()} instead,
   * which replays once.
   *
   * @param i the rewrite's index
   * @return the root after it
   */
  public Expr root(int i) {
    return roots().get(i);
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
