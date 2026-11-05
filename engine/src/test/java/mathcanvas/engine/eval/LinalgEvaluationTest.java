package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Step;
import org.junit.jupiter.api.Test;

/** The linear-algebra builtins through the evaluator ({@code docs/language.md} §14.16). */
class LinalgEvaluationTest {

  /** The narrated steps of a statement, whose trace must chain. */
  private static List<String> texts(String statement) {
    Session s = new Session(Environment.EMPTY);
    Outcome.Value v = (Outcome.Value) s.run(Parser.parseScript(statement, s.names())).getLast();
    TraceChecks.assertChain(new Rewriter.Result(v.value(), v.trace()));
    List<Step> steps = Narration.steps(new Evaluator.Result(v.value(), v.trace()), Context.DEFAULT);
    return steps.stream().map(Step::text).toList();
  }

  @Test
  void theValues() {
    assertThat(shown("det([[1, 2], [3, 4]])")).isEqualTo("-2");
    assertThat(shown("det([[a, b], [c, d]])")).isEqualTo("-b c + a d");
    assertThat(shown("transpose([[1, 2, 3], [4, 5, 6]])")).isEqualTo("[[1, 4], [2, 5], [3, 6]]");
    assertThat(shown("transpose([1, 2])")).isEqualTo("[[1, 2]]");
    assertThat(shown("trace([[1, 2], [3, 4]])")).isEqualTo("5");
    assertThat(shown("size([[1, 2, 3], [4, 5, 6]])")).isEqualTo("⟨2, 3⟩");
    assertThat(shown("size([1, 2])")).isEqualTo("⟨2⟩");
    assertThat(shown("identity(2)")).isEqualTo("[[1, 0], [0, 1]]");
    assertThat(shown("zeros(1, 2)")).isEqualTo("[[0, 0]]");
    assertThat(shown("norm([x, y])")).isEqualTo("√(x² + y²)");
    assertThat(shown("norm([1, 1])")).isEqualTo("√2");
    assertThat(shown("normalize([1, 1])")).isEqualTo("⟨√2/2, √2/2⟩");
    assertThat(shown("rank([[1, 2], [2, 4]])")).isEqualTo("1");
  }

  @Test
  void powersOfTheInverse() {
    assertThat(shown("[[1, 2], [3, 4]]^-1")).isEqualTo("[[-2, 1], [3/2, -1/2]]");
    assertThat(shown("[[1, 2], [3, 4]]^-2")).isEqualTo("[[11/2, -5/2], [-15/4, 7/4]]");
    assertThat(shown("M := [[2, 1], [1, 1]]\nM^-1 * M")).isEqualTo("[[1, 0], [0, 1]]");
    assertThat(error("[[1, 2], [2, 4]]^-1")).isEqualTo("the matrix is singular");
    // the assumptions decide a symbolic pivot
    assertThat(error("assume(a >= 0, a <= 0)\n[[a, 0], [0, 1]]^-1"))
        .isEqualTo("the matrix is singular");
  }

  @Test
  void anArgumentThatMayBeAMatrixLeavesTheCall() {
    assertThat(shown("det(M)")).isEqualTo("det(M)");
    assertThat(shown("rank(x)")).isEqualTo("rank(x)");
    assertThat(shown("linsolve(A, [1, 2])")).isEqualTo("linsolve(A, ⟨1, 2⟩)");
    assertThat(shown("identity(n)")).isEqualTo("identity(n)");
    assertThat(shown("M := [[1, 2], [3, 4]]\ndet(M)")).isEqualTo("-2");
  }

  @Test
  void theErrors() {
    assertThat(error("det(5)")).isEqualTo("`det` needs a square matrix");
    assertThat(error("det([1, 2])")).isEqualTo("`det` needs a square matrix");
    assertThat(error("det([[1, 2, 3]])")).isEqualTo("`det` needs a square matrix");
    assertThat(error("trace([1, 2])")).isEqualTo("`trace` needs a square matrix");
    assertThat(error("inverse([[1, 2, 3]])")).isEqualTo("`inverse` needs a square matrix");
    assertThat(error("rref(3)")).isEqualTo("`rref` needs a matrix");
    assertThat(error("det([[1, 2], [3, 4]], foo)"))
        .isEqualTo("`det`'s method must be cofactor or elimination");
    assertThat(error("inverse([[1, 2], [2, 4]])")).isEqualTo("the matrix is singular");
    assertThat(error("linsolve([[1, 2], [3, 4]], [1])"))
        .isEqualTo("`linsolve` needs a matrix and a vector of matching height");
    assertThat(error("gramschmidt([[1, 2], [3]])"))
        .isEqualTo("`gramschmidt` needs vectors of the same length");
    assertThat(error("identity(0)")).isEqualTo("`identity` needs a positive integer");
    assertThat(error("zeros(2, 1/2)")).isEqualTo("`zeros` needs positive integers");
    assertThat(error("norm([[1, 2]])")).isEqualTo("`norm` needs a vector");
  }

  @Test
  void theMethodWordIsHeld() {
    assertThat(shown("cofactor := 3\ndet([[1, 2], [3, 4]], cofactor)")).isEqualTo("-2");
  }

  @Test
  void theNarration() {
    assertThat(texts("steps(rref([[1, 2, 3], [4, 5, 6]]))"))
        .startsWith(
            "R₂ ← R₂ − 4R₁: [[1, 2, 3], [0, -3, -6]]",
            "Divide R₂ by -3 to make the pivot 1: [[1, 2, 3], [0, 1, 2]]",
            "R₁ ← R₁ − 2R₂: [[1, 0, -1], [0, 1, 2]]");
    assertThat(texts("steps(rref([[1, 2], [-1, 0]]))").getFirst())
        .isEqualTo("R₂ ← R₂ + R₁: [[1, 2], [0, 2]]");
    // a symbolic multiple with a negative coefficient reads with +
    assertThat(texts("steps(rref([[1, -1/x], [0, 1]]))").getFirst())
        .isEqualTo("R₁ ← R₁ + R₂/x: [[1, 0], [0, 1]]");
    assertThat(texts("steps(linsolve([[1, 2], [2, 4]], [a, b]))").getLast())
        .endsWith("This holds unless -2a + b = 0");
    assertThat(texts("steps(inverse([[a, b], [c, d]]))").getFirst())
        .endsWith("This assumes -b c + a d ≠ 0");
    assertThat(texts("steps(det([[1, 2, 3], [4, 5, 6], [7, 8, 10]]))").getFirst())
        .startsWith("Expand along the first row by cofactors");
    assertThat(texts("steps(linsolve([[1, 2], [2, 4]], [3, 6]))").getLast())
        .isEqualTo(
            "The columns without a pivot are free, with the parameters {t₁}: ⟨-2t₁ + 3, t₁⟩");
  }

  private static final String QUINTIC =
      "[[0, 1, 0, 0, 0], [0, 0, 1, 0, 0], [0, 0, 0, 1, 0], [0, 0, 0, 0, 1], [1, 1, 0, 0, 0]]";

  @Test
  void eigenvaluesFollowTheNumericRule() {
    assertThat(shown("eigenvalues([[2, 1], [1, 2]])")).isEqualTo("⟨1, 3⟩");
    assertThat(shown("charpoly([[1, 2], [3, 4]], x)")).isEqualTo("x² - 5x - 2");
    assertThat(shown("x := 5\ncharpoly([[1, 2], [3, 4]], x)")).isEqualTo("x² - 5x - 2");
    // exact formats: the quintic's roots are not exact, so the call stays, unmarked
    Outcome.Value exact = (Outcome.Value) Run.last("eigenvalues(" + QUINTIC + ")");
    assertThat(exact.value()).isInstanceOf(mathcanvas.engine.expr.Apply.class);
    assertThat(exact.numeric()).isFalse();
    // numeric(…) and a decimal format: decimals, marked numeric
    Outcome.Value numeric = (Outcome.Value) Run.last("numeric(eigenvalues(" + QUINTIC + "), 10)");
    assertThat(numeric.numeric()).isTrue();
    assertThat(shown("numeric(eigenvalues(" + QUINTIC + "), 10)")).startsWith("⟨1.167303978");
    Outcome.Value format =
        (Outcome.Value)
            Run.last("eigenvalues(" + QUINTIC + ")", new Settings(34, AngleMode.RADIANS, 10));
    assertThat(format.numeric()).isTrue();
    assertThat(texts("steps(numeric(eigenvalues(" + QUINTIC + "), 10))"))
        .anySatisfy(
            t ->
                assertThat(t)
                    .startsWith(
                        "The roots cannot be found exactly, so the eigenvalues are approximate, to 10 digits"));
    // an exact answer is never marked, whatever the format
    Outcome.Value closed =
        (Outcome.Value)
            Run.last("eigenvalues([[2, 1], [1, 2]])", new Settings(34, AngleMode.RADIANS, 10));
    assertThat(closed.numeric()).isFalse();
    assertThat(texts("steps(eigenvectors([[2, 1], [1, 2]]))"))
        .contains(
            "For λ = 3, the eigenvectors are the null space of A − λI: [[1, [[-1, 1]]], [3, [[1, 1]]]]");
  }
}
