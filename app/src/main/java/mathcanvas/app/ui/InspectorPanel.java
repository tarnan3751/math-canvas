package mathcanvas.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import mathcanvas.app.document.Inspection;
import mathcanvas.app.text.Messages;
import mathcanvas.engine.eval.Definition;
import mathcanvas.render.MathView;

/**
 * The variables inspector: the environment after the last evaluated cell, one row per name with its
 * kind (a value, or a function of its parameters) and its definition typeset small, then the
 * assumptions in force. A double click on a row inserts the name in the current cell; a row's clear
 * button runs {@code clear(name)} as a new cell. The worker prints the rows with the cells' results
 * ({@code Inspection}, each definition printed once); the panel only builds nodes from them.
 * Touched only on the FX thread.
 */
public final class InspectorPanel extends VBox {

  private final VBox rows = new VBox(6);
  private final Consumer<String> insert;
  private final Consumer<String> run;
  private Inspection shown;

  /**
   * A panel.
   *
   * @param insert inserts a name in the current cell
   * @param run runs a statement as a new cell
   */
  public InspectorPanel(Consumer<String> insert, Consumer<String> run) {
    this.insert = insert;
    this.run = run;
    setId("inspector");
    getStyleClass().add("side-panel");
    Label title = new Label(Messages.text("inspector.title"));
    title.getStyleClass().add("side-panel-title");
    rows.setId("inspector-rows");
    ScrollPane scroll = new ScrollPane(rows);
    scroll.setFitToWidth(true);
    VBox.setVgrow(scroll, Priority.ALWAYS);
    getChildren().addAll(title, scroll);
  }

  /**
   * Shows an inspection, unless it is the one shown.
   *
   * @param inspection what the worker printed of the environment after the last evaluated cell
   */
  public void show(Inspection inspection) {
    if (inspection.equals(shown)) {
      return;
    }
    shown = inspection;
    List<Node> out = new ArrayList<>();
    for (Inspection.Entry e : inspection.entries()) {
      out.add(row(e));
    }
    if (out.isEmpty()) {
      out.add(note(Messages.text("inspector.empty")));
    }
    if (!inspection.assumptions().isEmpty()) {
      Label heading = new Label(Messages.text("inspector.assumptions"));
      heading.getStyleClass().add("side-panel-title");
      out.add(heading);
      for (String fact : inspection.assumptions()) {
        Label l = note(fact);
        l.getStyleClass().add("inspector-assumption");
        out.add(l);
      }
    }
    rows.getChildren().setAll(out);
  }

  private Node row(Inspection.Entry entry) {
    String name = entry.name();
    Label label = new Label(name);
    label.getStyleClass().add("inspector-name");
    String kind =
        switch (entry.definition()) {
          case Definition.Value v -> Messages.text("inspector.value");
          case Definition.Function f ->
              Messages.text(
                  "inspector.function",
                  f.params().stream().map(p -> p.name()).collect(Collectors.joining(", ")));
        };
    Label kindLabel = note(kind);
    Region gap = new Region();
    HBox.setHgrow(gap, Priority.ALWAYS);
    Button clear = new Button(Messages.text("inspector.clear"));
    clear.setId("clear-" + name);
    clear.setTooltip(new Tooltip(Messages.text("inspector.clear.tip", name)));
    clear.setAccessibleText(Messages.text("inspector.clear.tip", name));
    clear.setOnAction(e -> run.accept("clear(" + name + ")"));
    HBox head = new HBox(6, label, kindLabel, gap, clear);
    Node body;
    if (entry.layout() == null) {
      body = note(Shown.capped(entry.text()));
    } else {
      MathView view = new MathView();
      view.setBox(entry.box());
      body = view;
    }
    VBox row = new VBox(2, head, body);
    row.setId("inspector-" + name);
    row.getStyleClass().add("inspector-row");
    row.setOnMouseClicked(
        e -> {
          if (e.getClickCount() == 2) {
            insert.accept(name);
          }
        });
    return row;
  }

  private static Label note(String text) {
    Label l = new Label(text);
    l.getStyleClass().add("inspector-note");
    l.setWrapText(true);
    return l;
  }

  /**
   * The names shown, in order, for the tests.
   *
   * @return the names
   */
  public List<String> names() {
    return rows.getChildren().stream()
        .filter(n -> n.getId() != null && n.getId().startsWith("inspector-"))
        .map(n -> n.getId().substring("inspector-".length()))
        .toList();
  }

  /**
   * The assumptions shown, for the tests.
   *
   * @return their texts
   */
  public List<String> assumptions() {
    return rows.getChildren().stream()
        .filter(n -> n.getStyleClass().contains("inspector-assumption"))
        .map(n -> ((Label) n).getText())
        .toList();
  }
}
