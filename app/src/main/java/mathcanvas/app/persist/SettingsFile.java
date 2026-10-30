package mathcanvas.app.persist;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.UnaryOperator;
import mathcanvas.app.document.DocumentSettings;
import mathcanvas.app.document.NumberFormat;
import mathcanvas.app.document.Preferences;
import mathcanvas.app.document.Theme;
import mathcanvas.app.json.Json;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.eval.Settings;

/**
 * The settings file, {@code settings.json}: the document settings and the app's preferences.
 * Version 3:
 *
 * <pre>{@code
 * {
 *   "version": 3,
 *   "precision": 34,
 *   "angleMode": "radians",
 *   "numberFormat": "exact",
 *   "numberDigits": 10,
 *   "theme": "system",
 *   "showSteps": false,
 *   "keypadShown": true,
 *   "canvasShown": false
 * }
 * }</pre>
 *
 * <p>The angle mode is {@code radians}, {@code degrees} or {@code gradians}; the number format
 * {@code exact}, {@code decimal}, {@code scientific} or {@code fraction}, with {@code numberDigits}
 * (1 to 10,000) the digits of the decimal and scientific formats; the theme {@code system}, {@code
 * light} or {@code dark}. Reading is strict, as for the session file. Version 1 had no {@code
 * numberDigits}, {@code showSteps} or {@code keypadShown}; its migration adds their defaults (10,
 * false, true). Version 2 had no {@code canvasShown}; its migration adds false.
 */
public final class SettingsFile {

  /** The file's name in the data directory. */
  public static final String NAME = "settings.json";

  /**
   * The versions of the file and the steps between them: version 1 to 2 adds three defaults, 2 to 3
   * the canvas hidden.
   */
  public static final Migrations MIGRATIONS =
      new Migrations(
          3,
          List.<UnaryOperator<Json.Obj>>of(
              v1 ->
                  v1.with("numberDigits", Json.Num.of(DocumentSettings.DEFAULT_DIGITS))
                      .with("showSteps", new Json.Bool(false))
                      .with("keypadShown", new Json.Bool(true)),
              v2 -> v2.with("canvasShown", new Json.Bool(false))));

  /**
   * What the file holds.
   *
   * @param settings the document settings
   * @param preferences the app's preferences
   */
  public record Stored(DocumentSettings settings, Preferences preferences) {

    /** The defaults. */
    public static final Stored DEFAULT = new Stored(DocumentSettings.DEFAULT, Preferences.DEFAULT);
  }

  private SettingsFile() {}

  /**
   * The file's tree, at the current version.
   *
   * @param stored the settings and the preferences
   * @return the tree
   */
  public static Json.Obj encode(Stored stored) {
    LinkedHashMap<String, Json> root = new LinkedHashMap<>();
    root.put(Migrations.VERSION, Json.Num.of(MIGRATIONS.current()));
    root.put("precision", Json.Num.of(stored.settings().precision()));
    root.put("angleMode", Fields.text(stored.settings().angleMode()));
    root.put("numberFormat", Fields.text(stored.settings().numberFormat()));
    root.put("numberDigits", Json.Num.of(stored.settings().numberDigits()));
    root.put("theme", Fields.text(stored.preferences().theme()));
    root.put("showSteps", new Json.Bool(stored.preferences().showSteps()));
    root.put("keypadShown", new Json.Bool(stored.preferences().keypadShown()));
    root.put("canvasShown", new Json.Bool(stored.preferences().canvasShown()));
    return new Json.Obj(root);
  }

  /**
   * What a file holds, after migrating it to the current version.
   *
   * @param root the file's root value
   * @return the settings and the preferences
   * @throws FileFormatException if it is not a settings file this release reads
   */
  public static Stored decode(Json root) throws FileFormatException {
    Json.Obj obj = MIGRATIONS.migrate(root);
    DocumentSettings settings =
        new DocumentSettings(
            Fields.integer(obj, "precision", Settings.MIN_PRECISION, Settings.MAX_PRECISION),
            Fields.constant(obj, "angleMode", AngleMode.class),
            Fields.constant(obj, "numberFormat", NumberFormat.class),
            Fields.integer(obj, "numberDigits", 1, DocumentSettings.MAX_DIGITS));
    return new Stored(
        settings,
        new Preferences(
            Fields.constant(obj, "theme", Theme.class),
            Fields.bool(obj, "showSteps"),
            Fields.bool(obj, "keypadShown"),
            Fields.bool(obj, "canvasShown")));
  }
}
