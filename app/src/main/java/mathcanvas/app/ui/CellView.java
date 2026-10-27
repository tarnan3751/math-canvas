package mathcanvas.app.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import mathcanvas.app.document.Cell;
import mathcanvas.app.document.Output;
import mathcanvas.app.document.Result;
import mathcanvas.engine.print.Caret;
import mathcanvas.render.MathFont;

/**
 * One cell of the minimal worksheet: a text field for the input and a label per line of output: a
 * value's display or a definition's echo in the math font, an error with its source line and a
 * caret under the column, or a note while the cell waits or after it was stopped. A result or an
 * error message longer than {@link Shown#MAX_SHOWN} code points is cut, with a note of the rest.
 * Phase 2c replaces the labels with typeset output. Touched only on the FX thread.
 */
public final class CellView extends VBox {

  private static final double OUTPUT_SIZE = 16;

  private final long id;
  private final TextField field = new TextField();
  private final VBox outputs = new VBox(2);
  private Output shown;
  private boolean syncing;

  /**
   * A view of a cell.
   *
   * @param id the cell's id
   */
  public CellView(long id) {
    this.id = id;
    getStyleClass().add("worksheet-cell");
    field.setPromptText(Messages.text("cell.prompt"));
    field.getStyleClass().add("cell-input");
    getChildren().addAll(field, outputs);
  }

  /**
   * The cell's id.
   *
   * @return the id
   */
  public long id() {
    return id;
  }

  /**
   * The input field.
   *
   * @return the field
   */
  public TextField field() {
    return field;
  }

  /**
   * Whether the field's text is being set from the document, not typed.
   *
   * @return true while syncing
   */
  boolean syncing() {
    return syncing;
  }

  /**
   * The lines of output shown, in order.
   *
   * @return the labels' texts
   */
  public List<String> outputTexts() {
    return outputs.getChildren().stream().map(n -> ((Label) n).getText()).toList();
  }

  /**
   * Shows a cell's state: its text, unless the field already holds it, and its output.
   *
   * @param cell the cell
   */
  void show(Cell cell) {
    if (!field.getText().equals(cell.input())) {
      syncing = true;
      try {
        field.setText(cell.input());
        field.positionCaret(cell.input().length());
      } finally {
        syncing = false;
      }
    }
    if (!cell.output().equals(shown)) {
      shown = cell.output();
      outputs.getChildren().setAll(labels(cell.output()));
    }
  }

  private static List<Node> labels(Output output) {
    List<Node> out = new ArrayList<>();
    switch (output) {
      case Output.None _ -> {}
      case Output.Pending _ -> out.add(label(Messages.text("output.pending"), "muted"));
      case Output.Cancelled _ -> out.add(label(Messages.text("output.cancelled"), "muted"));
      case Output.Done d -> {
        for (Result r : d.results()) {
          switch (r) {
            case Result.Value v when v.quiet() -> {}
            case Result.Value v -> out.add(math(Shown.capped(v.text())));
            case Result.Defined def when def.echo().quiet() -> {}
            case Result.Defined def -> out.add(math(Shown.capped(def.text())));
            case Result.Failed f ->
                out.add(
                    label(
                        Shown.capped(f.message())
                            + "\n"
                            + f.line()
                            + "\n"
                            + Caret.under(f.line(), f.at().column()),
                        "error"));
          }
        }
      }
    }
    return out;
  }

  private static Label math(String text) {
    Label l = label(text, null);
    l.setFont(MathFont.at(OUTPUT_SIZE));
    l.setWrapText(true);
    return l;
  }

  private static Label label(String text, String styleClass) {
    Label l = new Label(text);
    l.getStyleClass().add("cell-output");
    if (styleClass != null) {
      l.getStyleClass().add(styleClass);
    }
    l.setMinHeight(Region.USE_PREF_SIZE);
    return l;
  }
}
