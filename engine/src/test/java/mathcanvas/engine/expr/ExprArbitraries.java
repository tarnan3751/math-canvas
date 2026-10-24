package mathcanvas.engine.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import mathcanvas.engine.expr.Piecewise.Piece;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

/**
 * Generators of raw expressions for property tests, shared by later packages' tests.
 *
 * <p>Symbols come from a small alphabet and numbers from a small pool, so equal subterms are
 * common, and "twin" shapes ({@code e + e}, {@code e - e}, {@code e·e^-1}, {@code (e^a)^n}, {@code
 * e^a·e^b}, {@code (e^n)^q·(e^n)^(1-q)·e}, {@code c·M·e - c·M·e} around a matrix {@code M}) make
 * collection and cancellation frequent. Operands of a sum or product may themselves be raw sums or
 * products. {@link #exactExprs()} has exact numbers and the arithmetic kinds only, for evaluation
 * at points; {@link #exprs()} has every kind, inexact and complex numbers, infinity, undefined and
 * the domains.
 *
 * <p>The trees are built directly from jqwik's random source, which is reproducible from the seed
 * jqwik reports; jqwik's combinators were orders of magnitude slower on recursive trees of this
 * size. The cost is that a failing sample is reported as generated, not shrunk.
 */
public final class ExprArbitraries {

  /** The symbols generated. */
  public static final List<Symbol> SYMBOLS =
      List.of(Symbol.of("a"), Symbol.of("b"), Symbol.of("x"), Symbol.of("y"));

  /** The function heads generated. */
  public static final List<Symbol> HEADS = List.of(Symbol.of("f"), Symbol.of("g"));

  private static final int MAX_DEPTH = 4;

  private static final List<Numeric> SPECIAL =
      List.of(
          Int.of(4),
          Int.of(8),
          Int.of(9),
          Int.of(16),
          Rational.of(1, 4),
          Rational.of(4, 9),
          Rational.of(8, 27),
          Complex.I,
          Complex.I.negate(),
          Complex.of(Int.ONE, Int.ONE));

  private static final List<Numeric> INEXACT =
      List.of(
          Real.of("0.0"),
          Real.of("1.0"),
          Real.of("-1.0"),
          Real.of("0.5"),
          Real.of("2.5"),
          Real.of("-1.5"),
          Complex.of(Real.of("1.0"), Real.of("2.0")),
          Complex.of(Real.of("0.5"), Real.of("1.0")));

  private static final List<Expr> EXPONENTS =
      List.of(
          Num.of(Rational.of(1, 2)),
          Num.of(Rational.of(-1, 2)),
          Num.of(Rational.of(1, 3)),
          Num.of(Rational.of(2, 3)));

  private static final List<Constant> CONSTANTS =
      List.of(
          Constant.PI,
          Constant.E,
          Constant.INFINITY,
          Constant.UNDEFINED,
          Constant.INTEGERS,
          Constant.REALS);

  private ExprArbitraries() {}

  /**
   * Exact numbers: small integers most often, simple rationals, perfect powers, Gaussian integers.
   *
   * @return the generator
   */
  public static Arbitrary<Num> exactNumbers() {
    return Arbitraries.randomValue(random -> new Builder(random, true).number());
  }

  /**
   * Every kind of number: the exact ones, decimals (zero and one among them) and complex numbers
   * with inexact parts.
   *
   * @return the generator
   */
  public static Arbitrary<Num> numbers() {
    return Arbitraries.randomValue(random -> new Builder(random, false).number());
  }

  /**
   * Raw expressions over exact numbers, the symbols, {@code π} and {@code e}, built from sums,
   * products, powers and applications of {@code f} and {@code g} to depth 4.
   *
   * @return the generator
   */
  public static Arbitrary<Expr> exactExprs() {
    return Arbitraries.randomValue(random -> new Builder(random, true).expr(depth(random)));
  }

  /**
   * Raw expressions of every kind: the arithmetic of {@link #exactExprs()} over every kind of
   * number, infinity, undefined, the domains and truth values, with relations, collections,
   * lambdas, piecewise expressions, the unevaluated forms and products with a matrix mixed in.
   *
   * @return the generator
   */
  public static Arbitrary<Expr> exprs() {
    return Arbitraries.randomValue(random -> new Builder(random, false).expr(depth(random)));
  }

  private static int depth(Random random) {
    return random.nextInt(MAX_DEPTH + 1);
  }

  /** Builds one tree from a random source. */
  private record Builder(Random random, boolean exact) {

    Num number() {
      int pick = random.nextInt(exact ? 13 : 16);
      Numeric value;
      if (pick < 6) {
        value = Int.of(random.nextInt(7) - 3);
      } else if (pick < 8) {
        value = Int.of(random.nextInt(41) - 20);
      } else if (pick < 11) {
        value = Rational.of(random.nextInt(19) - 9, random.nextInt(6) + 1);
      } else if (pick < 13) {
        value = pickFrom(SPECIAL);
      } else {
        value = pickFrom(INEXACT);
      }
      return Num.of(value);
    }

    Expr expr(int depth) {
      if (depth == 0) {
        return leaf();
      }
      if (!exact && random.nextInt(4) == 0) {
        return other(depth - 1);
      }
      Supplier<Expr> sub = () -> expr(random.nextInt(depth));
      return switch (random.nextInt(19)) {
        case 0, 1 -> expr(depth - 1);
        case 2, 3, 4, 5 -> Sum.of(list(sub, 2, 4));
        case 6, 7, 8, 9 -> Product.of(list(sub, 2, 4));
        case 10, 11, 12 -> Power.of(sub.get(), exponent(sub));
        case 13 -> Apply.of(pickFrom(HEADS), list(sub, 1, 2));
        case 14 -> {
          Expr e = sub.get();
          yield random.nextBoolean() ? Sum.of(e, e) : Exprs.sub(e, e);
        }
        case 15 -> {
          Expr e = sub.get();
          yield Product.of(e, Exprs.inv(e));
        }
        case 16 -> Power.of(Power.of(sub.get(), exponent(sub)), Num.of(random.nextInt(7) - 3));
        case 17 -> {
          // Powers of a power whose exponents often sum to an integer, beside the base; the
          // exponent is rational or symbolic.
          Expr e = sub.get();
          Expr inner = Power.of(e, Num.of(random.nextInt(7) - 3));
          Expr q = random.nextInt(3) == 0 ? pickFrom(SYMBOLS) : pickFrom(EXPONENTS);
          Expr other = random.nextBoolean() ? q : Exprs.sub(Num.ONE, q);
          yield Product.of(
              Power.of(inner, q),
              Power.of(inner, other),
              random.nextBoolean() ? e : Power.of(e, exponent(sub)));
        }
        default -> {
          Expr e = sub.get();
          yield Product.of(Power.of(e, exponent(sub)), Power.of(e, exponent(sub)));
        }
      };
    }

    private Expr leaf() {
      int pick = random.nextInt(exact ? 9 : 11);
      if (pick < 3) {
        return number();
      }
      if (pick < 8) {
        return pickFrom(SYMBOLS);
      }
      if (pick < 9) {
        return random.nextBoolean() ? Constant.PI : Constant.E;
      }
      return pick < 10 ? pickFrom(CONSTANTS) : Bool.of(random.nextBoolean());
    }

    private Expr exponent(Supplier<Expr> sub) {
      int pick = random.nextInt(12);
      if (pick < 8) {
        return Num.of(random.nextInt(7) - 3);
      }
      if (pick < 10) {
        return pickFrom(EXPONENTS);
      }
      return pick < 11 ? pickFrom(SYMBOLS) : sub.get();
    }

    private Expr other(int depth) {
      Supplier<Expr> sub = () -> expr(depth);
      Symbol var = pickFrom(SYMBOLS);
      return switch (random.nextInt(16)) {
        case 0 -> Relation.of(pickFrom(List.of(Relation.Op.values())), sub.get(), sub.get());
        case 1 -> ListExpr.of(list(sub, 0, 3));
        case 2 -> Vector.of(list(sub, 1, 3));
        case 3 -> SetExpr.of(list(sub, 0, 3));
        case 4 -> Matrix.of(List.of(list(sub, 2, 2), list(sub, 2, 2)));
        case 5 -> Interval.of(sub.get(), sub.get(), random.nextBoolean(), random.nextBoolean());
        case 6 -> Piecewise.of(List.of(new Piece(sub.get(), sub.get())), sub.get());
        case 7 -> Lambda.of(var, sub.get());
        case 8 -> Derivative.of(sub.get(), var, random.nextInt(2) + 1);
        case 9 -> Integral.indefinite(sub.get(), var);
        case 10 -> Integral.definite(sub.get(), var, sub.get(), sub.get());
        case 11 -> Limit.of(sub.get(), var, sub.get(), pickFrom(List.of(Limit.Direction.values())));
        case 12 -> BigSum.of(sub.get(), var, sub.get(), sub.get());
        case 13 -> BigProduct.of(sub.get(), var, sub.get(), sub.get());
        case 14 -> {
          // Products that differ only by their coefficient, around a non-commuting factor.
          Expr e = sub.get();
          Expr term = Product.of(number(), Matrix.of(List.of(List.of(var, e))), e);
          yield random.nextBoolean() ? Exprs.sub(term, term) : Sum.of(term, Exprs.neg(term));
        }
        default -> {
          Expr e = sub.get();
          yield Product.of(e, Matrix.of(List.of(List.of(var, e))), var);
        }
      };
    }

    private List<Expr> list(Supplier<Expr> sub, int min, int max) {
      int size = min + random.nextInt(max - min + 1);
      List<Expr> items = new ArrayList<>(size);
      for (int k = 0; k < size; k++) {
        items.add(sub.get());
      }
      return items;
    }

    private <T> T pickFrom(List<T> items) {
      return items.get(random.nextInt(items.size()));
    }
  }
}
