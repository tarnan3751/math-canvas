package mathcanvas.app.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.WindowEvent;
import mathcanvas.app.text.Messages;
import mathcanvas.canvas.model.Slider;

/**
 * The data editor (Phase 8c): a popup anchored to a cell's editor, built as the {@link
 * MatrixEditor} is, holding a table of 1 to {@link DataTable#MAX_COLUMNS} columns with a header
 * each, which the user can name, and up to {@link DataTable#MAX_ROWS} rows. The grid shows {@link
 * #VISIBLE} rows of text fields at a time over the table, with a scroll bar beside it, so a table
 * of ten thousand rows costs no more nodes than one of ten. Enter in a row moves down a row, adding
 * one after the last; the up and down arrows move between rows and Tab between fields; pasting text
 * with tabs, commas or lines fills the grid from the field pasted in (a header's line first when
 * pasted in a header), adding rows and columns as it needs. Done writes the table's definitions
 * ({@link DataTable#write}); Escape, Cancel, a click outside or the window losing the focus
 * cancels. It only builds text: the cell evaluates it as it evaluates anything typed. Touched only
 * on the FX thread.
 */
public final class DataEditor {

  /** The rows of fields the grid shows at a time. */
  public static final int VISIBLE = 10;

  private final Popup popup = new Popup();
  private final Consumer<String> commit;
  private final TextField name = new TextField();
  private final Spinner<Integer> columns;
  private final GridPane grid = new GridPane();
  private final ScrollBar scroll = new ScrollBar();
  private final Label size = new Label();
  private final Button done = new Button(Messages.text("data.done"));
  private final Button cancel = new Button(Messages.text("matrix.cancel"));
  private final String[] headers = new String[DataTable.MAX_COLUMNS];
  private final List<String[]> rows = new ArrayList<>();
  private final List<TextField> headerFields = new ArrayList<>();
  private final List<List<TextField>> fields = new ArrayList<>();
  private int first;
  private boolean syncing;

  /**
   * An editor, filled with a table.
   *
   * @param table the table: the one a cell defines, or {@link DataTable.Table#empty()}
   * @param commit told of the table's definitions when Done closes the editor
   */
  public DataEditor(DataTable.Table table, Consumer<String> commit) {
    this.commit = commit;
    Arrays.fill(headers, "");
    for (int c = 0; c < table.headers().size(); c++) {
      headers[c] = table.headers().get(c);
    }
    for (List<String> row : table.rows()) {
      rows.add(row(row));
    }
    if (rows.isEmpty()) {
      rows.add(row(List.of()));
    }
    name.setText(table.name());
    name.setId("data-name");
    name.setPrefColumnCount(8);
    name.textProperty().addListener(o -> check());
    columns = new Spinner<>(1, DataTable.MAX_COLUMNS, table.headers().size());
    columns.setId("data-columns");
    columns.setPrefWidth(70);
    columns.valueProperty().addListener(o -> layoutGrid());

    for (int c = 0; c < DataTable.MAX_COLUMNS; c++) {
      int column = c;
      TextField h = new TextField();
      h.setId("data-header-" + c);
      h.setPrefColumnCount(5);
      h.setPromptText(Messages.text("data.header"));
      h.getStyleClass().add("data-header");
      h.textProperty()
          .addListener(
              (o, was, now) -> {
                if (!syncing) {
                  headers[column] = now;
                }
              });
      h.addEventFilter(KeyEvent.KEY_PRESSED, e -> key(e, -1, column));
      headerFields.add(h);
    }
    for (int s = 0; s < VISIBLE; s++) {
      List<TextField> slot = new ArrayList<>();
      for (int c = 0; c < DataTable.MAX_COLUMNS; c++) {
        int at = s;
        int column = c;
        TextField f = new TextField();
        f.setId("data-cell-" + s + "-" + c);
        f.setPrefColumnCount(5);
        f.textProperty()
            .addListener(
                (o, was, now) -> {
                  if (!syncing && first + at < rows.size()) {
                    rows.get(first + at)[column] = now;
                  }
                });
        f.addEventFilter(KeyEvent.KEY_PRESSED, e -> key(e, at, column));
        slot.add(f);
      }
      fields.add(slot);
    }
    scroll.setOrientation(Orientation.VERTICAL);
    scroll.setId("data-scroll");
    scroll
        .valueProperty()
        .addListener(
            (o, was, now) -> {
              if (!syncing) {
                first = (int) Math.round(now.doubleValue());
                refresh();
              }
            });
    grid.getStyleClass().add("matrix-grid");
    grid.addEventFilter(
        ScrollEvent.SCROLL,
        e -> {
          if (e.getDeltaY() != 0) {
            scrollTo(first - (int) Math.signum(e.getDeltaY()));
            e.consume();
          }
        });

    Label title = new Label(Messages.text("data.title"));
    title.getStyleClass().add("side-panel-title");
    HBox sizes =
        new HBox(
            new Label(Messages.text("data.name")),
            name,
            new Label(Messages.text("matrix.columns")),
            columns);
    sizes.getStyleClass().add("matrix-sizes");
    size.setId("data-size");
    done.setId("data-done");
    done.setOnAction(e -> commit());
    cancel.setId("data-cancel");
    cancel.setOnAction(e -> popup.hide());
    HBox buttons = new HBox(done, cancel, size);
    buttons.getStyleClass().add("matrix-buttons");
    Label hint = new Label(Messages.text("data.hint"));
    hint.getStyleClass().add("editor-hint");
    hint.setWrapText(true);
    VBox content = new VBox(title, hint, sizes, new HBox(4, grid, scroll), buttons);
    content.setId("data-editor");
    content.getStyleClass().add("matrix-editor");
    content.addEventFilter(
        KeyEvent.KEY_PRESSED,
        e -> {
          if (e.getCode() == KeyCode.ESCAPE) {
            popup.hide();
            e.consume();
          }
        });
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
   * Hides the editor when its window loses the focus, as the matrix editor does.
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

  /**
   * The table as it stands, for the tests.
   *
   * @return the table
   */
  public DataTable.Table table() {
    int n = columns.getValue();
    List<List<String>> out = new ArrayList<>();
    for (String[] row : rows) {
      out.add(List.of(Arrays.copyOf(row, n)));
    }
    return new DataTable.Table(name.getText().strip(), List.of(Arrays.copyOf(headers, n)), out);
  }

  /**
   * The first row the grid shows, for the tests.
   *
   * @return its index in the table
   */
  public int firstShown() {
    return first;
  }

  private static String[] row(List<String> cells) {
    String[] r = new String[DataTable.MAX_COLUMNS];
    Arrays.fill(r, "");
    for (int c = 0; c < cells.size() && c < r.length; c++) {
      r[c] = cells.get(c);
    }
    return r;
  }

  /** Shows the headers and the visible rows' fields of the current columns, row by row. */
  private void layoutGrid() {
    grid.getChildren().clear();
    int n = columns.getValue();
    for (int c = 0; c < n; c++) {
      grid.add(headerFields.get(c), c, 0);
    }
    for (int s = 0; s < Math.min(VISIBLE, rows.size()); s++) {
      for (int c = 0; c < n; c++) {
        grid.add(fields.get(s).get(c), c, s + 1);
      }
    }
    refresh();
    popup.sizeToScene();
  }

  /** Puts the table's texts in the fields from the first row shown, and the scroll bar there. */
  private void refresh() {
    syncing = true;
    try {
      first = Math.max(0, Math.min(first, rows.size() - VISIBLE));
      for (int c = 0; c < DataTable.MAX_COLUMNS; c++) {
        headerFields.get(c).setText(headers[c]);
      }
      for (int s = 0; s < VISIBLE; s++) {
        String[] row = first + s < rows.size() ? rows.get(first + s) : null;
        for (int c = 0; c < DataTable.MAX_COLUMNS; c++) {
          fields.get(s).get(c).setText(row == null ? "" : row[c]);
        }
      }
      scroll.setMin(0);
      scroll.setMax(Math.max(0, rows.size() - VISIBLE));
      scroll.setVisibleAmount(Math.min(VISIBLE, rows.size()));
      scroll.setValue(first);
      scroll.setVisible(rows.size() > VISIBLE);
      size.setText(Messages.text("data.size", rows.size()));
    } finally {
      syncing = false;
    }
    check();
  }

  /** Done is enabled while the name can be defined. */
  private void check() {
    done.setDisable(!Slider.isVariableName(name.getText().strip()));
  }

  private void scrollTo(int row) {
    first = row;
    refresh();
  }

  /**
   * Keys in a field: Enter moves down a row, adding one after the last; the arrows move up and
   * down, scrolling the grid at its ends; Shortcut+V with tabs, commas or lines pastes a table.
   *
   * @param slot the field's row of the grid, −1 for a header
   */
  private void key(KeyEvent e, int slot, int column) {
    int row = slot < 0 ? -1 : first + slot;
    if (e.getCode() == KeyCode.V && e.isShortcutDown()) {
      String text = Clipboard.getSystemClipboard().getString();
      if (text != null && (text.contains("\t") || text.contains(",") || text.contains("\n"))) {
        paste(text, row, column);
        e.consume();
      }
      return;
    }
    if (e.isShortcutDown() || e.isAltDown()) {
      return;
    }
    int to =
        switch (e.getCode()) {
          case ENTER, DOWN -> row + 1;
          case UP -> row - 1;
          default -> Integer.MIN_VALUE;
        };
    if (to == Integer.MIN_VALUE) {
      return;
    }
    e.consume();
    if (to == rows.size() && e.getCode() == KeyCode.ENTER && rows.size() < DataTable.MAX_ROWS) {
      rows.add(row(List.of()));
      layoutGrid();
    }
    if (to >= rows.size()) {
      return;
    }
    focus(to, column);
  }

  /** Focuses a field of the table, a header at row −1, scrolling it into the grid. */
  private void focus(int row, int column) {
    TextField f;
    if (row < 0) {
      f = headerFields.get(column);
    } else {
      if (row < first) {
        scrollTo(row);
      } else if (row >= first + VISIBLE) {
        scrollTo(row - VISIBLE + 1);
      }
      f = fields.get(row - first).get(column);
    }
    f.requestFocus();
    f.selectAll();
  }

  /**
   * Fills the table from pasted text: a line a row, its cells split at tabs, or at commas when it
   * has none, from the row and column pasted at; a header's row takes the first line as headers.
   */
  private void paste(String text, int row, int column) {
    List<String> lines = new ArrayList<>(text.lines().toList());
    while (!lines.isEmpty() && lines.getLast().isBlank()) {
      lines.removeLast();
    }
    String separator = text.contains("\t") ? "\t" : ",";
    int r = row;
    int widest = columns.getValue();
    for (String line : lines) {
      String[] cells = line.split(separator, -1);
      if (r >= DataTable.MAX_ROWS) {
        break;
      }
      String[] target;
      if (r < 0) {
        target = headers;
      } else {
        while (rows.size() <= r) {
          rows.add(row(List.of()));
        }
        target = rows.get(r);
      }
      for (int k = 0; k < cells.length && column + k < DataTable.MAX_COLUMNS; k++) {
        target[column + k] = cells[k].strip();
        widest = Math.max(widest, column + k + 1);
      }
      r++;
    }
    if (widest != columns.getValue()) {
      columns.getValueFactory().setValue(widest); // lays the grid out
    } else {
      layoutGrid();
    }
    focus(Math.max(0, Math.min(r - 1, rows.size() - 1)), column);
  }

  /** Closes the editor and hands over the table's definitions. */
  private void commit() {
    if (done.isDisabled()) {
      return;
    }
    DataTable.Table t = table();
    popup.hide();
    commit.accept(DataTable.write(t));
  }
}
