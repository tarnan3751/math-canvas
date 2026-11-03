package mathcanvas.engine.eval;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import mathcanvas.engine.builtin.Builtin;
import mathcanvas.engine.builtin.Builtins;
import mathcanvas.engine.calculus.VectorIntegrals;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Lambda;
import mathcanvas.engine.expr.Limit;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Piecewise;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Operands;

/**
 * Capture-avoiding substitution of symbols in raw trees, and the renaming of a binder's variable
 * ({@code docs/language.md} §11.2). Evaluation uses it to close a lambda over the values of the
 * frame it is built in, and to rename a binder its capture check refuses; values are otherwise
 * taken from frames, not substituted.
 *
 * <p>A binder's own variable is never substituted inside its scope. When a substituted value
 * mentions a binder's variable free, the binder's variable is renamed first, to {@code v_1}, {@code
 * v_2}, … (printed {@code v₁}), the first name that occurs nowhere in the binder or the values, so
 * the value's symbol is not captured. The binders are a lambda (its parameters, over its body), the
 * unevaluated forms (their variable, over their first operand) and the commands with a bound
 * variable (the second argument, a name or a list of names, over the first two arguments). Subtrees
 * that nothing changes are returned as they are, so a raw node keeps its identity.
 */
final class Substitution {

  private Substitution() {}

  /**
   * Substitutes the symbols named in {@code map} simultaneously.
   *
   * @param e the raw tree
   * @param map the values by symbol name
   * @return the tree with the free occurrences replaced
   */
  static Expr apply(Expr e, SortedMap<String, Expr> map) {
    if (map.isEmpty()) {
      return e;
    }
    return switch (e) {
      case Symbol s -> map.getOrDefault(s.name(), s);
      case Num _, Constant _, Bool _, mathcanvas.engine.expr.Unit _ -> e;
      case Lambda l -> binder(e, l.params(), List.of(l.params().size()), map);
      case Derivative d -> binder(e, List.of(d.var()), List.of(0), map);
      case Integral i -> binder(e, List.of(i.var()), List.of(0), map);
      case Limit l -> binder(e, List.of(l.var()), List.of(0), map);
      case BigSum s -> binder(e, List.of(s.var()), List.of(0), map);
      case BigProduct p -> binder(e, List.of(p.var()), List.of(0), map);
      case Apply a when VectorIntegrals.COMMANDS.contains(a.head().name()) -> field(a, map);
      case Apply a -> {
        List<Symbol> bound = boundBy(a);
        yield bound.isEmpty() ? operands(e, map) : binder(e, bound, List.of(0, 1), map);
      }
      case Sum _,
          Product _,
          Power _,
          Relation _,
          ListExpr _,
          Vector _,
          SetExpr _,
          Matrix _,
          Interval _,
          Piecewise _ ->
          operands(e, map);
    };
  }

  /**
   * {@code lineintegral} and {@code surfaceintegral}: the field's {@code x, y, z} and the
   * parameters are bound, the coordinates in the field, a parameter in the path or surface and in
   * the inner bracket's bounds; the parameters keep their names, with no renaming against a value
   * that mentions one.
   */
  private static Expr field(Apply a, SortedMap<String, Expr> map) {
    List<Expr> ops = new ArrayList<>(a.args());
    List<String> parameters = new ArrayList<>();
    boolean line = a.head().name().equals("lineintegral");
    if (line) {
      if (ops.size() > 2 && ops.get(2) instanceof Symbol t) {
        parameters.add(t.name());
      }
    } else {
      for (Expr b : ops.subList(Math.min(2, ops.size()), ops.size())) {
        if (Operands.of(b).size() == 3 && Operands.of(b).getFirst() instanceof Symbol p) {
          parameters.add(p.name());
        }
      }
    }
    TreeMap<String, Expr> inside = new TreeMap<>(map);
    parameters.forEach(inside::remove);
    TreeMap<String, Expr> field = new TreeMap<>(inside);
    VectorIntegrals.COORDINATES.forEach(c -> field.remove(c.name()));
    for (int k = 0; k < ops.size(); k++) {
      Expr op = ops.get(k);
      if (k == 0) {
        ops.set(k, apply(op, field));
      } else if (k == 1) {
        ops.set(k, apply(op, inside));
      } else if (line) {
        ops.set(k, k == 2 ? op : apply(op, map));
      } else {
        List<Expr> items = new ArrayList<>(Operands.of(op));
        if (items.size() == 3 && items.getFirst() instanceof Symbol) {
          // the inner bracket's bounds may hold the outer parameter; the outer bracket's see all
          SortedMap<String, Expr> bounds = k == 2 ? inside : map;
          items.set(1, apply(items.get(1), bounds));
          items.set(2, apply(items.get(2), bounds));
          ops.set(k, Operands.rebuild(op, items));
        } else {
          ops.set(k, apply(op, map));
        }
      }
    }
    return ops.equals(a.args()) ? a : new Apply(a.head(), ops);
  }

  /**
   * Renames every occurrence of a symbol, bound positions included (a function head is not an
   * occurrence). Used where no capture is possible: for a name chosen to occur nowhere else.
   *
   * @param e the tree
   * @param from the name replaced
   * @param to the symbol put in its place
   * @return the tree
   */
  static Expr rename(Expr e, String from, Symbol to) {
    if (e instanceof Symbol s) {
      return s.name().equals(from) ? to : s;
    }
    List<Expr> ops = Operands.of(e);
    List<Expr> out = null;
    for (int k = 0; k < ops.size(); k++) {
      Expr after = rename(ops.get(k), from, to);
      if (after != ops.get(k)) {
        if (out == null) {
          out = new ArrayList<>(ops);
        }
        out.set(k, after);
      }
    }
    return out == null ? e : Operands.rebuild(e, out);
  }

  /**
   * The symbols an application of a command binds: its variable argument, a name or a list of
   * names; none for any other application.
   *
   * @param a the application
   * @return the bound symbols
   */
  static List<Symbol> boundBy(Apply a) {
    Builtin b = Builtins.lookup(a.head().name()).orElse(null);
    if (b == null
        || b.binderFor(a.args().size()).isEmpty()
        || a.args().size() < b.binderFor(a.args().size()).getAsInt()) {
      return List.of();
    }
    Expr var = a.args().get(b.binderFor(a.args().size()).getAsInt() - 1);
    if (var instanceof Symbol s) {
      return List.of(s);
    }
    List<Expr> items =
        var instanceof ListExpr l
            ? l.items()
            : var instanceof Vector v ? v.items() : List.<Expr>of();
    List<Symbol> out = new ArrayList<>();
    for (Expr item : items) {
      if (!(item instanceof Symbol s)) {
        return List.of();
      }
      out.add(s);
    }
    return out;
  }

  /**
   * Renames a binder's variable {@code from} to {@code to}, in its variable position and through
   * its scope (where an inner binder of {@code from} stops it); {@code to} must occur nowhere in
   * the binder.
   *
   * @param binder a lambda, an unevaluated form or a command with a bound variable
   * @param from the variable's name
   * @param to the new variable
   * @return the binder
   */
  static Expr renameBinder(Expr binder, String from, Symbol to) {
    TreeMap<String, Expr> map = new TreeMap<>(Map.of(from, to));
    List<Expr> ops = new ArrayList<>(Operands.of(binder));
    List<Integer> scope = scope(binder);
    for (int k = 0; k < ops.size(); k++) {
      if (isVariable(binder, k)) {
        ops.set(k, rename(ops.get(k), from, to));
      } else if (scope.contains(k)) {
        ops.set(k, apply(ops.get(k), map));
      }
    }
    return Operands.rebuild(binder, ops);
  }

  /** The operand positions a binder's variable is bound in. */
  private static List<Integer> scope(Expr binder) {
    if (binder instanceof Lambda l) {
      return List.of(l.params().size());
    }
    if (binder instanceof Apply a) {
      // the arguments before the variable: the first for most commands, two for area(f, g, x, …)
      int variable = variable(a);
      List<Integer> before = new ArrayList<>();
      for (int k = 0; k < variable; k++) {
        before.add(k);
      }
      return before;
    }
    return List.of(0);
  }

  /**
   * Whether a symbol named {@code name} occurs anywhere in {@code e}, bound or free.
   *
   * @param e the tree
   * @param name the name
   * @return true if it occurs
   */
  static boolean occurs(Expr e, String name) {
    if (e instanceof Symbol s) {
      return s.name().equals(name);
    }
    for (Expr op : Operands.of(e)) {
      if (occurs(op, name)) {
        return true;
      }
    }
    return false;
  }

  /**
   * The name {@code base_k} for the least {@code k ≥ 1} that occurs in none of {@code trees}.
   *
   * @param base the name to derive from
   * @param trees the trees the name must not occur in
   * @return the fresh name
   */
  static String fresh(String base, Collection<Expr> trees) {
    for (int k = 1; ; k++) {
      String name = base + "_" + k;
      boolean used = false;
      for (Expr t : trees) {
        if (occurs(t, name)) {
          used = true;
          break;
        }
      }
      if (!used) {
        return name;
      }
    }
  }

  private static Expr operands(Expr e, SortedMap<String, Expr> map) {
    List<Expr> ops = Operands.of(e);
    List<Expr> out = null;
    for (int k = 0; k < ops.size(); k++) {
      Expr after = apply(ops.get(k), map);
      if (after != ops.get(k)) {
        if (out == null) {
          out = new ArrayList<>(ops);
        }
        out.set(k, after);
      }
    }
    return out == null ? e : Operands.rebuild(e, out);
  }

  /**
   * A binder: the operands in {@code scope} see the map without the bound names, the bound
   * positions keep their symbols, the other operands see the whole map. A bound symbol that a value
   * used in the scope mentions is renamed first.
   */
  private static Expr binder(
      Expr e, List<Symbol> bound, List<Integer> scope, SortedMap<String, Expr> map) {
    TreeMap<String, Expr> inner = new TreeMap<>(map);
    for (Symbol b : bound) {
      inner.remove(b.name());
    }
    List<Expr> ops = new ArrayList<>(Operands.of(e));
    if (!inner.isEmpty()) {
      List<Expr> trees = new ArrayList<>(inner.values());
      trees.add(e);
      for (Symbol b : bound) {
        if (mentionedBy(inner.values(), b.name())) {
          Symbol fresh = Symbol.of(fresh(b.name(), trees));
          TreeMap<String, Expr> renaming = new TreeMap<>(Map.of(b.name(), fresh));
          for (int k = 0; k < ops.size(); k++) {
            if (scope.contains(k)) {
              ops.set(k, apply(ops.get(k), renaming));
            } else if (isVariable(e, k)) {
              ops.set(k, rename(ops.get(k), b.name(), fresh));
            }
          }
          trees.add(fresh);
        }
      }
    }
    List<Expr> original = Operands.of(e);
    boolean changed = false;
    for (int k = 0; k < ops.size(); k++) {
      Expr op = ops.get(k);
      if (scope.contains(k)) {
        op = apply(op, inner);
      } else if (!isVariable(e, k)) {
        op = apply(op, map);
      }
      ops.set(k, op);
      changed |= op != original.get(k);
    }
    return changed ? Operands.rebuild(e, ops) : e;
  }

  /** Whether operand {@code k} of a binder holds its variable or variables. */
  private static boolean isVariable(Expr e, int k) {
    if (e instanceof Apply a) {
      return k == variable(a);
    }
    return Operands.isBound(e, k);
  }

  /** The 0-based position of a binder command's variable in this call. */
  private static int variable(Apply a) {
    return Builtins.lookup(a.head().name()).orElseThrow().binderFor(a.args().size()).getAsInt() - 1;
  }

  private static boolean mentionedBy(Collection<Expr> values, String name) {
    for (Expr v : values) {
      if (occurs(v, name)) {
        return true;
      }
    }
    return false;
  }
}
