package mathcanvas.app.ui;

import javafx.scene.control.DialogPane;
import javafx.stage.Window;

/**
 * Gives a dialog the window's look: its owner's stylesheets and the root's style classes ({@code
 * root}, {@code dark}), so the dialog takes the theme's tokens and is drawn as a Material dialog.
 */
final class DialogStyle {

  private DialogStyle() {}

  /**
   * Styles a dialog's pane after its owner's scene.
   *
   * @param pane the dialog's pane
   * @param owner the window it belongs to, or null
   */
  static void apply(DialogPane pane, Window owner) {
    if (owner != null && owner.getScene() != null) {
      pane.getStylesheets().addAll(owner.getScene().getStylesheets());
      pane.getStyleClass().addAll(owner.getScene().getRoot().getStyleClass());
    }
  }
}
