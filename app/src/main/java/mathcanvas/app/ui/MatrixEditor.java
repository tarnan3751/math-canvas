package mathcanvas.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.WindowEvent;
import mathcanvas.app.text.Messages;

/**
 * The matrix editor: a popup anchored to a cell's editor with a grid of text fields, one per entry,
 * sized by its rows and columns spinners (1 to 12 each; a vector has one column and no columns
 * spinner), which builds the language's literal ({@link MatrixLiteral#write}). Tab and Shift+Tab,
 * the up and down arrows, and the left and right arrows at a field's ends move between the fields;
 * Enter inserts, Escape, a click outside or the window losing the focus cancels. A field resized
 * away keeps its text for when the grid grows again. It only builds text: the cell evaluates it as
 * it evaluates anything typed. Touched only on the FX thread.
 */
public final class MatrixEditor {

  /** The largest number of rows or columns the spinners offer for a new grid. */
  public static final int MAX = 12;

  private final Popup popup = new Popup();
  private final boolean vector;
  private final Consumer<String> commit;
  private final Spinner<Integer> rows;
  private final Spinner<Integer> columns;
  private final GridPane grid = new GridPane();
  private final Button cancel = new Button(Messages.text("matrix.cancel"));
  private final List<List<TextField>> fields = new ArrayList<>();

  /**
   * An editor, filled with the cells of a literal being edited or empty for a new one.
   *
   * @param vector whether it builds a vector
   * @param cells the cells' texts row by row, ragged rows padded with empty fields; empty for a new
   *     grid of 2×2, or 3 rows for a vector
   * @param commit told of the literal when Enter or the insert button closes the editor
   */
  public MatrixEditor(boolean vector, List<List<String>> cells, Consumer<String> commit) {
    this.vector = vector;
    this.commit = commit;
    int height = cells.isEmpty() ? (vector ? 3 : 2) : cells.size();
    int width =
        vector ? 1 : cells.isEmpty() ? 2 : cells.stream().mapToInt(List::size).max().orElse(1);
    for (int r = 0; r < cells.size(); r++) {
      for (int c = 0; c < cells.get(r).size(); c++) {
        field(r, c).setText(cells.get(r).get(c));
      }
    }
    rows = spinner("matrix-rows", height);
    columns = spinner("matrix-columns", Math.max(1, width));

    Label title = new Label(Messages.text(vector ? "matrix.vector" : "matrix.matrix"));
    title.getStyleClass().add("side-panel-title");
    HBox sizes = new HBox(new Label(Messages.text("matrix.rows")), rows);
    if (!vector) {
      sizes.getChildren().addAll(new Label(Messages.text("matrix.columns")), columns);
    }
    sizes.getStyleClass().add("matrix-sizes");
    grid.getStyleClass().add("matrix-grid");
    Button insert = new Button(Messages.text(cells.isEmpty() ? "matrix.insert" : "matrix.replace"));
    insert.setId("matrix-insert");
    insert.getStyleClass().add("filled");
    insert.setOnAction(e -> commit());
    cancel.setId("matrix-cancel");
    cancel.getStyleClass().add("text-button");
    cancel.setOnAction(e -> popup.hide());
    HBox buttons = new HBox(insert, cancel);
    buttons.getStyleClass().add("matrix-buttons");
    Label hint = new Label(Messages.text("matrix.hint"));
    hint.getStyleClass().add("editor-hint");
    hint.setWrapText(true);
    VBox content = new VBox(title, hint, sizes, grid, buttons);
    content.setId("matrix-editor");
    content.getStyleClass().add("matrix-editor");
    content.addEventFilter(KeyEvent.KEY_PRESSED, this::key);
    popup.getContent().add(content);
    popup.setAutoHide(true);
    layoutGrid();
  }

  /**
   * Shows the editor under a node, in the node's stylesheets and theme, with the first field
   * focused.
   *
   * @param anchor the node it belongs to: the cell's editor
   */
  public void show(Node anchor) {
    Node content = popup.getContent().getFirst();
    if (content instanceof VBox box && anchor.getScene() != null) {
      box.getStylesheets().setAll(anchor.getScene().getStylesheets());
      // the root's classes ("root", "dark"), so the stylesheets' looked-up colours apply
      for (String c : anchor.getScene().getRoot().getStyleClass()) {
        if (!box.getStyleClass().contains(c)) {
          box.getStyleClass().add(c);
        }
      }
    }
    Bounds b = anchor.localToScreen(anchor.getBoundsInLocal());
    popup.show(anchor, b.getMinX(), b.getMaxY());
    hideOnFocusLoss(anchor.getScene().getWindow().focusedProperty());
    focus(0, 0);
  }

  /**
   * Hides the editor when its window loses the focus (another application or window taking it), so
   * no editor is left open behind it; the listener goes when the editor hides. {@link #show} binds
   * it to the owner window's focus; the tests drive it with a property of their own.
   *
   * @param focused whether the window has the focus
   */
  public void hideOnFocusLoss(ObservableValue<Boolean> focused) {
    ChangeListener<Boolean> lost =
        (o, was, now) -> {
          if (!now) {
            popup.hide();
          }
        };
    focused.addListener(lost);
    popup.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> focused.removeListener(lost));
  }

  /**
   * The popup, for the tests and for a listener on its hiding.
   *
   * @return the popup
   */
  public Popup popup() {
    return popup;
  }

  private Spinner<Integer> spinner(String id, int value) {
    Spinner<Integer> s = new Spinner<>(1, Math.max(MAX, value), value);
    s.setId(id);
    s.setPrefWidth(70);
    s.valueProperty().addListener(o -> layoutGrid());
    return s;
  }

  /** The field of a cell, made when first asked for. */
  private TextField field(int r, int c) {
    while (fields.size() <= r) {
      fields.add(new ArrayList<>());
    }
    List<TextField> row = fields.get(r);
    while (row.size() <= c) {
      int column = row.size();
      TextField f = new TextField();
      f.setId("matrix-cell-" + r + "-" + column);
      f.setPrefColumnCount(4);
      f.addEventFilter(KeyEvent.KEY_PRESSED, e -> move(e, f, r, column));
      row.add(f);
    }
    return row.get(c);
  }

  private int height() {
    return rows.getValue();
  }

  private int width() {
    return vector ? 1 : columns.getValue();
  }

  /** Shows the fields of the current size, row by row, so the focus traverses them in order. */
  private void layoutGrid() {
    grid.getChildren().clear();
    for (int r = 0; r < height(); r++) {
      for (int c = 0; c < width(); c++) {
        grid.add(field(r, c), c, r);
      }
    }
    popup.sizeToScene();
  }

  /** Enter inserts, unless the cancel button has the focus; Escape cancels. */
  private void key(KeyEvent e) {
    if (e.getCode() == KeyCode.ENTER && !e.isShiftDown() && !e.isShortcutDown()) {
      if (e.getTarget() == cancel) {
        popup.hide();
      } else {
        commit();
      }
      e.consume();
    } else if (e.getCode() == KeyCode.ESCAPE) {
      popup.hide();
      e.consume();
    }
  }

  /** Moves between the fields: Tab, the arrows, and left and right at a field's ends. */
  private void move(KeyEvent e, TextField f, int r, int c) {
    int at = r * width() + c;
    boolean caretAtEnds = f.getSelection().getLength() == 0;
    int to =
        switch (e.getCode()) {
          case TAB -> e.isShiftDown() ? at - 1 : at + 1;
          case UP -> at - width();
          case DOWN -> at + width();
          case LEFT -> caretAtEnds && f.getCaretPosition() == 0 ? at - 1 : -1;
          case RIGHT -> caretAtEnds && f.getCaretPosition() == f.getLength() ? at + 1 : -1;
          default -> -1;
        };
    if (to >= 0 && to < height() * width() && !e.isShortcutDown() && !e.isAltDown()) {
      focus(to / width(), to % width());
      e.consume();
    }
  }

  private void focus(int r, int c) {
    TextField f = field(r, c);
    f.requestFocus();
    f.selectAll();
  }

  /** Closes the editor and hands over the literal of the grid. */
  private void commit() {
    List<List<String>> cells = new ArrayList<>();
    for (int r = 0; r < height(); r++) {
      List<String> row = new ArrayList<>();
      for (int c = 0; c < width(); c++) {
        row.add(field(r, c).getText());
      }
      cells.add(row);
    }
    popup.hide();
    commit.accept(MatrixLiteral.write(cells, vector));
  }
}
