package mathcanvas.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import mathcanvas.app.document.Cell;
import mathcanvas.app.document.Output;
import mathcanvas.app.document.Result;
import mathcanvas.app.eval.Typeset;
import mathcanvas.engine.print.Caret;
import mathcanvas.engine.print.layout.Layout;
import mathcanvas.engine.rule.Path;
import mathcanvas.render.MathFont;
import mathcanvas.render.MathView;
import mathcanvas.render.typeset.Box;

/**
 * One cell of the worksheet: an input editor ({@link CellEditor}) and its outputs: a value or a
 * definition's echo in a {@code MathView} showing the box tree the worker typeset (its text in the
 * math font when the layout is over the typesetting cap), an error with its source line and a caret
 * under the column, or a note while the cell waits or after it was stopped. A result or an error
 * message longer than {@link Shown#MAX_SHOWN} code points is cut, with a note of the rest. Touched
 * only on the FX thread.
 */
public final class CellView extends VBox {

  /** The key of a typeset output's layout tree in its properties, for copying and the tests. */
  public static final String LAYOUT = "mathcanvas.layout";

  /** The key of an output node's text in its properties. */
  private static final String TEXT = "mathcanvas.text";

  /** The key of a typeset output's context menu in its properties, for the tests. */
  public static final String MENU = "mathcanvas.menu";

  private final long id;
  private final CellEditor editor = new CellEditor();
  private final TextArea field = editor.area();
  private final VBox outputs = new VBox(2);
  private final Label hint = new Label();
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
    hint.getStyleClass().addAll("cell-hint");
    hint.setVisible(false);
    hint.setManaged(false);
    getChildren().addAll(editor, hint, outputs);
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
   * The input text area.
   *
   * @return the field
   */
  public TextArea field() {
    return field;
  }

  /**
   * Shows or hides the hint under the input.
   *
   * @param text the hint, or null for none
   */
  void hint(String text) {
    hint.setText(text == null ? "" : text);
    hint.setVisible(text != null);
    hint.setManaged(text != null);
  }

  /**
   * The hint shown under the input, for the tests.
   *
   * @return the hint, or empty when none is shown
   */
  public String hintText() {
    return hint.isVisible() ? hint.getText() : "";
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
   * The outputs shown, in order, as text: a typeset one as the text it typesets.
   *
   * @return the texts
   */
  public List<String> outputTexts() {
    return outputs.getChildren().stream().map(n -> (String) n.getProperties().get(TEXT)).toList();
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
            case Result.Value v -> {
              out.add(
                  v.layout() == null
                      ? math(Shown.capped(v.text()))
                      : typeset(v.layout(), v.box(), v.text(), path -> Copy.of(v, path)));
              if (v.traced()) {
                out.add(steps(v.steps()));
              }
            }
            case Result.Defined def when def.echo().quiet() -> {}
            case Result.Defined def ->
                out.add(
                    def.layout() == null
                        ? math(Shown.capped(def.text()))
                        : typeset(
                            def.layout(), def.box(), def.text(), path -> Copy.of(def.echo())));
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

  /**
   * A typeset output: a {@code MathView} of the worker's box tree, which the view shows without
   * typesetting, with the layout kept in its properties and a context menu whose Copy puts the
   * selected subexpression, or the whole output, on the clipboard.
   */
  private static MathView typeset(Layout layout, Box box, String text, Function<Path, Copy> copy) {
    MathView view = new MathView();
    view.setBox(box);
    view.getProperties().put(LAYOUT, layout);
    view.getStyleClass().add("cell-math");
    view.getProperties().put(TEXT, text);
    MenuItem item = new MenuItem(Messages.text("output.copy"));
    item.setOnAction(e -> copy.apply(view.getSelection()).toClipboard());
    ContextMenu menu = new ContextMenu(item);
    view.getProperties().put(MENU, menu);
    view.setOnContextMenuRequested(e -> menu.show(view, e.getScreenX(), e.getScreenY()));
    return view;
  }

  /**
   * The steps expander: a disclosure, closed at first, listing each step's text and the whole
   * expression after it, typeset; a traced value that rewrote nothing says so.
   */
  private static TitledPane steps(List<Result.Step> steps) {
    VBox list = new VBox(6);
    if (steps.isEmpty()) {
      list.getChildren().add(label(Messages.text("steps.none"), "muted"));
    }
    for (int k = 0; k < steps.size(); k++) {
      Result.Step s = steps.get(k);
      Label text = label(Messages.text("steps.step", k + 1, s.text()), "step-text");
      text.setWrapText(true);
      Node after;
      if (s.layout() == null) {
        after = math(Shown.capped(s.after()));
      } else {
        MathView view = new MathView();
        view.setBox(s.box());
        view.getProperties().put(LAYOUT, s.layout());
        view.getStyleClass().add("step-math");
        after = view;
      }
      list.getChildren().addAll(text, after);
    }
    TitledPane pane = new TitledPane(Messages.text("steps.title", steps.size()), list);
    pane.setExpanded(false);
    pane.setAnimated(false);
    pane.getStyleClass().add("cell-steps");
    pane.getProperties().put(TEXT, pane.getText());
    return pane;
  }

  private static Label math(String text) {
    Label l = label(text, null);
    l.setFont(MathFont.at(Typeset.RESULT_SIZE));
    l.setWrapText(true);
    return l;
  }

  private static Label label(String text, String styleClass) {
    Label l = new Label(text);
    l.getProperties().put(TEXT, text);
    l.getStyleClass().add("cell-output");
    if (styleClass != null) {
      l.getStyleClass().add(styleClass);
    }
    l.setMinHeight(Region.USE_PREF_SIZE);
    return l;
  }
}
