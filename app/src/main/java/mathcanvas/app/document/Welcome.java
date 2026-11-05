package mathcanvas.app.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The welcome worksheet, which the app opens on its first launch and from Help's Getting started:
 * the cells of the resource {@code welcome.mc}, each a note or a step to run, on an empty canvas.
 *
 * <p>The resource is cut into cells by its lines: an empty line ends a cell, a note line ({@code #}
 * and a space, {@link Result.Note}) is a cell of its own, and the lines of code between them are
 * one cell, run as one.
 */
public final class Welcome {

  private static final String RESOURCE = "/mathcanvas/app/welcome.mc";

  private Welcome() {}

  /**
   * The welcome worksheet's cells, as {@code welcome.mc} holds them.
   *
   * @return the cells' texts, in order
   */
  public static List<String> cells() {
    try (InputStream in = Welcome.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("no resource " + RESOURCE);
      }
      return cells(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Cuts a script into the welcome worksheet's cells.
   *
   * @param text the script
   * @return the cells' texts, in order
   */
  static List<String> cells(String text) {
    List<String> out = new ArrayList<>();
    StringBuilder code = new StringBuilder();
    for (String line : text.split("\\r\\n|\\r|\\n")) {
      boolean note = Result.Note.of(line).isPresent();
      if ((line.isBlank() || note) && !code.isEmpty()) {
        out.add(code.toString());
        code.setLength(0);
      }
      if (note) {
        out.add(line);
      } else if (!line.isBlank()) {
        code.append(code.isEmpty() ? "" : "\n").append(line);
      }
    }
    if (!code.isEmpty()) {
      out.add(code.toString());
    }
    return out;
  }

  /**
   * The welcome worksheet in a document's settings: its cells submitted and pending, then a blank
   * cell, on an empty canvas at a new document's viewport.
   *
   * @param settings the document settings, which it keeps
   * @return the state
   */
  public static DocumentState state(DocumentSettings settings) {
    List<DocumentState.StoredCell> stored = new ArrayList<>();
    for (String cell : cells()) {
      stored.add(new DocumentState.StoredCell(cell, true));
    }
    stored.add(new DocumentState.StoredCell("", false));
    return DocumentState.of(stored, settings);
  }
}
