package mathcanvas.engine.compile;

/**
 * A node of a compiled evaluator: an immutable tree of final classes, one per operation, each
 * evaluating its operands and then itself. The tree form measured faster than a stack of
 * instructions (<b>Compilation</b> in {@code docs/reference/engine.md}). Nodes hold no state, so a
 * tree is safe to evaluate on any number of threads at once.
 */
abstract class Node {

  /**
   * The value at a point.
   *
   * @param args the values of the arguments, in order
   * @return the value
   */
  abstract double eval(double[] args);

  /** A constant. */
  static final class Const extends Node {
    private final double value;

    Const(double value) {
      this.value = value;
    }

    @Override
    double eval(double[] args) {
      return value;
    }
  }

  /** An argument, by index. */
  static final class Arg extends Node {
    private final int index;

    Arg(int index) {
      this.index = index;
    }

    @Override
    double eval(double[] args) {
      return args[index];
    }
  }

  /** {@code x + y}. */
  static final class Add2 extends Node {
    private final Node x;
    private final Node y;

    Add2(Node x, Node y) {
      this.x = x;
      this.y = y;
    }

    @Override
    double eval(double[] args) {
      return x.eval(args) + y.eval(args);
    }
  }

  /** {@code x·y}. */
  static final class Mul2 extends Node {
    private final Node x;
    private final Node y;

    Mul2(Node x, Node y) {
      this.x = x;
      this.y = y;
    }

    @Override
    double eval(double[] args) {
      return x.eval(args) * y.eval(args);
    }
  }

  /** {@code x^n} for an integer {@code n} other than −1, 0, 1 and 2. */
  static final class PowInt extends Node {
    private final Node x;
    private final int n;

    PowInt(Node x, int n) {
      this.x = x;
      this.n = n;
    }

    @Override
    double eval(double[] args) {
      return RealFunctions.pow(x.eval(args), n);
    }
  }

  /** {@code x^y} for a constant {@code y}. */
  static final class PowConst extends Node {
    private final Node x;
    private final double y;

    PowConst(Node x, double y) {
      this.x = x;
      this.y = y;
    }

    @Override
    double eval(double[] args) {
      return RealFunctions.pow(x.eval(args), y);
    }
  }

  /** {@code x^y} for an integer {@code y} beyond the {@code int} range, with its parity. */
  static final class PowParity extends Node {
    private final Node x;
    private final double y;
    private final boolean odd;

    PowParity(Node x, double y, boolean odd) {
      this.x = x;
      this.y = y;
      this.odd = odd;
    }

    @Override
    double eval(double[] args) {
      return RealFunctions.pow(x.eval(args), y, odd);
    }
  }

  /** {@code root(x, n)} for a degree {@code n ≥ 4}. */
  static final class Root extends Node {
    private final Node x;
    private final int n;

    Root(Node x, int n) {
      this.x = x;
      this.n = n;
    }

    @Override
    double eval(double[] args) {
      return RealFunctions.root(x.eval(args), n);
    }
  }

  /** {@code x·factor}: an inverse circular function's radians in the angle mode's unit. */
  static final class Scale extends Node {
    private final Node x;
    private final double factor;

    Scale(Node x, double factor) {
      this.x = x;
      this.factor = factor;
    }

    @Override
    double eval(double[] args) {
      return x.eval(args) * factor;
    }
  }

  /** A circular function of an angle in degrees or gradians ({@link RealFunctions#circular}). */
  static final class Circular extends Node {
    private final int function;
    private final Node x;
    private final double halfTurn;

    Circular(int function, Node x, double halfTurn) {
      this.function = function;
      this.x = x;
      this.halfTurn = halfTurn;
    }

    @Override
    double eval(double[] args) {
      return RealFunctions.circular(function, x.eval(args), halfTurn);
    }
  }

  /** The negation. */
  static final class Neg extends Node {
    private final Node x;

    Neg(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return -v;
    }
  }

  /** {@code 1/x}, NaN at 0. */
  static final class Inv extends Node {
    private final Node x;

    Inv(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.inverse(v);
    }
  }

  /** {@code x²}. */
  static final class Square extends Node {
    private final Node x;

    Square(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return v * v;
    }
  }

  /** {@code e^x}. */
  static final class Exp extends Node {
    private final Node x;

    Exp(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return Math.exp(v);
    }
  }

  /** The square root, NaN below 0. */
  static final class Sqrt extends Node {
    private final Node x;

    Sqrt(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return Math.sqrt(v);
    }
  }

  /** The real cube root. */
  static final class Cbrt extends Node {
    private final Node x;

    Cbrt(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return Math.cbrt(v);
    }
  }

  /** The absolute value. */
  static final class Abs extends Node {
    private final Node x;

    Abs(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return Math.abs(v);
    }
  }

  /** The sign. */
  static final class Sign extends Node {
    private final Node x;

    Sign(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.sign(v);
    }
  }

  /** The floor. */
  static final class Floor extends Node {
    private final Node x;

    Floor(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return Math.floor(v);
    }
  }

  /** The ceiling. */
  static final class Ceil extends Node {
    private final Node x;

    Ceil(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return Math.ceil(v);
    }
  }

  /** The integer part. */
  static final class Trunc extends Node {
    private final Node x;

    Trunc(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.trunc(v);
    }
  }

  /** The nearest integer, halves away from zero. */
  static final class Round extends Node {
    private final Node x;

    Round(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.round(v);
    }
  }

  /** The factorial of an integer. */
  static final class Factorial extends Node {
    private final Node x;

    Factorial(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.factorial(v);
    }
  }

  /** The natural logarithm. */
  static final class Ln extends Node {
    private final Node x;

    Ln(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.log(v);
    }
  }

  /** The logarithm to base 10. */
  static final class Log10 extends Node {
    private final Node x;

    Log10(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.log10(v);
    }
  }

  /** The logarithm to base 2. */
  static final class Log2 extends Node {
    private final Node x;

    Log2(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.log2(v);
    }
  }

  /** The sine in radians. */
  static final class Sin extends Node {
    private final Node x;

    Sin(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return Math.sin(v);
    }
  }

  /** The cosine in radians. */
  static final class Cos extends Node {
    private final Node x;

    Cos(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return Math.cos(v);
    }
  }

  /** The tangent in radians. */
  static final class Tan extends Node {
    private final Node x;

    Tan(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return Math.tan(v);
    }
  }

  /** The cotangent in radians. */
  static final class Cot extends Node {
    private final Node x;

    Cot(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.cot(v);
    }
  }

  /** The secant in radians. */
  static final class Sec extends Node {
    private final Node x;

    Sec(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return 1 / Math.cos(v);
    }
  }

  /** The cosecant in radians. */
  static final class Csc extends Node {
    private final Node x;

    Csc(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.csc(v);
    }
  }

  /** The arcsine in radians. */
  static final class Asin extends Node {
    private final Node x;

    Asin(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return Math.asin(v);
    }
  }

  /** The arccosine in radians. */
  static final class Acos extends Node {
    private final Node x;

    Acos(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return Math.acos(v);
    }
  }

  /** The arctangent in radians. */
  static final class Atan extends Node {
    private final Node x;

    Atan(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return Math.atan(v);
    }
  }

  /** The arccotangent in radians. */
  static final class Acot extends Node {
    private final Node x;

    Acot(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.acot(v);
    }
  }

  /** The arcsecant in radians. */
  static final class Asec extends Node {
    private final Node x;

    Asec(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.asec(v);
    }
  }

  /** The arccosecant in radians. */
  static final class Acsc extends Node {
    private final Node x;

    Acsc(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.acsc(v);
    }
  }

  /** The hyperbolic sine. */
  static final class Sinh extends Node {
    private final Node x;

    Sinh(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return Math.sinh(v);
    }
  }

  /** The hyperbolic cosine. */
  static final class Cosh extends Node {
    private final Node x;

    Cosh(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return Math.cosh(v);
    }
  }

  /** The hyperbolic tangent. */
  static final class Tanh extends Node {
    private final Node x;

    Tanh(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return Math.tanh(v);
    }
  }

  /** The hyperbolic cotangent. */
  static final class Coth extends Node {
    private final Node x;

    Coth(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.coth(v);
    }
  }

  /** The hyperbolic secant. */
  static final class Sech extends Node {
    private final Node x;

    Sech(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return 1 / Math.cosh(v);
    }
  }

  /** The hyperbolic cosecant. */
  static final class Csch extends Node {
    private final Node x;

    Csch(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.csch(v);
    }
  }

  /** The inverse hyperbolic sine. */
  static final class Asinh extends Node {
    private final Node x;

    Asinh(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.asinh(v);
    }
  }

  /** The inverse hyperbolic cosine. */
  static final class Acosh extends Node {
    private final Node x;

    Acosh(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.acosh(v);
    }
  }

  /** The inverse hyperbolic tangent. */
  static final class Atanh extends Node {
    private final Node x;

    Atanh(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.atanh(v);
    }
  }

  /** The inverse hyperbolic cotangent. */
  static final class Acoth extends Node {
    private final Node x;

    Acoth(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.acoth(v);
    }
  }

  /** The inverse hyperbolic secant. */
  static final class Asech extends Node {
    private final Node x;

    Asech(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.asech(v);
    }
  }

  /** The inverse hyperbolic cosecant. */
  static final class Acsch extends Node {
    private final Node x;

    Acsch(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.acsch(v);
    }
  }

  /** The imaginary part of a real value. */
  static final class Im extends Node {
    private final Node x;

    Im(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.im(v);
    }
  }

  /** The argument of a real value. */
  static final class ArgOf extends Node {
    private final Node x;

    ArgOf(Node x) {
      this.x = x;
    }

    @Override
    double eval(double[] args) {
      double v = x.eval(args);
      return RealFunctions.arg(v);
    }
  }

  /** {@code x^y}. */
  static final class Pow extends Node {
    private final Node x;
    private final Node y;

    Pow(Node x, Node y) {
      this.x = x;
      this.y = y;
    }

    @Override
    double eval(double[] args) {
      double u = x.eval(args);
      double v = y.eval(args);
      return RealFunctions.pow(u, v);
    }
  }

  /** {@code mod(x, y)}. */
  static final class Mod extends Node {
    private final Node x;
    private final Node y;

    Mod(Node x, Node y) {
      this.x = x;
      this.y = y;
    }

    @Override
    double eval(double[] args) {
      double u = x.eval(args);
      double v = y.eval(args);
      return RealFunctions.mod(u, v);
    }
  }

  /**
   * A bitwise function (§14.27) on integers within 2⁵³, as {@code BigInteger} computes it; NaN for
   * an argument that is no such integer, a shift or index out of range, or a result past 2⁵³.
   */
  static final class Bits extends Node {
    private static final double LIMIT = 9007199254740992.0;
    private final String name;
    private final Node[] xs;

    Bits(String name, Node[] xs) {
      this.name = name;
      this.xs = xs;
    }

    @Override
    double eval(double[] args) {
      long[] v = new long[xs.length];
      for (int k = 0; k < xs.length; k++) {
        double d = xs[k].eval(args);
        if (!(Math.abs(d) <= LIMIT) || d != Math.rint(d)) {
          return Double.NaN;
        }
        v[k] = (long) d;
      }
      long a = v[0];
      double out =
          switch (name) {
            case "bitand" -> a & v[1];
            case "bitor" -> a | v[1];
            case "bitxor" -> a ^ v[1];
            case "bitnot" -> ~a;
            case "bitcount" -> a < 0 ? Double.NaN : Long.bitCount(a);
            case "shiftleft" ->
                v[1] < 0 || v[1] > 53 ? Double.NaN : Math.scalb((double) a, (int) v[1]);
            case "shiftright" -> v[1] < 0 ? Double.NaN : v[1] > 62 ? (a < 0 ? -1 : 0) : a >> v[1];
            default -> v[1] < 0 ? Double.NaN : v[1] > 62 ? (a < 0 ? 1 : 0) : (a >> v[1]) & 1;
          };
      return Math.abs(out) <= LIMIT ? out : Double.NaN;
    }
  }

  /** {@code log(x, b)}. */
  static final class LogBase extends Node {
    private final Node x;
    private final Node y;

    LogBase(Node x, Node y) {
      this.x = x;
      this.y = y;
    }

    @Override
    double eval(double[] args) {
      double u = x.eval(args);
      double v = y.eval(args);
      return RealFunctions.log(u, v);
    }
  }

  /** {@code binomial(n, k)}. */
  static final class Binomial extends Node {
    private final Node x;
    private final Node y;

    Binomial(Node x, Node y) {
      this.x = x;
      this.y = y;
    }

    @Override
    double eval(double[] args) {
      double u = x.eval(args);
      double v = y.eval(args);
      return RealFunctions.binomial(u, v);
    }
  }

  /** {@code atan2(y, x)} in radians. */
  static final class Atan2 extends Node {
    private final Node x;
    private final Node y;

    Atan2(Node x, Node y) {
      this.x = x;
      this.y = y;
    }

    @Override
    double eval(double[] args) {
      double u = x.eval(args);
      double v = y.eval(args);
      return RealFunctions.atan2(u, v);
    }
  }

  /** A sum, of two or more operands. */
  static final class AddN extends Node {
    private final Node[] xs;

    AddN(Node[] xs) {
      this.xs = xs;
    }

    @Override
    double eval(double[] args) {
      double r = xs[0].eval(args);
      for (int k = 1; k < xs.length; k++) {
        double v = xs[k].eval(args);
        r += v;
      }
      return r;
    }
  }

  /** A product, of two or more operands. */
  static final class MulN extends Node {
    private final Node[] xs;

    MulN(Node[] xs) {
      this.xs = xs;
    }

    @Override
    double eval(double[] args) {
      double r = xs[0].eval(args);
      for (int k = 1; k < xs.length; k++) {
        double v = xs[k].eval(args);
        r *= v;
      }
      return r;
    }
  }

  /** The least value, of two or more operands. */
  static final class Min extends Node {
    private final Node[] xs;

    Min(Node[] xs) {
      this.xs = xs;
    }

    @Override
    double eval(double[] args) {
      double r = xs[0].eval(args);
      for (int k = 1; k < xs.length; k++) {
        double v = xs[k].eval(args);
        r = Math.min(r, v);
      }
      return r;
    }
  }

  /** The greatest value, of two or more operands. */
  static final class Max extends Node {
    private final Node[] xs;

    Max(Node[] xs) {
      this.xs = xs;
    }

    @Override
    double eval(double[] args) {
      double r = xs[0].eval(args);
      for (int k = 1; k < xs.length; k++) {
        double v = xs[k].eval(args);
        r = Math.max(r, v);
      }
      return r;
    }
  }

  /** The gcd of integers, of two or more operands. */
  static final class Gcd extends Node {
    private final Node[] xs;

    Gcd(Node[] xs) {
      this.xs = xs;
    }

    @Override
    double eval(double[] args) {
      double r = xs[0].eval(args);
      for (int k = 1; k < xs.length; k++) {
        double v = xs[k].eval(args);
        r = RealFunctions.gcd(r, v);
      }
      return r;
    }
  }

  /** The lcm of integers, of two or more operands. */
  static final class Lcm extends Node {
    private final Node[] xs;

    Lcm(Node[] xs) {
      this.xs = xs;
    }

    @Override
    double eval(double[] args) {
      double r = xs[0].eval(args);
      for (int k = 1; k < xs.length; k++) {
        double v = xs[k].eval(args);
        r = RealFunctions.lcm(r, v);
      }
      return r;
    }
  }
}
