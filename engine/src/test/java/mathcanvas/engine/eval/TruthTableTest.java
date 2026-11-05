package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static mathcanvas.engine.eval.Run.value;
import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.engine.expr.Matrix;
import org.junit.jupiter.api.Test;

/** {@code truthtable} ({@code docs/language.md} §14.24): its rows, its names and its errors. */
class TruthTableTest {

  @Test
  void anImplication() {
    assertThat(shown("truthtable(p implies q, [p, q])"))
        .isEqualTo(
            "[[p, q, p ⟹ q], [true, true, true], [true, false, false], [false, true, true],"
                + " [false, false, true]]");
  }

  @Test
  void theShape() {
    Matrix m = (Matrix) value("truthtable(p xor q xor r, [p, q, r])");
    assertThat(m.rows()).hasSize(9);
    assertThat(m.rows().getFirst()).hasSize(4);
    assertThat(shown("truthtable(not p, [p])"))
        .isEqualTo("[[p, ¬p], [true, false], [false, true]]");
    assertThat(shown("truthtable(1 < 2 and p, [p])"))
        .isEqualTo("[[p, p], [true, true], [false, false]]");
    assertThat(shown("truthtable(true or false, [])")).isEqualTo("[[true], [true]]");
  }

  @Test
  void theNamesAreBound() {
    assertThat(shown("p := 3; truthtable(p or q, [p, q])"))
        .isEqualTo(
            "[[p, q, p ∨ q], [true, true, true], [true, false, true], [false, true, true],"
                + " [false, false, false]]");
  }

  @Test
  void theErrors() {
    assertThat(error("truthtable(p and x > 0, [p])"))
        .isEqualTo("`truthtable` cannot tell whether `x > 0` is true from the names given");
    assertThat(error("truthtable(p, [p, p])"))
        .isEqualTo(
            "`truthtable` takes an expression and a list of different names, as in `truthtable(p and q, [p, q])`");
    assertThat(error("truthtable(p, [a, b, c, d, e1, f, g, h, j, k, l])"))
        .isEqualTo("`truthtable` takes at most 10 names");
    assertThat(error("truthtable(p + 1, [p])"))
        .isEqualTo("true and false cannot be added, multiplied or raised to powers");
  }
}
