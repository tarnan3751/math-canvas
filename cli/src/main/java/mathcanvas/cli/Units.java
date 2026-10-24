package mathcanvas.cli;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.parse.Lexer;
import mathcanvas.engine.parse.ParseException;
import mathcanvas.engine.parse.Token;
import mathcanvas.engine.parse.TokenKind;

/**
 * Source text cut into units, the pieces the CLI parses whole and then runs: a unit is a line, or
 * the lines an open bracket joins ({@code docs/language.md} §2). A unit ends where the lexer emits
 * a newline outside brackets, so it begins at the start of a line and its statements are complete.
 * A lexical error ends its unit at the error's line, and the next unit starts on the line after, so
 * a script's later lines still run, or are checked, after a bad one.
 */
final class Units {

  /**
   * One unit.
   *
   * @param text the unit's text, line ends included
   * @param line the line of the source it begins on, from 1
   */
  record Unit(String text, int line) {}

  private Units() {}

  /**
   * Cuts source text into units.
   *
   * @param source the text
   * @return the units, in order, covering the whole text
   */
  static List<Unit> of(String source) {
    List<Integer> starts = lineStarts(source);
    List<Unit> units = new ArrayList<>();
    int first = 1;
    int base = 0;
    Lexer lexer = new Lexer(source);
    while (first <= starts.size()) {
      int last;
      boolean restart = false;
      try {
        Token t = lexer.next();
        if (t.is(TokenKind.EOF)) {
          last = starts.size();
        } else if (t.is(TokenKind.NEWLINE)) {
          last = base + t.line();
        } else {
          continue;
        }
      } catch (ParseException e) {
        last = base + e.line();
        restart = true;
      }
      units.add(unit(source, starts, first, last));
      first = last + 1;
      if (restart && first <= starts.size()) {
        base = first - 1;
        lexer = new Lexer(source.substring(starts.get(base)));
      }
    }
    return units;
  }

  /**
   * Whether text leaves a bracket open at its end, so that more lines must be joined to it before
   * it is a unit. A lexical error closes the text: it is a unit, which fails to parse.
   *
   * @param text the lines so far
   * @return true if a bracket is still open
   */
  static boolean open(String text) {
    Lexer lexer = new Lexer(text);
    int depth = 0;
    try {
      for (Token t = lexer.next(); !t.is(TokenKind.EOF); t = lexer.next()) {
        switch (t.kind()) {
          case LPAREN, LBRACKET, LBRACE, LANGLE -> depth++;
          case RPAREN, RBRACKET, RBRACE, RANGLE -> depth = Math.max(0, depth - 1);
          default -> {}
        }
      }
    } catch (ParseException e) {
      return false;
    }
    return depth > 0;
  }

  /** The unit of lines {@code first} to {@code last}, from 1. */
  private static Unit unit(String source, List<Integer> starts, int first, int last) {
    int from = starts.get(first - 1);
    int to = last < starts.size() ? starts.get(last) : source.length();
    return new Unit(source.substring(from, to), first);
  }

  /** The offset of each line's start; a line ends at LF, CR LF or CR, as the lexer counts. */
  static List<Integer> lineStarts(String source) {
    List<Integer> starts = new ArrayList<>(List.of(0));
    for (int i = 0; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c == '\n' || (c == '\r' && (i + 1 >= source.length() || source.charAt(i + 1) != '\n'))) {
        starts.add(i + 1);
      }
    }
    return starts;
  }
}
