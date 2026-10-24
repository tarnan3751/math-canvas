package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprArbitraries;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.statistics.Statistics;

/**
 * Evaluation is deterministic, and evaluating a value again gives the value, over every kind of
 * expression, with the generator's heads defined as functions ({@code f} of one parameter, {@code
 * g} of two). A sample whose evaluation is an error (an arity that does not match, a complex
 * ordering) is counted and skipped. Passing a value through the identity function {@code h} gives
 * the value, in every angle mode: a substituted value is not evaluated again.
 */
class EvaluationProperties {

  static final Environment ENV =
      Environment.EMPTY
          .with(
              "f",
              new Definition.Function(
                  List.of(Symbol.of("t")), Parser.parseExpression("t^2 + 1", Names.NONE)))
          .with(
              "g",
              new Definition.Function(
                  List.of(Symbol.of("s"), Symbol.of("t")),
                  Parser.parseExpression("s - 2t", Names.NONE)))
          .with("b", new Definition.Value(Parser.parseExpression("a + 1", Names.NONE)))
          .with(
              "h",
              new Definition.Function(
                  List.of(Symbol.of("t")), Parser.parseExpression("t", Names.NONE)));

  @Property(tries = 3000)
  void evaluationIsDeterministicAndIdempotent(@ForAll("exprs") Expr e) {
    Expr first;
    try {
      first = Evaluator.evaluate(e, ENV, false).value();
    } catch (EvaluationException error) {
      Statistics.label("evaluated").collect(false);
      return;
    }
    Statistics.label("evaluated").collect(true);
    assertThat(Evaluator.evaluate(e, ENV, false).value()).isEqualTo(first);
    assertThat(Evaluator.evaluate(first, ENV, false).value()).as("%s", e).isEqualTo(first);
  }

  @net.jqwik.api.Provide
  net.jqwik.api.Arbitrary<Expr> exprs() {
    return ExprArbitraries.exprs();
  }

  @Property(tries = 1500)
  void aValuePassedThroughAFunctionIsTheValue(@ForAll("exprs") Expr e, @ForAll AngleMode mode) {
    Environment env = ENV.withSettings(Settings.DEFAULT.withAngleMode(mode));
    Expr direct;
    try {
      direct = Evaluator.evaluate(e, env, false).value();
    } catch (EvaluationException error) {
      return;
    }
    Expr passed = Evaluator.evaluate(Apply.of(Symbol.of("h"), e), env, false).value();
    assertThat(passed).as("%s in %s", e, mode).isEqualTo(direct);
  }

  /**
   * Evaluation agrees with direct substitution of the raw bodies: every call of {@code f}, {@code
   * g}, {@code h} replaced by its body with the arguments put in, every {@code b} by its body, then
   * evaluated in the empty environment. The expressions have no binders, so no renaming is
   * involved; a call whose arity does not match is skipped.
   */
  @Property(tries = 5000)
  void evaluationAgreesWithSubstitutingTheBodies(@ForAll("exactExprs") Expr e) {
    Expr inlined;
    try {
      inlined = inline(e);
    } catch (IllegalArgumentException arity) {
      Statistics.label("compared").collect(false);
      return;
    }
    Statistics.label("compared").collect(true);
    Expr expected = Evaluator.evaluate(inlined, Environment.EMPTY, false).value();
    assertThat(Evaluator.evaluate(e, ENV, false).value()).as("%s", e).isEqualTo(expected);
  }

  @net.jqwik.api.Provide
  net.jqwik.api.Arbitrary<Expr> exactExprs() {
    return ExprArbitraries.exactExprs();
  }

  /** {@code e} with the definitions of {@link #ENV} put in, raw. */
  static Expr inline(Expr e) {
    if (e instanceof Symbol s
        && ENV.definition(s.name()).orElse(null) instanceof Definition.Value v) {
      return inline(v.body());
    }
    List<Expr> ops = new java.util.ArrayList<>();
    for (Expr op : mathcanvas.engine.match.Operands.of(e)) {
      ops.add(inline(op));
    }
    Expr rebuilt = ops.isEmpty() ? e : mathcanvas.engine.match.Operands.rebuild(e, ops);
    if (rebuilt instanceof Apply a
        && ENV.definition(a.head().name()).orElse(null) instanceof Definition.Function f) {
      if (f.params().size() != a.args().size()) {
        throw new IllegalArgumentException("arity");
      }
      java.util.TreeMap<String, Expr> map = new java.util.TreeMap<>();
      for (int k = 0; k < a.args().size(); k++) {
        map.put(f.params().get(k).name(), a.args().get(k));
      }
      return inline(Substitution.apply(f.body(), map));
    }
    return rebuilt;
  }
}
