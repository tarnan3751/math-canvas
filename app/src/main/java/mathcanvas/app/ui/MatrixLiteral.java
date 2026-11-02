package mathcanvas.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The matrix and vector literals of a cell's text, for the matrix editor: found by the editor's own
 * bracket scan ({@link Brackets}), not the engine's parser, and written back as the language's
 * literal ({@code docs/language.md} §13.2). A literal is a balanced {@code […]} group whose items
 * are comma-separated: a vector when no item is itself wholly a {@code […]} group, a matrix when
 * every item is one (a row) and no row's item is. A {@code [} after an operand or a head, spaces
 * and tabs between them allowed (a letter, a digit, {@code _}, {@code '}, a closing bracket, a
 * subscript or superscript character, {@code ! % °}, or {@code ∑ ∏ ∫}), is indexing or the bounds
 * of a sum, a product or an integral, never a literal.
 */
public final class MatrixLiteral {

  /**
   * A literal in a text.
   *
   * @param from the index of its opening bracket
   * @param to the index after its closing bracket
   * @param vector whether it is a vector, each row then one cell
   * @param rows the cells' texts, stripped, row by row; a matrix's rows may be ragged
   */
  public record Literal(int from, int to, boolean vector, List<List<String>> rows) {

    /**
     * The number of columns: the longest row's length.
     *
     * @return the columns
     */
    public int columns() {
      return rows.stream().mapToInt(List::size).max().orElse(0);
    }
  }

  private MatrixLiteral() {}

  /**
   * The literal the caret is inside: the innermost one enclosing it, or the matrix whose row it is.
   *
   * @param text the text
   * @param caret the caret's index, from 0 to the text's length
   * @return the literal, or empty when the caret is inside none
   */
  public static Optional<Literal> at(String text, int caret) {
    int[] partners = Brackets.partners(text);
    List<Literal> enclosing = new ArrayList<>();
    for (int o = Math.min(caret, text.length()) - 1; o >= 0; o--) {
      if (text.charAt(o) == '[' && partners[o] >= caret) {
        literal(text, partners, o).ifPresent(enclosing::add);
      }
    }
    if (enclosing.isEmpty()) {
      return Optional.empty();
    }
    Literal inner = enclosing.getFirst();
    if (inner.vector() && enclosing.size() > 1) {
      Literal outer = enclosing.get(1);
      if (!outer.vector() && rowStarts(text, partners, outer.from()).contains(inner.from())) {
        return Optional.of(outer);
      }
    }
    return Optional.of(inner);
  }

  /**
   * Writes the literal of a grid of cells: {@code [a, b, c]} for a vector, {@code [[a, b], [c, d]]}
   * for a matrix; an empty cell is {@code 0}.
   *
   * @param rows the cells' texts, row by row, every row of one length (one cell for a vector)
   * @param vector whether to write a vector
   * @return the literal
   */
  public static String write(List<List<String>> rows, boolean vector) {
    List<String> written = new ArrayList<>();
    for (List<String> row : rows) {
      List<String> cells = row.stream().map(c -> c.isBlank() ? "0" : c.strip()).toList();
      written.add(vector ? String.join(", ", cells) : "[" + String.join(", ", cells) + "]");
    }
    return "[" + String.join(", ", written) + "]";
  }

  /** The literal of the group opening at {@code o}, if it is one. */
  private static Optional<Literal> literal(String text, int[] partners, int o) {
    if (afterOperand(text, o)) {
      return Optional.empty();
    }
    int close = partners[o];
    List<int[]> items = items(text, partners, o + 1, close);
    if (items == null || text.substring(o + 1, close).isBlank()) {
      return Optional.empty();
    }
    List<int[]> rows = new ArrayList<>();
    for (int[] item : items) {
      int[] row = group(text, partners, item);
      if (row != null) {
        rows.add(row);
      }
    }
    if (rows.isEmpty()) {
      return Optional.of(new Literal(o, close + 1, true, cells(text, items, true)));
    }
    if (rows.size() < items.size()) {
      return Optional.empty();
    }
    List<List<String>> matrix = new ArrayList<>();
    for (int[] row : rows) {
      List<int[]> cells = items(text, partners, row[0] + 1, row[1] - 1);
      if (cells == null) {
        return Optional.empty();
      }
      if (text.substring(row[0] + 1, row[1] - 1).isBlank()) {
        matrix.add(List.of());
        continue;
      }
      for (int[] cell : cells) {
        if (group(text, partners, cell) != null) {
          return Optional.empty();
        }
      }
      matrix.add(cells(text, cells, false).getFirst());
    }
    Literal literal = new Literal(o, close + 1, false, matrix);
    return literal.columns() == 0 ? Optional.empty() : Optional.of(literal);
  }

  /** The characters an operand can end with or a head can be, besides letters and digits. */
  private static final String OPERAND_ENDS = "_'!%°²³¹∑∏∫";

  /** Whether the bracket at {@code o} follows an operand or a head, past spaces and tabs. */
  private static boolean afterOperand(String text, int o) {
    int k = o - 1;
    while (k >= 0 && (text.charAt(k) == ' ' || text.charAt(k) == '\t')) {
      k--;
    }
    if (k < 0) {
      return false;
    }
    char c = text.charAt(k);
    return Character.isLetterOrDigit(c)
        || OPERAND_ENDS.indexOf(c) >= 0
        || (c >= '\u2070' && c <= '\u209f') // superscripts and subscripts
        || Brackets.isClosing(c);
  }

  /**
   * The items between {@code from} and {@code to}, split at the commas outside any bracket, each as
   * its {@code [start, end)}; null when a bracket among them is unmatched.
   */
  private static List<int[]> items(String text, int[] partners, int from, int to) {
    List<int[]> out = new ArrayList<>();
    int start = from;
    for (int k = from; k < to; k++) {
      char c = text.charAt(k);
      if (Brackets.isBracket(c)) {
        if (partners[k] < 0 || partners[k] < k) {
          return null;
        }
        k = partners[k];
      } else if (c == ',') {
        out.add(new int[] {start, k});
        start = k + 1;
      }
    }
    out.add(new int[] {start, to});
    return out;
  }

  /** The item's {@code [start, end)} when it is wholly a {@code […]} group, else null. */
  private static int[] group(String text, int[] partners, int[] item) {
    int start = item[0];
    int end = item[1];
    while (start < end && Character.isWhitespace(text.charAt(start))) {
      start++;
    }
    while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
      end--;
    }
    return start < end && text.charAt(start) == '[' && partners[start] == end - 1
        ? new int[] {start, end}
        : null;
  }

  /** The rows' opening brackets of the matrix opening at {@code o}. */
  private static List<Integer> rowStarts(String text, int[] partners, int o) {
    List<Integer> out = new ArrayList<>();
    for (int[] item : items(text, partners, o + 1, partners[o])) {
      out.add(group(text, partners, item)[0]);
    }
    return out;
  }

  /** The items' stripped texts: one row of them, or one row each for a vector. */
  private static List<List<String>> cells(String text, List<int[]> items, boolean vector) {
    List<String> texts = items.stream().map(i -> text.substring(i[0], i[1]).strip()).toList();
    return vector ? texts.stream().map(List::of).toList() : List.of(texts);
  }
}
