package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.AsciiPrinter;
import mathcanvas.engine.print.LatexPrinter;
import mathcanvas.engine.print.UnicodePrinter;
import org.junit.jupiter.api.Test;

/**
 * The set operators and functions ({@code docs/language.md} §14.24) through the evaluator: values,
 * empty sets, membership, undecided items, the errors and the three printers.
 */
class SetOperationsTest {

  @Test
  void theOperators() {
    assertThat(shown("{1, 2} ∪ {2, 3}")).isEqualTo("{1, 2, 3}");
    assertThat(shown("{1, 2, 3} ∩ {2, 3, 4}")).isEqualTo("{2, 3}");
    assertThat(shown("{1, 2, 3} \\ {2}")).isEqualTo("{1, 3}");
    assertThat(shown("union({1}, {1.0})")).isEqualTo("{1}");
    assertThat(shown("symdiff({1, 2}, {2, 3})")).isEqualTo("{1, 3}");
    assertThat(shown("{1, 2} ∪ {3} ∩ {1, 3}")).isEqualTo("{1, 3}");
    assertThat(shown("{1, 2} ∪ {3} \\ {3}")).isEqualTo("{1, 2}");
  }

  @Test
  void emptySets() {
    assertThat(shown("{1} ∪ {}")).isEqualTo("{1}");
    assertThat(shown("{1} ∩ {}")).isEqualTo("{}");
    assertThat(shown("{} \\ {1}")).isEqualTo("{}");
    assertThat(shown("subsets({})")).isEqualTo("{{}}");
    assertThat(shown("cartesian({}, {1})")).isEqualTo("{}");
    assertThat(shown("size({})")).isEqualTo("0");
    assertThat(shown("2 ∈ {}")).isEqualTo("false");
  }

  @Test
  void powerSetsProductsAndSizes() {
    assertThat(shown("subsets({1, 2})")).isEqualTo("{{}, {1}, {1, 2}, {2}}");
    assertThat(shown("size(subsets({1, 2, 3, 4}))")).isEqualTo("16");
    assertThat(shown("cartesian({1, 2}, {a})")).isEqualTo("{⟨1, a⟩, ⟨2, a⟩}");
    assertThat(shown("size({1, 2, 3})")).isEqualTo("3");
    assertThat(shown("{ifactor(12), 12}")).isEqualTo("{12}");
    assertThat(shown("size({ifactor(12), 12})")).isEqualTo("1");
    assertThat(shown("[ifactor(12), 12]")).isEqualTo("⟨2² · 3, 12⟩");
    assertThat(shown("size([{1}, 2])")).isEqualTo("2");
    assertThat(shown("size([1, 2])")).isEqualTo("⟨2⟩");
  }

  @Test
  void membership() {
    assertThat(shown("2 ∈ {1, 2}")).isEqualTo("true");
    assertThat(shown("5 ∈ {1, 2}")).isEqualTo("false");
    assertThat(shown("2.0 ∈ {1, 2}")).isEqualTo("true");
    assertThat(shown("x ∈ {1, 2}")).isEqualTo("x ∈ {1, 2}");
    assertThat(shown("x ∈ {x, 2}")).isEqualTo("true");
    assertThat(shown("[1, 2] ∈ {[1, 2], 3}")).isEqualTo("true");
    assertThat(shown("[1, 2] ∈ {[1, 3]}")).isEqualTo("false");
    assertThat(shown("2 ∈ A")).isEqualTo("2 ∈ A");
    assertThat(shown("2 in {1, 2} ∪ {3}")).isEqualTo("true");
    assertThat(error("2 ∈ 5")).isEqualTo("∈ needs a set or a domain on its right");
    assertThat(error("2 ∈ [1, 2]")).isEqualTo("∈ needs a set or a domain on its right");
    assertThat(shown("2 ∈ ℤ")).isEqualTo("true");
  }

  @Test
  void undecidedItemsLeaveTheCall() {
    assertThat(shown("{x} ∩ {1}")).isEqualTo("{x} ∩ {1}");
    assertThat(shown("{x} \\ {1}")).isEqualTo("{x} \\ {1}");
    assertThat(shown("{x} ∪ {1}")).isEqualTo("{1, x}");
    assertThat(shown("A ∪ B")).isEqualTo("A ∪ B");
    assertThat(shown("(A ∪ B) ∩ C")).isEqualTo("(A ∪ B) ∩ C");
    assertThat(shown("A ∪ (B ∩ C)")).isEqualTo("A ∪ (B ∩ C)");
    assertThat(shown("A \\ (B \\ C)")).isEqualTo("A \\ (B \\ C)");
  }

  @Test
  void theErrors() {
    assertThat(error("{1} ∪ 2")).isEqualTo("union takes sets");
    assertThat(error("subsets([1, 2])")).isEqualTo("subsets takes a set");
    assertThat(error("subsets({1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17})"))
        .isEqualTo("subsets takes a set of at most 16 items");
    assertThat(error("{1} ∪ {2} + 1")).isEqualTo("sets carry no arithmetic");
    assertThat(error("size(3)")).isEqualTo("size needs a vector, a matrix, a list or a set");
  }

  @Test
  void thePrinters() {
    var c = Canonical.of(Parser.parseExpression("(A ∪ B) ∩ C \\ D", name -> false));
    assertThat(UnicodePrinter.INSTANCE.display(c)).isEqualTo("(A ∪ B) ∩ C \\ D");
    assertThat(AsciiPrinter.INSTANCE.display(c)).isEqualTo("intersect(union(A, B), setdiff(C, D))");
    assertThat(LatexPrinter.INSTANCE.display(c)).isEqualTo("(A \\cup B) \\cap C \\setminus D");
    var m = Canonical.of(Parser.parseExpression("x ∈ A ∪ B", name -> false));
    assertThat(UnicodePrinter.INSTANCE.display(m)).isEqualTo("x ∈ A ∪ B");
    assertThat(AsciiPrinter.INSTANCE.display(m)).isEqualTo("x in union(A, B)");
  }
}
