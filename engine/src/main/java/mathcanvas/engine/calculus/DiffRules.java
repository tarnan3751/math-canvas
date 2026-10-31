package mathcanvas.engine.calculus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Lambda;
import mathcanvas.engine.expr.Limit;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Piecewise;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Matcher;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Match;
import mathcanvas.engine.rule.Replacement;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;

/**
 * The rule set {@code diff}: every rule rewrites a {@code Derivative} node, written {@code diff(u_,
 * x_)} in the patterns. The rewriter works innermost-first, so linearity, the product, quotient and
 * power rules and the chain rule drive the whole computation one {@code Derivative} at a time; a
 * {@code Derivative} no rule matches stays (the unevaluated form). Every rule replaces a derivative
 * of order 1 with derivatives of strict subterms of its expression, or with none; {@code
 * higher-order} and {@code merge-orders} trade orders for nesting under one predicate, {@link
 * #stuck}, which makes them exclusive ({@code docs/reference/engine.md}, <b>Differentiation</b>).
 *
 * <p>The derivatives of the builtin functions of one argument are one table, {@link #TABLE}: a rule
 * per function for an argument that is the variable itself, and the {@code chain-rule} for any
 * other argument, both reading the table. The formulas hold for the principal values wherever the
 * function is differentiable; {@code asec}, {@code acsc} and {@code acsch} use {@code |u|} only for
 * a real argument; {@code abs}, {@code sign} and the step functions need a real argument and ignore
 * their jumps.
 */
final class DiffRules {

  private DiffRules() {}

  /**
   * A function's derivative, written in its argument {@code u_}: the formula for a real argument,
   * whether only a real argument has one, and the formula for an argument not known to be real
   * where it differs (null where it does not).
   */
  private record Entry(String real, boolean realOnly, String general) {

    /** The formula for the argument {@code u}, or null when there is none. */
    String formula(Expr u, Assumptions a) {
      boolean isReal = a.isReal(u) == Tri.TRUE;
      if (realOnly && !isReal) {
        return null;
      }
      return general != null && !isReal ? general : real;
    }
  }

  /** The derivatives of the builtin functions of one argument, in the order of their rules. */
  private static final Map<String, Entry> TABLE = new LinkedHashMap<>();

  static {
    entry("exp", "exp(u_)");
    entry("ln", "1/u_");
    entry("log", "1/u_");
    entry("log10", "1/(u_ * log(10))");
    entry("log2", "1/(u_ * log(2))");
    entry("cbrt", "1/(3 * (cbrt(u_))^2)");
    entry("sin", "cos(u_)");
    entry("cos", "-sin(u_)");
    entry("tan", "(sec(u_))^2");
    entry("cot", "-(csc(u_))^2");
    entry("sec", "sec(u_) * tan(u_)");
    entry("csc", "-csc(u_) * cot(u_)");
    entry("asin", "1/sqrt(1 - u_^2)");
    entry("acos", "-1/sqrt(1 - u_^2)");
    entry("atan", "1/(1 + u_^2)");
    entry("acot", "-1/(1 + u_^2)");
    // asec, acsc and acsch: |u| for a real u, the principal form u²·√(1 ∓ 1/u²) otherwise.
    TABLE.put(
        "asec", new Entry("1/(abs(u_) * sqrt(u_^2 - 1))", false, "1/(u_^2 * sqrt(1 - 1/u_^2))"));
    TABLE.put(
        "acsc", new Entry("-1/(abs(u_) * sqrt(u_^2 - 1))", false, "-1/(u_^2 * sqrt(1 - 1/u_^2))"));
    entry("sinh", "cosh(u_)");
    entry("cosh", "sinh(u_)");
    entry("tanh", "(sech(u_))^2");
    entry("coth", "-(csch(u_))^2");
    entry("sech", "-sech(u_) * tanh(u_)");
    entry("csch", "-csch(u_) * coth(u_)");
    entry("asinh", "1/sqrt(u_^2 + 1)");
    entry("acosh", "1/(sqrt(u_ - 1) * sqrt(u_ + 1))");
    entry("atanh", "1/(1 - u_^2)");
    entry("acoth", "1/(1 - u_^2)");
    entry("asech", "-1/(u_^2 * sqrt(1/u_ - 1) * sqrt(1/u_ + 1))");
    TABLE.put(
        "acsch", new Entry("-1/(abs(u_) * sqrt(u_^2 + 1))", false, "-1/(u_^2 * sqrt(1 + 1/u_^2))"));
    TABLE.put("abs", new Entry("sign(u_)", true, null));
    for (String step : List.of("sign", "floor", "ceil", "round", "trunc")) {
      TABLE.put(step, new Entry("0", true, null));
    }
  }

  private static void entry(String name, String derivative) {
    TABLE.put(name, new Entry(derivative, false, null));
  }

  private static final Symbol U = Symbol.of("u_");
  private static final Symbol X = Symbol.of("x_");

  static final RuleSet RULES = new RuleSet("diff", rules());

  private static List<Rule> rules() {
    List<Rule> rules = new ArrayList<>();
    rules.add(
        Rule.of(
            "higher-order-constant",
            Pattern.parse("d_")
                .where(
                    "d_",
                    e -> e instanceof Derivative d && d.order() > 1 && constant(d.expr(), d.var())),
            Num.ZERO));
    rules.add(
        Rule.computed(
            "higher-order",
            Pattern.parse("d_").where("d_", e -> e instanceof Derivative d && d.order() > 1),
            DiffRules::unroll));
    rules.add(
        Rule.computed(
            "merge-orders",
            Pattern.parse("diff(d_, x_)")
                .where(
                    List.of("d_", "x_"),
                    (b, a) -> b.get("d_") instanceof Derivative d && d.var().equals(b.get("x_"))),
            DiffRules::merge));
    rules.add(Rule.of("undefined", Pattern.parse("diff(undefined, x_)"), template("undefined")));
    rules.add(
        Rule.of(
            "constant",
            Pattern.parse("diff(c_, x_)")
                .where(List.of("c_", "x_"), (b, a) -> constant(b.get("c_"), b.get("x_"))),
            Num.ZERO));
    rules.add(
        Rule.computed(
            "vector",
            Pattern.parse("diff(v_, x_)").where("v_", v -> v instanceof Vector),
            m -> Optional.of(Vector.of(each(((Vector) m.bindings().get("v_")).items(), m)))));
    rules.add(
        Rule.computed(
            "matrix",
            Pattern.parse("diff(v_, x_)").where("v_", v -> v instanceof Matrix),
            DiffRules::matrix));
    rules.add(
        Rule.computed(
            "piecewise",
            Pattern.parse("diff(p_, x_)").where("p_", p -> p instanceof Piecewise),
            DiffRules::piecewise));
    rules.add(Rule.of("variable", Pattern.parse("diff(x_, x_)"), Num.ONE));
    rules.add(
        Rule.computed("sum-rule", Pattern.parse("diff(u_ + rest__, x_)"), DiffRules::termwise));
    rules.add(
        Rule.of(
            "constant-multiple",
            Pattern.parse("diff(c_ * u__, x_)")
                .where(
                    List.of("c_", "x_"),
                    (b, a) -> Canonical.commutes(b.get("c_")) && free(b, "c_")),
            template("c_ * diff(u__, x_)")));
    rules.add(
        Rule.of(
            "quotient-rule",
            Pattern.parse("diff(u__ * v_^(-1), x_)")
                .where(
                    List.of("v_", "x_"),
                    (b, a) -> Canonical.commutes(b.get("v_")) && !free(b, "v_")),
            template("(diff(u__, x_) * v_ - u__ * diff(v_, x_)) / v_^2")));
    rules.add(
        Rule.of(
            "product-rule",
            Pattern.parse("diff(u_ * v__, x_)"),
            template("diff(u_, x_) * v__ + u_ * diff(v__, x_)")));
    rules.add(
        Rule.of(
            "exp-rule",
            Pattern.parse("diff(e^v_, x_)").where("v_", Canonical::commutes),
            template("e^v_ * diff(v_, x_)")));
    rules.add(
        Rule.of(
            "power-rule",
            Pattern.parse("diff(u_^n_, x_)")
                .where(
                    List.of("u_", "n_", "x_"),
                    (b, a) -> Canonical.commutes(b.get("u_")) && free(b, "n_")),
            template("n_ * u_^(n_ - 1) * diff(u_, x_)")));
    rules.add(
        Rule.of(
            "exponential-rule",
            Pattern.parse("diff(a_^v_, x_)")
                .where(
                    List.of("a_", "v_", "x_"),
                    (b, a) ->
                        !b.get("a_").equals(Num.ZERO)
                            && Canonical.commutes(b.get("a_"))
                            && Canonical.commutes(b.get("v_"))
                            && free(b, "a_")),
            template("a_^v_ * log(a_) * diff(v_, x_)")));
    rules.add(
        Rule.of(
            "general-power-rule",
            Pattern.parse("diff(u_^v_, x_)")
                .where(
                    List.of("u_", "v_", "x_"),
                    (b, a) ->
                        Canonical.commutes(b.get("u_"))
                            && Canonical.commutes(b.get("v_"))
                            && !free(b, "u_")),
            template("u_^v_ * (diff(v_, x_) * log(u_) + v_ * diff(u_, x_) / u_)")));
    for (Map.Entry<String, Entry> f : TABLE.entrySet()) {
      Entry entry = f.getValue();
      rules.add(
          Rule.computed(
              f.getKey(),
              Pattern.of(Derivative.of(Apply.of(Symbol.of(f.getKey()), X), X, 1)),
              m -> {
                String formula = entry.formula(m.bindings().get("x_"), m.context().assumptions());
                return formula == null
                    ? Optional.empty()
                    : Optional.of(
                        m.bindings().substituteInto(template(formula.replace("u_", "x_"))));
              }));
    }
    rules.add(
        Rule.computed(
            "chain-rule",
            Pattern.of(Derivative.of(Apply.of(Symbol.of("h_"), U), X, 1))
                .where("h_", h -> h instanceof Symbol s && TABLE.containsKey(s.name()))
                .where(List.of("u_", "x_"), (b, a) -> !b.get("u_").equals(b.get("x_"))),
            DiffRules::chain));
    rules.add(
        Rule.of(
            "log-base-constant",
            Pattern.parse("diff(log(u_, b_), x_)")
                .where(List.of("b_", "x_"), (b, a) -> free(b, "b_")),
            template("diff(u_, x_) / (u_ * log(b_))")));
    rules.add(
        Rule.of(
            "log-base",
            Pattern.parse("diff(log(u_, b_), x_)"),
            template(
                "diff(u_, x_) / (u_ * log(b_)) - log(u_) * diff(b_, x_) / (b_ * (log(b_))^2)")));
    rules.add(
        Rule.of(
            "root",
            Pattern.parse("diff(root(u_, n_), x_)")
                .where(List.of("n_", "x_"), (b, a) -> free(b, "n_")),
            template("root(u_, n_)^(1 - n_) * diff(u_, x_) / n_")));
    rules.add(
        Rule.of(
            "atan2",
            Pattern.parse("diff(atan2(u_, v_), x_)"),
            template("(v_ * diff(u_, x_) - u_ * diff(v_, x_)) / (u_^2 + v_^2)")));
    rules.add(
        Rule.of(
            "big-sum",
            Pattern.parse("diff(sum(f_, k_, a_, b_), x_)")
                .where(
                    List.of("k_", "a_", "b_", "x_"),
                    (b, a) -> !b.get("k_").equals(b.get("x_")) && free(b, "a_") && free(b, "b_")),
            template("sum(diff(f_, x_), k_, a_, b_)")));
    return rules;
  }

  /** Parses a template in the empty environment. */
  private static Expr template(String source) {
    return Parser.parseExpression(source, Names.NONE);
  }

  /** Whether the value of {@code name} is free of the bound {@code x_}. */
  private static boolean free(Bindings b, String name) {
    return Free.of(b.get(name), (Symbol) b.get("x_"));
  }

  /**
   * Whether {@code c} is a scalar constant in {@code x}: free of it, commuting, of an arithmetic
   * kind (not a relation, truth value, collection, interval or lambda), and with no {@code
   * undefined} inside.
   */
  static boolean constant(Expr c, Expr x) {
    return switch (c) {
      case Relation _, Bool _, SetExpr _, ListExpr _, Vector _, Matrix _, Interval _, Lambda _ ->
          false;
      case Num _,
          Constant _,
          Symbol _,
          Sum _,
          Product _,
          Power _,
          Apply _,
          Piecewise _,
          Derivative _,
          Integral _,
          Limit _,
          BigSum _,
          BigProduct _ ->
          Canonical.commutes(c) && !hasUndefined(c) && Free.of(c, (Symbol) x);
    };
  }

  private static boolean hasUndefined(Expr e) {
    if (e.equals(Constant.UNDEFINED)) {
      return true;
    }
    for (Expr o : Operands.of(e)) {
      if (hasUndefined(o)) {
        return true;
      }
    }
    return false;
  }

  /** {@code f'(u)·D(u)} from the table, under the entry's condition. */
  private static Optional<Expr> chain(Match m) {
    Bindings b = m.bindings();
    String formula =
        TABLE.get(((Symbol) b.get("h_")).name()).formula(b.get("u_"), m.context().assumptions());
    if (formula == null) {
      return Optional.empty();
    }
    return Optional.of(b.substituteInto(Product.of(template(formula), Derivative.of(U, X, 1))));
  }

  /** {@code D(a + b + …)} → {@code D(a) + D(b) + …}, every term at once. */
  private static Optional<Expr> termwise(Match m) {
    List<Expr> terms = new ArrayList<>(m.bindings().sequence("rest__"));
    terms.add(m.bindings().get("u_"));
    return Optional.of(Sum.of(each(terms, m)));
  }

  /** The order-1 derivatives of {@code items} in the match's variable. */
  private static List<Expr> each(List<Expr> items, Match m) {
    Symbol x = (Symbol) m.bindings().get("x_");
    return items.stream().<Expr>map(i -> Derivative.of(i, x, 1)).toList();
  }

  private static Optional<Expr> matrix(Match m) {
    Matrix v = (Matrix) m.bindings().get("v_");
    return Optional.of(Matrix.of(v.rows().stream().map(row -> each(row, m)).toList()));
  }

  private static Optional<Expr> piecewise(Match m) {
    Piecewise p = (Piecewise) m.bindings().get("p_");
    Symbol x = (Symbol) m.bindings().get("x_");
    List<Piecewise.Piece> pieces =
        p.pieces().stream()
            .map(q -> new Piecewise.Piece(Derivative.of(q.value(), x, 1), q.condition()))
            .toList();
    return Optional.of(Piecewise.of(pieces, Derivative.of(p.otherwise(), x, 1)));
  }

  /**
   * {@code D_n(u)} → {@code D_{n−1}(D_1(u))}, unless the first derivative of {@code u} is stuck.
   */
  private static Optional<Expr> unroll(Match m) {
    Derivative d = (Derivative) m.bindings().get("d_");
    if (stuck(d.expr(), d.var(), m.context())) {
      return Optional.empty();
    }
    return Optional.of(Derivative.of(Derivative.of(d.expr(), d.var(), 1), d.var(), d.order() - 1));
  }

  /**
   * {@code D_1(D_k(u))} → {@code D_{k+1}(u)}, only when the first derivative of {@code u} is stuck.
   */
  private static Optional<Expr> merge(Match m) {
    Derivative inner = (Derivative) m.bindings().get("d_");
    if (!stuck(inner.expr(), inner.var(), m.context())) {
      return Optional.empty();
    }
    return Optional.of(Derivative.of(inner.expr(), inner.var(), inner.order() + 1));
  }

  /**
   * Whether no rule of the set applies to {@code diff(u, x)}: the rewriter's own test (a match
   * whose replacement exists and differs from the node), under the facts that hold where that
   * derivative is or would be rewritten, inside a derivative's body, without the facts about {@code
   * x}.
   */
  static boolean stuck(Expr u, Symbol x, Context context) {
    Context inner = context.withAssumptions(context.assumptions().without(x));
    Assumptions a = inner.assumptions();
    Expr node = Derivative.of(u, x, 1);
    for (Rule rule : RULES.rules()) {
      Optional<Bindings> match = Matcher.match(rule.pattern(), node, a);
      if (match.isEmpty()) {
        continue;
      }
      Optional<Expr> raw =
          switch (rule.replacement()) {
            case Replacement.Template t -> Optional.of(match.get().substituteInto(t.template()));
            case Replacement.Computed c -> c.function().apply(new Match(match.get(), node, inner));
            case Replacement.Valued v ->
                v.function()
                    .apply(new Match(match.get(), node, inner))
                    .map(Replacement.Valued.Result::expr);
          };
      if (raw.isPresent() && !Canonical.of(raw.get(), inner.precision()).equals(node)) {
        return false;
      }
    }
    return true;
  }
}
