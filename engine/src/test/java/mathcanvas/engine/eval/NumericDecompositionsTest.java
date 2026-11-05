package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static mathcanvas.engine.eval.Run.value;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.builtin.Builtins;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Step;
import org.junit.jupiter.api.Test;

/**
 * The numeric linear algebra builtins ({@code docs/language.md} §14.17): their values, the digits
 * of the entries, the call left for a symbolic matrix, the errors, the step and the numeric mark.
 */
class NumericDecompositionsTest {

  private static Outcome.Value run(String script, Settings settings) {
    Session s = new Session(Environment.EMPTY.withSettings(settings));
    return (Outcome.Value) s.run(Parser.parseScript(script, s.names())).getLast();
  }

  private static Outcome.Value run(String script) {
    return run(script, Settings.DEFAULT);
  }

  private static List<String> rules(Outcome.Value v) {
    return v.trace().rewrites().stream().map(r -> r.ruleSet() + "." + r.rule()).toList();
  }

  private static Expr real(String text) {
    return new Num(Real.of(text));
  }

  @Test
  void eachBuiltinHasItsShape() {
    assertThat(shown("lu([[4, 3], [6, 3]])"))
        .isEqualTo(
            "[[[0.0, 1.0], [1.0, 0.0]], [[1.0, 0.0], [0.6666666666666666, 1.0]], [[6.0, 3.0], [0.0,"
                + " 1.0]]]");
    assertThat(((ListExpr) value("qr([[1, 2], [3, 4], [5, 6]])")).items())
        .extracting(m -> ((Matrix) m).rows().size())
        .containsExactly(3, 3);
    assertThat(shown("cholesky([[4, 2], [2, 3]])"))
        .isEqualTo("[[2.0, 0.0], [1.0, 1.414213562373095]]");
    assertThat(((ListExpr) value("svd([[1, 2, 3], [4, 5, 6]])")).items())
        .extracting(m -> ((Matrix) m).rows().size() + "x" + ((Matrix) m).rows().getFirst().size())
        .containsExactly("2x2", "2x2", "3x2");
    assertThat(value("lstsq([[1, 1], [1, 2], [1, 3]], [1, 2, 2])")).isInstanceOf(Vector.class);
    assertThat(shown("expm([[0, 1], [0, 0]])")).isEqualTo("[[1.0, 1.0], [0.0, 1.0]]");
  }

  @Test
  void entriesAreDecimalsOfAtMostSixteenDigits() {
    Matrix u = (Matrix) ((ListExpr) value("lu([[4, 3], [6, 3]])")).items().get(1);
    assertThat(u.rows().get(1).getFirst()).isEqualTo(real("0.6666666666666666"));
    assertThat(u.rows().getFirst().getFirst()).isEqualTo(real("1"));
    Matrix fifty = (Matrix) ((ListExpr) value("numeric(lu([[4, 3], [6, 3]]), 50)")).items().get(1);
    assertThat(fifty.rows().get(1).getFirst()).isEqualTo(real("0.6666666666666666"));
    Matrix five = (Matrix) ((ListExpr) value("numeric(lu([[4, 3], [6, 3]]), 5)")).items().get(1);
    assertThat(five.rows().get(1).getFirst()).isEqualTo(real("0.66667"));
    // a negative zero is zero
    assertThat(shown("expm([[0, 0], [0, 0]])")).isEqualTo("[[1.0, 0.0], [0.0, 1.0]]");
  }

  @Test
  void closedEntriesAreNumbers() {
    assertThat(shown("cholesky([[π, 0], [0, 1/4]])"))
        .isEqualTo("[[1.772453850905516, 0.0], [0.0, 0.5]]");
    assertThat(shown("cholesky([[√2]])")).isEqualTo("[[1.189207115002721]]");
    assertThat(shown("cholesky([[2.25]])")).isEqualTo("[[1.5]]");
    assertThat(shown("cholesky([[sin(30)]])", Run.DEGREES)).isEqualTo("[[0.7071067811865476]]");
  }

  @Test
  void aSymbolicMatrixLeavesTheCall() {
    assertThat(value("lu([[a, 1], [2, 3]])")).isInstanceOf(Apply.class);
    assertThat(value("lu(M)")).isInstanceOf(Apply.class);
    assertThat(value("lstsq([[1], [2]], [b, 1])")).isInstanceOf(Apply.class);
    assertThat(shown("M := [[4, 3], [6, 3]]; nth(lu(M), 3)")).isEqualTo("[[6.0, 3.0], [0.0, 1.0]]");
    assertThat(shown("f(a) := cholesky([[a]]); f(4)")).isEqualTo("[[2.0]]");
    Outcome.Value v = run("steps(lu([[a, 1], [2, 3]]))");
    assertThat(v.numeric()).isFalse();
    assertThat(rules(v)).containsExactly("decomposition.symbolic");
    TraceChecks.assertChain(new Rewriter.Result(v.value(), v.trace()));
    assertThat(Narration.steps(new Evaluator.Result(v.value(), v.trace()), Context.DEFAULT))
        .extracting(Step::text)
        .contains("lu needs a matrix of numbers, so it is left as it is");
  }

  @Test
  void theErrors() {
    assertThat(error("lu(5)")).isEqualTo("`lu` needs a matrix");
    assertThat(error("svd([1, 2])")).isEqualTo("`svd` needs a matrix");
    assertThat(error("lstsq([[1, 2]], 3)")).isEqualTo("`lstsq` needs a matrix and a vector");
    assertThat(error("lu([[1, 2], [2, 4]])"))
        .isEqualTo("`lu` needs a matrix that is not singular or too close to singular");
    assertThat(error("lu([[1, 2, 3]])")).isEqualTo("`lu` needs a square matrix");
    assertThat(error("expm([[1, 2]])")).isEqualTo("`expm` needs a square matrix");
    assertThat(error("cholesky([[1, 2], [2, 1]])"))
        .isEqualTo("`cholesky` needs a symmetric positive-definite matrix");
    assertThat(error("lstsq([[1, 2], [2, 4], [3, 6]], [1, 2, 3])"))
        .isEqualTo("`lstsq` needs a matrix whose columns are independent");
    assertThat(error("lstsq([[1, 1], [1, 2]], [1, 2, 3])"))
        .isEqualTo("`lstsq` needs a vector of 2 entries");
    assertThat(error("expm([[1000]])")).isEqualTo("`expm`'s result is too large to approximate");
    assertThat(error("lu([[i, 1], [1, 1]])")).isEqualTo("`lu` needs a real matrix");
    assertThat(error("lu([[10^400]])")).isEqualTo("`lu`'s entries are too large to approximate");
    assertThat(error("lu([[10^-400]])")).isEqualTo("`lu`'s entries are too large to approximate");
    for (String name : List.of("lu", "qr", "cholesky", "svd", "lstsq", "expm")) {
      assertThat(Builtins.lookup(name)).as(name).isPresent();
    }
  }

  @Test
  void theStepAndTheNumericMark() {
    Outcome.Value v = run("steps(lu([[4, 3], [6, 3]]))");
    assertThat(v.numeric()).isTrue();
    assertThat(rules(v)).containsExactly("numeric.decomposition");
    TraceChecks.assertChain(new Rewriter.Result(v.value(), v.trace()));
    Rewrite step = v.trace().rewrites().getFirst();
    assertThat(step.bindings().get("digits_")).isEqualTo(Num.of(16));
    assertThat(Narration.steps(new Evaluator.Result(v.value(), v.trace()), Context.DEFAULT))
        .extracting(Step::text)
        .contains("Approximate value, shown to at most 16 digits");
    Outcome.Value top = run("steps(numeric(expm([[0, 1], [0, 0]]), 5))");
    assertThat(rules(top)).containsExactly("numeric.decomposition");
    TraceChecks.assertChain(new Rewriter.Result(top.value(), top.trace()));
  }

  @Test
  void aNestedDecompositionIsMarkedAndUntraced() {
    Outcome.Value v = run("steps(2 nth(svd([[3, 0], [0, 4]]), 2))");
    assertThat(v.numeric()).isTrue();
    assertThat(rules(v)).noneMatch(r -> r.contains("decomposition"));
    assertThat(run("lu([[4, 3], [6, 3]])").numeric()).isTrue();
    assertThat(run("[[4, 3], [6, 3]]").numeric()).isFalse();
  }

  @Test
  void everyNumberFormatEvaluates() {
    // unlike quadrature, the decompositions do not wait for a decimal format
    assertThat(Settings.DEFAULT.quadratureDigits()).isZero();
    // the Padé approximant's double is one unit above e's nearest, and is shown as it came
    assertThat(value("expm([[1]])"))
        .isEqualTo(new Matrix(List.of(List.of(real("2.718281828459046")))));
  }
}
