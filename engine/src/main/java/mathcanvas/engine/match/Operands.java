package mathcanvas.engine.match;

import java.util.ArrayList;
import java.util.List;
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

/**
 * The operands of a node, in one fixed index scheme shared by the matcher, substitution, the
 * rewriter's traversal and trace paths.
 *
 * <ul>
 *   <li>Numbers, constants, truth values and symbols have none.
 *   <li>A sum's terms, a product's factors, a power's base then exponent, an application's
 *       arguments (the head is not an operand), a relation's left then right side, the items of a
 *       list, vector or set, the entries of a matrix row by row, an interval's lower then upper
 *       end.
 *   <li>A piecewise expression: each piece's value then condition, then {@code otherwise}.
 *   <li>A lambda: its parameters, then its body.
 *   <li>A derivative: the expression, the variable. An integral: the integrand, the variable, then
 *       the bounds when definite. A limit: the expression, the variable, the point. A Σ or Π: the
 *       body, the index, the first and last values.
 * </ul>
 *
 * <p>The <b>bound positions</b> are a lambda's parameters and the variable (index 1) of a
 * derivative, integral, limit, Σ or Π: the rewriter does not visit them, and they hold symbols.
 */
public final class Operands {

  private Operands() {}

  /**
   * Returns the operands of {@code e}.
   *
   * @param e the expression
   * @return its operands, possibly none
   */
  public static List<Expr> of(Expr e) {
    return switch (e) {
      case Num _,
          mathcanvas.engine.expr.Based _,
          Constant _,
          Bool _,
          Symbol _,
          mathcanvas.engine.expr.Unit _ ->
          List.of();
      case Sum s -> s.terms();
      case Product p -> p.factors();
      case Power p -> List.of(p.base(), p.exponent());
      case Apply a -> a.args();
      case Lambda l -> {
        List<Expr> out = new ArrayList<>(l.params());
        out.add(l.body());
        yield List.copyOf(out);
      }
      case Relation r -> List.of(r.left(), r.right());
      case ListExpr l -> l.items();
      case Vector v -> v.items();
      case SetExpr s -> s.items();
      case Matrix m -> m.rows().stream().flatMap(List::stream).toList();
      case Interval i -> List.of(i.lower(), i.upper());
      case Piecewise p -> {
        List<Expr> out = new ArrayList<>();
        for (Piecewise.Piece piece : p.pieces()) {
          out.add(piece.value());
          out.add(piece.condition());
        }
        out.add(p.otherwise());
        yield List.copyOf(out);
      }
      case Derivative d -> List.of(d.expr(), d.var());
      case Integral i ->
          i.isDefinite()
              ? List.of(i.integrand(), i.var(), i.lower(), i.upper())
              : List.of(i.integrand(), i.var());
      case Limit l -> List.of(l.expr(), l.var(), l.point());
      case BigSum s -> List.of(s.body(), s.var(), s.from(), s.to());
      case BigProduct p -> List.of(p.body(), p.var(), p.from(), p.to());
    };
  }

  /**
   * Whether operand {@code index} of {@code e} is a bound position.
   *
   * @param e the expression
   * @param index the operand's index
   * @return whether it binds a variable
   */
  public static boolean isBound(Expr e, int index) {
    return switch (e) {
      case Lambda l -> index < l.params().size();
      case Derivative _, Integral _, Limit _, BigSum _, BigProduct _ -> index == 1;
      case Num _,
          mathcanvas.engine.expr.Based _,
          Constant _,
          mathcanvas.engine.expr.Unit _,
          Bool _,
          Symbol _,
          Sum _,
          Product _,
          Power _,
          Apply _,
          Relation _,
          ListExpr _,
          Vector _,
          SetExpr _,
          Matrix _,
          Interval _,
          Piecewise _ ->
          false;
    };
  }

  /**
   * Whether {@code a} and {@code b} are the same kind of node with the same data beside their
   * operands (a relation's operator, an interval's closedness, a derivative's order, a limit's
   * direction, a matrix's shape, an application's head, a lambda's arity) and the same number of
   * operands. Two leaves have the same shape when they are equal.
   *
   * @param a an expression
   * @param b another
   * @return whether {@link #rebuild} of one with the operands of the other is well formed
   */
  public static boolean sameShape(Expr a, Expr b) {
    if (a.getClass() != b.getClass()) {
      return false;
    }
    boolean header =
        switch (a) {
          case Num _,
              mathcanvas.engine.expr.Based _,
              Constant _,
              Bool _,
              Symbol _,
              mathcanvas.engine.expr.Unit _ ->
              a.equals(b);
          case Apply x -> x.head().equals(((Apply) b).head());
          case Lambda x -> x.params().size() == ((Lambda) b).params().size();
          case Relation x -> x.op() == ((Relation) b).op();
          case Matrix x -> x.rows().size() == ((Matrix) b).rows().size();
          case Interval x ->
              x.lowerClosed() == ((Interval) b).lowerClosed()
                  && x.upperClosed() == ((Interval) b).upperClosed();
          case Derivative x -> x.order() == ((Derivative) b).order();
          case Limit x -> x.direction() == ((Limit) b).direction();
          case Sum _,
              Product _,
              Power _,
              ListExpr _,
              Vector _,
              SetExpr _,
              Piecewise _,
              Integral _,
              BigSum _,
              BigProduct _ ->
              true;
        };
    return header && of(a).size() == of(b).size();
  }

  /**
   * Returns {@code e} with its operands replaced, raw: nothing is canonicalised.
   *
   * @param e the expression whose kind and data are kept
   * @param operands the new operands, as many as {@code e} has
   * @return the rebuilt node
   * @throws IllegalArgumentException if the count differs, or a bound position gets a non-symbol
   */
  public static Expr rebuild(Expr e, List<Expr> operands) {
    if (operands.size() != of(e).size()) {
      throw new IllegalArgumentException(
          "expected " + of(e).size() + " operands, got " + operands.size());
    }
    List<Expr> o = operands;
    return switch (e) {
      case Num _,
          mathcanvas.engine.expr.Based _,
          Constant _,
          Bool _,
          Symbol _,
          mathcanvas.engine.expr.Unit _ ->
          e;
      case Sum _ -> new Sum(o);
      case Product _ -> new Product(o);
      case Power _ -> new Power(o.get(0), o.get(1));
      case Apply a -> new Apply(a.head(), o);
      case Lambda l -> {
        List<Symbol> params = new ArrayList<>();
        for (int i = 0; i < l.params().size(); i++) {
          params.add(symbol(o.get(i)));
        }
        yield new Lambda(params, o.getLast());
      }
      case Relation r -> new Relation(r.op(), o.get(0), o.get(1));
      case ListExpr _ -> new ListExpr(o);
      case Vector _ -> new Vector(o);
      case SetExpr _ -> new SetExpr(o);
      case Matrix m -> {
        int columns = m.rows().getFirst().size();
        List<List<Expr>> rows = new ArrayList<>();
        for (int i = 0; i < o.size(); i += columns) {
          rows.add(o.subList(i, i + columns));
        }
        yield Matrix.of(rows);
      }
      case Interval i -> new Interval(o.get(0), o.get(1), i.lowerClosed(), i.upperClosed());
      case Piecewise _ -> {
        List<Piecewise.Piece> pieces = new ArrayList<>();
        for (int i = 0; i + 1 < o.size(); i += 2) {
          pieces.add(new Piecewise.Piece(o.get(i), o.get(i + 1)));
        }
        yield new Piecewise(pieces, o.getLast());
      }
      case Derivative d -> new Derivative(o.get(0), symbol(o.get(1)), d.order());
      case Integral i ->
          i.isDefinite()
              ? Integral.definite(o.get(0), symbol(o.get(1)), o.get(2), o.get(3))
              : Integral.indefinite(o.get(0), symbol(o.get(1)));
      case Limit l -> new Limit(o.get(0), symbol(o.get(1)), o.get(2), l.direction());
      case BigSum _ -> new BigSum(o.get(0), symbol(o.get(1)), o.get(2), o.get(3));
      case BigProduct _ -> new BigProduct(o.get(0), symbol(o.get(1)), o.get(2), o.get(3));
    };
  }

  private static Symbol symbol(Expr e) {
    if (e instanceof Symbol s) {
      return s;
    }
    throw new IllegalArgumentException("a bound position needs a symbol, not " + e);
  }
}
