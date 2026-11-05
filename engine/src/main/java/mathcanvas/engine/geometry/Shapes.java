package mathcanvas.engine.geometry;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;

/**
 * Shape formulas, coordinate geometry and conics from their definitions ({@code docs/language.md}
 * §14.18). Points are vectors {@code [x, y]}; a line in a directrix's place is a relation linear in
 * {@code x} and {@code y}. The formulas hold for symbolic arguments; with numbers a shape that does
 * not exist is {@code undefined} (a triangle that fails the triangle inequality), and an argument
 * of the wrong kind is an {@link IllegalArgumentException} with the language's message.
 *
 * <p>A conic is the relation {@code P(x, y) = 0} its definition gives once the square roots are
 * squared away: {@code |PF|² = e²·d(P, L)²} for a focus and a directrix, and {@code 4L²·|PF₁|² =
 * (L² + |PF₁|² − |PF₂|²)²} for two foci and the sum (ellipse) or difference (hyperbola) {@code L}
 * of the distances, which holds for both since the sign of the difference squares away. {@code P}
 * is expanded and collected, its rational content cleared and its leading coefficient positive.
 */
public final class Shapes {

  private static final Symbol X = Symbol.of("x");
  private static final Symbol Y = Symbol.of("y");
  private static final Num TWO = Num.of(2);

  private final Context ctx;
  private final Decide decide;

  /**
   * The formulas in a context.
   *
   * @param ctx the context: precision, numerics, assumptions
   */
  public Shapes(Context ctx) {
    this.ctx = ctx;
    this.decide = new Decide(ctx);
  }

  /**
   * Evaluates one of the shape, coordinate-geometry or conic functions.
   *
   * @param name the function's name
   * @param args its arguments, evaluated
   * @return the value, or null when a comparison it needs cannot be decided, so the call stays
   * @throws IllegalArgumentException with the language's message for an argument of the wrong kind
   */
  public Expr apply(String name, List<Expr> args) {
    try {
      return formula(name, args);
    } catch (Decide.Undecided u) {
      return null;
    }
  }

  private Expr formula(String name, List<Expr> args) {
    return switch (name) {
      case "trianglearea" -> triangleArea(args);
      case "circumradius" -> circumradius(args);
      case "inradius" -> inradius(args);
      case "polygonarea" -> polygonArea(args.getFirst());
      case "distance" -> distance(point(name, args.get(0), -1), point(name, args.get(1), -1));
      case "midpoint" -> midpoint(point(name, args.get(0), -1), point(name, args.get(1), -1));
      case "slope" -> slope(point(name, args.get(0), 2), point(name, args.get(1), 2));
      case "lineequation" -> line(point(name, args.get(0), 2), point(name, args.get(1), 2));
      case "circleequation" -> circle(point(name, args.get(0), 2), args.get(1));
      case "parabola" -> conic(name, point(name, args.get(0), 2), args.get(1), Num.ONE);
      case "conic" -> conic(name, point(name, args.get(0), 2), args.get(1), args.get(2));
      case "ellipse", "hyperbola" ->
          focal(name, point(name, args.get(0), 2), point(name, args.get(1), 2), args.get(2));
      default -> throw new IllegalStateException("no shape formula " + name);
    };
  }

  /** The names {@link #apply} evaluates. */
  public static final List<String> NAMES =
      List.of(
          "trianglearea",
          "polygonarea",
          "circumradius",
          "inradius",
          "distance",
          "midpoint",
          "slope",
          "lineequation",
          "circleequation",
          "parabola",
          "ellipse",
          "hyperbola",
          "conic");

  // Triangles from their sides.

  /** Heron's formula, {@code √((a+b+c)(−a+b+c)(a−b+c)(a+b−c))/4}. */
  private Expr triangleArea(List<Expr> s) {
    if (!valid(s)) {
      return Constant.UNDEFINED;
    }
    return simplify(Exprs.div(Exprs.sqrt(heronProduct(s)), Num.of(4)));
  }

  /** {@code abc/(4·area)}. */
  private Expr circumradius(List<Expr> s) {
    if (!valid(s) || degenerate(s)) {
      return Constant.UNDEFINED;
    }
    Expr area = Exprs.div(Exprs.sqrt(heronProduct(s)), Num.of(4));
    return simplify(
        Exprs.div(Product.of(s.get(0), s.get(1), s.get(2)), Product.of(Num.of(4), area)));
  }

  /** {@code area/semiperimeter}. */
  private Expr inradius(List<Expr> s) {
    if (!valid(s)) {
      return Constant.UNDEFINED;
    }
    Expr area = Exprs.div(Exprs.sqrt(heronProduct(s)), Num.of(4));
    return simplify(Exprs.div(area, Exprs.div(Sum.of(s.get(0), s.get(1), s.get(2)), TWO)));
  }

  private static Expr heronProduct(List<Expr> s) {
    Expr a = s.get(0);
    Expr b = s.get(1);
    Expr c = s.get(2);
    return Product.of(
        Sum.of(a, b, c),
        Sum.of(Exprs.neg(a), b, c),
        Sum.of(a, Exprs.neg(b), c),
        Sum.of(a, b, Exprs.neg(c)));
  }

  /**
   * Whether three sides make a triangle, degenerate included: when they are closed, each positive
   * and none longer than the other two together; symbolic sides are taken as they are.
   */
  private boolean valid(List<Expr> s) {
    if (!s.stream().allMatch(Shapes::closed)) {
      return true;
    }
    for (int i = 0; i < 3; i++) {
      if (decide.sign(s.get(i)) <= 0
          || decide.compare(s.get(i), Sum.of(s.get((i + 1) % 3), s.get((i + 2) % 3))) > 0) {
        return false;
      }
    }
    return true;
  }

  private boolean degenerate(List<Expr> s) {
    if (!s.stream().allMatch(Shapes::closed)) {
      return false;
    }
    return decide.sign(heronProduct(s)) == 0;
  }

  /** The shoelace formula, signed: positive when the vertices run counterclockwise. */
  private Expr polygonArea(Expr vertices) {
    List<Expr> rows = new ArrayList<>();
    switch (vertices) {
      case Matrix m when m.rows().getFirst().size() == 2 ->
          m.rows().forEach(r -> rows.add(Vector.of(r)));
      case ListExpr l -> rows.addAll(l.items());
      default -> throw new IllegalArgumentException("`polygonarea` needs a list of points [x, y]");
    }
    if (rows.size() < 3) {
      throw new IllegalArgumentException("`polygonarea` needs at least three points [x, y]");
    }
    List<Expr> terms = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      List<Expr> p = point("polygonarea", rows.get(i), 2);
      List<Expr> q = point("polygonarea", rows.get((i + 1) % rows.size()), 2);
      terms.add(Product.of(p.get(0), q.get(1)));
      terms.add(Exprs.neg(Product.of(q.get(0), p.get(1))));
    }
    return simplify(Exprs.div(Sum.of(terms), TWO));
  }

  // Coordinate geometry.

  private Expr distance(List<Expr> p, List<Expr> q) {
    sameDimension("distance", p, q);
    List<Expr> squares = new ArrayList<>();
    for (int i = 0; i < p.size(); i++) {
      squares.add(Power.of(Exprs.sub(q.get(i), p.get(i)), TWO));
    }
    return simplify(Exprs.sqrt(squares.size() == 1 ? squares.getFirst() : Sum.of(squares)));
  }

  private Expr midpoint(List<Expr> p, List<Expr> q) {
    sameDimension("midpoint", p, q);
    List<Expr> out = new ArrayList<>();
    for (int i = 0; i < p.size(); i++) {
      out.add(simplify(Exprs.div(Sum.of(p.get(i), q.get(i)), TWO)));
    }
    return Canonical.of(Vector.of(out), ctx.precision());
  }

  private Expr slope(List<Expr> p, List<Expr> q) {
    Expr run = simplify(Exprs.sub(q.get(0), p.get(0)));
    if (run.equals(Num.ZERO)) {
      return Constant.UNDEFINED;
    }
    return cancel(Exprs.div(Exprs.sub(q.get(1), p.get(1)), run));
  }

  /** {@code y = m·x + b}, or {@code x = c} for a vertical line. */
  private Expr line(List<Expr> p, List<Expr> q) {
    noXY("lineequation", p, q);
    Expr run = simplify(Exprs.sub(q.get(0), p.get(0)));
    Expr rise = simplify(Exprs.sub(q.get(1), p.get(1)));
    if (run.equals(Num.ZERO)) {
      if (rise.equals(Num.ZERO)) {
        throw new IllegalArgumentException("`lineequation` needs two distinct points");
      }
      return relation(X, p.get(0));
    }
    Expr m = cancel(Exprs.div(rise, run));
    Expr b = cancel(Exprs.sub(p.get(1), Product.of(m, p.get(0))));
    return relation(Y, simplify(Sum.of(Product.of(m, X), b)));
  }

  /** {@code (x − h)² + (y − k)² = r²}. */
  private Expr circle(List<Expr> c, Expr r) {
    noXY("circleequation", c, List.of(r));
    if (closed(r) && decide.sign(r) <= 0) {
      throw new IllegalArgumentException("`circleequation` needs a positive radius");
    }
    Expr left =
        Sum.of(Power.of(Exprs.sub(X, c.get(0)), TWO), Power.of(Exprs.sub(Y, c.get(1)), TWO));
    return relation(left, simplify(Power.of(r, TWO)));
  }

  // Conics.

  /** {@code |PF|²·(α² + β²) − e²·(αx + βy + γ)² = 0} for the directrix {@code αx + βy + γ = 0}. */
  private Expr conic(String name, List<Expr> focus, Expr directrix, Expr e) {
    noXY(name, focus, List.of(e));
    if (!(directrix instanceof Relation r) || r.op() != Relation.Op.EQ) {
      throw new IllegalArgumentException("`" + name + "` needs a directrix, a line such as y = -1");
    }
    Polynomial l;
    try {
      l = Polynomial.of(Exprs.sub(r.left(), r.right()), ctx.cancellation());
    } catch (IllegalArgumentException notPolynomial) {
      throw new IllegalArgumentException("`" + name + "` needs a directrix, a line such as y = -1");
    }
    Expr alpha = l.coefficient(X, 1).toExpr();
    Expr beta = l.coefficient(Y, 1).toExpr();
    if (l.degree(X) > 1
        || l.degree(Y) > 1
        || l.coefficient(X, 1).degree(Y) > 0
        || (simplify(alpha).equals(Num.ZERO) && simplify(beta).equals(Num.ZERO))) {
      throw new IllegalArgumentException("`" + name + "` needs a directrix, a line such as y = -1");
    }
    if (closed(e) && decide.sign(e) <= 0) {
      throw new IllegalArgumentException("`conic` needs a positive eccentricity");
    }
    Expr norm = Sum.of(Power.of(alpha, TWO), Power.of(beta, TWO));
    Expr p =
        Sum.of(
            Product.of(squaredDistance(focus), norm),
            Exprs.neg(Product.of(Power.of(e, TWO), Power.of(Exprs.sub(r.left(), r.right()), TWO))));
    return curve(p);
  }

  /**
   * The ellipse or hyperbola of foci {@code f} and {@code g} and focal sum or difference {@code L}.
   */
  private Expr focal(String name, List<Expr> f, List<Expr> g, Expr length) {
    noXY(name, f, g);
    noXY(name, List.of(length), List.of());
    Expr between = distance(f, g);
    if (closed(length) && closed(between)) {
      boolean ok =
          name.equals("ellipse")
              ? decide.compare(length, between) > 0
              : decide.sign(length) > 0 && decide.compare(length, between) < 0;
      if (!ok) {
        throw new IllegalArgumentException(
            name.equals("ellipse")
                ? "`ellipse` needs 2a greater than the distance between the foci"
                : "`hyperbola` needs 2a between 0 and the distance between the foci");
      }
    }
    Expr d1 = squaredDistance(f);
    Expr d2 = squaredDistance(g);
    Expr l2 = Power.of(length, TWO);
    Expr p =
        Sum.of(
            Product.of(Num.of(4), l2, d1), Exprs.neg(Power.of(Sum.of(l2, d1, Exprs.neg(d2)), TWO)));
    return curve(p);
  }

  private static Expr squaredDistance(List<Expr> f) {
    return Sum.of(Power.of(Exprs.sub(X, f.get(0)), TWO), Power.of(Exprs.sub(Y, f.get(1)), TWO));
  }

  /** {@code p = 0}, {@code p} expanded, its rational content cleared, its leading term positive. */
  private Expr curve(Expr p) {
    Expr expanded =
        simplify(Algebra.expand(Canonical.of(p, ctx.precision()), ctx.withTrace(false)).expr());
    Polynomial poly = Polynomial.of(withoutZeroTerms(expanded), ctx.cancellation());
    if (poly.isZero()) {
      return relation(Num.ZERO, Num.ZERO);
    }
    BigInteger num = BigInteger.ZERO;
    BigInteger den = BigInteger.ONE;
    boolean rational = true;
    for (Polynomial.Term t : poly.terms()) {
      if (t.coefficient() instanceof Int i) {
        num = num.gcd(i.value());
      } else if (t.coefficient() instanceof Rational q) {
        num = num.gcd(q.num());
        den = den.divide(den.gcd(q.den())).multiply(q.den());
      } else {
        rational = false;
      }
    }
    RealNumeric scale = rational ? Rational.of(den, num) : Int.ONE;
    if (poly.leadingCoefficient().signum() < 0) {
      scale = scale.negate();
    }
    Expr cleared = poly.multiply(scale).toExpr();
    return relation(cleared, Num.ZERO);
  }

  /**
   * {@code e} without its terms whose coefficient is an inexact zero, which the canonical form
   * keeps ({@code 0.0x²} from a decimal eccentricity of 1).
   */
  private Expr withoutZeroTerms(Expr e) {
    if (!(e instanceof Sum s)) {
      return e;
    }
    List<Expr> kept = new ArrayList<>();
    for (Expr t : s.terms()) {
      Expr c = t instanceof Product p ? p.factors().getFirst() : t;
      if (!(c instanceof Num n && n.value().isZero())) {
        kept.add(t);
      }
    }
    return kept.isEmpty()
        ? Num.ZERO
        : kept.size() == 1 ? kept.getFirst() : Canonical.of(Sum.of(kept), ctx.precision());
  }

  // Arguments.

  /**
   * The coordinates of a point: a vector or list of scalars, of {@code dimension} of them, or any
   * number when {@code dimension} is negative.
   */
  private static List<Expr> point(String name, Expr e, int dimension) {
    List<Expr> items =
        switch (e) {
          case Vector v -> v.items();
          case ListExpr l -> l.items();
          default -> null;
        };
    if (items == null
        || items.isEmpty()
        || (dimension > 0 && items.size() != dimension)
        || items.stream()
            .anyMatch(i -> i instanceof Vector || i instanceof ListExpr || i instanceof Matrix)) {
      throw new IllegalArgumentException(
          name + (dimension == 2 ? " needs points [x, y]" : " needs points"));
    }
    return items;
  }

  private static void sameDimension(String name, List<Expr> p, List<Expr> q) {
    if (p.size() != q.size()) {
      throw new IllegalArgumentException("`" + name + "` needs two points of one dimension");
    }
  }

  /** The equation's variables {@code x} and {@code y} must not be in the arguments. */
  private static void noXY(String name, List<Expr> p, List<Expr> q) {
    for (List<Expr> list : List.of(p, q)) {
      for (Expr e : list) {
        if (holds(e, X) || holds(e, Y)) {
          throw new IllegalArgumentException("`" + name + "`'s arguments must not hold x or y");
        }
      }
    }
  }

  private static boolean holds(Expr e, Symbol s) {
    Deque<Expr> todo = new ArrayDeque<>();
    todo.push(e);
    while (!todo.isEmpty()) {
      Expr n = todo.pop();
      if (n.equals(s)) {
        return true;
      }
      for (Expr op : Operands.of(n)) {
        todo.push(op);
      }
    }
    return false;
  }

  /** Whether {@code e} holds no symbol. */
  private static boolean closed(Expr e) {
    Deque<Expr> todo = new ArrayDeque<>();
    todo.push(e);
    while (!todo.isEmpty()) {
      Expr n = todo.pop();
      if (n instanceof Symbol) {
        return false;
      }
      for (Expr op : Operands.of(n)) {
        todo.push(op);
      }
    }
    return true;
  }

  private Expr relation(Expr left, Expr right) {
    return Canonical.of(new Relation(Relation.Op.EQ, left, right), ctx.precision());
  }

  private Expr simplify(Expr e) {
    return decide.simplify(e);
  }

  private Expr cancel(Expr e) {
    return simplify(Algebra.cancel(Canonical.of(e, ctx.precision()), ctx.withTrace(false)));
  }
}
