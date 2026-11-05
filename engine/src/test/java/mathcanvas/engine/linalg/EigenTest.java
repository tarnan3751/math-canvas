package mathcanvas.engine.linalg;

import static mathcanvas.engine.linalg.LinalgCases.parse;
import static mathcanvas.engine.linalg.LinalgCases.rules;
import static mathcanvas.engine.linalg.LinalgCases.run;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import org.junit.jupiter.api.Test;

/** The characteristic polynomial, eigenvalues, eigenvectors and diagonalization. */
class EigenTest {

  private static Linalg.Outcome numeric(String call, int digits) {
    Linalg.Outcome o = Linalg.apply((Apply) parse(call), LinalgCases.TRACED, digits);
    TraceChecks.assertChain(o.result());
    return o;
  }

  @Test
  void theCharacteristicPolynomial() {
    assertThat(run("charpoly([[1, 2], [3, 4]], x)").expr()).isEqualTo(parse("x^2 - 5x - 2"));
    assertThat(run("charpoly([[2, 1, 0], [1, 2, 1], [0, 1, 2]], t)").expr())
        .isEqualTo(parse("t^3 - 6t^2 + 10t - 4"));
    assertThat(run("charpoly([[a, b], [c, d]], x)").expr())
        .isEqualTo(parse("x^2 - a x - d x + a d - b c"));
    assertThat(rules(run("charpoly([[1, 2], [3, 4]], x)"))).containsExactly("eigen.charpoly");
    assertThatThrownBy(() -> run("charpoly([[x, 1], [0, 1]], x)"))
        .hasMessage("`charpoly`'s variable must not occur in the matrix");
    assertThatThrownBy(() -> run("charpoly([[1, 1], [0, 1]], 2)"))
        .hasMessage("`charpoly` needs a variable");
  }

  @Test
  void eigenvaluesWithTheirMultiplicities() {
    assertThat(run("eigenvalues([[2, 1], [1, 2]])").expr()).isEqualTo(parse("[1, 3]"));
    assertThat(run("eigenvalues([[2, 1], [0, 2]])").expr()).isEqualTo(parse("[2, 2]"));
    assertThat(run("eigenvalues([[0, -1], [1, 0]])").expr()).isEqualTo(parse("[-i, i]"));
    assertThat(run("eigenvalues([[2, 0, 0], [0, 3, 4], [0, 4, 9]])").expr())
        .isEqualTo(parse("[1, 2, 11]"));
    assertThat(rules(run("eigenvalues([[2, 1], [1, 2]])")))
        .containsExactly("eigen.charpoly", "eigen.roots");
  }

  @Test
  void eigenvectors() {
    assertThat(run("eigenvectors([[2, 1], [1, 2]])").expr())
        .isEqualTo(parse("[[1, [[-1, 1]]], [3, [[1, 1]]]]"));
    // a defective matrix: one eigenvector for a double eigenvalue
    assertThat(run("eigenvectors([[2, 1], [0, 2]])").expr()).isEqualTo(parse("[[2, [[1, 0]]]]"));
    assertThat(run("eigenvectors([[3, 0], [0, 3]])").expr())
        .isEqualTo(parse("[[3, [[1, 0], [0, 1]]]]"));
  }

  @Test
  void theFieldOfAnIrreducibleFactor() {
    // x² − 2 over ℚ: x·x = 2, and the inverse of x is x/2
    Symbol x = Symbol.of("x");
    Polynomial q = Polynomial.of(parse("x^2 - 2"));
    Modular field = new Modular(q, mathcanvas.engine.Cancellation.NEVER);
    Polynomial px = Polynomial.variable(x);
    assertThat(field.mul(px, px)).isEqualTo(Polynomial.constant(Int.of(2)));
    assertThat(field.mul(px, field.inverse(px))).isEqualTo(Polynomial.ONE);
  }

  @Test
  void diagonalize() {
    Rewriter.Result r = run("diagonalize([[2, 1], [1, 2]])");
    assertThat(r.expr()).isEqualTo(parse("[[[-1, 1], [1, 1]], [[1, 0], [0, 3]]]"));
    assertThat(rules(r)).endsWith("diagonalize.assemble");
    Rewriter.Result defective = run("diagonalize([[2, 1], [0, 2]])");
    assertThat(defective.expr()).isEqualTo(parse("diagonalize([[2, 1], [0, 2]])"));
    assertThat(rules(defective)).endsWith("diagonalize.not-diagonalizable");
  }

  @Test
  void theNumericRule() {
    String quintic =
        "eigenvalues([[0, 1, 0, 0, 0], [0, 0, 1, 0, 0], [0, 0, 0, 1, 0], [0, 0, 0, 0, 1], [1, 1, 0, 0, 0]])";
    // exact: the solver finds the quintic's roots only numerically, so the call stays
    Linalg.Outcome exact = numeric(quintic, 0);
    assertThat(exact.result().expr()).isEqualTo(parse(quintic));
    assertThat(exact.numeric()).isFalse();
    assertThat(rules(exact.result())).containsExactly("eigen.charpoly", "eigen.unsolved");
    Linalg.Outcome decimals = numeric(quintic, 10);
    assertThat(decimals.numeric()).isTrue();
    assertThat(decimals.result().expr().toString()).contains("1.167303978");
    assertThat(rules(decimals.result()))
        .containsExactly("eigen.charpoly", "eigen.unsolved", "numeric.eigen");
    // a decimal matrix is inexact already: decimals under every format, and not marked
    Linalg.Outcome decimal = numeric("eigenvalues([[2.0, 1], [1, 2]])", 0);
    assertThat(decimal.numeric()).isFalse();
    assertThat(((mathcanvas.engine.expr.Vector) decimal.result().expr()).items())
        .allSatisfy(e -> assertThat(((Num) e).value().isExact()).isFalse());
  }

  @Test
  void aSymbolicMatrix() {
    assertThat(run("eigenvalues([[a, b], [b, a]])").expr()).isEqualTo(parse("[a + |b|, a - |b|]"));
    assertThat(run("eigenvectors([[a, b], [b, a]])").expr())
        .isEqualTo(parse("[[a + |b|, [[b/|b|, 1]]], [a - |b|, [[-b/|b|, 1]]]]"));
    // a cubic with a symbol: the solver leaves it, so the call stays
    assertThat(run("eigenvalues([[a, 1, 0], [0, 0, 1], [1, 0, 0]])").expr())
        .isInstanceOf(Apply.class);
    assertThat(Linalg.apply((Apply) parse("eigenvalues(M)"), Context.DEFAULT).expr())
        .isEqualTo(parse("eigenvalues(M)"));
    assertThatThrownBy(() -> run("eigenvalues([[1, 2, 3]])"))
        .hasMessage("`eigenvalues` needs a square matrix");
  }

  @Test
  void irrationalAndComplexEntriesTakeTheRowReduction() {
    assertThat(run("eigenvectors([[0, √2], [√2, 0]])").expr())
        .isEqualTo(parse("[[√2, [[1, 1]]], [-√2, [[-1, 1]]]]"));
    assertThat(run("eigenvectors([[0, i], [i, 0]])").expr())
        .isEqualTo(parse("[[-i, [[-1, 1]]], [i, [[1, 1]]]]"));
    assertThat(run("diagonalize([[0, √2], [√2, 0]])").expr())
        .isEqualTo(parse("[[[1, -1], [1, 1]], [[√2, 0], [0, -√2]]]"));
    assertThat(run("diagonalize([[0, i], [i, 0]])").expr())
        .isEqualTo(parse("[[[-1, 1], [1, 1]], [[-i, 0], [0, i]]]"));
  }

  @Test
  void theConditionsOfTheEigenspacesAreRecorded() {
    Rewriter.Result d = run("diagonalize([[a, 0], [1, b]])");
    assertThat(d.trace().rewrites().getLast().values().get("conditions"))
        .isEqualTo(parse("{a - b ≠ 0}"));
    Rewriter.Result v = run("eigenvectors([[a, b], [c, d]])");
    assertThat(v.trace().rewrites())
        .filteredOn(r -> r.rule().equals("nullspace"))
        .allSatisfy(r -> assertThat(r.values()).containsKey("conditions"));
  }

  @Test
  void aDecimalMatrixKeepsItsMultiplicities() {
    assertThat(run("eigenvalues([[1.0, 0], [0, 1.0]])").expr()).isEqualTo(parse("[1.0, 1.0]"));
    assertThat(run("eigenvectors([[1.0, 0], [0, 1.0]])").expr())
        .isEqualTo(parse("[[1.0, [[1, 0], [0, 1]]]]"));
    assertThat(run("eigenvalues([[2.0, 1], [0, 2.0]])").expr()).isEqualTo(parse("[2.0, 2.0]"));
    assertThat(rules(run("diagonalize([[2.0, 1], [0, 2.0]])")))
        .endsWith("diagonalize.not-diagonalizable");
    assertThat(numeric("eigenvalues([[2.0, 1], [0, 2.0]])", 10).result().expr())
        .isEqualTo(parse("[2.0, 2.0]"));
    // a triple root, which the solver alone does not find in decimals
    assertThat(run("eigenvalues([[1.0, 1, 0], [0, 1.0, 1], [0, 0, 1.0]])").expr())
        .isEqualTo(parse("[1.0, 1.0, 1.0]"));
  }
}
