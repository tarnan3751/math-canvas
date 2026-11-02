package mathcanvas.engine.linalg;

import static mathcanvas.engine.linalg.LinalgCases.parse;
import static mathcanvas.engine.linalg.LinalgCases.rules;
import static mathcanvas.engine.linalg.LinalgCases.run;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Gram–Schmidt, not normalised, and the norms. */
class GramSchmidtTest {

  @Test
  void orthogonalizes() {
    assertThat(run("gramschmidt([[1, 1, 0], [1, 0, 1]])").expr())
        .isEqualTo(parse("[[1, 1, 0], [1/2, -1/2, 1]]"));
    assertThat(rules(run("gramschmidt([[1, 0], [0, 1]])"))).containsExactly("gramschmidt.done");
  }

  @Test
  void dropsADependentVector() {
    var r = run("gramschmidt([[1, 1, 0], [1, 0, 1], [2, 1, 1]])");
    assertThat(r.expr()).isEqualTo(parse("[[1, 1, 0], [1/2, -1/2, 1]]"));
    assertThat(rules(r)).containsExactly("gramschmidt.project", "gramschmidt.dependent");
  }

  @Test
  void theHermitianProduct() {
    // ⟨1, i⟩ and ⟨1, -i⟩ are orthogonal under conj(u)·v, not under the plain dot product
    assertThat(run("gramschmidt([[1, i], [1, -i]])").expr()).isEqualTo(parse("[[1, i], [1, -i]]"));
  }

  @Test
  void theNorms() {
    assertThat(LinalgCases.value("norm", parse("[3, 4]"))).isEqualTo(parse("5"));
    assertThat(LinalgCases.value("norm", parse("[1, -2]"), parse("1"))).isEqualTo(parse("3"));
    assertThat(LinalgCases.value("norm", parse("[1, -5]"), parse("inf"))).isEqualTo(parse("5"));
    assertThat(LinalgCases.value("norm", parse("[3, 4i]"))).isEqualTo(parse("5"));
    assertThat(LinalgCases.value("normalize", parse("[3, 4]"))).isEqualTo(parse("[3/5, 4/5]"));
    assertThatThrownBy(() -> LinalgCases.value("norm", parse("[1, 2]"), parse("3")))
        .hasMessage("norm's order must be 1, 2 or inf");
    assertThatThrownBy(() -> LinalgCases.value("normalize", parse("[0, 0]")))
        .hasMessage("normalize needs a vector that is not zero");
    assertThatThrownBy(() -> run("gramschmidt([[1, 2], [3]])"))
        .hasMessage("gramschmidt needs vectors of one length");
  }
}
