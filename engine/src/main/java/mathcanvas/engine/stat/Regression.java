package mathcanvas.engine.stat;

import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.linalg.Linalg;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.simplify.Simplifier;
import mathcanvas.engine.stat.Data.Sample;

/**
 * Regression ({@code docs/language.md} §14.25): least squares of {@code y} on {@code x}. A
 * polynomial fit sets up the normal equations {@code XᵀX β = Xᵀy} with the columns {@code xⁿ, …, x,
 * 1} and solves them through the linear algebra's {@code linsolve}, whose steps are spliced into
 * the narration; on exact data the coefficients, {@code r²} and {@code r} are exact, and on decimal
 * data they are solved exactly from the rationals the decimals denote and rounded once. The
 * exponential, logarithmic and power fits are least squares of their linear forms ({@code ln y} on
 * {@code x}, {@code y} on {@code ln x}, {@code ln y} on {@code ln x}), numeric at the precision,
 * with the {@code r²} of the linear form. The value is the list {@code [y = …, r² = …]}, with
 * {@code r = …} for the line.
 */
final class Regression {

  /** The rule set of the steps, narrated by {@link StatisticsNarrator}. */
  static final String RULE_SET = "reg";

  /** The steps. */
  static final Set<String> STEPS = Set.of("normal", "exact", "fit", "linearise");

  /** The polynomial fits, with their degrees. */
  static final Map<String, Integer> DEGREES =
      Map.of("linreg", 1, "quadreg", 2, "cubicreg", 3, "quartreg", 4);

  /** The functions this class evaluates. */
  static final Set<String> NAMES =
      Set.of(
          "linreg",
          "quadreg",
          "cubicreg",
          "quartreg",
          "polyreg",
          "expreg",
          "lnreg",
          "powreg",
          "residuals",
          "predict");

  /** The largest degree of {@code polyreg}. */
  static final int MAX_DEGREE = 10;

  /** The guard digits of the linearised fits. */
  private static final int GUARD = 10;

  private static final Symbol X = Symbol.of("x");
  private static final Symbol Y = Symbol.of("y");

  /**
   * A value, its steps, and whether it is numeric.
   *
   * @param result the value and its steps
   * @param numeric whether it was computed numerically
   */
  record Value(Rewriter.Result result, boolean numeric) {}

  private final Context ctx;
  private final Arith ar;
  private final String name;

  private Regression(String name, Context ctx) {
    this.ctx = ctx;
    this.ar = new Arith(ctx);
    this.name = name;
  }

  /**
   * Evaluates a call whose arguments are evaluated.
   *
   * @param call the call
   * @param ctx the precision, trace, numerics and cancellation
   * @return the value, or empty when the call stays
   * @throws IllegalArgumentException with the language's message for a wrong argument
   */
  static Optional<Value> apply(Apply call, Context ctx) {
    return new Regression(call.head().name(), ctx).evaluate(call);
  }

  private Optional<Value> evaluate(Apply call) {
    List<Expr> args = call.args();
    switch (name) {
      case "predict" -> {
        Optional<Expr> fit = fitted(args.getFirst());
        return fit.map(f -> silent(at(f, args.get(1))));
      }
      case "residuals" -> {
        Optional<Expr> fit = fitted(args.get(2));
        Optional<List<Sample>> pairs = Data.pairs(name, args.subList(0, 2), 1, ctx);
        if (fit.isEmpty() || pairs.isEmpty()) {
          return Optional.empty();
        }
        List<Expr> out = new ArrayList<>();
        Sample xs = pairs.get().get(0);
        Sample ys = pairs.get().get(1);
        boolean decimal = xs.decimal() || ys.decimal() || Data.holdsDecimal(fit.get());
        for (int i = 0; i < xs.entries().size(); i++) {
          Expr r = ar.sub(ys.entries().get(i).value(), at(fit.get(), xs.entries().get(i).value()));
          out.add(decimal ? numeric(r) : r);
        }
        return Optional.of(silent(new Vector(out)));
      }
      default -> {}
    }
    int degree;
    List<Expr> data = args;
    if (name.equals("polyreg")) {
      data = args.subList(0, 2);
      Optional<Data.Entry> n = Data.real(name, args.get(2), "degree", ctx);
      if (n.isEmpty()) {
        return Optional.empty();
      }
      if (!(n.get().key() instanceof Int k)
          || k.signum() <= 0
          || k.value().compareTo(BigInteger.valueOf(MAX_DEGREE)) > 0) {
        throw new IllegalArgumentException(
            "polyreg's degree must be an integer from 1 to " + MAX_DEGREE);
      }
      degree = k.value().intValue();
    } else {
      degree = DEGREES.getOrDefault(name, 1);
    }
    if (data.size() > 2) {
      throw new IllegalArgumentException(name + " takes the data only");
    }
    Optional<List<Sample>> pairs = Data.pairs(name, data, 2, ctx);
    if (pairs.isEmpty()) {
      return Optional.empty();
    }
    Sample xs = pairs.get().get(0);
    Sample ys = pairs.get().get(1);
    Record steps = new Record(call, ctx);
    if (DEGREES.containsKey(name) || name.equals("polyreg")) {
      return Optional.of(new Value(polynomial(xs, ys, degree, steps), false));
    }
    return Optional.of(new Value(linearised(xs, ys, steps), true));
  }

  private Value silent(Expr value) {
    return new Value(new Rewriter.Result(value, mathcanvas.engine.rule.Trace.NONE), false);
  }

  /** The fitted relation's right side: of {@code y = …}, or of a list whose first item it is. */
  private Optional<Expr> fitted(Expr fit) {
    Expr first = fit instanceof ListExpr l && !l.items().isEmpty() ? l.items().getFirst() : fit;
    if (first instanceof Relation r && r.op() == Relation.Op.EQ && r.left().equals(Y)) {
      return Optional.of(r.right());
    }
    if (Data.holdsSymbol(fit) && !(fit instanceof ListExpr) && !(fit instanceof Relation)) {
      return Optional.empty();
    }
    throw new IllegalArgumentException(
        name + " needs a fit: a relation y = … or the list a fit gives");
  }

  /** The fitted right side at {@code x}: folded to a number when it is closed. */
  private Expr at(Expr fit, Expr x) {
    Expr value = ar.canon(substitute(fit, x));
    if (Data.holdsSymbol(value) || value instanceof Num) {
      return value;
    }
    return Data.holdsDecimal(value) ? numeric(value) : value;
  }

  private static Expr substitute(Expr e, Expr x) {
    if (e.equals(X)) {
      return x;
    }
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    List<Expr> out = new ArrayList<>(ops.size());
    for (Expr o : ops) {
      out.add(substitute(o, x));
    }
    return Operands.rebuild(e, out);
  }

  private Expr numeric(Expr value) {
    MathContext mc = ctx.precision();
    Optional<Numeric> v = ctx.numerics().value(value, mc);
    return v.isPresent() ? Num.of(v.get().toReal(mc)) : value;
  }

  // The polynomial fits.

  private Rewriter.Result polynomial(Sample xs, Sample ys, int degree, Record steps) {
    int size = degree + 1;
    List<Expr> x = xs.values();
    List<Expr> y = ys.values();
    // the sums Σ xᵏ for k up to 2n, and Σ xᵏ y for k up to n
    List<Expr> powerSums = new ArrayList<>();
    for (int k = 0; k <= 2 * degree; k++) {
      List<Expr> terms = new ArrayList<>();
      for (Expr xi : x) {
        terms.add(ar.pow(xi, k));
      }
      powerSums.add(ar.sum(terms));
    }
    List<Expr> moments = new ArrayList<>();
    for (int k = 0; k <= degree; k++) {
      List<Expr> terms = new ArrayList<>();
      for (int i = 0; i < x.size(); i++) {
        terms.add(ar.mul(ar.pow(x.get(i), k), y.get(i)));
      }
      moments.add(ar.sum(terms));
    }
    // the columns xⁿ, …, x, 1: entry (i, j) is Σ x^(2n − i − j)
    List<List<Expr>> rows = new ArrayList<>();
    List<Expr> right = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      List<Expr> row = new ArrayList<>();
      for (int j = 0; j < size; j++) {
        row.add(powerSums.get(2 * degree - i - j));
      }
      rows.add(row);
      right.add(moments.get(degree - i));
    }
    Matrix normal = new Matrix(rows);
    Vector rhs = new Vector(right);
    boolean decimal = xs.decimal() || ys.decimal();
    TreeMap<String, Expr> values = new TreeMap<>();
    values.put("model", new Relation(Relation.Op.EQ, Y, model(degree)));
    values.put("n", Arith.num(xs.n()));
    Apply solve = Apply.of(Symbol.of("linsolve"), normal, rhs);
    Rewriter.Result solved;
    if (decimal) {
      steps.step(
          RULE_SET,
          "normal",
          Apply.of(Symbol.of("linsolve"), mapNumeric(normal), mapNumeric(rhs)),
          values);
      solved = Linalg.apply(solve, ctx.withTrace(false));
    } else {
      steps.step(RULE_SET, "normal", solve, values);
      solved = Linalg.apply(solve, ctx);
    }
    if (!(solved.expr() instanceof Vector beta) || Data.holdsSymbol(beta)) {
      throw new IllegalArgumentException(name + " needs at least " + size + " distinct x values");
    }
    if (decimal) {
      steps.step(RULE_SET, "exact", mapNumeric(beta), new TreeMap<>());
    } else {
      steps.splice(solved);
    }
    List<Expr> coefficients = beta.items();
    Expr polynomial = polynomial(coefficients);
    // r² = 1 − SSres/SStot
    Expr mean = ar.div(ar.sum(y), Arith.num(ys.n()));
    List<Expr> residuals = new ArrayList<>();
    List<Expr> deviations = new ArrayList<>();
    for (int i = 0; i < x.size(); i++) {
      residuals.add(ar.pow(ar.sub(y.get(i), ar.canon(substitute(polynomial, x.get(i)))), 2));
      deviations.add(ar.pow(ar.sub(y.get(i), mean), 2));
    }
    Expr r2 = ar.sub(Num.ONE, ar.div(ar.sum(residuals), ar.sum(deviations)));
    List<Expr> items = new ArrayList<>();
    items.add(new Relation(Relation.Op.EQ, Y, decimal ? mapNumeric(polynomial) : polynomial));
    items.add(new Relation(Relation.Op.EQ, ar.pow(Symbol.of("r"), 2), out(r2, decimal)));
    if (degree == 1) {
      Expr slope = coefficients.getFirst();
      int sign = sign(slope);
      Expr r =
          r2.equals(Constant.UNDEFINED)
              ? Constant.UNDEFINED
              : sign == 0 ? Num.ZERO : ar.mul(Num.of(sign), ar.sqrt(r2));
      items.add(new Relation(Relation.Op.EQ, Symbol.of("r"), out(r, decimal)));
    }
    TreeMap<String, Expr> fitValues = new TreeMap<>();
    fitValues.put("r2", out(r2, decimal));
    steps.step(RULE_SET, "fit", new ListExpr(items), fitValues);
    return steps.result();
  }

  /** {@code a xⁿ + b xⁿ⁻¹ + …} with the letters of the TI calculators. */
  private Expr model(int degree) {
    List<Expr> terms = new ArrayList<>();
    for (int i = 0; i <= degree; i++) {
      terms.add(
          Product.of(Symbol.of(String.valueOf((char) ('a' + i))), Power.of(X, Num.of(degree - i))));
    }
    return ar.canon(Sum.of(terms));
  }

  private Expr polynomial(List<Expr> coefficients) {
    int degree = coefficients.size() - 1;
    List<Expr> terms = new ArrayList<>();
    for (int i = 0; i <= degree; i++) {
      terms.add(ar.mul(coefficients.get(i), ar.pow(X, degree - i)));
    }
    return ar.sum(terms);
  }

  private int sign(Expr e) {
    Optional<Numeric> v = ctx.numerics().value(e, ctx.precision());
    if (e instanceof Num n && n.value() instanceof RealNumeric r) {
      return r.signum();
    }
    return v.isPresent() && v.get() instanceof RealNumeric r ? r.signum() : 0;
  }

  /** A value as the result shows it: simplified on exact data, a decimal on decimal data. */
  private Expr out(Expr value, boolean decimal) {
    return decimal ? numeric(value) : Simplifier.simplify(value, ctx.withTrace(false)).expr();
  }

  /** Every closed number in a vector, a matrix or a polynomial made a decimal at the precision. */
  private Expr mapNumeric(Expr e) {
    if (!Data.holdsSymbol(e)) {
      if (e instanceof Vector || e instanceof Matrix) {
        List<Expr> ops = Operands.of(e);
        List<Expr> out = new ArrayList<>(ops.size());
        for (Expr o : ops) {
          out.add(mapNumeric(o));
        }
        return Operands.rebuild(e, out);
      }
      return numeric(e);
    }
    if (e instanceof Power p) {
      // an exponent of x stays an integer
      return ar.canon(Power.of(mapNumeric(p.base()), p.exponent()));
    }
    List<Expr> ops = Operands.of(e);
    List<Expr> out = new ArrayList<>(ops.size());
    for (Expr o : ops) {
      out.add(mapNumeric(o));
    }
    return ar.canon(Operands.rebuild(e, out));
  }

  // The linearised fits.

  private Rewriter.Result linearised(Sample xs, Sample ys, Record steps) {
    // the logarithms and the sums at 10 guard digits, the coefficients rounded once at the end
    MathContext mc = ctx.precision();
    Context w =
        new Context(
            ctx.assumptions(),
            new MathContext(mc.getPrecision() + GUARD, RoundingMode.HALF_EVEN),
            false,
            ctx.rewriteCap(),
            ctx.providers(),
            ctx.numerics(),
            ctx.cancellation());
    Regression wide = new Regression(name, w);
    Arith wa = wide.ar;
    List<Expr> x = xs.values();
    List<Expr> y = ys.values();
    List<Expr> u = new ArrayList<>();
    List<Expr> v = new ArrayList<>();
    boolean logX = name.equals("lnreg") || name.equals("powreg");
    boolean logY = name.equals("expreg") || name.equals("powreg");
    for (int i = 0; i < x.size(); i++) {
      if (logX && xs.entries().get(i).key().signum() <= 0) {
        throw new IllegalArgumentException(name + " needs positive x values");
      }
      if (logY && ys.entries().get(i).key().signum() <= 0) {
        throw new IllegalArgumentException(name + " needs positive y values");
      }
      u.add(wide.numeric(logX ? log(x.get(i)) : x.get(i)));
      v.add(wide.numeric(logY ? log(y.get(i)) : y.get(i)));
    }
    // least squares of v on u
    Expr n = Arith.num(xs.n());
    Expr mu = wa.div(wa.sum(u), n);
    Expr mv = wa.div(wa.sum(v), n);
    List<Expr> suv = new ArrayList<>();
    List<Expr> suu = new ArrayList<>();
    List<Expr> svv = new ArrayList<>();
    for (int i = 0; i < u.size(); i++) {
      Expr du = wa.sub(u.get(i), mu);
      Expr dv = wa.sub(v.get(i), mv);
      suv.add(wa.mul(du, dv));
      suu.add(wa.mul(du, du));
      svv.add(wa.mul(dv, dv));
    }
    Expr sxx = wa.sum(suu);
    if (sxx instanceof Num s && s.value().isZero()) {
      throw new IllegalArgumentException(name + " needs at least 2 distinct x values");
    }
    Expr slope = wa.div(wa.sum(suv), sxx);
    Expr intercept = wa.sub(mv, wa.mul(slope, mu));
    Expr r2 = numeric(wa.div(wa.pow(wa.sum(suv), 2), wa.mul(sxx, wa.sum(svv))));
    Expr equation =
        switch (name) {
          case "expreg" ->
              Product.of(
                  numeric(wide.numeric(exp(intercept))),
                  Power.of(numeric(wide.numeric(exp(slope))), X));
          case "lnreg" ->
              Sum.of(numeric(intercept), Product.of(numeric(slope), Apply.of(Symbol.of("log"), X)));
          default -> Product.of(numeric(wide.numeric(exp(intercept))), Power.of(X, numeric(slope)));
        };
    TreeMap<String, Expr> values = new TreeMap<>();
    values.put("model", new Relation(Relation.Op.EQ, Y, linearModel()));
    values.put("linear", linearForm());
    values.put("digits", Num.of(ctx.precision().getPrecision()));
    steps.step(
        RULE_SET,
        "linearise",
        new ListExpr(
            List.of(
                new Relation(Relation.Op.EQ, Y, ar.canon(equation)),
                new Relation(Relation.Op.EQ, ar.pow(Symbol.of("r"), 2), r2))),
        values);
    return steps.result();
  }

  private Expr linearModel() {
    Symbol a = Symbol.of("a");
    Symbol b = Symbol.of("b");
    return switch (name) {
      case "expreg" -> Product.of(a, Power.of(b, X));
      case "lnreg" -> Sum.of(a, Product.of(b, Apply.of(Symbol.of("log"), X)));
      default -> Product.of(a, Power.of(X, b));
    };
  }

  private Expr linearForm() {
    Expr lny = Apply.of(Symbol.of("log"), Y);
    Expr lnx = Apply.of(Symbol.of("log"), X);
    Expr lna = Apply.of(Symbol.of("log"), Symbol.of("a"));
    Expr lnb = Apply.of(Symbol.of("log"), Symbol.of("b"));
    return switch (name) {
      case "expreg" -> new Relation(Relation.Op.EQ, lny, Sum.of(lna, Product.of(X, lnb)));
      case "lnreg" ->
          new Relation(Relation.Op.EQ, Y, Sum.of(Symbol.of("a"), Product.of(Symbol.of("b"), lnx)));
      default -> new Relation(Relation.Op.EQ, lny, Sum.of(lna, Product.of(Symbol.of("b"), lnx)));
    };
  }

  private static Expr log(Expr e) {
    return Apply.of(Symbol.of("log"), e);
  }

  private static Expr exp(Expr e) {
    return Power.of(Constant.E, e);
  }
}
