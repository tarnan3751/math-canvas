package mathcanvas.engine.parse;

/**
 * A place in source text as typed ({@code docs/language.md} §16): the first code point of a token.
 *
 * @param line the line, from 1
 * @param column the column, from 1, in code points
 * @param offset the UTF-16 offset in the text as typed, from 0
 */
public record Position(int line, int column, int offset) {

  /**
   * The position of a token.
   *
   * @param t the token
   * @return its position
   */
  static Position of(Token t) {
    return new Position(t.line(), t.column(), t.offset());
  }
}
