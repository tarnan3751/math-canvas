package mathcanvas.engine.linalg;

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
 * The steps of one operation, each a rewrite at the root replacing the whole state: the call, the
 * matrices it passes through, then its value. A step that changes nothing is not recorded, except a
 * terminal note ({@link #note}), so the steps chain from the call to the value.
 */
final class Recorder {

  private final Expr call;
  private final Context ctx;
  private final List<Rewrite> rewrites = new ArrayList<>();
  private Expr current;

  Recorder(Expr call, Context ctx) {
    this.call = Canonical.of(call, ctx.precision());
    this.ctx = ctx;
    this.current = this.call;
  }

  /** The call, the first state. */
  Expr call() {
    return call;
  }

  /** The current state. */
  Expr current() {
    return current;
  }

  /** Whether a step has been recorded. */
  boolean started() {
    return !current.equals(call);
  }

  /**
   * Records the step {@code set.rule} to {@code after}, with named values, unless it changes
   * nothing.
   */
  void step(String set, String rule, Expr after, TreeMap<String, Expr> values) {
    Expr next = Canonical.of(after, ctx.precision());
    if (next.equals(current)) {
      return;
    }
    if (ctx.traceEnabled()) {
      rewrites.add(
          new Rewrite(current, next, set, rule, Path.ROOT, Bindings.EMPTY, canonical(values)));
    }
    current = next;
  }

  /** A terminal note that leaves the state as it is. */
  void note(String set, String rule, TreeMap<String, Expr> values) {
    if (ctx.traceEnabled()) {
      rewrites.add(
          new Rewrite(current, current, set, rule, Path.ROOT, Bindings.EMPTY, canonical(values)));
    }
  }

  /** The value and the trace. */
  Rewriter.Result result() {
    Trace trace = ctx.traceEnabled() ? new Trace(call, current, rewrites, List.of()) : Trace.NONE;
    return new Rewriter.Result(current, trace);
  }

  private TreeMap<String, Expr> canonical(TreeMap<String, Expr> values) {
    TreeMap<String, Expr> out = new TreeMap<>();
    values.forEach((k, v) -> out.put(k, Canonical.of(v, ctx.precision())));
    return out;
  }

  /** Named values from name and value pairs; a null value is left out. */
  static TreeMap<String, Expr> values(Object... pairs) {
    TreeMap<String, Expr> out = new TreeMap<>();
    for (int k = 0; k < pairs.length; k += 2) {
      if (pairs[k + 1] != null) {
        out.put((String) pairs[k], (Expr) pairs[k + 1]);
      }
    }
    return out;
  }
}
