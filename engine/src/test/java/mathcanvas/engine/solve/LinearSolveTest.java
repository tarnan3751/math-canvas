package mathcanvas.engine.solve;

import static mathcanvas.engine.solve.SolveCases.run;
import static mathcanvas.engine.solve.SolveCases.shown;
import static mathcanvas.engine.solve.SolveCases.steps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import org.junit.jupiter.api.Test;

/** One equation of degree 1, the preprocessing, and the arguments. */
class LinearSolveTest {

  @Test
  void solvesWithTheStudentsSteps() {
    assertThat(shown("solve(2x + 3 = 7, x)")).isEqualTo("{2}");
    assertThat(steps("solve(2x + 3 = 7, x)")).containsExactly("isolate", "divide", "roots");
    assertThat(shown("solve(2x + 3 = x + 7, x)")).isEqualTo("{4}");
    assertThat(steps("solve(2x + 3 = x + 7, x)")).containsExactly("collect", "isolate", "roots");
    assertThat(shown("solve(7 = 2x + 3, x)")).isEqualTo("{2}");
    assertThat(steps("solve(7 = 2x + 3, x)"))
        .containsExactly("collect", "isolate", "divide", "roots");
    assertThat(shown("solve(x/2 + 1/3 = 1, x)")).isEqualTo("{4/3}");
    assertThat(shown("solve(x = 5, x)")).isEqualTo("{5}");
    assertThat(steps("solve(x = 5, x)")).containsExactly("roots");
  }

  @Test
  void theStatesAreTheEquationsAStudentWrites() {
    List<Rewrite> rs = run("solve(2x + 3 = x + 7, x)").trace().rewrites();
    assertThat(rs.stream().map(r -> UnicodePrinter.INSTANCE.display(r.after())).toList())
        .containsExactly("x + 3 = 7", "x = 4", "{4}");
    assertThat(UnicodePrinter.INSTANCE.display(rs.getFirst().before())).isEqualTo("2x + 3 = x + 7");
  }

  @Test
  void aSymbolicCoefficientIsAssumedNonZero() {
    assertThat(shown("solve(a x + b = 0, x)")).isEqualTo("{-b/a}");
    Rewrite divide = run("solve(a x + b = 0, x)").trace().rewrites().get(1);
    assertThat(divide.rule()).isEqualTo("divide");
    assertThat(UnicodePrinter.INSTANCE.display(divide.bindings().get("conditions_")))
        .isEqualTo("{a ≠ 0}");
    assertThat(shown("solve(a x + b x = c, x)")).isEqualTo("{c/(a + b)}");
    // a known non-zero coefficient needs no condition
    Context positive = Context.DEFAULT.withAssumptions(assume("a > 0"));
    Rewrite known = run("solve(a x + b = 0, x)", positive).trace().rewrites().get(1);
    assertThat(UnicodePrinter.INSTANCE.display(known.bindings().get("conditions_")))
        .isEqualTo("{}");
  }

  @Test
  void aCoefficientKnownToBeZeroDegenerates() {
    Context zero = Context.DEFAULT.withAssumptions(assume("a >= 0", "a <= 0"));
    assertThat(UnicodePrinter.INSTANCE.display(run("solve(a x + 1 = 0, x)", zero).expr()))
        .isEqualTo("{}");
    assertThat(UnicodePrinter.INSTANCE.display(run("solve(a x = 0, x)", zero).expr()))
        .isEqualTo("solve(a x = 0, x)");
  }

  @Test
  void anEquationFreeOfTheUnknown() {
    assertThat(shown("solve(x = x + 1, x)")).isEqualTo("{}");
    assertThat(shown("solve(2 = 3, x)")).isEqualTo("{}");
    assertThat(shown("solve(x = x, x)")).isEqualTo("solve(x = x, x)");
    assertThat(steps("solve(x = x, x)")).containsExactly("all-values");
    assertThat(shown("solve(y = 0, x)")).isEqualTo("solve(y = 0, x)");
    assertThat(steps("solve(y = 0, x)")).containsExactly("depends-on-parameters");
    assertThat(shown("solve(y^2 + 1 = 0, x)")).isEqualTo("{}");
  }

  @Test
  void denominatorsAreClearedAndTheirZerosExcluded() {
    assertThat(shown("solve(1/x = 2, x)")).isEqualTo("{1/2}");
    assertThat(steps("solve(1/x = 2, x)").getFirst()).isEqualTo("clear-denominators");
    assertThat(shown("solve(1/x = 0, x)")).isEqualTo("{}");
    assertThat(shown("solve((x^2 - 1)/(x - 1) = 0, x)")).isEqualTo("{-1}");
    assertThat(steps("solve((x^2 - 1)/(x - 1) = 0, x)"))
        .containsExactly("clear-denominators", "exclude", "isolate", "roots");
    assertThat(shown("solve(x/(x - 1) = 1/(x - 1), x)")).isEqualTo("{}");
    assertThat(shown("solve(1/(x - 1) + 1/(x + 1) = 0, x)")).isEqualTo("{0}");
    assertThat(shown("solve((x^2 - 4)/((x - 2)(x + 3)) = 0, x)")).isEqualTo("{-2}");
  }

  @Test
  void inequalitiesAndOtherKindsAreRefused() {
    assertThat(shown("solve(x < 2, x)")).isEqualTo("solve(x < 2, x)");
    assertThat(steps("solve(x < 2, x)")).containsExactly("not-an-equation");
    Context ctx = Context.DEFAULT;
    Expr x = Symbol.of("x");
    assertThatThrownBy(() -> Solver.solve(x, x, ctx))
        .hasMessage("solve needs an equation or a set of equations");
    Expr eq = Canonical.of(Parser.parseExpression("x = 1", Names.NONE));
    assertThatThrownBy(() -> Solver.solve(eq, Parser.parseExpression("[x, x]", Names.NONE), ctx))
        .hasMessage("solve needs a variable or a list of distinct variables");
    assertThatThrownBy(() -> Solver.solve(eq, Parser.parseExpression("2", Names.NONE), ctx))
        .hasMessage("solve needs a variable or a list of distinct variables");
  }

  @Test
  void untracedRunsGiveTheSameResult() {
    for (String call : List.of("solve(2x + 3 = 7, x)", "solve(1/x = 2, x)", "solve(x^2 = 4, x)")) {
      mathcanvas.engine.expr.Apply a =
          (mathcanvas.engine.expr.Apply) Parser.parseExpression(call, Names.NONE);
      assertThat(
              Solver.solve(Canonical.of(a.args().get(0)), a.args().get(1), Context.DEFAULT).expr())
          .isEqualTo(run(call).expr());
    }
  }

  static Assumptions assume(String... relations) {
    Assumptions a = Assumptions.EMPTY;
    for (String r : relations) {
      a = a.with((Relation) Canonical.of(Parser.parseExpression(r, Names.NONE)));
    }
    return a;
  }
}
