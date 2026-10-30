package mathcanvas.app.ui;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Optional;

/**
 * The bracket pairs of a cell's text, for the input editor's highlight: {@code ()}, {@code []},
 * {@code {}} and {@code ⟨⟩}, matched with one stack over all four kinds, so a closing bracket of
 * the wrong kind matches nothing and leaves its opener open.
 */
public final class Brackets {

  private static final String OPEN = "([{⟨";
  private static final String CLOSE = ")]}⟩";

  /**
   * A bracket and its partner.
   *
   * @param at the bracket's index in the text
   * @param partner the index of the bracket it pairs with, or -1 when it is unmatched
   */
  public record Match(int at, int partner) {

    /**
     * Whether the bracket has a partner.
     *
     * @return true if it is matched
     */
    public boolean matched() {
      return partner >= 0;
    }
  }

  private Brackets() {}

  /**
   * Whether a character is a bracket of the four kinds.
   *
   * @param c the character
   * @return true for an opening or a closing bracket
   */
  public static boolean isBracket(char c) {
    return OPEN.indexOf(c) >= 0 || CLOSE.indexOf(c) >= 0;
  }

  /**
   * Whether a character is a closing bracket.
   *
   * @param c the character
   * @return true for {@code ) ] } ⟩}
   */
  public static boolean isClosing(char c) {
    return CLOSE.indexOf(c) >= 0;
  }

  /**
   * The bracket at the caret: the character just before it if that is a bracket, else the one just
   * after it, with its partner.
   *
   * @param text the text
   * @param caret the caret's index, from 0 to the text's length
   * @return the bracket and its partner, or empty when neither neighbour is a bracket
   */
  public static Optional<Match> at(String text, int caret) {
    int at;
    if (caret > 0 && caret <= text.length() && isBracket(text.charAt(caret - 1))) {
      at = caret - 1;
    } else if (caret >= 0 && caret < text.length() && isBracket(text.charAt(caret))) {
      at = caret;
    } else {
      return Optional.empty();
    }
    return Optional.of(new Match(at, partners(text)[at]));
  }

  /**
   * Every bracket's partner.
   *
   * @param text the text
   * @return for each index, the partner's index, or -1 for an unmatched bracket or any other
   *     character
   */
  static int[] partners(String text) {
    int[] out = new int[text.length()];
    Arrays.fill(out, -1);
    Deque<Integer> open = new ArrayDeque<>();
    for (int k = 0; k < text.length(); k++) {
      char c = text.charAt(k);
      if (OPEN.indexOf(c) >= 0) {
        open.push(k);
      } else if (CLOSE.indexOf(c) >= 0
          && !open.isEmpty()
          && OPEN.indexOf(text.charAt(open.peek())) == CLOSE.indexOf(c)) {
        int o = open.pop();
        out[o] = k;
        out[k] = o;
      }
    }
    return out;
  }
}
