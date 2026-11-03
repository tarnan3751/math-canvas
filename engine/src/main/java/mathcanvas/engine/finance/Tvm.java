package mathcanvas.engine.finance;

import java.util.List;
import java.util.Set;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.RealNumeric;

/**
 * The time value of money in closed form ({@code docs/language.md} §14.28), in Excel's conventions:
 * money paid out is negative, {@code type} 0 pays at the end of each period and 1 at the beginning.
 * With {@code g = (1 + r)ⁿ} and {@code A = (1 + r·type)(g − 1)/r}, the equation is {@code pv·g +
 * pmt·A + fv = 0}, and at {@code r = 0} it is {@code pv + pmt·n + fv = 0}. Each function returns an
 * expression for the evaluator to evaluate: exact numbers give an exact value (a rational, or a
 * radical or a logarithm where the formula needs one), a decimal a decimal. A rate of −1 or less is
 * {@code undefined}.
 */
public final class Tvm {

  /** The closed-form functions. */
  public static final Set<String> NAMES = Set.of("pv", "fv", "pmt", "nper", "effective", "nominal");

  private Tvm() {}

  /** Why a call is refused: its message is the error's. */
  public static final class FinanceException extends RuntimeException {

    @java.io.Serial private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message the error's message
     */
    public FinanceException(String message) {
      super(message);
    }
  }

  /**
   * The expression a closed-form function stands for, to be evaluated.
   *
   * @param name the function
   * @param args the evaluated arguments
   * @return the expression, or null when an argument is not a real number (the call stays)
   * @throws FinanceException if {@code type} is not 0 or 1, or {@code m} not a positive integer
   */
  public static Expr apply(String name, List<Expr> args) {
    for (Expr a : args) {
      if (!(a instanceof Num n && n.value() instanceof RealNumeric)) {
        return null;
      }
    }
    if (name.equals("effective") || name.equals("nominal")) {
      return compounding(name, args.get(0), args.get(1));
    }
    Expr r = args.get(0);
    if (real(r).compareValue(Int.MINUS_ONE) <= 0) {
      return Constant.UNDEFINED;
    }
    Expr type = args.size() > 4 ? args.get(4) : Num.ZERO;
    RealNumeric t = real(type);
    if (!(t.isZero() || t.isOne())) {
      throw new FinanceException(name + "'s type must be 0 (end) or 1 (beginning)");
    }
    Expr fourth = args.size() > 3 ? args.get(3) : Num.ZERO;
    return switch (name) {
      case "fv" -> fv(r, args.get(1), args.get(2), fourth, type);
      case "pv" -> pv(r, args.get(1), args.get(2), fourth, type);
      case "pmt" -> pmt(r, args.get(1), args.get(2), fourth, type);
      case "nper" -> nper(r, args.get(1), args.get(2), fourth, type);
      default -> throw new IllegalArgumentException("not a finance function: " + name);
    };
  }

  /**
   * The real number of a number node.
   *
   * @param e a number node holding a real number
   * @return the number
   */
  public static RealNumeric real(Expr e) {
    return (RealNumeric) ((Num) e).value();
  }

  static boolean zero(Expr r) {
    return real(r).isZero();
  }

  private static Expr plus(Expr... terms) {
    return Sum.of(List.of(terms));
  }

  private static Expr times(Expr... factors) {
    return Product.of(List.of(factors));
  }

  private static Expr neg(Expr e) {
    return times(Num.MINUS_ONE, e);
  }

  private static Expr over(Expr a, Expr b) {
    return times(a, Power.of(b, Num.MINUS_ONE));
  }

  /** {@code (1 + r)ⁿ}. */
  static Expr growth(Expr r, Expr n) {
    return Power.of(plus(Num.ONE, r), n);
  }

  /** {@code (1 + r·type)(g − 1)/r}, the annuity factor. */
  static Expr annuity(Expr r, Expr n, Expr type) {
    return over(times(plus(Num.ONE, times(r, type)), plus(growth(r, n), Num.MINUS_ONE)), r);
  }

  static Expr fv(Expr r, Expr n, Expr pmt, Expr pv, Expr type) {
    if (zero(r)) {
      return neg(plus(pv, times(pmt, n)));
    }
    return neg(plus(times(pv, growth(r, n)), times(pmt, annuity(r, n, type))));
  }

  static Expr pv(Expr r, Expr n, Expr pmt, Expr fv, Expr type) {
    if (zero(r)) {
      return neg(plus(fv, times(pmt, n)));
    }
    return neg(over(plus(fv, times(pmt, annuity(r, n, type))), growth(r, n)));
  }

  static Expr pmt(Expr r, Expr n, Expr pv, Expr fv, Expr type) {
    if (zero(r)) {
      return neg(over(plus(pv, fv), n));
    }
    return neg(over(plus(times(pv, growth(r, n)), fv), annuity(r, n, type)));
  }

  static Expr nper(Expr r, Expr pmt, Expr pv, Expr fv, Expr type) {
    if (zero(r)) {
      return neg(over(plus(pv, fv), pmt));
    }
    Expr paid = times(pmt, plus(Num.ONE, times(r, type)));
    Expr ratio = Canonical.of(over(plus(paid, neg(times(fv, r))), plus(paid, times(pv, r))));
    if (!(ratio instanceof Num q && q.value() instanceof RealNumeric v && v.signum() > 0)) {
      // a logarithm of a number that is not positive: no number of periods
      return Constant.UNDEFINED;
    }
    return new Apply(Symbol.of("log"), List.of(ratio, plus(Num.ONE, r)));
  }

  private static Expr compounding(String name, Expr rate, Expr m) {
    if (!(real(m) instanceof Int k) || k.signum() <= 0) {
      throw new FinanceException(name + "'s m must be a positive integer");
    }
    if (name.equals("effective")) {
      return plus(Power.of(plus(Num.ONE, over(rate, m)), m), Num.MINUS_ONE);
    }
    return times(m, plus(Power.of(plus(Num.ONE, rate), Power.of(m, Num.MINUS_ONE)), Num.MINUS_ONE));
  }
}
