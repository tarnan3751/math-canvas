package mathcanvas.engine.compile;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.eval.Definition;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.EvaluationException;
import mathcanvas.engine.eval.Evaluator;
import mathcanvas.engine.eval.NumericForm;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Canonical;
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
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;

/**
 * The oracle of the compiled evaluators: a compiled value at a point against the engine's.
 *
 * <p><b>The whole value.</b> The engine's value is the compiled expression evaluated in the mode
 * with each argument bound to the decimal its double denotes, every digit kept, and then {@link
 * NumericForm#numeric} at 17 digits. Decimals, not the exact rationals: in degrees and gradians the
 * mode applies to a plain number, and nested functions convert only at decimal points ({@code
 * sin(sin 30)} is {@code sin(1/2)}, while {@code sin(sin 30.0)} converts both); and {@code numeric}
 * turns the degree of {@code root(x, 3)} into the decimal {@code 3.0}, on which the call stays,
 * while evaluation takes the root of a decimal {@code x} at once. A real engine value within the
 * double range must agree within {@code 1e-9} relative or {@code 1e-12} absolute; an undefined,
 * non-real or symbolic one needs NaN.
 *
 * <p><b>Each node.</b> Where the whole value disagrees, every variable node is checked locally: its
 * compiled value against the engine's operation applied to the decimals of its children's compiled
 * values at 34 digits, with the same tolerance and NaN rules (a sum's against the magnitudes of its
 * terms, which it rounds in turn), a NaN child needing a NaN node. A circular or inverse circular
 * function of plain arguments in degrees or gradians is evaluated in the mode at the exact
 * argument, as the exact evaluator does ({@code tan(90)} is undefined). A local disagreement fails
 * with the node. When every node agrees, the compiled evaluator did each operation right and the
 * whole disagrees only by the problem's conditioning: the point is ill-conditioned, or it meets an
 * exact quarter turn, or a child overflowed. A sum or product whose compiled value is infinite or
 * NaN while its value is in range, where its finite terms or factors pass the double range on the
 * way ({@code cosh x·(2cosh²x − 2)/sinh x} at 270 in degrees), counts as ill-conditioned too; a
 * finite compiled value that disagrees still fails. A node whose compiled value is NaN while the
 * engine's value is real, through a part the engine finds not real ({@code root(−2, 2)^4} is 4 in
 * the engine, through {@code i√2}), is a legitimate difference of a plotting evaluator over the
 * reals: the point is counted as {@link Outcome#NON_REAL_PATH}, not asserted.
 *
 * <p><b>Bounds.</b> A point whose evaluation would pass {@link #BOUND} ({@link #beyondBounds}) is
 * not evaluated at all, so that what is compared does not depend on the machine's speed.
 */
final class CompileOracle {

  private static final double RELATIVE = 1e-9;
  private static final double ABSOLUTE = 1e-12;
  private static final int DIGITS = 17;

  /**
   * The digits of the local check: {@code numeric} carries 10 guard digits whatever the
   * cancellation, so {@code sec(2^15·10^15 + π/3)} at 17 digits is off by {@code 6e-8}, at 34
   * right.
   */
  private static final int LOCAL_DIGITS = 34;

  /**
   * The bound on the arguments of {@code exp} and powers of {@code e}, on the magnitude of other
   * exponents, and on the decimal exponent of a logarithm's argument, as {@code
   * testing.NumericEvaluator} bounds its own: past it the engine takes seconds to minutes at 34
   * digits ({@code 10^(-1.2e8)} took a minute), and such a point is no useful sample.
   */
  private static final double BOUND = 1e4;

  static final Set<String> CIRCULAR_NAMES =
      Set.of(
          "sin", "cos", "tan", "cot", "sec", "csc", "asin", "acos", "atan", "acot", "asec", "acsc",
          "atan2");
  private static final Set<String> DIRECT = Set.of("sin", "cos", "tan", "cot", "sec", "csc");

  private CompileOracle() {}

  /** How a point compared. */
  enum Outcome {
    /** The compiled value agrees with the engine's, or both have none. */
    AGREE,
    /** The engine's value or a child's compiled value is beyond the double range. */
    OUT_OF_RANGE,
    /** The point would take the engine past {@link #BOUND}: not evaluated. */
    BOUNDED,
    /** A circular function in degrees or gradians met an exact quarter turn. */
    QUARTER_TURN,
    /** Every node agrees locally; the whole disagrees by conditioning. */
    ILL_CONDITIONED,
    /**
     * The engine's value is real only through a non-real part ({@code root(−2, 2)^4} is 4), where
     * the compiled evaluator, over the reals, rightly gives NaN.
     */
    NON_REAL_PATH
  }

  /** The engine's value: a real double, none (undefined or not real), or out of range. */
  private record Value(double value, boolean none, boolean outOfRange) {
    static final Value NONE = new Value(Double.NaN, true, false);
    static final Value OUT = new Value(Double.NaN, false, true);
  }

  /**
   * Compares {@code compiled}, the compilation of {@code e}, with the engine at {@code point}.
   *
   * @throws AssertionError at a node whose compiled value disagrees with the engine's operation
   */
  static Outcome check(
      Expr e, List<Symbol> args, Settings settings, Compiled compiled, double[] point) {
    if (beyondBounds(e, args, settings, point)) {
      return Outcome.BOUNDED;
    }
    double c = compiled.eval(point);
    Value v = whole(e, args, settings, point);
    if (v.outOfRange()) {
      // Beyond the double range the compiled value is infinite, or NaN from an infinite or NaN
      // operand (∞ − ∞). A finite one is right only by conditioning (csc(285π − e^(−855)) is
      // 1e371, while π rounds to give −3.4e14), so every node must agree.
      if (Double.isInfinite(c)
          || Double.isNaN(c) && hasNonFiniteOperand(e, args, settings, point)) {
        return Outcome.OUT_OF_RANGE;
      }
      return locally(e, args, settings, point);
    }
    if (agrees(c, v)) {
      return Outcome.AGREE;
    }
    return locally(e, args, settings, point);
  }

  /**
   * Checks every node locally where the whole disagrees: a quarter turn, an overflow below, or else
   * the point is ill-conditioned.
   *
   * @throws AssertionError at a node whose compiled value disagrees with the engine's operation
   */
  private static Outcome locally(Expr e, List<Symbol> args, Settings settings, double[] point) {
    Local local = new Local(args, settings, point);
    local.check(e);
    if (local.quarterTurn) {
      return Outcome.QUARTER_TURN;
    }
    if (local.nonRealPath) {
      return Outcome.NON_REAL_PATH;
    }
    // An overflow between a node's terms or factors is no evidence either: skipped as an
    // ill-conditioned point, under its cap, though the nodes above it then see an infinite child.
    if (local.intermediateOverflow) {
      return Outcome.ILL_CONDITIONED;
    }
    return local.overflow ? Outcome.OUT_OF_RANGE : Outcome.ILL_CONDITIONED;
  }

  /** Whether an operand of {@code e} that holds an argument compiles to a non-finite value. */
  private static boolean hasNonFiniteOperand(
      Expr e, List<Symbol> args, Settings settings, double[] point) {
    for (Expr op : Operands.of(e)) {
      if (variable(op, args) && !Double.isFinite(at(op, args, settings, point))) {
        return true;
      }
    }
    return false;
  }

  private static boolean agrees(double c, Value v) {
    if (v.none()) {
      return Double.isNaN(c);
    }
    double d = Math.abs(c - v.value());
    return d <= RELATIVE * Math.abs(v.value()) || d <= ABSOLUTE;
  }

  private static Value whole(Expr e, List<Symbol> args, Settings settings, double[] point) {
    Environment env = Environment.EMPTY.withSettings(settings);
    for (int k = 0; k < args.size(); k++) {
      env = env.with(args.get(k).name(), new Definition.Value(decimal(point[k])));
    }
    return inMode(e, env, DIGITS);
  }

  /**
   * Whether evaluating {@code e} at the point would take the engine past {@link #BOUND}, judged
   * before anything large is evaluated: {@code exp}, a power of {@code e} or a hyperbolic function
   * (which the engine forms through {@code exp}) of an argument of modulus above the bound, another
   * power whose exponent's magnitude is above it, or above 1 on a base beyond the double range, a
   * negative base to a non-integer power whose (complex) value has a modulus above the bound, or a
   * logarithm of a value whose decimal exponent is beyond ±bound, which in a double is an infinite
   * one. The parts are judged from the inside out on their compiled values, and each value that
   * bounds the point is first checked against the engine's value of its part, which its own bounds
   * keep cheap: a part the engine finds out of range bounds the point, one it finds within the
   * bound must agree, so a wrongly large compiled value fails rather than bounding the point away.
   * The local check evaluates the same parts on the same values, so the bound covers it too.
   *
   * <p>An integer exponent beyond the {@code int} range is above the bound, so every point of a
   * power that keeps its parity ({@code x^(2^60 + 1)}) is bounded here: {@code CompiledTest} covers
   * those.
   */
  static boolean beyondBounds(Expr e, List<Symbol> args, Settings settings, double[] point) {
    for (Expr op : Operands.of(e)) {
      if (beyondBounds(op, args, settings, point)) {
        return true;
      }
    }
    if (e instanceof Power p) {
      double y = at(p.exponent(), args, settings, point);
      double b = at(p.base(), args, settings, point);
      // A base beyond the double range has a decimal exponent above 308, which a power above 1
      // multiplies past what the engine forms in reasonable time ((x^x)^(3x/2) at 853 took 3 s).
      // A negative base to a non-integer power is complex, NaN here, and a circular function of
      // a large complex value forms exp of its imaginary part (cot(x^(5/2)) at -779 took 15 s):
      // its modulus |b|^y is bounded.
      if (Math.abs(y) > BOUND) {
        return confirmed(p.exponent(), y, args, settings, point);
      }
      if (Math.abs(y) > 1 && Double.isInfinite(b)) {
        return confirmed(p.base(), b, args, settings, point);
      }
      if (b < 0 && y != Math.rint(y) && Math.pow(-b, y) > BOUND) {
        return confirmed(p.base(), b, args, settings, point)
            && confirmed(p.exponent(), y, args, settings, point);
      }
    } else if (e instanceof Apply a) {
      switch (a.head().name()) {
        case "exp", "sinh", "cosh", "tanh", "coth", "sech", "csch" -> {
          Expr arg = a.args().getFirst();
          double u = at(arg, args, settings, point);
          if (Math.abs(u) > BOUND) {
            return confirmed(arg, u, args, settings, point);
          }
        }
        case "ln", "log", "log10", "log2" -> {
          for (Expr arg : a.args()) {
            double u = at(arg, args, settings, point);
            if (Double.isInfinite(u)) {
              return confirmed(arg, u, args, settings, point);
            }
          }
        }
        default -> {}
      }
    }
    return false;
  }

  /**
   * Checks a compiled value that bounds the point against the engine's value of its part: out of
   * range, or within the tolerance, or else every node of the part agrees locally (the compiled
   * value is large by the part's conditioning, as {@code csc(2πx)} is at {@code x = -8.5} where π
   * rounds, or by an overflow below it, as {@code (x^x)^(1/x)} is at 957).
   *
   * @return true
   * @throws AssertionError at a node of the part that disagrees with the engine's operation
   */
  private static boolean confirmed(
      Expr part, double compiled, List<Symbol> args, Settings settings, double[] point) {
    Value v = whole(part, args, settings, point);
    if (!v.outOfRange() && !agrees(compiled, v)) {
      new Local(args, settings, point).check(part);
    }
    return true;
  }

  /**
   * The compiled value of a part at the point; NaN for a part with no real value, such as a complex
   * constant inside a real one ({@code (1 + i)(1 - i)}), which the bounds then do not judge.
   */
  private static double at(Expr part, List<Symbol> args, Settings settings, double[] point) {
    try {
      return Compiled.compile(part, args, settings).eval(point);
    } catch (UnsupportedException | EvaluationException noRealValue) {
      return Double.NaN;
    }
  }

  /** Whether {@code e} is arithmetic of real numbers, unfolded. */
  private static boolean unfolded(Expr e) {
    if (!(e instanceof Sum || e instanceof Product || e instanceof Power)) {
      return false;
    }
    for (Expr op : Operands.of(e)) {
      if (!(op instanceof Num n && n.value() instanceof RealNumeric) && !unfolded(op)) {
        return false;
      }
    }
    return true;
  }

  /**
   * The value of an expression evaluated in {@code env}'s mode, then made numeric. A {@code root}
   * the evaluation leaves (its argument holds a constant that is not a number, {@code root(sin(4)
   * x, 2)}) is first written as the real root it denotes, since {@code numeric} would turn its
   * degree into a decimal and leave the call (the deferred engine item on {@code numeric(root(5,
   * 3))}).
   */
  private static Value inMode(Expr e, Environment env, int digits) {
    try {
      Expr value = Canonical.of(realRoots(Evaluator.evaluate(e, env, false).value()));
      return classify(NumericForm.numeric(value, digits, Cancellation.NEVER));
    } catch (EvaluationException | ArithmeticException outOfRange) {
      return Value.OUT;
    }
  }

  /**
   * A real number is a value; a complex one, {@code undefined}, or a call that stays (on a complex
   * argument, or {@code factorial(2.5)}) is none; arithmetic of real numbers left unfolded is a
   * number the tower could not reach ({@code e^(1e17)}), out of range.
   */
  private static Value classify(Expr numeric) {
    if (unfolded(numeric)) {
      return Value.OUT;
    }
    if (!(numeric instanceof Num n && n.value() instanceof RealNumeric r)) {
      return Value.NONE;
    }
    if (r.isZero()) {
      return new Value(0, false, false);
    }
    BigDecimal magnitude = r.abs().toReal(new MathContext(DIGITS)).value();
    if (magnitude.compareTo(BigDecimal.valueOf(Double.MAX_VALUE)) > 0) {
      return Value.OUT;
    }
    return new Value(r.toDouble(), false, false);
  }

  /** {@code e} with every decimal made the exact rational it denotes. */
  static Expr exactly(Expr e) {
    if (e instanceof Num n && n.value() instanceof Real r) {
      return exact(r.toDouble());
    }
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    List<Expr> out = new ArrayList<>(ops.size());
    for (Expr op : ops) {
      out.add(exactly(op));
    }
    return Operands.rebuild(e, out);
  }

  /** The exact rational a double denotes. */
  static Num exact(double d) {
    BigDecimal b = new BigDecimal(d);
    return b.scale() <= 0
        ? new Num(Int.of(b.toBigIntegerExact()))
        : new Num(Rational.of(b.unscaledValue(), BigInteger.TEN.pow(b.scale())));
  }

  /** The decimal a double denotes, every digit kept. */
  static Num decimal(double d) {
    return d == 0 ? new Num(Real.of(BigDecimal.ZERO)) : new Num(Real.of(new BigDecimal(d)));
  }

  /**
   * {@code e} with every {@code root(u, n)} of a constant integer degree written as the real root:
   * {@code sign(u)·|u|^(1/n)} for an odd {@code n}, and {@code u^(1/n)} for an even one, complex
   * (so none) where the engine's root is undefined.
   */
  static Expr realRoots(Expr e) {
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    List<Expr> out = new ArrayList<>(ops.size());
    for (Expr op : ops) {
      out.add(realRoots(op));
    }
    Expr rebuilt = Operands.rebuild(e, out);
    if (rebuilt instanceof Apply a
        && a.head().name().equals("root")
        && a.args().get(1) instanceof Num n
        && n.value() instanceof Int k
        && k.signum() > 0) {
      Expr u = a.args().getFirst();
      Expr inverse = Num.of(Rational.of(BigInteger.ONE, k.value()));
      return k.value().testBit(0)
          ? Product.of(
              Apply.of(Symbol.of("sign"), u), Power.of(Apply.of(Symbol.of("abs"), u), inverse))
          : Power.of(u, inverse);
    }
    return rebuilt;
  }

  /** Whether {@code e} holds an argument. */
  static boolean variable(Expr e, List<Symbol> args) {
    if (e instanceof Symbol s) {
      return args.contains(s);
    }
    for (Expr op : Operands.of(e)) {
      if (variable(op, args)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether {@code e} is a real number once the arguments are decimals ({@code docs/language.md}
   * §14.2): an argument, a real number, or arithmetic and functions of plain parts that hold an
   * argument; a constant part that is not a number stays symbolic.
   */
  static boolean plain(Expr e, List<Symbol> args) {
    return switch (e) {
      case Num n -> n.value() instanceof RealNumeric;
      case Symbol s -> args.contains(s);
      case Sum _, Product _, Power _, Apply _ ->
          variable(e, args) && Operands.of(e).stream().allMatch(op -> plain(op, args));
      case Constant _,
          mathcanvas.engine.expr.Unit _,
          Bool _,
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
          false;
    };
  }

  /** The local check of every variable node, depth first. */
  private static final class Local {
    private final List<Symbol> args;
    private final Settings settings;
    private final double[] point;
    boolean quarterTurn;
    boolean overflow;
    boolean intermediateOverflow;
    boolean nonRealPath;

    Local(List<Symbol> args, Settings settings, double[] point) {
      this.args = args;
      this.settings = settings;
      this.point = point;
    }

    /** Checks the variable nodes of {@code e} and returns its compiled value. */
    double check(Expr e) {
      double compiled = Compiled.compile(e, args, settings).eval(point);
      if (e instanceof Symbol) {
        return compiled;
      }
      List<Expr> ops = Operands.of(e);
      List<Expr> children = new ArrayList<>(ops.size());
      boolean nanChild = false;
      boolean infiniteChild = false;
      double first = Double.NaN;
      for (Expr op : ops) {
        if (!variable(op, args)) {
          children.add(op);
          continue;
        }
        double v = check(op);
        if (children.isEmpty()) {
          first = v;
        }
        nanChild |= Double.isNaN(v);
        infiniteChild |= Double.isInfinite(v);
        children.add(Double.isFinite(v) ? decimal(v) : op);
      }
      if (nanChild) {
        assertLocal(Double.isNaN(compiled), e, "a NaN operand gives NaN", compiled, Value.NONE);
        return compiled;
      }
      if (infiniteChild) {
        overflow = true;
        return compiled;
      }
      Expr local = Operands.rebuild(e, children);
      Value v;
      if (settings.angleMode() != AngleMode.RADIANS
          && e instanceof Apply a
          && CIRCULAR_NAMES.contains(a.head().name())
          && a.args().stream().allMatch(op -> plain(op, args))) {
        v = inMode(exactly(local), Environment.EMPTY.withSettings(settings), LOCAL_DIGITS);
        if (DIRECT.contains(a.head().name())
            && first % (settings.angleMode().halfTurn() / 2.0) == 0) {
          quarterTurn = true;
        }
      } else {
        v = inMode(local, Environment.EMPTY, LOCAL_DIGITS);
      }
      if (v.outOfRange()) {
        overflow = true;
        return compiled;
      }
      if (!Double.isFinite(compiled) && !v.none() && overflowsInBetween(e, children)) {
        // The node's value is in range, but accumulating its terms or factors passed it on the way
        // (cosh(270)³ before the division by sinh 270): no evidence about the compilation.
        intermediateOverflow = true;
        return compiled;
      }
      if (Double.isNaN(compiled) && !v.none() && throughNonReal(e)) {
        // The node is real only through a part that is not (root(−2, 2)^4 through i√2): a
        // plotting evaluator over the reals rightly has no value there.
        nonRealPath = true;
        return compiled;
      }
      double terms = 0;
      for (Expr child : children) {
        terms += Math.abs(Compiled.compile(child, args, settings).eval(point));
      }
      assertLocal(agreesLocally(e, compiled, v, terms), e, "the node", compiled, v);
      return compiled;
    }

    /**
     * Whether a proper part of {@code e} takes a value that is not real at the point, as the engine
     * evaluates it in the mode with each argument bound to its decimal: a real function outside its
     * real domain ({@code root(−2, 2)}, {@code log(−1)}, {@code asin(2)}, a negative base to a
     * non-integer power) takes a complex value there.
     */
    private boolean throughNonReal(Expr e) {
      Environment env = Environment.EMPTY.withSettings(settings);
      for (int k = 0; k < args.size(); k++) {
        env = env.with(args.get(k).name(), new Definition.Value(decimal(point[k])));
      }
      return throughNonReal(e, env);
    }

    private static boolean throughNonReal(Expr e, Environment env) {
      for (Expr op : Operands.of(e)) {
        if (nonReal(op, env) || throughNonReal(op, env)) {
          return true;
        }
      }
      return false;
    }

    /** Whether the engine's value of {@code e} in {@code env} is a number that is not real. */
    private static boolean nonReal(Expr e, Environment env) {
      try {
        Expr value = Canonical.of(realRoots(Evaluator.evaluate(e, env, false).value()));
        return NumericForm.numeric(value, DIGITS, Cancellation.NEVER) instanceof Num n
            && !(n.value() instanceof RealNumeric);
      } catch (EvaluationException | ArithmeticException noValue) {
        return false;
      }
    }

    /**
     * Whether accumulating a sum's terms or a product's factors, at their compiled values, can pass
     * the double range in some order though each is finite: the magnitudes of the terms add past
     * it, or those of the factors above 1 multiply past it.
     */
    private boolean overflowsInBetween(Expr e, List<Expr> children) {
      if (!(e instanceof Sum || e instanceof Product)) {
        return false;
      }
      double total = 0;
      for (Expr child : children) {
        double v = Math.abs(Compiled.compile(child, args, settings).eval(point));
        total += e instanceof Sum ? v : Math.max(0, Math.log(v));
      }
      return e instanceof Sum ? total > Double.MAX_VALUE : total > Math.log(Double.MAX_VALUE);
    }

    /**
     * Whether a node agrees: a sum within the tolerance of the sum of its terms' magnitudes, since
     * adding them in turn rounds at each step; any other node within the tolerance of its value.
     */
    private static boolean agreesLocally(Expr e, double compiled, Value v, double terms) {
      if (e instanceof Sum && !v.none()) {
        return Math.abs(compiled - v.value()) <= RELATIVE * terms || agrees(compiled, v);
      }
      return agrees(compiled, v);
    }

    private void assertLocal(boolean ok, Expr e, String what, double compiled, Value v) {
      if (!ok) {
        throw new AssertionError(
            what
                + " disagrees at "
                + Arrays.toString(point)
                + " in "
                + settings.angleMode()
                + ": "
                + e
                + " compiled "
                + compiled
                + ", engine "
                + (v.none() ? "none" : v.value()));
      }
    }
  }
}
