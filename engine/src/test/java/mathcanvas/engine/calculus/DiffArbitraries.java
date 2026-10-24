package mathcanvas.engine.calculus;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

/**
 * Expressions to differentiate in {@code x}, with a parameter {@code a}: sums and products, powers
 * with numeric and bounded symbolic exponents, exponentials, every function of the derivative
 * table, logarithms to a base, roots and {@code atan2}, nested to depth 4. Built from jqwik's
 * random source directly, as {@code ExprArbitraries} is, so a failing sample is reported unshrunk.
 */
final class DiffArbitraries {

  static final Symbol X = Symbol.of("x");
  static final Symbol A = Symbol.of("a");

  /** The one-argument functions of the derivative table. */
  static final List<String> FUNCTIONS =
      List.of(
          "exp", "ln", "log", "log10", "log2", "cbrt", "sin", "cos", "tan", "cot", "sec", "csc",
          "asin", "acos", "atan", "acot", "asec", "acsc", "sinh", "cosh", "tanh", "coth", "sech",
          "csch", "asinh", "acosh", "atanh", "acoth", "asech", "acsch", "abs", "sign", "floor",
          "ceil", "round", "trunc");

  private static final int MAX_DEPTH = 4;

  private DiffArbitraries() {}

  static Arbitrary<Expr> exprs() {
    return Arbitraries.randomValue(
        random -> new Builder(random).expr(random.nextInt(MAX_DEPTH) + 1));
  }

  private record Builder(Random random) {

    Expr leaf() {
      return switch (random.nextInt(10)) {
        case 0, 1, 2, 3, 4 -> X;
        case 5 -> A;
        case 6 -> Num.of(random.nextInt(7) - 3);
        case 7 -> Num.of(Rational.of(random.nextInt(9) - 4, random.nextInt(4) + 1));
        case 8 -> random.nextBoolean() ? Constant.PI : Constant.E;
        default -> Num.of(Real.of(random.nextBoolean() ? "0.5" : "-1.5"));
      };
    }

    Expr exponent() {
      return switch (random.nextInt(8)) {
        case 0 -> Num.of(Rational.of(1, 2));
        case 1 -> Num.of(Rational.of(-1, 2));
        case 2 -> Num.of(Rational.of(1, 3));
        case 3 -> Num.of(Rational.of(2, 3));
        case 4 -> Num.of(-1);
        default -> Num.of(random.nextInt(6) - 1);
      };
    }

    Expr expr(int depth) {
      if (depth == 0) {
        return leaf();
      }
      int d = depth - 1;
      return switch (random.nextInt(14)) {
        case 0, 1 -> Sum.of(list(d));
        case 2, 3 -> Product.of(list(d));
        case 4, 5 -> Power.of(expr(d), exponent());
        case 6 -> Power.of(expr(d), bounded(d));
        case 7 -> Power.of(random.nextBoolean() ? Constant.E : Num.of(Int.TWO), bounded(d));
        case 8 -> Apply.of(Symbol.of("log"), expr(d), random.nextBoolean() ? Num.of(2) : expr(d));
        case 9 -> Apply.of(Symbol.of("root"), expr(d), Num.of(random.nextInt(2) + 3));
        case 10 -> Apply.of(Symbol.of("atan2"), expr(d), expr(d));
        default -> Apply.of(Symbol.of(FUNCTIONS.get(random.nextInt(FUNCTIONS.size()))), expr(d));
      };
    }

    /**
     * A symbolic exponent whose value stays small where the points are (well below 10⁴, the numeric
     * evaluator's own bound): a leaf, a multiple of {@code x}, or a bounded function of any
     * expression. An unbounded one ({@code (πx²)^(−2x)}) makes the power oscillate or wind faster
     * than any difference quotient can follow.
     */
    Expr bounded(int depth) {
      return switch (random.nextInt(4)) {
        case 0 -> leaf();
        case 1 -> Product.of(Num.of(random.nextInt(5) - 2), X);
        default ->
            Apply.of(
                Symbol.of(List.of("sin", "cos", "atan", "tanh").get(random.nextInt(4))),
                expr(depth));
      };
    }

    List<Expr> list(int depth) {
      int n = 2 + random.nextInt(2);
      List<Expr> out = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
        out.add(expr(random.nextInt(depth + 1)));
      }
      return out;
    }
  }
}
