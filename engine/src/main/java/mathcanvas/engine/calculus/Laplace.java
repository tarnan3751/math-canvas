package mathcanvas.engine.calculus;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.algebra.RationalFunction;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.builtin.Builtins;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Match;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;
import mathcanvas.engine.rule.Trace;
import mathcanvas.engine.simplify.Simplifier;

/**
 * Laplace transforms by table ({@code docs/language.md} §14.21): the rule sets {@code laplace}, on
 * calls {@code laplace(f, t, s)}, and {@code ilaplace}, on calls {@code ilaplace(F, s, t)}, through
 * the matcher, innermost first, each rule replacing a call with transforms of smaller expressions
 * or with none.
 *
 * <p>{@code laplace}: {@code linear} (a sum term by term), {@code constant-multiple} (a factor free
 * of {@code t} out), {@code table} (a constant, {@code tⁿ}, {@code e^(at + b)}, and {@code sin},
 * {@code cos}, {@code sinh}, {@code cosh} of {@code bt + c}), {@code shift} ({@code e^(at + b)·g}
 * is {@code e^b} times {@code g}'s transform at {@code s − a}), {@code multiply-t} ({@code tⁿ·g} is
 * {@code (−1)ⁿ dⁿ/dsⁿ} of {@code g}'s transform), {@code derivative} (of {@code dⁿy/dtⁿ} for an
 * unknown function {@code y(t)}: {@code sⁿ L{y} − Σ s^(n−1−k) y⁽ᵏ⁾(0)}, the initial values written
 * {@code y(0)}, {@code y'(0)}, …, the applied unknown and its primed heads). {@code ilaplace}:
 * {@code partial-fractions} (a rational function of {@code s} by {@code apart}), {@code linear},
 * {@code constant-multiple}, and {@code table} ({@code c/(s − a)ⁿ} and {@code (As + B)/(s² + ps +
 * q)} with {@code q − p²/4} not zero and of a known sign). All or nothing: a transform any part of
 * which no rule takes stays whole, with the note {@code laplace.none} or {@code ilaplace.none}; a
 * transform of an unknown function {@code laplace(y(t), t, s)} may stay inside a result. Angles are
 * radians.
 */
public final class Laplace {

  private Laplace() {}

  /** The rule set of the transform. */
  public static final RuleSet LAPLACE = new RuleSet("laplace", laplaceRules());

  /** The rule set of the inverse transform. */
  public static final RuleSet ILAPLACE = new RuleSet("ilaplace", ilaplaceRules());

  /** The terminal notes. */
  public static final List<String> NOTES = List.of("laplace.none", "ilaplace.none");

  /**
   * The transform {@code laplace(f, t, s)} or {@code ilaplace(F, s, t)}: the rule set to the
   * fixpoint, then the standard simplifier; the call, canonical, with its note when a part stays.
   *
   * @param call the call, its first argument evaluated, its variables names
   * @param context the assumptions (without facts about the variables), precision, trace, cap
   * @return the value and its trace
   */
  public static Rewriter.Result of(Apply call, Context context) {
    boolean inverse = call.head().name().equals("ilaplace");
    Expr start = Canonical.of(call, context.precision());
    Rewriter.Result r = Rewriter.rewrite(start, inverse ? ILAPLACE : LAPLACE, context);
    if (r.expr().equals(start) || !finished(r.expr())) {
      if (!context.traceEnabled()) {
        return new Rewriter.Result(start, Trace.NONE);
      }
      Rewrite note =
          new Rewrite(
              start, start, inverse ? "ilaplace" : "laplace", "none", Path.ROOT, Bindings.EMPTY);
      return new Rewriter.Result(start, new Trace(start, start, List.of(note), List.of()));
    }
    Rewriter.Result s = Simplifier.simplify(r.expr(), context);
    Trace trace = context.traceEnabled() ? Trace.concat(r.trace(), s.trace()) : Trace.NONE;
    return new Rewriter.Result(s.expr(), trace);
  }

  /**
   * Whether no transform is left but the transform of an unknown function, {@code laplace(y(t), t,
   * s)}.
   */
  private static boolean finished(Expr e) {
    if (e instanceof Apply a && (isCall(a, "laplace") || isCall(a, "ilaplace"))) {
      return isCall(a, "laplace")
          && a.args().getFirst() instanceof Apply y
          && Builtins.lookup(y.head().name()).isEmpty()
          && y.args().equals(List.of(a.args().get(1)));
    }
    for (Expr o : Operands.of(e)) {
      if (!finished(o)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isCall(Expr e, String head) {
    return e instanceof Apply a
        && a.head().name().equals(head)
        && a.args().size() == 3
        && a.args().get(1) instanceof Symbol;
  }

  private static Rule rule(String name, String head, RuleFunction f) {
    return Rule.computed(
        name,
        Pattern.parse("c_").where("c_", e -> isCall(e, head)),
        m -> {
          Apply c = (Apply) m.bindings().get("c_");
          return f.apply(c.args().get(0), (Symbol) c.args().get(1), c.args().get(2), m);
        });
  }

  /** A rule's replacement from the call's three arguments. */
  @FunctionalInterface
  private interface RuleFunction {
    Optional<Expr> apply(Expr f, Symbol v, Expr w, Match m);
  }

  private static Expr call(String head, Expr f, Expr v, Expr w) {
    return Apply.of(Symbol.of(head), f, v, w);
  }

  // The transform.

  private static List<Rule> laplaceRules() {
    List<Rule> rules = new ArrayList<>();
    rules.add(
        rule(
            "linear",
            "laplace",
            (f, t, s, m) ->
                f instanceof Sum sum
                    ? Optional.of(
                        Sum.of(sum.terms().stream().map(u -> call("laplace", u, t, s)).toList()))
                    : Optional.empty()));
    rules.add(rule("constant-multiple", "laplace", (f, t, s, m) -> multiple(f, t, s, "laplace")));
    rules.add(rule("table", "laplace", Laplace::table));
    rules.add(rule("shift", "laplace", Laplace::shift));
    rules.add(rule("multiply-t", "laplace", Laplace::multiplyT));
    rules.add(rule("derivative", "laplace", Laplace::derivative));
    return rules;
  }

  /** {@code c·g} → {@code c·T(g)} for the factors {@code c} free of the variable. */
  private static Optional<Expr> multiple(Expr f, Symbol v, Expr w, String head) {
    if (!(f instanceof Product p)) {
      return Optional.empty();
    }
    List<Expr> constant = new ArrayList<>();
    List<Expr> rest = new ArrayList<>();
    for (Expr factor : p.factors()) {
      (Free.of(factor, v) ? constant : rest).add(factor);
    }
    if (constant.isEmpty() || rest.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(Product.of(product(constant), call(head, product(rest), v, w)));
  }

  /** The product of the factors, the factor itself when there is one. */
  private static Expr product(List<Expr> factors) {
    return factors.size() == 1 ? factors.getFirst() : Product.of(factors);
  }

  /** {@code [a, b]} when {@code e = a·t + b} with {@code a, b} free of {@code t}. */
  private static Optional<Expr[]> linear(Expr e, Symbol t, Context ctx) {
    Expr a = Calculus.differentiate(e, t, 1, ctx.withTrace(false)).expr();
    if (!Free.of(a, t)) {
      return Optional.empty();
    }
    Expr b = Canonical.of(Sum.of(e, Product.of(Num.MINUS_ONE, a, t)), ctx.precision());
    b = Simplifier.simplify(b, ctx.withTrace(false)).expr();
    return Free.of(b, t) ? Optional.of(new Expr[] {a, b}) : Optional.empty();
  }

  private static Expr minus(Expr a, Expr b) {
    return Sum.of(a, Product.of(Num.MINUS_ONE, b));
  }

  private static Expr over(Expr a, Expr b) {
    return Product.of(a, Power.of(b, Num.MINUS_ONE));
  }

  private static Expr squared(Expr a) {
    return Power.of(a, Num.of(2));
  }

  private static Optional<Expr> table(Expr f, Symbol t, Expr s, Match m) {
    Context ctx = m.context();
    if (Free.of(f, t)) {
      return Calculus.isScalar(f) ? Optional.of(over(f, s)) : Optional.empty();
    }
    if (f.equals(t)) {
      return Optional.of(over(Num.ONE, squared(s)));
    }
    if (f instanceof Power p && p.base().equals(t) && natural(p.exponent()) != null) {
      BigInteger n = natural(p.exponent());
      return Optional.of(over(factorial(n), Power.of(s, Num.of(Int.of(n.add(BigInteger.ONE))))));
    }
    if (f instanceof Power p && p.base().equals(Constant.E)) {
      return linear(p.exponent(), t, ctx)
          .map(ab -> over(Power.of(Constant.E, ab[1]), minus(s, ab[0])));
    }
    if (f instanceof Apply a && a.args().size() == 1) {
      String name = a.head().name();
      Optional<Expr[]> ab = linear(a.args().getFirst(), t, ctx);
      if (ab.isEmpty()) {
        return Optional.empty();
      }
      Expr b = ab.get()[0];
      Expr c = ab.get()[1];
      Expr circle = Sum.of(squared(s), squared(b));
      Expr hyperbola = minus(squared(s), squared(b));
      if (c.equals(Num.ZERO)) {
        // the table's own rows, sin(bt) → b/(s² + b²) and the others
        return switch (name) {
          case "sin" -> Optional.of(over(b, circle));
          case "cos" -> Optional.of(over(s, circle));
          case "sinh" -> Optional.of(over(b, hyperbola));
          case "cosh" -> Optional.of(over(s, hyperbola));
          case "exp" -> Optional.of(over(Num.ONE, minus(s, b)));
          default -> Optional.empty();
        };
      }
      return switch (name) {
        case "sin" ->
            Optional.of(
                over(Sum.of(Product.of(b, fn("cos", c)), Product.of(s, fn("sin", c))), circle));
        case "cos" ->
            Optional.of(
                over(minus(Product.of(s, fn("cos", c)), Product.of(b, fn("sin", c))), circle));
        case "sinh" ->
            Optional.of(
                over(
                    Sum.of(Product.of(b, fn("cosh", c)), Product.of(s, fn("sinh", c))), hyperbola));
        case "cosh" ->
            Optional.of(
                over(minus(Product.of(s, fn("cosh", c)), Product.of(b, fn("sinh", c))), hyperbola));
        case "exp" -> Optional.of(over(Power.of(Constant.E, c), minus(s, b)));
        default -> Optional.empty();
      };
    }
    return Optional.empty();
  }

  private static Expr fn(String name, Expr x) {
    return Apply.of(Symbol.of(name), x);
  }

  /** {@code n!} as a number. */
  private static Expr factorial(BigInteger n) {
    BigInteger f = BigInteger.ONE;
    for (BigInteger k = BigInteger.TWO; k.compareTo(n) <= 0; k = k.add(BigInteger.ONE)) {
      f = f.multiply(k);
    }
    return Num.of(Int.of(f));
  }

  /** The integer value of a positive integer number, or null. */
  private static BigInteger natural(Expr e) {
    return e instanceof Num n
            && n.value() instanceof Int k
            && k.signum() > 0
            && k.value().bitLength() < 16
        ? k.value()
        : null;
  }

  /** {@code e^(at + b)·g} → {@code e^b·T(g)(s − a)}, {@code a} not zero. */
  private static Optional<Expr> shift(Expr f, Symbol t, Expr s, Match m) {
    if (!(f instanceof Product p)) {
      return Optional.empty();
    }
    List<Expr> factors = p.factors();
    for (int k = 0; k < factors.size(); k++) {
      if (factors.get(k) instanceof Power e && e.base().equals(Constant.E) && !Free.of(e, t)) {
        Optional<Expr[]> ab = linear(e.exponent(), t, m.context());
        if (ab.isEmpty() || ab.get()[0].equals(Num.ZERO)) {
          continue;
        }
        List<Expr> rest = new ArrayList<>(factors);
        rest.remove(k);
        Expr g = product(rest);
        return Optional.of(
            Product.of(
                Power.of(Constant.E, ab.get()[1]), call("laplace", g, t, minus(s, ab.get()[0]))));
      }
    }
    return Optional.empty();
  }

  /** The highest power of {@code t} {@code multiply-t} takes; beyond it the transform stays. */
  static final int MAX_T_POWER = 10;

  /** {@code tⁿ·g} → {@code (−1)ⁿ dⁿ/dsⁿ T(g)}, the inner transform finished in a fresh variable. */
  private static Optional<Expr> multiplyT(Expr f, Symbol t, Expr s, Match m) {
    if (!(f instanceof Product p)) {
      return Optional.empty();
    }
    List<Expr> factors = p.factors();
    for (int k = 0; k < factors.size(); k++) {
      Expr factor = factors.get(k);
      BigInteger n =
          factor.equals(t)
              ? BigInteger.ONE
              : factor instanceof Power pw && pw.base().equals(t) ? natural(pw.exponent()) : null;
      if (n == null) {
        continue;
      }
      List<Expr> rest = new ArrayList<>(factors);
      rest.remove(k);
      Expr g = product(rest);
      Symbol sigma = fresh(f, s);
      Context quiet = m.context().withTrace(false);
      Expr inner =
          Rewriter.rewrite(
                  Canonical.of(call("laplace", g, t, sigma), quiet.precision()), LAPLACE, quiet)
              .expr();
      if (!finished(inner) || contains(inner, "laplace")) {
        return Optional.empty();
      }
      if (n.intValue() > MAX_T_POWER) {
        return Optional.empty();
      }
      // one derivative at a time, each simplified, so the n-th stays small
      Expr d = inner;
      for (int order = 0; order < n.intValue(); order++) {
        d = Simplifier.simplify(Calculus.differentiate(d, sigma, 1, quiet).expr(), quiet).expr();
      }
      Expr at = IntegrateRules.replace(d, sigma, s, quiet);
      return Optional.of(Product.of(Power.of(Num.MINUS_ONE, Num.of(Int.of(n))), at));
    }
    return Optional.empty();
  }

  /** {@code dⁿy/dtⁿ} of an unknown {@code y(t)} → {@code sⁿ L{y} − Σ s^(n−1−k) y⁽ᵏ⁾(0)}. */
  private static Optional<Expr> derivative(Expr f, Symbol t, Expr s, Match m) {
    if (!(f instanceof Derivative d)
        || !d.var().equals(t)
        || !(d.expr() instanceof Apply y)
        || Builtins.lookup(y.head().name()).isPresent()
        || !y.args().equals(List.of(t))) {
      return Optional.empty();
    }
    int n = d.order();
    List<Expr> terms = new ArrayList<>();
    terms.add(Product.of(Power.of(s, Num.of(n)), call("laplace", y, t, s)));
    for (int k = 0; k < n; k++) {
      terms.add(
          Product.of(Num.MINUS_ONE, Power.of(s, Num.of(n - 1 - k)), initialValue(y.head(), k)));
    }
    return Optional.of(Sum.of(terms));
  }

  /**
   * The initial value {@code y⁽ᵏ⁾(0)} as written in an initial condition: {@code y(0)}, {@code
   * y'(0)}, {@code y''(0)}, …, the applied unknown and its primed heads.
   *
   * @param y the unknown function's name
   * @param k the order of the derivative
   * @return the initial value
   */
  public static Expr initialValue(Symbol y, int k) {
    return Apply.of(Symbol.of(y.name() + "'".repeat(k)), Num.ZERO);
  }

  // The inverse transform.

  private static List<Rule> ilaplaceRules() {
    List<Rule> rules = new ArrayList<>();
    rules.add(
        rule(
            "linear",
            "ilaplace",
            (f, s, t, m) ->
                f instanceof Sum sum
                    ? Optional.of(
                        Sum.of(sum.terms().stream().map(u -> call("ilaplace", u, s, t)).toList()))
                    : Optional.empty()));
    rules.add(rule("constant-multiple", "ilaplace", (f, s, t, m) -> multiple(f, s, t, "ilaplace")));
    rules.add(rule("partial-fractions", "ilaplace", Laplace::partialFractions));
    rules.add(rule("table", "ilaplace", Laplace::inverseTable));
    return rules;
  }

  /** A rational function of {@code s} split by {@code apart} when that changes it. */
  private static Optional<Expr> partialFractions(Expr f, Symbol s, Expr t, Match m) {
    Context quiet = m.context().withTrace(false);
    Optional<Expr> parts = Algebra.apart(f, s, quiet);
    if (parts.isEmpty()) {
      return Optional.empty();
    }
    Expr split = Canonical.of(parts.get(), quiet.precision());
    // a split only when apart gives a sum: a denominator merely multiplied out is no split
    return !(split instanceof Sum) || split.equals(Canonical.of(f, quiet.precision()))
        ? Optional.empty()
        : Optional.of(call("ilaplace", split, s, t));
  }

  /** {@code c/(s − a)ⁿ} and {@code (As + B)/(s² + ps + q)}, the coefficients free of {@code s}. */
  private static Optional<Expr> inverseTable(Expr f, Symbol s, Expr t, Match m) {
    Context ctx = m.context();
    RationalFunction r;
    try {
      r = RationalFunction.of(Canonical.of(f, ctx.precision()), ctx.cancellation());
    } catch (IllegalArgumentException refused) {
      return Optional.empty();
    }
    Polynomial num = r.numerator();
    Polynomial den = r.denominator();
    if (!onlyIn(num, s) || !onlyIn(den, s) || den.degree(s) < 1 || num.degree(s) >= den.degree(s)) {
      return Optional.empty();
    }
    int n = den.degree(s);
    Expr lead = den.coefficient(s, n).toExpr();
    // a power of a linear factor: den = lead·(s − a)ⁿ
    Expr a =
        Canonical.of(
            over(
                Product.of(Num.MINUS_ONE, den.coefficient(s, n - 1).toExpr()),
                Product.of(Num.of(n), lead)),
            ctx.precision());
    Expr power = Product.of(lead, Power.of(minus(s, a), Num.of(n)));
    if (num.degree(s) == 0 && sameValue(power, den.toExpr(), ctx)) {
      Expr c = over(num.toExpr(), lead);
      return Optional.of(
          Product.of(
              c,
              Power.of(t, Num.of(n - 1)),
              Power.of(Constant.E, Product.of(a, t)),
              Power.of(factorial(BigInteger.valueOf(n - 1)), Num.MINUS_ONE)));
    }
    if (n == 2) {
      Expr p = over(den.coefficient(s, 1).toExpr(), lead);
      Expr q = over(den.coefficient(s, 0).toExpr(), lead);
      Expr shiftA = Canonical.of(Product.of(rational(-1, 2), p), ctx.precision());
      Expr k =
          Simplifier.simplify(
                  Canonical.of(minus(q, Product.of(rational(1, 4), squared(p))), ctx.precision()),
                  ctx.withTrace(false))
              .expr();
      Expr bigA = over(num.coefficient(s, 1).toExpr(), lead);
      Expr bigB = over(num.coefficient(s, 0).toExpr(), lead);
      Expr second = Sum.of(bigB, Product.of(bigA, shiftA));
      Expr exp = Power.of(Constant.E, Product.of(shiftA, t));
      Tri positive = ctx.assumptions().isPositive(k);
      Tri negative = ctx.assumptions().isNegative(k);
      if (positive == Tri.TRUE) {
        Expr beta = Exprs.sqrt(k);
        return Optional.of(
            Product.of(
                exp,
                Sum.of(
                    Product.of(bigA, fn("cos", Product.of(beta, t))),
                    Product.of(over(second, beta), fn("sin", Product.of(beta, t))))));
      }
      if (negative == Tri.TRUE) {
        Expr gamma = Exprs.sqrt(Product.of(Num.MINUS_ONE, k));
        return Optional.of(
            Product.of(
                exp,
                Sum.of(
                    Product.of(bigA, fn("cosh", Product.of(gamma, t))),
                    Product.of(over(second, gamma), fn("sinh", Product.of(gamma, t))))));
      }
    }
    return Optional.empty();
  }

  private static Expr rational(long p, long q) {
    return Num.of(mathcanvas.engine.number.Rational.of(p, q));
  }

  /** Whether two polynomials' expressions are equal once expanded. */
  private static boolean sameValue(Expr a, Expr b, Context ctx) {
    Context quiet = ctx.withTrace(false);
    Expr d = Algebra.expand(Canonical.of(minus(a, b), ctx.precision()), quiet).expr();
    return d.equals(Num.ZERO);
  }

  /** Whether a polynomial's kernels other than {@code s} are free of it. */
  private static boolean onlyIn(Polynomial p, Symbol s) {
    for (Expr k : p.variables()) {
      if (!k.equals(s) && !Free.of(k, s)) {
        return false;
      }
    }
    return true;
  }

  private static boolean contains(Expr e, String head) {
    if (e instanceof Apply a && a.head().name().equals(head)) {
      return true;
    }
    for (Expr o : Operands.of(e)) {
      if (contains(o, head)) {
        return true;
      }
    }
    return false;
  }

  /** A variable named {@code σ}, {@code σ_1}, … occurring nowhere in {@code e} or {@code s}. */
  private static Symbol fresh(Expr e, Expr s) {
    Predicate<Symbol> taken = x -> !Free.of(e, x) || !Free.of(s, x);
    for (int k = 0; ; k++) {
      Symbol x = Symbol.of(k == 0 ? "σ" : "σ_" + k);
      if (!taken.test(x)) {
        return x;
      }
    }
  }
}
