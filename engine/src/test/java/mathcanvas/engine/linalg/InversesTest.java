package mathcanvas.engine.linalg;

import static mathcanvas.engine.linalg.LinalgCases.parse;
import static mathcanvas.engine.linalg.LinalgCases.rules;
import static mathcanvas.engine.linalg.LinalgCases.run;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import org.junit.jupiter.api.Test;

/** Inverses by the adjugate and by Gauss–Jordan on {@code [A | I]}. */
class InversesTest {

  @Test
  void twoByTwoByTheAdjugate() {
    Rewriter.Result r = run("inverse([[1, 2], [3, 4]])");
    assertThat(r.expr()).isEqualTo(parse("[[-2, 1], [3/2, -1/2]]"));
    assertThat(rules(r)).containsExactly("inverse.adjugate");
    Rewriter.Result s = run("inverse([[a, b], [c, d]])");
    assertThat(s.trace().rewrites().getFirst().values().get("conditions"))
        .isEqualTo(parse("{a d - b c ≠ 0}"));
  }

  @Test
  void largerByGaussJordan() {
    Rewriter.Result r = run("inverse([[2, 0, 1], [1, 1, 0], [0, 3, 1]])");
    assertThat(r.expr()).isEqualTo(parse("[[1/5, 3/5, -1/5], [-1/5, 2/5, 1/5], [3/5, -6/5, 2/5]]"));
    assertThat(rules(r)).startsWith("inverse.augment").endsWith("inverse.read");
  }

  @Test
  void aSymbolicInverseAssumesItsDeterminantIsNotZero() {
    // the elimination divides by x, but what the inverse needs is det = x(y − 1) ≠ 0
    Rewriter.Result r = run("inverse([[x, 1, 0], [0, y, 1], [0, 1, 1]])");
    assertThat(r.trace().rewrites().getLast().values().get("conditions"))
        .isEqualTo(parse("{x y - x ≠ 0}"));
  }

  @Test
  void aSingularMatrixIsAnError() {
    assertThatThrownBy(() -> run("inverse([[1, 2], [2, 4]])")).hasMessage("the matrix is singular");
    assertThatThrownBy(() -> run("inverse([[1, 2, 3], [4, 5, 6], [7, 8, 9]])"))
        .hasMessage("the matrix is singular");
    assertThatThrownBy(() -> Linalg.inverse((Matrix) parse("[[0]]"), Context.DEFAULT))
        .hasMessage("the matrix is singular");
  }
}
