package mathcanvas.engine.linalg;

import static mathcanvas.engine.linalg.LinalgCases.parse;
import static mathcanvas.engine.linalg.LinalgCases.rules;
import static mathcanvas.engine.linalg.LinalgCases.run;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import mathcanvas.engine.expr.Num;
import org.junit.jupiter.api.Test;

/** Determinants by cofactors and by Bareiss elimination. */
class DeterminantsTest {

  @Test
  void smallMatricesAreOneStep() {
    assertThat(run("det([[7]])").expr()).isEqualTo(Num.of(7));
    assertThat(rules(run("det([[1, 2], [3, 4]])"))).containsExactly("det.formula");
    assertThat(run("det([[a, b], [c, d]])").expr()).isEqualTo(parse("a d - b c"));
  }

  @Test
  void cofactorsUpToThreeByDefault() {
    assertThat(run("det([[1, 2, 3], [4, 5, 6], [7, 8, 10]])").expr()).isEqualTo(Num.of(-3));
    assertThat(rules(run("det([[1, 2, 3], [4, 5, 6], [7, 8, 10]])")))
        .containsExactly("det.cofactor", "det.minors");
    assertThat(rules(run("det([[2, 0, 1, 3], [1, 1, 0, 2], [0, 3, 1, 1], [1, 0, 2, 1]])")))
        .startsWith("det.eliminate")
        .endsWith("det.pivots");
  }

  @Test
  void theMethodsAgree() {
    String m = "[[2, 0, 1, 3], [1, 1, 0, 2], [0, 3, 1, 1], [1, 0, 2, 1]]";
    assertThat(run("det(" + m + ", cofactor)").expr()).isEqualTo(Num.of(-1));
    assertThat(run("det(" + m + ", elimination)").expr()).isEqualTo(Num.of(-1));
    assertThat(run("det([[a, b, c], [d, e2, f], [g, h, k]], elimination)").expr())
        .isEqualTo(run("det([[a, b, c], [d, e2, f], [g, h, k]], cofactor)").expr());
  }

  @Test
  void swapsChangeTheSignAndAMissingPivotGivesZero() {
    assertThat(run("det([[0, 1, 0], [1, 0, 0], [0, 0, 1]], elimination)").expr())
        .isEqualTo(Num.MINUS_ONE);
    assertThat(rules(run("det([[0, 1, 0], [1, 0, 0], [0, 0, 1]], elimination)")))
        .startsWith("det.swap");
    assertThat(rules(run("det([[0, 1, 2], [0, 3, 4], [0, 5, 6]], elimination)")))
        .containsExactly("det.zero-column");
  }

  @Test
  void cofactorExpansionIsCapped() {
    String row = "[1, 0, 0, 0, 0, 0, 0, 0, 0]";
    String m = "[" + String.join(", ", java.util.Collections.nCopies(9, row)) + "]";
    assertThatThrownBy(() -> run("det(" + m + ", cofactor)"))
        .hasMessage("cofactor expansion works up to 8×8: use `det(M, elimination)`");
  }
}
