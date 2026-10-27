package mathcanvas.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShownTest {

  @Test
  void aShortTextIsShownWhole() {
    String text = "x".repeat(Shown.MAX_SHOWN);

    assertThat(Shown.capped(text)).isSameAs(text);
  }

  @Test
  void aLongTextIsCutWithANoteOfTheRest() {
    String text = "π".repeat(Shown.MAX_SHOWN) + "𝑥".repeat(12_345);

    assertThat(Shown.capped(text))
        .isEqualTo("π".repeat(Shown.MAX_SHOWN) + " … (12,345 more characters)");
  }
}
