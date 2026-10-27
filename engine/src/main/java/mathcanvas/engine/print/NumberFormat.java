package mathcanvas.engine.print;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.EvaluationException;
import mathcanvas.engine.eval.NumericForm;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;

/**
 * What a {@link Format} does to numbers, for every printer: {@link #prepare} turns a canonical
 * value into the value a display prints under the decimal formats, and {@link #leaf} says how one
 * number prints.
 */
final class NumberFormat {

  private static final Num HALF = Num.of(Rational.of(1, 2));
  private static final Num MINUS_HALF = Num.of(Rational.of(-1, 2));
  private static final Num I = Num.of(Complex.I);

  private NumberFormat() {}

  /** How one number prints. */
  sealed interface Leaf permits Unchanged, Literal, Mixed, Shape {}

  /** As under {@link Format#EXACT}. */
  record Unchanged() implements Leaf {}

  /**
   * A decimal or scientific literal of the number's magnitude, after a sign when negative.
   *
   * @param text the literal of the magnitude
   * @param negative whether a minus sign comes first
   */
  record Literal(String text, boolean negative) implements Leaf {}

  /**
   * A mixed number of the magnitude, {@code 3 1/2}, after a sign when negative.
   *
   * @param negative whether a minus sign comes first
   * @param whole the whole part
   * @param num the numerator of the fraction part
   * @param den its denominator
   */
  record Mixed(boolean negative, String whole, String num, String den) implements Leaf {}

  /**
   * A complex number as the sum of its parts, each a number the format then prints.
   *
   * @param shape the raw tree
   */
  record Shape(Expr shape) implements Leaf {}

  /**
   * How a number prints under a format.
   *
   * @param n the number
   * @param f the format
   * @return the leaf
   */
  static Leaf leaf(Numeric n, Format f) {
    if (f.kind() == Format.Kind.EXACT || n.equals(Complex.I)) {
      return new Unchanged();
    }
    if (n instanceof Complex c) {
      boolean shaped = f.decimal() || improper(c.re()) || improper(c.im());
      return shaped ? new Shape(parts(c)) : new Unchanged();
    }
    RealNumeric r = (RealNumeric) n;
    if (f.kind() == Format.Kind.FRACTION) {
      if (!improper(r)) {
        return new Unchanged();
      }
      Rational q = (Rational) r;
      BigInteger num = q.num().abs();
      BigInteger den = q.den();
      BigInteger[] split = num.divideAndRemainder(den);
      return new Mixed(r.signum() < 0, split[0].toString(), split[1].toString(), den.toString());
    }
    MathContext mc = new MathContext(f.digits(), RoundingMode.HALF_EVEN);
    if (r instanceof Int k) {
      BigInteger magnitude = k.value().abs();
      if (f.kind() == Format.Kind.DECIMAL || magnitude.toString().length() <= f.digits()) {
        return new Unchanged();
      }
      return new Literal(scientific(new BigDecimal(magnitude), mc), k.signum() < 0);
    }
    BigDecimal value = r.toReal(mc).value().abs();
    BigDecimal rounded = value.round(mc).stripTrailingZeros();
    String text =
        f.kind() == Format.Kind.DECIMAL ? Real.of(rounded).toString() : scientific(rounded, mc);
    return new Literal(text, r.signum() < 0);
  }

  /** Whether a number is a rational whose magnitude exceeds 1: a mixed number's. */
  private static boolean improper(RealNumeric r) {
    return r instanceof Rational q && q.num().abs().compareTo(q.den()) > 0;
  }

  /** A non-negative decimal in scientific notation: {@code 1.235e+4}, {@code 1e+21}. */
  private static String scientific(BigDecimal value, MathContext mc) {
    BigDecimal v = value.round(mc).stripTrailingZeros();
    if (v.signum() == 0) {
      return "0e+0";
    }
    String digits = v.unscaledValue().toString();
    int exponent = v.precision() - v.scale() - 1;
    return digits.charAt(0)
        + (digits.length() > 1 ? "." + digits.substring(1) : "")
        + "e"
        + (exponent < 0 ? "-" : "+")
        + Math.abs(exponent);
  }

  /** A complex number as {@code re + im·i}, its parts left as numbers. */
  private static Expr parts(Complex c) {
    RealNumeric im = c.im();
    RealNumeric magnitude = im.abs();
    Expr imaginary =
        magnitude.isOne() && magnitude.isExact() ? I : Product.of(Num.of(magnitude), I);
    if (im.signum() < 0) {
      imaginary = Product.of(Num.MINUS_ONE, imaginary);
    }
    return Display.dropsRealPart(c) ? imaginary : Sum.of(Num.of(c.re()), imaginary);
  }

  /**
   * The value a display prints under a format. Under the decimal formats each rational becomes a
   * decimal of the format's digits, so that a coefficient is written as one ({@code 3.5x}, not
   * {@code 7x/2}); a subexpression made only of numbers ({@code 3√2/4}) becomes the decimal of its
   * numeric form; integer exponents and the {@code ±1/2} of a root keep their notation. Other
   * formats leave the value as it is.
   *
   * @param canonical the value
   * @param f the format
   * @param c the request to stop
   * @return the tree to display
   */
  static Expr prepare(Expr canonical, Format f, Cancellation c) {
    return f.decimal() ? decimals(canonical, f.digits(), c) : canonical;
  }

  private static Expr decimals(Expr e, int digits, Cancellation c) {
    c.check();
    if (e instanceof Num n) {
      return n.value() instanceof Rational q
          ? Num.of(Real.of(q.toReal(new MathContext(digits, RoundingMode.HALF_EVEN)).value()))
          : n;
    }
    if (e instanceof Power p && notation(p.exponent()) && !numbersOnly(p)) {
      return Power.of(decimals(p.base(), digits, c), p.exponent());
    }
    if (numbersOnly(e)) {
      try {
        return NumericForm.numeric(e, digits, c);
      } catch (EvaluationException x) {
        // Out of range at this precision: the parts are written one by one.
      }
    }
    List<Expr> operands = Operands.of(e);
    if (operands.isEmpty()) {
      return e;
    }
    List<Expr> out = new ArrayList<>(operands.size());
    for (Expr operand : operands) {
      out.add(decimals(operand, digits, c));
    }
    return Operands.rebuild(e, out);
  }

  /** An exponent that is notation rather than a printed number: an integer, or {@code ±1/2}. */
  private static boolean notation(Expr exponent) {
    return exponent instanceof Num n
        && (n.value() instanceof Int || exponent.equals(HALF) || exponent.equals(MINUS_HALF));
  }

  /** Whether a tree is arithmetic over numbers only. */
  private static boolean numbersOnly(Expr e) {
    return e instanceof Num
        || (e instanceof Sum s && s.terms().stream().allMatch(NumberFormat::numbersOnly))
        || (e instanceof Product p && p.factors().stream().allMatch(NumberFormat::numbersOnly))
        || (e instanceof Power p && numbersOnly(p.base()) && numbersOnly(p.exponent()));
  }
}
