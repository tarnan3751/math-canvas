package mathcanvas.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The data editor's text: the definitions written and read back. */
class DataTableTest {

  @Test
  void oneColumnIsAList() {
    DataTable.Table t =
        new DataTable.Table(
            "data", List.of("x"), List.of(List.of("1"), List.of(" 2.5 "), List.of("")));
    assertThat(DataTable.write(t)).isEqualTo("data := [1, 2.5]");
    assertThat(DataTable.read("data := [1, 2.5]"))
        .contains(new DataTable.Table("data", List.of(""), List.of(List.of("1"), List.of("2.5"))));
  }

  @Test
  void severalColumnsAreAMatrixOfRecordsAndAListPerNamedColumn() {
    DataTable.Table t =
        new DataTable.Table(
            "pts",
            List.of("x", "height (cm)", "y"),
            List.of(List.of("1", "", "2"), List.of("", "", ""), List.of("3", "5", "4")));
    String text = DataTable.write(t);
    assertThat(text).isEqualTo("pts := [[1, 0, 2], [3, 5, 4]]\nx := [1, 3]\ny := [2, 4]");
    // read back: the named columns found by their values, the blank cell now 0
    assertThat(DataTable.read(text))
        .contains(
            new DataTable.Table(
                "pts",
                List.of("x", "", "y"),
                List.of(List.of("1", "0", "2"), List.of("3", "5", "4"))));
  }

  @Test
  void aHeaderThatIsTheTablesNameOrTakenOrABuiltinIsNoDefinition() {
    DataTable.Table t =
        new DataTable.Table(
            "d", List.of("d", "sin", "a", "a"), List.of(List.of("1", "2", "3", "4")));
    assertThat(DataTable.write(t)).isEqualTo("d := [[1, 2, 3, 4]]\na := [3]");
  }

  @Test
  void anythingElseIsNoTable() {
    for (String text :
        List.of(
            "",
            "1 + 2",
            "data := 5",
            "data := [1, 2] + 1",
            "sin := [1, 2]",
            "data := [1, 2]\nx := [1, 2]",
            "m := [[1, 2], [3, 4]]\nx := [1, 5]",
            "m := [[1, 2], [3, 4]]\nx := 3",
            "m := [[1, 2, 3, 4, 5, 6, 7, 8, 9]]")) {
      assertThat(DataTable.read(text)).as(text).isEmpty();
    }
    assertThat(DataTable.read("data := []"))
        .contains(new DataTable.Table("data", List.of(""), List.of()));
    // ragged rows are padded
    assertThat(DataTable.read("m := [[1, 2], [3]]").orElseThrow().rows())
        .containsExactly(List.of("1", "2"), List.of("3", ""));
  }
}
