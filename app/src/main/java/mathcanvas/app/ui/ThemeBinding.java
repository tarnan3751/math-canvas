package mathcanvas.app.ui;

import javafx.application.ColorScheme;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.scene.Scene;
import mathcanvas.app.document.Theme;

/**
 * Applies the theme to a scene: the root has the style class {@code dark} when the theme is dark,
 * or when it is the system theme and the scene's colour scheme ({@link Scene.Preferences}, which
 * follows the platform's) is dark. The stylesheet keys its colours on that class.
 */
public final class ThemeBinding {

  /** The style class on the root of a dark scene. */
  public static final String DARK = "dark";

  private ThemeBinding() {}

  /**
   * Keeps a scene's root in step with a theme and with the platform's colour scheme.
   *
   * @param scene the scene
   * @param theme the theme
   */
  public static void bind(Scene scene, ObjectProperty<Theme> theme) {
    InvalidationListener apply = o -> apply(scene, theme.get());
    theme.addListener(apply);
    scene.getPreferences().colorSchemeProperty().addListener(apply);
    apply(scene, theme.get());
  }

  private static void apply(Scene scene, Theme theme) {
    boolean dark =
        switch (theme) {
          case DARK -> true;
          case LIGHT -> false;
          case SYSTEM -> scene.getPreferences().getColorScheme() == ColorScheme.DARK;
        };
    var classes = scene.getRoot().getStyleClass();
    if (dark && !classes.contains(DARK)) {
      classes.add(DARK);
    } else if (!dark) {
      classes.remove(DARK);
    }
  }
}
