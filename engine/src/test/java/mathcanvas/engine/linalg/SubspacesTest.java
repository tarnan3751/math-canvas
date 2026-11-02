package mathcanvas.engine.linalg;

import static mathcanvas.engine.linalg.LinalgCases.parse;
import static mathcanvas.engine.linalg.LinalgCases.rules;
import static mathcanvas.engine.linalg.LinalgCases.run;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.rule.Rewriter;
import org.junit.jupiter.api.Test;

/** Rank, the null and column spaces, and linear solve in matrix form. */
class SubspacesTest {

  @Test
  void rank() {
    assertThat(run("rank([[1, 2], [2, 4]])").expr()).isEqualTo(Num.ONE);
    assertThat(run("rank([[0, 0], [0, 0]])").expr()).isEqualTo(Num.ZERO);
    assertThat(rules(run("rank([[1, 2], [2, 4]])"))).containsExactly("rref.add", "rank.count");
  }

  @Test
  void nullAndColumnSpaces() {
    assertThat(run("nullspace([[1, 2], [2, 4]])").expr()).isEqualTo(parse("[[-2, 1]]"));
    assertThat(run("nullspace([[1, 2], [3, 4]])").expr())
        .isEqualTo(new ListExpr(java.util.List.of()));
    assertThat(run("nullspace([[1, 2, 1, 0], [2, 4, 0, 2], [3, 6, 1, 2]])").expr())
        .isEqualTo(parse("[[-2, 1, 0, 0], [-1, 0, 1, 1]]"));
    // the pivot columns of the matrix itself, not of its reduced form
    assertThat(run("colspace([[1, 2, 1, 0], [2, 4, 0, 2], [3, 6, 1, 2]])").expr())
        .isEqualTo(parse("[[1, 2, 3], [1, 0, 1]]"));
  }

  @Test
  void linsolve() {
    assertThat(run("linsolve([[1, 2], [3, 4]], [5, 6])").expr()).isEqualTo(parse("[-4, 9/2]"));
    assertThat(run("linsolve([[1, 2], [2, 4]], [3, 6])").expr())
        .isEqualTo(parse("[3 - 2 t_1, t_1]"));
    assertThat(run("linsolve([[1, 2], [2, 4]], [3, 7])").expr())
        .isEqualTo(new SetExpr(java.util.List.of()));
    assertThat(run("linsolve([[1, 2], [3, 4]], [[1, 0], [0, 1]])").expr())
        .isEqualTo(parse("[[-2, 1], [3/2, -1/2]]"));
    // the parameters are numbered column by column and avoid the names of the arguments
    assertThat(run("linsolve([[1, 1]], [[t_1, 1]])").expr())
        .isEqualTo(parse("[[t_1 - t_2, 1 - t_3], [t_2, t_3]]"));
    // a right side that may vanish: the generic answer, with the condition
    Rewriter.Result generic = run("linsolve([[1, 1], [1, 1]], [1, a])");
    assertThat(rules(generic)).endsWith("linsolve.inconsistent");
    // no solution unless the right side vanishes
    assertThat(generic.trace().rewrites().getLast().values().get("unless"))
        .isEqualTo(parse("{a - 1 = 0}"));
    assertThatThrownBy(() -> run("linsolve([[1, 2], [3, 4]], [1, 2, 3])"))
        .hasMessage("linsolve needs a matrix and a vector of matching height");
  }
}
