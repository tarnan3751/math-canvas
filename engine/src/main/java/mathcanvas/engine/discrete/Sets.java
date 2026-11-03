package mathcanvas.engine.discrete;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.parse.Brackets;

/**
 * The operations on finite sets ({@code docs/language.md} §14.24): union, intersection, difference,
 * symmetric difference, the power set, the Cartesian product and membership. Two items are the same
 * when they are equal as trees or are numbers of equal value, and distinct when both are closed
 * values (numbers, truth values, or collections of them) that differ; otherwise it is not known,
 * and an operation that would need to know leaves its call ({@link #apply} gives null). Results are
 * canonical sets.
 */
public final class Sets {

  /** The set functions this class evaluates; {@code ∪}, {@code ∩} and {@code \} are the first. */
  public static final Set<String> FUNCTIONS =
      Set.of("union", "intersect", "setdiff", "symdiff", "subsets", "cartesian");

  /** The most items of a set {@code subsets} takes. */
  public static final int MAX_SUBSETS = 16;

  /** The most pairs {@code cartesian} forms. */
  public static final int MAX_PAIRS = 100_000;

  private Sets() {}

  /**
   * Evaluates a set function of evaluated arguments.
   *
   * @param name the function
   * @param args the arguments, canonical
   * @param cancellation the request to stop
   * @return the value, or null when the call stays
   * @throws IllegalArgumentException if an argument is a value that is not a set
   */
  public static Expr apply(String name, List<Expr> args, Cancellation cancellation) {
    List<SetExpr> sets = new ArrayList<>();
    for (Expr arg : args) {
      if (arg instanceof SetExpr s) {
        sets.add(s);
      } else if (isValue(arg)) {
        throw new IllegalArgumentException(
            name + " takes " + (args.size() == 1 ? "a set" : "sets"));
      } else {
        return null;
      }
    }
    return switch (name) {
      case "union" -> union(sets.getFirst(), sets.get(1));
      case "intersect" -> intersect(sets.getFirst(), sets.get(1));
      case "setdiff" -> difference(sets.getFirst(), sets.get(1));
      case "symdiff" -> {
        Expr ab = difference(sets.getFirst(), sets.get(1));
        Expr ba = difference(sets.get(1), sets.getFirst());
        yield ab == null || ba == null ? null : union((SetExpr) ab, (SetExpr) ba);
      }
      case "subsets" -> subsets(sets.getFirst(), cancellation);
      case "cartesian" -> cartesian(sets.getFirst(), sets.get(1), cancellation);
      default -> throw new IllegalStateException("no set function " + name);
    };
  }

  /**
   * Whether an expression is a value that is not a set, so cannot become one: a number, a truth
   * value, a domain, a relation or a list, vector or matrix.
   *
   * @param e the expression
   * @return true for such a value
   */
  public static boolean isValue(Expr e) {
    return e instanceof Num
        || e instanceof Bool
        || e instanceof Relation
        || e instanceof ListExpr
        || e instanceof Vector
        || e instanceof Matrix
        || (e instanceof Constant k
            && switch (k.kind()) {
              case INTEGERS, RATIONALS, REALS, COMPLEXES -> true;
              default -> false;
            });
  }

  /**
   * Whether two items are the same element.
   *
   * @param a an item
   * @param b another
   * @return true, false, or unknown when that depends on the values of symbols
   */
  public static Tri same(Expr a, Expr b) {
    if (a.equals(b)) {
      return Tri.TRUE;
    }
    if (a instanceof Num x && b instanceof Num y) {
      Numeric p = x.value();
      Numeric q = y.value();
      return Tri.of(p.re().compareValue(q.re()) == 0 && p.im().compareValue(q.im()) == 0);
    }
    if (a instanceof Bool && b instanceof Bool) {
      return Tri.FALSE;
    }
    List<Expr> left = items(a);
    List<Expr> right = items(b);
    if (left != null && right != null) {
      if (a.getClass() != b.getClass() || left.size() != right.size()) {
        return closed(a) && closed(b) ? Tri.FALSE : Tri.UNKNOWN;
      }
      Tri all = Tri.TRUE;
      for (int i = 0; i < left.size(); i++) {
        Tri t = same(left.get(i), right.get(i));
        if (t == Tri.FALSE) {
          return Tri.FALSE;
        }
        if (t == Tri.UNKNOWN) {
          all = Tri.UNKNOWN;
        }
      }
      return all;
    }
    return closed(a) && closed(b) ? Tri.FALSE : Tri.UNKNOWN;
  }

  /** The items of a list, vector or matrix (its rows), or null. */
  private static List<Expr> items(Expr e) {
    return switch (e) {
      case ListExpr l -> l.items();
      case Vector v -> v.items();
      case Matrix m -> m.rows().stream().<Expr>map(Vector::of).toList();
      default -> null;
    };
  }

  /** Whether an expression is a closed value: a number, a truth value, a collection of them. */
  private static boolean closed(Expr e) {
    if (e instanceof Num || e instanceof Bool) {
      return true;
    }
    List<Expr> items = items(e);
    return items != null && items.stream().allMatch(Sets::closed);
  }

  /**
   * Whether {@code x} is an item of {@code s}.
   *
   * @param x the item
   * @param s the set
   * @return true when it is one of them, false when it is distinct from each, else unknown
   */
  public static Tri member(Expr x, SetExpr s) {
    Tri result = Tri.FALSE;
    for (Expr item : s.items()) {
      Tri t = same(x, item);
      if (t == Tri.TRUE) {
        return Tri.TRUE;
      }
      if (t == Tri.UNKNOWN) {
        result = Tri.UNKNOWN;
      }
    }
    return result;
  }

  private static Expr union(SetExpr a, SetExpr b) {
    List<Expr> out = new ArrayList<>(a.items());
    for (Expr item : b.items()) {
      if (member(item, a) != Tri.TRUE) {
        out.add(item);
      }
    }
    return Canonical.of(new SetExpr(out));
  }

  private static Expr intersect(SetExpr a, SetExpr b) {
    List<Expr> out = new ArrayList<>();
    for (Expr item : a.items()) {
      switch (member(item, b)) {
        case TRUE -> out.add(item);
        case UNKNOWN -> {
          return null;
        }
        case FALSE -> {}
      }
    }
    return Canonical.of(new SetExpr(out));
  }

  private static Expr difference(SetExpr a, SetExpr b) {
    List<Expr> out = new ArrayList<>();
    for (Expr item : a.items()) {
      switch (member(item, b)) {
        case FALSE -> out.add(item);
        case UNKNOWN -> {
          return null;
        }
        case TRUE -> {}
      }
    }
    return Canonical.of(new SetExpr(out));
  }

  private static Expr subsets(SetExpr a, Cancellation cancellation) {
    int n = a.items().size();
    if (n > MAX_SUBSETS) {
      throw new IllegalArgumentException(
          "subsets takes a set of at most " + MAX_SUBSETS + " items");
    }
    List<Expr> out = new ArrayList<>(1 << n);
    for (int mask = 0; mask < 1 << n; mask++) {
      if ((mask & 1023) == 0) {
        cancellation.check();
      }
      List<Expr> subset = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        if ((mask >> i & 1) == 1) {
          subset.add(a.items().get(i));
        }
      }
      out.add(new SetExpr(subset));
    }
    return Canonical.of(new SetExpr(out));
  }

  private static Expr cartesian(SetExpr a, SetExpr b, Cancellation cancellation) {
    if ((long) a.items().size() * b.items().size() > MAX_PAIRS) {
      throw new IllegalArgumentException("cartesian forms at most " + MAX_PAIRS + " pairs");
    }
    List<Expr> out = new ArrayList<>();
    for (Expr x : a.items()) {
      cancellation.check();
      for (Expr y : b.items()) {
        out.add(Brackets.of(List.of(x, y)));
      }
    }
    return Canonical.of(new SetExpr(out));
  }

  /**
   * Whether an expression is an application of a set operation, which is not a scalar and carries
   * no arithmetic.
   *
   * @param e the expression
   * @return true for {@code union(…)} and the others of {@link #FUNCTIONS}
   */
  public static boolean isSetOperation(Expr e) {
    return e instanceof Apply a && FUNCTIONS.contains(a.head().name());
  }
}
