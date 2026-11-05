package mathcanvas.engine.linalg;

import static mathcanvas.engine.linalg.LinalgCases.rules;
import static mathcanvas.engine.linalg.LinalgCases.run;
import static mathcanvas.engine.testing.Inputs.canonical;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Gram–Schmidt, not normalised, and the norms. */
class GramSchmidtTest {

  @Test
  void orthogonalizes() {
    assertThat(run("gramschmidt([[1, 1, 0], [1, 0, 1]])").expr())
        .isEqualTo(canonical("[[1, 1, 0], [1/2, -1/2, 1]]"));
    assertThat(rules(run("gramschmidt([[1, 0], [0, 1]])"))).containsExactly("gramschmidt.done");
  }

  @Test
  void dropsADependentVector() {
    var r = run("gramschmidt([[1, 1, 0], [1, 0, 1], [2, 1, 1]])");
    assertThat(r.expr()).isEqualTo(canonical("[[1, 1, 0], [1/2, -1/2, 1]]"));
    assertThat(rules(r)).containsExactly("gramschmidt.project", "gramschmidt.dependent");
  }

  @Test
  void theHermitianProduct() {
    // ⟨1, i⟩ and ⟨1, -i⟩ are orthogonal under conj(u)·v, not under the plain dot product
    assertThat(run("gramschmidt([[1, i], [1, -i]])").expr())
        .isEqualTo(canonical("[[1, i], [1, -i]]"));
  }

  @Test
  void theNorms() {
    assertThat(LinalgCases.value("norm", canonical("[3, 4]"))).isEqualTo(canonical("5"));
    assertThat(LinalgCases.value("norm", canonical("[1, -2]"), canonical("1")))
        .isEqualTo(canonical("3"));
    assertThat(LinalgCases.value("norm", canonical("[1, -5]"), canonical("inf")))
        .isEqualTo(canonical("5"));
    assertThat(LinalgCases.value("norm", canonical("[3, 4i]"))).isEqualTo(canonical("5"));
    assertThat(LinalgCases.value("normalize", canonical("[3, 4]")))
        .isEqualTo(canonical("[3/5, 4/5]"));
    assertThatThrownBy(() -> LinalgCases.value("norm", canonical("[1, 2]"), canonical("3")))
        .hasMessage("`norm`'s order must be 1, 2 or inf");
    assertThatThrownBy(() -> LinalgCases.value("normalize", canonical("[0, 0]")))
        .hasMessage("`normalize` needs a vector that is not zero");
    assertThatThrownBy(() -> run("gramschmidt([[1, 2], [3]])"))
        .hasMessage("`gramschmidt` needs vectors of the same length");
  }
}
