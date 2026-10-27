package mathcanvas.engine.print;

/**
 * The caret line that points at a column of a source line, under an error message, as the CLI and
 * the worksheet show it.
 */
public final class Caret {

  private Caret() {}

  /**
   * The line that puts a caret under a column of a source line: a space for each code point before
   * the column, except that a tab of the source is copied so the caret lines up, then {@code ^}. A
   * column past the end of the line is reached with spaces.
   *
   * @param source the source line
   * @param column the column, from 1, in code points
   * @return the caret line, without a line end
   */
  public static String under(String source, int column) {
    StringBuilder caret = new StringBuilder();
    int[] cps = source.codePoints().toArray();
    for (int k = 0; k < column - 1; k++) {
      caret.append(k < cps.length && cps[k] == '\t' ? '\t' : ' ');
    }
    return caret.append('^').toString();
  }
}
