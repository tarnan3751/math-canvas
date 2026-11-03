package mathcanvas.engine.stat;

import java.io.Serial;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.provider.Distribute;
import mathcanvas.engine.provider.Distribute.Kind;
import mathcanvas.engine.provider.Distribute.Law;
import mathcanvas.engine.provider.DistributionValue;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.simplify.Simplifier;
import mathcanvas.engine.stat.Data.Entry;

/**
 * The probability distributions ({@code docs/language.md} §14.25), the exact cases decided before
 * the {@link Distribute} provider is asked. Every density and probability mass function, the
 * discrete cumulative functions and the exponential and uniform ones have a closed form, which an
 * exact call evaluates to ({@code binompdf(10, 1/2, 3)} is {@code 15/128}, {@code normalpdf(x)} is
 * {@code e^(−x²/2)/√(2π)}) and a call with a decimal evaluates at the precision; a discrete law
 * with a decimal parameter, and every cumulative function and quantile of the normal, t, χ² and F
 * distributions, are numeric, through the provider in {@code double}, at most {@link #DIGITS}
 * digits. A discrete law past {@link #MAX_TERMS} trials, draws or terms stays as it is, and {@code
 * numeric} evaluates it through the provider. An argument holding a symbol leaves the call, but the
 * point of the normal and t densities, which have the closed form at every real point.
 */
final class Distributions {

  /** The most significant digits of a numeric value: a {@code double}'s. */
  static final int DIGITS = 16;

  /** The digits an argument is evaluated to before it is taken to a {@code double}. */
  static final int INPUT_DIGITS = 17;

  /** The largest {@code n} (or {@code N}, or {@code k} for Poisson) with an exact value. */
  static final int MAX_TERMS = 2000;

  /** The functions, with the law each is of. */
  static final Map<String, Law> LAWS =
      Map.ofEntries(
          Map.entry("normalpdf", Law.NORMAL),
          Map.entry("normalcdf", Law.NORMAL),
          Map.entry("invnorm", Law.NORMAL),
          Map.entry("tpdf", Law.STUDENT_T),
          Map.entry("tcdf", Law.STUDENT_T),
          Map.entry("invt", Law.STUDENT_T),
          Map.entry("chi2pdf", Law.CHI_SQUARED),
          Map.entry("chi2cdf", Law.CHI_SQUARED),
          Map.entry("invchi2", Law.CHI_SQUARED),
          Map.entry("fpdf", Law.F),
          Map.entry("fcdf", Law.F),
          Map.entry("invf", Law.F),
          Map.entry("binompdf", Law.BINOMIAL),
          Map.entry("binomcdf", Law.BINOMIAL),
          Map.entry("poissonpdf", Law.POISSON),
          Map.entry("poissoncdf", Law.POISSON),
          Map.entry("geometpdf", Law.GEOMETRIC),
          Map.entry("geometcdf", Law.GEOMETRIC),
          Map.entry("hypergeompdf", Law.HYPERGEOMETRIC),
          Map.entry("hypergeomcdf", Law.HYPERGEOMETRIC),
          Map.entry("exppdf", Law.EXPONENTIAL),
          Map.entry("expcdf", Law.EXPONENTIAL),
          Map.entry("unifpdf", Law.UNIFORM),
          Map.entry("unifcdf", Law.UNIFORM));

  /** The functions this class evaluates. */
  static final Set<String> NAMES = LAWS.keySet();

  /**
   * A value.
   *
   * @param value the value
   * @param numeric whether the provider gave it
   */
  record Value(Expr value, boolean numeric) {}

  /** The provider found no value to its accuracy: the call stays, with its note. */
  static final class NoConvergence extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;

    private NoConvergence() {
      super(null, null, false, false);
    }
  }

  private final Context ctx;
  private final Arith ar;
  private final String name;
  private final Law law;
  private final boolean force;

  private Distributions(String name, Context ctx, boolean force) {
    this.ctx = ctx;
    this.ar = new Arith(ctx);
    this.name = name;
    this.law = LAWS.get(name);
    this.force = force;
  }

  /**
   * Evaluates a call whose arguments are evaluated.
   *
   * @param name the function
   * @param args the arguments
   * @param ctx the precision, numerics and cancellation
   * @param force whether to evaluate through the provider a discrete law past {@link #MAX_TERMS}
   *     ({@code numeric})
   * @return the value, or empty when the call stays
   * @throws IllegalArgumentException with the language's message for a wrong argument
   * @throws NoConvergence when the provider did not converge
   */
  static Optional<Value> apply(String name, List<Expr> args, Context ctx, boolean force) {
    return new Distributions(name, ctx, force).evaluate(args);
  }

  private Optional<Value> evaluate(List<Expr> args) {
    if (name.endsWith("pdf") && law.continuous()) {
      return density(args);
    }
    if (name.startsWith("inv")) {
      return inverse(args);
    }
    if (law.continuous()) {
      return probability(args);
    }
    return discrete(args);
  }

  // Parameters.

  /** The parameters after the leading {@code skip} arguments, with the normal's defaults. */
  private Optional<List<Entry>> parameters(List<Expr> args, int skip) {
    List<Expr> raw = new ArrayList<>(args.subList(skip, args.size()));
    if (law == Law.NORMAL) {
      if (raw.isEmpty()) {
        raw.add(Num.ZERO);
      }
      if (raw.size() == 1) {
        raw.add(Num.ONE);
      }
    }
    List<String> what = names();
    List<Entry> out = new ArrayList<>();
    for (int i = 0; i < raw.size(); i++) {
      Optional<Entry> e = Data.real(name, raw.get(i), what.get(i), ctx);
      if (e.isEmpty()) {
        return Optional.empty();
      }
      out.add(e.get());
    }
    check(out);
    return Optional.of(out);
  }

  private List<String> names() {
    return switch (law) {
      case NORMAL -> List.of("μ", "σ");
      case STUDENT_T, CHI_SQUARED -> List.of("df");
      case F -> List.of("d1", "d2");
      case EXPONENTIAL -> List.of("λ");
      case UNIFORM -> List.of("a", "b");
      case BINOMIAL -> List.of("n", "p");
      case POISSON -> List.of("λ");
      case GEOMETRIC -> List.of("p");
      case HYPERGEOMETRIC -> List.of("N", "K", "n");
      case ERF, ERFC, GAMMA, BETA -> throw new IllegalStateException("not a distribution");
    };
  }

  private void check(List<Entry> p) {
    switch (law) {
      case NORMAL -> require(positive(p.get(1)), "σ > 0");
      case STUDENT_T, CHI_SQUARED -> require(positive(p.get(0)), "df > 0");
      case F -> require(positive(p.get(0)) && positive(p.get(1)), "d1 > 0 and d2 > 0");
      case EXPONENTIAL, POISSON -> require(positive(p.get(0)), "λ > 0");
      case UNIFORM -> require(p.get(0).key().compareValue(p.get(1).key()) < 0, "a < b");
      case BINOMIAL -> {
        require(integer(p.get(0)) && p.get(0).key().signum() >= 0, "a non-negative integer n");
        RealNumeric q = p.get(1).key();
        require(q.signum() >= 0 && q.compareValue(Int.ONE) <= 0, "0 ≤ p ≤ 1");
      }
      case GEOMETRIC -> {
        RealNumeric q = p.get(0).key();
        require(q.signum() > 0 && q.compareValue(Int.ONE) <= 0, "0 < p ≤ 1");
      }
      case HYPERGEOMETRIC -> {
        boolean ints = integer(p.get(0)) && integer(p.get(1)) && integer(p.get(2));
        require(
            ints
                && p.get(1).key().signum() >= 0
                && p.get(1).key().compareValue(p.get(0).key()) <= 0
                && p.get(2).key().signum() >= 0
                && p.get(2).key().compareValue(p.get(0).key()) <= 0,
            "integers 0 ≤ K ≤ N and 0 ≤ n ≤ N");
      }
      case ERF, ERFC, GAMMA, BETA -> throw new IllegalStateException("not a distribution");
    }
  }

  private void require(boolean ok, String what) {
    if (!ok) {
      throw new IllegalArgumentException(name + " needs " + what);
    }
  }

  private static boolean positive(Entry e) {
    return e.key().signum() > 0;
  }

  private static boolean integer(Entry e) {
    return e.key().isInteger();
  }

  private static BigInteger integerValue(Entry e) {
    return switch (e.key()) {
      case Int i -> i.value();
      case Real r -> r.value().toBigIntegerExact();
      case Rational q -> throw new IllegalStateException("not an integer: " + q);
    };
  }

  private static boolean decimal(List<Expr> args) {
    return args.stream().anyMatch(Data::holdsDecimal);
  }

  // Continuous densities.

  private Optional<Value> density(List<Expr> args) {
    Optional<List<Entry>> ps = parameters(args, 1);
    if (ps.isEmpty()) {
      return Optional.empty();
    }
    List<Expr> p = ps.get().stream().map(Entry::value).toList();
    Expr x = args.getFirst();
    boolean dec = decimal(args);
    if (Data.holdsSymbol(x)) {
      // the closed form at every real point: the normal and t densities, with exact parameters
      if ((law == Law.NORMAL || law == Law.STUDENT_T) && !dec) {
        return Optional.of(new Value(closedDensity(x, p), false));
      }
      return Optional.empty();
    }
    Optional<Entry> point = Data.real(name, x, "x", ctx);
    Entry at = point.orElseThrow();
    Expr value =
        switch (law) {
          case NORMAL, STUDENT_T -> closedDensity(at.value(), p);
          case CHI_SQUARED, F -> {
            int sign = at.key().signum();
            if (sign < 0) {
              yield Num.ZERO;
            }
            if (sign == 0) {
              // finite at 0 only from 2 degrees of freedom: 1/2 for χ², 1 for F
              int order = ps.get().getFirst().key().compareValue(Int.TWO);
              yield order > 0
                  ? Num.ZERO
                  : order == 0
                      ? (law == Law.F ? Num.ONE : Num.of(Rational.of(1, 2)))
                      : Constant.UNDEFINED;
            }
            yield closedDensity(at.value(), p);
          }
          case EXPONENTIAL -> at.key().signum() < 0 ? Num.ZERO : closedDensity(at.value(), p);
          case UNIFORM ->
              at.key().compareValue(ps.get().get(0).key()) < 0
                      || at.key().compareValue(ps.get().get(1).key()) > 0
                  ? Num.ZERO
                  : closedDensity(at.value(), p);
          default -> throw new IllegalStateException("not continuous: " + law);
        };
    if (dec || force) {
      Expr simplified = Simplifier.simplify(value, ctx.withTrace(false)).expr();
      if (holdsSpecial(simplified)) {
        // a gamma or beta with no exact value (a non-half-integer degree of freedom): taken at
        // the precision it would widen a double to digits it does not hold, so the provider gives
        // the density, marked numeric
        return Optional.of(new Value(provide(Kind.DENSITY, ps.get(), toDouble(at.key())), true));
      }
      return Optional.of(new Value(dec ? decimal(simplified) : simplified, false));
    }
    return Optional.of(new Value(value, false));
  }

  /** Whether an expression holds a call of {@code gamma} or {@code beta}. */
  private static boolean holdsSpecial(Expr e) {
    if (e instanceof Apply a
        && (a.head().name().equals("gamma") || a.head().name().equals("beta"))) {
      return true;
    }
    for (Expr o : Operands.of(e)) {
      if (holdsSpecial(o)) {
        return true;
      }
    }
    return false;
  }

  /** The density's closed form at {@code x}, inside the support. */
  private Expr closedDensity(Expr x, List<Expr> p) {
    Expr pi = Constant.PI;
    Expr e = Constant.E;
    Num half = Num.of(Rational.of(1, 2));
    return switch (law) {
      case NORMAL -> {
        Expr z2 = ar.div(ar.pow(ar.sub(x, p.get(0)), 2), ar.mul(Num.of(2), ar.pow(p.get(1), 2)));
        yield ar.div(
            ar.pow(e, ar.mul(Num.MINUS_ONE, z2)), ar.mul(p.get(1), ar.sqrt(ar.mul(Num.of(2), pi))));
      }
      case STUDENT_T -> {
        Expr nu = p.getFirst();
        Expr up = ar.mul(ar.add(nu, Num.ONE), half);
        Expr c = ar.div(gamma(up), ar.mul(ar.sqrt(ar.mul(nu, pi)), gamma(ar.mul(nu, half))));
        yield ar.mul(
            c, ar.pow(ar.add(Num.ONE, ar.div(ar.pow(x, 2), nu)), ar.mul(Num.MINUS_ONE, up)));
      }
      case CHI_SQUARED -> {
        Expr k2 = ar.mul(p.getFirst(), half);
        Expr top =
            ar.mul(
                ar.pow(x, ar.sub(k2, Num.ONE)), ar.pow(e, ar.mul(x, Num.of(Rational.of(-1, 2)))));
        yield ar.div(top, ar.mul(ar.pow(Num.of(2), k2), gamma(k2)));
      }
      case F -> {
        Expr d1 = p.get(0);
        Expr d2 = p.get(1);
        Expr h1 = ar.mul(d1, half);
        Expr h2 = ar.mul(d2, half);
        Expr top =
            ar.mul(
                ar.pow(ar.div(d1, d2), h1),
                ar.pow(x, ar.sub(h1, Num.ONE)),
                ar.pow(
                    ar.add(Num.ONE, ar.div(ar.mul(d1, x), d2)),
                    ar.mul(Num.MINUS_ONE, ar.add(h1, h2))));
        yield ar.div(top, ar.canon(Apply.of(Symbol.of("beta"), h1, h2)));
      }
      case EXPONENTIAL ->
          ar.mul(p.getFirst(), ar.pow(e, ar.mul(Num.MINUS_ONE, ar.mul(p.getFirst(), x))));
      case UNIFORM -> ar.div(Num.ONE, ar.sub(p.get(1), p.get(0)));
      default -> throw new IllegalStateException("not continuous: " + law);
    };
  }

  private Expr gamma(Expr a) {
    return ar.canon(Apply.of(Symbol.of("gamma"), a));
  }

  /**
   * A closed value of decimal arguments at the precision: simplified first, so that the gamma
   * values at integers and half-integers are exact before the value is taken.
   */
  private Expr decimal(Expr value) {
    Expr simplified = Simplifier.simplify(value, ctx.withTrace(false)).expr();
    MathContext mc = ctx.precision();
    Optional<Numeric> v = ctx.numerics().value(simplified, mc);
    return v.isPresent() ? Num.of(v.get().toReal(mc)) : simplified;
  }

  // Probabilities of an interval.

  /** {@code ±∞}, or a real bound; empty when it holds a symbol. */
  private Optional<Double> bound(Expr e, String what) {
    if (e.equals(Constant.INFINITY)) {
      return Optional.of(Double.POSITIVE_INFINITY);
    }
    if (e.equals(ar.canon(Product.of(Num.MINUS_ONE, Constant.INFINITY)))) {
      return Optional.of(Double.NEGATIVE_INFINITY);
    }
    return Data.real(name, e, what, ctx).map(b -> toDouble(b.key()));
  }

  private Optional<Value> probability(List<Expr> args) {
    Optional<List<Entry>> ps = parameters(args, 2);
    Optional<Double> a = bound(args.get(0), "bound");
    Optional<Double> b = bound(args.get(1), "bound");
    if (ps.isEmpty() || a.isEmpty() || b.isEmpty()) {
      return Optional.empty();
    }
    if (law == Law.EXPONENTIAL || law == Law.UNIFORM) {
      return Optional.of(closedProbability(args, ps.get()));
    }
    double lo = Math.min(a.get(), b.get());
    double hi = Math.max(a.get(), b.get());
    Expr v = provide(Kind.PROBABILITY, ps.get(), lo, hi);
    return Optional.of(new Value(a.get() > b.get() ? ar.mul(Num.MINUS_ONE, v) : v, true));
  }

  /** {@code F(b) − F(a)} of the exponential and uniform laws, exactly. */
  private Value closedProbability(List<Expr> args, List<Entry> ps) {
    Expr fb = cumulative(args.get(1), ps);
    Expr fa = cumulative(args.get(0), ps);
    Expr value = ar.sub(fb, fa);
    return new Value(decimal(args) ? decimal(value) : value, false);
  }

  /** {@code F(t)} of the exponential or uniform law at a bound. */
  private Expr cumulative(Expr t, List<Entry> ps) {
    if (t.equals(Constant.INFINITY)) {
      return Num.ONE;
    }
    if (t.equals(ar.canon(Product.of(Num.MINUS_ONE, Constant.INFINITY)))) {
      return Num.ZERO;
    }
    Entry at = Data.real(name, t, "bound", ctx).orElseThrow();
    if (law == Law.EXPONENTIAL) {
      if (at.key().signum() <= 0) {
        return Num.ZERO;
      }
      Expr lambda = ps.getFirst().value();
      return ar.sub(Num.ONE, ar.pow(Constant.E, ar.mul(Num.MINUS_ONE, ar.mul(lambda, at.value()))));
    }
    Entry lo = ps.get(0);
    Entry hi = ps.get(1);
    if (at.key().compareValue(lo.key()) <= 0) {
      return Num.ZERO;
    }
    if (at.key().compareValue(hi.key()) >= 0) {
      return Num.ONE;
    }
    return ar.div(ar.sub(at.value(), lo.value()), ar.sub(hi.value(), lo.value()));
  }

  // Quantiles.

  private Optional<Value> inverse(List<Expr> args) {
    Optional<List<Entry>> ps = parameters(args, 1);
    Optional<Entry> p = Data.real(name, args.getFirst(), "p", ctx);
    if (ps.isEmpty() || p.isEmpty()) {
      return Optional.empty();
    }
    RealNumeric q = p.get().key();
    require(q.signum() >= 0 && q.compareValue(Int.ONE) <= 0, "0 ≤ p ≤ 1");
    if (q.isZero()) {
      return Optional.of(
          new Value(
              law == Law.NORMAL || law == Law.STUDENT_T
                  ? ar.canon(Product.of(Num.MINUS_ONE, Constant.INFINITY))
                  : Num.ZERO,
              false));
    }
    if (q.isOne()) {
      return Optional.of(new Value(Constant.INFINITY, false));
    }
    // the smaller tail, formed exactly: 1 − q in double would carry q's rounding
    boolean lower = q.compareValue(Rational.of(1, 2)) <= 0;
    double tail = toDouble(lower ? q : (RealNumeric) Int.ONE.subtract(q, ctx.precision()));
    if (tail == 0) {
      throw new IllegalArgumentException(name + "'s tail is beyond the double range");
    }
    return Optional.of(
        new Value(provide(lower ? Kind.INVERSE : Kind.UPPER_INVERSE, ps.get(), tail), true));
  }

  // Discrete laws.

  private Optional<Value> discrete(List<Expr> args) {
    int count = law.parameters();
    Optional<List<Entry>> ps = parameters(args.subList(0, count), 0);
    Optional<Entry> k = Data.real(name, args.get(count), "k", ctx);
    if (ps.isEmpty() || k.isEmpty()) {
      return Optional.empty();
    }
    require(integer(k.get()), "an integer k");
    List<Entry> p = ps.get();
    BigInteger kk = integerValue(k.get());
    boolean cdf = name.endsWith("cdf");
    boolean exact = !decimal(args);
    BigInteger size =
        switch (law) {
          case BINOMIAL, HYPERGEOMETRIC -> integerValue(p.getFirst());
          case POISSON -> kk.max(BigInteger.ZERO);
          default -> BigInteger.ZERO;
        };
    if (exact && size.compareTo(BigInteger.valueOf(MAX_TERMS)) <= 0) {
      return Optional.of(new Value(exactDiscrete(p, kk, cdf), false));
    }
    if (exact && !force) {
      return Optional.empty();
    }
    double at = kk.doubleValue();
    Expr v =
        cdf
            ? provide(Kind.PROBABILITY, p, Double.NEGATIVE_INFINITY, at)
            : provide(Kind.DENSITY, p, at);
    return Optional.of(new Value(v, true));
  }

  private Expr exactDiscrete(List<Entry> ps, BigInteger k, boolean cdf) {
    List<Expr> p = ps.stream().map(Entry::value).toList();
    return switch (law) {
      case BINOMIAL -> {
        int n = integerValue(ps.getFirst()).intValueExact();
        if (k.signum() < 0) {
          yield Num.ZERO;
        }
        if (k.compareTo(BigInteger.valueOf(n)) > 0) {
          yield cdf ? Num.ONE : Num.ZERO;
        }
        int top = k.intValueExact();
        List<Expr> terms = new ArrayList<>();
        for (int j = cdf ? 0 : top; j <= top; j++) {
          ctx.cancellation().check();
          terms.add(
              ar.mul(
                  Arith.num(Int.of(n).binomial(Int.of(j)).value()),
                  ar.pow(p.get(1), j),
                  ar.pow(ar.sub(Num.ONE, p.get(1)), n - j)));
        }
        yield ar.sum(terms);
      }
      case POISSON -> {
        if (k.signum() < 0) {
          yield Num.ZERO;
        }
        int top = k.intValueExact();
        Expr e = ar.pow(Constant.E, ar.mul(Num.MINUS_ONE, p.getFirst()));
        if (cdf && ps.getFirst().key() instanceof RealNumeric lambda && lambda.isExact()) {
          // a rational λ = a/b: Σ λʲ/j! as one rational, by Horner's scheme 1 + (λ/j)·(…) from
          // j = k down in integers, reduced once
          BigInteger a = lambda instanceof Rational q ? q.num() : ((Int) lambda).value();
          BigInteger b = lambda instanceof Rational q ? q.den() : BigInteger.ONE;
          BigInteger num = BigInteger.ONE;
          BigInteger den = BigInteger.ONE;
          for (int j = top; j >= 1; j--) {
            ctx.cancellation().check();
            BigInteger step = den.multiply(b).multiply(BigInteger.valueOf(j));
            num = step.add(a.multiply(num));
            den = step;
          }
          yield ar.mul(e, Num.of(Rational.of(num, den)));
        }
        List<Expr> terms = new ArrayList<>();
        for (int j = cdf ? 0 : top; j <= top; j++) {
          ctx.cancellation().check();
          terms.add(ar.div(ar.pow(p.getFirst(), j), Arith.num(Int.of(j).factorial().value())));
        }
        yield ar.mul(e, ar.sum(terms));
      }
      case GEOMETRIC -> {
        if (k.signum() <= 0) {
          yield Num.ZERO;
        }
        Expr miss = ar.sub(Num.ONE, p.getFirst());
        yield cdf
            ? ar.sub(Num.ONE, ar.pow(miss, Arith.num(k)))
            : ar.mul(ar.pow(miss, Arith.num(k.subtract(BigInteger.ONE))), p.getFirst());
      }
      case HYPERGEOMETRIC -> {
        int big = integerValue(ps.get(0)).intValueExact();
        int successes = integerValue(ps.get(1)).intValueExact();
        int draws = integerValue(ps.get(2)).intValueExact();
        int lo = Math.max(0, draws + successes - big);
        int hi = Math.min(draws, successes);
        if (k.compareTo(BigInteger.valueOf(lo)) < 0) {
          yield Num.ZERO;
        }
        if (k.compareTo(BigInteger.valueOf(hi)) > 0) {
          yield cdf ? Num.ONE : Num.ZERO;
        }
        int top = k.intValueExact();
        BigInteger whole = Int.of(big).binomial(Int.of(draws)).value();
        BigInteger ways = BigInteger.ZERO;
        for (int j = cdf ? lo : top; j <= top; j++) {
          ctx.cancellation().check();
          ways =
              ways.add(
                  Int.of(successes)
                      .binomial(Int.of(j))
                      .value()
                      .multiply(Int.of(big - successes).binomial(Int.of(draws - j)).value()));
        }
        yield Num.of(Rational.of(ways, whole));
      }
      default -> throw new IllegalStateException("not discrete: " + law);
    };
  }

  // The provider.

  private static double toDouble(RealNumeric r) {
    return r.toDouble();
  }

  /** A value through the provider, as a decimal of at most {@link #DIGITS} digits. */
  private Expr provide(Kind kind, List<Entry> ps, double... points) {
    double[] parameters = new double[ps.size()];
    for (int i = 0; i < parameters.length; i++) {
      double x = toDouble(ps.get(i).key());
      if (!Double.isFinite(x)) {
        throw new IllegalArgumentException(name + "'s parameters are beyond the double range");
      }
      parameters[i] = x;
    }
    if ((law == Law.BINOMIAL || law == Law.HYPERGEOMETRIC) && parameters[0] > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          name
              + "'s numeric value needs "
              + (law == Law.BINOMIAL ? "n" : "N")
              + " ≤ "
              + Integer.MAX_VALUE
              + ", the int range of the distribution provider");
    }
    DistributionValue v;
    try {
      v =
          ctx.providers()
              .run(new Distribute(law, kind, parameters, points), ctx, Steps.NONE)
              .value();
    } catch (IllegalArgumentException outOfRange) {
      throw new IllegalArgumentException(name + "'s parameters are beyond the double range");
    }
    return switch (v.status()) {
      case OK -> decimal(v.value(), ctx.precision());
      case NOT_FINITE ->
          throw new IllegalArgumentException(name + "'s value is beyond the double range");
      case NO_CONVERGENCE -> throw new NoConvergence();
    };
  }

  /**
   * A {@code double} as a decimal of at most {@link #DIGITS} digits, fewer at a lower precision.
   */
  static Num decimal(double x, MathContext mc) {
    MathContext out = new MathContext(Math.min(mc.getPrecision(), DIGITS), RoundingMode.HALF_EVEN);
    return Num.of(Real.of(BigDecimal.valueOf(x).round(out)));
  }
}
