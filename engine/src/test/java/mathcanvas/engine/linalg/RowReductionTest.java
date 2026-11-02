package mathcanvas.engine.linalg;

import static mathcanvas.engine.linalg.LinalgCases.count;
import static mathcanvas.engine.linalg.LinalgCases.parse;
import static mathcanvas.engine.linalg.LinalgCases.rules;
import static mathcanvas.engine.linalg.LinalgCases.run;
import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.rule.Rewriter;
import org.junit.jupiter.api.Test;

/**
 * Gauss–Jordan elimination as a textbook does it by hand. The five reference cases are
 * textbook-style matrices whose row-operation counts were derived by hand from the algorithm as
 * stated (first pivot not known to be zero, scale to 1 unless already 1, clear every other row),
 * not quoted from a book.
 */
class RowReductionTest {

  private static void reduces(String m, String reduced, long operations) {
    Rewriter.Result r = run("rref(" + m + ")");
    assertThat(r.expr()).isEqualTo(parse(reduced));
    assertThat(count(r, "rref") - (rules(r).contains("rref.done") ? 1 : 0))
        .as("row operations of %s", m)
        .isEqualTo(operations);
  }

  @Test
  void theFiveReferenceCases() {
    // add, scale, add
    reduces("[[1, 2, 3], [4, 5, 6]]", "[[1, 0, -1], [0, 1, 2]]", 3);
    // swap, add; then add, add; the third column has no pivot
    reduces("[[0, 1, 2], [1, 1, 1], [2, 3, 4]]", "[[1, 0, -1], [0, 1, 2], [0, 0, 0]]", 4);
    // scale, add, add; add, add; scale, add, add
    reduces("[[2, 4, -2], [4, 9, -3], [-2, -3, 7]]", "[[1, 0, 0], [0, 1, 0], [0, 0, 1]]", 8);
    // add, add; the second column has no pivot; scale, add, add
    reduces(
        "[[1, 2, 1, 0], [2, 4, 0, 2], [3, 6, 1, 2]]",
        "[[1, 2, 0, 1], [0, 0, 1, -1], [0, 0, 0, 0]]",
        5);
    // add, add; swap in the second column, add; add, add
    reduces("[[1, 1, 1], [1, 1, 2], [1, 2, 3]]", "[[1, 0, 0], [0, 1, 0], [0, 0, 1]]", 6);
  }

  @Test
  void theOperationsInOrder() {
    assertThat(rules(run("rref([[0, 1], [2, 0]])"))).containsExactly("rref.swap", "rref.scale");
    assertThat(rules(run("rref([[1, 0], [0, 1]])"))).containsExactly("rref.done");
    assertThat(rules(run("rref([[0, 0], [0, 0]])"))).containsExactly("rref.done");
  }

  @Test
  void aSymbolicPivotIsAssumedNotZero() {
    Rewriter.Result r = run("rref([[a, 1], [1, 1]])");
    assertThat(r.expr()).isEqualTo(parse("[[1, 0], [0, 1]]"));
    assertThat(r.trace().rewrites().getFirst().values().get("conditions"))
        .isEqualTo(parse("{a ≠ 0}"));
    // a pivot known to be zero is passed over
    assertThat(run("rref([[a - a, 1], [1, 0]])").expr()).isEqualTo(parse("[[1, 0], [0, 1]]"));
  }

  @Test
  void decimalsStayDecimal() {
    Matrix m = (Matrix) run("rref([[2.0, 1, 1], [1, 1, 3]])").expr();
    assertThat(m.rows().getFirst().getLast())
        .isInstanceOfSatisfying(Num.class, n -> assertThat(n.value().isExact()).isFalse());
  }
}
