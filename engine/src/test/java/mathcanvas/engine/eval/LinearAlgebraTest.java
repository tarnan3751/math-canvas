package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static mathcanvas.engine.eval.Run.value;
import static mathcanvas.engine.testing.Inputs.canonical;
import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import org.junit.jupiter.api.Test;

class LinearAlgebraTest {

  @Test
  void vectorsAddAndScale() {
    assertThat(shown("[1, 2] + [3, 4]")).isEqualTo("⟨4, 6⟩");
    assertThat(shown("[1, 2] - [3, 4]")).isEqualTo("⟨-2, -2⟩");
    assertThat(shown("2*[1, 2]")).isEqualTo("⟨2, 4⟩");
    assertThat(shown("x*[1, 2]")).isEqualTo("⟨x, 2x⟩");
    assertThat(shown("[2, 4]/2")).isEqualTo("⟨1, 2⟩");
    assertThat(shown("v := [1, 2]; v - v")).isEqualTo("⟨0, 0⟩");
  }

  @Test
  void theProductOfTwoVectorsIsTheDotProduct() {
    assertThat(value("[1, 2] * [3, 4]")).isEqualTo(Num.of(11));
    assertThat(value("[1, 2] · [3, 4]")).isEqualTo(Num.of(11));
    assertThat(value("dot([1, 2], [3, 4])")).isEqualTo(Num.of(11));
    assertThat(shown("[x, y] * [x, y]")).isEqualTo("x² + y²");
    assertThat(shown("[1, 2] * [3, 4] * [1, 1]")).isEqualTo("⟨11, 11⟩");
    assertThat(value("dot(u, v)")).isEqualTo(canonical("dot(u, v)"));
  }

  @Test
  void theCrossProductOfThreeVectors() {
    assertThat(shown("[1, 0, 0] × [0, 1, 0]")).isEqualTo("⟨0, 0, 1⟩");
    assertThat(shown("[a, b, c] × [a, b, c]")).isEqualTo("⟨0, 0, 0⟩");
    assertThat(value("2 × 3")).isEqualTo(Num.of(6));
    assertThat(error("[1, 2] × [3, 4]")).isEqualTo("× needs two 3-vectors");
    assertThat(error("[1, 2, 3] × 2")).isEqualTo("× needs two 3-vectors");
  }

  @Test
  void matrixProducts() {
    assertThat(shown("[[1, 2], [3, 4]] + [[1, 0], [0, 1]]")).isEqualTo("[[2, 2], [3, 5]]");
    assertThat(shown("[[1, 2], [3, 4]] * [[5, 6], [7, 8]]")).isEqualTo("[[19, 22], [43, 50]]");
    assertThat(shown("[[5, 6], [7, 8]] * [[1, 2], [3, 4]]")).isEqualTo("[[23, 34], [31, 46]]");
    assertThat(shown("[[1, 2], [3, 4]] * [5, 6]")).isEqualTo("⟨17, 39⟩");
    assertThat(shown("[5, 6] * [[1, 2], [3, 4]]")).isEqualTo("⟨23, 34⟩");
    assertThat(shown("[[1, 2, 3]] * [[1], [2], [3]]")).isEqualTo("[[14]]");
    assertThat(shown("3*[[1, 2], [3, 4]]/2")).isEqualTo("[[3/2, 3], [9/2, 6]]");
  }

  @Test
  void matrixPowers() {
    assertThat(shown("[[1, 2], [3, 4]]^3")).isEqualTo("[[37, 54], [81, 118]]");
    assertThat(shown("[[1, 1], [0, 1]]^100")).isEqualTo("[[1, 100], [0, 1]]");
    assertThat(shown("[[x, 1], [0, x]]^2")).isEqualTo("[[x², 2x], [0, x²]]");
    assertThat(shown("[[1, 2], [3, 4]]^-1")).isEqualTo("[[-2, 1], [3/2, -1/2]]");
    assertThat(value("[[1, 2], [3, 4]]^n")).isInstanceOf(Power.class);
    assertThat(value("[[x, 1], [0, x]]^17")).isInstanceOf(Power.class);
    assertThat(shown("[[1, 2], [3, 4]]^0")).isEqualTo("[[1, 0], [0, 1]]");
    assertThat(shown("M := [[x, 1], [2, 3]]; M^0 * M")).isEqualTo("[[x, 1], [2, 3]]");
    assertThat(error("[[1, 2, 3], [4, 5, 6]]^0")).isEqualTo("a power of a non-square 2×3 matrix");
    assertThat(error("[1, 2]^0")).isEqualTo("a vector has no powers");
  }

  @Test
  void dimensionsAreChecked() {
    assertThat(error("[1, 2] + [1, 2, 3]")).isEqualTo("cannot add vectors of lengths 2 and 3");
    assertThat(error("1 + [1, 2]")).isEqualTo("cannot add a scalar and a vector");
    assertThat(error("x + [[1, 2], [3, 4]]")).isEqualTo("cannot add a scalar and a matrix");
    assertThat(error("[1, 2] + [[1, 2], [3, 4]]")).isEqualTo("cannot add a vector and a matrix");
    assertThat(error("[[1, 2]] + [[1], [2]]"))
        .isEqualTo("cannot add a 2×1 matrix and a 1×2 matrix");
    assertThat(error("[1, 2] * [1, 2, 3]"))
        .isEqualTo("the dot product needs two vectors of the same length, not 2 and 3");
    assertThat(error("[[1, 2, 3], [4, 5, 6]] * [[1, 2], [3, 4]]"))
        .isEqualTo("cannot multiply a 2×3 matrix by a 2×2 matrix");
    assertThat(error("[[1, 2], [3, 4]] * [1, 2, 3]"))
        .isEqualTo("cannot multiply a 2×2 matrix by a vector of length 3");
    assertThat(error("[1, 2, 3] * [[1, 2], [3, 4]]"))
        .isEqualTo("cannot multiply a vector of length 3 by a 2×2 matrix");
    assertThat(error("[[1, 2, 3], [4, 5, 6]]^2")).isEqualTo("a power of a non-square 2×3 matrix");
    assertThat(error("[1, 2]^2")).isEqualTo("a vector has no powers");
    assertThat(error("dot([1, 2], 3)")).isEqualTo("`dot` needs two vectors of the same length");
    assertThat(error("[[1e999999999, 0], [0, 1]]^3"))
        .isEqualTo("a number grew too large or too small for this precision");
  }

  @Test
  void collectionFunctions() {
    assertThat(value("nth([10, 20, 30], 2)")).isEqualTo(Num.of(20));
    assertThat(shown("nth([[1, 2], [3, 4]], 2)")).isEqualTo("⟨3, 4⟩");
    assertThat(value("nth([x = 1, y = 2], 1)")).isEqualTo(canonical("x = 1"));
    assertThat(value("nth([1, 2], k)")).isEqualTo(canonical("nth([1, 2], k)"));
    assertThat(error("nth([10, 20, 30], 4)")).isEqualTo("`nth`: index 4 is out of range 1 to 3");
    assertThat(error("nth([10, 20], 1/2)")).isEqualTo("`nth` needs a whole-number index");
    assertThat(error("nth({1, 2}, 1)")).isEqualTo("`nth` needs a list, a vector or a matrix");
    assertThat(value("length({1, 2, 2})")).isEqualTo(Num.of(2));
    assertThat(value("length([[1, 2], [3, 4], [5, 6]])")).isEqualTo(Num.of(3));
    assertThat(value("length([])")).isEqualTo(Num.ZERO);
    assertThat(value("length(v)")).isEqualTo(canonical("length(v)"));
    assertThat(error("length(3)")).isEqualTo("`length` needs a list, a vector or a set");
  }

  @Test
  void listsAndSetsCarryNoArithmetic() {
    assertThat(value("a := {1, 2}; [a, 1]")).isInstanceOf(ListExpr.class);
  }
}
