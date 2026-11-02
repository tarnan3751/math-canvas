package mathcanvas.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MatrixLiteralTest {

  @Test
  void aVectorIsFoundWithTheCaretAnywhereInside() {
    String t = "2 * [1, x + 1, sin(x, y)] + 1";
    var expected =
        new MatrixLiteral.Literal(
            4, 25, true, List.of(List.of("1"), List.of("x + 1"), List.of("sin(x, y)")));

    for (int caret = 5; caret <= 24; caret++) {
      assertThat(MatrixLiteral.at(t, caret)).as("caret %d", caret).contains(expected);
    }
  }

  @Test
  void aCaretOutsideFindsNothing() {
    String t = "a + [1, 2] + (3)";

    assertThat(MatrixLiteral.at(t, 4)).isEmpty(); // before the opening bracket
    assertThat(MatrixLiteral.at(t, 10)).isEmpty(); // after the closing bracket
    assertThat(MatrixLiteral.at(t, 0)).isEmpty();
    assertThat(MatrixLiteral.at(t, 15)).isEmpty();
    assertThat(MatrixLiteral.at("", 0)).isEmpty();
  }

  @Test
  void aMatrixIsFoundFromInsideARowOrBetweenRows() {
    String t = "M := [[1, 2], [3, 4]]";
    var expected =
        new MatrixLiteral.Literal(5, 21, false, List.of(List.of("1", "2"), List.of("3", "4")));

    assertThat(MatrixLiteral.at(t, 8)).contains(expected); // inside [1, 2]
    assertThat(MatrixLiteral.at(t, 13)).contains(expected); // between the rows
    assertThat(MatrixLiteral.at(t, 20)).contains(expected); // after [3, 4]
  }

  @Test
  void theInnermostLiteralWins() {
    String t = "f([1, [[a, b]], 2])";

    // the vector [a, b] is the row of the 1×2 matrix around it
    var matrix = new MatrixLiteral.Literal(6, 14, false, List.of(List.of("a", "b")));
    assertThat(MatrixLiteral.at(t, 9)).contains(matrix);
    // the outer group mixes a matrix with scalars: not uniform, so no literal
    assertThat(MatrixLiteral.at(t, 4)).isEmpty();
    // a vector inside a call inside a vector
    var inner = new MatrixLiteral.Literal(6, 12, true, List.of(List.of("x"), List.of("y")));
    assertThat(MatrixLiteral.at("[1, g([x, y])]", 8)).contains(inner);
  }

  @Test
  void raggedRowsKeepTheirLengths() {
    var literal = MatrixLiteral.at("[[1, 2, 3], [4], []]", 2).orElseThrow();

    assertThat(literal.vector()).isFalse();
    assertThat(literal.rows()).containsExactly(List.of("1", "2", "3"), List.of("4"), List.of());
    assertThat(literal.columns()).isEqualTo(3);
  }

  @Test
  void deeperNestingIsNotUniform() {
    assertThat(MatrixLiteral.at("[[[1]], [[2]]]", 1)).isEmpty();
    assertThat(MatrixLiteral.at("[[1, 2], 3]", 9)).isEmpty();
    // but the innermost uniform group under the caret is still found
    assertThat(MatrixLiteral.at("[[[1, 2]]]", 4).orElseThrow().rows())
        .containsExactly(List.of("1", "2"));
  }

  @Test
  void indexingSumBoundsEmptyAndUnbalancedGroupsAreNotLiterals() {
    assertThat(MatrixLiteral.at("v[1, 2]", 3)).isEmpty();
    assertThat(MatrixLiteral.at("Σ[k=1,n] k", 3)).isEmpty();
    assertThat(MatrixLiteral.at("(a)[1]", 4)).isEmpty();
    for (String t :
        List.of(
            "∑[k=1,n] k",
            "∏[k=1,n] k",
            "Π[k=1,n] k",
            "∫[0, 1] x dx",
            "x₁[1, 2]",
            "x²[1, 2]",
            "x⁻¹[1, 2]",
            "3![1, 2]",
            "50%[1, 2]",
            "2 [1, 2]",
            "f(x)\t[1, 2]")) {
      int caret = t.indexOf('[') + 2;
      assertThat(MatrixLiteral.at(t, caret)).as(t).isEmpty();
    }
    // after an operator, a comma, an opening bracket or a line break, a group is a literal
    for (String t : List.of("2 * [1, 2]", "f(x, [1, 2])", "x := [1, 2]", "x\n[1, 2]")) {
      int caret = t.indexOf('[') + 2;
      assertThat(MatrixLiteral.at(t, caret)).as(t).isPresent();
    }
    assertThat(MatrixLiteral.at("[]", 1)).isEmpty();
    assertThat(MatrixLiteral.at("[[]]", 2)).isEmpty();
    assertThat(MatrixLiteral.at("[1, (2]", 2)).isEmpty();
    assertThat(MatrixLiteral.at("[1, 2", 2)).isEmpty();
  }

  @Test
  void literalsAreWrittenInTheLanguage() {
    assertThat(MatrixLiteral.write(List.of(List.of("1", " 2 "), List.of("", "x")), false))
        .isEqualTo("[[1, 2], [0, x]]");
    assertThat(MatrixLiteral.write(List.of(List.of("a"), List.of(""), List.of("c")), true))
        .isEqualTo("[a, 0, c]");
  }
}
