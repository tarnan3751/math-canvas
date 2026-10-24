package mathcanvas.engine.simplify;

import static mathcanvas.engine.simplify.Rules.known;
import static mathcanvas.engine.simplify.Rules.scalar;
import static mathcanvas.engine.simplify.Rules.template;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Match;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;

/**
 * The rule set {@code trig}: exact values at multiples of {@code π/12} and of the inverse functions
 * there; shifts by whole multiples of {@code π} and by {@code ±π/2} removed and other rational
 * shifts reduced by the period; odd and even functions of an argument that looks negative; the
 * compositions with the inverse functions; the circular factors of a product in a normal form of
 * fewest functions; the Pythagorean identities and the double angles in their collapsing direction
 * only; {@code cosh² - sinh²} and {@code sinh/cosh}. The identities hold over the complex numbers
 * with the engine's principal branches, except where a rule asks for a real argument or a range.
 * Every rule removes a term, a factor, a shift or a level of application, or turns an argument that
 * looks negative into one that does not ({@code docs/reference/engine.md}, <b>Simplification</b>).
 */
final class TrigRules {

  private TrigRules() {}

  private static final Set<String> CIRCULAR = Set.copyOf(TrigTables.CIRCULAR);

  private static final Set<String> INVERSE = Set.of("asin", "acos", "atan", "acot", "asec", "acsc");

  private static final Set<String> ODD =
      Set.of(
          "sin", "tan", "cot", "csc", "sinh", "tanh", "asin", "atan", "acot", "acsc", "asinh",
          "atanh");

  private static final Set<String> EVEN = Set.of("cos", "sec", "cosh");

  static final RuleSet RULES =
      new RuleSet(
          "trig",
          List.of(
              Rule.computed(
                  "special-angle",
                  call("h_", Symbol.of("x_"))
                      .where("h_", head(CIRCULAR))
                      .where("x_", x -> TrigTables.piCoefficient(x) != null),
                  m ->
                      Optional.of(
                          TrigTables.specialValue(
                              name(m.bindings()),
                              TrigTables.piCoefficient(m.bindings().get("x_"))))),
              Rule.computed(
                  "inverse-special-value",
                  call("h_", Symbol.of("x_")).where("h_", head(INVERSE)),
                  m -> TrigTables.inverseValue(name(m.bindings()), m.bindings().get("x_"))),
              Rule.computed(
                  "shift-by-multiple-of-pi",
                  call("h_", Sum.of(Product.of(Constant.PI, Symbol.of("k___")), Symbol.of("u___")))
                      .where("h_", head(CIRCULAR))
                      .where(List.of("k___"), (b, a) -> a.isInteger(b.get("k___")) == Tri.TRUE),
                  TrigRules::shiftByMultipleOfPi),
              Rule.computed(
                  "reduce-rational-shift",
                  call("h_", Sum.of(Product.of(Symbol.of("q_"), Constant.PI), Symbol.of("u__")))
                      .where("h_", head(CIRCULAR))
                      .where("q_", q -> Rules.fraction(q) != null)
                      .where(
                          List.of("h_", "q_"),
                          (b, a) ->
                              !TrigTables.reduceShift(name(b), Rules.fraction(b.get("q_")))
                                  .equals(Rules.fraction(b.get("q_")))),
                  m ->
                      Optional.of(
                          Apply.of(
                              Symbol.of(name(m.bindings())),
                              Sum.of(
                                  Product.of(
                                      Num.of(
                                          TrigTables.reduceShift(
                                              name(m.bindings()),
                                              Rules.fraction(m.bindings().get("q_")))),
                                      Constant.PI),
                                  m.bindings().get("u__"))))),
              quarterTurn("sin-plus-half-pi", "sin(u__ + pi/2)", "cos(u__)"),
              quarterTurn("sin-minus-half-pi", "sin(u__ - pi/2)", "-cos(u__)"),
              quarterTurn("cos-plus-half-pi", "cos(u__ + pi/2)", "-sin(u__)"),
              quarterTurn("cos-minus-half-pi", "cos(u__ - pi/2)", "sin(u__)"),
              quarterTurn("tan-plus-half-pi", "tan(u__ + pi/2)", "-cot(u__)"),
              quarterTurn("cot-plus-half-pi", "cot(u__ + pi/2)", "-tan(u__)"),
              quarterTurn("sec-plus-half-pi", "sec(u__ + pi/2)", "-csc(u__)"),
              quarterTurn("sec-minus-half-pi", "sec(u__ - pi/2)", "csc(u__)"),
              quarterTurn("csc-plus-half-pi", "csc(u__ + pi/2)", "sec(u__)"),
              quarterTurn("csc-minus-half-pi", "csc(u__ - pi/2)", "-sec(u__)"),
              Rule.computed(
                  "odd-function",
                  scalar(
                      call("h_", Symbol.of("u_"))
                          .where("h_", head(ODD))
                          .where("u_", TrigTables::looksNegative)
                          .where(
                              List.of("h_", "u_"),
                              (b, a) ->
                                  !name(b).equals("acot") || a.isNonZero(b.get("u_")) == Tri.TRUE),
                      "u_"),
                  m ->
                      Optional.of(
                          Product.of(
                              Num.MINUS_ONE,
                              Apply.of(
                                  Symbol.of(name(m.bindings())),
                                  TrigTables.negate(m.bindings().get("u_")))))),
              Rule.computed(
                  "even-function",
                  scalar(
                      call("h_", Symbol.of("u_"))
                          .where("h_", head(EVEN))
                          .where("u_", TrigTables::looksNegative),
                      "u_"),
                  m ->
                      Optional.of(
                          Apply.of(
                              Symbol.of(name(m.bindings())),
                              TrigTables.negate(m.bindings().get("u_"))))),
              Rule.of(
                  "acos-of-negative",
                  Pattern.parse("acos(u_)").where("u_", TrigRules::negativeNumberOrCoefficient),
                  template("pi - acos(-u_)")),
              Rule.of(
                  "asec-of-negative",
                  Pattern.parse("asec(u_)").where("u_", TrigRules::negativeNumberOrCoefficient),
                  template("pi - asec(-u_)")),
              Rule.of("sin-of-asin", Pattern.parse("sin(asin(u_))"), template("u_")),
              Rule.of("cos-of-acos", Pattern.parse("cos(acos(u_))"), template("u_")),
              Rule.of("tan-of-atan", Pattern.parse("tan(atan(u_))"), template("u_")),
              Rule.of(
                  "sin-of-acos",
                  scalar(Pattern.parse("sin(acos(u_))"), "u_"),
                  template("sqrt(1 - u_^2)")),
              Rule.of(
                  "cos-of-asin",
                  scalar(Pattern.parse("cos(asin(u_))"), "u_"),
                  template("sqrt(1 - u_^2)")),
              Rule.of(
                  "sin-of-atan",
                  known(Pattern.parse("sin(atan(u_))"), Assumptions::isReal, "u_"),
                  template("u_ / sqrt(1 + u_^2)")),
              Rule.of(
                  "cos-of-atan",
                  known(Pattern.parse("cos(atan(u_))"), Assumptions::isReal, "u_"),
                  template("1 / sqrt(1 + u_^2)")),
              Rule.of(
                  "asin-of-sin",
                  Pattern.parse("asin(sin(u_))")
                      .where(
                          List.of("u_"), (b, a) -> TrigTables.withinHalfPi(b.get("u_"), a, false)),
                  template("u_")),
              Rule.of(
                  "atan-of-tan",
                  Pattern.parse("atan(tan(u_))")
                      .where(
                          List.of("u_"), (b, a) -> TrigTables.withinHalfPi(b.get("u_"), a, true)),
                  template("u_")),
              Rule.of(
                  "atan-of-reciprocal",
                  known(Pattern.parse("atan(u_^-1)"), Assumptions::isPositive, "u_"),
                  template("pi/2 - atan(u_)")),
              Rule.computed(
                  "combine-trig-factors",
                  Pattern.of(
                          Product.of(
                              Power.of(Apply.of(Symbol.of("h_"), Symbol.of("u_")), Symbol.of("n_")),
                              Symbol.of("rest___")))
                      .optional("n_", Num.ONE)
                      .where("h_", head(CIRCULAR))
                      .where("n_", n -> n instanceof Num k && k.value() instanceof Int),
                  m -> TrigTables.combineFactors(m.subject())),
              Rule.of(
                  "sine-double-angle",
                  scalar(Pattern.parse("c_ * sin(u_) * cos(u_) * rest___"), "u_")
                      .where("c_", TrigRules::evenNumerator),
                  template("c_/2 * sin(2*u_) * rest___")),
              Rule.of(
                  "tanh-of-quotient",
                  Pattern.parse("sinh(u_)^n_ * cosh(u_)^m_ * rest___")
                      .optional("n_", Num.ONE)
                      .where(
                          List.of("n_", "m_"),
                          (b, a) ->
                              b.get("n_") instanceof Num n
                                  && n.value() instanceof Int
                                  && Canonical.of(Product.of(Num.MINUS_ONE, n))
                                      .equals(b.get("m_"))),
                  template("tanh(u_)^n_ * rest___")),
              sumRule("pythagorean", "sin", (d, u) -> times(1, d, square("cos", u)), (d, u) -> d),
              sumRule(
                  "one-minus-sin-squared",
                  "sin",
                  (d, u) -> times(-1, d),
                  (d, u) -> times(-1, d, square("cos", u))),
              sumRule(
                  "one-minus-cos-squared",
                  "cos",
                  (d, u) -> times(-1, d),
                  (d, u) -> times(-1, d, square("sin", u))),
              sumRule(
                  "one-plus-tan-squared",
                  "tan",
                  (d, u) -> d,
                  (d, u) -> times(1, d, square("sec", u))),
              sumRule(
                  "one-plus-cot-squared",
                  "cot",
                  (d, u) -> d,
                  (d, u) -> times(1, d, square("csc", u))),
              sumRule(
                  "cosine-double-angle",
                  "cos",
                  (d, u) -> times(-1, d, square("sin", u)),
                  (d, u) -> times(1, d, doubleAngle(u))),
              sumRule(
                  "cosine-double-angle-from-cos",
                  "cos",
                  (d, u) -> times(Rational.of(-1, 2), d),
                  (d, u) -> times(Rational.of(1, 2), d, doubleAngle(u))),
              sumRule(
                  "cosine-double-angle-from-sin",
                  "sin",
                  (d, u) -> times(Rational.of(-1, 2), d),
                  (d, u) -> times(Rational.of(-1, 2), d, doubleAngle(u))),
              sumRule(
                  "hyperbolic-pythagorean",
                  "cosh",
                  (d, u) -> times(-1, d, square("sinh", u)),
                  (d, u) -> d)));

  /** {@code h(arg)} with a head variable, as a pattern. */
  private static Pattern call(String head, Expr arg) {
    return Pattern.of(Apply.of(Symbol.of(head), arg));
  }

  /** The condition that a head variable names one of {@code names}. */
  private static Predicate<Expr> head(Set<String> names) {
    return h -> h instanceof Symbol s && names.contains(s.name());
  }

  private static String name(Bindings b) {
    return ((Symbol) b.get("h_")).name();
  }

  private static Rule quarterTurn(String name, String left, String right) {
    return Rule.of(name, Pattern.parse(left), template(right));
  }

  /**
   * A sum rule anchored on a term {@code d·f(u)²}: it applies when the other terms include {@code
   * partner(d, u)}, and replaces the two by {@code result(d, u)}. The replacement does so for every
   * such pair of the sum at once ({@link TrigTables#collapsePairs}): one rewrite per rule, rather
   * than one per pair, each of which would revisit every term of a long sum.
   */
  private static Rule sumRule(
      String name, String f, BinaryOperator<Expr> partner, BinaryOperator<Expr> result) {
    return Rule.computed(
        name,
        scalar(Pattern.parse("d___ * " + f + "(u_)^2 + rest__"), "d___", "u_")
            .where(
                List.of("d___", "u_", "rest__"),
                (b, a) ->
                    b.sequence("rest__")
                        .contains(Canonical.of(partner.apply(b.get("d___"), b.get("u_"))))),
        m -> TrigTables.collapsePairs(m.subject(), f, partner, result));
  }

  /** {@code c·factors}, raw. */
  private static Expr times(long c, Expr... factors) {
    return times(Int.of(c), factors);
  }

  private static Expr times(RealNumeric c, Expr... factors) {
    List<Expr> all = new ArrayList<>();
    all.add(Num.of(c));
    all.addAll(List.of(factors));
    return Product.of(all);
  }

  private static Expr square(String g, Expr u) {
    return Power.of(Apply.of(Symbol.of(g), u), Num.of(2));
  }

  private static Expr doubleAngle(Expr u) {
    return Apply.of(Symbol.of("cos"), Product.of(Num.of(2), u));
  }

  /**
   * {@code sin(u + πk)} for an integer {@code k}: {@code u} alone for {@code tan} and {@code cot},
   * else times {@code (-1)^k}, which is {@code ±1} when the parity of {@code k} is known, and is
   * written with whichever of {@code k} and {@code -k} does not look negative.
   */
  private static Optional<Expr> shiftByMultipleOfPi(Match m) {
    String f = name(m.bindings());
    Expr call = Apply.of(Symbol.of(f), m.bindings().get("u___"));
    if (f.equals("tan") || f.equals("cot")) {
      return Optional.of(call);
    }
    Expr k = m.bindings().get("k___");
    Assumptions a = m.context().assumptions();
    Expr sign;
    if (a.isEven(k) == Tri.TRUE) {
      sign = Num.ONE;
    } else if (a.isOdd(k) == Tri.TRUE) {
      sign = Num.MINUS_ONE;
    } else {
      Expr exponent = TrigTables.looksNegative(k) ? TrigTables.negate(k) : k;
      sign = Power.of(Num.MINUS_ONE, exponent);
    }
    return Optional.of(Product.of(sign, call));
  }

  /** A negative real number, or a product with a negative real coefficient. */
  private static boolean negativeNumberOrCoefficient(Expr u) {
    return !(u instanceof Sum) && TrigTables.looksNegative(u);
  }

  /** An exact rational whose numerator is even and not zero. */
  private static boolean evenNumerator(Expr c) {
    RealNumeric q = Rules.rational(c);
    if (q == null || q.signum() == 0) {
      return false;
    }
    BigInteger n = q instanceof Rational r ? r.num() : ((Int) q).value();
    return !n.testBit(0);
  }
}
