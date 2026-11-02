package mathcanvas.engine.linalg;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;

/**
 * Vector norms: the Euclidean {@code √(Σ |vᵢ|²)}, the 1-norm {@code Σ |vᵢ|} and the maximum norm
 * {@code max |vᵢ|}, and {@code normalize(v) = v / ‖v‖}. The modulus of a real number is taken here;
 * any other entry's is {@code abs}, which the simplifier settles for real symbols.
 */
final class Norms {

  private static final Num HALF = Num.of(Rational.of(1, 2));

  private Norms() {}

  /** The message for an order other than 1, 2 and ∞. */
  static final String ORDER = "norm's order must be 1, 2 or inf";

  static Expr norm(Vector v, Expr order, Entries en) {
    if (order.equals(Num.of(2))) {
      List<Expr> squares = new ArrayList<>();
      for (Expr e : v.items()) {
        squares.add(square(e, en));
      }
      return en.canon(new Power(sum(squares), HALF));
    }
    if (order.equals(Num.ONE)) {
      List<Expr> terms = new ArrayList<>();
      for (Expr e : v.items()) {
        terms.add(modulus(e, en));
      }
      return en.canon(sum(terms));
    }
    if (order.equals(Constant.INFINITY)) {
      List<Expr> moduli = new ArrayList<>();
      RealNumeric largest = null;
      boolean numbers = true;
      for (Expr e : v.items()) {
        Expr m = modulus(e, en);
        moduli.add(m);
        if (m instanceof Num n && n.value() instanceof RealNumeric r) {
          largest = largest == null || r.compareTo(largest) > 0 ? r : largest;
        } else {
          numbers = false;
        }
      }
      return numbers
          ? Num.of(largest)
          : moduli.size() == 1 ? moduli.getFirst() : en.canon(new Apply(Symbol.of("max"), moduli));
    }
    throw new IllegalArgumentException(ORDER);
  }

  static Expr normalize(Vector v, Entries en) {
    if (v.items().stream().allMatch(e -> en.isZero(e) == Tri.TRUE)) {
      throw new IllegalArgumentException("normalize needs a vector that is not zero");
    }
    Expr norm = norm(v, Num.of(2), en);
    List<Expr> out = new ArrayList<>();
    for (Expr e : v.items()) {
      out.add(en.canon(Product.of(e, new Power(norm, Num.MINUS_ONE))));
    }
    return Vector.of(out);
  }

  /** {@code |e|²}: {@code e²} for a real number, {@code re² + im²} for a complex one. */
  private static Expr square(Expr e, Entries en) {
    if (e instanceof Num n) {
      return en.add(
          en.mul(Num.of(n.value().re()), Num.of(n.value().re())),
          en.mul(Num.of(n.value().im()), Num.of(n.value().im())));
    }
    return en.canon(new Power(new Apply(Symbol.of("abs"), List.of(e)), Num.of(2)));
  }

  private static Expr modulus(Expr e, Entries en) {
    if (e instanceof Num n && n.value() instanceof RealNumeric r) {
      return Num.of(r.abs());
    }
    if (e instanceof Num) {
      return en.canon(new Power(square(e, en), HALF));
    }
    return new Apply(Symbol.of("abs"), List.of(e));
  }

  private static Expr sum(List<Expr> terms) {
    return terms.size() == 1 ? terms.getFirst() : Sum.of(terms);
  }
}
