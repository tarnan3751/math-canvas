package mathcanvas.app.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import mathcanvas.app.document.Cell;
import mathcanvas.app.document.Command;
import mathcanvas.app.document.DocumentState;
import mathcanvas.app.document.Theme;
import mathcanvas.app.eval.Document;
import mathcanvas.engine.Version;

/**
 * The minimal worksheet window of Phase 2b: a toolbar (undo, redo, stop with a busy indicator, the
 * theme), the cells, and a status line. Keyboard first: Enter evaluates the cell and moves to the
 * next one (a new cell after the last), Shift+Enter starts a new line in the cell (a cell offered
 * as a definition, {@link DefinitionShortcut}, is defined by Enter and evaluated as written by
 * Shift+Enter), Up and Down on a cell's first or last line move between cells, Alt+Up and Alt+Down
 * recall the inputs of the cells above into this one, Escape stops the evaluation, Backspace in an
 * empty cell deletes it, and Shortcut+Z, Shortcut+Shift+Z and Shortcut+Y undo and redo the
 * document, not the field. Every change goes through the document's commands; the view only follows
 * its state. Phase 2c replaces it with the full worksheet.
 */
public final class WorksheetWindow {

  /** The stylesheet. */
  public static final String STYLESHEET =
      Objects.requireNonNull(WorksheetWindow.class.getResource("/mathcanvas/app/app.css"))
          .toExternalForm();

  private static final KeyCombination UNDO =
      new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);
  private static final KeyCombination REDO =
      new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
  private static final KeyCombination REDO_Y =
      new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN);

  private final Document document;
  private final ObjectProperty<Theme> theme;
  private final VBox cells = new VBox();
  private final Map<Long, CellView> views = new HashMap<>();
  private final Label status = new Label();
  private final Keypad keypad = new Keypad(this::insert);
  private final HistoryPanel history = new HistoryPanel(this::recallInput);
  private final InspectorPanel inspector =
      new InspectorPanel(
          name -> insert(new KeypadKeys.Key("name", name, name.length(), name.length())),
          this::append);

  /** The cell that last had the focus, where the keypad inserts. */
  private long current = -1;

  /** The cell whose inputs Alt+Up and Alt+Down recall into, or -1. */
  private long recallCell = -1;

  private int recallIndex;
  private String recallOriginal;
  private boolean recalling;

  /**
   * Builds the window's scene on a stage and shows it.
   *
   * @param stage the stage
   * @param document the document
   * @param theme the theme preference
   * @param notice the status line's message
   */
  public WorksheetWindow(
      Stage stage,
      Document document,
      ObjectProperty<Theme> theme,
      ReadOnlyObjectProperty<Notice> notice) {
    this.document = document;
    this.theme = theme;

    Button undo = new Button(Messages.text("toolbar.undo"));
    undo.setId("undo");
    undo.setOnAction(e -> undo());
    undo.disableProperty().bind(document.canUndoProperty().not());
    Button redo = new Button(Messages.text("toolbar.redo"));
    redo.setId("redo");
    redo.setOnAction(e -> redo());
    redo.disableProperty().bind(document.canRedoProperty().not());
    Button stop = new Button(Messages.text("toolbar.stop"));
    stop.setId("stop");
    stop.setOnAction(e -> document.stop());
    stop.disableProperty().bind(document.busyProperty().not());
    ProgressIndicator busy = new ProgressIndicator();
    busy.setId("busy");
    busy.setPrefSize(18, 18);
    busy.visibleProperty().bind(document.busyProperty());
    Button themeButton = new Button();
    themeButton.setId("theme");
    themeButton
        .textProperty()
        .bind(theme.map(t -> Messages.text("toolbar.theme", Messages.text("theme." + t.name()))));
    themeButton.setOnAction(
        e -> theme.set(Theme.values()[(theme.get().ordinal() + 1) % Theme.values().length]));
    ToggleButton keypadToggle = new ToggleButton(Messages.text("toolbar.keypad"));
    keypadToggle.setId("toggle-keypad");
    keypadToggle.setSelected(true);
    keypad.visibleProperty().bind(keypadToggle.selectedProperty());
    keypad.managedProperty().bind(keypadToggle.selectedProperty());
    ToggleButton historyToggle = new ToggleButton(Messages.text("toolbar.history"));
    historyToggle.setId("toggle-history");
    history.visibleProperty().bind(historyToggle.selectedProperty());
    history.managedProperty().bind(historyToggle.selectedProperty());
    ToggleButton inspectorToggle = new ToggleButton(Messages.text("toolbar.inspector"));
    inspectorToggle.setId("toggle-inspector");
    inspector.visibleProperty().bind(inspectorToggle.selectedProperty());
    inspector.managedProperty().bind(inspectorToggle.selectedProperty());
    ToolBar toolbar =
        new ToolBar(
            undo, redo, stop, busy, themeButton, keypadToggle, historyToggle, inspectorToggle);
    VBox side = new VBox(keypad, history, inspector);
    VBox.setVgrow(history, Priority.ALWAYS);
    VBox.setVgrow(inspector, Priority.ALWAYS);
    side.getStyleClass().add("side-panels");

    cells.getStyleClass().add("worksheet");
    cells.setId("cells");
    ScrollPane scroll = new ScrollPane(cells);
    scroll.setFitToWidth(true);
    scroll.getStyleClass().add("worksheet");

    status.getStyleClass().add("status-line");
    status.setId("status");
    notice.subscribe(
        n -> {
          status.setText(n == null ? "" : n.text());
          status.setTooltip(n == null || n.detail().isEmpty() ? null : new Tooltip(n.detail()));
        });

    BorderPane root = new BorderPane(scroll, toolbar, side, status, null);
    Scene scene = new Scene(root, 760, 540);
    scene.getStylesheets().add(STYLESHEET);
    scene.addEventFilter(KeyEvent.KEY_PRESSED, this::shortcuts);
    scene.focusOwnerProperty().addListener((o, was, now) -> focusMoved(now));
    ThemeBinding.bind(scene, theme);

    document.addListener(change -> show(change.before(), change.after()));
    show(null, document.state());

    stage.setTitle(Messages.text("window.title", Version.current()));
    stage.setScene(scene);
    stage.show();
    focus(document.state().cells().size() - 1);
  }

  private void shortcuts(KeyEvent e) {
    if (UNDO.match(e)) {
      undo();
      e.consume();
    } else if (REDO.match(e) || REDO_Y.match(e)) {
      redo();
      e.consume();
    }
  }

  private void undo() {
    DocumentState before = document.state();
    document.undo();
    focusChange(before, document.state());
  }

  private void redo() {
    DocumentState before = document.state();
    document.redo();
    focusChange(before, document.state());
  }

  /** Focuses the first cell an undo or a redo changed. */
  private void focusChange(DocumentState before, DocumentState after) {
    List<Cell> a = before.cells();
    List<Cell> b = after.cells();
    for (int k = 0; k < b.size(); k++) {
      if (k >= a.size()
          || a.get(k).id() != b.get(k).id()
          || !a.get(k).input().equals(b.get(k).input())) {
        focus(k);
        return;
      }
    }
    if (a.size() > b.size()) {
      focus(b.size() - 1);
    }
  }

  private void show(DocumentState before, DocumentState after) {
    history.show(after);
    inspector.show(Document.inspectionBefore(after, after.cells().size()));
    List<Node> order = new ArrayList<>();
    for (Cell c : after.cells()) {
      CellView v = views.computeIfAbsent(c.id(), this::newView);
      v.show(c);
      showHint(v);
      order.add(v);
    }
    views.keySet().retainAll(after.cells().stream().map(Cell::id).toList());
    if (!cells.getChildren().equals(order)) {
      cells.getChildren().setAll(order);
    }
  }

  private CellView newView(long id) {
    CellView v = new CellView(id);
    TextArea field = v.field();
    field
        .textProperty()
        .addListener(
            (o, was, now) -> {
              if (!v.syncing()) {
                if (!recalling) {
                  recallCell = -1;
                }
                document.execute(new Command.EditInput(id, now));
              }
              showHint(v);
            });
    field.addEventFilter(KeyEvent.KEY_PRESSED, e -> key(v, e));
    return v;
  }

  private void key(CellView v, KeyEvent e) {
    boolean plain = !e.isAltDown() && !e.isShortcutDown() && !e.isShiftDown();
    int index = index(v.id());
    switch (e.getCode()) {
      case ENTER -> {
        boolean shift = e.isShiftDown() && !e.isAltDown() && !e.isShortcutDown();
        Optional<DefinitionShortcut.Offer> offer = offer(v);
        if (plain || shift && offer.isPresent()) {
          if (plain && offer.isPresent()) {
            document.execute(new Command.EditInput(v.id(), offer.get().definition()));
          }
          document.execute(new Command.Submit(v.id()));
          focus(Math.min(index(v.id()) + 1, document.state().cells().size() - 1));
          e.consume();
        } else if (shift) {
          v.field().replaceSelection("\n");
          e.consume();
        }
      }
      case UP, DOWN -> {
        int step = e.getCode() == KeyCode.UP ? -1 : 1;
        if (plain && onEdgeLine(v.field(), step)) {
          focus(Math.max(0, Math.min(index + step, document.state().cells().size() - 1)));
          e.consume();
        } else if (e.isAltDown() && !e.isShortcutDown()) {
          recall(v, index, -step);
          e.consume();
        }
      }
      case TAB -> {
        if (plain && keypad.isVisible()) {
          if (!keypad.acceptFocused()) {
            keypad.focusFirst();
          }
          e.consume();
        }
      }
      case ESCAPE -> {
        document.stop();
        e.consume();
      }
      case BACK_SPACE -> {
        if (plain && v.field().getText().isEmpty() && document.state().cells().size() > 1) {
          document.execute(new Command.DeleteCell(v.id()));
          focus(Math.max(0, index - 1));
          e.consume();
        }
      }
      default -> {}
    }
  }

  /** The definition the cell's text is offered as, if any. */
  private Optional<DefinitionShortcut.Offer> offer(CellView v) {
    DocumentState state = document.state();
    return DefinitionShortcut.offer(
        v.field().getText(), Document.environmentBefore(state, index(v.id())));
  }

  /** Shows the definition hint under a cell while its text is offered as a definition. */
  private void showHint(CellView v) {
    v.hint(offer(v).map(o -> Messages.text("hint.define", o.name())).orElse(null));
  }

  /**
   * Inserts a keypad key's text at the caret of the current cell, replacing its selection, and
   * selects the key's placeholder; the focus returns to the cell.
   */
  private void insert(KeypadKeys.Key key) {
    CellView v = views.get(current);
    if (v == null) {
      v = cellViews().getLast();
    }
    TextArea area = v.field();
    int at = area.getSelection().getStart();
    area.replaceSelection(key.text());
    area.requestFocus();
    area.selectRange(at + key.from(), at + key.to());
  }

  /**
   * Follows the scene's focus owner (which a window without the platform's focus still has): a cell
   * taking it becomes the current cell and ends the undo step of the last edits; a keypad key
   * becomes the keypad's focused key.
   */
  private void focusMoved(Node owner) {
    keypad.noteFocus(owner);
    for (CellView v : views.values()) {
      if (v.field() == owner) {
        document.endMerge();
        current = v.id();
      }
    }
  }

  /** Runs a statement as a new cell. */
  private void append(String text) {
    document.execute(new Command.Append(text));
  }

  /** Puts an earlier input into the current cell, as one undo step, and gives it the focus. */
  private void recallInput(String input) {
    CellView v = views.getOrDefault(current, cellViews().getLast());
    document.endMerge();
    document.execute(new Command.EditInput(v.id(), input));
    document.endMerge();
    v.field().requestFocus();
    v.field().positionCaret(input.length());
  }

  /** Whether the caret is on the first line (going up) or the last line (going down). */
  private static boolean onEdgeLine(TextArea area, int step) {
    String text = area.getText();
    int caret = area.getCaretPosition();
    return step < 0 ? text.lastIndexOf('\n', caret - 1) < 0 : text.indexOf('\n', caret) < 0;
  }

  /**
   * Recalls into a cell the input of a cell above it: older with each Alt+Up, newer with each
   * Alt+Down, back to what was typed past the newest.
   */
  private void recall(CellView v, int index, int older) {
    List<String> history = new ArrayList<>();
    for (int k = index - 1; k >= 0; k--) {
      String input = document.state().cells().get(k).input();
      if (!input.isBlank()) {
        history.add(input);
      }
    }
    if (recallCell != v.id()) {
      recallCell = v.id();
      recallIndex = -1;
      recallOriginal = v.field().getText();
    }
    int next = Math.max(-1, Math.min(recallIndex + older, history.size() - 1));
    if (next == recallIndex) {
      return;
    }
    recallIndex = next;
    recalling = true;
    try {
      document.execute(
          new Command.EditInput(v.id(), next < 0 ? recallOriginal : history.get(next)));
    } finally {
      recalling = false;
    }
    v.field().positionCaret(v.field().getText().length());
  }

  private int index(long id) {
    OptionalInt k = document.state().indexOf(id);
    return k.orElse(0);
  }

  private void focus(int index) {
    long id = document.state().cells().get(index).id();
    CellView v = views.get(id);
    if (v != null) {
      v.field().requestFocus();
      v.field().positionCaret(v.field().getText().length());
      Platform.runLater(() -> v.field().requestFocus());
    }
  }

  /**
   * The cell views, in order, for the tests.
   *
   * @return the views
   */
  public List<CellView> cellViews() {
    return cells.getChildren().stream().map(CellView.class::cast).toList();
  }

  /**
   * The status line, for the tests.
   *
   * @return the label
   */
  public Label statusLine() {
    return status;
  }

  /**
   * The theme preference.
   *
   * @return the property
   */
  public ObjectProperty<Theme> themeProperty() {
    return theme;
  }
}
