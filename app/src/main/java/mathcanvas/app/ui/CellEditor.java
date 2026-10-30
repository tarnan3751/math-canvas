package mathcanvas.app.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.control.skin.TextAreaSkin;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

/**
 * A cell's input: a text area as tall as its lines (a cell may hold several statements, one per
 * line), with the bracket at the caret and its partner marked, an unmatched one in the error colour
 * ({@link Brackets}), and typing a closing bracket over the same bracket moving past it. The caret
 * and the selection are the text area's own. Touched only on the FX thread.
 */
public final class CellEditor extends StackPane {

  private final TextArea area = new TextArea();
  private final Rectangle mark = new Rectangle();
  private final Rectangle partner = new Rectangle();

  /** An empty editor. */
  public CellEditor() {
    area.setPrefRowCount(1);
    area.setWrapText(false);
    area.getStyleClass().add("cell-input");
    Pane overlay = new Pane(mark, partner);
    overlay.setMouseTransparent(true);
    overlay.setManaged(true);
    StackPane.setAlignment(overlay, Pos.TOP_LEFT);
    mark.setVisible(false);
    partner.setVisible(false);
    getChildren().addAll(area, overlay);

    area.textProperty()
        .addListener(
            (o, was, now) ->
                area.setPrefRowCount((int) now.chars().filter(c -> c == '\n').count() + 1));
    // after the next layout, when the skin's character bounds are up to date
    area.caretPositionProperty().addListener(o -> Platform.runLater(this::markBrackets));
    area.textProperty().addListener(o -> Platform.runLater(this::markBrackets));
    area.focusedProperty().addListener(o -> Platform.runLater(this::markBrackets));
    // the scene's focus owner moves even while the window lacks the platform's focus
    area.sceneProperty()
        .flatMap(Scene::focusOwnerProperty)
        // a change listener: an invalidation listener on a lazy binding fires only once
        .addListener((o, was, now) -> Platform.runLater(this::markBrackets));
    area.addEventFilter(KeyEvent.KEY_TYPED, this::overClosing);
  }

  /**
   * The text area.
   *
   * @return the area
   */
  public TextArea area() {
    return area;
  }

  /** Typing a closing bracket when the next character is that bracket moves over it. */
  private void overClosing(KeyEvent e) {
    String typed = e.getCharacter();
    int caret = area.getCaretPosition();
    if (typed.length() == 1
        && Brackets.isClosing(typed.charAt(0))
        && area.getSelection().getLength() == 0
        && caret < area.getLength()
        && area.getText().charAt(caret) == typed.charAt(0)) {
      area.positionCaret(caret + 1);
      e.consume();
    }
  }

  /** Marks the bracket at the caret and its partner, while the area has the focus. */
  private void markBrackets() {
    mark.setVisible(false);
    partner.setVisible(false);
    // the scene's focus owner, which a window without the platform's focus still has
    boolean owner = area.getScene() != null && area.getScene().getFocusOwner() == area;
    if (!owner || !(area.getSkin() instanceof TextAreaSkin skin)) {
      return;
    }
    Brackets.at(area.getText(), area.getCaretPosition())
        .ifPresent(
            m -> {
              place(mark, skin, m.at(), m.matched() ? "bracket-match" : "bracket-unmatched");
              if (m.matched()) {
                place(partner, skin, m.partner(), "bracket-match");
              }
            });
  }

  private void place(Rectangle r, TextAreaSkin skin, int index, String styleClass) {
    Rectangle2D b = skin.getCharacterBounds(index);
    r.setX(b.getMinX());
    r.setY(b.getMinY());
    r.setWidth(b.getWidth());
    r.setHeight(b.getHeight());
    r.getStyleClass().setAll(styleClass);
    r.setVisible(true);
  }

  /**
   * Where the bracket marks are, for the tests.
   *
   * @return the mark at the caret's bracket and the mark at its partner
   */
  public Rectangle[] marks() {
    return new Rectangle[] {mark, partner};
  }
}
