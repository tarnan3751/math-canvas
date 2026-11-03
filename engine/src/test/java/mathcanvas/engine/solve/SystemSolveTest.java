package mathcanvas.engine.solve;

import static mathcanvas.engine.solve.SolveCases.run;
import static mathcanvas.engine.solve.SolveCases.shown;
import static mathcanvas.engine.solve.SolveCases.steps;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.rule.Rewrite;
import org.junit.jupiter.api.Test;

/** Linear systems by fraction-free elimination. */
class SystemSolveTest {

  @Test
  void aUniqueSolutionIsOneListOfEquations() {
    assertThat(shown("solve({x + y = 3, x - y = 1}, [x, y])")).isEqualTo("{[x = 2, y = 1]}");
    assertThat(steps("solve({x + y = 3, x - y = 1}, [x, y])"))
        .containsExactly("augment", "eliminate", "back-substitute", "roots");
    assertThat(shown("solve({2x + 3y = 4, y = 1}, [x, y])")).isEqualTo("{[x = 1/2, y = 1]}");
    // the unknowns' order is the one given
    assertThat(shown("solve({x + y = 3, x - y = 1}, [y, x])")).isEqualTo("{[y = 1, x = 2]}");
  }

  @Test
  void aSymbolicCoefficientIn3x3() {
    String call = "solve({x + y + z = 6, x - y + 2z = 5, 2x + a y - z = 1}, [x, y, z])";
    assertThat(shown(call))
        .isEqualTo("{[x = (7a - 14)/(a - 8), y = -14/(a - 8), z = (-a - 20)/(a - 8)]}");
    List<Rewrite> rs = run(call).trace().rewrites();
    assertThat(rs.stream().map(Rewrite::rule).toList())
        .containsExactly(
            "augment",
            "eliminate",
            "eliminate",
            "swap-rows",
            "eliminate",
            "back-substitute",
            "roots");
    assertThat(UnicodePrinter.INSTANCE.display(rs.get(5).bindings().get("conditions_")))
        .isEqualTo("{-a + 8 ≠ 0}");
    assertThat(UnicodePrinter.INSTANCE.display(rs.get(4).after()))
        .isEqualTo("[[1, 1, 1, 6], [0, -2, 1, -1], [0, 0, -a + 8, a + 20]]");
  }

  @Test
  void anInconsistentSystemHasNoSolution() {
    assertThat(shown("solve({x + y = 1, x + y = 2}, [x, y])")).isEqualTo("{}");
    Rewrite last = run("solve({x + y = 1, x + y = a}, [x, y])").trace().rewrites().getLast();
    assertThat(UnicodePrinter.INSTANCE.display(last.after())).isEqualTo("{}");
    assertThat(UnicodePrinter.INSTANCE.display(last.bindings().get("conditions_")))
        .isEqualTo("{a - 1 ≠ 0}");
  }

  @Test
  void aFreeUnknownStaysInTheSolution() {
    assertThat(shown("solve(x + y = 3, [x, y])")).isEqualTo("{[x = -y + 3]}");
    assertThat(steps("solve(x + y = 3, [x, y])"))
        .containsExactly("augment", "free-variables", "roots");
    assertThat(shown("solve({x + y + z = 1, x - y = 0}, [x, y, z])"))
        .isEqualTo("{[x = -z/2 + 1/2, y = -z/2 + 1/2]}");
    assertThat(shown("solve({x + y = 1, 2x + 2y = 2}, [x, y])")).isEqualTo("{[x = -y + 1]}");
  }

  @Test
  void oneUnknownWithSeveralEquationsGivesValues() {
    assertThat(shown("solve({x + 1 = 2, 2x = 2}, x)")).isEqualTo("{1}");
    assertThat(shown("solve({x + 1 = 2, 2x = 4}, x)")).isEqualTo("{}");
  }

  @Test
  void aPolynomialSystemIsSolvedByEliminationAndAnyOtherIsRefused() {
    assertThat(shown("solve({x y = 1, x + y = 2}, [x, y])")).isEqualTo("{[x = 1, y = 1]}");
    assertThat(steps("solve({x y = 1, x + y = 2}, [x, y])"))
        .containsExactly("eliminate-polynomial");
    assertThat(shown("solve({x^2 = 1, x = 1}, x)")).isEqualTo("{1}");
    assertThat(steps("solve({sin(x) = y, x + y = 1}, [x, y])"))
        .containsExactly("not-linear-system");
    assertThat(steps("solve({1/x = 1, y = 1}, [x, y])")).containsExactly("not-linear-system");
    assertThat(steps("solve({x^2 + y = 3, 2x^2 + 2y = 6}, [x, y])"))
        .containsExactly("not-polynomial-system");
  }
}
