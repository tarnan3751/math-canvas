package mathcanvas.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnitsTest {

  @Test
  void aUnitIsALineOrTheLinesABracketJoins() {
    assertThat(Units.of("a := 1; b\n# c (\n[1,\n 2]\r\n\r3"))
        .containsExactly(
            new Units.Unit("a := 1; b\n", 1),
            new Units.Unit("# c (\n", 2),
            new Units.Unit("[1,\n 2]\r\n", 3),
            new Units.Unit("\r", 5),
            new Units.Unit("3", 6));
    assertThat(Units.of("")).containsExactly(new Units.Unit("", 1));
    assertThat(Units.of("x\n")).containsExactly(new Units.Unit("x\n", 1), new Units.Unit("", 2));
  }

  @Test
  void anOpenBracketAtTheEndIsOneUnit() {
    assertThat(Units.of("1\n(2,\n3\n"))
        .containsExactly(new Units.Unit("1\n", 1), new Units.Unit("(2,\n3\n", 2));
  }

  @Test
  void aLexicalErrorEndsItsUnitAndTheNextLineStartsAnother() {
    assertThat(Units.of("(a ⊗\nb)\nc 5.\nd"))
        .containsExactly(
            new Units.Unit("(a ⊗\n", 1),
            new Units.Unit("b)\n", 2),
            new Units.Unit("c 5.\n", 3),
            new Units.Unit("d", 4));
  }

  @Test
  void openTellsWhetherABracketIsStillOpen() {
    assertThat(Units.open("[1,\n")).isTrue();
    assertThat(Units.open("f(⟨1,\n")).isTrue();
    assertThat(Units.open("[1, 2]\n")).isFalse();
    assertThat(Units.open("1)\n")).isFalse();
    assertThat(Units.open("# (\n")).isFalse();
    assertThat(Units.open("(a ⊗\n")).isFalse();
  }
}
