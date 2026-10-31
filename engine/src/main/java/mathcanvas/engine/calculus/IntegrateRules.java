package mathcanvas.engine.calculus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Bindings.Binding;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Match;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;

/**
 * The rule set {@code integrate}: every rule rewrites an {@code Integral} node, written {@code
 * integrate(f_, x_)} in the patterns ({@code integrate(f_, x_, a_, b_)} for a definite one). The
 * rewriter works innermost-first, so linearity splits an integral into integrals of its terms and
 * each is finished by the rules after it; an {@code Integral} no rule matches stays. {@code
 * docs/reference/engine.md}, <b>Integration</b>, lists every rule.
 *
 * <p>The table's rules take a linear argument {@code u} of the variable, its slope {@code s} free
 * of the variable and known not to be zero: {@code ∫ sin(2x + 1) dx = -cos(2x + 1)/2}. A logarithm
 * is of an absolute value for a real argument ({@code ∫ 1/x dx = log|x|}), and of the argument
 * itself otherwise.
 *
 * <p>The set exists at each search depth from 0 to {@link #DEPTH} ({@link #at}), all named {@code
 * integrate}: a substitution tries the integral it leads to with the set one depth lower and fires
 * only when that integral is found whole, so substitutions nest at most {@link #DEPTH} deep; at
 * depth 0 none is tried. Integration by parts keeps the depth, since it lowers a measure of its own
 * (<b>Integration</b>, <b>Termination</b>).
 */
final class IntegrateRules {

  private IntegrateRules() {}

  /** The most substitutions nested in one search. */
  static final int DEPTH = 3;

  /**
   * One search: the rule set at each depth, whose searching rules share a memo of the integrals
   * they tried. {@link Calculus#integrate}'s provider makes one per call; the shared one, {@link
   * #SHARED}, which {@link RuleSets#INTEGRATE} is, remembers nothing, since it is used across calls
   * and threads.
   */
  static final class Search {

    private final Map<Attempt, Optional<Expr>> memo;
    private final List<RuleSet> sets = new ArrayList<>();

    /**
     * Builds the rule sets.
     *
     * @param memoise whether to remember the integrals tried
     */
    Search(boolean memoise) {
      memo = memoise ? new HashMap<>() : null;
      for (int d = 0; d <= DEPTH; d++) {
        sets.add(new RuleSet("integrate", rules(d, this)));
      }
    }

    /**
     * The rule set at a search depth.
     *
     * @param depth from 0 to {@link #DEPTH}
     * @return the set whose substitutions try their integrals at {@code depth − 1}
     */
    RuleSet at(int depth) {
      return sets.get(depth);
    }

    /**
     * An antiderivative of {@code f} in {@code x} found whole by the set at {@code depth}, without
     * a trace, under the context's facts, remembered for this search.
     *
     * @return the antiderivative, or empty when an integral in {@code x} stays in the result
     */
    Optional<Expr> attempt(Expr f, Symbol x, int depth, Context ctx) {
      Attempt key = new Attempt(Canonical.of(f, ctx.precision()), x, depth, ctx.assumptions());
      if (memo != null && memo.containsKey(key)) {
        return memo.get(key);
      }
      Expr r = Rewriter.rewrite(Integral.indefinite(f, x), at(depth), ctx.withTrace(false)).expr();
      Optional<Expr> found = complete(r, x) ? Optional.of(r) : Optional.empty();
      if (memo != null) {
        memo.put(key, found);
      }
      return found;
    }
  }

  /** An integral tried: the integrand, the variable, the depth and the facts it was tried under. */
  private record Attempt(Expr f, Symbol x, int depth, Assumptions facts) {}

  /**
   * The rule set at a search depth, of {@link #SHARED}.
   *
   * @param depth from 0 to {@link #DEPTH}
   * @return the set whose substitutions try their integrals at {@code depth − 1}
   */
  static RuleSet at(int depth) {
    return SHARED.at(depth);
  }

  /** The rules of the set at {@code depth} of {@code search}. */
  private static List<Rule> rules(int depth, Search search) {
    List<Rule> rules = new ArrayList<>(COMMON);
    // Substitution.
    rules.add(
        Rule.valued(
            "substitution",
            ANY,
            IntegrateSearch.SUBSTITUTION_VALUES,
            m -> IntegrateSearch.substitute(m, depth, search)));
    // By parts.
    rules.add(
        Rule.valued(
            "by-parts-twice",
            ANY,
            IntegrateSearch.CYCLE_VALUES,
            m -> IntegrateSearch.byPartsTwice(m, depth, search)));
    rules.add(
        Rule.valued(
            "by-parts",
            ANY,
            IntegrateSearch.PARTS_VALUES,
            m -> IntegrateSearch.byParts(m, depth, search)));
    // Expansion.
    rules.add(EXPAND);
    return rules;
  }

  private static final Pattern ANY = Pattern.parse("integrate(f_, x_)");

  private static final Rule EXPAND = Rule.computed("expand", ANY, IntegrateRules::expand);

  /** The rules that neither search nor depend on the depth, in their order. */
  private static final List<Rule> COMMON = common();

  private static List<Rule> common() {
    List<Rule> rules = new ArrayList<>();
    // Linearity.
    rules.add(Rule.of("undefined", Pattern.parse("integrate(undefined, x_)"), Constant.UNDEFINED));
    rules.add(
        Rule.of(
            "constant",
            Pattern.parse("integrate(c_, x_)")
                .where(List.of("c_", "x_"), (b, a) -> DiffRules.constant(b.get("c_"), variable(b))),
            template("c_ * x_")));
    rules.add(Rule.computed("sum-rule", Pattern.parse("integrate(u_ + rest__, x_)"), m -> sum(m)));
    rules.add(
        Rule.of(
            "constant-multiple",
            Pattern.parse("integrate(c_ * u__, x_)")
                .where(
                    List.of("c_", "x_"),
                    (b, a) -> Canonical.commutes(b.get("c_")) && Free.of(b.get("c_"), variable(b))),
            template("c_ * integrate(u__, x_)")));
    // The table, each with a linear argument.
    rules.add(
        table(
            "power",
            Pattern.parse("integrate(u_^n_, x_)")
                .optional("n_", Num.ONE)
                .where(
                    List.of("n_", "x_"),
                    (b, a) ->
                        Free.of(b.get("n_"), variable(b))
                            && a.isNonZero(Canonical.of(Sum.of(b.get("n_"), Num.ONE))) == Tri.TRUE),
            "u_^(n_ + 1) / ((n_ + 1) * s_)",
            null));
    rules.add(
        table(
            "reciprocal",
            Pattern.parse("integrate(u_^(-1), x_)"),
            "log(abs(u_)) / s_",
            "log(u_) / s_"));
    rules.add(table("exp", Pattern.parse("integrate(e^u_, x_)"), "e^u_ / s_", null));
    rules.add(
        table(
            "exponential",
            Pattern.parse("integrate(b_^u_, x_)")
                .where(
                    List.of("b_", "x_"),
                    (b, a) ->
                        Free.of(b.get("b_"), variable(b))
                            && a.isPositive(b.get("b_")) == Tri.TRUE
                            && !b.get("b_").equals(Num.ONE)),
            "b_^u_ / (s_ * log(b_))",
            null));
    rules.add(table("sin", Pattern.parse("integrate(sin(u_), x_)"), "-cos(u_) / s_", null));
    rules.add(table("cos", Pattern.parse("integrate(cos(u_), x_)"), "sin(u_) / s_", null));
    rules.add(
        table(
            "tan",
            Pattern.parse("integrate(tan(u_), x_)"),
            "-log(abs(cos(u_))) / s_",
            "-log(cos(u_)) / s_"));
    rules.add(
        table(
            "cot",
            Pattern.parse("integrate(cot(u_), x_)"),
            "log(abs(sin(u_))) / s_",
            "log(sin(u_)) / s_"));
    rules.add(
        table(
            "sec",
            Pattern.parse("integrate(sec(u_), x_)"),
            "log(abs(sec(u_) + tan(u_))) / s_",
            "log(sec(u_) + tan(u_)) / s_"));
    rules.add(
        table(
            "csc",
            Pattern.parse("integrate(csc(u_), x_)"),
            "-log(abs(csc(u_) + cot(u_))) / s_",
            "-log(csc(u_) + cot(u_)) / s_"));
    rules.add(
        table("sec-squared", Pattern.parse("integrate(sec(u_)^2, x_)"), "tan(u_) / s_", null));
    rules.add(
        table("csc-squared", Pattern.parse("integrate(csc(u_)^2, x_)"), "-cot(u_) / s_", null));
    rules.add(
        table("sec-tan", Pattern.parse("integrate(sec(u_) * tan(u_), x_)"), "sec(u_) / s_", null));
    rules.add(
        table("csc-cot", Pattern.parse("integrate(csc(u_) * cot(u_), x_)"), "-csc(u_) / s_", null));
    rules.add(table("sinh", Pattern.parse("integrate(sinh(u_), x_)"), "cosh(u_) / s_", null));
    rules.add(table("cosh", Pattern.parse("integrate(cosh(u_), x_)"), "sinh(u_) / s_", null));
    rules.add(table("tanh", Pattern.parse("integrate(tanh(u_), x_)"), "log(cosh(u_)) / s_", null));
    rules.add(
        table(
            "coth",
            Pattern.parse("integrate(coth(u_), x_)"),
            "log(abs(sinh(u_))) / s_",
            "log(sinh(u_)) / s_"));
    rules.add(table("sech", Pattern.parse("integrate(sech(u_), x_)"), "atan(sinh(u_)) / s_", null));
    rules.add(
        table(
            "csch",
            Pattern.parse("integrate(csch(u_), x_)"),
            "log(abs(tanh(u_ / 2))) / s_",
            "log(tanh(u_ / 2)) / s_"));
    rules.add(
        table("sech-squared", Pattern.parse("integrate(sech(u_)^2, x_)"), "tanh(u_) / s_", null));
    rules.add(
        table("csch-squared", Pattern.parse("integrate(csch(u_)^2, x_)"), "-coth(u_) / s_", null));
    rules.add(
        table(
            "sech-tanh",
            Pattern.parse("integrate(sech(u_) * tanh(u_), x_)"),
            "-sech(u_) / s_",
            null));
    rules.add(
        table(
            "csch-coth",
            Pattern.parse("integrate(csch(u_) * coth(u_), x_)"),
            "-csch(u_) / s_",
            null));
    rules.add(
        Rule.computed(
            "square-reciprocal",
            squarePattern("integrate((k_ * u_^2 + c_)^(-1), x_)"),
            m -> square(m, false)));
    rules.add(
        Rule.computed(
            "square-root-reciprocal",
            squarePattern("integrate((k_ * u_^2 + c_)^(-1/2), x_)"),
            m -> square(m, true)));
    // The trigonometric identities.
    rules.add(Rule.computed("odd-power", Pattern.parse("integrate(f_, x_)"), TrigIdentities::odd));
    rules.add(
        Rule.computed("half-angle", Pattern.parse("integrate(f_, x_)"), TrigIdentities::halfAngle));
    rules.add(
        Rule.computed(
            "product-to-sum", Pattern.parse("integrate(f_, x_)"), TrigIdentities::productToSum));
    rules.add(
        Rule.of(
            "tan-power",
            powerPattern("integrate(tan(u_)^n_, x_)", 2),
            template("integrate(tan(u_)^(n_ - 2) * sec(u_)^2 - tan(u_)^(n_ - 2), x_)")));
    rules.add(
        Rule.of(
            "cot-power",
            powerPattern("integrate(cot(u_)^n_, x_)", 2),
            template("integrate(cot(u_)^(n_ - 2) * csc(u_)^2 - cot(u_)^(n_ - 2), x_)")));
    rules.add(
        table(
            "sec-reduction",
            powerPattern("integrate(sec(u_)^n_, x_)", 3),
            "sec(u_)^(n_ - 2) * tan(u_) / (s_ * (n_ - 1))"
                + " + (n_ - 2) / (n_ - 1) * integrate(sec(u_)^(n_ - 2), x_)",
            null));
    rules.add(
        table(
            "csc-reduction",
            powerPattern("integrate(csc(u_)^n_, x_)", 3),
            "-csc(u_)^(n_ - 2) * cot(u_) / (s_ * (n_ - 1))"
                + " + (n_ - 2) / (n_ - 1) * integrate(csc(u_)^(n_ - 2), x_)",
            null));
    // Completing the square.
    rules.add(
        Rule.computed(
            "complete-the-square",
            Pattern.parse("integrate(q_^n_, x_)")
                .where("n_", n -> n.equals(Num.MINUS_ONE) || n.equals(MINUS_HALF)),
            IntegrateRules::completeSquare));
    return rules;
  }

  private static final Num MINUS_HALF = (Num) Canonical.of(template("-1/2"));

  /**
   * The search that remembers nothing, for every use but one call's; declared after every static
   * field its rules read.
   */
  static final Search SHARED = new Search(false);

  /** Parses a template in the empty environment. */
  static Expr template(String source) {
    return Parser.parseExpression(source, Names.NONE);
  }

  /** The variable bound to {@code x_}. */
  private static Symbol variable(Bindings b) {
    return (Symbol) b.get("x_");
  }

  /** A pattern of a power whose exponent {@code n_} is an integer of at least {@code min}. */
  private static Pattern powerPattern(String source, int min) {
    return Pattern.parse(source)
        .where(
            "n_",
            n ->
                n instanceof Num k
                    && k.value() instanceof Int i
                    && i.value().compareTo(java.math.BigInteger.valueOf(min)) >= 0);
  }

  /** {@code k u² + c} with {@code k}, {@code c} free of the variable and not zero. */
  private static Pattern squarePattern(String source) {
    return Pattern.parse(source)
        .optional("k_", Num.ONE)
        .where(
            List.of("k_", "c_", "x_"),
            (b, a) ->
                Free.of(b.get("k_"), variable(b))
                    && Free.of(b.get("c_"), variable(b))
                    && a.isNonZero(b.get("c_")) == Tri.TRUE);
  }

  /**
   * A table rule: {@code formula} for a real argument {@code u_}, {@code general} (or {@code
   * formula} when null) otherwise, with {@code s_} the slope of the linear {@code u_}. The rule
   * does not apply when {@code u_} is not linear in the variable.
   */
  private static Rule table(String name, Pattern pattern, String formula, String general) {
    Expr real = template(formula);
    Expr other = general == null ? real : template(general);
    return Rule.computed(
        name,
        pattern,
        m -> {
          Bindings b = m.bindings();
          Expr u = b.get("u_");
          return slope(u, variable(b), m.context().assumptions())
              .map(
                  s ->
                      b.with(new Binding("s_", s, null, null))
                          .substituteInto(
                              m.context().assumptions().isReal(u) == Tri.TRUE ? real : other));
        });
  }

  /**
   * The slope of {@code u} in {@code x} when {@code u} is linear in it: a polynomial of degree 1 in
   * {@code x} whose other kernels and slope are free of {@code x}, the slope known not to be zero.
   *
   * @return the slope, or empty when {@code u} is not linear in {@code x}
   */
  static Optional<Expr> slope(Expr u, Symbol x, Assumptions a) {
    if (u.equals(x)) {
      return Optional.of(Num.ONE);
    }
    if (Free.of(u, x)) {
      return Optional.empty();
    }
    mathcanvas.engine.algebra.Polynomial p;
    try {
      p = mathcanvas.engine.algebra.Polynomial.of(u);
    } catch (RuntimeException refused) {
      return Optional.empty();
    }
    if (!p.variables().contains(x) || p.degree(x) != 1) {
      return Optional.empty();
    }
    for (Expr k : p.variables()) {
      if (!k.equals(x) && !Free.of(k, x)) {
        return Optional.empty();
      }
    }
    Expr s = Canonical.of(p.coefficient(x, 1).toExpr());
    return a.isNonZero(s) == Tri.TRUE ? Optional.of(s) : Optional.empty();
  }

  /**
   * Whether {@code e} holds no unevaluated integral in {@code x}: an antiderivative found whole.
   *
   * @param e the expression
   * @param x the variable
   * @return true if no {@code Integral} node in {@code e} has the variable {@code x}
   */
  static boolean complete(Expr e, Symbol x) {
    if (e instanceof Integral i && i.var().equals(x)) {
      return false;
    }
    for (Expr o : Operands.of(e)) {
      if (!complete(o, x)) {
        return false;
      }
    }
    return true;
  }

  /**
   * An antiderivative of {@code f} in {@code x} found whole by the set at {@code depth} of {@link
   * #SHARED}.
   *
   * @return the antiderivative, or empty when an integral in {@code x} stays in the result
   */
  static Optional<Expr> attempt(Expr f, Symbol x, int depth, Context ctx) {
    return SHARED.attempt(f, x, depth, ctx);
  }

  /**
   * {@code e} with every occurrence of {@code target} replaced by {@code value} at once, through
   * rules whose pattern matches one expression alone (a condition of equality, so a name that reads
   * as a pattern variable, {@code x_}, is still only itself): first {@code target} by a placeholder
   * no expression holds, then the placeholder by {@code value}, so a value that contains the target
   * ({@code x} by {@code x + 1}) is put in once.
   */
  static Expr replace(Expr e, Expr target, Expr value, Context ctx) {
    Expr once =
        Rewriter.rewrite(e, replacing(target, PLACEHOLDER, ctx), ctx.withTrace(false)).expr();
    return Rewriter.rewrite(once, replacing(PLACEHOLDER, value, ctx), ctx.withTrace(false)).expr();
  }

  /** A name the language cannot write. */
  private static final Symbol PLACEHOLDER = Symbol.of("\u0000replaced");

  private static RuleSet replacing(Expr target, Expr value, Context ctx) {
    Expr t = Canonical.of(target, ctx.precision());
    return new RuleSet(
        "replace",
        List.of(
            Rule.computed(
                "replace",
                Pattern.parse("t_").where("t_", n -> n.equals(t)),
                m -> Optional.of(value))));
  }

  /** {@code ∫ (a + b + …)} → {@code ∫ a + ∫ b + …}, every term at once. */
  private static Optional<Expr> sum(Match m) {
    Bindings b = m.bindings();
    Symbol x = variable(b);
    List<Expr> terms = new ArrayList<>();
    terms.add(Integral.indefinite(b.get("u_"), x));
    for (Expr t : b.sequence("rest__")) {
      terms.add(Integral.indefinite(t, x));
    }
    return Optional.of(Sum.of(terms));
  }

  /**
   * {@code ∫ (k u² + c)^-1} as an arctangent when {@code k} and {@code c} have one sign, as a
   * difference of logarithms when they have opposite signs; {@code ∫ (k u² + c)^(-1/2)} as a
   * logarithm when {@code k > 0}, an arcsine when {@code k < 0 < c}. Unknown signs decline.
   */
  private static Optional<Expr> square(Match m, boolean root) {
    Bindings b = m.bindings();
    Assumptions a = m.context().assumptions();
    Optional<Expr> s = slope(b.get("u_"), variable(b), a);
    if (s.isEmpty()) {
      return Optional.empty();
    }
    Tri kPositive = a.isPositive(b.get("k_"));
    Tri kNegative = a.isNegative(b.get("k_"));
    Tri cPositive = a.isPositive(b.get("c_"));
    Tri cNegative = a.isNegative(b.get("c_"));
    String formula = null;
    if (!root) {
      if (kPositive == Tri.TRUE && cPositive == Tri.TRUE) {
        formula = "atan(u_ * sqrt(k_ / c_)) / (s_ * sqrt(k_ * c_))";
      } else if (kNegative == Tri.TRUE && cNegative == Tri.TRUE) {
        formula = "-atan(u_ * sqrt(k_ / c_)) / (s_ * sqrt(k_ * c_))";
      } else if ((kPositive == Tri.TRUE && cNegative == Tri.TRUE)
          || (kNegative == Tri.TRUE && cPositive == Tri.TRUE)) {
        formula =
            "(log(abs(u_ - sqrt(-c_ / k_))) - log(abs(u_ + sqrt(-c_ / k_))))"
                + " / (2 * s_ * k_ * sqrt(-c_ / k_))";
      }
    } else if (kPositive == Tri.TRUE && cPositive == Tri.TRUE) {
      formula = "log(sqrt(k_) * u_ + sqrt(k_ * u_^2 + c_)) / (s_ * sqrt(k_))";
    } else if (kPositive == Tri.TRUE && cNegative == Tri.TRUE) {
      formula = "log(abs(sqrt(k_) * u_ + sqrt(k_ * u_^2 + c_))) / (s_ * sqrt(k_))";
    } else if (kNegative == Tri.TRUE && cPositive == Tri.TRUE) {
      formula = "asin(u_ * sqrt(-k_ / c_)) / (s_ * sqrt(-k_))";
    }
    if (formula == null || a.isReal(b.get("u_")) != Tri.TRUE) {
      return Optional.empty();
    }
    return Optional.of(
        b.with(new Binding("s_", s.get(), null, null)).substituteInto(template(formula)));
  }

  /**
   * {@code ∫ (A x² + B x + C)^n} with {@code n} −1 or −1/2, the quadratic written out term by term
   * with {@code B ≠ 0}: {@code ∫ (A (x + B/(2A))² + C − B²/(4A))^n}, which the square rules take.
   */
  private static Optional<Expr> completeSquare(Match m) {
    Bindings b = m.bindings();
    Symbol x = variable(b);
    if (!(b.get("q_") instanceof Sum q)) {
      return Optional.empty();
    }
    List<Expr> as = new ArrayList<>();
    List<Expr> bs = new ArrayList<>();
    List<Expr> cs = new ArrayList<>();
    for (Expr t : q.terms()) {
      if (Free.of(t, x)) {
        cs.add(t);
        continue;
      }
      List<Expr> factors = t instanceof Product p ? p.factors() : List.of(t);
      List<Expr> coefficient = new ArrayList<>();
      int degree = 0;
      for (Expr f : factors) {
        if (f.equals(x)) {
          degree += 1;
        } else if (f instanceof Power w && w.base().equals(x) && w.exponent().equals(Num.of(2))) {
          degree += 2;
        } else if (Free.of(f, x)) {
          coefficient.add(f);
        } else {
          return Optional.empty();
        }
      }
      switch (degree) {
        case 1 -> bs.add(product(coefficient));
        case 2 -> as.add(product(coefficient));
        default -> {
          return Optional.empty();
        }
      }
    }
    Assumptions facts = m.context().assumptions();
    Expr a = Canonical.of(Bindings.combine(as, Bindings.Origin.SUM));
    Expr bb = Canonical.of(Bindings.combine(bs, Bindings.Origin.SUM));
    if (as.isEmpty() || bs.isEmpty() || facts.isNonZero(a) != Tri.TRUE) {
      return Optional.empty();
    }
    Expr c = Bindings.combine(cs, Bindings.Origin.SUM);
    // A (x + B/(2A))² + C − B²/(4A)
    Expr shift = Product.of(bb, Power.of(Product.of(Num.of(2), a), Num.MINUS_ONE));
    Expr square = Product.of(a, Power.of(Sum.of(x, shift), Num.of(2)));
    Expr rest =
        Sum.of(
            c,
            Product.of(
                Num.MINUS_ONE,
                Power.of(bb, Num.of(2)),
                Power.of(Product.of(Num.of(4), a), Num.MINUS_ONE)));
    return Optional.of(Integral.indefinite(Power.of(Sum.of(square, rest), b.get("n_")), x));
  }

  /**
   * {@code ∫ f} → {@code ∫ expand(f)} for a product or a positive integer power of a sum whose
   * expansion is a sum, which linearity then splits.
   */
  private static Optional<Expr> expand(Match m) {
    Expr f = m.bindings().get("f_");
    boolean candidate =
        f instanceof Product
            || f instanceof Power w
                && w.base() instanceof Sum
                && w.exponent() instanceof Num n
                && n.value() instanceof Int k
                && k.signum() > 0;
    if (!candidate) {
      return Optional.empty();
    }
    Expr e;
    try {
      e = mathcanvas.engine.algebra.Algebra.expand(f, m.context().withTrace(false)).expr();
    } catch (mathcanvas.engine.algebra.AlgebraLimitException tooLarge) {
      return Optional.empty();
    }
    return e instanceof Sum && !e.equals(f)
        ? Optional.of(Integral.indefinite(e, variable(m.bindings())))
        : Optional.empty();
  }

  /** The product of {@code factors}: 1 for none, the factor itself for one. */
  static Expr product(List<Expr> factors) {
    return Bindings.combine(factors, Bindings.Origin.PRODUCT);
  }
}
