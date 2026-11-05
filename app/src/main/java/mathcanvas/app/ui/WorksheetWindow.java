package mathcanvas.app.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Control;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import mathcanvas.app.AppInfo;
import mathcanvas.app.document.Cell;
import mathcanvas.app.document.Command;
import mathcanvas.app.document.DocumentState;
import mathcanvas.app.document.Output;
import mathcanvas.app.document.Theme;
import mathcanvas.app.document.Welcome;
import mathcanvas.app.eval.Document;
import mathcanvas.app.text.Messages;
import mathcanvas.app.update.UpdateCheck;
import mathcanvas.engine.Version;

/**
 * The worksheet window: menus; the navigation rail (New, the worksheet, canvas, keypad, history,
 * variables and unit circle toggles, Help, and Settings at the bottom); the app bar (the title and
 * the saved state, a busy indicator, Undo, Redo, Show steps, and Run all or, while something runs,
 * Stop); the cells, the last one the input bar; the side panels (the keypad, the history, the
 * variables inspector, the unit circle view, {@link UnitCirclePanel}), the canvas beside the cells
 * when it is shown ({@link CanvasPane}, the two split side by side), and a status line, which shows
 * the canvas's trace readout while the pointer is over an object. Each cell typesets its results
 * ({@link CellView}). Keyboard first: Enter evaluates the cell and moves to the next one (a new
 * cell after the last), Shift+Enter starts a new line in the cell (a cell offered as a definition,
 * {@link DefinitionShortcut}, is defined by Enter and evaluated as written by Shift+Enter), Up and
 * Down on a cell's first or last line move between cells, Alt+Up and Alt+Down recall the inputs of
 * the cells above into this one, Tab accepts the keypad's focused key, Escape stops the evaluation,
 * Backspace in an empty cell deletes it, and Shortcut+Z, Shortcut+Shift+Z and Shortcut+Y undo and
 * redo the document, not the field; the menus give every other action an accelerator. Every change
 * goes through the document's commands; the view only follows its state.
 */
public final class WorksheetWindow {

  /** The stylesheet. */
  public static final String STYLESHEET =
      Objects.requireNonNull(WorksheetWindow.class.getResource("/mathcanvas/app/app.css"))
          .toExternalForm();

  /** The window's size when it opens, in pixels. */
  static final double WIDTH = 1280;

  static final double HEIGHT = 800;

  /** The least width of the worksheet's column; the canvas's is {@link CanvasPane#MIN_WIDTH}. */
  static final double COLUMN_WIDTH = 360;

  /** The navigation rail's width and the room a split's divider takes. */
  private static final double RAIL_WIDTH = 80;

  private static final double SPLIT_DIVIDER = 4;

  /**
   * The narrowest the window goes, at least; the app bar's title and buttons, whole, and the
   * columns shown at their least may ask for more.
   */
  private static final double MIN_WIDTH = 920;

  private static final double MIN_HEIGHT = 600;

  /** The side column's width: the keypad's 340 px and the column's padding. */
  private static final double SIDE_WIDTH = 364;

  /** The least height of the cells above the side panels stacked under them. */
  private static final double STACKED_CELLS_HEIGHT = 160;

  /** The side panels' state while they are stacked under the cells. */
  private static final PseudoClass STACKED = PseudoClass.getPseudoClass("stacked");

  /** The sizes of the window's icons, each a square PNG the build copies from {@code app/icon}. */
  static final List<Integer> ICON_SIZES = List.of(16, 32, 64, 128, 256, 512);

  /** The icon of one size, as a resource URL. */
  static String icon(int size) {
    return Objects.requireNonNull(
            WorksheetWindow.class.getResource("/mathcanvas/app/icon/math-canvas-" + size + ".png"))
        .toExternalForm();
  }

  private static final KeyCombination UNDO =
      new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);
  private static final KeyCombination REDO =
      new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
  private static final KeyCombination REDO_Y =
      new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN);

  private final Document document;
  private final PreferenceProperties preferences;
  private final Stage stage;
  private SettingsDialog settings;
  private HelpWindow help;
  private final VBox cells = new VBox();
  private final ScrollPane scroll = new ScrollPane();
  // the cells' column: the cells, and the side panels under them when the window is too narrow
  private final VBox worksheetColumn = new VBox();
  private final VBox side = new VBox();
  // the side panels' area: their column, or a scrolling area under the cells
  private final ScrollPane sideArea = new ScrollPane();
  private boolean sideStacked;
  private final Map<Long, CellView> views = new HashMap<>();
  private final Label status = new Label();
  private final Label modes = new Label();
  private final Hyperlink updateLink = new Hyperlink();
  private final CanvasPane canvas;
  private final CanvasExport export;
  // the status line's message: the workspace's notice, or the outcome of an export, the later
  private final ObjectProperty<Notice> statusNotice = new SimpleObjectProperty<>();
  private final Keypad keypad = new Keypad(this::insert, this::editMatrix, this::editData);
  private final ToggleButton stepsToggle = new ToggleButton(Messages.text("toolbar.steps"));
  private final HistoryPanel history = new HistoryPanel(this::recallInput);
  private final InspectorPanel inspector =
      new InspectorPanel(
          name -> insert(new KeypadKeys.Key("name", name, name.length(), name.length())),
          this::append);
  private final UnitCirclePanel unitCircle;

  /** The cell that last had the focus, where the keypad inserts. */
  private long current = -1;

  /** The matrix editor last opened, or null. */
  private MatrixEditor matrixEditor;

  /** The data editor last opened, or null. */
  private DataEditor dataEditor;

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
   * @param preferences the preferences: the theme, the steps, the keypad and the canvas shown
   * @param notice the status line's message
   * @param update a newer release the update check found, or null
   * @param browse opens a web page in the person's browser
   */
  public WorksheetWindow(
      Stage stage,
      Document document,
      PreferenceProperties preferences,
      ReadOnlyObjectProperty<Notice> notice,
      ReadOnlyObjectProperty<UpdateCheck.Release> update,
      Consumer<String> browse) {
    AppFonts.load();
    this.document = document;
    this.preferences = preferences;
    this.stage = stage;
    this.unitCircle = new UnitCirclePanel(document, this::sendToCell);
    ObjectProperty<Theme> theme = preferences.theme();

    Button undo = new Button(Messages.text("toolbar.undo"), Icons.icon("undo"));
    undo.setId("undo");
    tip(undo, "toolbar.undo.tip");
    undo.setOnAction(e -> undo());
    undo.disableProperty().bind(document.canUndoProperty().not());
    Button redo = new Button(Messages.text("toolbar.redo"), Icons.icon("redo"));
    redo.setId("redo");
    tip(redo, "toolbar.redo.tip");
    redo.setOnAction(e -> redo());
    redo.disableProperty().bind(document.canRedoProperty().not());
    // Run all, the one filled button, and Stop in its place while something runs
    Button runAll = new Button(Messages.text("toolbar.runAll"), Icons.icon("play_arrow"));
    runAll.setId("run-all");
    runAll.getStyleClass().add("filled");
    runAll.setTooltip(new Tooltip(Messages.text("toolbar.runAll.tip")));
    runAll.setAccessibleHelp(Messages.text("toolbar.runAll.tip"));
    runAll.setOnAction(e -> runAll());
    runAll.visibleProperty().bind(document.busyProperty().not());
    runAll.managedProperty().bind(document.busyProperty().not());
    Button stop = new Button(Messages.text("toolbar.stop"), Icons.icon("stop"));
    stop.setId("stop");
    tip(stop, "toolbar.stop.tip");
    stop.getStyleClass().add("filled");
    stop.setOnAction(e -> document.stop());
    stop.disableProperty().bind(document.busyProperty().not());
    stop.visibleProperty().bind(document.busyProperty());
    stop.managedProperty().bind(document.busyProperty());
    ProgressIndicator busy = new ProgressIndicator();
    busy.setId("busy");
    busy.setPrefSize(18, 18);
    busy.visibleProperty().bind(document.busyProperty());
    busy.setAccessibleText(Messages.text("toolbar.busy"));
    Button newButton = new Button(Messages.text("toolbar.new"), Icons.icon("add"));
    newButton.setId("new");
    newButton.getStyleClass().add("rail-new");
    newButton.setContentDisplay(ContentDisplay.TOP);
    newButton.setTooltip(new Tooltip(Messages.text("toolbar.new.tip")));
    newButton.setAccessibleHelp(Messages.text("toolbar.new.tip"));
    newButton.setOnAction(e -> newWorksheet());
    ToggleButton worksheetToggle =
        rail(
            new ToggleButton(Messages.text("toolbar.worksheet")),
            "toggle-worksheet",
            "description");
    tip(worksheetToggle, "toolbar.worksheet.tip");
    worksheetToggle.setSelected(true);
    ToggleButton keypadToggle =
        rail(new ToggleButton(Messages.text("toolbar.keypad")), "toggle-keypad", "dialpad");
    tip(keypadToggle, "toolbar.keypad.tip");
    keypadToggle.selectedProperty().bindBidirectional(preferences.keypadShown());
    keypad.visibleProperty().bind(keypadToggle.selectedProperty());
    keypad.managedProperty().bind(keypadToggle.selectedProperty());
    ToggleButton historyToggle =
        rail(new ToggleButton(Messages.text("toolbar.history")), "toggle-history", "history");
    tip(historyToggle, "toolbar.history.tip");
    history.visibleProperty().bind(historyToggle.selectedProperty());
    history.managedProperty().bind(historyToggle.selectedProperty());
    ToggleButton inspectorToggle =
        rail(
            new ToggleButton(Messages.text("toolbar.inspector")),
            "toggle-inspector",
            "data_object");
    tip(inspectorToggle, "toolbar.inspector.tip");
    inspector.visibleProperty().bind(inspectorToggle.selectedProperty());
    inspector.managedProperty().bind(inspectorToggle.selectedProperty());
    stepsToggle.setId("toggle-steps");
    stepsToggle.setTooltip(new Tooltip(Messages.text("toolbar.steps.tip")));
    stepsToggle.setAccessibleHelp(Messages.text("toolbar.steps.tip"));
    stepsToggle.selectedProperty().bindBidirectional(preferences.showSteps());
    Button settingsButton =
        rail(new Button(Messages.text("toolbar.settings.rail")), "settings", "settings");
    tip(settingsButton, "toolbar.settings.tip");
    settingsButton.setOnAction(e -> openSettings());
    Button helpButton = rail(new Button(Messages.text("toolbar.help")), "help", "help");
    helpButton.setTooltip(new Tooltip(Messages.text("toolbar.help.tip")));
    helpButton.setAccessibleHelp(Messages.text("toolbar.help.tip"));
    helpButton.setOnAction(e -> openHelp(null));
    ToggleButton canvasToggle =
        rail(new ToggleButton(Messages.text("toolbar.canvas")), "toggle-canvas", "show_chart");
    tip(canvasToggle, "toolbar.canvas.tip");
    canvasToggle.selectedProperty().bindBidirectional(preferences.canvasShown());
    // Phase 7d: the 3D view, shown in the canvas's place; a chip in the canvas card's tool row
    ToggleButton spaceToggle = new ToggleButton(Messages.text("toolbar.space"));
    spaceToggle.setId("toggle-space");
    spaceToggle.setTooltip(new Tooltip(Messages.text("toolbar.space.tip")));
    spaceToggle.setAccessibleHelp(Messages.text("toolbar.space.tip"));
    ToggleButton unitCircleToggle =
        rail(
            new ToggleButton(Messages.text("toolbar.unitCircle")),
            "toggle-unit-circle",
            "radio_button_unchecked");
    tip(unitCircleToggle, "toolbar.unitCircle.tip");
    unitCircle.visibleProperty().bind(unitCircleToggle.selectedProperty());
    unitCircle.managedProperty().bind(unitCircleToggle.selectedProperty());

    // the navigation rail: New, the views, Help, and Settings at the bottom
    Region railGap = new Region();
    VBox.setVgrow(railGap, Priority.ALWAYS);
    VBox railBox =
        new VBox(
            newButton,
            worksheetToggle,
            canvasToggle,
            keypadToggle,
            historyToggle,
            inspectorToggle,
            unitCircleToggle,
            helpButton,
            railGap,
            settingsButton);
    railBox.setId("rail");
    railBox.getStyleClass().add("rail");

    // the app bar: the worksheet's title and saved state, then the document's actions
    Label title = new Label(Messages.text("toolbar.untitled"));
    title.setId("title");
    title.getStyleClass().add("title-text");
    Label saved = new Label(Messages.text("toolbar.saved"));
    saved.setId("saved");
    saved.getStyleClass().add("label-text");
    Region barGap = new Region();
    HBox.setHgrow(barGap, Priority.ALWAYS);
    HBox appBar = new HBox(8, title, saved, barGap, busy, undo, redo, stepsToggle, runAll, stop);
    appBar.setId("app-bar");
    appBar.getStyleClass().add("app-bar");
    appBar.setAlignment(Pos.CENTER_LEFT);

    side.getChildren().setAll(keypad, history, inspector, unitCircle);
    VBox.setVgrow(history, Priority.ALWAYS);
    VBox.setVgrow(inspector, Priority.ALWAYS);
    side.getStyleClass().add("side-panels");

    cells.getStyleClass().add("worksheet");
    cells.setId("cells");
    cells.setAccessibleText(Messages.text("window.cells"));
    scroll.setContent(cells);
    scroll.setFitToWidth(true);
    scroll.getStyleClass().add("worksheet");
    worksheetColumn.getStyleClass().add("worksheet-column");
    worksheetColumn.getChildren().setAll(scroll);
    canvas = new CanvasPane(document);
    canvas.addChip(spaceToggle);
    export = new CanvasExport(stage, document, canvas.view(), preferences, statusNotice::set);
    export.setSpace(canvas.spaceView(), canvas::spaceShown);
    spaceToggle
        .selectedProperty()
        .subscribe(
            on -> {
              canvas.setSpaceShown(on);
              if (on) {
                preferences.canvasShown().set(true);
              }
            });
    SplitPane split = new SplitPane();
    split.setId("split");
    // the worksheet and the canvas, each shown or hidden by its rail item; the worksheet's is not
    // saved
    Runnable columns =
        () -> {
          List<Node> shown = new ArrayList<>();
          if (worksheetToggle.isSelected()) {
            shown.add(worksheetColumn);
          }
          if (preferences.canvasShown().get()) {
            shown.add(canvas);
          }
          if (!split.getItems().equals(shown)) {
            split.getItems().setAll(shown);
            if (shown.size() == 2) {
              split.setDividerPositions(0.35);
            }
          }
        };
    columns.run();
    worksheetToggle.selectedProperty().subscribe(columns);
    preferences.canvasShown().subscribe(columns);
    HBox.setHgrow(split, Priority.ALWAYS);
    scroll.setMinWidth(COLUMN_WIDTH);
    canvas.setMinWidth(CanvasPane.MIN_WIDTH);
    side.setMinWidth(Region.USE_PREF_SIZE);
    HBox content = new HBox(split);
    content.getStyleClass().add("content");
    // the side panels (the keypad, the history, the variables, the unit circle) have a column of
    // their own while the window has room for it beside the columns at their least; otherwise they
    // stack under the cells in the worksheet's column, in an area of their own that scrolls and
    // takes at most half the column's height, the cells keeping the rest. With the worksheet hidden
    // the side column has its room back.
    VBox.setVgrow(scroll, Priority.ALWAYS);
    scroll.setMinHeight(STACKED_CELLS_HEIGHT);
    sideArea.setContent(side);
    sideArea.setFitToWidth(true);
    sideArea.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    sideArea.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    sideArea.setMinWidth(Region.USE_PREF_SIZE);
    sideArea.getStyleClass().add("side-area");
    Runnable placeSide =
        () -> {
          boolean stack =
              worksheetToggle.isSelected()
                  && content.getWidth() < columnsNeeded(worksheetToggle.isSelected());
          Pane home = stack ? worksheetColumn : content;
          if (sideArea.getParent() != home) {
            if (sideArea.getParent() instanceof Pane old) {
              old.getChildren().remove(sideArea);
            }
            home.getChildren().add(sideArea);
            side.pseudoClassStateChanged(STACKED, stack);
            sideStacked = stack;
            sideArea.setFitToHeight(!stack);
            sideArea.maxHeightProperty().unbind();
            if (stack) {
              sideArea.maxHeightProperty().bind(worksheetColumn.heightProperty().divide(2));
            } else {
              sideArea.setMaxHeight(Double.MAX_VALUE);
            }
          }
        };
    // showing the canvas in a window too narrow for the side column turns the side panels off,
    // their rail toggles with them; the person turns one back on, and it stacks under the cells.
    // Nothing turns them back on by itself.
    List<ToggleButton> sideToggles =
        List.of(keypadToggle, historyToggle, inspectorToggle, unitCircleToggle);
    preferences
        .canvasShown()
        .subscribe(
            shown -> {
              if (shown
                  && worksheetToggle.isSelected()
                  && windowWidth() - RAIL_WIDTH < columnsNeeded(true)) {
                for (ToggleButton t : sideToggles) {
                  t.setSelected(false);
                }
              }
            });
    content.widthProperty().addListener(o -> placeSide.run());
    split.getItems().addListener((ListChangeListener<Node>) c -> placeSide.run());
    placeSide.run();

    status.getStyleClass().add("status-line");
    status.setId("status");
    status.setAccessibleRole(AccessibleRole.TEXT);
    notice.subscribe(statusNotice::set);
    statusNotice.subscribe(n -> showStatus(n, canvas.readoutProperty().get()));
    canvas.readoutProperty().subscribe(r -> showStatus(statusNotice.get(), r));
    updateLink.setId("update");
    updateLink.getStyleClass().add("status-line");
    update.subscribe(
        r -> {
          updateLink.setVisible(r != null);
          updateLink.setManaged(r != null);
          if (r != null) {
            updateLink.setText(Messages.text("status.update", AppInfo.name(), r.version()));
            updateLink.setOnAction(e -> browse.accept(r.page()));
          }
        });
    // the modes and the version on the right of the status row
    modes.setId("status-modes");
    modes.getStyleClass().add("status-line");
    Region gap = new Region();
    HBox.setHgrow(gap, Priority.ALWAYS);
    HBox statusBar = new HBox(8, status, updateLink, gap, modes);
    statusBar.getStyleClass().add("status-row");
    statusBar.setAlignment(Pos.CENTER_LEFT);

    MenuBar menus =
        menus(
            keypadToggle,
            historyToggle,
            inspectorToggle,
            stepsToggle,
            canvasToggle,
            unitCircleToggle,
            settingsButton);
    // set in the order Tab moves through them: the rail, the app bar, the cells and the canvas,
    // the side panels, the status line
    BorderPane main = new BorderPane();
    main.setTop(appBar);
    main.setCenter(content);
    main.setBottom(statusBar);
    BorderPane root = new BorderPane();
    root.setTop(menus);
    root.setLeft(railBox);
    root.setCenter(main);
    Scene scene = new Scene(root, WIDTH, HEIGHT);
    scene.getStylesheets().add(STYLESHEET);
    scene.addEventFilter(KeyEvent.KEY_PRESSED, this::shortcuts);
    scene.focusOwnerProperty().addListener((o, was, now) -> focusMoved(now));
    ThemeBinding.bind(scene, theme);
    root.getStyleClass().addListener((ListChangeListener<String>) c -> canvas.sendTheme());

    document.addListener(change -> show(change.before(), change.after()));
    document.setHelpListener(this::openHelp);
    show(null, document.state());

    stage.setTitle(Messages.text("window.title", AppInfo.name(), Version.current()));
    for (int size : ICON_SIZES) {
      stage.getIcons().add(new Image(icon(size)));
    }
    stage.setScene(scene);
    stage.setMinHeight(MIN_HEIGHT);
    stage.show();
    // room for the rail and the app bar whole, and for the columns shown at their least; the side
    // panels drop out before anything is squeezed
    // (the columns' least widths as laid out: after the pass that follows a change, so the side
    // panels' new place is counted)
    Runnable fit =
        () -> {
          double least =
              Math.max(
                  MIN_WIDTH,
                  RAIL_WIDTH
                      + Math.max(appBar.prefWidth(-1), content.minWidth(-1) + SPLIT_DIVIDER));
          main.setMinWidth(least - RAIL_WIDTH);
          stage.setMinWidth(least + stage.getWidth() - scene.getWidth());
        };
    fit.run();
    split.getItems().subscribe(() -> Platform.runLater(fit));
    sideArea.parentProperty().subscribe(() -> Platform.runLater(fit));
    focus(document.state().cells().size() - 1);
  }

  /**
   * Makes a control an item of the navigation rail: its icon over its label, the icon in the
   * indicator that marks a selected item.
   */
  private static <T extends Labeled> T rail(T item, String id, String icon) {
    item.setId(id);
    StackPane indicator = new StackPane(Icons.icon(icon));
    indicator.getStyleClass().add("rail-indicator");
    item.setGraphic(indicator);
    item.setContentDisplay(ContentDisplay.TOP);
    item.getStyleClass().add("rail-item");
    return item;
  }

  /**
   * A new worksheet in place of this one: the cells and the canvas cleared, one undo step that
   * brings them back.
   */
  private void newWorksheet() {
    DocumentState before = document.state();
    document.endMerge();
    document.execute(new Command.Replace(before, before.cleared()));
    focus(0);
  }

  /**
   * Runs every cell that waits to be evaluated (its output none, or stopped), in order, each as
   * Enter would, all in one undo step; the input bar is kept in view.
   */
  private void runAll() {
    List<Long> waiting = new ArrayList<>();
    for (Cell c : document.state().cells()) {
      boolean idle = c.output() instanceof Output.None || c.output() instanceof Output.Cancelled;
      if (idle && !c.input().isBlank()) {
        waiting.add(c.id());
      }
    }
    if (!waiting.isEmpty()) {
      document.endMerge();
      document.execute(new Command.SubmitAll(waiting));
    }
    focus(document.state().cells().size() - 1, true);
  }

  /** Gives a control its tooltip, which assistive technology reads as its help too. */
  private static void tip(Control control, String key) {
    control.setTooltip(new Tooltip(Messages.text(key)));
    control.setAccessibleHelp(Messages.text(key));
  }

  /**
   * The width the window's content needs for the side column beside the columns shown at their
   * least: the canvas's when it is shown, and the worksheet's when asked.
   */
  private double columnsNeeded(boolean worksheet) {
    double needed = SIDE_WIDTH;
    if (worksheet) {
      needed += COLUMN_WIDTH + SPLIT_DIVIDER;
    }
    if (preferences.canvasShown().get()) {
      needed += CanvasPane.MIN_WIDTH + SPLIT_DIVIDER;
    }
    return needed;
  }

  /** The width of the window's content: the scene's, or the size it opens at before it has one. */
  private double windowWidth() {
    return stage.getScene() != null ? stage.getScene().getWidth() : WIDTH;
  }

  /** Shows a notice in the status line, or the canvas's trace readout while there is one. */
  private void showStatus(Notice n, String readout) {
    if (readout != null) {
      status.setText(readout);
      status.setGraphic(null);
      status.setTooltip(null);
    } else {
      // a notice shows its quoted code in the Input type; its text is the plain words. It is not
      // capitalised: the app writes its notices, and one may start with a file's name
      String text = n == null ? "" : n.text();
      status.setText(MessageText.plain(text));
      boolean code = text.indexOf('`') >= 0;
      status.setGraphic(code ? MessageText.flow(text) : null);
      status.setContentDisplay(code ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
      status.setTooltip(n == null || n.detail().isEmpty() ? null : new Tooltip(n.detail()));
    }
  }

  /**
   * The menus, which give every toolbar action a keyboard accelerator: File (quit), Edit (undo,
   * redo), Evaluate (stop), View (the keypad, the history, the variables, the steps, the canvas,
   * the unit circle) and the settings. On macOS they are in the system menu bar. Undo and redo keep
   * their scene filter, which runs first, so the focused text area never takes the keys for its own
   * undo.
   */
  private MenuBar menus(
      ToggleButton keypadToggle,
      ToggleButton historyToggle,
      ToggleButton inspectorToggle,
      ToggleButton stepsToggle,
      ToggleButton canvasToggle,
      ToggleButton unitCircleToggle,
      Button settingsButton) {
    MenuItem undo = item("menu.undo", "menu-undo", UNDO, this::undo);
    undo.disableProperty().bind(document.canUndoProperty().not());
    MenuItem redo = item("menu.redo", "menu-redo", REDO, this::redo);
    redo.disableProperty().bind(document.canRedoProperty().not());
    MenuItem stop =
        item(
            "menu.stop",
            "menu-stop",
            new KeyCodeCombination(KeyCode.PERIOD, KeyCombination.SHORTCUT_DOWN),
            document::stop);
    stop.disableProperty().bind(document.busyProperty().not());
    Menu edit = new Menu(Messages.text("menu.edit"), null, undo, redo);
    Menu evaluate = new Menu(Messages.text("menu.evaluate"), null, stop);
    Menu view =
        new Menu(
            Messages.text("menu.view"),
            null,
            check("menu.keypad", "menu-keypad", KeyCode.K, keypadToggle),
            check("menu.history", "menu-history", KeyCode.H, historyToggle),
            check("menu.inspector", "menu-inspector", KeyCode.V, inspectorToggle),
            check("menu.steps", "menu-steps", KeyCode.T, stepsToggle),
            check("menu.canvas", "menu-canvas", KeyCode.G, canvasToggle),
            check("menu.unitCircle", "menu-unit-circle", KeyCode.U, unitCircleToggle),
            new SeparatorMenuItem(),
            item(
                "menu.settings",
                "menu-settings",
                new KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHORTCUT_DOWN),
                settingsButton::fire));
    Menu helpMenu =
        new Menu(
            Messages.text("menu.help"),
            null,
            item(
                "menu.helpContents",
                "menu-help",
                new KeyCodeCombination(KeyCode.F1),
                () -> openHelp(null)));
    MenuBar bar = new MenuBar(fileMenu(), edit, evaluate, view, helpMenu);
    bar.setUseSystemMenuBar(true);
    return bar;
  }

  /**
   * The File menu: Export (the canvas as PNG or SVG, while it is shown), then Quit, which exits as
   * closing the window does (the application's stop saves). Items that act on the canvas have their
   * {@code disableProperty} bound to {@link #canvasShownProperty()} negated.
   */
  private Menu fileMenu() {
    MenuItem png = new MenuItem(Messages.text("menu.export.png"));
    png.setId("menu-export-png");
    png.setOnAction(e -> export.choosePng());
    MenuItem svg = new MenuItem(Messages.text("menu.export.svg"));
    svg.setId("menu-export-svg");
    svg.setOnAction(e -> export.chooseSvg());
    Menu exportMenu = new Menu(Messages.text("menu.export"), null, png, svg);
    exportMenu.setId("menu-export");
    exportMenu.disableProperty().bind(preferences.canvasShown().not());
    MenuItem quit =
        item(
            "menu.quit",
            "menu-quit",
            new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN),
            Platform::exit);
    Menu file =
        new Menu(Messages.text("menu.file"), null, exportMenu, new SeparatorMenuItem(), quit);
    file.setId("menu-file");
    return file;
  }

  private static MenuItem item(String key, String id, KeyCombination accelerator, Runnable action) {
    MenuItem item = new MenuItem(Messages.text(key));
    item.setId(id);
    item.setAccelerator(accelerator);
    item.setOnAction(e -> action.run());
    return item;
  }

  /** A view toggle: Shortcut+Shift and a letter, which toggles its toolbar button. */
  private static MenuItem check(String key, String id, KeyCode code, ToggleButton toggle) {
    return item(
        key,
        id,
        new KeyCodeCombination(code, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
        toggle::fire);
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
    modes.setText(
        Messages.text(
            "status.modes",
            Messages.text("format." + after.settings().numberFormat().name()),
            Messages.text("angle." + after.settings().angleMode().name()),
            Version.current()));
    history.show(after);
    unitCircle.show(after.settings());
    inspector.show(Document.inspectionBefore(after, after.cells().size()));
    List<Node> order = new ArrayList<>();
    boolean empty = after.cells().size() == 1 && after.cells().getFirst().input().isEmpty();
    for (Cell c : after.cells()) {
      CellView v = views.computeIfAbsent(c.id(), this::newView);
      v.show(c);
      v.emptyWorksheet(empty);
      showHint(v);
      // the last cell, until its first evaluation, is where the person types next
      v.inputBar(c.id() == after.cells().getLast().id() && c.output() instanceof Output.None);
      order.add(v);
    }
    views.keySet().retainAll(after.cells().stream().map(Cell::id).toList());
    Children.reorder(cells.getChildren(), order);
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
    v.enterButton().setOnAction(e -> submit(v, false));
    return v;
  }

  private void key(CellView v, KeyEvent e) {
    boolean plain = !e.isAltDown() && !e.isShortcutDown() && !e.isShiftDown();
    int index = index(v.id());
    switch (e.getCode()) {
      case ENTER -> {
        boolean shift = e.isShiftDown() && !e.isAltDown() && !e.isShortcutDown();
        if (plain || shift && offer(v).isPresent()) {
          submit(v, shift);
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
        boolean canvasShown = preferences.canvasShown().get();
        if (plain && (keypad.isVisible() || canvasShown)) {
          if (!(keypad.isVisible() && keypad.acceptFocused())) {
            // on in reading order: the keypad stacked under the cells, else the canvas beside
            // them, else the keypad
            if (canvasShown && !(sideStacked && keypad.isVisible())) {
              canvas.lookup("#canvas-tool-select").requestFocus();
            } else {
              keypad.focusFirst();
            }
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

  /**
   * Submits a cell, as Enter does (or Shift+Enter, the equation evaluated as written, when the cell
   * is offered as a definition), and moves to the next cell.
   */
  private void submit(CellView v, boolean asWritten) {
    Optional<DefinitionShortcut.Offer> offer = offer(v);
    if (!asWritten && offer.isPresent()) {
      document.execute(new Command.EditInput(v.id(), offer.get().definition()));
    }
    document.execute(new Command.Submit(v.id()));
    focus(Math.min(index(v.id()) + 1, document.state().cells().size() - 1), true);
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
   * Opens the matrix editor on the current cell: filled with the matrix or vector literal the caret
   * is inside, which Enter replaces, or empty, Enter inserting its literal in place of the
   * selection. The new text is one undo step; the caret goes after the literal and the focus back
   * to the cell.
   */
  private void editMatrix(boolean vector) {
    CellView v = views.getOrDefault(current, cellViews().getLast());
    TextArea area = v.field();
    Optional<MatrixLiteral.Literal> literal =
        MatrixLiteral.at(area.getText(), area.getCaretPosition());
    int from = literal.map(MatrixLiteral.Literal::from).orElse(area.getSelection().getStart());
    int to = literal.map(MatrixLiteral.Literal::to).orElse(area.getSelection().getEnd());
    matrixEditor =
        new MatrixEditor(
            literal.map(MatrixLiteral.Literal::vector).orElse(vector),
            literal.map(MatrixLiteral.Literal::rows).orElse(List.of()),
            text -> {
              String input = area.getText();
              document.endMerge();
              document.execute(
                  new Command.EditInput(
                      v.id(), input.substring(0, from) + text + input.substring(to)));
              document.endMerge();
              area.positionCaret(from + text.length());
            });
    matrixEditor.popup().setOnHidden(e -> area.requestFocus());
    matrixEditor.show(area);
  }

  /**
   * Opens the data editor on the current cell (Phase 8c): filled with the table the cell defines,
   * which Done replaces, or a new one, which Done writes in place of an empty cell's text or on a
   * line after its text. The new text is one undo step; the focus goes back to the cell.
   */
  private void editData() {
    CellView v = views.getOrDefault(current, cellViews().getLast());
    TextArea area = v.field();
    String before = area.getText();
    Optional<DataTable.Table> table = DataTable.read(before);
    dataEditor =
        new DataEditor(
            table.orElse(DataTable.Table.empty()),
            text -> {
              String input =
                  table.isPresent() || before.isBlank()
                      ? text
                      : before.stripTrailing() + "\n" + text;
              document.endMerge();
              document.execute(new Command.EditInput(v.id(), input));
              document.endMerge();
              area.positionCaret(input.length());
            });
    dataEditor.popup().setOnHidden(e -> area.requestFocus());
    dataEditor.show(area);
  }

  /**
   * Inserts a call the unit circle view sends at the caret of the current cell, replacing its
   * selection, as one undo step; the caret goes after it and the focus to the cell.
   */
  private void sendToCell(String text) {
    CellView v = views.getOrDefault(current, cellViews().getLast());
    TextArea area = v.field();
    String input = area.getText();
    int from = area.getSelection().getStart();
    int to = area.getSelection().getEnd();
    document.endMerge();
    document.execute(
        new Command.EditInput(v.id(), input.substring(0, from) + text + input.substring(to)));
    document.endMerge();
    area.requestFocus();
    area.positionCaret(from + text.length());
  }

  /**
   * The unit circle view, for the tests.
   *
   * @return the panel
   */
  public UnitCirclePanel unitCircle() {
    return unitCircle;
  }

  /**
   * The matrix editor last opened, for the tests.
   *
   * @return the editor, or null
   */
  public MatrixEditor matrixEditor() {
    return matrixEditor;
  }

  /**
   * The data editor last opened, for the tests.
   *
   * @return the editor, or null
   */
  public DataEditor dataEditor() {
    return dataEditor;
  }

  /**
   * Follows the scene's focus owner (which a window without the platform's focus still has): a cell
   * taking it becomes the current cell and ends the undo step of the last edits; a keypad key
   * becomes the keypad's focused key, which anything but a cell makes it forget.
   */
  private void focusMoved(Node owner) {
    boolean cell = false;
    for (CellView v : views.values()) {
      if (v.field() == owner) {
        document.endMerge();
        current = v.id();
        cell = true;
      }
    }
    keypad.noteFocus(owner, cell);
  }

  /** Opens the settings dialog; its OK applies the document settings as one command. */
  private void openSettings() {
    settings =
        new SettingsDialog(
            stage,
            document.state().settings(),
            preferences.get(),
            (chosen, prefs) -> {
              document.execute(new Command.SetSettings(chosen));
              preferences.theme().set(prefs.theme());
              preferences.showSteps().set(prefs.showSteps());
              preferences.keypadShown().set(prefs.keypadShown());
              preferences.checkForUpdates().set(prefs.checkForUpdates());
              preferences.language().set(prefs.language());
            });
    settings.show();
  }

  /**
   * Opens the Help window, on a name's entry when one is given; a {@code help(name)} statement the
   * user submitted opens it on that name. An example clicked there runs as a new cell.
   *
   * @param name the name, or null for the entry the window shows
   */
  public void openHelp(String name) {
    if (help == null) {
      help =
          new HelpWindow(
              stage,
              preferences.theme(),
              text -> document.execute(new Command.Append(text)),
              this::openWelcome);
    }
    help.show(name);
  }

  /**
   * Opens the welcome worksheet in the place of this one, as one undo step, with the canvas shown.
   */
  private void openWelcome() {
    DocumentState state = document.state();
    preferences.canvasShown().set(true);
    document.execute(new Command.Replace(state, Welcome.state(state.settings())));
  }

  /**
   * The Help window, once it was opened, for the tests.
   *
   * @return the window, or null
   */
  public HelpWindow helpWindow() {
    return help;
  }

  /**
   * The settings dialog last opened, for the tests.
   *
   * @return the dialog, or null
   */
  public SettingsDialog settingsDialog() {
    return settings;
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
    focus(index, false);
  }

  /**
   * Focuses a cell; after a run (Enter, Run all), the input bar, the last cell, is scrolled into
   * view, while the window opens at the top of the worksheet.
   */
  private void focus(int index, boolean afterRun) {
    long id = document.state().cells().get(index).id();
    CellView v = views.get(id);
    if (v != null) {
      v.field().requestFocus();
      v.field().positionCaret(v.field().getText().length());
      Platform.runLater(() -> v.field().requestFocus());
      if (afterRun && index == document.state().cells().size() - 1) {
        // the input bar, the last cell, stays in view: after the next layout, at the bottom
        Platform.runLater(
            () -> {
              scroll.layout();
              scroll.setVvalue(scroll.getVmax());
            });
      }
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
   * The status line's link to a newer release, hidden while there is none; for the tests.
   *
   * @return the link
   */
  public Hyperlink updateLink() {
    return updateLink;
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
   * Whether the canvas is shown beside the cells: the preference.
   *
   * @return the property
   */
  public BooleanProperty canvasShownProperty() {
    return preferences.canvasShown();
  }

  /**
   * The canvas export, which File → Export runs after its dialogs; for the tests.
   *
   * @return the export
   */
  public CanvasExport canvasExport() {
    return export;
  }

  /**
   * The canvas side, for the tests.
   *
   * @return the pane
   */
  public CanvasPane canvasPane() {
    return canvas;
  }

  /**
   * The theme preference.
   *
   * @return the property
   */
  public ObjectProperty<Theme> themeProperty() {
    return preferences.theme();
  }
}
