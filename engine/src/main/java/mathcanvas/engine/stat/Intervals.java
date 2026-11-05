package mathcanvas.engine.stat;

import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.provider.Distribute;
import mathcanvas.engine.provider.Distribute.Kind;
import mathcanvas.engine.provider.Distribute.Law;
import mathcanvas.engine.provider.DistributionValue;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.stat.Data.Sample;

/**
 * The confidence intervals ({@code docs/language.md} §14.25): {@code center ± critical·se}, the
 * center and the standard error exact where the data are, the critical value the quantile of the
 * normal or t distribution at {@code (1 + C)/2} through the {@link Distribute} provider, and the
 * bounds decimals of at most 16 digits. The steps share the tests' rule set: the critical value,
 * then the interval.
 */
final class Intervals {

  /** The steps, in the tests' rule set. */
  static final Set<String> STEPS = Set.of("critical", "interval");

  /** The intervals; {@code tinterval2} is a command, which reads the word {@code pooled}. */
  static final Set<String> NAMES =
      Set.of("zinterval", "tinterval", "tinterval2", "propinterval", "propinterval2");

  private static final int INPUT_DIGITS = 17;

  private final Context ctx;
  private final Arith ar;
  private final String name;
  private final Descriptive moments;

  private Intervals(String name, Context ctx) {
    this.ctx = ctx;
    this.ar = new Arith(ctx);
    this.name = name;
    this.moments = Descriptive.of(ctx);
  }

  /**
   * Evaluates an interval whose arguments are evaluated.
   *
   * @param call the call, without the word {@code pooled}
   * @param pooled whether the word {@code pooled} was given
   * @param ctx the precision, trace, numerics, providers and cancellation
   * @return the value and its steps, or empty when the call stays
   * @throws IllegalArgumentException with the language's message for a wrong argument
   */
  static Optional<Rewriter.Result> apply(Apply call, boolean pooled, Context ctx) {
    return new Intervals(call.head().name(), ctx).evaluate(call, pooled);
  }

  private Optional<Rewriter.Result> evaluate(Apply call, boolean pooled) {
    List<Expr> args = call.args();
    if (pooled && !name.equals("tinterval2")) {
      throw new IllegalArgumentException(
          "`" + name + "` has no `pooled` option: `pooled` belongs to `ttest2` and `tinterval2`");
    }
    Optional<Data.Entry> level = Data.real(name, args.getLast(), "level", ctx);
    if (level.isEmpty()) {
      return Optional.empty();
    }
    RealNumeric c = level.get().key();
    if (c.signum() <= 0 || c.compareValue(Int.ONE) >= 0) {
      throw new IllegalArgumentException("`" + name + "` needs a confidence level between 0 and 1");
    }
    // the upper tail (1 − C)/2 of the critical value, formed exactly
    double tail =
        toDouble(
            (RealNumeric) Int.ONE.subtract(c, ctx.precision()).divide(Int.TWO, ctx.precision()));
    List<Expr> rest = args.subList(0, args.size() - 1);
    Record steps = new Record(call, ctx);
    Expr center;
    Expr se;
    Law law = Law.NORMAL;
    double df = 0;
    switch (name) {
      case "zinterval", "tinterval" -> {
        boolean z = name.equals("zinterval");
        if (rest.size() == (z ? 2 : 1)) {
          Optional<Sample> s = Data.sample(name, rest.getFirst(), null, z ? 1 : 2, ctx);
          Optional<Data.Entry> sigma =
              z ? Data.real(name, rest.get(1), "σ", ctx) : Optional.empty();
          if (s.isEmpty() || z && sigma.isEmpty()) {
            return Optional.empty();
          }
          center = moments.mean(s.get());
          Expr spread = z ? sigma.get().value() : ar.sqrt(moments.variance(s.get()));
          if (sign(spread) <= 0) {
            throw new IllegalArgumentException("`" + name + "` needs " + (z ? "σ" : "s") + " > 0");
          }
          se = ar.div(spread, ar.sqrt(Arith.num(s.get().n())));
          df = s.get().n().doubleValue() - 1;
        } else if (rest.size() == 3) {
          List<Data.Entry> e = reals(rest, List.of("x̄", z ? "σ" : "s", "n"));
          if (e == null) {
            return Optional.empty();
          }
          if (!(e.get(2).key() instanceof Int n && n.signum() > 0)) {
            throw new IllegalArgumentException("`" + name + "` needs a positive integer n");
          }
          if (e.get(1).key().signum() <= 0) {
            throw new IllegalArgumentException("`" + name + "` needs " + (z ? "σ" : "s") + " > 0");
          }
          center = e.get(0).value();
          se = ar.div(e.get(1).value(), ar.sqrt(e.get(2).value()));
          df = n.value().doubleValue() - 1;
        } else {
          throw new IllegalArgumentException(
              "`"
                  + name
                  + "` takes the data"
                  + (z ? ", σ" : "")
                  + " and the level, or x̄, "
                  + (z ? "σ" : "s")
                  + ", n and the level");
        }
        if (!z) {
          if (df < 1) {
            throw new IllegalArgumentException("`tinterval` needs at least 2 values");
          }
          law = Law.STUDENT_T;
        }
      }
      case "tinterval2" -> {
        if (rest.size() != 2) {
          throw new IllegalArgumentException("`tinterval2` takes two samples and the level");
        }
        Optional<Sample> a = Data.sample(name, rest.get(0), null, 2, ctx);
        Optional<Sample> b = Data.sample(name, rest.get(1), null, 2, ctx);
        if (a.isEmpty() || b.isEmpty()) {
          return Optional.empty();
        }
        Expr n1 = Arith.num(a.get().n());
        Expr n2 = Arith.num(b.get().n());
        Expr v1 = moments.variance(a.get());
        Expr v2 = moments.variance(b.get());
        center = ar.sub(moments.mean(a.get()), moments.mean(b.get()));
        Expr dfe;
        if (pooled) {
          Expr sp2 =
              ar.div(
                  ar.add(ar.mul(ar.sub(n1, Num.ONE), v1), ar.mul(ar.sub(n2, Num.ONE), v2)),
                  ar.sub(ar.add(n1, n2), Num.of(2)));
          se = ar.sqrt(ar.mul(sp2, ar.add(ar.div(Num.ONE, n1), ar.div(Num.ONE, n2))));
          dfe = ar.sub(ar.add(n1, n2), Num.of(2));
        } else {
          Expr a1 = ar.div(v1, n1);
          Expr a2 = ar.div(v2, n2);
          se = ar.sqrt(ar.add(a1, a2));
          dfe =
              ar.div(
                  ar.pow(ar.add(a1, a2), 2),
                  ar.add(
                      ar.div(ar.pow(a1, 2), ar.sub(n1, Num.ONE)),
                      ar.div(ar.pow(a2, 2), ar.sub(n2, Num.ONE))));
        }
        df = toDouble(dfe);
        law = Law.STUDENT_T;
      }
      case "propinterval" -> {
        List<Data.Entry> e = reals(rest, List.of("x", "n"));
        if (e == null) {
          return Optional.empty();
        }
        BigInteger[] xn = successes(e.get(0), e.get(1));
        Expr n = Arith.num(xn[1]);
        center = ar.div(Arith.num(xn[0]), n);
        se = ar.sqrt(ar.div(ar.mul(center, ar.sub(Num.ONE, center)), n));
      }
      case "propinterval2" -> {
        List<Data.Entry> e = reals(rest, List.of("x₁", "n₁", "x₂", "n₂"));
        if (e == null) {
          return Optional.empty();
        }
        BigInteger[] a = successes(e.get(0), e.get(1));
        BigInteger[] b = successes(e.get(2), e.get(3));
        Expr n1 = Arith.num(a[1]);
        Expr n2 = Arith.num(b[1]);
        Expr p1 = ar.div(Arith.num(a[0]), n1);
        Expr p2 = ar.div(Arith.num(b[0]), n2);
        center = ar.sub(p1, p2);
        se =
            ar.sqrt(
                ar.add(
                    ar.div(ar.mul(p1, ar.sub(Num.ONE, p1)), n1),
                    ar.div(ar.mul(p2, ar.sub(Num.ONE, p2)), n2)));
      }
      default -> throw new IllegalStateException("not an interval: " + name);
    }
    double critical =
        quantile(law, law == Law.NORMAL ? new double[] {0, 1} : new double[] {df}, tail);
    MathContext mc = ctx.precision();
    TreeMap<String, Expr> values = new TreeMap<>();
    values.put("name", Symbol.of(law == Law.NORMAL ? "z" : "t"));
    values.put("critical", Distributions.decimal(critical, mc));
    values.put("level", level.get().original());
    steps.step(Tests.RULE_SET, "critical", Distributions.decimal(critical, mc), values);
    double mid = toDouble(center);
    double half = critical * toDouble(se);
    Expr bounds =
        new Vector(
            List.of(Distributions.decimal(mid - half, mc), Distributions.decimal(mid + half, mc)));
    TreeMap<String, Expr> interval = new TreeMap<>();
    interval.put("center", tidy(center));
    interval.put("se", tidy(se));
    interval.put("critical", Distributions.decimal(critical, mc));
    steps.step(Tests.RULE_SET, "interval", bounds, interval);
    return Optional.of(steps.result());
  }

  private Expr tidy(Expr e) {
    return mathcanvas.engine.simplify.Simplifier.simplify(e, ctx.withTrace(false)).expr();
  }

  private BigInteger[] successes(Data.Entry x, Data.Entry n) {
    if (!(n.key() instanceof Int nn && nn.signum() > 0)) {
      throw new IllegalArgumentException("`" + name + "` needs a positive integer n");
    }
    if (!(x.key() instanceof Int xx && xx.signum() >= 0 && xx.compareValue(nn) <= 0)) {
      throw new IllegalArgumentException("`" + name + "` needs an integer x with 0 ≤ x ≤ n");
    }
    return new BigInteger[] {xx.value(), nn.value()};
  }

  private List<Data.Entry> reals(List<Expr> args, List<String> what) {
    if (args.size() != what.size()) {
      throw new IllegalArgumentException(
          "`" + name + "` takes " + String.join(", ", what) + " and the level");
    }
    List<Data.Entry> out = new ArrayList<>();
    for (int i = 0; i < args.size(); i++) {
      Optional<Data.Entry> r = Data.real(name, args.get(i), what.get(i), ctx);
      if (r.isEmpty()) {
        return null;
      }
      out.add(r.get());
    }
    return out;
  }

  /** The quantile of the upper tail {@code tail}. */
  private double quantile(Law law, double[] p, double tail) {
    DistributionValue v;
    try {
      v =
          ctx.providers()
              .run(new Distribute(law, Kind.UPPER_INVERSE, p, new double[] {tail}), ctx, Steps.NONE)
              .value();
    } catch (IllegalArgumentException outOfRange) {
      throw new IllegalArgumentException(
          "`" + name + "`'s parameters are too large to approximate");
    }
    if (v.status() != DistributionValue.Status.OK) {
      throw new IllegalArgumentException(
          "`" + name + "`'s critical value is too large to approximate");
    }
    return v.value();
  }

  /** The sign of a real constant's value. */
  private int sign(Expr e) {
    if (e instanceof Num n && n.value() instanceof RealNumeric r) {
      return r.signum();
    }
    Optional<Numeric> v = ctx.numerics().value(e, ctx.precision());
    return v.isPresent() && v.get() instanceof RealNumeric r ? r.signum() : 0;
  }

  private double toDouble(RealNumeric r) {
    return r.toDouble();
  }

  private double toDouble(Expr e) {
    if (e instanceof Num n && n.value() instanceof RealNumeric r) {
      return r.toDouble();
    }
    Optional<Numeric> v =
        ctx.numerics().value(e, new MathContext(INPUT_DIGITS, RoundingMode.HALF_EVEN));
    if (v.isPresent() && v.get() instanceof RealNumeric r) {
      return r.toDouble();
    }
    throw new IllegalArgumentException("`" + name + "` needs real values");
  }
}
