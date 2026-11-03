package mathcanvas.engine.parse;

import java.math.BigInteger;
import java.text.Normalizer;
import java.util.Arrays;

/**
 * The lexer of {@code docs/language.md} §2 and §3: a pull lexer over source text.
 *
 * <p><b>Normalisation and positions.</b> The text is normalised to NFC in segments: a segment
 * starts at every code point that is not a combining mark (Mn, Mc, Me) or a Hangul medial or final
 * jamo, so each segment holds one starter and the marks that may compose with it, and normalising
 * the segments one by one gives the NFC of the whole text. Each normalised code point keeps the
 * line, column and UTF-16 offset of the code point it came from in the text as typed, or of its
 * segment's start when normalisation changed the segment; tokens begin at starters, so a token's
 * position is the user's own. Columns count code points (a tab is one); a newline is LF, CR LF or
 * CR alone.
 *
 * <p><b>Tokens.</b> Longest match throughout. Identifiers have their subscript runs replaced by
 * {@code _} and ASCII ({@code x₁} is {@code x_1}); {@code in} is the token {@link TokenKind#IN}.
 * The lexer tracks bracket depth over {@code ( [ { ⟨} and emits {@link TokenKind#NEWLINE} only at
 * depth 0; inside brackets a newline is whitespace. Comments run from {@code #} to the end of the
 * line. Reserved words stay identifiers for the parser to classify, so a message names the word
 * as typed. Primes ({@code '} or {@code ′}) right after an identifier join it as {@code '}, so
 * {@code y′′} is the identifier {@code y''} (§5, the prime notation).
 *
 * <p><b>The derivative token</b> (§10.1) is committed once its head has been read: {@code d/d}
 * followed by an identifier, {@code d^}digits{@code /d} followed by an identifier, or {@code d}
 * followed by superscript digits, {@code /d} and an identifier. After the head, anything but the
 * exact shape is the shape error, and unequal or zero orders are the order error. A head that does
 * not match ({@code d / dx}, {@code d^2 + 1}) lexes as ordinary tokens.
 */
public final class Lexer {

  private static final String SHAPE = "the derivative notation is d/dx, d^n/dx^n or dⁿ/dxⁿ";
  private static final String ORDERS = "the orders must be equal positive integers";

  /** The longest numeral or superscript run read: every digit up to this bound is kept. */
  public static final int MAX_DIGITS = 1_000_000;

  private int[] cps = new int[16];
  private int[] lines = new int[16];
  private int[] columns = new int[16];
  private int[] offsets = new int[16];
  private int length;
  private int pos;
  private int depth;

  /**
   * Creates a lexer over source text, normalising it to NFC.
   *
   * @param source the text as typed
   */
  public Lexer(String source) {
    // A byte-order mark at the very start is not part of the text, but positions count it as
    // typed: the first token after it is in column 2, at UTF-16 offset 1.
    boolean bom = source.startsWith("\uFEFF");
    int line = 1;
    int column = bom ? 2 : 1;
    int i = bom ? 1 : 0;
    while (i < source.length()) {
      int start = i;
      i += Character.charCount(source.codePointAt(i));
      while (i < source.length() && !isStarter(source.codePointAt(i))) {
        i += Character.charCount(source.codePointAt(i));
      }
      String segment = source.substring(start, i);
      String normal = Normalizer.normalize(segment, Normalizer.Form.NFC);
      boolean same = normal.equals(segment);
      if (!same) {
        normal.codePoints().forEach(cp -> append(cp, 0, 0, 0));
      }
      int first = length - (int) normal.codePoints().count();
      for (int k = start; k < i; ) {
        int cp = source.codePointAt(k);
        if (same) {
          append(cp, line, column, k);
        } else if (k == start) {
          for (int m = first; m < length; m++) {
            lines[m] = line;
            columns[m] = column;
            offsets[m] = start;
          }
        }
        k += Character.charCount(cp);
        if (cp == '\n' || (cp == '\r' && (k >= source.length() || source.charAt(k) != '\n'))) {
          line++;
          column = 1;
        } else {
          column++;
        }
      }
    }
    append(-1, line, column, source.length());
    length--;
  }

  private void append(int cp, int line, int column, int offset) {
    if (length == cps.length) {
      int grown = cps.length * 2;
      cps = Arrays.copyOf(cps, grown);
      lines = Arrays.copyOf(lines, grown);
      columns = Arrays.copyOf(columns, grown);
      offsets = Arrays.copyOf(offsets, grown);
    }
    cps[length] = cp;
    lines[length] = line;
    columns[length] = column;
    offsets[length] = offset;
    length++;
  }

  /** Whether a code point starts a normalisation segment. */
  private static boolean isStarter(int cp) {
    int type = Character.getType(cp);
    if (type == Character.NON_SPACING_MARK
        || type == Character.ENCLOSING_MARK
        || type == Character.COMBINING_SPACING_MARK) {
      return false;
    }
    return !((cp >= 0x1160 && cp <= 0x11FF) || (cp >= 0xD7B0 && cp <= 0xD7FF));
  }

  /**
   * Returns the next token; {@link TokenKind#EOF} at the end, repeatedly.
   *
   * @return the token
   * @throws ParseException on a lexical error
   */
  public Token next() {
    skipSpace();
    int start = pos;
    if (pos >= length) {
      return token(TokenKind.EOF, "", start);
    }
    int c = cps[pos];
    if (c == '\n' || c == '\r') {
      pos++;
      if (c == '\r' && at(pos) == '\n') {
        pos++;
      }
      return token(TokenKind.NEWLINE, "\n", start);
    }
    if (isDigit(c) || c == '.') {
      return number();
    }
    if (c == 'd') {
      Token derivative = derivative();
      if (derivative != null) {
        return derivative;
      }
    }
    if (isIdentStart(c)) {
      String name = identifier();
      // the prime notation (§5): primes right after a name join it, ′ written as '
      StringBuilder primes = new StringBuilder();
      while (pos < length && (cps[pos] == '\'' || cps[pos] == '′')) {
        primes.append('\'');
        pos++;
      }
      if (!primes.isEmpty()) {
        return token(TokenKind.IDENT, name + primes, start);
      }
      return token(name.equals("in") ? TokenKind.IN : TokenKind.IDENT, name, start);
    }
    if (c == '⁻' || superscriptDigit(c) >= 0) {
      return superscript();
    }
    pos++;
    return switch (c) {
      case '+' -> token(TokenKind.PLUS, "+", start);
      case '-', '−' -> {
        if (c == '-' && at(pos) == '>') {
          pos++;
          yield token(TokenKind.ARROW, "->", start);
        }
        yield token(TokenKind.MINUS, "-", start);
      }
      case '*' -> {
        if (at(pos) == '*') {
          pos++;
          yield token(TokenKind.CARET, "**", start);
        }
        yield token(TokenKind.STAR, "*", start);
      }
      case '·', '⋅' -> token(TokenKind.STAR, new String(Character.toChars(c)), start);
      case '/', '÷' -> token(TokenKind.SLASH, "/", start);
      case '^' -> token(TokenKind.CARET, "^", start);
      case '×' -> token(TokenKind.CROSS, "×", start);
      case '!' -> {
        if (at(pos) == '=') {
          pos++;
          yield token(TokenKind.NE, "!=", start);
        }
        yield token(TokenKind.BANG, "!", start);
      }
      case '%' -> token(TokenKind.PERCENT, "%", start);
      case '°' -> token(TokenKind.DEGREE, "°", start);
      case '′' -> token(TokenKind.MINUTE, "′", start);
      case '″' -> token(TokenKind.SECOND, "″", start);
      case '=' -> {
        int d = at(pos);
        if (d == '=') {
          throw error("use `=`", start);
        }
        if (d == '<') {
          throw error("use `<=`", start);
        }
        if (d == '>') {
          throw error("use `>=`", start);
        }
        yield token(TokenKind.EQ, "=", start);
      }
      case '<' -> {
        if (at(pos) == '=') {
          pos++;
          yield token(TokenKind.LE, "<=", start);
        }
        if (at(pos) == '>') {
          throw error("use `!=`", start);
        }
        yield token(TokenKind.LT, "<", start);
      }
      case '>' -> {
        if (at(pos) == '=') {
          pos++;
          yield token(TokenKind.GE, ">=", start);
        }
        yield token(TokenKind.GT, ">", start);
      }
      case '≤' -> token(TokenKind.LE, "≤", start);
      case '≥' -> token(TokenKind.GE, "≥", start);
      case '≠' -> token(TokenKind.NE, "≠", start);
      case '∈' -> token(TokenKind.IN, "∈", start);
      case '→' -> token(TokenKind.ARROW, "→", start);
      case ':' -> {
        if (at(pos) != '=') {
          throw unknown(c, start);
        }
        pos++;
        yield token(TokenKind.DEFINE, ":=", start);
      }
      case '(' -> open(TokenKind.LPAREN, "(", start);
      case '[' -> open(TokenKind.LBRACKET, "[", start);
      case '{' -> open(TokenKind.LBRACE, "{", start);
      case '⟨' -> open(TokenKind.LANGLE, "⟨", start);
      case ')' -> close(TokenKind.RPAREN, ")", start);
      case ']' -> close(TokenKind.RBRACKET, "]", start);
      case '}' -> close(TokenKind.RBRACE, "}", start);
      case '⟩' -> close(TokenKind.RANGLE, "⟩", start);
      case '|' -> token(TokenKind.BAR, "|", start);
      case ',' -> token(TokenKind.COMMA, ",", start);
      case ';' -> token(TokenKind.SEMI, ";", start);
      case 'π' -> token(TokenKind.PI, "π", start);
      case '∞' -> token(TokenKind.INFINITY, "∞", start);
      case 'ℤ' -> token(TokenKind.DOMAIN, "ℤ", start);
      case 'ℚ' -> token(TokenKind.DOMAIN, "ℚ", start);
      case 'ℝ' -> token(TokenKind.DOMAIN, "ℝ", start);
      case 'ℂ' -> token(TokenKind.DOMAIN, "ℂ", start);
      case '√' -> token(TokenKind.SQRT, "√", start);
      case '∛' -> token(TokenKind.CBRT, "∛", start);
      case '∜' -> token(TokenKind.QRT, "∜", start);
      case '∫' -> token(TokenKind.INTEGRAL, "∫", start);
      case 'Σ', '∑' -> token(TokenKind.SIGMA, new String(Character.toChars(c)), start);
      case 'Π', '∏' -> token(TokenKind.CAPITAL_PI, new String(Character.toChars(c)), start);
      default -> throw unknown(c, start);
    };
  }

  private void skipSpace() {
    while (pos < length) {
      int c = cps[pos];
      if (c == ' ' || c == '\t' || c == ' ') {
        pos++;
      } else if (c == '#') {
        while (pos < length && cps[pos] != '\n' && cps[pos] != '\r') {
          pos++;
        }
      } else if ((c == '\n' || c == '\r') && depth > 0) {
        pos++;
      } else {
        return;
      }
    }
  }

  private Token open(TokenKind kind, String text, int start) {
    depth++;
    return token(kind, text, start);
  }

  private Token close(TokenKind kind, String text, int start) {
    if (depth > 0) {
      depth--;
    }
    return token(kind, text, start);
  }

  private Token number() {
    int start = pos;
    boolean decimal = false;
    while (isDigit(at(pos))) {
      pos++;
    }
    if (at(pos) == '.') {
      if (!isDigit(at(pos + 1))) {
        throw error("expected a digit after `.`", pos);
      }
      decimal = true;
      pos++;
      while (isDigit(at(pos))) {
        pos++;
      }
    }
    if (at(pos) == 'e' || at(pos) == 'E') {
      int j = pos + 1;
      if (at(j) == '+' || at(j) == '-') {
        j++;
      }
      if (isDigit(at(j))) {
        decimal = true;
        pos = j;
        while (isDigit(at(pos))) {
          pos++;
        }
      }
    }
    checkLength(start, pos, start);
    return token(decimal ? TokenKind.DECIMAL : TokenKind.INT, text(start, pos), start);
  }

  /** The derivative token at {@code pos}, which holds {@code d}, or null when no head matches. */
  private Token derivative() {
    int start = pos;
    int p = pos + 1;
    if (at(p) == '/' && at(p + 1) == 'd' && isIdentStart(at(p + 2))) {
      pos = p + 2;
      String var = identifier();
      int next = at(pos);
      if (next == '^' || next == '⁻' || superscriptDigit(next) >= 0) {
        throw error(SHAPE, start);
      }
      return derivativeToken(var, BigInteger.ONE, start);
    }
    if (at(p) == '^' && isDigit(at(p + 1))) {
      int r = p + 1;
      while (isDigit(at(r))) {
        r++;
      }
      if (at(r) == '/' && at(r + 1) == 'd' && isIdentStart(at(r + 2))) {
        checkLength(p + 1, r, start);
        BigInteger order = Numerals.integer(text(p + 1, r));
        pos = r + 2;
        String var = identifier();
        if (at(pos) != '^' || !isDigit(at(pos + 1))) {
          throw error(SHAPE, start);
        }
        int s = pos + 1;
        pos = s;
        while (isDigit(at(pos))) {
          pos++;
        }
        checkLength(s, pos, start);
        return checkedOrders(var, order, Numerals.integer(text(s, pos)), start);
      }
      return null;
    }
    if (superscriptDigit(at(p)) >= 0) {
      int r = p;
      while (superscriptDigit(at(r)) >= 0) {
        r++;
      }
      if (at(r) == '/' && at(r + 1) == 'd' && isIdentStart(at(r + 2))) {
        checkLength(p, r, start);
        BigInteger order = superscriptValue(p, r);
        pos = r + 2;
        String var = identifier();
        if (superscriptDigit(at(pos)) < 0) {
          throw error(SHAPE, start);
        }
        int s = pos;
        while (superscriptDigit(at(pos)) >= 0) {
          pos++;
        }
        checkLength(s, pos, start);
        return checkedOrders(var, order, superscriptValue(s, pos), start);
      }
    }
    return null;
  }

  private Token checkedOrders(String var, BigInteger first, BigInteger second, int start) {
    if (!first.equals(second) || first.signum() <= 0) {
      throw error(ORDERS, start);
    }
    return derivativeToken(var, first, start);
  }

  private Token derivativeToken(String var, BigInteger order, int start) {
    return new Token(
        TokenKind.DERIVATIVE, var, lines[start], columns[start], offsets[start], order);
  }

  /** Refuses a run of digits longer than {@link #MAX_DIGITS}. */
  private void checkLength(int from, int to, int start) {
    if (to - from > MAX_DIGITS) {
      throw error("number out of range", start);
    }
  }

  private BigInteger superscriptValue(int from, int to) {
    StringBuilder digits = new StringBuilder();
    for (int k = from; k < to; k++) {
      digits.append((char) ('0' + superscriptDigit(cps[k])));
    }
    return Numerals.integer(digits.toString());
  }

  private Token superscript() {
    int start = pos;
    StringBuilder value = new StringBuilder();
    if (cps[pos] == '⁻') {
      value.append('-');
      pos++;
    }
    if (superscriptDigit(at(pos)) < 0) {
      throw error("expected superscript digits", start);
    }
    while (superscriptDigit(at(pos)) >= 0) {
      value.append((char) ('0' + superscriptDigit(cps[pos])));
      pos++;
    }
    checkLength(start, pos, start);
    return token(TokenKind.SUPERSCRIPT, value.toString(), start);
  }

  private String identifier() {
    StringBuilder name = new StringBuilder();
    while (pos < length) {
      int c = cps[pos];
      if (subscript(c) != 0) {
        name.append('_');
        while (pos < length && subscript(cps[pos]) != 0) {
          name.append(subscript(cps[pos]));
          pos++;
        }
      } else if (isIdentStart(c) || isDigit(c)) {
        name.appendCodePoint(c);
        pos++;
      } else {
        break;
      }
    }
    return name.toString();
  }

  /** Whether a code point may start an identifier (§3.2). */
  static boolean isIdentStart(int c) {
    if (c == '_') {
      return true;
    }
    if (c == 'π' || c == 'Σ' || c == 'Π' || c == 'ℤ' || c == 'ℚ' || c == 'ℝ' || c == 'ℂ') {
      return false;
    }
    int type = Character.getType(c);
    return type == Character.UPPERCASE_LETTER
        || type == Character.LOWERCASE_LETTER
        || type == Character.TITLECASE_LETTER
        || type == Character.OTHER_LETTER;
  }

  private static boolean isDigit(int c) {
    return c >= '0' && c <= '9';
  }

  /** The ASCII equivalent of a subscript character, or 0. */
  static char subscript(int c) {
    if (c >= 0x2080 && c <= 0x2089) {
      return (char) ('0' + c - 0x2080);
    }
    return switch (c) {
      case 'ₐ' -> 'a';
      case 'ₑ' -> 'e';
      case 'ₒ' -> 'o';
      case 'ₓ' -> 'x';
      case 'ₕ' -> 'h';
      case 'ₖ' -> 'k';
      case 'ₗ' -> 'l';
      case 'ₘ' -> 'm';
      case 'ₙ' -> 'n';
      case 'ₚ' -> 'p';
      case 'ₛ' -> 's';
      case 'ₜ' -> 't';
      case 'ᵢ' -> 'i';
      case 'ᵣ' -> 'r';
      case 'ᵤ' -> 'u';
      case 'ᵥ' -> 'v';
      case 'ⱼ' -> 'j';
      default -> 0;
    };
  }

  /** The value of a superscript digit, or -1. */
  private static int superscriptDigit(int c) {
    return switch (c) {
      case '⁰' -> 0;
      case '¹' -> 1;
      case '²' -> 2;
      case '³' -> 3;
      case '⁴' -> 4;
      case '⁵' -> 5;
      case '⁶' -> 6;
      case '⁷' -> 7;
      case '⁸' -> 8;
      case '⁹' -> 9;
      default -> -1;
    };
  }

  private int at(int index) {
    return index < length ? cps[index] : -1;
  }

  private String text(int from, int to) {
    return new String(cps, from, to - from);
  }

  private Token token(TokenKind kind, String text, int start) {
    return new Token(kind, text, lines[start], columns[start], offsets[start], null);
  }

  private ParseException error(String message, int at) {
    return new ParseException(message, lines[at], columns[at], offsets[at]);
  }

  private ParseException unknown(int c, int at) {
    int type = Character.getType(c);
    boolean visible =
        !(type == Character.CONTROL
            || type == Character.FORMAT
            || type == Character.SPACE_SEPARATOR
            || type == Character.LINE_SEPARATOR
            || type == Character.PARAGRAPH_SEPARATOR
            || type == Character.UNASSIGNED
            || type == Character.SURROGATE
            || type == Character.PRIVATE_USE
            || type == Character.NON_SPACING_MARK
            || type == Character.ENCLOSING_MARK
            || type == Character.COMBINING_SPACING_MARK);
    String name =
        visible ? "`" + new String(Character.toChars(c)) + "`" : String.format("U+%04X", c);
    return error("unknown operator " + name, at);
  }
}
