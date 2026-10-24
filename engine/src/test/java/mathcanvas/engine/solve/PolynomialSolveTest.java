package mathcanvas.engine.solve;

import static mathcanvas.engine.solve.SolveCases.run;
import static mathcanvas.engine.solve.SolveCases.shown;
import static mathcanvas.engine.solve.SolveCases.steps;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.rule.Rewrite;
import org.junit.jupiter.api.Test;

/** Polynomial equations: factoring, then the quadratic, cubic and quartic formulas. */
class PolynomialSolveTest {

  private static final Random RANDOM = new Random(1);

  /** Solves {@code p = 0} and checks the solutions and their count. */
  private static SetExpr check(String p, int count) {
    SetExpr s = (SetExpr) run("solve(" + p + " = 0, x)").expr();
    assertThat(s.items()).as(p).hasSize(count);
    SolveCases.assertSolutions(
        Polynomial.of(Canonical.of(Parser.parseExpression(p, Names.NONE))), s, RANDOM);
    return s;
  }

  @Test
  void factorsFirst() {
    assertThat(shown("solve(x^2 - 5x + 6 = 0, x)")).isEqualTo("{2, 3}");
    assertThat(steps("solve(x^2 - 5x + 6 = 0, x)")).containsExactly("factor", "roots");
    Rewrite factor = run("solve(x^2 - 5x + 6 = 0, x)").trace().rewrites().getFirst();
    assertThat(UnicodePrinter.INSTANCE.display(factor.after())).isEqualTo("(x - 3)(x - 2) = 0");
    assertThat(shown("solve(x^2 = 4, x)")).isEqualTo("{-2, 2}");
    assertThat(shown("solve(x^3 = x, x)")).isEqualTo("{-1, 0, 1}");
    assertThat(shown("solve((x - 1)^2 (x + 2) = 0, x)")).isEqualTo("{-2, 1}");
    assertThat(shown("solve(x^2 - (a + b) x + a b = 0, x)")).isEqualTo("{a, b}");
    check("(x - 1)^2 (x^2 - 2)", 3);
    check("x^4 - 1", 4);
  }

  @Test
  void theQuadraticFormulaGivesEveryComplexRoot() {
    assertThat(steps("solve(x^2 + x - 1 = 0, x)"))
        .containsExactly("discriminant", "formula", "roots");
    var rs = run("solve(x^2 + x - 1 = 0, x)").trace().rewrites();
    assertThat(UnicodePrinter.INSTANCE.display(rs.get(0).after())).isEqualTo("(x + 1/2)² = 5/4");
    assertThat(UnicodePrinter.INSTANCE.display(rs.get(0).bindings().get("d_"))).isEqualTo("5");
    assertThat(shown("solve(x^2 + 1 = 0, x)")).isEqualTo("{-i, i}");
    check("x^2 - 2", 2);
    check("x^2 + x + 1", 2);
    check("x^2 + x - 1", 2);
    check("3x^2 - 7", 2);
    check("x^2 + 2x + 2", 2);
    check("x^2 - 2x - 1", 2);
  }

  @Test
  void aQuadraticWithSymbolicCoefficients() {
    var r = run("solve(a x^2 + b x + c = 0, x)");
    assertThat(((SetExpr) r.expr()).items()).hasSize(2);
    assertThat(
            UnicodePrinter.INSTANCE.display(
                r.trace().rewrites().getFirst().bindings().get("conditions_")))
        .isEqualTo("{a ≠ 0}");
    check("a x^2 + b x + c", 2);
    check("x^2 - a", 2);
  }

  @Test
  void theCubicFormulaInItsThreeForms() {
    SetExpr trig = check("x^3 - 3x + 1", 3);
    assertThat(UnicodePrinter.INSTANCE.display(trig)).contains("cos");
    assertThat(
            UnicodePrinter.INSTANCE.display(
                run("solve(x^3 - 3x + 1 = 0, x)")
                    .trace()
                    .rewrites()
                    .getFirst()
                    .bindings()
                    .get("form_")))
        .isEqualTo("trigonometric");
    check("x^3 - 2", 3);
    check("x^3 + 3x + 1", 3);
    check("x^3 - 3x - 3", 3);
    check("2x^3 + 3x^2 - 1x + 5", 3);
    check("x^3 - 6x^2 + 9x - 1", 3);
    check("x^3 + x^2 + x + 2", 3);
  }

  @Test
  void theQuarticFormula() {
    assertThat(shown("solve(x^4 - 10x^2 + 1 = 0, x)"))
        .isEqualTo("{√2 + √3, -(√2 + √3), -√2 + √3, -(-√2 + √3)}");
    check("x^4 - 10x^2 + 1", 4);
    check("x^4 + x + 1", 4);
    check("x^4 - 2", 4);
    check("x^4 + 1", 4);
    check("x^4 - 4x^3 + 2x + 7", 4);
    check("2x^4 + x^3 - 3x + 1", 4);
    check("x^4 + 4x^2 + x + 1", 4);
  }

  @Test
  void symbolicHigherDegreesAreRefused() {
    assertThat(shown("solve(x^3 + a x + 1 = 0, x)")).isEqualTo("solve(x³ + a x + 1 = 0, x)");
    assertThat(steps("solve(x^3 + a x + 1 = 0, x)")).containsExactly("symbolic-degree");
  }
}
