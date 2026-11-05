package mathcanvas.app.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * A message as the window shows it. The engine writes its messages in lowercase, with code quoted
 * in backticks ({@code `sin` takes 1 argument}), as the CLI prints them; the window starts a
 * message with a capital, unless it starts with code, and shows each quoted span in the Input type
 * without its backticks. The accessible text is the plain words, without backticks.
 */
final class MessageText {

  /**
   * A run of a message: plain words, or code that was quoted in backticks.
   *
   * @param text the run's text, without backticks
   * @param code whether it is code
   */
  record Span(String text, boolean code) {}

  private MessageText() {}

  /**
   * The message with a leading lowercase letter capitalised; one that starts with a backtick, or
   * with anything but a lowercase letter, is unchanged, as is everything after its first character.
   *
   * @param message the message
   * @return the message as the window starts it
   */
  static String capitalised(String message) {
    if (message.isEmpty()) {
      return message;
    }
    int first = message.codePointAt(0);
    if (!Character.isLowerCase(first)) {
      return message;
    }
    return new StringBuilder()
        .appendCodePoint(Character.toUpperCase(first))
        .append(message, Character.charCount(first), message.length())
        .toString();
  }

  /**
   * The message's runs: the text between a backtick and the next is code; a backtick with no
   * partner after it is kept as it is, in the plain words.
   *
   * @param message the message
   * @return the runs, in order, none empty
   */
  static List<Span> spans(String message) {
    List<Span> out = new ArrayList<>();
    int at = 0;
    while (at < message.length()) {
      int open = message.indexOf('`', at);
      int close = open < 0 ? -1 : message.indexOf('`', open + 1);
      if (close < 0) {
        out.add(new Span(message.substring(at), false));
        break;
      }
      if (open > at) {
        out.add(new Span(message.substring(at, open), false));
      }
      if (close > open + 1) {
        out.add(new Span(message.substring(open + 1, close), true));
      }
      at = close + 1;
    }
    return out;
  }

  /**
   * The message's words without its backticks: its text for reading aloud and for the tests.
   *
   * @param message the message
   * @return the plain text
   */
  static String plain(String message) {
    StringBuilder out = new StringBuilder();
    for (Span s : spans(message)) {
      out.append(s.text());
    }
    return out.toString();
  }

  /**
   * The message as text nodes in a flow: the plain words with the style class {@code message-text},
   * the code with {@code message-code} as well, in the Input type.
   *
   * @param message the message, as it is to be shown (capitalised or not)
   * @return the flow, read as the plain words
   */
  static TextFlow flow(String message) {
    TextFlow flow = new TextFlow();
    for (Span s : spans(message)) {
      Text t = new Text(s.text());
      t.getStyleClass().add("message-text");
      if (s.code()) {
        t.getStyleClass().add("message-code");
      }
      flow.getChildren().add(t);
    }
    flow.getStyleClass().add("message");
    flow.setAccessibleText(plain(message));
    return flow;
  }
}
