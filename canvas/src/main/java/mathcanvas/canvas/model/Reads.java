package mathcanvas.canvas.model;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Set;
import java.util.TreeSet;
import mathcanvas.engine.builtin.Builtins;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;

/**
 * The names an object's expressions read, for {@link CanvasObject#reads()}. A query over the tree,
 * not a rewrite: every symbol, and the head of every application that is not a builtin (a worksheet
 * function), less the object's own variables. A binder's variable (a Σ index, a lambda parameter)
 * is counted too: the over-approximation costs at most a recompute when a variable of that name
 * changes, and keeps this walk from restating the engine's scoping rules.
 */
final class Reads {

  private Reads() {}

  /**
   * The names {@code exprs} read, less {@code own}.
   *
   * @param own the object's own variables, which are not engine variables
   * @param exprs the expressions
   * @return the names, sorted
   */
  static Set<String> of(Set<Symbol> own, Expr... exprs) {
    Set<String> out = new TreeSet<>();
    Deque<Expr> todo = new ArrayDeque<>();
    for (Expr e : exprs) {
      todo.push(e);
    }
    while (!todo.isEmpty()) {
      Expr e = todo.pop();
      if (e instanceof Symbol s) {
        if (!own.contains(s) && !Builtins.isConstant(s.name())) {
          out.add(s.name());
        }
      } else if (e instanceof Apply a && !Builtins.isCallable(a.head().name())) {
        out.add(a.head().name());
      }
      for (Expr child : Operands.of(e)) {
        todo.push(child);
      }
    }
    return Collections.unmodifiableSet(out);
  }
}
