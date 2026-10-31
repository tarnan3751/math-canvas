package mathcanvas.engine.calculus;

import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import mathcanvas.engine.algebra.AlgebraLimitException;
import mathcanvas.engine.assume.Facts;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Match;
import mathcanvas.engine.rule.Replacement;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;

/**
 * The rule sets {@code sum} and {@code product}: a {@code Σ} or {@code Π} node to its value, each
 * rule a family, or stays. Integer bounds with at most {@link Calculus#MAX_TERMS} terms are the
 * evaluator's, term by term through the session; these are the closed forms of the families named
 * in {@code docs/reference/engine.md}, <b>Series and sums</b>. Every rule is terminal: the node is
 * replaced by an expression without it.
 */
final class SumRules {

  private SumRules() {}

  /** The highest power of a polynomial summand, and of an even p-series, given a closed form. */
  static final int MAX_DEGREE = 200;

  /** The highest degree of a polynomial sum's closed form that is factored; above, expanded. */
  static final int MAX_FACTORED = 20;

  /** The largest shift between the bases of a telescoping summand. */
  static final int MAX_SHIFT = 100;

  /**
   * The most terms of a summand merged into one polynomial or one rational function, and the
   * highest degree of the merged denominator; a summand whose terms are partial fractions over
   * linear bases is taken term by term at any length.
   */
  static final int MAX_MERGED = 64;

  private static final MathContext EXACT = MathContext.UNLIMITED;

  static final RuleSet SUM =
      new RuleSet(
          "sum",
          List.of(
              rule("binomial", SumRules::binomial),
              rule("polynomial", SumRules::polynomial),
              rule("geometric", SumRules::geometric),
              rule("arithmetic-geometric", SumRules::arithmeticGeometric),
              rule("telescoping", SumRules::telescoping),
              rule("p-series", SumRules::pSeries)));

  static final RuleSet PRODUCT =
      new RuleSet(
          "product",
          List.of(
              rule("constant", m -> products(m, Kind.CONSTANT)),
              rule("factorial", m -> products(m, Kind.FACTORIAL)),
              rule("geometric", m -> products(m, Kind.GEOMETRIC))));

  /** A family: the value of the node, with the values its narration prints. */
  private interface Family {
    Optional<Replacement.Valued.Result> apply(Match m);
  }

  private static Rule rule(String name, Family f) {
    return Rule.valued(
        name,
        Pattern.parse("s_").where("s_", e -> e instanceof BigSum || e instanceof BigProduct),
        Set.of("ratio", "conditions"),
        m -> {
          try {
            return f.apply(m);
          } catch (AlgebraLimitException | ArithmeticException | IllegalArgumentException refused) {
            return Optional.empty();
          }
        });
  }

  /** The parts of a node. */
  private record Node(Expr body, Symbol k, Expr from, Expr to, boolean sum, Context ctx) {

    static Optional<Node> of(Match m, boolean sum) {
      Expr e = m.bindings().get("s_");
      Context c = m.context().withTrace(false);
      Node n;
      if (sum && e instanceof BigSum s) {
        n = new Node(s.body(), s.var(), s.from(), s.to(), true, c);
      } else if (!sum && e instanceof BigProduct p) {
        n = new Node(p.body(), p.var(), p.from(), p.to(), false, c);
      } else {
        return Optional.empty();
      }
      return n.wellBounded() && Signs.scalar(n.body()) ? Optional.of(n) : Optional.empty();
    }

    /**
     * Whether the bounds give a sum: each an integer or an expression with a symbol, the upper also
     * {@code ∞}, and integers no further apart than an empty sum.
     */
    boolean wellBounded() {
      if (!bound(from) || !(bound(to) || to.equals(Constant.INFINITY))) {
        return false;
      }
      Optional<Int> a = integer(from);
      Optional<Int> b = integer(to);
      return a.isEmpty()
          || b.isEmpty()
          || b.get().value().subtract(a.get().value()).add(BigInteger.ONE).signum() >= 0;
    }

    private static boolean bound(Expr e) {
      return integer(e).isPresent() || Signs.scalar(e) && Signs.finite(e) && Signs.hasSymbol(e);
    }

    /** The context inside the body, without the facts about the index. */
    Context inner() {
      return ctx.withAssumptions(ctx.assumptions().without(k));
    }

    boolean infinite() {
      return to.equals(Constant.INFINITY);
    }

    Expr canon(Expr e) {
      return Signs.canon(e, ctx);
    }

    /** The number of terms, {@code to − from + 1}. */
    Expr count() {
      return canon(Sum.of(to, Product.of(Num.MINUS_ONE, from), Num.ONE));
    }

    /** {@code e} with {@code k = v}. */
    Expr at(Expr e, Expr v) {
      return Subst.of(e, k, v, ctx.precision()).orElseThrow();
    }
  }

  private static Optional<Replacement.Valued.Result> result(Expr value) {
    return Optional.of(new Replacement.Valued.Result(value, new TreeMap<>()));
  }

  private static Optional<Replacement.Valued.Result> result(
      Expr value, Expr ratio, List<Expr> conditions) {
    TreeMap<String, Expr> values = new TreeMap<>();
    if (ratio != null) {
      values.put("ratio", ratio);
    }
    values.put("conditions", new SetExpr(conditions));
    return Optional.of(new Replacement.Valued.Result(value, values));
  }

  private static Optional<Int> integer(Expr e) {
    return e instanceof Num n && n.value() instanceof Int i ? Optional.of(i) : Optional.empty();
  }

  // The binomial sums.

  private static Optional<Replacement.Valued.Result> binomial(Match m) {
    Optional<Node> n = Node.of(m, true);
    if (n.isEmpty() || !n.get().from().equals(Num.ZERO)) {
      return Optional.empty();
    }
    Node node = n.get();
    Symbol k = node.k();
    Expr x = Num.ONE;
    Expr top = null;
    for (Expr f : node.body() instanceof Product p ? p.factors() : List.of(node.body())) {
      if (f instanceof Apply a
          && a.head().name().equals("binomial")
          && a.args().get(1).equals(k)
          && Free.of(a.args().get(0), k)
          && top == null) {
        top = a.args().get(0);
      } else if (f instanceof Power w
          && w.exponent().equals(k)
          && Free.of(w.base(), k)
          && x.equals(Num.ONE)) {
        x = w.base();
      } else {
        return Optional.empty();
      }
    }
    if (top == null || !node.to().equals(top)) {
      return Optional.empty();
    }
    return result(node.canon(Power.of(Sum.of(Num.ONE, x), top)));
  }

  // Polynomial summands: Faulhaber's formula.

  private static Optional<Replacement.Valued.Result> polynomial(Match m) {
    Optional<Node> n = Node.of(m, true);
    if (n.isEmpty()) {
      return Optional.empty();
    }
    Node node = n.get();
    Symbol k = node.k();
    if (node.body() instanceof Sum s && s.terms().size() > MAX_MERGED) {
      return Optional.empty();
    }
    Optional<List<Expr>> coefficients = coefficients(node.body(), k);
    if (coefficients.isEmpty() || Free.of(node.body(), k) && !Signs.finite(node.body())) {
      return Optional.empty();
    }
    List<Expr> c = coefficients.get();
    if (!Signs.finite(node.from()) || LimitMethods.infinite(node.from())) {
      return Optional.empty();
    }
    if (node.infinite()) {
      // a polynomial that is not zero diverges, to the sign of its leading coefficient
      Optional<Integer> s = Signs.sign(c.getLast(), node.ctx());
      if (s.isEmpty() || s.get() == 0) {
        return Optional.empty();
      }
      return result(LimitMethods.inf(s.get()));
    }
    if (!Signs.finite(node.to())) {
      return Optional.empty();
    }
    Expr below = node.canon(Sum.of(node.from(), Num.MINUS_ONE));
    List<Expr> terms = new ArrayList<>();
    List<RealNumeric> b = bernoulli(c.size() - 1);
    for (int p = 0; p < c.size(); p++) {
      Expr fp = faulhaber(p, node.to(), node, b);
      Expr fq = faulhaber(p, below, node, b);
      terms.add(Product.of(c.get(p), Sum.of(fp, Product.of(Num.MINUS_ONE, fq))));
    }
    Expr value = node.canon(LimitMethods.sumOf(terms));
    return result(factored(value, node.ctx()));
  }

  /** The coefficients of {@code e} as a polynomial in {@code k} of degree at most the bound. */
  private static Optional<List<Expr>> coefficients(Expr e, Symbol k) {
    mathcanvas.engine.algebra.Polynomial p = mathcanvas.engine.algebra.Polynomial.of(e);
    for (Expr v : p.variables()) {
      if (!v.equals(k) && !Free.of(v, k)) {
        return Optional.empty();
      }
    }
    int d = p.variables().contains(k) ? p.degree(k) : 0;
    if (d > MAX_DEGREE) {
      return Optional.empty();
    }
    List<Expr> out = new ArrayList<>();
    for (int i = 0; i <= d; i++) {
      out.add(p.variables().contains(k) ? p.coefficient(k, i).toExpr() : p.toExpr());
    }
    return Optional.of(out);
  }

  /**
   * A polynomial in symbols with rational coefficients factored, or expanded where it has no
   * factor; anything else as it is.
   */
  private static Expr factored(Expr value, Context ctx) {
    try {
      mathcanvas.engine.algebra.Polynomial p = mathcanvas.engine.algebra.Polynomial.of(value);
      if (!p.variables().stream().allMatch(v -> v instanceof Symbol)) {
        return value;
      }
      if (p.totalDegree() > MAX_FACTORED) {
        return mathcanvas.engine.algebra.Algebra.expand(value, ctx.withTrace(false)).expr();
      }
    } catch (IllegalArgumentException notPolynomial) {
      return value;
    }
    Context quiet = ctx.withTrace(false);
    Expr f =
        mathcanvas.engine.algebra.Algebra.factor(value, quiet).map(r -> r.expr()).orElse(value);
    return f instanceof Sum ? mathcanvas.engine.algebra.Algebra.expand(value, quiet).expr() : f;
  }

  /** {@code Σ_{k=1}^{n} k^p} as a polynomial in {@code n}: Faulhaber's formula. */
  private static Expr faulhaber(int p, Expr n, Node node, List<RealNumeric> b) {
    List<Expr> terms = new ArrayList<>();
    for (int j = 0; j <= p; j++) {
      RealNumeric bj = j == 1 ? Rational.of(1, 2) : b.get(j);
      if (bj.isZero()) {
        continue;
      }
      RealNumeric c =
          (RealNumeric) Int.of(binomial(p + 1, j)).multiply(bj, EXACT).divide(Int.of(p + 1), EXACT);
      terms.add(Product.of(Num.of(c), Power.of(n, Num.of(p + 1 - j))));
    }
    return node.canon(LimitMethods.sumOf(terms));
  }

  private static BigInteger binomial(int n, int k) {
    BigInteger r = BigInteger.ONE;
    for (int i = 0; i < k; i++) {
      r = r.multiply(BigInteger.valueOf(n - i)).divide(BigInteger.valueOf(i + 1));
    }
    return r;
  }

  /** The Bernoulli numbers {@code B_0 … B_n} with {@code B_1 = −1/2}, exactly. */
  static List<RealNumeric> bernoulli(int n) {
    List<RealNumeric> b = new ArrayList<>();
    b.add(Int.ONE);
    for (int m = 1; m <= n; m++) {
      RealNumeric s = Int.ZERO;
      BigInteger c = BigInteger.ONE;
      for (int j = 0; j < m; j++) {
        // c = C(m + 1, j), by the row
        s = (RealNumeric) s.add(Int.of(c).multiply(b.get(j), EXACT), EXACT);
        c = c.multiply(BigInteger.valueOf(m + 1 - j)).divide(BigInteger.valueOf(j + 1));
      }
      b.add((RealNumeric) s.negate().divide(Int.of(m + 1), EXACT));
    }
    return b;
  }

  // Geometric summands.

  /** {@code c·r^k}: the coefficient and the ratio, or empty. */
  private record Geometric(Expr coefficient, Expr ratio) {}

  private static Optional<Geometric> geometric(Expr body, Symbol k, Node node) {
    List<Expr> constant = new ArrayList<>();
    List<Expr> ratio = new ArrayList<>();
    for (Expr f : body instanceof Product p ? p.factors() : List.of(body)) {
      if (Free.of(f, k)) {
        constant.add(f);
        continue;
      }
      if (!(f instanceof Power w) || !Free.of(w.base(), k)) {
        return Optional.empty();
      }
      Optional<List<Expr>> c = coefficients(w.exponent(), k);
      if (c.isEmpty() || c.get().size() != 2) {
        return Optional.empty();
      }
      ratio.add(Power.of(w.base(), c.get().get(1)));
      constant.add(Power.of(w.base(), c.get().get(0)));
    }
    if (ratio.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new Geometric(
            node.canon(LimitMethods.productOf(constant)),
            node.canon(LimitMethods.productOf(ratio))));
  }

  /** Whether {@code |r| < 1}: a value decided strictly between −1 and 1, or a symbol bounded so. */
  private static boolean insideUnit(Expr r, Context ctx) {
    if (Signs.positive(Signs.canon(Sum.of(Num.ONE, Product.of(Num.MINUS_ONE, r)), ctx), ctx)
        && Signs.positive(Signs.canon(Sum.of(Num.ONE, r), ctx), ctx)) {
      return true;
    }
    if (r instanceof Symbol s) {
      Facts f = ctx.assumptions().facts(s);
      return above(f.lower(), -1) && below(f.upper(), 1);
    }
    return false;
  }

  private static boolean above(Facts.Bound b, int v) {
    if (b == null) {
      return false;
    }
    int c = b.value().compareTo(Int.of(v));
    return c > 0 || c == 0 && b.strict();
  }

  private static boolean below(Facts.Bound b, int v) {
    if (b == null) {
      return false;
    }
    int c = b.value().compareTo(Int.of(v));
    return c < 0 || c == 0 && b.strict();
  }

  private static Optional<Replacement.Valued.Result> geometric(Match m) {
    Optional<Node> n = Node.of(m, true);
    if (n.isEmpty() || !Signs.finite(n.get().from())) {
      return Optional.empty();
    }
    Node node = n.get();
    Optional<Geometric> g = geometric(node.body(), node.k(), node);
    if (g.isEmpty()) {
      return Optional.empty();
    }
    Expr c = g.get().coefficient();
    Expr r = g.get().ratio();
    Expr a = node.from();
    Tri one = Signs.zero(node.canon(Sum.of(r, Num.MINUS_ONE)), node.ctx());
    if (node.infinite()) {
      return infiniteGeometric(c, r, a, one, node);
    }
    if (!Signs.finite(node.to())) {
      return Optional.empty();
    }
    if (one == Tri.TRUE) {
      return result(node.canon(Product.of(c, node.count())), r, List.of());
    }
    List<Expr> conditions = new ArrayList<>();
    if (one == Tri.UNKNOWN) {
      if (!Signs.hasSymbol(r)) {
        return Optional.empty();
      }
      conditions.add(Relation.of(Relation.Op.NE, r, Num.ONE));
    }
    Expr value =
        Product.of(
            c,
            Sum.of(
                Power.of(r, Sum.of(node.to(), Num.ONE)), Product.of(Num.MINUS_ONE, Power.of(r, a))),
            Power.of(Sum.of(r, Num.MINUS_ONE), Num.MINUS_ONE));
    return result(node.canon(value), r, conditions);
  }

  private static Optional<Replacement.Valued.Result> infiniteGeometric(
      Expr c, Expr r, Expr a, Tri one, Node node) {
    Context ctx = node.ctx();
    if (insideUnit(r, ctx)) {
      Expr value =
          Product.of(
              c,
              Power.of(r, a),
              Power.of(Sum.of(Num.ONE, Product.of(Num.MINUS_ONE, r)), Num.MINUS_ONE));
      return result(node.canon(value), r, List.of());
    }
    Optional<Integer> sc = Signs.sign(c, ctx);
    Optional<Integer> s1 = Signs.sign(node.canon(Sum.of(r, Num.MINUS_ONE)), ctx);
    Optional<Integer> s2 = Signs.sign(node.canon(Sum.of(r, Num.ONE)), ctx);
    if (sc.isEmpty() || sc.get() == 0) {
      return Optional.empty();
    }
    if (s1.isPresent() && s1.get() >= 0) {
      return result(LimitMethods.inf(sc.get()), r, List.of());
    }
    if (s2.isPresent() && s2.get() <= 0) {
      return result(Constant.UNDEFINED, r, List.of());
    }
    return Optional.empty();
  }

  /** {@code (αk + β)·c·r^k}. */
  private static Optional<Replacement.Valued.Result> arithmeticGeometric(Match m) {
    Optional<Node> n = Node.of(m, true);
    if (n.isEmpty() || !Signs.finite(n.get().from())) {
      return Optional.empty();
    }
    Node node = n.get();
    Symbol k = node.k();
    List<Expr> linear = new ArrayList<>();
    List<Expr> rest = new ArrayList<>();
    for (Expr f : node.body() instanceof Product p ? p.factors() : List.of(node.body())) {
      boolean poly =
          !Free.of(f, k)
              && coefficients(f, k).map(c -> c.size() == 2).orElse(false)
              && !(f instanceof Power w && !Free.of(w.exponent(), k));
      (poly ? linear : rest).add(f);
    }
    if (linear.size() != 1) {
      return Optional.empty();
    }
    Optional<Geometric> g = geometric(node.canon(LimitMethods.productOf(rest)), k, node);
    if (g.isEmpty()) {
      return Optional.empty();
    }
    List<Expr> ab = coefficients(linear.getFirst(), k).orElseThrow();
    Expr alpha = node.canon(Product.of(ab.get(1), g.get().coefficient()));
    Expr beta = node.canon(Product.of(ab.get(0), g.get().coefficient()));
    Expr r = g.get().ratio();
    Context ctx = node.ctx();
    if (Signs.zero(node.canon(Sum.of(r, Num.MINUS_ONE)), ctx) != Tri.FALSE) {
      return Optional.empty();
    }
    Expr a = node.from();
    Expr t;
    Expr geometricPart;
    if (node.infinite()) {
      if (!insideUnit(r, ctx)) {
        return Optional.empty();
      }
      // Σ_{k=a}^∞ k r^k = r/(1 − r)² − T(a − 1); Σ r^k = r^a/(1 − r)
      t =
          Sum.of(
              Product.of(r, Power.of(Sum.of(Num.ONE, Product.of(Num.MINUS_ONE, r)), Num.of(-2))),
              Product.of(Num.MINUS_ONE, kPowers(r, node.canon(Sum.of(a, Num.MINUS_ONE)))));
      geometricPart =
          Product.of(
              Power.of(r, a),
              Power.of(Sum.of(Num.ONE, Product.of(Num.MINUS_ONE, r)), Num.MINUS_ONE));
    } else {
      if (!Signs.finite(node.to())) {
        return Optional.empty();
      }
      t =
          Sum.of(
              kPowers(r, node.to()),
              Product.of(Num.MINUS_ONE, kPowers(r, node.canon(Sum.of(a, Num.MINUS_ONE)))));
      geometricPart =
          Product.of(
              Sum.of(
                  Power.of(r, Sum.of(node.to(), Num.ONE)),
                  Product.of(Num.MINUS_ONE, Power.of(r, a))),
              Power.of(Sum.of(r, Num.MINUS_ONE), Num.MINUS_ONE));
    }
    Expr value = Sum.of(Product.of(alpha, t), Product.of(beta, geometricPart));
    return result(node.canon(value), r, List.of());
  }

  /** {@code T(n) = Σ_{k=0}^{n} k r^k = r(1 − (n + 1)r^n + n r^(n+1))/(1 − r)²}. */
  private static Expr kPowers(Expr r, Expr n) {
    return Product.of(
        r,
        Sum.of(
            Num.ONE,
            Product.of(Num.MINUS_ONE, Sum.of(n, Num.ONE), Power.of(r, n)),
            Product.of(n, Power.of(r, Sum.of(n, Num.ONE)))),
        Power.of(Sum.of(Num.ONE, Product.of(Num.MINUS_ONE, r)), Num.of(-2)));
  }

  // Telescoping summands.

  /** One term {@code c·g(k + α)} of a telescoping summand. */
  private record Shifted(RealNumeric c, RealNumeric alpha, int power) {}

  private static Optional<Replacement.Valued.Result> telescoping(Match m) {
    Optional<Node> n = Node.of(m, true);
    if (n.isEmpty() || !Signs.finite(n.get().from())) {
      return Optional.empty();
    }
    Node node = n.get();
    Optional<List<Shifted>> rational = rationalTerms(node);
    boolean log = rational.isEmpty();
    Optional<List<Shifted>> terms = log ? logTerms(node) : rational;
    if (terms.isEmpty() || terms.get().isEmpty()) {
      return Optional.empty();
    }
    // group by power and by α modulo 1; each group's coefficients must cancel
    Map<String, List<Shifted>> groups = new TreeMap<>();
    for (Shifted s : terms.get()) {
      RealNumeric frac = (RealNumeric) s.alpha().subtract(s.alpha().floor(), EXACT);
      groups.computeIfAbsent(s.power() + ":" + frac, key -> new ArrayList<>()).add(s);
    }
    List<Expr> total = new ArrayList<>();
    boolean infinite = node.infinite();
    if (!infinite && !Signs.finite(node.to())) {
      return Optional.empty();
    }
    RealNumeric divergence = Int.ZERO;
    for (List<Shifted> group : groups.values()) {
      RealNumeric sum = Int.ZERO;
      RealNumeric low = group.getFirst().alpha();
      for (Shifted s : group) {
        sum = (RealNumeric) sum.add(s.c(), EXACT);
        if (s.alpha().compareTo(low) < 0) {
          low = s.alpha();
        }
      }
      if (!sum.isZero()) {
        return Optional.empty();
      }
      for (Shifted s : group) {
        int d =
            ((RealNumeric) s.alpha().subtract(low, EXACT)).toReal(EXACT).value().intValueExact();
        if (d > MAX_SHIFT) {
          return Optional.empty();
        }
        divergence = (RealNumeric) divergence.add(s.c().multiply(Int.of(d), EXACT), EXACT);
        for (int i = 0; i < d; i++) {
          // − c·g(a + α₀ + i), and + c·g(b + α₀ + i + 1) for a finite b
          total.add(
              Product.of(
                  Num.of(s.c().negate()),
                  g(Sum.of(node.from(), Num.of(low), Num.of(i)), s.power(), log)));
          if (!infinite) {
            total.add(
                Product.of(
                    Num.of(s.c()),
                    g(Sum.of(node.to(), Num.of(low), Num.of(i + 1)), s.power(), log)));
          }
        }
      }
    }
    if (infinite && log && !divergence.isZero()) {
      return result(LimitMethods.inf(divergence.signum()));
    }
    return result(node.canon(LimitMethods.sumOf(total)));
  }

  private static Expr g(Expr u, int power, boolean log) {
    return log ? Apply.of(Symbol.of("log"), u) : Power.of(u, Num.of(-power));
  }

  /**
   * The partial fractions of a rational summand over linear bases, with no polynomial part: those
   * of each term, added, or where a term has a base of higher degree, those of the terms merged
   * into one rational function, at most {@link #MAX_MERGED} of them with a denominator of at most
   * that degree.
   */
  private static Optional<List<Shifted>> rationalTerms(Node node) {
    Symbol k = node.k();
    if (hasLog(node.body())) {
      return Optional.empty();
    }
    List<Expr> summands = node.body() instanceof Sum s ? s.terms() : List.of(node.body());
    List<mathcanvas.engine.algebra.RationalFunction> each = new ArrayList<>();
    int degree = 0;
    for (Expr t : summands) {
      mathcanvas.engine.algebra.RationalFunction f =
          mathcanvas.engine.algebra.RationalFunction.of(t, node.ctx().cancellation());
      if (!f.variables().isEmpty() && !List.of(k).equals(f.variables())) {
        return Optional.empty();
      }
      each.add(f);
      degree += f.denominator().isConstant() ? 0 : f.denominator().degree(k);
    }
    Optional<List<Shifted>> termwise = partialFractions(each, node);
    if (termwise.isPresent() || summands.size() == 1) {
      return termwise;
    }
    if (summands.size() > MAX_MERGED || degree > MAX_MERGED) {
      return Optional.empty();
    }
    return partialFractions(
        List.of(
            mathcanvas.engine.algebra.RationalFunction.of(node.body(), node.ctx().cancellation())),
        node);
  }

  /**
   * The shifted terms of the partial fractions of rational functions of the index, added: each base
   * linear and each numerator constant, the polynomial parts adding to zero.
   */
  private static Optional<List<Shifted>> partialFractions(
      List<mathcanvas.engine.algebra.RationalFunction> fs, Node node) {
    Symbol k = node.k();
    mathcanvas.engine.algebra.Polynomial polynomial = mathcanvas.engine.algebra.Polynomial.ZERO;
    List<Shifted> out = new ArrayList<>();
    for (mathcanvas.engine.algebra.RationalFunction f : fs) {
      if (f.denominator().isConstant()) {
        polynomial =
            polynomial.add(
                f.numerator()
                    .multiply((RealNumeric) Int.ONE.divide(f.denominator().constantTerm(), EXACT)));
        continue;
      }
      if (f.denominator().degree(k) > MAX_MERGED) {
        return Optional.empty();
      }
      mathcanvas.engine.algebra.Factorization den =
          node.ctx()
              .providers()
              .run(
                  new mathcanvas.engine.provider.Factor(f.denominator()),
                  node.ctx(),
                  mathcanvas.engine.provider.Steps.NONE)
              .value();
      Optional<mathcanvas.engine.algebra.PartialFractions.Decomposition> d =
          mathcanvas.engine.algebra.PartialFractions.of(f, k, den, node.ctx().cancellation());
      if (d.isEmpty()) {
        return Optional.empty();
      }
      polynomial = polynomial.add(d.get().polynomial());
      for (mathcanvas.engine.algebra.PartialFractions.Term t : d.get().terms()) {
        if (t.base().degree(k) != 1 || !t.numerator().isConstant()) {
          return Optional.empty();
        }
        RealNumeric u = t.base().coefficient(1);
        RealNumeric v = t.base().coefficient(0);
        RealNumeric alpha = (RealNumeric) v.divide(u, EXACT);
        RealNumeric c =
            (RealNumeric) t.numerator().constantTerm().divide(u.pow(t.power(), EXACT), EXACT);
        out.add(new Shifted(c, alpha, t.power()));
        if (!clearOfPoles(alpha, node)) {
          return Optional.empty();
        }
      }
    }
    return polynomial.isZero() ? Optional.of(out) : Optional.empty();
  }

  /** No term of the range is a pole: {@code k + α ≠ 0} for {@code a ≤ k}. */
  private static boolean clearOfPoles(RealNumeric alpha, Node node) {
    Expr first = node.canon(Sum.of(node.from(), Num.of(alpha)));
    return Signs.positive(first, node.ctx()) || !alpha.isInteger() && Signs.hasSymbol(first);
  }

  private static boolean hasLog(Expr e) {
    if (e instanceof Apply a && Set.of("log", "ln").contains(a.head().name())) {
      return true;
    }
    for (Expr o : Operands.of(e)) {
      if (hasLog(o)) {
        return true;
      }
    }
    return false;
  }

  /** A sum of rational multiples of logarithms of rational functions of {@code k}. */
  private static Optional<List<Shifted>> logTerms(Node node) {
    Symbol k = node.k();
    List<Shifted> out = new ArrayList<>();
    for (Expr t : node.body() instanceof Sum s ? s.terms() : List.of(node.body())) {
      RealNumeric c = Int.ONE;
      Expr log = t;
      if (t instanceof Product p
          && p.factors().size() == 2
          && p.factors().getFirst() instanceof Num n
          && n.value() instanceof RealNumeric r
          && r.isExact()) {
        c = r;
        log = p.factors().get(1);
      }
      if (!(log instanceof Apply a)
          || !Set.of("log", "ln").contains(a.head().name())
          || a.args().size() != 1) {
        return Optional.empty();
      }
      mathcanvas.engine.algebra.RationalFunction f =
          mathcanvas.engine.algebra.RationalFunction.of(
              a.args().getFirst(), node.ctx().cancellation());
      if (!List.of(k).equals(f.variables())) {
        return Optional.empty();
      }
      for (int side = 0; side < 2; side++) {
        mathcanvas.engine.algebra.Polynomial p = side == 0 ? f.numerator() : f.denominator();
        if (p.isConstant()) {
          if (!p.constantTerm().isOne() && side == 1 || side == 0 && !p.constantTerm().isOne()) {
            return Optional.empty();
          }
          continue;
        }
        mathcanvas.engine.algebra.Factorization fz =
            node.ctx()
                .providers()
                .run(
                    new mathcanvas.engine.provider.Factor(p),
                    node.ctx(),
                    mathcanvas.engine.provider.Steps.NONE)
                .value();
        if (!fz.unit().isOne()) {
          return Optional.empty();
        }
        for (mathcanvas.engine.algebra.Factorization.Factor fac : fz.factors()) {
          mathcanvas.engine.algebra.Polynomial base = fac.base();
          if (base.degree(k) != 1 || !base.coefficient(1).isOne()) {
            return Optional.empty();
          }
          RealNumeric alpha = base.coefficient(0);
          if (!Signs.positive(node.canon(Sum.of(node.from(), Num.of(alpha))), node.ctx())) {
            return Optional.empty();
          }
          RealNumeric e =
              (RealNumeric) c.multiply(Int.of(fac.multiplicity() * (side == 0 ? 1 : -1)), EXACT);
          out.add(new Shifted(e, alpha, 1));
        }
      }
    }
    return Optional.of(out);
  }

  // The p-series.

  private static Optional<Replacement.Valued.Result> pSeries(Match m) {
    Optional<Node> n = Node.of(m, true);
    if (n.isEmpty() || !n.get().infinite()) {
      return Optional.empty();
    }
    Node node = n.get();
    Symbol k = node.k();
    List<Expr> constant = new ArrayList<>();
    RealNumeric q = null;
    for (Expr f : node.body() instanceof Product p ? p.factors() : List.of(node.body())) {
      if (Free.of(f, k)) {
        constant.add(f);
      } else if (f instanceof Power w
          && w.base().equals(k)
          && w.exponent() instanceof Num e
          && e.value() instanceof RealNumeric r
          && r.isExact()
          && q == null) {
        q = r;
      } else {
        return Optional.empty();
      }
    }
    Optional<Int> a = integer(node.from());
    if (q == null || a.isEmpty() || a.get().signum() < 1) {
      return Optional.empty();
    }
    Expr c = node.canon(LimitMethods.productOf(constant));
    if (q.compareTo(Int.MINUS_ONE) >= 0) {
      Optional<Integer> s = Signs.sign(c, node.ctx());
      return s.isPresent() && s.get() != 0 ? result(LimitMethods.inf(s.get())) : Optional.empty();
    }
    RealNumeric p = q.negate();
    if (!p.isInteger() || !((RealNumeric) p.divide(Int.TWO, EXACT)).isInteger()) {
      return Optional.empty();
    }
    int twoM = p.toReal(EXACT).value().intValueExact();
    if (twoM > MAX_DEGREE) {
      return Optional.empty();
    }
    // ζ(2m) = (−1)^(m+1) B_2m (2π)^2m / (2 (2m)!)
    RealNumeric b = bernoulli(twoM).get(twoM);
    BigInteger fact = BigInteger.ONE;
    for (int i = 2; i <= twoM; i++) {
      fact = fact.multiply(BigInteger.valueOf(i));
    }
    RealNumeric coef =
        (RealNumeric)
            b.multiply(Int.of(BigInteger.TWO.pow(twoM)), EXACT)
                .divide(Int.of(fact.shiftLeft(1)), EXACT);
    if ((twoM / 2) % 2 == 0) {
      coef = coef.negate();
    }
    List<Expr> terms = new ArrayList<>();
    terms.add(Product.of(Num.of(coef), Power.of(Constant.PI, Num.of(twoM))));
    for (BigInteger j = BigInteger.ONE;
        j.compareTo(a.get().value()) < 0;
        j = j.add(BigInteger.ONE)) {
      terms.add(Num.of(Rational.of(BigInteger.ONE.negate(), j.pow(twoM))));
    }
    return result(node.canon(Product.of(c, LimitMethods.sumOf(terms))));
  }

  // Products.

  private enum Kind {
    CONSTANT,
    FACTORIAL,
    GEOMETRIC
  }

  private static Optional<Replacement.Valued.Result> products(Match m, Kind kind) {
    Optional<Node> n = Node.of(m, false);
    if (n.isEmpty() || !Signs.finite(n.get().from()) || !Signs.finite(n.get().to())) {
      return Optional.empty();
    }
    Node node = n.get();
    Symbol k = node.k();
    List<Expr> out = new ArrayList<>();
    boolean linear = false;
    boolean exponential = false;
    for (Expr f : node.body() instanceof Product p ? p.factors() : List.of(node.body())) {
      if (Free.of(f, k)) {
        out.add(Power.of(f, node.count()));
        continue;
      }
      Expr base = f;
      Expr e = Num.ONE;
      if (f instanceof Power w && Free.of(w.exponent(), k)) {
        base = w.base();
        e = w.exponent();
      }
      Optional<List<Expr>> c = coefficients(base, k);
      if (c.isPresent()
          && c.get().size() == 2
          && c.get().get(1).equals(Num.ONE)
          && e instanceof Num en
          && en.value() instanceof Int) {
        linear = true;
        Expr shift = c.get().get(0);
        if (integer(shift).isEmpty()) {
          return Optional.empty();
        }
        Expr low = node.canon(Sum.of(node.from(), shift));
        Optional<Integer> s = Signs.sign(low, node.ctx());
        if (s.isPresent() && s.get() < 1) {
          return Optional.empty();
        }
        Expr ratio =
            Product.of(
                Apply.of(Symbol.of("factorial"), Sum.of(node.to(), shift)),
                Power.of(
                    Apply.of(Symbol.of("factorial"), Sum.of(low, Num.MINUS_ONE)), Num.MINUS_ONE));
        out.add(Power.of(ratio, e));
        continue;
      }
      if (f instanceof Power w && Free.of(w.base(), k)) {
        Optional<List<Expr>> ec = coefficients(w.exponent(), k);
        if (ec.isEmpty() || ec.get().size() != 2) {
          return Optional.empty();
        }
        exponential = true;
        // Π r^(αk + β) = r^(α Σk + β n), Σk = (a + b)·n/2
        Expr sumK =
            Product.of(Num.of(Rational.of(1, 2)), Sum.of(node.from(), node.to()), node.count());
        out.add(
            Power.of(
                w.base(),
                Sum.of(
                    Product.of(ec.get().get(1), sumK), Product.of(ec.get().get(0), node.count()))));
        continue;
      }
      return Optional.empty();
    }
    Kind found = linear ? Kind.FACTORIAL : exponential ? Kind.GEOMETRIC : Kind.CONSTANT;
    if (found != kind) {
      return Optional.empty();
    }
    Expr value = node.canon(LimitMethods.productOf(out));
    return result(node.canon(node.ctx().numerics().exact(value, node.ctx().precision())));
  }
}
