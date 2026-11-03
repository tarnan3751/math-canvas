package mathcanvas.engine.bases;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import mathcanvas.engine.expr.Based;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Int;

/**
 * Integers in other bases ({@code docs/language.md} §14.27): {@code hex}, {@code bin}, {@code oct}
 * and {@code base(n, b)} show an integer in base 16, 2, 8 or {@code b} ({@link Based}), and {@code
 * digits(n, b)} gives its digits. A symbolic argument leaves the call as it is.
 */
public final class Bases {

  /** The functions. */
  public static final Set<String> NAMES = Set.of("hex", "bin", "oct", "base", "digits");

  private Bases() {}

  /** Why a call is refused: its message is the error's. */
  public static final class BaseException extends RuntimeException {

    @java.io.Serial private static final long serialVersionUID = 1L;

    BaseException(String message) {
      super(message);
    }
  }

  /**
   * Applies a function of {@link #NAMES} to evaluated arguments.
   *
   * @param name the function
   * @param args the arguments, evaluated, based integers unwrapped
   * @return the value, or empty when an argument is symbolic
   * @throws BaseException if an argument is no integer or the base is out of range
   */
  public static Optional<Expr> apply(String name, List<Expr> args) {
    for (Expr a : args) {
      if (!(a instanceof Num)) {
        return Optional.empty();
      }
    }
    BigInteger n = integer(name, args.getFirst());
    int base =
        switch (name) {
          case "hex" -> 16;
          case "bin" -> 2;
          case "oct" -> 8;
          default -> base(args.get(1));
        };
    if (name.equals("digits")) {
      if (n.signum() < 0) {
        throw new BaseException("digits needs a non-negative integer");
      }
      List<Expr> out = new ArrayList<>();
      for (char c : n.toString(base).toCharArray()) {
        out.add(Num.of(Character.digit(c, base)));
      }
      return Optional.of(Vector.of(out));
    }
    return Optional.of(base == 10 ? Num.of(Int.of(n)) : new Based(Int.of(n), base));
  }

  private static BigInteger integer(String name, Expr e) {
    if (e instanceof Num num && num.value() instanceof Int i) {
      return i.value();
    }
    throw new BaseException(name + " needs an integer");
  }

  private static int base(Expr e) {
    if (e instanceof Num num
        && num.value() instanceof Int i
        && i.value().compareTo(BigInteger.TWO) >= 0
        && i.value().compareTo(BigInteger.valueOf(36)) <= 0) {
      return i.value().intValue();
    }
    throw new BaseException("the base must be an integer from 2 to 36");
  }

  /**
   * The text of a based integer: {@code 0xff}, {@code -0b101}, {@code 0o17}; in another base its
   * digits, which the printers mark with the base.
   *
   * @param b the based integer
   * @return the prefixed literal, or the bare digits with their sign
   */
  public static String text(Based b) {
    BigInteger v = b.value().value();
    String sign = v.signum() < 0 ? "-" : "";
    String digits = v.abs().toString(b.base());
    return switch (b.base()) {
      case 16 -> sign + "0x" + digits;
      case 2 -> sign + "0b" + digits;
      case 8 -> sign + "0o" + digits;
      default -> sign + digits;
    };
  }
}
