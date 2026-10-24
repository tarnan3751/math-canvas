package mathcanvas.engine.expr;

import java.util.Comparator;
import java.util.List;
import mathcanvas.engine.expr.Piecewise.Piece;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.RealNumeric;

/**
 * The total order on expressions that the canonical form sorts by, shaped after Cohen's order (Joel
 * S. Cohen, <i>Computer Algebra and Symbolic Computation: Mathematical Methods</i>, §3.1, rules O-1
 * to O-13), which puts numbers first and keeps like terms adjacent: {@code x < 2x < x^2 < y < x·y}.
 *
 * <p>{@code compare(u, v)} is the first applicable step:
 *
 * <ol>
 *   <li><b>Numbers</b> come before everything else. Two real numbers compare by the tower's {@link
 *       RealNumeric#compareTo} (exact before inexact on a value tie); a complex number comes after
 *       every real, and two complex numbers compare by real part, then imaginary part (O-1, O-7).
 *   <li>If either side is a <b>product</b>, the operand lists compare from the last operand
 *       backwards, and a list that runs out first comes first; a non-product {@code v} counts as
 *       the list {@code [v]} (O-3, O-8).
 *   <li>Otherwise, if either side is a <b>power</b>, the bases compare, then the exponents; a
 *       non-power {@code v} counts as {@code v^1}, and comes before a power it ties with, so {@code
 *       x < x^1} (O-4, O-9).
 *   <li>Otherwise, if either side is a <b>sum</b>, as for products (O-3, O-10).
 *   <li>Otherwise by kind: constants first, in {@link Constant.Kind} order; then symbols and
 *       applications together by name, a symbol before an application of the same name, and two
 *       applications of one name by their arguments from the left, fewer first (O-2, O-6, O-12);
 *       then the other kinds in the order derivative, integral, limit, Σ, Π, lambda, relation,
 *       interval, piecewise, vector, matrix, list, set, truth value, each compared field by field.
 * </ol>
 *
 * <p>Each mixed step compares a strict part of one side with the whole of the other, which keeps
 * the order transitive. The order is consistent with {@code equals} and deterministic: it uses no
 * hash and no identity.
 */
public final class ExprOrder implements Comparator<Expr> {

  /** The order. */
  public static final ExprOrder INSTANCE = new ExprOrder();

  private ExprOrder() {}

  @Override
  public int compare(Expr u, Expr v) {
    if (u == v) {
      return 0;
    }
    if (u instanceof Num a) {
      return v instanceof Num b ? compareNumbers(a.value(), b.value()) : -1;
    }
    if (v instanceof Num) {
      return 1;
    }
    if (u instanceof Product || v instanceof Product) {
      return compareFromLast(factors(u), factors(v));
    }
    if (u instanceof Power || v instanceof Power) {
      return comparePowers(u, v);
    }
    if (u instanceof Sum || v instanceof Sum) {
      return compareFromLast(terms(u), terms(v));
    }
    return compareAtoms(u, v);
  }

  /** The order on numbers: reals by the tower's order, then complex numbers by their parts. */
  static int compareNumbers(Numeric a, Numeric b) {
    return switch (a) {
      case RealNumeric x ->
          switch (b) {
            case RealNumeric y -> x.compareTo(y);
            case Complex _ -> -1;
          };
      case Complex x ->
          switch (b) {
            case RealNumeric _ -> 1;
            case Complex y -> {
              int re = x.re().compareTo(y.re());
              yield re != 0 ? re : x.im().compareTo(y.im());
            }
          };
    };
  }

  private static List<Expr> factors(Expr e) {
    return e instanceof Product p ? p.factors() : List.of(e);
  }

  private static List<Expr> terms(Expr e) {
    return e instanceof Sum s ? s.terms() : List.of(e);
  }

  /** Cohen's O-3: from the last operands backwards; a list that runs out first comes first. */
  private int compareFromLast(List<Expr> a, List<Expr> b) {
    int i = a.size() - 1;
    int j = b.size() - 1;
    for (; i >= 0 && j >= 0; i--, j--) {
      int order = compare(a.get(i), b.get(j));
      if (order != 0) {
        return order;
      }
    }
    return Integer.compare(a.size(), b.size());
  }

  private int comparePowers(Expr u, Expr v) {
    Expr ub = u instanceof Power p ? p.base() : u;
    Expr vb = v instanceof Power p ? p.base() : v;
    int order = compare(ub, vb);
    if (order != 0) {
      return order;
    }
    Expr ue = u instanceof Power p ? p.exponent() : Num.ONE;
    Expr ve = v instanceof Power p ? p.exponent() : Num.ONE;
    order = compare(ue, ve);
    return order != 0 ? order : Boolean.compare(u instanceof Power, v instanceof Power);
  }

  /** Step 5: everything but numbers, products, powers and sums. */
  private int compareAtoms(Expr u, Expr v) {
    int order = Integer.compare(rank(u), rank(v));
    if (order != 0) {
      return order;
    }
    return switch (u) {
      case Constant a -> a.kind().compareTo(((Constant) v).kind());
      case Symbol a -> compareNamed(a.name(), List.of(), false, v);
      case Apply a -> compareNamed(a.head().name(), a.args(), true, v);
      case Derivative a -> {
        Derivative b = (Derivative) v;
        order = compare(a.expr(), b.expr());
        if (order == 0) {
          order = compare(a.var(), b.var());
        }
        yield order != 0 ? order : Integer.compare(a.order(), b.order());
      }
      case Integral a -> {
        Integral b = (Integral) v;
        order = compare(a.integrand(), b.integrand());
        if (order == 0) {
          order = compare(a.var(), b.var());
        }
        if (order == 0) {
          order = Boolean.compare(a.isDefinite(), b.isDefinite());
        }
        if (order == 0 && a.isDefinite()) {
          order = compareLists(List.of(a.lower(), a.upper()), List.of(b.lower(), b.upper()));
        }
        yield order;
      }
      case Limit a -> {
        Limit b = (Limit) v;
        order =
            compareLists(
                List.of(a.expr(), a.var(), a.point()), List.of(b.expr(), b.var(), b.point()));
        yield order != 0 ? order : a.direction().compareTo(b.direction());
      }
      case BigSum a -> {
        BigSum b = (BigSum) v;
        yield compareLists(
            List.of(a.body(), a.var(), a.from(), a.to()),
            List.of(b.body(), b.var(), b.from(), b.to()));
      }
      case BigProduct a -> {
        BigProduct b = (BigProduct) v;
        yield compareLists(
            List.of(a.body(), a.var(), a.from(), a.to()),
            List.of(b.body(), b.var(), b.from(), b.to()));
      }
      case Lambda a -> {
        Lambda b = (Lambda) v;
        order = compareLists(a.params(), b.params());
        yield order != 0 ? order : compare(a.body(), b.body());
      }
      case Relation a -> {
        Relation b = (Relation) v;
        order = a.op().compareTo(b.op());
        yield order != 0
            ? order
            : compareLists(List.of(a.left(), a.right()), List.of(b.left(), b.right()));
      }
      case Interval a -> {
        Interval b = (Interval) v;
        order = compareLists(List.of(a.lower(), a.upper()), List.of(b.lower(), b.upper()));
        if (order == 0) {
          order = Boolean.compare(a.lowerClosed(), b.lowerClosed());
        }
        yield order != 0 ? order : Boolean.compare(a.upperClosed(), b.upperClosed());
      }
      case Piecewise a -> {
        Piecewise b = (Piecewise) v;
        order = comparePieces(a.pieces(), b.pieces());
        yield order != 0 ? order : compare(a.otherwise(), b.otherwise());
      }
      case Vector a -> compareLists(a.items(), ((Vector) v).items());
      case Matrix a -> compareRows(a.rows(), ((Matrix) v).rows());
      case ListExpr a -> compareLists(a.items(), ((ListExpr) v).items());
      case SetExpr a -> compareLists(a.items(), ((SetExpr) v).items());
      case Bool a -> Boolean.compare(a.value(), ((Bool) v).value());
      case Num _, Sum _, Product _, Power _ -> throw new IllegalStateException("not an atom: " + u);
    };
  }

  /** Symbols and applications by name; a symbol first; applications by arguments (O-6, O-12). */
  private int compareNamed(String name, List<Expr> args, boolean applied, Expr v) {
    String otherName = v instanceof Apply b ? b.head().name() : ((Symbol) v).name();
    int order = name.compareTo(otherName);
    if (order != 0) {
      return order;
    }
    order = Boolean.compare(applied, v instanceof Apply);
    return order != 0 || !applied ? order : compareLists(args, ((Apply) v).args());
  }

  /** From the first operands forwards; a list that runs out first comes first. */
  private int compareLists(List<? extends Expr> a, List<? extends Expr> b) {
    for (int k = 0; k < a.size() && k < b.size(); k++) {
      int order = compare(a.get(k), b.get(k));
      if (order != 0) {
        return order;
      }
    }
    return Integer.compare(a.size(), b.size());
  }

  private int compareRows(List<List<Expr>> a, List<List<Expr>> b) {
    for (int k = 0; k < a.size() && k < b.size(); k++) {
      int order = compareLists(a.get(k), b.get(k));
      if (order != 0) {
        return order;
      }
    }
    return Integer.compare(a.size(), b.size());
  }

  private int comparePieces(List<Piece> a, List<Piece> b) {
    for (int k = 0; k < a.size() && k < b.size(); k++) {
      int order = compare(a.get(k).value(), b.get(k).value());
      if (order == 0) {
        order = compare(a.get(k).condition(), b.get(k).condition());
      }
      if (order != 0) {
        return order;
      }
    }
    return Integer.compare(a.size(), b.size());
  }

  /** The kind rank of step 5; symbols and applications share one rank. */
  private static int rank(Expr e) {
    return switch (e) {
      case Num _, Sum _, Product _, Power _ -> -1;
      case Constant _ -> 0;
      case Symbol _, Apply _ -> 1;
      case Derivative _ -> 2;
      case Integral _ -> 3;
      case Limit _ -> 4;
      case BigSum _ -> 5;
      case BigProduct _ -> 6;
      case Lambda _ -> 7;
      case Relation _ -> 8;
      case Interval _ -> 9;
      case Piecewise _ -> 10;
      case Vector _ -> 11;
      case Matrix _ -> 12;
      case ListExpr _ -> 13;
      case SetExpr _ -> 14;
      case Bool _ -> 15;
    };
  }
}
