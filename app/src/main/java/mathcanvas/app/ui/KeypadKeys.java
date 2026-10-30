package mathcanvas.app.ui;

import java.util.List;

/**
 * The keypad's keys and what each inserts, apart from the keypad's nodes so that the tests read
 * them without a toolkit: the language's ASCII forms ({@code docs/language.md}).
 */
public final class KeypadKeys {

  private KeypadKeys() {}

  /**
   * What a key inserts.
   *
   * @param id the key's name: its label is the bundle's {@code key.<id>}, its node id {@code
   *     key-<id>}
   * @param text the text inserted
   * @param from where the selection starts in the text after inserting
   * @param to where it ends
   */
  public record Key(String id, String text, int from, int to) {

    /** A key that inserts its text with the caret after it. */
    static Key text(String id, String text) {
      return new Key(id, text, text.length(), text.length());
    }

    /** A call: its name and parentheses, the caret between them. */
    static Key call(String name) {
      return new Key(name, name + "()", name.length() + 1, name.length() + 1);
    }

    /** A template whose placeholder, the first occurrence of {@code placeholder}, is selected. */
    static Key template(String id, String text, String placeholder) {
      int at = text.indexOf(placeholder);
      return new Key(id, text, at, at + placeholder.length());
    }
  }

  /**
   * Every key after the shift toggles, the trigonometric keys and {@code log}, in grid order.
   *
   * @return the keys
   */
  public static List<Key> keys() {
    return List.of(
        Key.call("ln"),
        Key.call("exp"),
        Key.call("sqrt"),
        Key.text("square", "^2"),
        Key.text("power", "^"),
        Key.call("abs"),
        Key.text("7", "7"),
        Key.text("8", "8"),
        Key.text("9", "9"),
        Key.text("divide", "/"),
        Key.text("open", "("),
        Key.text("close", ")"),
        Key.text("4", "4"),
        Key.text("5", "5"),
        Key.text("6", "6"),
        Key.text("times", "*"),
        Key.text("bracket-open", "["),
        Key.text("bracket-close", "]"),
        Key.text("1", "1"),
        Key.text("2", "2"),
        Key.text("3", "3"),
        Key.text("minus", "-"),
        Key.text("brace-open", "{"),
        Key.text("brace-close", "}"),
        Key.text("0", "0"),
        Key.text("point", "."),
        Key.text("comma", ", "),
        Key.text("plus", "+"),
        Key.text("equals", " = "),
        Key.text("define", " := "),
        Key.text("pi", "pi"),
        Key.text("e", "e"),
        Key.text("i", "i"),
        Key.text("inf", "inf"),
        Key.text("arrow", " -> "),
        Key.text("factorial", "!"),
        Key.text("x", "x"),
        Key.text("y", "y"),
        Key.text("t", "t"),
        Key.text("percent", "%"),
        Key.text("degree", "°"),
        Key.call("floor"),
        Key.call("ceil"),
        Key.call("round"),
        Key.template("sum", "sum(f, k, 1, n)", "f"),
        Key.template("integral", "integrate(f, x)", "f"),
        Key.template("derivative", "diff(f, x)", "f"),
        Key.call("simplify"),
        Key.call("expand"),
        Key.call("factor"),
        Key.template("solve", "solve(e, x)", "e"),
        Key.call("numeric"),
        Key.call("steps"));
  }
}
