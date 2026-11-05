package mathcanvas.app.ui;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.beans.property.ObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import mathcanvas.app.document.Theme;
import mathcanvas.app.help.HelpEntries;
import mathcanvas.app.help.HelpEntry;
import mathcanvas.app.text.Messages;

/**
 * The Help window: a non-modal window beside the worksheet with a search field, the list of every
 * entry the build generated from the language reference ({@link HelpEntries}: the functions, the
 * commands, the reserved names and the operators), and the chosen entry's description and examples.
 * The search keeps the entries whose name holds the text, or whose description does. Clicking an
 * example, or Enter on it, runs it as a new cell of the worksheet ({@code Command.Append}, through
 * the document's command stack). The Getting started entry, listed first, also opens the welcome
 * worksheet. It takes the worksheet's stylesheet and theme. Touched only on the FX thread.
 */
public final class HelpWindow {

  private final Stage stage = new Stage();
  private final TextField search = new TextField();
  private final FilteredList<HelpEntry> shown =
      new FilteredList<>(FXCollections.observableList(HelpEntries.all()));
  private final ListView<HelpEntry> list = new ListView<>(shown);
  private final VBox detail = new VBox();
  private final Consumer<String> run;
  private final Runnable welcome;

  /**
   * Builds the window, hidden.
   *
   * @param owner the worksheet's window, which it stays in front of
   * @param theme the theme preference
   * @param run runs an example's text as a new cell
   * @param welcome opens the welcome worksheet in the worksheet's place
   */
  public HelpWindow(
      Window owner, ObjectProperty<Theme> theme, Consumer<String> run, Runnable welcome) {
    this.run = Objects.requireNonNull(run, "run");
    this.welcome = Objects.requireNonNull(welcome, "welcome");
    stage.initOwner(owner);
    stage.setTitle(Messages.text("help.title"));

    search.setId("help-search");
    search.setPromptText(Messages.text("help.search"));
    search.setAccessibleText(Messages.text("help.search"));
    search
        .textProperty()
        .subscribe(
            text -> {
              String wanted = text.strip().toLowerCase(Locale.ROOT);
              shown.setPredicate(
                  e ->
                      wanted.isEmpty()
                          || e.name().toLowerCase(Locale.ROOT).contains(wanted)
                          || e.description().toLowerCase(Locale.ROOT).contains(wanted));
              if (list.getSelectionModel().getSelectedItem() == null && !shown.isEmpty()) {
                list.getSelectionModel().selectFirst();
              }
            });
    search.setOnKeyPressed(
        e -> {
          if (e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.ENTER) {
            list.requestFocus();
            e.consume();
          }
        });

    list.setId("help-list");
    list.setAccessibleText(Messages.text("help.list"));
    list.setCellFactory(
        v ->
            new ListCell<>() {
              @Override
              protected void updateItem(HelpEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                // Getting started is its own category: its row is its name alone
                setText(
                    empty || entry == null
                        ? null
                        : entry.name().equals(entry.category())
                            ? entry.name()
                            : Messages.text("help.row", entry.name(), entry.category()));
              }
            });
    list.getSelectionModel().selectedItemProperty().subscribe(this::showEntry);

    detail.setId("help-detail");
    detail.getStyleClass().add("help-detail");
    ScrollPane scroll = new ScrollPane(detail);
    scroll.setFitToWidth(true);
    SplitPane split = new SplitPane(list, scroll);
    split.setDividerPositions(0.3);
    BorderPane root = new BorderPane(split, search, null, null, null);
    root.getStyleClass().add("help-window");
    Scene scene = new Scene(root, 720, 480);
    scene.getStylesheets().add(WorksheetWindow.STYLESHEET);
    ThemeBinding.bind(scene, theme);
    stage.setScene(scene);
    list.getSelectionModel().selectFirst();
  }

  /**
   * Shows the window in front, on a name's entry: the first entry of that name, the search cleared;
   * or, when there is none, the search set to the name and a line saying so.
   *
   * @param name the name, or null for the entry already chosen
   */
  public void show(String name) {
    if (name != null) {
      Optional<HelpEntry> entry = HelpEntries.lookup(name);
      if (entry.isPresent()) {
        search.setText("");
        list.getSelectionModel().select(entry.get());
        list.scrollTo(entry.get());
      } else {
        search.setText(name);
        list.getSelectionModel().clearSelection();
        detail.getChildren().setAll(new Label(Messages.text("help.none", name)));
      }
    }
    stage.show();
    stage.toFront();
  }

  private void showEntry(HelpEntry entry) {
    detail.getChildren().clear();
    if (entry == null) {
      return;
    }
    Label name = new Label(entry.name());
    name.getStyleClass().add("help-name");
    name.setId("help-name");
    detail.getChildren().add(name);
    boolean gettingStarted = entry.section().equals(HelpEntries.GETTING_STARTED);
    if (!gettingStarted) {
      detail
          .getChildren()
          .add(muted(Messages.text("help.category", entry.category(), entry.section())));
    }
    if (!entry.arity().isEmpty()) {
      detail.getChildren().add(muted(Messages.text("help.arity", entry.arity())));
    }
    if (!entry.alias().isEmpty()) {
      detail.getChildren().add(muted(Messages.text("help.alias", entry.alias())));
    }
    if (!entry.description().isEmpty()) {
      Label description = new Label(entry.description());
      description.setWrapText(true);
      description.setId("help-description");
      detail.getChildren().add(description);
    }
    if (gettingStarted) {
      Hyperlink open = new Hyperlink(Messages.text("help.welcome"));
      open.setId("help-welcome");
      open.setTooltip(new Tooltip(Messages.text("help.welcome.tip")));
      open.setOnAction(e -> welcome.run());
      detail.getChildren().add(open);
    }
    Label examples = new Label(Messages.text("help.examples"));
    examples.getStyleClass().add("help-heading");
    detail.getChildren().add(examples);
    for (HelpEntry.Example x : entry.examples()) {
      Hyperlink link = new Hyperlink(x.input());
      link.getStyleClass().add("help-example");
      link.setTooltip(new Tooltip(Messages.text("help.example.tip")));
      link.setAccessibleText(Messages.text("help.example.accessible", x.input()));
      link.setOnAction(e -> run.accept(x.input()));
      HBox row = new HBox(4, link, new Label(Messages.text("help.result", x.result())));
      detail.getChildren().add(row);
    }
  }

  private static Label muted(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("help-muted");
    label.setWrapText(true);
    return label;
  }

  /**
   * The window's stage, for the tests.
   *
   * @return the stage
   */
  public Stage stage() {
    return stage;
  }

  /**
   * The entry chosen, for the tests.
   *
   * @return the entry, or null
   */
  public HelpEntry chosen() {
    return list.getSelectionModel().getSelectedItem();
  }

  /**
   * The search field, for the tests.
   *
   * @return the field
   */
  public TextField searchField() {
    return search;
  }
}
