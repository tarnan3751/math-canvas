package mathcanvas.engine.simplify;

import static mathcanvas.engine.simplify.RuleCases.canonical;
import static mathcanvas.engine.simplify.RuleCases.facts;
import static mathcanvas.engine.simplify.RuleCases.parse;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.eval.Outcome;
import mathcanvas.engine.eval.Session;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.rule.Context;
import org.junit.jupiter.api.Test;

/**
 * PLAN's acceptance pairs, where assumptions change results, through the standard list and through
 * a session.
 */
class AcceptancePairsTest {

  private static Expr simplify(String e, String... relations) {
    return Simplifier.simplify(parse(e), Context.DEFAULT.withAssumptions(facts(relations))).expr();
  }

  /** The last value of a script run in a new session, in Unicode display form. */
  private static String shown(String script) {
    Session s = new Session();
    List<Outcome> outcomes = s.run(Parser.parseScript(script, s.names()));
    Outcome last = outcomes.getLast();
    assertThat(last).as(script).isInstanceOf(Outcome.Value.class);
    return UnicodePrinter.INSTANCE.display(((Outcome.Value) last).value());
  }

  @Test
  void theSquareRootOfASquare() {
    assertThat(simplify("sqrt(x^2)")).isEqualTo(canonical("|x|"));
    assertThat(simplify("sqrt(x^2)", "x >= 0")).isEqualTo(canonical("x"));
    assertThat(simplify("sqrt(x^2)", "x ∈ ℂ")).isEqualTo(canonical("sqrt(x^2)"));
    assertThat(shown("sqrt(x^2)")).isEqualTo("|x|");
    assertThat(shown("assume(x >= 0); sqrt(x^2)")).isEqualTo("x");
  }
}
