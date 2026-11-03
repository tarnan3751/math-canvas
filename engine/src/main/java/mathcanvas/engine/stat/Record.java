package mathcanvas.engine.stat;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;

/**
 * The steps of a regression or a test, each a rewrite at the root replacing the whole state: the
 * call, what it passes through, then its value, and the steps of an operation it calls spliced in
 * (the linear algebra's row operations). A note leaves the state as it is. Narrated by {@link
 * StatisticsNarrator}, the spliced steps by their own narrators.
 */
final class Record {

  private final Context ctx;
  private final Expr call;
  private final List<Rewrite> rewrites = new ArrayList<>();
  private final List<Trace.Normalisation> normalisations = new ArrayList<>();
  private Expr current;

  Record(Expr call, Context ctx) {
    this.ctx = ctx;
    this.call = Canonical.of(call, ctx.precision(), ctx.cancellation());
    this.current = this.call;
  }

  /** Records the step {@code set.rule} to {@code after}, with named values. */
  void step(String set, String rule, Expr after, TreeMap<String, Expr> values) {
    Expr next = Canonical.of(after, ctx.precision(), ctx.cancellation());
    if (ctx.traceEnabled()) {
      rewrites.add(
          new Rewrite(current, next, set, rule, Path.ROOT, Bindings.EMPTY, canonical(values)));
    }
    current = next;
  }

  /** A note that leaves the state as it is. */
  void note(String set, String rule, TreeMap<String, Expr> values) {
    if (ctx.traceEnabled()) {
      rewrites.add(
          new Rewrite(current, current, set, rule, Path.ROOT, Bindings.EMPTY, canonical(values)));
    }
  }

  /**
   * Splices in an operation's steps, which start from the current state, and moves to its value.
   */
  void splice(Rewriter.Result r) {
    Trace t = r.trace();
    if (ctx.traceEnabled() && !t.rewrites().isEmpty()) {
      int offset = rewrites.size();
      normalisations.add(new Trace.Normalisation(offset, Path.ROOT, t.initial()));
      rewrites.addAll(t.rewrites());
      for (Trace.Normalisation n : t.normalisations()) {
        normalisations.add(new Trace.Normalisation(n.afterRewrites() + offset, n.path(), n.node()));
      }
    }
    current = r.expr();
  }

  /** The current state. */
  Expr current() {
    return current;
  }

  /** The value and the trace. */
  Rewriter.Result result() {
    Trace trace =
        ctx.traceEnabled() && !rewrites.isEmpty()
            ? new Trace(call, current, rewrites, normalisations)
            : Trace.NONE;
    return new Rewriter.Result(current, trace);
  }

  private TreeMap<String, Expr> canonical(TreeMap<String, Expr> values) {
    TreeMap<String, Expr> out = new TreeMap<>();
    values.forEach((k, v) -> out.put(k, Canonical.of(v, ctx.precision(), ctx.cancellation())));
    return out;
  }
}
