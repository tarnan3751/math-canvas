package mathcanvas.engine.match;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;

/**
 * Where a name occurs in a tree, structurally: bound or free, through {@link Operands}, and the
 * fresh names that avoid it. A query over the tree, not a rewrite; whether a value depends on a
 * variable is {@code calculus/Free}'s question, not this one.
 *
 * <p>Two senses of occurring: {@link #occurs} sees a symbol node only, {@link #mentions} also the
 * head of an application, so {@code y} is mentioned in {@code y(x)} but does not occur in it.
 */
public final class Occurrence {

  private Occurrence() {}

  /**
   * Whether the symbol {@code x} occurs in {@code e} as a symbol node, bound or free.
   *
   * @param e the expression
   * @param x the symbol
   * @return true when it occurs
   */
  public static boolean occurs(Expr e, Symbol x) {
    return search(e, x, false);
  }

  /**
   * Whether the symbol {@code x} occurs in {@code e} as a symbol node or as the head of an
   * application, bound or free.
   *
   * @param e the expression
   * @param x the symbol
   * @return true when it is mentioned
   */
  public static boolean mentions(Expr e, Symbol x) {
    return search(e, x, true);
  }

  private static boolean search(Expr e, Symbol x, boolean heads) {
    Deque<Expr> todo = new ArrayDeque<>();
    todo.push(e);
    while (!todo.isEmpty()) {
      Expr n = todo.pop();
      if (n.equals(x) || heads && n instanceof Apply a && a.head().equals(x)) {
        return true;
      }
      for (Expr op : Operands.of(n)) {
        todo.push(op);
      }
    }
    return false;
  }

  /**
   * The names occurring in a tree, sorted: its symbols and the heads of its applications. The walk
   * keeps an identity set of the nodes visited, a traversal memo only, so a shared subtree is
   * walked once.
   *
   * @param e the expression
   * @return the names
   */
  public static Set<String> names(Expr e) {
    Set<String> out = new TreeSet<>();
    Set<Expr> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    Deque<Expr> nodes = new ArrayDeque<>();
    nodes.push(e);
    while (!nodes.isEmpty()) {
      Expr n = nodes.pop();
      if (!visited.add(n)) {
        continue;
      }
      if (n instanceof Symbol s) {
        out.add(s.name());
      } else if (n instanceof Apply a) {
        out.add(a.head().name());
      }
      for (Expr op : Operands.of(n)) {
        nodes.push(op);
      }
    }
    return out;
  }

  /**
   * {@code base}, or {@code base_k} for the least {@code k ≥ 1}: the first that {@linkplain #occurs
   * occurs} in none of {@code in}.
   *
   * @param base the name to start from
   * @param in the trees the symbol must not occur in
   * @return the fresh symbol
   */
  public static Symbol fresh(String base, Expr... in) {
    return fresh(base, x -> occursInAny(in, x));
  }

  /**
   * {@code base}, or {@code base_k} for the least {@code k ≥ 1}: the first not {@code taken}.
   *
   * @param base the name to start from
   * @param taken the symbols that may not be returned
   * @return the fresh symbol
   */
  public static Symbol fresh(String base, Predicate<Symbol> taken) {
    Symbol s = Symbol.of(base);
    for (int k = 1; taken.test(s); k++) {
      s = Symbol.of(base + "_" + k);
    }
    return s;
  }

  /**
   * {@code base_k} for the least {@code k ≥ 1} not {@code taken}, never {@code base} itself: the
   * renaming of a binder, which keeps its own name out of the choice.
   *
   * @param base the name to derive from
   * @param taken the symbols that may not be returned
   * @return the fresh symbol
   */
  public static Symbol subscripted(String base, Predicate<Symbol> taken) {
    for (int k = 1; ; k++) {
      Symbol s = Symbol.of(base + "_" + k);
      if (!taken.test(s)) {
        return s;
      }
    }
  }

  private static boolean occursInAny(Expr[] in, Symbol x) {
    for (Expr e : in) {
      if (occurs(e, x)) {
        return true;
      }
    }
    return false;
  }
}
