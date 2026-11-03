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
  void aBlockIsOneUnitToItsEnd() {
    assertThat(Units.of("x\nfor k in 1..2\n  print(k)\nend\ny"))
        .containsExactly(
            new Units.Unit("x\n", 1),
            new Units.Unit("for k in 1..2\n  print(k)\nend\n", 2),
            new Units.Unit("y", 5));
    assertThat(Units.of("function f(x)\n  if x > 0 then\n    1\n  end\nend\n"))
        .containsExactly(
            new Units.Unit("function f(x)\n  if x > 0 then\n    1\n  end\nend\n", 1),
            new Units.Unit("", 6));
    // a one-line if closes at its line's end; a block never closed runs to the end
    assertThat(Units.of("if p then 1\n2")).hasSize(2);
    assertThat(Units.of("while p\n1\n2")).containsExactly(new Units.Unit("while p\n1\n2", 1));
  }

  @Test
  void openTellsWhetherABracketOrABlockIsStillOpen() {
    assertThat(Units.open("for k in 1..2\n")).isTrue();
    assertThat(Units.open("for k in 1..2\nk\nend\n")).isFalse();
    assertThat(Units.open("if p then\n")).isTrue();
    assertThat(Units.open("if p then 1\n")).isFalse();
    assertThat(Units.open("[1,\n")).isTrue();
    assertThat(Units.open("f(⟨1,\n")).isTrue();
    assertThat(Units.open("[1, 2]\n")).isFalse();
    assertThat(Units.open("1)\n")).isFalse();
    assertThat(Units.open("# (\n")).isFalse();
    assertThat(Units.open("(a ⊗\n")).isFalse();
  }
}
