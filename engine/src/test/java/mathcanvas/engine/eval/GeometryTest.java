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

/**
 * The geometry solvers in the language: the triangle solver's cases and steps, shape formulas,
 * coordinate geometry and conics, and their errors.
 */
class GeometryTest {

  @Test
  void everyCaseOfTheTriangleSolver() {
    // SSS
    assertThat(shown("triangle(a = 3, b = 4, c = 5)"))
        .isEqualTo("[a = 3, b = 4, c = 5, A = acos(4/5), B = acos(3/5), C = π/2]");
    assertThat(shown("triangle(a = 1, b = 1, c = 1)"))
        .isEqualTo("[a = 1, b = 1, c = 1, A = π/3, B = π/3, C = π/3]");
    // SAS
    assertThat(shown("triangle(a = 3, b = 4, C = 90°)"))
        .isEqualTo("[a = 3, b = 4, c = 5, A = asin(3/5), B = π/2 - asin(3/5), C = π/2]");
    // ASA, AAS
    assertThat(shown("triangle(A = 30°, B = 60°, c = 2)"))
        .isEqualTo("[a = 1, b = √3, c = 2, A = π/6, B = π/3, C = π/2]");
    assertThat(shown("triangle(A = 30°, C = 90°, a = 1)"))
        .isEqualTo("[a = 1, b = √3, c = 2, A = π/6, B = π/3, C = π/2]");
  }

  @Test
  void theAmbiguousCaseIsASet() {
    assertThat(shown("triangle(a = 1, b = 2, A = 30°)"))
        .isEqualTo("{[a = 1, b = 2, c = √3, A = π/6, B = π/2, C = π/3]}");
    assertThat(shown("triangle(a = 1, b = 4, A = 30°)")).isEqualTo("{}");
    assertThat(Run.value("triangle(a = 3, b = 4, A = 30°)").toString()).startsWith("SetExpr");
    assertThat(shown("triangle(a = 3, b = 4, A = 30°)")).contains("B = π - asin(2/3)");
    assertThat(shown("triangle(a = 5, b = 4, A = 30°)")).contains("B = asin(2/5)");
    assertThat(shown("triangle(a = 4, b = 5, A = 120°)")).isEqualTo("{}");
  }

  @Test
  void dataThatMakeNoTriangleGiveTheEmptySet() {
    assertThat(shown("triangle(a = 1, b = 2, c = 3)")).isEqualTo("{}");
    assertThat(shown("triangle(A = 100°, B = 90°, c = 2)")).isEqualTo("{}");
  }

  @Test
  void anglesAreInTheSessionsUnit() {
    assertThat(shown("triangle(a = 3, b = 4, C = 90)", Run.DEGREES))
        .isEqualTo("[a = 3, b = 4, c = 5, A = 180 asin(3/5)/π, B = -180 asin(3/5)/π + 90, C = 90]");
    assertThat(shown("triangle(A = 30, B = 60, c = 2)", Run.DEGREES))
        .isEqualTo("[a = 1, b = √3, c = 2, A = 30, B = 60, C = 90]");
    assertThat(shown("triangle(A = π/6, B = 60, c = 2)", Run.DEGREES))
        .isEqualTo("[a = 1, b = √3, c = 2, A = 30, B = 60, C = 90]");
  }

  @Test
  void decimalDataGiveDecimals() {
    assertThat(shown("triangle(a = 3, b = 4, C = 90.0)", Run.DEGREES))
        .startsWith("[a = 3.0, b = 4.0, c = 5.0, A = 36.869897645");
  }

  @Test
  void theStepsNameTheLaws() {
    assertThat(steps("triangle(a = 3, b = 4, C = 90°)"))
        .containsExactly("law-of-cosines", "law-of-sines", "angle-sum");
    assertThat(steps("triangle(a = 3, b = 4, c = 5)")).containsExactly("law-of-cosines");
    assertThat(steps("triangle(A = 30°, B = 60°, c = 2)"))
        .containsExactly("angle-sum", "law-of-sines");
    assertThat(steps("triangle(a = 3, b = 4, A = 30°)"))
        .containsExactly("ambiguous", "angle-sum", "law-of-sines");
    assertThat(steps("triangle(a = 1, b = 4, A = 30°)")).containsExactly("no-triangle");
    assertThat(steps("triangle(a = 1, b = 2, c = 3)")).containsExactly("no-triangle");
  }

  @Test
  void aSymbolLeavesTheCallAndBadPartsAreErrors() {
    assertThat(shown("triangle(a = 3, b = x, C = 90°)"))
        .isEqualTo("triangle(a = 3, b = x, C = π/2)");
    assertThat(error("triangle(A = 30°, B = 60°, C = 90°)")).isEqualTo("`triangle` needs a side");
    assertThat(error("triangle(a = -1, b = 2, c = 2)"))
        .isEqualTo("`triangle`'s sides must be positive");
    assertThat(error("triangle(a = 1, b = 2, C = 200°)"))
        .isEqualTo("`triangle`'s angles must be between 0 and a half turn");
    assertThat(error("triangle(a = 1, a = 2, C = 20°)"))
        .isEqualTo("`triangle` takes each part once");
    assertThat(error("triangle(d = 1, a = 2, C = 20°)"))
        .startsWith("`triangle` takes three parts such as a = 3 or C = 90°, from a b c A B C");
  }

  @Test
  void shapeFormulas() {
    assertThat(shown("trianglearea(3, 4, 5)")).isEqualTo("6");
    assertThat(shown("trianglearea(1, 1, 3)")).isEqualTo("undefined");
    assertThat(shown("trianglearea(1, 2, 3)")).isEqualTo("0");
    assertThat(shown("trianglearea(a, b, c)"))
        .isEqualTo("√((a + b + c)(-a + b + c)(a - b + c)(a + b - c))/4");
    assertThat(shown("circumradius(3, 4, 5)")).isEqualTo("5/2");
    assertThat(shown("circumradius(1, 2, 3)")).isEqualTo("undefined");
    assertThat(shown("inradius(3, 4, 5)")).isEqualTo("1");
    assertThat(shown("polygonarea([[0, 0], [4, 0], [4, 3]])")).isEqualTo("6");
    assertThat(shown("polygonarea([[0, 0], [0, 3], [4, 0]])")).isEqualTo("-6");
    assertThat(error("polygonarea([[0, 0], [1, 1]])"))
        .isEqualTo("`polygonarea` needs at least three points [x, y]");
  }

  @Test
  void coordinateGeometry() {
    assertThat(shown("distance([1, 2], [4, 6])")).isEqualTo("5");
    assertThat(shown("distance([0, 0, 0], [1, 2, 2])")).isEqualTo("3");
    assertThat(shown("midpoint([1, 2], [4, 6])")).isEqualTo("⟨5/2, 4⟩");
    assertThat(shown("slope([1, 2], [4, 6])")).isEqualTo("4/3");
    assertThat(shown("slope([1, 2], [1, 6])")).isEqualTo("undefined");
    assertThat(shown("lineequation([1, 2], [3, 6])")).isEqualTo("y = 2x");
    assertThat(shown("lineequation([1, 2], [1, 6])")).isEqualTo("x = 1");
    assertThat(shown("circleequation([1, -2], 3)")).isEqualTo("(x - 1)² + (y + 2)² = 9");
    assertThat(error("lineequation([1, 2], [1, 2])"))
        .isEqualTo("`lineequation` needs two distinct points");
    assertThat(error("distance([1, 2], [1, 2, 3])"))
        .isEqualTo("`distance` needs two points of one dimension");
    assertThat(error("slope(1, [1, 2])")).isEqualTo("slope needs points [x, y]");
  }

  @Test
  void conicsFromTheirDefinitions() {
    assertThat(shown("parabola([0, 1], y = -1)")).isEqualTo("x² - 4y = 0");
    // a decimal eccentricity of 1: the x² term is gone, not 0.0x²
    assertThat(shown("conic([0, 0], x = 4, 1.0)")).isEqualTo("y² + 8.0x - 16.0 = 0");
    assertThat(shown("ellipse([-3, 0], [3, 0], 10)")).isEqualTo("16x² + 25y² - 400 = 0");
    assertThat(shown("hyperbola([-5, 0], [5, 0], 6)")).isEqualTo("16x² - 9y² - 144 = 0");
    assertThat(shown("conic([0, 0], x = 4, 1/2)")).isEqualTo("3x² + 4y² + 8x - 16 = 0");
    assertThat(shown("parabola([p, 0], x = -p)")).isEqualTo("4p x - y² = 0");
    assertThat(error("ellipse([-3, 0], [3, 0], 6)"))
        .isEqualTo("`ellipse` needs 2a greater than the distance between the foci");
    assertThat(error("hyperbola([-3, 0], [3, 0], 6)"))
        .isEqualTo("`hyperbola` needs 2a between 0 and the distance between the foci");
    assertThat(error("conic([0, 0], x = 4, -1)"))
        .isEqualTo("`conic` needs a positive eccentricity");
    assertThat(error("parabola([0, 0], x^2 = 4)"))
        .isEqualTo("`parabola` needs a directrix, a line such as y = -1");
    assertThat(error("parabola([x, 0], y = 1)"))
        .isEqualTo("`parabola`'s arguments must not hold x or y");
  }

  private static List<String> steps(String call) {
    Session s = new Session(Environment.EMPTY);
    Outcome o = s.run(Parser.parseScript("steps(" + call + ")", s.names())).getLast();
    Outcome.Value v = (Outcome.Value) o;
    TraceChecks.assertChain(new Rewriter.Result(v.value(), v.trace()));
    return v.trace().rewrites().stream()
        .filter(r -> r.ruleSet().equals("triangle"))
        .map(Rewrite::rule)
        .toList();
  }
}
