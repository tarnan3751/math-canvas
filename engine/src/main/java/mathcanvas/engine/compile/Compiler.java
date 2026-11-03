package mathcanvas.engine.compile;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.builtin.Builtin;
import mathcanvas.engine.builtin.Builtins;
import mathcanvas.engine.compile.UnsupportedException.Kind;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.eval.EvaluationException;
import mathcanvas.engine.eval.NumericForm;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Lambda;
import mathcanvas.engine.expr.Limit;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Piecewise;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.print.AsciiPrinter;

/**
 * One compilation: two passes over the tree, each linear and checking the cancellation at every
 * node. The first records, per node by identity, whether it depends on an argument (or is refused,
 * which is never folded) and whether it is <em>plain</em>: a value that is a real number once the
 * arguments are decimals, which is what decides the angle mode ({@code docs/language.md} §14.2).
 * The second builds the evaluator, folding each maximal constant part through {@link
 * NumericForm#numeric} to the nearest double.
 */
final class Compiler {

  /**
   * The digits constant parts are folded at before they are rounded to a double: well beyond a
   * double's 17, since {@code numeric} carries only 10 guard digits through a cancellation ({@code
   * sec(2^15·10^15 + π/3)}).
   */
  private static final int FOLD_DIGITS = 34;

  private static final int VARIABLE = 1;
  private static final int PLAIN = 2;

  private static final Num HALF = Num.of(Rational.of(1, 2));
  private static final Num MINUS_HALF = Num.of(Rational.of(-1, 2));

  /** The builtin functions compiled; any other application is refused. */
  private static final Set<String> FUNCTIONS =
      Set.of(
          "abs",
          "sign",
          "floor",
          "ceil",
          "trunc",
          "round",
          "factorial",
          "mod",
          "binomial",
          "gcd",
          "lcm",
          "min",
          "max",
          "re",
          "im",
          "conj",
          "arg",
          "sqrt",
          "cbrt",
          "root",
          "exp",
          "ln",
          "log",
          "log10",
          "log2",
          "sin",
          "cos",
          "tan",
          "cot",
          "sec",
          "csc",
          "asin",
          "acos",
          "atan",
          "acot",
          "asec",
          "acsc",
          "atan2",
          "sinh",
          "cosh",
          "tanh",
          "coth",
          "sech",
          "csch",
          "asinh",
          "acosh",
          "atanh",
          "acoth",
          "asech",
          "acsch",
          // the bitwise functions (§14.27), on integers within 2^53
          "bitand",
          "bitor",
          "bitxor",
          "bitnot",
          "shiftleft",
          "shiftright",
          "bitcount",
          "bit",
          // the closed forms of the time value of money (§14.28)
          "pv",
          "fv",
          "pmt",
          "nper",
          "effective",
          "nominal");

  private final Map<String, Integer> index;
  private final AngleMode mode;
  private final Cancellation cancellation;
  private final Map<Expr, Integer> flags = new IdentityHashMap<>();

  /** Whether a constant part is being compiled as nodes rather than folded ({@link #inDouble}). */
  private boolean inDouble;

  Compiler(Map<String, Integer> index, AngleMode mode, Cancellation cancellation) {
    this.index = index;
    this.mode = mode;
    this.cancellation = cancellation;
  }

  Node compile(Expr e) {
    analyse(e);
    return node(e);
  }

  // The first pass.

  private int analyse(Expr e) {
    Integer known = flags.get(e);
    if (known != null) {
      return known;
    }
    cancellation.check();
    int f =
        switch (e) {
          case Num n -> n.value() instanceof RealNumeric ? PLAIN : 0;
          case mathcanvas.engine.expr.Based b -> PLAIN;
          case Constant _ -> 0;
          // A free symbol too: it is refused when compiled, never folded.
          case Symbol _ -> VARIABLE | PLAIN;
          // a quantity is refused when compiled (§14.26)
          case mathcanvas.engine.expr.Unit _ -> VARIABLE;
          case Sum s -> operands(s.terms());
          case Product p -> operands(p.factors());
          case Power p -> operands(List.of(p.base(), p.exponent()));
          case Apply a -> FUNCTIONS.contains(name(a)) ? operands(a.args()) : VARIABLE;
          case Bool _,
              Lambda _,
              Relation _,
              ListExpr _,
              Vector _,
              Matrix _,
              SetExpr _,
              Interval _,
              Piecewise _,
              Derivative _,
              Integral _,
              Limit _,
              BigSum _,
              BigProduct _ ->
              VARIABLE;
        };
    flags.put(e, f);
    return f;
  }

  /**
   * Variable when any operand is; plain when variable and every operand is plain. A constant part
   * that is not a number ({@code √2}, {@code sin(π/7)}) stays symbolic in evaluation, so it is not
   * plain.
   */
  private int operands(List<Expr> operands) {
    int variable = 0;
    int plain = PLAIN;
    for (Expr op : operands) {
      int f = analyse(op);
      variable |= f & VARIABLE;
      plain &= f;
    }
    return variable == 0 ? 0 : variable | plain;
  }

  private boolean isVariable(Expr e) {
    return (flags.get(e) & VARIABLE) != 0;
  }

  private boolean isPlain(Expr e) {
    return (flags.get(e) & PLAIN) != 0;
  }

  private static String name(Apply a) {
    String head = a.head().name();
    return Builtins.lookup(head)
        .filter(b -> b.kind() == Builtin.Kind.FUNCTION)
        .map(Builtin::name)
        .orElse(head);
  }

  // The second pass.

  private Node node(Expr e) {
    cancellation.check();
    if (!isVariable(e) && !inDouble) {
      return new Node.Const(fold(e));
    }
    return switch (e) {
      case Num _, mathcanvas.engine.expr.Based _, Constant _ -> new Node.Const(fold(e));
      case Symbol s -> {
        Integer i = index.get(s.name());
        if (i == null) {
          throw new UnsupportedException(Kind.FREE_SYMBOL, s.name());
        }
        yield new Node.Arg(i);
      }
      case Sum s -> sum(s.terms());
      case Product p -> product(p.factors());
      case Power p -> power(p);
      case Apply a -> apply(a);
      case mathcanvas.engine.expr.Unit _ -> refuse("quantity");
      case Bool _ -> refuse("truth value");
      case Lambda _ -> refuse("lambda");
      case Relation _ -> refuse("relation");
      case ListExpr _ -> refuse("list");
      case Vector _ -> refuse("vector");
      case Matrix _ -> refuse("matrix");
      case SetExpr _ -> refuse("set");
      case Interval _ -> refuse("interval");
      case Piecewise _ -> refuse("piecewise expression");
      case Derivative _ -> refuse("derivative");
      case Integral _ -> refuse("integral");
      case Limit _ -> refuse("limit");
      case BigSum _ -> refuse("sum Σ");
      case BigProduct _ -> refuse("product Π");
    };
  }

  private static Node refuse(String form) {
    throw new UnsupportedException(Kind.FORM, form);
  }

  private Node[] all(List<Expr> operands) {
    Node[] out = new Node[operands.size()];
    for (int k = 0; k < out.length; k++) {
      out[k] = node(operands.get(k));
    }
    return out;
  }

  private Node sum(List<Expr> terms) {
    Node[] xs = all(terms);
    return xs.length == 2 ? new Node.Add2(xs[0], xs[1]) : new Node.AddN(xs);
  }

  private Node product(List<Expr> factors) {
    Node[] xs = all(factors);
    if (xs.length == 2) {
      return factors.getFirst().equals(Num.MINUS_ONE)
          ? new Node.Neg(xs[1])
          : new Node.Mul2(xs[0], xs[1]);
    }
    return new Node.MulN(xs);
  }

  private Node power(Power p) {
    Expr exponent = p.exponent();
    if (p.base().equals(Constant.E)) {
      return new Node.Exp(node(exponent));
    }
    Node x = node(p.base());
    if (isVariable(exponent)) {
      return new Node.Pow(x, node(exponent));
    }
    if (exponent instanceof Num n && n.value() instanceof Int i) {
      if (i.value().bitLength() >= 32) {
        return new Node.PowParity(x, i.toDouble(), i.value().testBit(0));
      }
      int k = i.value().intValue();
      return switch (k) {
        case -1 -> new Node.Inv(x);
        case 2 -> new Node.Square(x);
        default -> new Node.PowInt(x, k);
      };
    }
    if (exponent.equals(HALF)) {
      return new Node.Sqrt(x);
    }
    if (exponent.equals(MINUS_HALF)) {
      return new Node.Inv(new Node.Sqrt(x));
    }
    return new Node.PowConst(x, fold(exponent));
  }

  private Node apply(Apply a) {
    String name = name(a);
    if (!FUNCTIONS.contains(name)) {
      throw new UnsupportedException(Kind.FUNCTION, name);
    }
    List<Expr> args = a.args();
    if (name.equals("root")) {
      return root(args);
    }
    Node[] xs = all(args);
    Node x = xs[0];
    // In degrees and gradians a circular function converts a plain argument, and an inverse one
    // answers in the unit for a plain argument; anything else stays in radians, as evaluation does.
    boolean inUnit = mode != AngleMode.RADIANS && args.stream().allMatch(this::isPlain);
    double halfTurn = mode.halfTurn();
    return switch (name) {
      case "abs" -> new Node.Abs(x);
      case "sign" -> new Node.Sign(x);
      case "floor" -> new Node.Floor(x);
      case "ceil" -> new Node.Ceil(x);
      case "trunc" -> new Node.Trunc(x);
      case "round" -> new Node.Round(x);
      case "factorial" -> new Node.Factorial(x);
      case "mod" -> new Node.Mod(x, xs[1]);
      case "binomial" -> new Node.Binomial(x, xs[1]);
      case "gcd" -> new Node.Gcd(xs);
      case "lcm" -> new Node.Lcm(xs);
      case "min" -> xs.length == 1 ? x : new Node.Min(xs);
      case "max" -> xs.length == 1 ? x : new Node.Max(xs);
      case "re", "conj" -> x;
      case "im" -> new Node.Im(x);
      case "arg" -> new Node.ArgOf(x);
      case "sqrt" -> new Node.Sqrt(x);
      case "cbrt" -> new Node.Cbrt(x);
      case "exp" -> new Node.Exp(x);
      case "ln" -> new Node.Ln(x);
      case "log" -> xs.length == 1 ? new Node.Ln(x) : new Node.LogBase(x, xs[1]);
      case "log10" -> new Node.Log10(x);
      case "log2" -> new Node.Log2(x);
      case "sin" -> inUnit ? circular(RealFunctions.SIN, x) : new Node.Sin(x);
      case "cos" -> inUnit ? circular(RealFunctions.COS, x) : new Node.Cos(x);
      case "tan" -> inUnit ? circular(RealFunctions.TAN, x) : new Node.Tan(x);
      case "cot" -> inUnit ? circular(RealFunctions.COT, x) : new Node.Cot(x);
      case "sec" -> inUnit ? circular(RealFunctions.SEC, x) : new Node.Sec(x);
      case "csc" -> inUnit ? circular(RealFunctions.CSC, x) : new Node.Csc(x);
      case "asin" -> inUnit(new Node.Asin(x), inUnit, halfTurn);
      case "acos" -> inUnit(new Node.Acos(x), inUnit, halfTurn);
      case "atan" -> inUnit(new Node.Atan(x), inUnit, halfTurn);
      case "acot" -> inUnit(new Node.Acot(x), inUnit, halfTurn);
      case "asec" -> inUnit(new Node.Asec(x), inUnit, halfTurn);
      case "acsc" -> inUnit(new Node.Acsc(x), inUnit, halfTurn);
      case "atan2" -> inUnit(new Node.Atan2(x, xs[1]), inUnit, halfTurn);
      case "sinh" -> new Node.Sinh(x);
      case "cosh" -> new Node.Cosh(x);
      case "tanh" -> new Node.Tanh(x);
      case "coth" -> new Node.Coth(x);
      case "sech" -> new Node.Sech(x);
      case "csch" -> new Node.Csch(x);
      case "asinh" -> new Node.Asinh(x);
      case "acosh" -> new Node.Acosh(x);
      case "atanh" -> new Node.Atanh(x);
      case "acoth" -> new Node.Acoth(x);
      case "asech" -> new Node.Asech(x);
      case "pv", "fv", "pmt", "nper", "effective", "nominal" -> new Node.Finance(name, xs);
      case "bitand", "bitor", "bitxor", "bitnot", "shiftleft", "shiftright", "bitcount", "bit" ->
          new Node.Bits(name, xs);
      default -> new Node.Acsch(x);
    };
  }

  private Node circular(int function, Node x) {
    return new Node.Circular(function, x, mode.halfTurn());
  }

  /** An inverse circular function's radians, in the mode's unit when the argument is plain. */
  private static Node inUnit(Node radians, boolean inUnit, double halfTurn) {
    return inUnit ? new Node.Scale(radians, halfTurn / Math.PI) : radians;
  }

  /** {@code root(x, n)}: the degree must be a constant positive integer, as evaluation needs. */
  private Node root(List<Expr> args) {
    if (!(args.get(1) instanceof Num n
        && n.value() instanceof Int i
        && i.signum() > 0
        && i.value().bitLength() < 32)) {
      throw new UnsupportedException(Kind.FUNCTION, "root");
    }
    Node x = node(args.getFirst());
    int degree = i.value().intValue();
    return switch (degree) {
      case 1 -> x;
      case 2 -> new Node.Sqrt(x);
      case 3 -> new Node.Cbrt(x);
      default -> new Node.Root(x, degree);
    };
  }

  // Constants.

  /**
   * The nearest double to a constant part: {@code π}, {@code e} and numbers directly, {@code
   * undefined} as NaN, anything else through {@code numeric} at {@link #FOLD_DIGITS} digits, in
   * radians, since the value has been evaluated already. A part {@code numeric} cannot fold, out of
   * the tower's range, is evaluated once in {@code double} instead ({@link #inDouble}).
   */
  private double fold(Expr e) {
    if (e instanceof Num n) {
      if (n.value() instanceof RealNumeric r) {
        return r.toDouble();
      }
      throw refusal(n);
    }
    if (e.equals(Constant.PI)) {
      return Math.PI;
    }
    if (e.equals(Constant.E)) {
      return Math.E;
    }
    Expr value;
    try {
      value = NumericForm.numeric(e, FOLD_DIGITS, cancellation);
    } catch (EvaluationException outOfRange) {
      return inDouble(e);
    }
    if (value instanceof Num n && n.value() instanceof RealNumeric r) {
      return r.toDouble();
    }
    if (value.equals(Constant.UNDEFINED)) {
      return Double.NaN;
    }
    if (isUnfolded(value)) {
      return inDouble(e);
    }
    UnsupportedException refused = refusal(value);
    // A complex part, or one numeric leaves without a name to give, is named as written, not by
    // its 34-digit value.
    if (refused == null) {
      throw new UnsupportedException(Kind.FORM, AsciiPrinter.INSTANCE.print(e));
    }
    throw refused.kind() == Kind.COMPLEX
        ? new UnsupportedException(Kind.COMPLEX, AsciiPrinter.INSTANCE.print(e))
        : refused;
  }

  /**
   * Whether {@code numeric} left arithmetic of real numbers unfolded: the tower could not reach the
   * value ({@code e^(10^30)}).
   */
  private static boolean isUnfolded(Expr value) {
    if (value instanceof Num n) {
      return n.value() instanceof RealNumeric;
    }
    if (!(value instanceof Sum || value instanceof Product || value instanceof Power)) {
      return false;
    }
    for (Expr op : Operands.of(value)) {
      if (!isUnfolded(op)) {
        return false;
      }
    }
    return true;
  }

  /**
   * A constant part {@code numeric} cannot fold, out of its range, compiled as nodes and evaluated
   * once in {@code double}: ±∞ or NaN like any overflow.
   */
  private double inDouble(Expr e) {
    boolean outer = inDouble;
    inDouble = true;
    try {
      return node(e).eval(new double[0]);
    } finally {
      inDouble = outer;
    }
  }

  /**
   * The refusal of a constant value that is not a real number, naming what it holds, or null when
   * it holds nothing to name but arithmetic {@code numeric} left unfolded ({@code sin(e^(10^30))}).
   */
  private static UnsupportedException refusal(Expr value) {
    if (value instanceof Num n) {
      return new UnsupportedException(Kind.COMPLEX, AsciiPrinter.INSTANCE.print(n));
    }
    if (value instanceof Constant k) {
      return new UnsupportedException(
          Kind.FORM, k.equals(Constant.INFINITY) ? "infinity" : "domain");
    }
    // What stays is named from the inside out: a call that stays on a complex argument (sign(i))
    // is a complex value, one that stays on real numbers is the function's. A complex operand
    // names the refusal before any other.
    UnsupportedException named = null;
    boolean unnamed = false;
    for (Expr op : Operands.of(value)) {
      if (!(op instanceof Num n && n.value() instanceof RealNumeric)) {
        UnsupportedException r = refusal(op);
        if (r != null && r.kind() == Kind.COMPLEX) {
          return r;
        }
        if (r == null) {
          unnamed = true;
        } else if (named == null) {
          named = r;
        }
      }
    }
    if (named != null || unnamed) {
      return named;
    }
    if (value instanceof Apply a) {
      return new UnsupportedException(Kind.FUNCTION, name(a));
    }
    return null;
  }
}
