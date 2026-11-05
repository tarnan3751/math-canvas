package mathcanvas.engine.calculus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import mathcanvas.engine.algebra.AlgebraLimitException;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.algebra.RationalFunction;
import mathcanvas.engine.assume.Facts;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Occurrence;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Match;
import mathcanvas.engine.rule.Replacement;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;
import mathcanvas.engine.simplify.Simplifier;

/**
 * The rule {@code trig-substitution} of the {@code integrate} rule set: an integrand rational in
 * {@code x} and the square root of one quadratic {@code q} with rational coefficients, {@code q =
 * A(x + h)² + C} by completing the square, {@code u = x + h}, is integrated by the substitution
 * that makes the root a trigonometric function: {@code u = a sin θ} for {@code A < 0 < C} ({@code
 * √q = √(−A)·a cos θ}), {@code u = a tan θ} for {@code A, C > 0} ({@code √q = √A·a sec θ}), and
 * {@code u = a sec θ} for {@code A > 0 > C}, only when the facts say {@code u ≥ a} ({@code √q =
 * √A·a tan θ}) or {@code u ≤ −a} ({@code √q = −√A·a tan θ}), {@code a² = |C/A|}, an even power of
 * {@code tan θ} then written as one of {@code sec² θ − 1}. The integral in {@code θ} is tried one
 * depth lower; its antiderivative has its multiple angles expanded ({@code sin 2θ = 2 sin θ cos θ},
 * by the rules {@code sine-of-multiple} and {@code cosine-of-multiple}), then each trigonometric
 * function of {@code θ} replaced by its value in {@code u} and the root ({@code cos θ =
 * √q/(√(−A)·a)} and so on) and {@code θ} by {@code asin(u/a)}, {@code atan(u/a)} or {@code
 * asec(u/a)}. It is tried after every other technique, so the table's arcsines and logarithms stay
 * as they are.
 */
final class TrigSubstitution {

  private TrigSubstitution() {}

  /** The values the rule names. */
  static final Set<String> VALUES = Set.of("substitution", "transformed", "antiderivative");

  private static final Expr HALF = Num.of(Rational.of(1, 2));

  /** The three forms. */
  private enum Form {
    SINE,
    TANGENT,
    SECANT
  }

  static Optional<Replacement.Valued.Result> substitute(
      Match m, int depth, IntegrateRules.Search search) {
    if (depth == 0) {
      return Optional.empty();
    }
    Expr f = Canonical.of(m.bindings().get("f_"), m.context().precision());
    Symbol x = (Symbol) m.bindings().get("x_");
    Context ctx = m.context().withTrace(false);
    // the one quadratic under every half-integer power
    Set<Expr> bases = new LinkedHashSet<>();
    radicals(f, x, bases);
    if (bases.size() != 1) {
      return Optional.empty();
    }
    Expr q = bases.iterator().next();
    Optional<Polynomial> qp;
    try {
      qp = Polynomial.of(q, List.of(x));
    } catch (IllegalArgumentException | AlgebraLimitException notPolynomial) {
      return Optional.empty();
    }
    if (qp.isEmpty() || qp.get().degree(x) != 2 || !exact(qp.get())) {
      return Optional.empty();
    }
    Symbol root = Occurrence.fresh("r", s -> Occurrence.mentions(f, s));
    // f with q^(n/2) as root^n: rational in x and root
    Expr written = rewrite(f, halfPowers(q, root), ctx);
    if (!rational(written, x, root)) {
      return Optional.empty();
    }
    RealNumeric big = qp.get().coefficient(2);
    RealNumeric mid = qp.get().coefficient(1);
    RealNumeric low = qp.get().coefficient(0);
    Expr A = Num.of(big);
    Expr h =
        Canonical.of(Product.of(Num.of(mid), Power.of(Product.of(Num.of(2), A), Num.MINUS_ONE)));
    Expr C =
        Canonical.of(
            Sum.of(
                Num.of(low),
                Product.of(
                    Num.MINUS_ONE,
                    Power.of(Num.of(mid), Num.of(2)),
                    Power.of(Product.of(Num.of(4), A), Num.MINUS_ONE))));
    if (!(C instanceof Num cn) || !(cn.value() instanceof RealNumeric c) || c.signum() == 0) {
      return Optional.empty();
    }
    Form form;
    if (big.signum() < 0 && c.signum() > 0) {
      form = Form.SINE;
    } else if (big.signum() > 0 && c.signum() > 0) {
      form = Form.TANGENT;
    } else if (big.signum() > 0) {
      form = Form.SECANT;
    } else {
      return Optional.empty();
    }
    Expr scale = Canonical.of(Power.of(Num.of(big.abs()), HALF)); // √|A|
    Expr a = Canonical.of(Power.of(Num.of(divide(c, big).abs()), HALF));
    Expr u = Canonical.of(Sum.of(x, h));
    int sign = 1;
    if (form == Form.SECANT) {
      // u ≥ a, or u ≤ −a, by the facts about x: x ≥ a − h, or x ≤ −a − h
      Facts facts = ctx.assumptions().facts(x);
      Expr least = Sum.of(a, Product.of(Num.MINUS_ONE, h));
      Expr most = Sum.of(Product.of(Num.MINUS_ONE, a), Product.of(Num.MINUS_ONE, h));
      if (facts.lower() != null
          && Definite.sign(
                  Sum.of(Num.of(facts.lower().value()), Product.of(Num.MINUS_ONE, least)), ctx)
              >= 0) {
        sign = 1;
      } else if (facts.upper() != null
          && Definite.sign(
                  Sum.of(most, Product.of(Num.MINUS_ONE, Num.of(facts.upper().value()))), ctx)
              >= 0) {
        sign = -1;
      } else {
        return Optional.empty();
      }
    }
    Symbol theta = Occurrence.fresh("θ", s -> Occurrence.mentions(f, s));
    Expr sin = call("sin", theta);
    Expr cos = call("cos", theta);
    Expr tan = call("tan", theta);
    Expr sec = call("sec", theta);
    // u, the root and du/dθ in θ
    Expr uOf;
    Expr rootOf;
    Expr dOf;
    switch (form) {
      case SINE -> {
        uOf = Product.of(a, sin);
        rootOf = Product.of(scale, a, cos);
        dOf = Product.of(a, cos);
      }
      case TANGENT -> {
        uOf = Product.of(a, tan);
        rootOf = Product.of(scale, a, sec);
        dOf = Product.of(a, Power.of(sec, Num.of(2)));
      }
      default -> {
        uOf = Product.of(a, sec);
        rootOf = Product.of(Num.of(sign), scale, a, tan);
        dOf = Product.of(a, sec, tan);
      }
    }
    Expr xOf = Sum.of(uOf, Product.of(Num.MINUS_ONE, h));
    Expr inTheta =
        IntegrateRules.replace(IntegrateRules.replace(written, x, xOf, ctx), root, rootOf, ctx);
    Expr transformed = Simplifier.simplify(Product.of(inTheta, dOf), ctx.withTrace(false)).expr();
    if (form == Form.SECANT) {
      // in sec θ alone where it can be: an even power of tan θ as one of sec² θ − 1
      transformed = rewrite(transformed, EVEN_TANGENTS, ctx);
    }
    Context inner = ctx.withAssumptions(ctx.assumptions().without(theta));
    Optional<Expr> G = search.attempt(transformed, theta, depth - 1, inner);
    if (G.isEmpty()) {
      return Optional.empty();
    }
    // back: the multiple angles expanded, then each function of θ and θ itself in u and √q
    Expr expanded = rewrite(G.get(), MULTIPLE_ANGLES, ctx);
    Expr sqrtQ = Power.of(q, HALF);
    Expr cosOf;
    Expr sinOf;
    Expr thetaOf;
    switch (form) {
      case SINE -> {
        sinOf = Product.of(u, Power.of(a, Num.MINUS_ONE));
        cosOf = Product.of(sqrtQ, Power.of(Product.of(scale, a), Num.MINUS_ONE));
        thetaOf = call("asin", sinOf);
      }
      case TANGENT -> {
        cosOf = Product.of(scale, a, Power.of(sqrtQ, Num.MINUS_ONE));
        sinOf = Product.of(u, scale, Power.of(sqrtQ, Num.MINUS_ONE));
        thetaOf = call("atan", Product.of(u, Power.of(a, Num.MINUS_ONE)));
      }
      default -> {
        cosOf = Product.of(a, Power.of(u, Num.MINUS_ONE));
        sinOf = Product.of(Num.of(sign), sqrtQ, Power.of(Product.of(scale, u), Num.MINUS_ONE));
        thetaOf = call("asec", Product.of(u, Power.of(a, Num.MINUS_ONE)));
      }
    }
    Expr back = expanded;
    for (String g : List.of("sin", "cos", "tan", "cot", "sec", "csc")) {
      back = IntegrateRules.replace(back, call(g, theta), value(g, sinOf, cosOf), ctx);
    }
    back = IntegrateRules.replace(back, theta, thetaOf, ctx);
    if (!Free.of(back, theta)) {
      return Optional.empty();
    }
    TreeMap<String, Expr> values = new TreeMap<>();
    values.put("substitution", Relation.of(Relation.Op.EQ, u, uOf));
    values.put("transformed", Integral.indefinite(transformed, theta));
    values.put("antiderivative", G.get());
    return Optional.of(new Replacement.Valued.Result(back, values));
  }

  /** A trigonometric function of {@code θ} from its sine and cosine. */
  private static Expr value(String g, Expr sin, Expr cos) {
    return switch (g) {
      case "sin" -> sin;
      case "cos" -> cos;
      case "tan" -> Product.of(sin, Power.of(cos, Num.MINUS_ONE));
      case "cot" -> Product.of(cos, Power.of(sin, Num.MINUS_ONE));
      case "sec" -> Power.of(cos, Num.MINUS_ONE);
      default -> Power.of(sin, Num.MINUS_ONE);
    };
  }

  /**
   * The multiple-angle expansions, as rules: {@code sin(nθ)} and {@code cos(nθ)}, {@code n ≥ 2}.
   */
  private static final RuleSet MULTIPLE_ANGLES =
      new RuleSet(
          "multiple-angle",
          List.of(
              Rule.computed(
                  "sine-of-multiple",
                  Pattern.parse("sin(n_ * t_)").where("n_", TrigSubstitution::multiple),
                  mm ->
                      Optional.of(
                          Sum.of(
                              Product.of(
                                  call("sin", lower(mm)), call("cos", mm.bindings().get("t_"))),
                              Product.of(
                                  call("cos", lower(mm)), call("sin", mm.bindings().get("t_")))))),
              Rule.computed(
                  "cosine-of-multiple",
                  Pattern.parse("cos(n_ * t_)").where("n_", TrigSubstitution::multiple),
                  mm ->
                      Optional.of(
                          Sum.of(
                              Product.of(
                                  call("cos", lower(mm)), call("cos", mm.bindings().get("t_"))),
                              Product.of(
                                  Num.MINUS_ONE,
                                  call("sin", lower(mm)),
                                  call("sin", mm.bindings().get("t_"))))))));

  /** {@code tan(t)^(2k) = (sec(t)² − 1)^k}, as a rule. */
  private static final RuleSet EVEN_TANGENTS =
      new RuleSet(
          "even-tangent",
          List.of(
              Rule.computed(
                  "even-tangent",
                  Pattern.parse("tan(t_)^n_").where("n_", TrigSubstitution::even),
                  mm ->
                      Optional.of(
                          Power.of(
                              Sum.of(
                                  Power.of(call("sec", mm.bindings().get("t_")), Num.of(2)),
                                  Num.MINUS_ONE),
                              Canonical.of(Product.of(mm.bindings().get("n_"), HALF)))))));

  private static boolean even(Expr n) {
    return n instanceof Num k
        && k.value() instanceof Int i
        && i.value().signum() > 0
        && !i.value().testBit(0);
  }

  private static boolean multiple(Expr n) {
    return n instanceof Num k
        && k.value() instanceof Int i
        && i.value().compareTo(java.math.BigInteger.TWO) >= 0;
  }

  /** {@code (n − 1)·t} of a match of {@code n_ * t_}. */
  private static Expr lower(Match mm) {
    return Product.of(Sum.of(mm.bindings().get("n_"), Num.MINUS_ONE), mm.bindings().get("t_"));
  }

  private static Expr rewrite(Expr e, RuleSet rules, Context ctx) {
    return Canonical.of(Rewriter.rewrite(e, rules, ctx.withTrace(false)).expr(), ctx.precision());
  }

  /** The rule that writes {@code q^(n/2)}, {@code n} odd, as {@code root^n}. */
  private static RuleSet halfPowers(Expr q, Symbol root) {
    return new RuleSet(
        "replace",
        List.of(
            Rule.computed(
                "replace",
                Pattern.parse("b_^n_")
                    .where(
                        List.of("b_", "n_"), (b, as) -> b.get("b_").equals(q) && half(b.get("n_"))),
                mm ->
                    Optional.of(
                        Power.of(
                            root, Canonical.of(Product.of(Num.of(2), mm.bindings().get("n_"))))))));
  }

  private static boolean half(Expr n) {
    return n instanceof Num k
        && k.value() instanceof Rational r
        && r.den().equals(java.math.BigInteger.TWO);
  }

  /** The bases in {@code x} of the half-integer powers in {@code e}. */
  private static void radicals(Expr e, Symbol x, Set<Expr> out) {
    if (e instanceof Power p && half(p.exponent()) && !Free.of(p.base(), x)) {
      out.add(p.base());
    }
    for (Expr o : Operands.of(e)) {
      radicals(o, x, out);
    }
  }

  /** Whether {@code e} is a rational function of {@code x} and {@code root} over ℚ. */
  private static boolean rational(Expr e, Symbol x, Symbol root) {
    try {
      RationalFunction r = RationalFunction.of(e);
      return List.of(x, root).containsAll(r.variables())
          && r.variables().contains(root)
          && exact(r.numerator())
          && exact(r.denominator());
    } catch (IllegalArgumentException | AlgebraLimitException notRational) {
      return false;
    }
  }

  private static boolean exact(Polynomial p) {
    return p.coefficients().values().stream()
        .allMatch(c -> c instanceof Int || c instanceof Rational);
  }

  private static RealNumeric divide(RealNumeric a, RealNumeric b) {
    return (RealNumeric)
        ((Num) Canonical.of(Product.of(Num.of(a), Power.of(Num.of(b), Num.MINUS_ONE)))).value();
  }

  private static Expr call(String name, Expr arg) {
    return Apply.of(Symbol.of(name), arg);
  }
}
