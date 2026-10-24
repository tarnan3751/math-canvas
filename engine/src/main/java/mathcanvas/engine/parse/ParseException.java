package mathcanvas.engine.parse;

import java.util.Optional;

/**
 * A parse error ({@code docs/language.md} §16): a message naming what was expected or what is
 * wrong, and the position of the offending token, or of the end of the input for an "expected …"
 * error there. The position refers to the text as typed, before NFC normalisation.
 */
public final class ParseException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final int line;
  private final int column;
  private final int offset;

  /**
   * Creates the error.
   *
   * @param message the message, the gist of §16
   * @param line the line, from 1
   * @param column the column, from 1, in code points
   * @param offset the UTF-16 offset, from 0
   */
  public ParseException(String message, int line, int column, int offset) {
    super(message);
    this.line = line;
    this.column = column;
    this.offset = offset;
  }

  /**
   * Creates the error at a token.
   *
   * @param message the message
   * @param at the offending token
   */
  ParseException(String message, Token at) {
    this(message, at.line(), at.column(), at.offset());
  }

  /**
   * The line, from 1.
   *
   * @return the line
   */
  public int line() {
    return line;
  }

  /**
   * The column, from 1, counted in code points.
   *
   * @return the column
   */
  public int column() {
    return column;
  }

  /**
   * The UTF-16 offset in the text as typed, for a caret.
   *
   * @return the offset, from 0
   */
  public int offset() {
    return offset;
  }

  /**
   * What was expected, when the message says: the text after "expected " ({@code "`)`"}, {@code "an
   * operand"}).
   *
   * @return what was expected, or empty
   */
  public Optional<String> expected() {
    String message = getMessage();
    return message.startsWith("expected ")
        ? Optional.of(message.substring("expected ".length()))
        : Optional.empty();
  }

  @Override
  public String toString() {
    return line + ":" + column + ": " + getMessage();
  }
}
