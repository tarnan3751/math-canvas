package mathcanvas.app.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The welcome worksheet's cells, cut from {@code welcome.mc}. */
class WelcomeCellsTest {

  @Test
  void aNoteIsACellOfItsOwnAndTheCodeBetweenNotesIsOne() {
    assertThat(Welcome.cells("# one\nx := 1\n\n# two\na := 2\nb := 3\n# three\n\n\n4"))
        .containsExactly("# one", "x := 1", "# two", "a := 2\nb := 3", "# three", "4");
  }

  @Test
  void theWelcomeHasSixNotesAndItsStepsEndWithABlankCell() {
    List<String> cells = Welcome.cells();
    assertThat(cells.stream().filter(c -> Result.Note.of(c).isPresent())).hasSize(6);
    assertThat(cells)
        .contains(
            "sqrt(8) + 1/3",
            "f(x) := x^2 - 2x",
            "plot(f(x))",
            "a := slider(-3, 3, 1)\nplot(f(x) + a)",
            "steps(solve(f(x) = 0, x))");
    DocumentState s = Welcome.state(DocumentSettings.DEFAULT);
    assertThat(s.cells()).hasSize(cells.size() + 1);
    assertThat(s.cells().getLast().input()).isEmpty();
    assertThat(s.pendingIds()).hasSize(cells.size());
    assertThat(s.canvas().objects()).isEmpty();
  }
}
