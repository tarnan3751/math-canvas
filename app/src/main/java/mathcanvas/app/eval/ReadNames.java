package mathcanvas.app.eval;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import mathcanvas.engine.builtin.Builtins;
import mathcanvas.engine.eval.Definition;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;

/**
 * The names an expression reads, for deciding what a changed canvas variable makes stale. A query
 * over the tree, never a rewrite, run on the worker: every symbol that is not a constant, and the
 * head of every call that is not a builtin (a worksheet function). Binders are not scoped (a Σ
 * index or a lambda parameter counts too), so the answer may name too much, which costs an
 * evaluation, never too little.
 */
final class ReadNames {

  private ReadNames() {}

  /**
   * The names an expression reads directly.
   *
   * @param e the expression
   * @return the names
   */
  static Set<String> of(Expr e) {
    Set<String> out = new HashSet<>();
    Deque<Expr> todo = new ArrayDeque<>();
    todo.push(e);
    while (!todo.isEmpty()) {
      Expr x = todo.pop();
      if (x instanceof Symbol s) {
        if (!Builtins.isConstant(s.name())) {
          out.add(s.name());
        }
      } else if (x instanceof Apply a && !Builtins.isCallable(a.head().name())) {
        out.add(a.head().name());
      }
      for (Expr child : Operands.of(x)) {
        todo.push(child);
      }
    }
    return out;
  }

  /**
   * Some names with every name their definitions read, and theirs, in an environment: what
   * evaluating the names in it reads. A function's parameters are its own, not read.
   *
   * @param names the names
   * @param env the environment
   * @return the names and everything they read
   */
  static Set<String> closure(Set<String> names, Environment env) {
    Set<String> out = new HashSet<>(names);
    Deque<String> todo = new ArrayDeque<>(names);
    while (!todo.isEmpty()) {
      Optional<Definition> d = env.definition(todo.pop());
      if (d.isEmpty()) {
        continue;
      }
      Set<String> read = of(d.get().body());
      if (d.get() instanceof Definition.Function f) {
        for (Symbol p : f.params()) {
          read.remove(p.name());
        }
      }
      for (String n : read) {
        if (out.add(n)) {
          todo.push(n);
        }
      }
    }
    return out;
  }
}
