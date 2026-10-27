package mathcanvas.engine.eval;

import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.parse.Brackets;

/**
 * Vector and matrix arithmetic at Phase 1 depth ({@code docs/language.md} §13.5), and the
 * collection functions {@code nth}, {@code length}, {@code dot} and {@code cross}.
 *
 * <p>It runs on canonical sums, products and powers, whose vector and matrix factors the canonical
 * form keeps in typed order. Literal vectors and matrices add element by element; a product
 * multiplies its scalar factors into the entries and folds its vectors and matrices left to right
 * (vector times vector is the dot product, matrix times vector a vector, vector times matrix the
 * row vector's product); a square matrix to a positive integer power is multiplied out, and to the
 * power 0 (which the canonical form keeps for a non-commuting base) is the identity. Entries may be
 * symbolic. Mismatched dimensions, a scalar added to a vector or a matrix, a power of a vector or
 * of a non-square matrix are evaluation errors. {@code M^-1} and other powers stay for Phase 5.
 */
final class LinearAlgebra {

  /** The most factors in a power of a matrix with an entry that is not a number. */
  static final int MAX_SYMBOLIC_POWER = 16;

  private LinearAlgebra() {}

  /**
   * Carries out the vector and matrix arithmetic of a canonical node.
   *
   * @param e the canonical node
   * @param mc the precision
   * @param c the request to stop, checked at every entry computed and every squaring
   * @param node the raw node, for errors
   * @return the canonical result; {@code e} when it holds no vector or matrix arithmetic
   */
  static Expr reduce(Expr e, MathContext mc, Cancellation c, Expr node) {
    if (Canonical.commutes(e)
        || !(e instanceof Sum || e instanceof Product || e instanceof Power)) {
      return e;
    }
    c.check();
    // The canonical form can build new arithmetic below the top, (M^a)^n as M^(a·n) say, so the
    // operands that hold vectors or matrices are reduced first.
    List<Expr> ops = Operands.of(e);
    List<Expr> out = null;
    for (int k = 0; k < ops.size(); k++) {
      Expr after = reduce(ops.get(k), mc, c, node);
      if (after != ops.get(k)) {
        if (out == null) {
          out = new ArrayList<>(ops);
        }
        out.set(k, after);
      }
    }
    if (out != null) {
      e = Canonical.of(Operands.rebuild(e, out), mc);
      if (Canonical.commutes(e)) {
        return e;
      }
    }
    if (e instanceof Sum s) {
      return sum(s, mc, c, node);
    }
    if (e instanceof Product p) {
      return product(p, mc, c, node);
    }
    return e instanceof Power p ? power(p, mc, c, node) : e;
  }

  // Sums.

  private static Expr sum(Sum s, MathContext mc, Cancellation c, Expr node) {
    List<Expr> scalars = new ArrayList<>();
    List<Expr> others = new ArrayList<>();
    Expr literal = null;
    for (Expr t : s.terms()) {
      if (Canonical.commutes(t)) {
        scalars.add(t);
      } else if (t instanceof Vector || t instanceof Matrix) {
        literal = literal == null ? t : add(literal, t, mc, c, node);
      } else {
        others.add(t);
      }
    }
    if (!scalars.isEmpty()) {
      Expr shaped = literal != null ? literal : others.getFirst();
      throw new EvaluationException(
          "cannot add a scalar and a " + (holdsMatrix(shaped) ? "matrix" : "vector"), node);
    }
    if (literal == null) {
      return s;
    }
    if (others.isEmpty()) {
      return literal;
    }
    List<Expr> terms = new ArrayList<>(others);
    terms.addFirst(literal);
    return Canonical.of(Sum.of(terms), mc);
  }

  private static Expr add(Expr a, Expr b, MathContext mc, Cancellation c, Expr node) {
    if (a instanceof Vector u && b instanceof Vector v) {
      if (u.items().size() != v.items().size()) {
        throw new EvaluationException(
            "cannot add vectors of lengths " + u.items().size() + " and " + v.items().size(), node);
      }
      return new Vector(addAll(u.items(), v.items(), mc, c));
    }
    if (a instanceof Matrix m && b instanceof Matrix n) {
      if (rows(m) != rows(n) || columns(m) != columns(n)) {
        throw new EvaluationException(
            "cannot add a " + shape(m) + " matrix and a " + shape(n) + " matrix", node);
      }
      List<List<Expr>> out = new ArrayList<>();
      for (int i = 0; i < rows(m); i++) {
        out.add(addAll(m.rows().get(i), n.rows().get(i), mc, c));
      }
      return new Matrix(out);
    }
    throw new EvaluationException("cannot add a vector and a matrix", node);
  }

  private static List<Expr> addAll(List<Expr> a, List<Expr> b, MathContext mc, Cancellation c) {
    List<Expr> out = new ArrayList<>(a.size());
    for (int k = 0; k < a.size(); k++) {
      c.check();
      out.add(Canonical.of(Sum.of(a.get(k), b.get(k)), mc));
    }
    return out;
  }

  // Products.

  private static Expr product(Product p, MathContext mc, Cancellation c, Expr node) {
    List<Expr> scalars = new ArrayList<>();
    List<Expr> out = new ArrayList<>();
    Expr acc = null;
    for (Expr f : p.factors()) {
      if (Canonical.commutes(f)) {
        scalars.add(f);
      } else if (f instanceof Vector || f instanceof Matrix) {
        if (acc == null) {
          acc = f;
        } else {
          acc = multiply(acc, f, mc, c, node);
          if (Canonical.commutes(acc)) {
            scalars.add(acc);
            acc = null;
          }
        }
      } else {
        if (acc != null) {
          out.add(acc);
          acc = null;
        }
        out.add(f);
      }
    }
    if (acc != null) {
      out.add(acc);
    }
    Expr scalar = Canonical.of(product(scalars), mc);
    if (out.isEmpty()) {
      return scalar;
    }
    if (out.size() == 1 && (out.getFirst() instanceof Vector || out.getFirst() instanceof Matrix)) {
      return scale(scalar, out.getFirst(), mc, c);
    }
    List<Expr> factors = new ArrayList<>(out);
    factors.addFirst(scalar);
    return Canonical.of(Product.of(factors), mc);
  }

  /** The product of two literals: a scalar (the dot product), a vector or a matrix. */
  private static Expr multiply(Expr a, Expr b, MathContext mc, Cancellation c, Expr node) {
    if (a instanceof Vector u && b instanceof Vector v) {
      return dot(u, v, mc, c, node);
    }
    if (a instanceof Matrix m && b instanceof Matrix n) {
      if (columns(m) != rows(n)) {
        throw new EvaluationException(
            "cannot multiply a " + shape(m) + " matrix by a " + shape(n) + " matrix", node);
      }
      List<List<Expr>> out = new ArrayList<>();
      for (int i = 0; i < rows(m); i++) {
        List<Expr> row = new ArrayList<>();
        for (int j = 0; j < columns(n); j++) {
          c.check();
          List<Expr> terms = new ArrayList<>();
          for (int k = 0; k < columns(m); k++) {
            terms.add(Product.of(m.rows().get(i).get(k), n.rows().get(k).get(j)));
          }
          row.add(Canonical.of(sum(terms), mc));
        }
        out.add(row);
      }
      return new Matrix(out);
    }
    if (a instanceof Matrix m && b instanceof Vector v) {
      if (columns(m) != v.items().size()) {
        throw new EvaluationException(
            "cannot multiply a " + shape(m) + " matrix by a vector of length " + v.items().size(),
            node);
      }
      List<Expr> out = new ArrayList<>();
      for (List<Expr> row : m.rows()) {
        c.check();
        out.add(dot(row, v.items(), mc, c));
      }
      return new Vector(out);
    }
    Vector v = (Vector) a;
    Matrix m = (Matrix) b;
    if (v.items().size() != rows(m)) {
      throw new EvaluationException(
          "cannot multiply a vector of length "
              + v.items().size()
              + " by a "
              + shape(m)
              + " matrix",
          node);
    }
    List<Expr> out = new ArrayList<>();
    for (int j = 0; j < columns(m); j++) {
      List<Expr> column = new ArrayList<>();
      for (List<Expr> row : m.rows()) {
        column.add(row.get(j));
      }
      out.add(dot(v.items(), column, mc, c));
    }
    return new Vector(out);
  }

  private static Expr dot(Vector u, Vector v, MathContext mc, Cancellation c, Expr node) {
    if (u.items().size() != v.items().size()) {
      throw new EvaluationException(
          "the dot product needs two vectors of one length, not "
              + u.items().size()
              + " and "
              + v.items().size(),
          node);
    }
    return dot(u.items(), v.items(), mc, c);
  }

  private static Expr dot(List<Expr> u, List<Expr> v, MathContext mc, Cancellation c) {
    List<Expr> terms = new ArrayList<>();
    for (int k = 0; k < u.size(); k++) {
      terms.add(Product.of(u.get(k), v.get(k)));
    }
    return Canonical.of(sum(terms), mc);
  }

  /**
   * A scalar times every entry of a vector or matrix. The factor commutes but need not be a scalar
   * of §13.2 (a truth value, say), so the result is classified again as brackets are.
   */
  private static Expr scale(Expr scalar, Expr literal, MathContext mc, Cancellation c) {
    if (scalar.equals(Num.ONE)) {
      return literal;
    }
    if (literal instanceof Vector v) {
      return Brackets.of(scaleAll(scalar, v.items(), mc, c));
    }
    List<Expr> rows = new ArrayList<>();
    for (List<Expr> row : ((Matrix) literal).rows()) {
      rows.add(Brackets.of(scaleAll(scalar, row, mc, c)));
    }
    return Brackets.of(rows);
  }

  private static List<Expr> scaleAll(
      Expr scalar, List<Expr> items, MathContext mc, Cancellation c) {
    List<Expr> out = new ArrayList<>(items.size());
    for (Expr item : items) {
      c.check();
      out.add(Canonical.of(Product.of(scalar, item), mc));
    }
    return out;
  }

  // Powers.

  private static Expr power(Power p, MathContext mc, Cancellation c, Expr node) {
    if (p.exponent() instanceof Vector) {
      throw new EvaluationException("a vector cannot be an exponent", node);
    }
    if (p.exponent() instanceof Matrix) {
      throw new EvaluationException("a matrix cannot be an exponent", node);
    }
    if (p.base() instanceof Vector) {
      throw new EvaluationException("a vector has no powers", node);
    }
    if (!(p.base() instanceof Matrix m)) {
      return p;
    }
    if (rows(m) != columns(m)) {
      throw new EvaluationException("a power of a non-square " + shape(m) + " matrix", node);
    }
    if (p.exponent().equals(Num.ZERO)) {
      return identity(rows(m));
    }
    if (!(p.exponent() instanceof Num n
        && n.value() instanceof Int k
        && k.signum() > 0
        && k.value().bitLength() < 32
        && withinReach(m, k.value().intValue()))) {
      return p;
    }
    int e = k.value().intValue();
    Expr result = null;
    Expr square = m;
    while (e > 0) {
      c.check();
      if ((e & 1) == 1) {
        result = result == null ? square : multiply(result, square, mc, c, node);
      }
      e >>= 1;
      if (e > 0) {
        square = multiply(square, square, mc, c, node);
      }
    }
    return result;
  }

  /**
   * Whether {@code m^n} is multiplied out: for numeric entries while the entries' bits stay under
   * the canonical form's fold limit, for symbolic ones up to {@link #MAX_SYMBOLIC_POWER}, since the
   * canonical form does not expand and the entries grow with every product.
   */
  private static boolean withinReach(Matrix m, int n) {
    long bits = 0;
    for (List<Expr> row : m.rows()) {
      for (Expr entry : row) {
        if (!(entry instanceof Num num)) {
          return n <= MAX_SYMBOLIC_POWER;
        }
        bits = Math.max(bits, bitLength(num.value()));
      }
    }
    long perFactor = bits + 64 - Long.numberOfLeadingZeros(rows(m));
    return perFactor * n <= Canonical.MAX_FOLD_BITS;
  }

  /** The {@code n×n} identity matrix: {@code M^0} for a square {@code M}. */
  private static Matrix identity(int n) {
    List<List<Expr>> rows = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      List<Expr> row = new ArrayList<>(n);
      for (int j = 0; j < n; j++) {
        row.add(i == j ? Num.ONE : Num.ZERO);
      }
      rows.add(row);
    }
    return new Matrix(rows);
  }

  private static long bitLength(Numeric x) {
    return Math.max(bitLength(x.re()), bitLength(x.im()));
  }

  private static long bitLength(RealNumeric x) {
    return switch (x) {
      case Int i -> i.value().bitLength();
      case Rational q -> q.num().bitLength() + q.den().bitLength();
      case Real _ -> 0;
    };
  }

  // The collection functions.

  /**
   * {@code nth}, {@code length}, {@code dot} and {@code cross} on evaluated arguments.
   *
   * @param head the function's name
   * @param args the arguments, canonical
   * @param mc the precision
   * @param node the raw call, for errors
   * @return the value, or the call when an argument is symbolic
   */
  static Expr collection(Symbol head, List<Expr> args, MathContext mc, Expr node) {
    Expr a = args.get(0);
    return switch (head.name()) {
      case "length" -> {
        List<Expr> items = items(a);
        if (items != null) {
          yield Num.of(items.size());
        }
        if (a instanceof Num || a instanceof Bool) {
          throw new EvaluationException("length needs a list, a vector or a set", node);
        }
        yield new Apply(head, args);
      }
      case "nth" -> nth(head, args, node);
      case "dot" -> {
        Expr b = args.get(1);
        if (a instanceof Vector u && b instanceof Vector v) {
          yield dot(u, v, mc, Cancellation.NEVER, node);
        }
        if (isCollection(a) || isCollection(b) || a instanceof Num || b instanceof Num) {
          throw new EvaluationException("dot needs two vectors of one length", node);
        }
        yield new Apply(head, args);
      }
      case "cross" -> {
        Expr b = args.get(1);
        if (Brackets.isScalar(a) && Brackets.isScalar(b)) {
          yield Canonical.of(Product.of(a, b), mc);
        }
        if (a instanceof Vector u
            && b instanceof Vector v
            && u.items().size() == 3
            && v.items().size() == 3) {
          yield cross(u.items(), v.items(), mc, Cancellation.NEVER);
        }
        throw new EvaluationException("× needs two 3-vectors", node);
      }
      default -> throw new IllegalArgumentException("not a collection function: " + head.name());
    };
  }

  private static Expr nth(Symbol head, List<Expr> args, Expr node) {
    Expr c = args.get(0);
    Expr k = args.get(1);
    List<Expr> items = c instanceof SetExpr ? null : items(c);
    if (items == null) {
      if (c instanceof Num || c instanceof Bool || c instanceof SetExpr) {
        throw new EvaluationException("nth needs a list, a vector or a matrix", node);
      }
      return new Apply(head, args);
    }
    if (!(k instanceof Num n)) {
      return new Apply(head, args);
    }
    if (!(n.value() instanceof Int i)) {
      throw new EvaluationException("nth needs a whole-number index", node);
    }
    BigInteger index = i.value();
    if (index.signum() <= 0 || index.compareTo(BigInteger.valueOf(items.size())) > 0) {
      throw new EvaluationException(
          "nth: index " + index + " is out of range 1 to " + items.size(), node);
    }
    return items.get(index.intValue() - 1);
  }

  private static Expr cross(List<Expr> u, List<Expr> v, MathContext mc, Cancellation c) {
    List<Expr> out = new ArrayList<>(3);
    for (int k = 0; k < 3; k++) {
      int a = (k + 1) % 3;
      int b = (k + 2) % 3;
      out.add(
          Canonical.of(
              Sum.of(Product.of(u.get(a), v.get(b)), Product.of(Num.MINUS_ONE, u.get(b), v.get(a))),
              mc));
    }
    return new Vector(out);
  }

  // Helpers.

  /**
   * The items of a list, vector or set, the rows of a matrix as vectors; null for anything else.
   */
  private static List<Expr> items(Expr e) {
    if (e instanceof ListExpr l) {
      return l.items();
    }
    if (e instanceof Vector v) {
      return v.items();
    }
    if (e instanceof SetExpr s) {
      return s.items();
    }
    if (e instanceof Matrix m) {
      return m.rows().stream().map(Brackets::of).toList();
    }
    return null;
  }

  private static boolean isCollection(Expr e) {
    return e instanceof Vector
        || e instanceof Matrix
        || e instanceof ListExpr
        || e instanceof SetExpr;
  }

  private static boolean holdsMatrix(Expr e) {
    if (e instanceof Matrix) {
      return true;
    }
    if (e instanceof Vector) {
      return false;
    }
    for (Expr op : Operands.of(e)) {
      if (holdsMatrix(op)) {
        return true;
      }
    }
    return false;
  }

  private static Expr sum(List<Expr> terms) {
    return terms.size() == 1 ? terms.getFirst() : Sum.of(terms);
  }

  private static Expr product(List<Expr> factors) {
    return switch (factors.size()) {
      case 0 -> Num.ONE;
      case 1 -> factors.getFirst();
      default -> Product.of(factors);
    };
  }

  private static int rows(Matrix m) {
    return m.rows().size();
  }

  private static int columns(Matrix m) {
    return m.rows().getFirst().size();
  }

  private static String shape(Matrix m) {
    return rows(m) + "×" + columns(m);
  }
}
