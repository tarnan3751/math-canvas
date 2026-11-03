package mathcanvas.app.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mathcanvas.canvas.model.Slider;

/**
 * The text of a data table, for the data editor (Phase 8c): the definitions a cell holds for it,
 * written and read back by text alone, as {@link MatrixLiteral} reads a literal. One column is
 * {@code name := [v₁, v₂, …]}; several are {@code name := [[c₁, c₂, …], …]}, a matrix with a row
 * per record, followed by {@code h := [ … ]} on a line of its own for each column whose header is a
 * variable name (other than the table's and the other headers'). A row with every cell blank is
 * left out; a blank cell in another row is written {@code 0}, as the matrix editor writes one.
 */
public final class DataTable {

  /** The most columns a table has. */
  public static final int MAX_COLUMNS = 8;

  /** The most rows a table has. */
  public static final int MAX_ROWS = 10_000;

  /** The name a new table is given. */
  public static final String DEFAULT_NAME = "data";

  /**
   * A table.
   *
   * @param name the name it is defined as
   * @param headers the columns' headers, one per column, blank for none
   * @param rows the cells' texts, row by row, each as long as the headers
   */
  public record Table(String name, List<String> headers, List<List<String>> rows) {

    /**
     * Copies the lists.
     *
     * @throws IllegalArgumentException if there is no column or a row's length differs
     */
    public Table {
      headers = List.copyOf(headers);
      rows = rows.stream().map(List::copyOf).toList();
      if (headers.isEmpty() || headers.size() > MAX_COLUMNS) {
        throw new IllegalArgumentException("a table has 1 to " + MAX_COLUMNS + " columns");
      }
      for (List<String> row : rows) {
        if (row.size() != headers.size()) {
          throw new IllegalArgumentException("a row is as long as the headers");
        }
      }
    }

    /**
     * A new table: one column, one blank row.
     *
     * @return the table
     */
    public static Table empty() {
      return new Table(DEFAULT_NAME, List.of(""), List.of(List.of("")));
    }
  }

  private static final Pattern DEFINITION =
      Pattern.compile("\\s*(\\S+?)\\s*:=\\s*(\\[.*])\\s*;?\\s*", Pattern.DOTALL);

  private DataTable() {}

  /**
   * The definitions of a table.
   *
   * @param t the table, its name a variable name
   * @return the text, a line per definition
   */
  public static String write(Table t) {
    List<List<String>> rows = new ArrayList<>();
    for (List<String> row : t.rows()) {
      if (row.stream().anyMatch(c -> !c.isBlank())) {
        rows.add(row.stream().map(c -> c.isBlank() ? "0" : c.strip()).toList());
      }
    }
    int columns = t.headers().size();
    StringBuilder out = new StringBuilder();
    if (columns == 1) {
      List<String> values = rows.stream().map(List::getFirst).toList();
      return out.append(t.name())
          .append(" := [")
          .append(String.join(", ", values))
          .append(']')
          .toString();
    }
    List<String> written = rows.stream().map(r -> "[" + String.join(", ", r) + "]").toList();
    out.append(t.name()).append(" := [").append(String.join(", ", written)).append(']');
    Set<String> taken = new HashSet<>(Set.of(t.name()));
    for (int c = 0; c < columns; c++) {
      String header = t.headers().get(c).strip();
      if (Slider.isVariableName(header) && taken.add(header)) {
        int column = c;
        out.append('\n')
            .append(header)
            .append(" := [")
            .append(String.join(", ", rows.stream().map(r -> r.get(column)).toList()))
            .append(']');
      }
    }
    return out.toString();
  }

  /**
   * The table a cell's text defines, if it holds nothing else: a first line {@code name := […]}
   * with a vector or a matrix of at most {@link #MAX_COLUMNS} columns and {@link #MAX_ROWS} rows,
   * and after a matrix, lines {@code h := […]} each repeating one of its columns, which names it.
   *
   * @param text the cell's text
   * @return the table, or empty when the text is anything else
   */
  public static Optional<Table> read(String text) {
    List<String> lines = text.lines().filter(l -> !l.isBlank()).toList();
    if (lines.isEmpty()) {
      return Optional.empty();
    }
    Optional<Definition> first = definition(lines.getFirst());
    if (first.isEmpty() || !Slider.isVariableName(first.get().name())) {
      return Optional.empty();
    }
    List<List<String>> rows = first.get().rows();
    boolean vector = first.get().vector();
    int columns = rows.stream().mapToInt(List::size).max().orElse(1);
    if (columns > MAX_COLUMNS || rows.size() > MAX_ROWS || vector && lines.size() > 1) {
      return Optional.empty();
    }
    List<List<String>> padded = new ArrayList<>();
    for (List<String> row : rows) {
      List<String> r = new ArrayList<>(row);
      while (r.size() < columns) {
        r.add("");
      }
      padded.add(r);
    }
    List<String> headers = new ArrayList<>();
    for (int c = 0; c < columns; c++) {
      headers.add("");
    }
    for (String line : lines.subList(1, lines.size())) {
      Optional<Definition> d = definition(line);
      if (d.isEmpty() || !d.get().vector()) {
        return Optional.empty();
      }
      List<String> values = d.get().rows().stream().map(List::getFirst).toList();
      int named = -1;
      for (int c = 0; c < columns && named < 0; c++) {
        int column = c;
        if (headers.get(c).isEmpty()
            && values.equals(padded.stream().map(r -> r.get(column)).toList())) {
          named = c;
        }
      }
      if (named < 0) {
        return Optional.empty();
      }
      headers.set(named, d.get().name());
    }
    return Optional.of(new Table(first.get().name(), headers, padded));
  }

  /** One definition of a literal: its name, whether a vector, and its cells row by row. */
  private record Definition(String name, boolean vector, List<List<String>> rows) {}

  private static Optional<Definition> definition(String line) {
    Matcher m = DEFINITION.matcher(line);
    if (!m.matches()) {
      return Optional.empty();
    }
    String literal = m.group(2);
    if (literal.substring(1, literal.length() - 1).isBlank()) {
      return Optional.of(new Definition(m.group(1), true, List.of()));
    }
    return MatrixLiteral.at(literal, 1)
        .filter(l -> l.from() == 0 && l.to() == literal.length())
        .map(l -> new Definition(m.group(1), l.vector(), l.rows()));
  }
}
