package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ListView;
import mathcanvas.app.document.Theme;
import mathcanvas.app.help.HelpEntries;
import mathcanvas.app.help.HelpEntry;
import mathcanvas.app.ui.HelpWindow;
import org.junit.jupiter.api.Test;

/**
 * The Help window: opened from the toolbar and by a {@code help(name)} cell on that name's entry,
 * searched, an example clicked and run as a new cell in one undo step, in both themes.
 */
class HelpWindowTest extends WorksheetHarness {

  private HelpWindow help() throws Exception {
    return get(() -> window.helpWindow());
  }

  @Test
  void helpOpensOnANameAndRunsItsExamples() throws Exception {
    click("help");
    waitUntil(10, () -> window.helpWindow() != null && window.helpWindow().stage().isShowing());
    assertThat(get(() -> help().chosen())).isNotNull();
    int all =
        get(() -> ((ListView<?>) help().stage().getScene().lookup("#help-list")).getItems().size());
    assertThat(all).isEqualTo(HelpEntries.all().size());

    // help(name) in a cell opens the window on the name's entry
    fx(() -> window.helpWindow().stage().hide());
    enter("help(abs)");
    waitForOutput(0, "abs");
    waitUntil(10, () -> help().stage().isShowing() && help().chosen().name().equals("abs"));

    // an example clicked runs as a new cell, one undo step
    HelpEntry abs = HelpEntries.lookup("abs").orElseThrow();
    HelpEntry.Example example = abs.examples().getFirst();
    fx(() -> ((Hyperlink) window.helpWindow().stage().getScene().lookup(".help-example")).fire());
    waitForOutput(1, example.result());
    assertThat(input(1)).isEqualTo(example.input());
    fx(() -> window.themeProperty().set(Theme.LIGHT));
    screenshot(get(() -> help().stage().getScene()), "help-light.png");
    fx(() -> window.themeProperty().set(Theme.DARK));
    screenshot(get(() -> help().stage().getScene()), "help-dark.png");
    fx(() -> main.document().undo());
    waitUntil(10, () -> window.cellViews().size() == 2);
    assertThat(input(1)).isEmpty();

    // the search keeps the names and descriptions that hold the text
    fx(() -> window.helpWindow().searchField().setText("eigen"));
    List<String> names =
        get(
            () ->
                ((ListView<?>) help().stage().getScene().lookup("#help-list"))
                    .getItems().stream().map(e -> ((HelpEntry) e).name()).toList());
    assertThat(names).contains("eigenvalues", "eigenvectors").doesNotContain("abs");

    // a name with no entry says so
    fx(() -> window.openHelp("nosuchname"));
    assertThat(get(() -> help().chosen())).isNull();
  }

  @Test
  void gettingStartedOpensTheWelcomeWorksheetInOneUndoStep() throws Exception {
    enter("1 + 1");
    waitForOutput(0, "2");
    click("help");
    waitUntil(10, () -> window.helpWindow() != null && window.helpWindow().stage().isShowing());
    fx(() -> window.helpWindow().searchField().setText(""));
    fx(
        () ->
            ((ListView<?>) window.helpWindow().stage().getScene().lookup("#help-list"))
                .getSelectionModel()
                .selectFirst());
    assertThat(get(() -> help().chosen().name())).isEqualTo("Getting started");
    assertThat(
            get(
                () ->
                    ((ListView<?>) window.helpWindow().stage().getScene().lookup("#help-list"))
                        .lookupAll(".list-cell").stream()
                            .map(c -> ((javafx.scene.control.ListCell<?>) c).getText())
                            .filter(t -> t != null && t.startsWith("Getting started"))
                            .toList()))
        .containsExactly("Getting started");

    fx(() -> ((Hyperlink) window.helpWindow().stage().getScene().lookup("#help-welcome")).fire());
    List<String> welcome = mathcanvas.app.document.Welcome.cells();
    waitUntil(10, () -> window.cellViews().size() == welcome.size() + 1);
    assertThat(input(1)).isEqualTo(welcome.get(1));
    waitUntil(
        30,
        () ->
            main.document().state().canvas().objects().stream()
                    .filter(o -> o instanceof mathcanvas.canvas.model.FunctionGraph)
                    .count()
                == 2);
    assertThat(get(() -> stage.getScene().lookup("#toggle-canvas").isVisible())).isTrue();

    // one undo gives the worksheet back
    fx(() -> main.document().undo());
    waitUntil(10, () -> window.cellViews().size() == 2);
    assertThat(input(0)).isEqualTo("1 + 1");
    assertThat(get(() -> main.document().state().canvas().objects())).isEmpty();
  }
}
