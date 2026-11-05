package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import org.junit.jupiter.api.Test;

/** {@code solve} in the language: held arguments, bound unknowns, numeric values, errors, steps. */
class SolveEvaluationTest {

  @Test
  void theUnknownIsBoundAndTheRestEvaluated() {
    assertThat(shown("solve(x^2 - 5x + 6 = 0, x)")).isEqualTo("{2, 3}");
    assertThat(shown("x := 3; solve(x^2 = 4, x)")).isEqualTo("{-2, 2}");
    assertThat(shown("a := 4; solve(x^2 = a, x)")).isEqualTo("{-2, 2}");
    assertThat(shown("solve(i^2 = 4, i)")).isEqualTo("{-2, 2}");
    assertThat(shown("solve({x + y = 3, x - y = 1}, [x, y])")).isEqualTo("{[x = 2, y = 1]}");
    assertThat(shown("solve({x + y = 3, x - y = 1}, ⟨x, y⟩)")).isEqualTo("{[x = 2, y = 1]}");
  }

  @Test
  void theFactsAboutTheUnknownDoNotApply() {
    assertThat(shown("assume(x > 0); solve(x^2 = 4, x)")).isEqualTo("{-2, 2}");
  }

  @Test
  void aRenamedUnknownIsRenamedBackInTheSolutions() {
    assertThat(shown("g(t) := solve(x + t = 0, x); g(x)")).isEqualTo("{-x}");
    // captured through the frame, and its name free of the solutions: written back
    assertThat(shown("m(t) := solve({x + y = 2, x - y = 0}, [x, y]); m(x)"))
        .isEqualTo("{[x = 1, y = 1]}");
    assertThat(shown("k(t) := solve(x + y = t, [x, y]); k(z)")).isEqualTo("{[x = -y + z]}");
  }

  @Test
  void aRenamedUnknownKeepsItsFreshNameWhereItsNameIsFree() {
    assertThat(shown("k(t) := solve(x + y = t, [x, y]); k(x)")).isEqualTo("{[x₁ = x - y]}");
    assertThat(shown("b := y; solve({x + y = b, x - y = 1}, [x, y])"))
        .isEqualTo("{[x = y/2 + 1/2, y₁ = y/2 - 1/2]}");
    assertThat(shown("a := x; solve(x^2 = a + 1, x)")).isEqualTo("{√(x + 1), -√(x + 1)}");
    // free variables keep the fresh name too
    assertThat(shown("b := y; solve(x + y = b, [x, y])")).isEqualTo("{[x = y - y₁]}");
  }

  @Test
  void theNumericFallbackUsesTheSessionsValuesAndPrecision() {
    assertThat(shown("solve(x = cos(x), x)")).isEqualTo("{0.7390851332151606416553120876738734}");
    assertThat(shown("numeric(solve(x = cos(x), x), 10)")).isEqualTo("{0.7390851332}");
    assertThat(shown("solve(x^5 - x - 1 = 0, x)", Settings.DEFAULT.withPrecision(10)))
        .startsWith("{1.167303978, ");
    // in degrees an angle of the unknown is not solved numerically
    assertThat(shown("solve(x = cos(x), x)", Run.DEGREES)).isEqualTo("solve(x = cos(x), x)");
    assertThat(shown("solve(x^5 - x - 1 = 0, x)", Run.DEGREES)).startsWith("{1.167");
  }

  @Test
  void theArgumentsMustHaveTheirKinds() {
    assertThat(error("solve(x, x)")).isEqualTo("`solve` needs an equation or a set of equations");
    assertThat(error("solve(x = 1, 2)"))
        .isEqualTo("`solve` needs a variable or a list of distinct variables");
    assertThat(error("solve(x = 1, [x, x])"))
        .isEqualTo("`solve` needs a variable or a list of distinct variables");
    assertThat(shown("solve(x < 1, x)")).isEqualTo("solve(x < 1, x)");
    assertThat(error("solve(x^20000 = 1, x)")).isEqualTo("the polynomial's degree exceeds 10,000");
  }

  @Test
  void stepsShowTheSolverThenTheSimplifier() {
    Session s = new Session(Environment.EMPTY);
    Outcome o = s.run(Parser.parseScript("steps(solve(x^2 - 2 = 0, x))", s.names())).getLast();
    Outcome.Value v = (Outcome.Value) o;
    List<String> rules = v.trace().rewrites().stream().map(Rewrite::rule).toList();
    assertThat(rules).containsExactly("discriminant", "formula", "roots");
    TraceChecks.assertChain(new Rewriter.Result(v.value(), v.trace()));
    // nested in a larger statement, the solver's steps are not shown
    Outcome nested =
        s.run(Parser.parseScript("steps(nth([solve(x = 1, x)], 1))", s.names())).getLast();
    assertThat(((Outcome.Value) nested).trace().rewrites())
        .noneMatch(w -> w.ruleSet().equals("solve"));
  }
}
