package mathcanvas.app.document;

import java.util.List;
import java.util.Objects;
import mathcanvas.engine.eval.Definition;
import mathcanvas.engine.print.layout.Layout;

/**
 * What the variables inspector shows of the environment after a cell, printed on the worker with
 * the cell's results so that the inspector only builds nodes: each name with its definition, as
 * text and as a layout tree, and the assumptions in force as text.
 *
 * @param entries the names in order
 * @param assumptions the recorded assumptions, printed
 */
public record Inspection(List<Entry> entries, List<String> assumptions) {

  /** Nothing defined, nothing assumed. */
  public static final Inspection EMPTY = new Inspection(List.of(), List.of());

  /**
   * Copies the lists.
   *
   * @throws NullPointerException if an operand is null
   */
  public Inspection {
    entries = List.copyOf(entries);
    assumptions = List.copyOf(assumptions);
  }

  /**
   * One name.
   *
   * @param name the name
   * @param definition its definition, for its kind and parameters
   * @param text the body printed, for a definition too large to typeset
   * @param layout the layout of {@code name := body}, or null when it is too large to typeset
   */
  public record Entry(String name, Definition definition, String text, Layout layout) {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if the name, the definition or the text is null
     */
    public Entry {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(definition, "definition");
      Objects.requireNonNull(text, "text");
    }
  }
}
