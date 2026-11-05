package mathcanvas.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Messages as the window shows them: capitalised, with backticked code split out. */
class MessageTextTest {

  @Test
  void aLeadingLowercaseLetterIsCapitalisedAndNothingElse() {
    assertThat(MessageText.capitalised("in `f`, line 3: x is not defined"))
        .isEqualTo("In `f`, line 3: x is not defined");
    assertThat(MessageText.capitalised("`sin` takes 1 argument"))
        .isEqualTo("`sin` takes 1 argument");
    assertThat(MessageText.capitalised("Already capital")).isEqualTo("Already capital");
    assertThat(MessageText.capitalised("3 arguments")).isEqualTo("3 arguments");
    assertThat(MessageText.capitalised("é accentué")).isEqualTo("É accentué");
    assertThat(MessageText.capitalised("")).isEmpty();
  }

  @Test
  void backticksSplitCodeFromWords() {
    assertThat(MessageText.spans("`sin` takes 1 argument; `help(sin)` shows examples"))
        .containsExactly(
            new MessageText.Span("sin", true),
            new MessageText.Span(" takes 1 argument; ", false),
            new MessageText.Span("help(sin)", true),
            new MessageText.Span(" shows examples", false));
    assertThat(MessageText.spans("no code here"))
        .containsExactly(new MessageText.Span("no code here", false));
    // a backtick with no partner stays a backtick
    assertThat(MessageText.spans("a ` alone"))
        .containsExactly(new MessageText.Span("a ` alone", false));
    assertThat(MessageText.spans("`a` and ` b"))
        .containsExactly(new MessageText.Span("a", true), new MessageText.Span(" and ` b", false));
    assertThat(MessageText.spans("``")).isEqualTo(List.of());
  }

  @Test
  void thePlainWordsDropTheBackticks() {
    assertThat(MessageText.plain("In `f`, line 3: `x` is not defined"))
        .isEqualTo("In f, line 3: x is not defined");
  }
}
