package mathcanvas.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import mathcanvas.app.document.Cell;
import mathcanvas.app.document.DocumentState;

/**
 * The history panel: the inputs of the document's evaluated cells, newest first. Clicking one, or
 * Enter on it, recalls it into the current cell, as Alt+Up does. Touched only on the FX thread.
 */
public final class HistoryPanel extends VBox {

  private final ListView<String> list = new ListView<>();

  /**
   * A panel.
   *
   * @param recall puts an input into the current cell
   */
  public HistoryPanel(Consumer<String> recall) {
    setId("history");
    getStyleClass().add("side-panel");
    Label title = new Label(Messages.text("history.title"));
    title.getStyleClass().add("side-panel-title");
    list.setId("history-list");
    list.setPlaceholder(new Label(Messages.text("history.empty")));
    list.setOnMouseClicked(
        e -> {
          String chosen = list.getSelectionModel().getSelectedItem();
          if (chosen != null) {
            recall.accept(chosen);
          }
        });
    list.setOnKeyPressed(
        e -> {
          String chosen = list.getSelectionModel().getSelectedItem();
          if (e.getCode() == KeyCode.ENTER && chosen != null) {
            recall.accept(chosen);
            e.consume();
          }
        });
    VBox.setVgrow(list, Priority.ALWAYS);
    getChildren().addAll(title, list);
  }

  /**
   * Shows a state's history.
   *
   * @param state the state
   */
  public void show(DocumentState state) {
    List<String> inputs = new ArrayList<>();
    for (Cell c : state.cells().reversed()) {
      if (c.evaluated() && !c.input().isBlank()) {
        inputs.add(c.input());
      }
    }
    if (!list.getItems().equals(inputs)) {
      list.getItems().setAll(inputs);
    }
  }

  /**
   * The list, for the tests.
   *
   * @return the list
   */
  public ListView<String> list() {
    return list;
  }
}
