package mathcanvas.engine.calculus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.simplify.Simplifier;

/**
 * The per-rule cases of {@code integrate}, written in the language: each positive case names a rule
 * that must be among the steps of {@link Calculus#integrate} on its input and the value it reaches,
 * simplified; each negative case names a rule that must not be. Every rule has at least one of
 * each.
 */
final class IntegrateCases {

  private final List<String> positive = new ArrayList<>();
  private final List<String> negative = new ArrayList<>();
  private final Context context;

  IntegrateCases(Context context) {
    this.context = context.withTrace(true);
  }

  static Expr parse(String source) {
    return Parser.parseExpression(source, Names.NONE);
  }

  /** {@code text} parsed and canonical. */
  static Expr parseCanonical(String text) {
    return Canonical.of(parse(text));
  }

  /** {@code integrate(…)} written in the language, integrated under {@code ctx}. */
  static Rewriter.Result integrate(String input, Context ctx) {
    Integral i = (Integral) Canonical.of(parse(input));
    return Calculus.integrate(i.integrand(), i.var(), i.lower(), i.upper(), ctx);
  }

  /**
   * The value {@code text} simplified and then given the final pass in {@code x}, as a result is:
   * an antiderivative's when it holds {@code x}, a definite value's otherwise.
   */
  static Expr simplified(String text, Context ctx) {
    Context quiet = ctx.withTrace(false);
    Expr s = Simplifier.simplify(parse(text), quiet).expr();
    mathcanvas.engine.expr.Symbol x = mathcanvas.engine.expr.Symbol.of("x");
    // a value free of x is taken as a definite one, which only distributes numbers
    return IntegrateResult.apply(s, x, Free.of(s, x), quiet).expr();
  }

  IntegrateCases fires(String rule, String input, String value) {
    Rewriter.Result r = integrate(input, context);
    assertThat(fired(r)).as("%s on %s", rule, input).contains(rule);
    assertThat(r.expr()).as("%s on %s", rule, input).isEqualTo(simplified(value, context));
    positive.add(rule);
    return this;
  }

  IntegrateCases skips(String rule, String input) {
    assertThat(fired(integrate(input, context))).as("%s on %s", rule, input).doesNotContain(rule);
    negative.add(rule);
    return this;
  }

  void covered() {
    TreeSet<String> names =
        RuleSets.INTEGRATE.rules().stream()
            .map(Rule::name)
            .collect(Collectors.toCollection(TreeSet::new));
    assertThat(new TreeSet<>(positive)).as("rules with a positive case").isEqualTo(names);
    assertThat(new TreeSet<>(negative)).as("rules with a negative case").isEqualTo(names);
  }

  /** The rules of the integration steps of a trace. */
  static List<String> fired(Rewriter.Result r) {
    return r.trace().rewrites().stream()
        .filter(w -> w.ruleSet().equals(Calculus.INTEGRATE))
        .map(Rewrite::rule)
        .toList();
  }
}
