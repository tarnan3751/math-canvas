package mathcanvas.engine.simplify;

import static mathcanvas.engine.simplify.Rules.known;
import static mathcanvas.engine.simplify.Rules.scalar;
import static mathcanvas.engine.simplify.Rules.template;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
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
import mathcanvas.engine.match.Operands;
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
                  m ->
                      radianArgument(m) && keepsItsUnit(m, m.bindings().get("u___"))
                          ? Optional.of(
                              shift(
                                  name(m.bindings()),
                                  m.bindings().get("u___"),
                                  m.bindings().get("k___"),
                                  m.context().assumptions()))
                          : Optional.empty()),
              Rule.computed(
                  "shift-by-half-turns",
                  call("h_", Symbol.of("x_"))
                      .where("h_", head(CIRCULAR))
                      .where("x_", x -> x instanceof Sum),
                  TrigRules::shiftByHalfTurns),
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
                      !radianArgument(m)
                          ? Optional.empty()
                          : Optional.of(
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
              Rule.computed(
                  "reduce-half-turn-shift",
                  call("h_", Symbol.of("x_"))
                      .where("h_", head(CIRCULAR))
                      .where("x_", x -> x instanceof Sum),
                  TrigRules::reduceHalfTurnShift),
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
                  "quarter-turn-in-unit",
                  call("h_", Symbol.of("x_"))
                      .where("h_", head(CIRCULAR))
                      .where("x_", x -> x instanceof Sum),
                  TrigRules::quarterTurnInUnit),
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
              radian(
                  "acos-of-negative",
                  Pattern.parse("acos(u_)").where("u_", TrigRules::negativeNumberOrCoefficient),
                  "pi - acos(-u_)",
                  null),
              inUnit(
                  "acos-of-negative-in-unit",
                  Pattern.parse("acos(u_)").where("u_", TrigRules::negativeNumberOrCoefficient),
                  (h, u) ->
                      Sum.of(Num.of(h), Product.of(Num.MINUS_ONE, applyTo("acos", negateRaw(u))))),
              radian(
                  "asec-of-negative",
                  Pattern.parse("asec(u_)").where("u_", TrigRules::negativeNumberOrCoefficient),
                  "pi - asec(-u_)",
                  null),
              inUnit(
                  "asec-of-negative-in-unit",
                  Pattern.parse("asec(u_)").where("u_", TrigRules::negativeNumberOrCoefficient),
                  (h, u) ->
                      Sum.of(Num.of(h), Product.of(Num.MINUS_ONE, applyTo("asec", negateRaw(u))))),
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
              radian(
                  "atan-of-reciprocal",
                  known(Pattern.parse("atan(u_^-1)"), Assumptions::isPositive, "u_"),
                  "pi/2 - atan(u_)",
                  null),
              inUnit(
                  "atan-of-reciprocal-in-unit",
                  known(Pattern.parse("atan(u_^-1)"), Assumptions::isPositive, "u_"),
                  (h, u) ->
                      Sum.of(
                          Num.of(Rational.of(h, 2)),
                          Product.of(Num.MINUS_ONE, applyTo("atan", u)))),
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
                  (d, u) -> d),
              sumRule(
                  "one-plus-sinh-squared",
                  "sinh",
                  (d, u) -> d,
                  (d, u) -> times(1, d, square("cosh", u))),
              sumRule(
                  "cosh-squared-minus-one",
                  "cosh",
                  (d, u) -> times(-1, d),
                  (d, u) -> times(1, d, square("sinh", u))),
              sumRule(
                  "one-minus-tanh-squared",
                  "tanh",
                  (d, u) -> times(-1, d),
                  (d, u) -> times(-1, d, square("sech", u))),
              sumRule(
                  "coth-squared-minus-one",
                  "coth",
                  (d, u) -> times(-1, d),
                  (d, u) -> times(1, d, square("csch", u)))));

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
    return radian(name, Pattern.parse(left), right, "u__");
  }

  /**
   * A radian identity written as a template: it applies in radians, and in degrees or gradians to
   * an argument read in radians ({@link #radianArgument}) when the argument it leaves, the binding
   * {@code rest}, is still read in radians ({@link #keepsItsUnit}); with no {@code rest}, an
   * inverse function's reflection, only to a closed argument ({@link #closedArgument}).
   */
  private static Rule radian(String name, Pattern pattern, String right, String rest) {
    Expr template = template(right);
    return Rule.computed(
        name,
        pattern,
        m ->
            (rest == null
                    ? closedArgument(m)
                    : radianArgument(m) && keepsItsUnit(m, m.bindings().get(rest)))
                ? Optional.of(m.bindings().substituteInto(template))
                : Optional.empty());
  }

  /**
   * Whether an inverse function's reflection may apply in radians: always in radians, and in
   * degrees or gradians only to a closed argument. A constant-typed one is 0 where its coefficient
   * vanishes, where the inverse function of a plain 0 answers in the unit: {@code acos(-πx)} is 90
   * in degrees at {@code x = 0}, while {@code π - acos(πx)} is {@code π - 90}.
   */
  private static boolean closedArgument(Match m) {
    return halfTurn(m) == 0 || !hasSymbol(argument(m));
  }

  /**
   * The same identity in the session's unit, {@code right} given the half turn and {@code u_}: it
   * applies in degrees or gradians to an argument with a symbol that is plain-typed ({@link
   * #unitArgument}).
   */
  private static Rule inUnit(String name, Pattern pattern, BiFunction<Integer, Expr, Expr> right) {
    return Rule.computed(
        name,
        pattern,
        m ->
            unitArgument(m)
                ? Optional.of(right.apply(halfTurn(m), m.bindings().get("u_")))
                : Optional.empty());
  }

  /** The session's half turn: 180 in degrees, 200 in gradians, 0 in radians. */
  private static int halfTurn(Match m) {
    return m.context().numerics().halfTurn();
  }

  /** The argument of the call the rule matched. */
  private static Expr argument(Match m) {
    return ((Apply) m.subject()).args().getFirst();
  }

  /**
   * Whether a radian identity may rewrite the matched call: always in radians; in degrees or
   * gradians when its argument is read in radians at every use. That is a closed argument (§14.2:
   * {@code sin(π)} is 0 in degrees, and the evaluator's own conversions are written that way:
   * {@code acos(-1/2)} in degrees is {@code 180·acos(-1/2)/π}) or a constant-typed one ({@link
   * #constantTyped}); a plain-typed argument takes the unit, and any other with a symbol is
   * ambiguous, so the radian identity is not applied to them.
   */
  private static boolean radianArgument(Match m) {
    Expr x = argument(m);
    return halfTurn(m) == 0 || !hasSymbol(x) || constantTyped(x);
  }

  /**
   * Whether the argument a radian identity leaves, {@code rest}, is read in radians at every value
   * where the argument it came from is, in degrees or gradians. It is when {@code rest} is 0, the
   * same angle in every unit; when it is closed and not a plain number; when it has no plain-typed
   * term, so that it is 0 wherever its constant terms vanish; or when it holds a constant term and
   * each has a fixed non-zero rational coefficient ({@code π/3}, never {@code πy}), so that it is
   * never plain. A remainder with a plain term beside a constant term whose coefficient can be 0 is
   * plain there, read in the unit, while the argument it came from still held the removed shift:
   * {@code sin(πy + x + π/2)} does not become {@code cos(x + πy)}, which at {@code y = 0} is read
   * in degrees. Likewise {@code sin(x + π)} does not become {@code -sin(x)}, nor {@code sin(1.0 +
   * π)} {@code -sin(1.0)}.
   */
  private static boolean keepsItsUnit(Match m, Expr rest) {
    if (halfTurn(m) == 0 || rest == null || rest.equals(Num.ZERO)) {
      return true;
    }
    if (!hasSymbol(rest)) {
      return !plainTyped(rest);
    }
    List<Expr> terms = rest instanceof Sum s ? s.terms() : List.of(rest);
    boolean plainTerm = terms.stream().anyMatch(TrigRules::plainTyped);
    if (!plainTerm) {
      return true;
    }
    List<Expr> constants = terms.stream().filter(t -> !plainTyped(t)).toList();
    return !constants.isEmpty() && constants.stream().allMatch(TrigRules::fixedConstantTerm);
  }

  /** {@code π} or {@code e} times a non-zero rational, or alone. */
  private static boolean fixedConstantTerm(Expr t) {
    if (isAngleConstant(t)) {
      return true;
    }
    return t instanceof Product p
        && p.factors().size() == 2
        && p.factors().stream().anyMatch(TrigRules::isAngleConstant)
        && p.factors().stream()
            .anyMatch(f -> f instanceof Num n && n.value().isExact() && !n.value().isZero());
  }

  /**
   * Whether an argument with a symbol is constant-typed: each of its top-level terms is plain-typed
   * or holds {@code π} or {@code e} once, as a factor beside plain-typed ones ({@code 2π}, {@code
   * πk}), and at least one does. Under every plain value of its symbols, exact or decimal, such a
   * term is a rational or a decimal times the constant, or 0; §14.1 folds neither a decimal nor a
   * rational into {@code π} or {@code e} in a product or a sum, so the argument is a plain number
   * only where every constant term's coefficient is 0. The radian shifts and quarter turns apply to
   * it when what they leave keeps that property ({@link #keepsItsUnit}): then, where the argument
   * is plain, the removed shift was 0 as well, and the rewrite changes nothing.
   */
  private static boolean constantTyped(Expr x) {
    List<Expr> terms = x instanceof Sum s ? s.terms() : List.of(x);
    boolean constant = false;
    for (Expr t : terms) {
      if (plainTyped(t)) {
        continue;
      }
      if (!constantTerm(t)) {
        return false;
      }
      constant = true;
    }
    return constant;
  }

  /** {@code π} or {@code e}, alone or as one factor beside plain-typed ones. */
  private static boolean constantTerm(Expr t) {
    if (isAngleConstant(t)) {
      return true;
    }
    if (!(t instanceof Product p)) {
      return false;
    }
    int constants = 0;
    for (Expr f : p.factors()) {
      if (isAngleConstant(f)) {
        constants++;
      } else if (!plainTyped(f)) {
        return false;
      }
    }
    return constants == 1;
  }

  /** Whether {@code e} holds a decimal: a decimal multiple of a half turn is no whole one. */
  private static boolean inexact(Expr e) {
    if (e instanceof Num n) {
      return !n.value().isExact();
    }
    for (Expr o : Operands.of(e)) {
      if (inexact(o)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isAngleConstant(Expr e) {
    return e.equals(Constant.PI) || e.equals(Constant.E);
  }

  /**
   * The argument a rule in the session's unit leaves: as it is, or, when it has lost its symbols,
   * the evaluator's conversion of that plain number to radians, {@code rest·π/H}, so that the
   * radian rules read it as the unit meant ({@code sin(360n + 30)} is {@code sin(π/6)}).
   */
  private static Expr inUnit(Match m, Expr rest) {
    return hasSymbol(rest)
        ? rest
        : Product.of(rest, Constant.PI, Power.of(Num.of(halfTurn(m)), Num.MINUS_ONE));
  }

  /**
   * Whether an identity in the session's unit may rewrite the matched call: in degrees or gradians,
   * for an argument with a symbol that is plain-typed, so that it is a plain number at every plain
   * value of its symbols, exact or decimal, and is read in the session's unit whenever it is used.
   * An argument with a constant, a radical or a function is left alone: whether it is plain at use
   * depends on whether the value put in is exact or decimal.
   */
  private static boolean unitArgument(Match m) {
    Expr x = argument(m);
    return halfTurn(m) != 0 && hasSymbol(x) && plainTyped(x);
  }

  /** Whether {@code e} holds a symbol. */
  private static boolean hasSymbol(Expr e) {
    if (e instanceof Symbol) {
      return true;
    }
    for (Expr o : Operands.of(e)) {
      if (hasSymbol(o)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether {@code e} is built from real numbers and symbols by sums, products and integer powers.
   */
  private static boolean plainTyped(Expr e) {
    return switch (e) {
      case Num n -> n.value() instanceof RealNumeric;
      case Symbol _ -> true;
      case Sum s -> s.terms().stream().allMatch(TrigRules::plainTyped);
      case Product p -> p.factors().stream().allMatch(TrigRules::plainTyped);
      case Power w ->
          plainTyped(w.base()) && w.exponent() instanceof Num k && k.value() instanceof Int;
      default -> false;
    };
  }

  /** {@code name(arg)}, raw. */
  private static Expr applyTo(String name, Expr arg) {
    return Apply.of(Symbol.of(name), arg);
  }

  /** The sum of {@code terms}, raw: the term itself when there is one. */
  private static Expr sum(List<Expr> terms) {
    return terms.size() == 1 ? terms.getFirst() : Sum.of(terms);
  }

  private static Expr negateRaw(Expr u) {
    return TrigTables.negate(u);
  }

  /**
   * {@code h(u + k·H)} in degrees or gradians, {@code H} the half turn and {@code k} an integer (a
   * number, or a symbol known to be an integer, as {@code 180n}): the shift removed as {@link
   * #shift} removes {@code kπ} in radians.
   */
  private static Optional<Expr> shiftByHalfTurns(Match m) {
    if (!unitArgument(m)) {
      return Optional.empty();
    }
    String f = name(m.bindings());
    int h = halfTurn(m);
    List<Expr> terms = ((Sum) argument(m)).terms();
    Assumptions a = m.context().assumptions();
    for (int i = 0; i < terms.size(); i++) {
      Expr k = Canonical.of(Product.of(terms.get(i), Num.of(Rational.of(1, h))));
      if (k.equals(Num.ZERO) || inexact(k) || a.isInteger(k) != Tri.TRUE) {
        continue;
      }
      List<Expr> rest = new ArrayList<>(terms);
      rest.remove(i);
      return Optional.of(shift(f, inUnit(m, sum(rest)), k, a));
    }
    return Optional.empty();
  }

  /**
   * {@code h(u + q·H)} in degrees or gradians, {@code q} a rational outside the centred range of
   * {@code h}'s period ({@link TrigTables#reduceShift}): the shift reduced, as {@code
   * reduce-rational-shift} reduces {@code qπ}.
   */
  private static Optional<Expr> reduceHalfTurnShift(Match m) {
    if (!unitArgument(m)) {
      return Optional.empty();
    }
    String f = name(m.bindings());
    int h = halfTurn(m);
    List<Expr> terms = ((Sum) argument(m)).terms();
    for (int i = 0; i < terms.size(); i++) {
      Rational q =
          Rules.fraction(Canonical.of(Product.of(terms.get(i), Num.of(Rational.of(1, h)))));
      if (!(terms.get(i) instanceof Num) || q == null) {
        continue;
      }
      RealNumeric reduced = TrigTables.reduceShift(f, q);
      if (reduced.equals(q)) {
        continue;
      }
      List<Expr> rest = new ArrayList<>(terms);
      rest.set(i, Product.of(Num.of(reduced), Num.of(h)));
      return Optional.of(applyTo(f, inUnit(m, sum(rest))));
    }
    return Optional.empty();
  }

  /**
   * The quarter turns in degrees or gradians: {@code h(u ± H/2)}, {@code H} the half turn, as the
   * radian rules {@code sin-plus-half-pi} and the rest turn {@code h(u ± π/2)}, from the same table
   * ({@link #QUARTER_TURNS}).
   */
  private static Optional<Expr> quarterTurnInUnit(Match m) {
    if (!unitArgument(m)) {
      return Optional.empty();
    }
    String f = name(m.bindings());
    int h = halfTurn(m);
    List<Expr> terms = ((Sum) argument(m)).terms();
    for (int i = 0; i < terms.size(); i++) {
      if (!(terms.get(i) instanceof Num n)) {
        continue;
      }
      int sign;
      if (n.value().equals(Rational.of(h, 2))) {
        sign = 1;
      } else if (n.value().equals(Rational.of(-h, 2))) {
        sign = -1;
      } else {
        continue;
      }
      QuarterTurn t = QUARTER_TURNS.get(f).get(sign);
      if (t == null) {
        continue;
      }
      List<Expr> rest = new ArrayList<>(terms);
      rest.remove(i);
      return Optional.of(Product.of(Num.of(t.sign()), applyTo(t.function(), inUnit(m, sum(rest)))));
    }
    return Optional.empty();
  }

  /** A quarter turn's result: {@code sign·function(u)}. */
  private record QuarterTurn(String function, int sign) {}

  /**
   * The quarter turns the radian rules take, by function and the sign of the quarter turn: {@code
   * sin(u + π/2)} is {@code cos(u)}, {@code sin(u - π/2)} is {@code -cos(u)}, and so on; {@code
   * tan} and {@code cot} take only {@code +}, the other sign being reduced to it first.
   */
  private static final Map<String, Map<Integer, QuarterTurn>> QUARTER_TURNS =
      Map.of(
          "sin", Map.of(1, new QuarterTurn("cos", 1), -1, new QuarterTurn("cos", -1)),
          "cos", Map.of(1, new QuarterTurn("sin", -1), -1, new QuarterTurn("sin", 1)),
          "tan", Map.of(1, new QuarterTurn("cot", -1)),
          "cot", Map.of(1, new QuarterTurn("tan", -1)),
          "sec", Map.of(1, new QuarterTurn("csc", -1), -1, new QuarterTurn("csc", 1)),
          "csc", Map.of(1, new QuarterTurn("sec", 1), -1, new QuarterTurn("sec", -1)));

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
   * {@code f(u)} shifted by {@code k} half turns, {@code k} an integer: {@code f(u)} alone for
   * {@code tan} and {@code cot}, else times {@code (-1)^k}, which is {@code ±1} when the parity of
   * {@code k} is known, and is written with whichever of {@code k} and {@code -k} does not look
   * negative.
   */
  private static Expr shift(String f, Expr u, Expr k, Assumptions a) {
    Expr call = Apply.of(Symbol.of(f), u);
    if (f.equals("tan") || f.equals("cot")) {
      return call;
    }
    Expr sign;
    if (a.isEven(k) == Tri.TRUE) {
      sign = Num.ONE;
    } else if (a.isOdd(k) == Tri.TRUE) {
      sign = Num.MINUS_ONE;
    } else {
      Expr exponent = TrigTables.looksNegative(k) ? TrigTables.negate(k) : k;
      sign = Power.of(Num.MINUS_ONE, exponent);
    }
    return Product.of(sign, call);
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
