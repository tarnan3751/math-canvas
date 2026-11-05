package mathcanvas.engine.bases;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.number.Int;

/**
 * The bitwise functions ({@code docs/language.md} §14.27), on integers with {@code BigInteger}'s
 * two's complement semantics for negatives: {@code bitand}, {@code bitor}, {@code bitxor}, {@code
 * bitnot} ({@code -a - 1}), {@code shiftleft}, {@code shiftright} (arithmetic), {@code bitcount}
 * and {@code bit}. A symbolic argument leaves the call as it is.
 */
public final class Bitwise {

  /** The functions. */
  public static final Set<String> NAMES =
      Set.of("bitand", "bitor", "bitxor", "bitnot", "shiftleft", "shiftright", "bitcount", "bit");

  /** The largest shift: the canonical form's folding bound, 2²⁰ bits. */
  public static final int MAX_SHIFT = 1 << 20;

  private Bitwise() {}

  /**
   * Applies a function of {@link #NAMES} to evaluated arguments.
   *
   * @param name the function
   * @param args the arguments, evaluated, based integers unwrapped
   * @return the value, or empty when an argument is symbolic
   * @throws Bases.BaseException if an argument is no integer or a shift or index is out of range
   */
  public static Optional<Expr> apply(String name, List<Expr> args) {
    for (Expr a : args) {
      if (!(a instanceof Num)) {
        return Optional.empty();
      }
    }
    BigInteger a = integer(name, args.getFirst());
    BigInteger result =
        switch (name) {
          case "bitand" -> a.and(integer(name, args.get(1)));
          case "bitor" -> a.or(integer(name, args.get(1)));
          case "bitxor" -> a.xor(integer(name, args.get(1)));
          case "bitnot" -> a.not();
          case "shiftleft" -> a.shiftLeft(shift(name, args.get(1)));
          case "shiftright" -> a.shiftRight(shift(name, args.get(1)));
          case "bitcount" -> {
            if (a.signum() < 0) {
              throw new Bases.BaseException("`bitcount` needs a non-negative integer");
            }
            yield BigInteger.valueOf(a.bitCount());
          }
          case "bit" -> a.testBit(shift(name, args.get(1))) ? BigInteger.ONE : BigInteger.ZERO;
          default -> throw new IllegalArgumentException("not a bitwise function: " + name);
        };
    return Optional.of(Num.of(Int.of(result)));
  }

  private static BigInteger integer(String name, Expr e) {
    if (e instanceof Num num && num.value() instanceof Int i) {
      return i.value();
    }
    throw new Bases.BaseException("`" + name + "` needs integers");
  }

  private static int shift(String name, Expr e) {
    BigInteger n = integer(name, e);
    if (n.signum() < 0 || n.compareTo(BigInteger.valueOf(MAX_SHIFT)) > 0) {
      throw new Bases.BaseException(
          (name.equals("bit") ? "`bit`'s index" : "`" + name + "`'s shift")
              + " must be an integer from 0 to "
              + MAX_SHIFT);
    }
    return n.intValue();
  }
}
