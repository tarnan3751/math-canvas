package mathcanvas.engine.parse;

import java.math.BigInteger;
import java.util.Objects;

/**
 * A token with the position of its first code point in the text as typed.
 *
 * @param kind the kind
 * @param text the normalised text: an identifier with its subscripts as {@code _} and ASCII, a
 *     number's digits, a superscript power's integer, the derivative's variable; for an operator or
 *     a symbol, its spelling as typed
 * @param line the line, from 1
 * @param column the column, from 1, in code points
 * @param offset the UTF-16 offset in the text as typed, from 0
 * @param order the derivative's order for {@link TokenKind#DERIVATIVE}, otherwise null
 */
public record Token(
    TokenKind kind, String text, int line, int column, int offset, BigInteger order) {

  /**
   * Checks the operands.
   *
   * @throws NullPointerException if the kind or the text is null
   */
  public Token {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(text, "text");
  }

  /**
   * Whether the token has a kind.
   *
   * @param k the kind
   * @return true if the token is of that kind
   */
  public boolean is(TokenKind k) {
    return kind == k;
  }
}
