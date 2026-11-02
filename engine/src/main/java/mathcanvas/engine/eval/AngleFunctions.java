package mathcanvas.engine.eval;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Set;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.parse.Brackets;

/**
 * Degrees, minutes and seconds ({@code docs/language.md} §14.18): {@code dms(x)} writes an angle as
 * the list {@code [d, m, s]}, {@code d} and {@code m} integers and {@code s} a number, the sign on
 * the first component that is not zero and the rest non-negative; {@code degrees(d, m, s)} is the
 * inverse. A plain number is degrees in every angle mode; a rational multiple of {@code π} is an
 * angle in radians and converted. An argument of any other kind leaves the call.
 */
final class AngleFunctions {

  /** The names this class evaluates. */
  static final Set<String> NAMES = Set.of("dms", "degrees");

  private static final Int SIXTY = Int.of(60);

  private AngleFunctions() {}

  /**
   * Evaluates {@code dms} or {@code degrees} on evaluated arguments.
   *
   * @param call the call, for an error's position
   * @param args the arguments, evaluated
   * @param mc the precision
   * @return the value, or the call with the arguments when it stays
   * @throws EvaluationException if a component of {@code degrees} is negative after one that is not
   *     zero, or is not real
   */
  static Expr apply(Apply call, List<Expr> args, MathContext mc) {
    Expr stays = Canonical.of(Apply.of(call.head(), args), mc);
    if (call.head().name().equals("dms")) {
      RealNumeric degrees = degrees(args.getFirst(), mc);
      return degrees == null ? stays : dms(degrees, mc);
    }
    RealNumeric total = Int.ZERO;
    int sign = 0;
    RealNumeric[] scale = {Int.ONE, SIXTY, Int.of(3600)};
    for (int k = 0; k < 3; k++) {
      Expr a = args.get(k);
      if (!(a instanceof Num n)) {
        return stays;
      }
      if (!(n.value() instanceof RealNumeric r)) {
        throw new EvaluationException("degrees needs real numbers", call);
      }
      if (sign != 0 && r.signum() < 0) {
        throw new EvaluationException(
            "only the first component of degrees that is not zero carries a sign", call);
      }
      if (sign == 0) {
        sign = r.signum();
      }
      total = (RealNumeric) total.add(r.abs().divide(scale[k], mc), mc);
    }
    return Num.of(sign < 0 ? total.negate() : total);
  }

  /**
   * The value in degrees of an angle: a real number as it is, a rational multiple of {@code π}
   * converted from radians; null for anything else.
   */
  private static RealNumeric degrees(Expr x, MathContext mc) {
    if (x instanceof Num n && n.value() instanceof RealNumeric r) {
      return r;
    }
    Expr q = Canonical.of(Exprs.div(x, Constant.PI), mc);
    if (q instanceof Num n && (n.value() instanceof Int || n.value() instanceof Rational)) {
      return (RealNumeric) n.value().multiply(Int.of(180), mc);
    }
    return null;
  }

  /** {@code [d, m, s]} of a value in degrees. */
  private static Expr dms(RealNumeric value, MathContext mc) {
    RealNumeric a = value.abs();
    Int d = whole(a.floor());
    RealNumeric rest = (RealNumeric) a.subtract(d, mc).multiply(SIXTY, mc);
    Int m = whole(rest.floor());
    RealNumeric s = (RealNumeric) rest.subtract(m, mc).multiply(SIXTY, mc);
    if (s instanceof Real r) {
      s = new Real(r.value().stripTrailingZeros().max(BigDecimal.ZERO));
    }
    RealNumeric[] parts = {d, m, s};
    if (value.signum() < 0) {
      for (int k = 0; k < 3; k++) {
        if (parts[k].signum() != 0) {
          parts[k] = parts[k].negate();
          break;
        }
      }
    }
    return Brackets.of(List.of(Num.of(parts[0]), Num.of(parts[1]), Num.of(parts[2])));
  }

  private static Int whole(RealNumeric r) {
    return r instanceof Int i ? i : Int.of(((Real) r).value().toBigIntegerExact());
  }
}
