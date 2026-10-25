package mathcanvas.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import mathcanvas.engine.algebra.ExpandRules;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprArbitraries;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.simplify.Simplifier;
import mathcanvas.engine.testing.RandomAssumptions;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Grouping the rules by the root they can match changes nothing: the rewrite and its trace are
 * those of trying every rule at every node, for the standard list and the expand rules, and for
 * random subsets of them together with edge rules whose roots exercise every branch of the index's
 * {@code canMatch}, which the real rule sets leave unused.
 */
class RewriterIndexProperties {

  /** Every rule set the engine has: the standard list, then expand. */
  static RuleSet allRules() {
    List<Rule> rules = new ArrayList<>();
    for (RuleSet set : Simplifier.standard()) {
      rules.addAll(set.rules());
    }
    rules.addAll(ExpandRules.RULES.rules());
    return new RuleSet("all", rules);
  }

  private static final RuleSet ALL = allRules();

  private static Expr t(String s) {
    return Parser.parseExpression(s, Names.NONE);
  }

  /**
   * One rule per root kind and {@code canMatch} branch, each able to fire on the generated
   * expressions and leading to a fresh symbol, so none loops: a variable root; a literal root that
   * is an application, a number and a constant; an application with a variable head; a power with a
   * literal exponent and one whose exponent has a default, so it matches a non-power; a product and
   * a sum with a default operand, so they match a non-product and a non-sum; and the structural
   * roots (a relation, a vector, a set, a matrix).
   */
  static final List<Rule> EDGE =
      List.of(
          Rule.of(
              "var-root", Pattern.parse("x_").where("x_", x -> x.equals(Symbol.of("x"))), t("zz0")),
          Rule.of("lit-apply", Pattern.parse("log(1)"), t("zza")),
          Rule.of("lit-num", Pattern.parse("3"), t("zzb")),
          Rule.of("lit-const", Pattern.parse("pi"), t("zzc")),
          Rule.of(
              "head-var",
              Pattern.of(Apply.of(Symbol.of("h_"), Symbol.of("x_")))
                  .where("h_", h -> h.equals(Symbol.of("abs")) || h.equals(Symbol.of("sin"))),
              t("zzd(x_) + 1")),
          Rule.of("power-lit", Pattern.parse("x_^2"), t("zzl(x_)")),
          Rule.of(
              "opt-exp",
              Pattern.parse("x_^n_")
                  .optional("n_", Num.ONE)
                  .where("x_", x -> x.equals(Symbol.of("a"))),
              t("zze^n_")),
          Rule.of(
              "opt-coef",
              Pattern.parse("c_ * x_")
                  .optional("c_", Num.ONE)
                  .where("x_", x -> x.equals(Symbol.of("b"))),
              t("zzf * c_")),
          Rule.of(
              "opt-term",
              Pattern.parse("x_ + y_")
                  .optional("y_", Num.ZERO)
                  .where("x_", x -> x.equals(Symbol.of("y"))),
              t("zzg + y_")),
          Rule.of("struct-rel", Pattern.parse("x_ = y_"), t("zzh")),
          Rule.of("struct-vec", Pattern.parse("[x_, y_]"), t("zzi")),
          Rule.of("struct-set", Pattern.parse("{x_}"), t("zzj")),
          Rule.of("struct-mat", Pattern.parse("[[x_, y_], [z_, w_]]"), t("zzk")));

  private static final List<Rule> WITH_EDGES = new ArrayList<>(ALL.rules());

  static {
    WITH_EDGES.addAll(EDGE);
  }

  @Provide
  Arbitrary<Expr> exprs() {
    return Arbitraries.oneOf(ExprArbitraries.elementaryExprs(), ExprArbitraries.exprs());
  }

  @Provide
  Arbitrary<Expr> moreExprs() {
    return Arbitraries.oneOf(
        ExprArbitraries.elementaryExprs(), ExprArbitraries.exprs(), ExprArbitraries.exactExprs());
  }

  @Provide
  Arbitrary<Assumptions> assumptions() {
    return RandomAssumptions.about(ExprArbitraries.SYMBOLS);
  }

  @Property(tries = 2000)
  void theIndexChangesNeitherTheResultNorTheTrace(
      @ForAll("exprs") Expr e, @ForAll("assumptions") Assumptions a) {
    Context ctx = Context.DEFAULT.withAssumptions(a).withTrace(true).withRewriteCap(2_000);
    Object indexed = outcome(() -> Rewriter.rewrite(e, ALL, ctx));
    Object everyRule = outcome(() -> Rewriter.rewriteTryingEveryRule(e, ALL, ctx));
    assertThat(indexed).as("%s", e).isEqualTo(everyRule);
  }

  /** Random subsets of every rule and the edge rules, in random order, agree too. */
  @Property(tries = 3000)
  void theIndexAgreesWithEdgeRules(
      @ForAll("moreExprs") Expr e, @ForAll("assumptions") Assumptions a, @ForAll Random random) {
    List<Rule> subset = new ArrayList<>();
    for (Rule r : WITH_EDGES) {
      if (random.nextInt(3) != 0) {
        subset.add(r);
      }
    }
    if (random.nextBoolean()) {
      Collections.shuffle(subset, random);
    }
    RuleSet set = new RuleSet("subset", subset);
    Context ctx = Context.DEFAULT.withAssumptions(a).withTrace(true).withRewriteCap(2_000);
    Object indexed = outcome(() -> Rewriter.rewrite(e, set, ctx));
    Object everyRule = outcome(() -> Rewriter.rewriteTryingEveryRule(e, set, ctx));
    assertThat(indexed).as("%s", e).isEqualTo(everyRule);
  }

  /** The result, or the failure's class and message. */
  private static Object outcome(Supplier<Rewriter.Result> run) {
    try {
      return run.get();
    } catch (RuntimeException failure) {
      return failure.getClass().getName() + ": " + failure.getMessage();
    }
  }
}
