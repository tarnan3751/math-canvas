package mathcanvas.app.ui;

import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import mathcanvas.app.document.Preferences;
import mathcanvas.app.document.Theme;

/**
 * The app's preferences as properties the window binds to: the theme, whether steps are shown,
 * whether the keypad is shown, whether the canvas is shown. They change outside the undo history.
 * Touched only on the FX thread.
 */
public final class PreferenceProperties {

  private final ObjectProperty<Theme> theme = new SimpleObjectProperty<>(this, "theme");
  private final BooleanProperty showSteps = new SimpleBooleanProperty(this, "showSteps");
  private final BooleanProperty keypadShown = new SimpleBooleanProperty(this, "keypadShown");
  private final BooleanProperty canvasShown = new SimpleBooleanProperty(this, "canvasShown");

  /**
   * Properties holding preferences.
   *
   * @param initial the preferences
   */
  public PreferenceProperties(Preferences initial) {
    theme.set(initial.theme());
    showSteps.set(initial.showSteps());
    keypadShown.set(initial.keypadShown());
    canvasShown.set(initial.canvasShown());
  }

  /**
   * The preferences now.
   *
   * @return the preferences
   */
  public Preferences get() {
    return new Preferences(theme.get(), showSteps.get(), keypadShown.get(), canvasShown.get());
  }

  /**
   * Calls a listener whenever any preference changes.
   *
   * @param listener the listener
   */
  public void addListener(InvalidationListener listener) {
    theme.addListener(listener);
    showSteps.addListener(listener);
    keypadShown.addListener(listener);
    canvasShown.addListener(listener);
  }

  /**
   * The theme.
   *
   * @return the property
   */
  public ObjectProperty<Theme> theme() {
    return theme;
  }

  /**
   * Whether every evaluation is traced, so each value shows its steps.
   *
   * @return the property
   */
  public BooleanProperty showSteps() {
    return showSteps;
  }

  /**
   * Whether the keypad is shown.
   *
   * @return the property
   */
  public BooleanProperty keypadShown() {
    return keypadShown;
  }

  /**
   * Whether the canvas is shown beside the worksheet: the canvas mode, which the View menu and the
   * toolbar toggle and which enables what works on the canvas (its menu items, as they land).
   *
   * @return the property
   */
  public BooleanProperty canvasShown() {
    return canvasShown;
  }
}
