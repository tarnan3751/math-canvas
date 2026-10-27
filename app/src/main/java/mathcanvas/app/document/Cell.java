package mathcanvas.app.document;

import java.util.Objects;

/**
 * One worksheet cell.
 *
 * @param id its identity within the document, unchanged by edits and moves
 * @param input the text typed
 * @param evaluated whether the cell was submitted for evaluation, so a re-evaluation runs it
 * @param output its evaluation
 */
public record Cell(long id, String input, boolean evaluated, Output output) {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if the input or the output is null
   */
  public Cell {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(output, "output");
  }

  /**
   * A blank cell, never evaluated.
   *
   * @param id its identity
   * @return the cell
   */
  public static Cell blank(long id) {
    return new Cell(id, "", false, Output.None.INSTANCE);
  }

  /**
   * This cell with other text.
   *
   * @param text the text
   * @return the cell
   */
  public Cell withInput(String text) {
    return new Cell(id, text, evaluated, output);
  }

  /**
   * This cell with another output.
   *
   * @param out the output
   * @return the cell
   */
  public Cell withOutput(Output out) {
    return new Cell(id, input, evaluated, out);
  }
}
