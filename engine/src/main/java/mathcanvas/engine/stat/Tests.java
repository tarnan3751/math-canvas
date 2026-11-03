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
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.provider.Distribute;
import mathcanvas.engine.provider.Distribute.Kind;
import mathcanvas.engine.provider.Distribute.Law;
import mathcanvas.engine.provider.DistributionValue;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.simplify.Simplifier;
import mathcanvas.engine.stat.Data.Sample;

/**
 * The hypothesis tests ({@code docs/language.md} §14.25): the statistic exact where the data are,
 * computed from the rationals decimal data denote and rounded once, the degrees of freedom exact,
 * and the {@code p}-value numeric, through the {@link Distribute} provider. The narration states
 * the hypotheses, the summary of the data, the statistic's formula, the degrees of freedom and the
 * {@code p}-value; it reaches no decision at a level.
 */
final class Tests {

  /** The rule set of the steps, narrated by {@link StatisticsNarrator}. */
  static final String RULE_SET = "test";

  /** The steps. */
  static final Set<String> STEPS =
      Set.of(
          "hypotheses.two",
          "hypotheses.less",
          "hypotheses.greater",
          "summary",
          "summary-z",
          "summary2",
          "summary-paired",
          "expected",
          "z",
          "t",
          "t2",
          "t2-pooled",
          "prop",
          "prop2",
          "chi2",
          "chi2-independence",
          "anova",
          "df",
          "df-welch",
          "p.two",
          "p.less",
          "p.greater");

  /** The tests that take a trailing alternative, and {@code ttest2} the word {@code pooled}. */
  static final Set<String> COMMANDS =
      Set.of("ztest", "ttest", "ttest2", "pairedttest", "proptest", "proptest2");

  /** The functions, which take no word. */
  static final Set<String> FUNCTIONS = Set.of("chi2gof", "chi2test", "anova");

  /** The alternatives, as written. */
  static final Set<String> ALTERNATIVES = Set.of("two", "less", "greater");

  /** The digits a statistic is taken to before it becomes a {@code double}. */
  private static final int INPUT_DIGITS = 17;

  /**
   * A value and its steps.
   *
   * @param result the value and its steps
   */
  record Value(Rewriter.Result result) {}

  private final Context ctx;
  private final Arith ar;
  private final String name;
  private final Descriptive moments;

  private Tests(String name, Context ctx) {
    this.ctx = ctx;
    this.ar = new Arith(ctx);
    this.name = name;
    this.moments = Descriptive.of(ctx);
  }

  /**
   * Evaluates a test whose arguments are evaluated, its words read.
   *
   * @param call the call, without its trailing words
   * @param alternative {@code two}, {@code less} or {@code greater}
   * @param pooled whether the word {@code pooled} was given
   * @param ctx the precision, trace, numerics, providers and cancellation
   * @return the value, or empty when the call stays
   * @throws IllegalArgumentException with the language's message for a wrong argument
   */
  static Optional<Value> apply(Apply call, String alternative, boolean pooled, Context ctx) {
    return new Tests(call.head().name(), ctx).evaluate(call, alternative, pooled);
  }

  private Optional<Value> evaluate(Apply call, String alternative, boolean pooled) {
    List<Expr> args = call.args();
    if (pooled && !name.equals("ttest2")) {
      throw new IllegalArgumentException(name + " takes no word pooled");
    }
    if (FUNCTIONS.contains(name) && alternative != null) {
      throw new IllegalArgumentException(name + " takes no alternative");
    }
    String alt = alternative == null ? "two" : alternative;
    Record steps = new Record(call, ctx);
    return switch (name) {
      case "ztest", "ttest" -> oneSample(call, args, alt, steps);
      case "ttest2" -> twoSamples(args, alt, pooled, steps);
      case "pairedttest" -> paired(args, alt, steps);
      case "proptest" -> proportion(args, alt, steps);
      case "proptest2" -> proportions(args, alt, steps);
      case "chi2gof" -> goodnessOfFit(args, steps);
      case "chi2test" -> independence(args, steps);
      case "anova" -> anova(args, steps);
      default -> throw new IllegalStateException("not a test: " + name);
    };
  }

  // One sample.

  private Optional<Value> oneSample(Apply call, List<Expr> args, String alt, Record steps) {
    boolean z = name.equals("ztest");
    int dataForm = z ? 3 : 2;
    int summaryForm = 4;
    Expr mean;
    Expr spread;
    Expr n;
    Expr mu0;
    boolean decimal;
    if (args.size() == dataForm) {
      Optional<Sample> s = Data.sample(name, args.getFirst(), null, z ? 1 : 2, ctx);
      Optional<Data.Entry> m0 = Data.real(name, args.getLast(), "μ₀", ctx);
      Optional<Data.Entry> sigma = z ? Data.real(name, args.get(1), "σ", ctx) : Optional.empty();
      if (s.isEmpty() || m0.isEmpty() || z && sigma.isEmpty()) {
        return Optional.empty();
      }
      Sample d = s.get();
      mean = moments.mean(d);
      spread = z ? sigma.get().value() : ar.sqrt(moments.variance(d));
      n = Arith.num(d.n());
      mu0 = m0.get().value();
      decimal =
          d.decimal() || Data.holdsDecimal(args.getLast()) || z && Data.holdsDecimal(args.get(1));
      hypotheses(steps, Symbol.of("μ"), mu0, alt);
      TreeMap<String, Expr> values = new TreeMap<>();
      values.put("n", n);
      values.put("mean", out(mean, decimal));
      values.put("s", out(spread, decimal));
      steps.step(
          RULE_SET,
          z ? "summary-z" : "summary",
          Apply.of(call.head(), out(mean, decimal), out(spread, decimal), n, mu0),
          values);
    } else if (args.size() == summaryForm) {
      List<Data.Entry> e = new ArrayList<>();
      for (Expr a : args) {
        Optional<Data.Entry> r = Data.real(name, a, "argument", ctx);
        if (r.isEmpty()) {
          return Optional.empty();
        }
        e.add(r.get());
      }
      mean = e.get(0).value();
      spread = e.get(1).value();
      n = e.get(2).value();
      mu0 = e.get(3).value();
      if (!(e.get(2).key() instanceof Int k && k.signum() > 0)) {
        throw new IllegalArgumentException(name + " needs a positive integer n");
      }
      if (!z && k.compareValue(Int.of(2)) < 0) {
        throw new IllegalArgumentException(name + " needs at least 2 values");
      }
      decimal = args.stream().anyMatch(Data::holdsDecimal);
      hypotheses(steps, Symbol.of("μ"), mu0, alt);
    } else {
      if (args.getLast() instanceof Symbol) {
        throw new IllegalArgumentException(name + "'s alternative must be two, less or greater");
      }
      throw new IllegalArgumentException(
          name
              + " takes the data"
              + (z ? ", σ" : "")
              + " and μ₀, or x̄, "
              + (z ? "σ" : "s")
              + ", n and μ₀");
    }
    if (positive(spread) <= 0) {
      throw new IllegalArgumentException(name + " needs " + (z ? "σ" : "s") + " > 0");
    }
    Expr statistic = ar.div(ar.sub(mean, mu0), ar.div(spread, ar.sqrt(n)));
    TreeMap<String, Expr> values = new TreeMap<>();
    values.put("mean", out(mean, decimal));
    values.put("null", mu0);
    values.put("s", out(spread, decimal));
    values.put("n", n);
    Symbol letter = Symbol.of(z ? "z" : "t");
    List<Expr> items = new ArrayList<>();
    items.add(equation(letter, out(statistic, decimal)));
    steps.step(RULE_SET, z ? "z" : "t", new ListExpr(items), values);
    if (z) {
      return finish(steps, items, statistic, Law.NORMAL, new double[] {0, 1}, alt);
    }
    Expr df = ar.sub(n, Num.ONE);
    items.add(equation(Symbol.of("df"), out(df, false)));
    steps.step(RULE_SET, "df", new ListExpr(items), new TreeMap<>());
    return finish(steps, items, statistic, Law.STUDENT_T, new double[] {toDouble(df)}, alt);
  }

  // Two samples.

  private Optional<Value> twoSamples(List<Expr> args, String alt, boolean pooled, Record steps) {
    if (args.size() != 2) {
      throw new IllegalArgumentException("ttest2 takes two samples");
    }
    Optional<Sample> a = Data.sample(name, args.get(0), null, 2, ctx);
    Optional<Sample> b = Data.sample(name, args.get(1), null, 2, ctx);
    if (a.isEmpty() || b.isEmpty()) {
      return Optional.empty();
    }
    Sample x = a.get();
    Sample y = b.get();
    boolean decimal = x.decimal() || y.decimal();
    hypotheses(steps, difference("μ"), Num.ZERO, alt);
    Expr n1 = Arith.num(x.n());
    Expr n2 = Arith.num(y.n());
    Expr m1 = moments.mean(x);
    Expr m2 = moments.mean(y);
    Expr v1 = moments.variance(x);
    Expr v2 = moments.variance(y);
    TreeMap<String, Expr> summary = new TreeMap<>();
    summary.put("n1", n1);
    summary.put("n2", n2);
    summary.put("mean1", out(m1, decimal));
    summary.put("mean2", out(m2, decimal));
    summary.put("s1", out(ar.sqrt(v1), decimal));
    summary.put("s2", out(ar.sqrt(v2), decimal));
    steps.step(
        RULE_SET,
        "summary2",
        new Matrix(
            List.of(
                List.of(n1, out(m1, decimal), out(ar.sqrt(v1), decimal)),
                List.of(n2, out(m2, decimal), out(ar.sqrt(v2), decimal)))),
        summary);
    Expr statistic;
    Expr df;
    String dfStep;
    if (pooled) {
      Expr sp2 =
          ar.div(
              ar.add(ar.mul(ar.sub(n1, Num.ONE), v1), ar.mul(ar.sub(n2, Num.ONE), v2)),
              ar.sub(ar.add(n1, n2), Num.of(2)));
      statistic =
          ar.div(
              ar.sub(m1, m2),
              ar.sqrt(ar.mul(sp2, ar.add(ar.div(Num.ONE, n1), ar.div(Num.ONE, n2)))));
      df = ar.sub(ar.add(n1, n2), Num.of(2));
      dfStep = "df";
    } else {
      Expr a1 = ar.div(v1, n1);
      Expr a2 = ar.div(v2, n2);
      statistic = ar.div(ar.sub(m1, m2), ar.sqrt(ar.add(a1, a2)));
      df =
          ar.div(
              ar.pow(ar.add(a1, a2), 2),
              ar.add(
                  ar.div(ar.pow(a1, 2), ar.sub(n1, Num.ONE)),
                  ar.div(ar.pow(a2, 2), ar.sub(n2, Num.ONE))));
      dfStep = "df-welch";
    }
    if (positive(ar.add(v1, v2)) <= 0) {
      throw new IllegalArgumentException("ttest2 needs samples that are not both constant");
    }
    List<Expr> items = new ArrayList<>();
    items.add(equation(Symbol.of("t"), out(statistic, decimal)));
    steps.step(RULE_SET, pooled ? "t2-pooled" : "t2", new ListExpr(items), new TreeMap<>());
    items.add(equation(Symbol.of("df"), out(df, decimal)));
    steps.step(RULE_SET, dfStep, new ListExpr(items), new TreeMap<>());
    return finish(steps, items, statistic, Law.STUDENT_T, new double[] {toDouble(df)}, alt);
  }

  private Optional<Value> paired(List<Expr> args, String alt, Record steps) {
    if (args.size() > 2) {
      throw new IllegalArgumentException(
          "pairedttest takes two samples or a matrix of two columns");
    }
    Optional<List<Sample>> pairs = Data.pairs(name, args, 2, ctx);
    if (pairs.isEmpty()) {
      return Optional.empty();
    }
    Sample x = pairs.get().get(0);
    Sample y = pairs.get().get(1);
    List<Expr> d = new ArrayList<>();
    for (int i = 0; i < x.entries().size(); i++) {
      d.add(ar.sub(x.entries().get(i).value(), y.entries().get(i).value()));
    }
    Sample diff =
        Data.sample(name, new mathcanvas.engine.expr.Vector(d), null, 2, ctx).orElseThrow();
    boolean decimal = x.decimal() || y.decimal();
    hypotheses(steps, Symbol.of("μ_d"), Num.ZERO, alt);
    Expr mean = moments.mean(diff);
    Expr s = ar.sqrt(moments.variance(diff));
    Expr n = Arith.num(diff.n());
    TreeMap<String, Expr> values = new TreeMap<>();
    values.put("n", n);
    values.put("mean", out(mean, decimal));
    values.put("s", out(s, decimal));
    steps.step(
        RULE_SET,
        "summary-paired",
        new mathcanvas.engine.expr.Vector(d.stream().map(v -> out(v, decimal)).toList()),
        values);
    if (positive(s) <= 0) {
      throw new IllegalArgumentException("pairedttest needs differences that are not all equal");
    }
    Expr statistic = ar.div(mean, ar.div(s, ar.sqrt(n)));
    values.put("null", Num.ZERO);
    List<Expr> items = new ArrayList<>();
    items.add(equation(Symbol.of("t"), out(statistic, decimal)));
    steps.step(RULE_SET, "t", new ListExpr(items), values);
    Expr df = ar.sub(n, Num.ONE);
    items.add(equation(Symbol.of("df"), out(df, false)));
    steps.step(RULE_SET, "df", new ListExpr(items), new TreeMap<>());
    return finish(steps, items, statistic, Law.STUDENT_T, new double[] {toDouble(df)}, alt);
  }

  // Proportions.

  private Optional<Value> proportion(List<Expr> args, String alt, Record steps) {
    List<Data.Entry> e = reals(args, List.of("x", "n", "p₀"));
    if (e == null) {
      return Optional.empty();
    }
    BigInteger[] xn = successes(e.get(0), e.get(1));
    Expr p0 = e.get(2).value();
    RealNumeric p0v = e.get(2).key();
    if (p0v.signum() <= 0 || p0v.compareValue(Int.ONE) >= 0) {
      throw new IllegalArgumentException(name + " needs 0 < p₀ < 1");
    }
    boolean decimal = Data.holdsDecimal(args.get(2));
    hypotheses(steps, Symbol.of("p"), p0, alt);
    Expr n = Arith.num(xn[1]);
    Expr phat = ar.div(Arith.num(xn[0]), n);
    Expr statistic = ar.div(ar.sub(phat, p0), ar.sqrt(ar.div(ar.mul(p0, ar.sub(Num.ONE, p0)), n)));
    TreeMap<String, Expr> values = new TreeMap<>();
    values.put("phat", out(phat, false));
    values.put("null", p0);
    values.put("n", n);
    List<Expr> items = new ArrayList<>();
    items.add(equation(Symbol.of("z"), out(statistic, decimal)));
    steps.step(RULE_SET, "prop", new ListExpr(items), values);
    return finish(steps, items, statistic, Law.NORMAL, new double[] {0, 1}, alt);
  }

  private Optional<Value> proportions(List<Expr> args, String alt, Record steps) {
    List<Data.Entry> e = reals(args, List.of("x₁", "n₁", "x₂", "n₂"));
    if (e == null) {
      return Optional.empty();
    }
    BigInteger[] a = successes(e.get(0), e.get(1));
    BigInteger[] b = successes(e.get(2), e.get(3));
    hypotheses(steps, difference("p"), Num.ZERO, alt);
    Expr n1 = Arith.num(a[1]);
    Expr n2 = Arith.num(b[1]);
    Expr p1 = ar.div(Arith.num(a[0]), n1);
    Expr p2 = ar.div(Arith.num(b[0]), n2);
    Expr p = ar.div(Arith.num(a[0].add(b[0])), Arith.num(a[1].add(b[1])));
    Expr se =
        ar.sqrt(
            ar.mul(
                ar.mul(p, ar.sub(Num.ONE, p)), ar.add(ar.div(Num.ONE, n1), ar.div(Num.ONE, n2))));
    if (positive(se) <= 0) {
      throw new IllegalArgumentException(
          "proptest2 needs a pooled proportion strictly between 0 and 1");
    }
    Expr statistic = ar.div(ar.sub(p1, p2), se);
    TreeMap<String, Expr> values = new TreeMap<>();
    values.put("phat1", out(p1, false));
    values.put("phat2", out(p2, false));
    values.put("phat", out(p, false));
    List<Expr> items = new ArrayList<>();
    items.add(equation(Symbol.of("z"), out(statistic, false)));
    steps.step(RULE_SET, "prop2", new ListExpr(items), values);
    return finish(steps, items, statistic, Law.NORMAL, new double[] {0, 1}, alt);
  }

  /**
   * {@code [x, n]} with {@code n} a positive integer and {@code x} an integer in {@code [0, n]}.
   */
  private BigInteger[] successes(Data.Entry x, Data.Entry n) {
    if (!(n.key() instanceof Int nn && nn.signum() > 0)) {
      throw new IllegalArgumentException(name + " needs a positive integer n");
    }
    if (!(x.key() instanceof Int xx && xx.signum() >= 0 && xx.compareValue(nn) <= 0)) {
      throw new IllegalArgumentException(name + " needs an integer x with 0 ≤ x ≤ n");
    }
    return new BigInteger[] {xx.value(), nn.value()};
  }

  private List<Data.Entry> reals(List<Expr> args, List<String> what) {
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

  // χ² and the analysis of variance.

  /** The relative difference allowed between the observed and the expected totals of chi2gof. */
  private static final double TOTALS_TOLERANCE = 1e-3;

  private Optional<Value> goodnessOfFit(List<Expr> args, Record steps) {
    Optional<Sample> o = Data.sample(name, args.get(0), null, 1, ctx);
    Optional<Sample> e = Data.sample(name, args.get(1), null, 1, ctx);
    if (o.isEmpty() || e.isEmpty()) {
      return Optional.empty();
    }
    List<Data.Entry> observed = o.get().entries();
    List<Data.Entry> expected = e.get().entries();
    if (observed.size() != expected.size() || observed.size() < 2) {
      throw new IllegalArgumentException(
          "chi2gof needs observed and expected counts of the same length, at least 2");
    }
    boolean decimal = o.get().decimal() || e.get().decimal();
    Expr observedTotal = ar.sum(observed.stream().map(Data.Entry::value).toList());
    Expr expectedTotal = ar.sum(expected.stream().map(Data.Entry::value).toList());
    // rounded expected counts ([33.33, 33.33, 33.33] against 100) pass within a relative 1e-3
    if (Math.abs(toDouble(ar.sub(observedTotal, expectedTotal)))
        > TOTALS_TOLERANCE * Math.abs(toDouble(observedTotal))) {
      throw new IllegalArgumentException(
          "chi2gof needs expected counts that sum to the observed total: "
              + UnicodePrinter.INSTANCE.print(out(expectedTotal, decimal))
              + " against "
              + UnicodePrinter.INSTANCE.print(out(observedTotal, decimal)));
    }
    List<Expr> terms = new ArrayList<>();
    for (int i = 0; i < observed.size(); i++) {
      if (expected.get(i).key().signum() <= 0) {
        throw new IllegalArgumentException("chi2gof needs positive expected counts");
      }
      Expr diff = ar.sub(observed.get(i).value(), expected.get(i).value());
      terms.add(ar.div(ar.pow(diff, 2), expected.get(i).value()));
    }
    return chiSquared(steps, "chi2", ar.sum(terms), Arith.num(observed.size() - 1), decimal);
  }

  private Optional<Value> independence(List<Expr> args, Record steps) {
    Expr m = args.getFirst();
    if (!(m instanceof Matrix table)) {
      if (Data.holdsSymbol(m)) {
        return Optional.empty();
      }
      throw new IllegalArgumentException(
          "chi2test needs a matrix of at least 2 rows and 2 columns");
    }
    int rows = table.rows().size();
    int columns = table.rows().getFirst().size();
    if (rows < 2 || columns < 2) {
      throw new IllegalArgumentException(
          "chi2test needs a matrix of at least 2 rows and 2 columns");
    }
    List<Data.Entry> cells = new ArrayList<>();
    for (List<Expr> row : table.rows()) {
      for (Expr c : row) {
        Optional<Data.Entry> r = Data.real(name, c, "count", ctx);
        if (r.isEmpty()) {
          return Optional.empty();
        }
        if (r.get().key().signum() < 0) {
          throw new IllegalArgumentException("chi2test needs counts that are not negative");
        }
        cells.add(r.get());
      }
    }
    boolean decimal = Data.holdsDecimal(m);
    List<Expr> rowTotals = new ArrayList<>();
    List<Expr> columnTotals = new ArrayList<>();
    for (int i = 0; i < rows; i++) {
      List<Expr> t = new ArrayList<>();
      for (int j = 0; j < columns; j++) {
        t.add(cells.get(i * columns + j).value());
      }
      rowTotals.add(ar.sum(t));
    }
    for (int j = 0; j < columns; j++) {
      List<Expr> t = new ArrayList<>();
      for (int i = 0; i < rows; i++) {
        t.add(cells.get(i * columns + j).value());
      }
      columnTotals.add(ar.sum(t));
    }
    Expr total = ar.sum(rowTotals);
    List<Expr> terms = new ArrayList<>();
    List<List<Expr>> expected = new ArrayList<>();
    for (int i = 0; i < rows; i++) {
      List<Expr> row = new ArrayList<>();
      for (int j = 0; j < columns; j++) {
        Expr e = ar.div(ar.mul(rowTotals.get(i), columnTotals.get(j)), total);
        if (positive(e) <= 0) {
          throw new IllegalArgumentException("chi2test needs every row and column total positive");
        }
        row.add(out(e, decimal));
        Expr diff = ar.sub(cells.get(i * columns + j).value(), e);
        terms.add(ar.div(ar.pow(diff, 2), e));
      }
      expected.add(row);
    }
    TreeMap<String, Expr> values = new TreeMap<>();
    values.put("expected", new Matrix(expected));
    steps.step(RULE_SET, "expected", new Matrix(expected), values);
    return chiSquared(
        steps,
        "chi2-independence",
        ar.sum(terms),
        Arith.num((long) (rows - 1) * (columns - 1)),
        decimal);
  }

  private Optional<Value> chiSquared(
      Record steps, String step, Expr statistic, Expr df, boolean decimal) {
    List<Expr> items = new ArrayList<>();
    items.add(equation(ar.pow(Symbol.of("χ"), 2), out(statistic, decimal)));
    steps.step(RULE_SET, step, new ListExpr(items), new TreeMap<>());
    items.add(equation(Symbol.of("df"), df));
    steps.step(RULE_SET, "df", new ListExpr(items), new TreeMap<>());
    return finish(steps, items, statistic, Law.CHI_SQUARED, new double[] {toDouble(df)}, "greater");
  }

  private Optional<Value> anova(List<Expr> args, Record steps) {
    List<Sample> groups = new ArrayList<>();
    boolean decimal = false;
    for (Expr g : args) {
      Optional<Sample> s = Data.sample(name, g, null, 1, ctx);
      if (s.isEmpty()) {
        return Optional.empty();
      }
      groups.add(s.get());
      decimal |= s.get().decimal();
    }
    int k = groups.size();
    BigInteger total = BigInteger.ZERO;
    List<Expr> all = new ArrayList<>();
    for (Sample s : groups) {
      total = total.add(s.n());
      all.addAll(s.values());
    }
    if (total.compareTo(BigInteger.valueOf(k)) <= 0) {
      throw new IllegalArgumentException("anova needs more values than groups");
    }
    Expr grand = ar.div(ar.sum(all), Arith.num(total));
    List<Expr> between = new ArrayList<>();
    List<Expr> within = new ArrayList<>();
    for (Sample s : groups) {
      Expr m = moments.mean(s);
      between.add(ar.mul(Arith.num(s.n()), ar.pow(ar.sub(m, grand), 2)));
      for (Expr v : s.values()) {
        within.add(ar.pow(ar.sub(v, m), 2));
      }
    }
    Expr ssb = ar.sum(between);
    Expr ssw = ar.sum(within);
    if (positive(ssw) <= 0) {
      throw new IllegalArgumentException("anova needs groups that are not all constant");
    }
    Expr df1 = Arith.num(k - 1);
    Expr df2 = Arith.num(total.subtract(BigInteger.valueOf(k)));
    Expr statistic = ar.div(ar.div(ssb, df1), ar.div(ssw, df2));
    TreeMap<String, Expr> values = new TreeMap<>();
    values.put("ssb", out(ssb, decimal));
    values.put("ssw", out(ssw, decimal));
    List<Expr> items = new ArrayList<>();
    items.add(equation(Symbol.of("F"), out(statistic, decimal)));
    steps.step(RULE_SET, "anova", new ListExpr(items), values);
    items.add(equation(Symbol.of("df1"), df1));
    items.add(equation(Symbol.of("df2"), df2));
    steps.step(RULE_SET, "df", new ListExpr(items), new TreeMap<>());
    return finish(
        steps, items, statistic, Law.F, new double[] {toDouble(df1), toDouble(df2)}, "greater");
  }

  // The p-value.

  private Optional<Value> finish(
      Record steps, List<Expr> items, Expr statistic, Law law, double[] p, String alt) {
    double s = toDouble(statistic);
    double v =
        switch (alt) {
          case "less" -> probability(law, p, Double.NEGATIVE_INFINITY, s);
          case "greater" -> probability(law, p, s, Double.POSITIVE_INFINITY);
          default -> 2 * probability(law, p, Math.abs(s), Double.POSITIVE_INFINITY);
        };
    items.add(equation(Symbol.of("p"), Distributions.decimal(Math.min(1.0, v), ctx.precision())));
    steps.step(RULE_SET, "p." + alt, new ListExpr(items), new TreeMap<>());
    return Optional.of(new Value(steps.result()));
  }

  private double probability(Law law, double[] p, double a, double b) {
    DistributionValue v;
    try {
      v =
          ctx.providers()
              .run(new Distribute(law, Kind.PROBABILITY, p, new double[] {a, b}), ctx, Steps.NONE)
              .value();
    } catch (IllegalArgumentException outOfRange) {
      throw new IllegalArgumentException(name + "'s parameters are beyond the double range");
    }
    if (v.status() != DistributionValue.Status.OK) {
      throw new IllegalArgumentException(name + "'s p-value is beyond the double range");
    }
    return v.value();
  }

  // Helpers.

  /** The step to the hypotheses, the list {@code [H₀, H₁]} of relations. */
  private void hypotheses(Record steps, Expr parameter, Expr value, String alt) {
    Relation.Op op =
        switch (alt) {
          case "less" -> Relation.Op.LT;
          case "greater" -> Relation.Op.GT;
          default -> Relation.Op.NE;
        };
    steps.step(
        RULE_SET,
        "hypotheses." + alt,
        new ListExpr(
            List.of(
                new Relation(Relation.Op.EQ, parameter, value),
                new Relation(op, parameter, value))),
        new TreeMap<>());
  }

  /** {@code μ₁ − μ₂}, or {@code p₁ − p₂}. */
  private static Expr difference(String letter) {
    return Sum.of(
        Symbol.of(letter + "_1"),
        mathcanvas.engine.expr.Product.of(Num.MINUS_ONE, Symbol.of(letter + "_2")));
  }

  private static Relation equation(Expr left, Expr right) {
    return new Relation(Relation.Op.EQ, left, right);
  }

  /** The sign of a real constant's value. */
  private int positive(Expr e) {
    if (e instanceof Num n && n.value() instanceof RealNumeric r) {
      return r.signum();
    }
    Optional<Numeric> v = ctx.numerics().value(e, ctx.precision());
    return v.isPresent() && v.get() instanceof RealNumeric r ? r.signum() : 0;
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
    throw new IllegalArgumentException(name + "'s statistic is not a real number");
  }

  /**
   * A value as the narration and the result show it: simplified on exact data, a decimal at the
   * precision on decimal data.
   */
  private Expr out(Expr value, boolean decimal) {
    if (!decimal) {
      return Simplifier.simplify(value, ctx.withTrace(false)).expr();
    }
    MathContext mc = ctx.precision();
    Optional<Numeric> v = ctx.numerics().value(value, mc);
    return v.isPresent() ? Num.of(v.get().toReal(mc)) : value;
  }
}
