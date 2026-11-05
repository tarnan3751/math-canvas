package mathcanvas.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import mathcanvas.app.text.Messages;

/**
 * The scientific keypad: buttons in a grid, each inserting the language's ASCII form at the caret
 * of the current cell ({@code docs/language.md}); a template inserts with its placeholder selected,
 * so typing replaces it, and a call leaves the caret between its parentheses. The {@code inv} and
 * {@code hyp} toggles shift the trigonometric keys to the inverse and hyperbolic functions. Labels
 * and tooltips come from the bundle; a tooltip names the text the key inserts. The {@code matrix}
 * and {@code vector} keys, a group of their own, open the matrix editor ({@link MatrixEditor}), and
 * the {@code data} key beside them the data editor ({@link DataEditor}). The last key that had the
 * focus is the keypad's focused key, which Tab in a cell accepts.
 */
public final class Keypad extends VBox {

  private static final int COLUMNS = 6;

  /** The command keys, each with a tooltip of what its command does. */
  private static final Set<String> COMMANDS =
      Set.of(
          "sum",
          "integral",
          "derivative",
          "simplify",
          "expand",
          "factor",
          "solve",
          "numeric",
          "steps");

  /** The keys drawn as digits, on the higher surface. */
  private static final Set<String> DIGITS =
      Set.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "point");

  /** The command keys drawn tonal: the ones that act on an expression. */
  private static final Set<String> TONAL =
      Set.of("solve", "simplify", "steps", "expand", "factor", "numeric");

  /** What a button does, in its properties: a supplier of its action as the key is focused. */
  private static final String KEY = "mathcanvas.key";

  private final Consumer<KeypadKeys.Key> insert;
  private final Consumer<Boolean> openGrid;
  private final Runnable openData;
  private final ToggleButton inverse = new ToggleButton();
  private final ToggleButton hyperbolic = new ToggleButton();
  private Runnable focused;

  /**
   * A keypad.
   *
   * @param insert inserts a key's text in the current cell
   * @param openGrid opens the matrix editor on the current cell, for a vector when given true
   * @param openData opens the data editor on the current cell
   */
  public Keypad(Consumer<KeypadKeys.Key> insert, Consumer<Boolean> openGrid, Runnable openData) {
    this.insert = insert;
    this.openGrid = openGrid;
    this.openData = openData;
    getStyleClass().add("keypad");
    setId("keypad");
    inverse.setText(Messages.text("key.inv"));
    inverse.getStyleClass().add("keypad-key");
    inverse.setId("key-inv");
    inverse.setTooltip(new Tooltip(Messages.text("key.inv.tip")));
    inverse.setAccessibleText(Messages.text("key.inv.tip"));
    hyperbolic.setText(Messages.text("key.hyp"));
    hyperbolic.getStyleClass().add("keypad-key");
    hyperbolic.setId("key-hyp");
    hyperbolic.setTooltip(new Tooltip(Messages.text("key.hyp.tip")));
    hyperbolic.setAccessibleText(Messages.text("key.hyp.tip"));

    GridPane grid = new GridPane();
    grid.getStyleClass().add("keypad-grid");
    List<Node> nodes = new ArrayList<>();
    nodes.add(inverse);
    nodes.add(hyperbolic);
    for (String trig : List.of("sin", "cos", "tan")) {
      nodes.add(trigKey(trig));
    }
    nodes.add(button(KeypadKeys.Key.call("log")));
    for (KeypadKeys.Key k : KeypadKeys.keys()) {
      nodes.add(button(k));
    }
    for (int n = 0; n < nodes.size(); n++) {
      grid.add(nodes.get(n), n % COLUMNS, n / COLUMNS);
    }
    GridPane matrices = new GridPane();
    matrices.getStyleClass().add("keypad-grid");
    // six equal columns in both grids, so the keys line up
    for (GridPane g : List.of(grid, matrices)) {
      for (int c = 0; c < COLUMNS; c++) {
        ColumnConstraints column = new ColumnConstraints();
        column.setPercentWidth(100.0 / COLUMNS);
        g.getColumnConstraints().add(column);
      }
    }
    matrices.add(gridKey("matrix", false), 0, 0);
    matrices.add(gridKey("vector", true), 1, 0);
    matrices.add(dataKey(), 2, 0);
    getChildren().addAll(grid, matrices);
  }

  private Button button(KeypadKeys.Key key) {
    Button b = new Button(Messages.text("key." + key.id()));
    b.setId("key-" + key.id());
    b.getStyleClass().add("keypad-key");
    if (DIGITS.contains(key.id())) {
      b.getStyleClass().add("key-digit");
    } else if (TONAL.contains(key.id())) {
      b.getStyleClass().add("key-command");
    }
    // a command key says what its command does; another key, what it inserts
    String tip =
        COMMANDS.contains(key.id())
            ? Messages.text("key." + key.id() + ".tip")
            : Messages.text("key.tip", key.text().strip());
    b.setTooltip(new Tooltip(tip));
    b.setAccessibleText(tip);
    b.setOnAction(e -> insert.accept(key));
    b.getProperties().put(KEY, (Supplier<Runnable>) () -> () -> insert.accept(key));
    return b;
  }

  /** A key opening the matrix editor, for a vector or a matrix. */
  private Button gridKey(String id, boolean vector) {
    Button b = new Button(Messages.text("key." + id));
    b.setId("key-" + id);
    b.getStyleClass().add("keypad-key");
    b.setTooltip(new Tooltip(Messages.text("key." + id + ".tip")));
    b.setAccessibleText(Messages.text("key." + id + ".tip"));
    b.setOnAction(e -> openGrid.accept(vector));
    b.getProperties().put(KEY, (Supplier<Runnable>) () -> () -> openGrid.accept(vector));
    return b;
  }

  /** The key opening the data editor. */
  private Button dataKey() {
    Button b = new Button(Messages.text("key.data"));
    b.setId("key-data");
    b.getStyleClass().add("keypad-key");
    b.setTooltip(new Tooltip(Messages.text("key.data.tip")));
    b.setAccessibleText(Messages.text("key.data.tip"));
    b.setOnAction(e -> openData.run());
    b.getProperties().put(KEY, (Supplier<Runnable>) () -> openData);
    return b;
  }

  /**
   * A trigonometric key: {@code sin}, or {@code asin}, {@code sinh}, {@code asinh} when shifted.
   */
  private Button trigKey(String name) {
    Button b = new Button();
    b.setId("key-" + name);
    b.getStyleClass().add("keypad-key");
    b.textProperty()
        .bind(
            Bindings.createStringBinding(
                () -> function(name), inverse.selectedProperty(), hyperbolic.selectedProperty()));
    b.tooltipProperty()
        .bind(
            Bindings.createObjectBinding(
                () -> new Tooltip(Messages.text("key.tip", function(name) + "()")),
                inverse.selectedProperty(),
                hyperbolic.selectedProperty()));
    b.accessibleTextProperty()
        .bind(
            Bindings.createStringBinding(
                () -> Messages.text("key.tip", function(name) + "()"),
                inverse.selectedProperty(),
                hyperbolic.selectedProperty()));
    b.setOnAction(e -> insert.accept(KeypadKeys.Key.call(function(name))));
    b.getProperties()
        .put(
            KEY,
            (Supplier<Runnable>)
                () -> {
                  KeypadKeys.Key key = KeypadKeys.Key.call(function(name));
                  return () -> insert.accept(key);
                });
    return b;
  }

  /** The function a trigonometric key inserts under the shift toggles. */
  private String function(String name) {
    return (inverse.isSelected() ? "a" : "") + name + (hyperbolic.isSelected() ? "h" : "");
  }

  /**
   * Notes the scene's new focus owner: a key of this keypad becomes its focused key; a cell keeps
   * it, so Tab there accepts the key; anything else forgets it, so a Tab that moved on past the
   * keypad does not accept its last key when it comes round to a cell.
   *
   * @param owner the focus owner
   * @param cell whether the owner is a cell's field
   */
  @SuppressWarnings("unchecked")
  public void noteFocus(Node owner, boolean cell) {
    if (owner != null && owner.getProperties().get(KEY) instanceof Supplier<?> key) {
      focused = ((Supplier<Runnable>) key).get();
    } else if (!cell) {
      focused = null;
    }
  }

  /** Gives the focus to the keypad's first key. */
  public void focusFirst() {
    inverse.requestFocus();
  }

  /**
   * Accepts the keypad's focused key, the last key that had the focus, if any: inserts its text or
   * opens the matrix editor or the data editor.
   *
   * @return whether a key was accepted
   */
  public boolean acceptFocused() {
    if (focused == null) {
      return false;
    }
    focused.run();
    return true;
  }
}
