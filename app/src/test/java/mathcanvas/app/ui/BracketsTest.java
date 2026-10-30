package mathcanvas.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BracketsTest {

  @Test
  void theBracketBeforeTheCaretComesFirst() {
    String t = "f(x) + (y)";

    assertThat(Brackets.at(t, 4)).contains(new Brackets.Match(3, 1));
    assertThat(Brackets.at(t, 1)).contains(new Brackets.Match(1, 3));
    assertThat(Brackets.at(t, 7)).contains(new Brackets.Match(7, 9));
    assertThat(Brackets.at(t, 8)).contains(new Brackets.Match(7, 9));
    assertThat(Brackets.at(t, 5)).isEmpty();
  }

  @Test
  void allFourKindsNest() {
    String t = "{[(⟨a⟩)]}";

    for (int k = 0; k < 4; k++) {
      assertThat(Brackets.partners(t)[k]).isEqualTo(t.length() - 1 - k);
    }
  }

  @Test
  void aWrongOrMissingPartnerIsUnmatched() {
    assertThat(Brackets.at("(]", 1)).contains(new Brackets.Match(0, -1));
    assertThat(Brackets.at("(]", 2)).contains(new Brackets.Match(1, -1));
    assertThat(Brackets.at("((x)", 1)).contains(new Brackets.Match(0, -1));
    assertThat(Brackets.at("x)", 2).orElseThrow().matched()).isFalse();
    assertThat(Brackets.isClosing(')')).isTrue();
    assertThat(Brackets.isClosing('(')).isFalse();
  }
}
