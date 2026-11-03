package mathcanvas.engine.stat;

import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.stat.Data.Entry;
import mathcanvas.engine.stat.Data.Sample;

/**
 * The descriptive statistics ({@code docs/language.md} §14.25): exact on exact data, every value
 * computed from the rationals a decimal sample denotes and rounded once at the precision. The
 * moments are sums over the values; the order statistics walk the values sorted by value with their
 * counts, so a count of a million costs nothing. Quartiles are the medians of the halves without
 * the median for an odd count (the TI convention); a percentile interpolates linearly between order
 * statistics at {@code h = (n − 1)p/100} (R's type 7); skewness and kurtosis are Excel's {@code
 * SKEW} and {@code KURT}, adjusted for the sample, the kurtosis in excess of 3.
 */
final class Descriptive {

  /** The fewest values each function needs. */
  static final Map<String, Integer> LEAST =
      Map.ofEntries(
          Map.entry("mean", 1),
          Map.entry("median", 1),
          Map.entry("mode", 1),
          Map.entry("variance", 2),
          Map.entry("pvariance", 1),
          Map.entry("stdev", 2),
          Map.entry("pstdev", 1),
          Map.entry("range", 1),
          Map.entry("quartiles", 2),
          Map.entry("iqr", 2),
          Map.entry("percentile", 1),
          Map.entry("zscore", 2),
          Map.entry("sort", 1),
          Map.entry("cumsum", 1),
          Map.entry("summary", 2),
          Map.entry("skewness", 3),
          Map.entry("kurtosis", 4),
          Map.entry("cov", 2),
          Map.entry("cor", 2),
          Map.entry("frequencies", 1));

  /** The functions this class evaluates. */
  static final Set<String> NAMES = LEAST.keySet();

  private final Context ctx;
  private final Arith ar;

  private Descriptive(Context ctx) {
    this.ctx = ctx;
    this.ar = new Arith(ctx);
  }

  /**
   * Evaluates a call whose arguments are evaluated.
   *
   * @param name the function
   * @param args the arguments
   * @param ctx the precision, numerics and cancellation
   * @return the value, or empty when the call stays
   * @throws IllegalArgumentException with the language's message for a wrong argument
   */
  static Optional<Expr> apply(String name, List<Expr> args, Context ctx) {
    return new Descriptive(ctx).evaluate(name, args);
  }

  private Optional<Expr> evaluate(String name, List<Expr> args) {
    int least = LEAST.get(name);
    switch (name) {
      case "cov", "cor" -> {
        Optional<List<Sample>> pairs = Data.pairs(name, args, least, ctx);
        if (pairs.isEmpty()) {
          return Optional.empty();
        }
        Sample x = pairs.get().get(0);
        Sample y = pairs.get().get(1);
        Expr value = name.equals("cov") ? covariance(x, y) : correlation(x, y);
        return Optional.of(out(value, x.decimal() || y.decimal()));
      }
      case "zscore" -> {
        if (args.size() == 3) {
          Optional<Numeric> sigma = ctx.numerics().value(args.get(2), ctx.precision());
          if (sigma.isPresent() && sigma.get() instanceof RealNumeric r && r.signum() <= 0) {
            throw new IllegalArgumentException("zscore needs σ > 0");
          }
          return Optional.of(ar.div(ar.sub(args.get(0), args.get(1)), args.get(2)));
        }
        Optional<Sample> s = Data.sample(name, args.get(1), null, least, ctx);
        if (s.isEmpty()) {
          return Optional.empty();
        }
        Sample d = s.get();
        Expr value = ar.div(ar.sub(Data.exact(args.getFirst()), mean(d)), ar.sqrt(variance(d)));
        return Optional.of(out(value, d.decimal() || Data.holdsDecimal(args.getFirst())));
      }
      case "percentile" -> {
        Expr counts = args.size() == 3 ? args.get(1) : null;
        Optional<Sample> s = Data.sample(name, args.getFirst(), counts, least, ctx);
        Optional<Entry> p = Data.real(name, args.getLast(), "p", ctx);
        if (s.isEmpty() || p.isEmpty()) {
          return Optional.empty();
        }
        RealNumeric k = p.get().key();
        if (k.signum() < 0 || k.compareValue(Int.of(100)) > 0) {
          throw new IllegalArgumentException("percentile needs 0 ≤ p ≤ 100");
        }
        Expr value = percentile(s.get(), p.get().value(), k);
        return Optional.of(out(value, s.get().decimal() || Data.holdsDecimal(args.getLast())));
      }
      default -> {}
    }
    if (args.size() == 2 && (name.equals("sort") || name.equals("cumsum"))) {
      throw new IllegalArgumentException(name + " takes 1 argument");
    }
    Optional<Sample> s =
        Data.sample(name, args.getFirst(), args.size() == 2 ? args.get(1) : null, least, ctx);
    if (s.isEmpty()) {
      return Optional.empty();
    }
    Sample d = s.get();
    boolean dec = d.decimal();
    return Optional.of(
        switch (name) {
          case "mean" -> out(mean(d), dec);
          case "median" -> out(median(d.sorted(), BigInteger.ZERO, d.n()), dec);
          case "mode" -> mode(d);
          case "variance" -> out(variance(d), dec);
          case "pvariance" -> out(pvariance(d), dec);
          case "stdev" -> out(ar.sqrt(variance(d)), dec);
          case "pstdev" -> out(ar.sqrt(pvariance(d)), dec);
          case "range" -> {
            List<Entry> sorted = d.sorted();
            yield out(ar.sub(sorted.getLast().value(), sorted.getFirst().value()), dec);
          }
          case "quartiles" -> {
            List<Expr> q = quartiles(d);
            yield new Vector(q.stream().map(e -> out(e, dec)).toList());
          }
          case "iqr" -> {
            List<Expr> q = quartiles(d);
            yield out(ar.sub(q.get(2), q.get(0)), dec);
          }
          case "sort" -> new Vector(d.sorted().stream().map(Entry::original).toList());
          case "cumsum" -> {
            List<Expr> out = new ArrayList<>();
            Expr total = Num.ZERO;
            for (Entry e : d.entries()) {
              total = ar.add(total, e.value());
              out.add(out(total, dec));
            }
            yield new Vector(out);
          }
          case "skewness" -> out(skewness(d), dec);
          case "kurtosis" -> out(kurtosis(d), dec);
          case "frequencies" -> frequencies(d);
          case "summary" -> summary(d);
          default -> throw new IllegalStateException("not a descriptive function: " + name);
        });
  }

  /** A value of decimal data as a decimal at the precision; exact data's as it is. */
  private Expr out(Expr value, boolean decimal) {
    if (!decimal) {
      return value;
    }
    MathContext mc = ctx.precision();
    Optional<Numeric> v = ctx.numerics().value(value, mc);
    return v.isPresent() ? Num.of(v.get().toReal(mc)) : value;
  }

  // Moments.

  Expr total(Sample d) {
    List<Expr> terms = new ArrayList<>();
    for (Entry e : d.entries()) {
      terms.add(ar.mul(Arith.num(e.count()), e.value()));
    }
    return ar.sum(terms);
  }

  Expr mean(Sample d) {
    return ar.div(total(d), Arith.num(d.n()));
  }

  /** {@code Σ cᵢ (xᵢ − x̄)^k}. */
  private Expr centralSum(Sample d, Expr mean, int k) {
    List<Expr> terms = new ArrayList<>();
    for (Entry e : d.entries()) {
      terms.add(ar.mul(Arith.num(e.count()), ar.pow(ar.sub(e.value(), mean), k)));
    }
    return ar.sum(terms);
  }

  Expr variance(Sample d) {
    return ar.div(centralSum(d, mean(d), 2), Arith.num(d.n().subtract(BigInteger.ONE)));
  }

  private Expr pvariance(Sample d) {
    return ar.div(centralSum(d, mean(d), 2), Arith.num(d.n()));
  }

  /** Excel's {@code SKEW}: {@code n/((n − 1)(n − 2)) Σ ((xᵢ − x̄)/s)³}. */
  private Expr skewness(Sample d) {
    Expr n = Arith.num(d.n());
    Expr m = mean(d);
    Expr cubes = centralSum(d, m, 3);
    Expr s3 = ar.pow(variance(d), Num.of(mathcanvas.engine.number.Rational.of(3, 2)));
    Expr factor = ar.div(n, ar.mul(ar.sub(n, Num.ONE), ar.sub(n, Num.of(2))));
    return ar.mul(factor, ar.div(cubes, s3));
  }

  /**
   * Excel's {@code KURT}: {@code n(n + 1)/((n − 1)(n − 2)(n − 3)) Σ ((xᵢ − x̄)/s)⁴ − 3(n − 1)²/((n
   * − 2)(n − 3))}.
   */
  private Expr kurtosis(Sample d) {
    Expr n = Arith.num(d.n());
    Expr m = mean(d);
    Expr fourths = ar.div(centralSum(d, m, 4), ar.pow(variance(d), 2));
    Expr n1 = ar.sub(n, Num.ONE);
    Expr n2 = ar.sub(n, Num.of(2));
    Expr n3 = ar.sub(n, Num.of(3));
    Expr factor = ar.div(ar.mul(n, ar.add(n, Num.ONE)), ar.mul(n1, n2, n3));
    Expr shift = ar.div(ar.mul(Num.of(3), ar.pow(n1, 2)), ar.mul(n2, n3));
    return ar.sub(ar.mul(factor, fourths), shift);
  }

  private Expr covariance(Sample x, Sample y) {
    return ar.div(products(x, y), Arith.num(x.n().subtract(BigInteger.ONE)));
  }

  /** {@code Σ (xᵢ − x̄)(yᵢ − ȳ)}. */
  private Expr products(Sample x, Sample y) {
    Expr mx = mean(x);
    Expr my = mean(y);
    List<Expr> terms = new ArrayList<>();
    for (int i = 0; i < x.entries().size(); i++) {
      terms.add(
          ar.mul(ar.sub(x.entries().get(i).value(), mx), ar.sub(y.entries().get(i).value(), my)));
    }
    return ar.sum(terms);
  }

  private Expr correlation(Sample x, Sample y) {
    Expr sxy = products(x, y);
    Expr sxx = centralSum(x, mean(x), 2);
    Expr syy = centralSum(y, mean(y), 2);
    return ar.div(sxy, ar.sqrt(ar.mul(sxx, syy)));
  }

  // Order statistics.

  /** The value at a 0-based position of the sorted values, each repeated by its count. */
  private static Expr at(List<Entry> sorted, BigInteger position) {
    BigInteger seen = BigInteger.ZERO;
    for (Entry e : sorted) {
      seen = seen.add(e.count());
      if (position.compareTo(seen) < 0) {
        return e.value();
      }
    }
    throw new IllegalStateException("position beyond the sample");
  }

  /** The median of the {@code count} sorted values from the 0-based {@code from}. */
  private Expr median(List<Entry> sorted, BigInteger from, BigInteger count) {
    BigInteger[] half = count.divideAndRemainder(BigInteger.TWO);
    BigInteger mid = from.add(half[0]);
    if (half[1].signum() != 0) {
      return at(sorted, mid);
    }
    return ar.div(ar.add(at(sorted, mid.subtract(BigInteger.ONE)), at(sorted, mid)), Num.of(2));
  }

  /** {@code [Q1, Q2, Q3]}: the medians of the halves, the median left out for an odd count. */
  private List<Expr> quartiles(Sample d) {
    List<Entry> sorted = d.sorted();
    BigInteger n = d.n();
    BigInteger half = n.shiftRight(1);
    return List.of(
        median(sorted, BigInteger.ZERO, half),
        median(sorted, BigInteger.ZERO, n),
        median(sorted, n.subtract(half), half));
  }

  /** R's type 7: {@code x_⌊h⌋ + (h − ⌊h⌋)(x_⌊h⌋+1 − x_⌊h⌋)} with {@code h = (n − 1)p/100}. */
  private Expr percentile(Sample d, Expr p, RealNumeric pValue) {
    List<Entry> sorted = d.sorted();
    Expr h = ar.div(ar.mul(Arith.num(d.n().subtract(BigInteger.ONE)), p), Num.of(100));
    MathContext w =
        new MathContext(ctx.precision().getPrecision() + 10, java.math.RoundingMode.HALF_EVEN);
    RealNumeric hValue =
        (RealNumeric)
            Int.of(d.n().subtract(BigInteger.ONE)).multiply(pValue, w).divide(Int.of(100), w);
    BigInteger lo = floor(hValue);
    Expr low = at(sorted, lo);
    Expr fraction = ar.sub(h, Arith.num(lo));
    if (lo.add(BigInteger.ONE).compareTo(d.n()) >= 0 || fraction.equals(Num.ZERO)) {
      return low;
    }
    Expr high = at(sorted, lo.add(BigInteger.ONE));
    return ar.add(low, ar.mul(fraction, ar.sub(high, low)));
  }

  private static BigInteger floor(RealNumeric r) {
    return switch (r) {
      case Int i -> i.value();
      case mathcanvas.engine.number.Rational q -> q.floor().value();
      case mathcanvas.engine.number.Real x ->
          x.value().setScale(0, java.math.RoundingMode.FLOOR).toBigIntegerExact();
    };
  }

  /** The entries merged by value, in ascending order, with their counts summed. */
  private static List<Entry> grouped(Sample d) {
    List<Entry> out = new ArrayList<>();
    for (Entry e : d.sorted()) {
      if (!out.isEmpty() && out.getLast().key().compareValue(e.key()) == 0) {
        Entry last = out.removeLast();
        out.add(new Entry(last.value(), last.original(), last.key(), last.count().add(e.count())));
      } else {
        out.add(e);
      }
    }
    return out;
  }

  private Expr mode(Sample d) {
    List<Entry> groups = grouped(d);
    BigInteger top = groups.stream().map(Entry::count).reduce(BigInteger.ZERO, BigInteger::max);
    List<Expr> modes =
        groups.stream().filter(g -> g.count().equals(top)).map(Entry::original).toList();
    return modes.size() == 1 ? modes.getFirst() : new SetExpr(modes);
  }

  private Expr frequencies(Sample d) {
    List<List<Expr>> rows = new ArrayList<>();
    for (Entry g : grouped(d)) {
      rows.add(List.of(g.original(), Arith.num(g.count())));
    }
    return new Matrix(rows);
  }

  private Expr summary(Sample d) {
    boolean dec = d.decimal();
    List<Entry> sorted = d.sorted();
    List<Expr> q = quartiles(d);
    List<Expr> items = new ArrayList<>();
    items.add(equation("n", Arith.num(d.n())));
    items.add(equation("mean", out(mean(d), dec)));
    items.add(equation("median", out(q.get(1), dec)));
    items.add(equation("min", out(sorted.getFirst().value(), dec)));
    items.add(equation("Q1", out(q.get(0), dec)));
    items.add(equation("Q3", out(q.get(2), dec)));
    items.add(equation("max", out(sorted.getLast().value(), dec)));
    items.add(equation("stdev", out(ar.sqrt(variance(d)), dec)));
    return new ListExpr(items);
  }

  private static Relation equation(String name, Expr value) {
    return new Relation(Relation.Op.EQ, Symbol.of(name), value);
  }
}
