package mathcanvas.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * The scientific keypad: buttons in a grid, each inserting the language's ASCII form at the caret
 * of the current cell ({@code docs/language.md}); a template inserts with its placeholder selected,
 * so typing replaces it, and a call leaves the caret between its parentheses. The {@code inv} and
 * {@code hyp} toggles shift the trigonometric keys to the inverse and hyperbolic functions. Labels
 * and tooltips come from the bundle; a tooltip names the text the key inserts. The last key that
 * had the focus is the keypad's focused key, which Tab in a cell accepts.
 */
public final class Keypad extends VBox {

  private static final int COLUMNS = 6;

  /** The key a button inserts, in its properties. */
  private static final String KEY = "mathcanvas.key";

  private final Consumer<KeypadKeys.Key> insert;
  private final ToggleButton inverse = new ToggleButton();
  private final ToggleButton hyperbolic = new ToggleButton();
  private KeypadKeys.Key focused;

  /**
   * A keypad.
   *
   * @param insert inserts a key's text in the current cell
   */
  public Keypad(Consumer<KeypadKeys.Key> insert) {
    this.insert = insert;
    getStyleClass().add("keypad");
    setId("keypad");
    inverse.setText(Messages.text("key.inv"));
    inverse.setId("key-inv");
    inverse.setTooltip(new Tooltip(Messages.text("key.inv.tip")));
    hyperbolic.setText(Messages.text("key.hyp"));
    hyperbolic.setId("key-hyp");
    hyperbolic.setTooltip(new Tooltip(Messages.text("key.hyp.tip")));

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
    getChildren().add(grid);
  }

  private Button button(KeypadKeys.Key key) {
    Button b = new Button(Messages.text("key." + key.id()));
    b.setId("key-" + key.id());
    b.getStyleClass().add("keypad-key");
    b.setTooltip(new Tooltip(Messages.text("key.tip", key.text().strip())));
    b.setOnAction(e -> insert.accept(key));
    b.getProperties().put(KEY, (Supplier<KeypadKeys.Key>) () -> key);
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
    b.setOnAction(e -> insert.accept(KeypadKeys.Key.call(function(name))));
    b.getProperties()
        .put(KEY, (Supplier<KeypadKeys.Key>) () -> KeypadKeys.Key.call(function(name)));
    return b;
  }

  /** The function a trigonometric key inserts under the shift toggles. */
  private String function(String name) {
    return (inverse.isSelected() ? "a" : "") + name + (hyperbolic.isSelected() ? "h" : "");
  }

  /**
   * Notes the scene's new focus owner: a key of this keypad becomes its focused key.
   *
   * @param owner the focus owner
   */
  @SuppressWarnings("unchecked")
  public void noteFocus(Node owner) {
    if (owner != null && owner.getProperties().get(KEY) instanceof Supplier<?> key) {
      focused = ((Supplier<KeypadKeys.Key>) key).get();
    }
  }

  /** Gives the focus to the keypad's first key. */
  public void focusFirst() {
    inverse.requestFocus();
  }

  /**
   * Inserts the keypad's focused key, the last key that had the focus, if any.
   *
   * @return whether a key was inserted
   */
  public boolean acceptFocused() {
    if (focused == null) {
      return false;
    }
    insert.accept(focused);
    return true;
  }
}
