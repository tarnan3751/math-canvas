package mathcanvas.engine.print;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.number.Elementary;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;

/**
 * The display order of a set of constants: {@code -∞} first, then the real values ascending, then
 * {@code ∞}, then the non-real values by real part and then imaginary part. The values are taken in
 * inexact arithmetic at 30 digits through the elementary functions, every number made a {@code
 * Real} first, so a value costs a few operations per node. Each member's value is taken once and
 * the members sorted on those keys; only the members tied at 30 digits are valued again, once each,
 * at 60, and equal there keep their canonical order. A set with a member that has no such value
 * keeps its canonical order: a member that is not arithmetic over numbers, {@code π}, {@code e} and
 * the elementary functions, one with a power whose exponent exceeds 10⁶ in magnitude ({@code
 * 3^1000000000}), or one whose value leaves {@code BigDecimal}'s range.
 */
final class ConstantOrder {

  private static final MathContext COARSE = new MathContext(30, RoundingMode.HALF_EVEN);
  private static final MathContext FINE = new MathContext(60, RoundingMode.HALF_EVEN);

  /** The largest exponent magnitude evaluated. */
  private static final BigDecimal MAX_EXPONENT = BigDecimal.TEN.pow(6);

  /** An imaginary part below this fraction of the value is taken as rounding, the value as real. */
  private static final BigDecimal NOISE = new BigDecimal("1e-25");

  private ConstantOrder() {}

  /** A member and where it sorts: its rank (-∞, real, ∞, non-real) and its parts. */
  private record Keyed(Expr item, int rank, BigDecimal re, BigDecimal im) {}

  /** The items in display order, or the list itself when one has no value. */
  static List<Expr> of(List<Expr> items) {
    if (items.size() < 2) {
      return items;
    }
    List<Keyed> keyed = new ArrayList<>(items.size());
    for (Expr item : items) {
      Keyed k = key(item, COARSE);
      if (k == null) {
        return items;
      }
      keyed.add(k);
    }
    // each key computed once; a stable sort on the 30-digit keys, then each run of equal keys
    // sorted again, stably, on 60-digit keys computed once per member of the run
    keyed.sort(ConstantOrder::coarse);
    List<Expr> result = new ArrayList<>(keyed.size());
    int i = 0;
    while (i < keyed.size()) {
      int j = i + 1;
      while (j < keyed.size() && coarse(keyed.get(i), keyed.get(j)) == 0) {
        j++;
      }
      List<Keyed> run = keyed.subList(i, j);
      if (run.size() > 1 && run.getFirst().rank() % 2 == 1) {
        List<Keyed[]> fine = new ArrayList<>(run.size());
        boolean all = true;
        for (Keyed k : run) {
          Keyed f = key(k.item(), FINE);
          all &= f != null;
          fine.add(new Keyed[] {k, f});
        }
        if (all) {
          fine.sort((x, y) -> coarse(x[1], y[1]));
          run = fine.stream().map(x -> x[0]).toList();
        }
      }
      run.forEach(k -> result.add(k.item()));
      i = j;
    }
    return result;
  }

  private static int coarse(Keyed a, Keyed b) {
    int c = Integer.compare(a.rank(), b.rank());
    if (c != 0) {
      return c;
    }
    c = a.re().compareTo(b.re());
    return c != 0 ? c : a.im().compareTo(b.im());
  }

  /** The key of a member at a precision, or null when it has no value. */
  private static Keyed key(Expr item, MathContext mc) {
    if (item instanceof Constant c && c.kind() == Constant.Kind.INFINITY) {
      return new Keyed(item, 2, BigDecimal.ZERO, BigDecimal.ZERO);
    }
    if (item instanceof Product p
        && p.factors().size() == 2
        && p.factors().get(0).equals(Num.MINUS_ONE)
        && p.factors().get(1) instanceof Constant c
        && c.kind() == Constant.Kind.INFINITY) {
      return new Keyed(item, 0, BigDecimal.ZERO, BigDecimal.ZERO);
    }
    Optional<Numeric> v;
    try {
      v = value(item, mc);
    } catch (ArithmeticException | IllegalArgumentException noValue) {
      return null;
    }
    if (v.isEmpty()) {
      return null;
    }
    BigDecimal re = v.get().re().toReal(mc).value();
    BigDecimal im = v.get().im().toReal(mc).value();
    boolean real = im.abs().compareTo(re.abs().max(im.abs()).multiply(NOISE)) <= 0;
    return real ? new Keyed(item, 1, re, BigDecimal.ZERO) : new Keyed(item, 3, re, im);
  }

  private static Optional<Numeric> value(Expr e, MathContext mc) {
    return switch (e) {
      case Num n -> Optional.of(inexact(n.value(), mc));
      case Constant c when c.kind() == Constant.Kind.PI -> Optional.of(Elementary.pi(mc));
      case Constant c when c.kind() == Constant.Kind.E -> Optional.of(Elementary.e(mc));
      case Sum s -> fold(s.terms(), true, mc);
      case Product p -> fold(p.factors(), false, mc);
      case Power w -> power(w, mc);
      case Apply a when a.args().size() == 1 -> {
        Optional<Numeric> x = value(a.args().getFirst(), mc);
        if (x.isEmpty()) {
          yield Optional.empty();
        }
        Numeric v = x.get();
        yield switch (a.head().name()) {
          case "exp" -> Optional.of(Elementary.exp(v, mc));
          case "log" -> Optional.of(Elementary.log(v, mc));
          case "sin" -> Optional.of(Elementary.sin(v, mc));
          case "cos" -> Optional.of(Elementary.cos(v, mc));
          case "tan" -> Optional.of(Elementary.tan(v, mc));
          case "asin" -> Optional.of(Elementary.asin(v, mc));
          case "acos" -> Optional.of(Elementary.acos(v, mc));
          case "atan" -> Optional.of(Elementary.atan(v, mc));
          default -> Optional.empty();
        };
      }
      default -> Optional.empty();
    };
  }

  /** A number made inexact at the precision: a {@code Real}, or a complex number of them. */
  private static Numeric inexact(Numeric n, MathContext mc) {
    return n instanceof RealNumeric r ? r.toReal(mc) : n.toReal(mc);
  }

  /**
   * A power in inexact arithmetic: an integer exponent by squaring, rounding each step, a
   * half-integer one through the square root, any other through {@code exp(y·log x)}; empty past
   * the exponent bound.
   */
  private static Optional<Numeric> power(Power w, MathContext mc) {
    Optional<Numeric> b = value(w.base(), mc);
    Optional<Numeric> x = value(w.exponent(), mc);
    if (b.isEmpty() || x.isEmpty()) {
      return Optional.empty();
    }
    Numeric y = x.get();
    BigDecimal size = y.re().toReal(mc).value().abs().max(y.im().toReal(mc).value().abs());
    if (size.compareTo(MAX_EXPONENT) > 0) {
      return Optional.empty();
    }
    if (y instanceof Real r && r.isInteger()) {
      return Optional.of(integerPower(b.get(), r.value().longValueExact(), mc));
    }
    if (y instanceof Real r
        && r.value().multiply(BigDecimal.TWO).stripTrailingZeros().scale() <= 0) {
      // a half-integer: the square root, which is fast, to an integer power
      long twice = r.value().multiply(BigDecimal.TWO).longValueExact();
      return Optional.of(integerPower(b.get().sqrt(mc), twice, mc));
    }
    return Optional.of(b.get().pow(y, mc));
  }

  /** {@code b^n} by squaring, rounding each step. */
  private static Numeric integerPower(Numeric b, long n, MathContext mc) {
    Numeric result = new Real(BigDecimal.ONE);
    Numeric square = b;
    for (long k = Math.abs(n); k > 0; k >>= 1) {
      if ((k & 1) == 1) {
        result = result.multiply(square, mc);
      }
      if (k > 1) {
        square = square.multiply(square, mc);
      }
    }
    return n < 0 ? new Real(BigDecimal.ONE).divide(result, mc) : result;
  }

  private static Optional<Numeric> fold(List<Expr> operands, boolean sum, MathContext mc) {
    Numeric acc = null;
    for (Expr op : operands) {
      Optional<Numeric> v = value(op, mc);
      if (v.isEmpty()) {
        return Optional.empty();
      }
      acc = acc == null ? v.get() : sum ? acc.add(v.get(), mc) : acc.multiply(v.get(), mc);
    }
    return Optional.ofNullable(acc);
  }
}
