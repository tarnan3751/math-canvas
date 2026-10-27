package mathcanvas.app.persist;

import java.util.LinkedHashMap;
import java.util.List;
import mathcanvas.app.document.DocumentSettings;
import mathcanvas.app.document.NumberFormat;
import mathcanvas.app.document.Preferences;
import mathcanvas.app.document.Theme;
import mathcanvas.app.json.Json;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.eval.Settings;

/**
 * The settings file, {@code settings.json}: the document settings and the app's preferences.
 * Version 1:
 *
 * <pre>{@code
 * {
 *   "version": 1,
 *   "precision": 34,
 *   "angleMode": "radians",
 *   "numberFormat": "exact",
 *   "theme": "system"
 * }
 * }</pre>
 *
 * <p>The angle mode is {@code radians}, {@code degrees} or {@code gradians}; the number format
 * {@code exact}, {@code decimal}, {@code scientific} or {@code fraction}; the theme {@code system},
 * {@code light} or {@code dark}. Reading is strict, as for the session file.
 */
public final class SettingsFile {

  /** The file's name in the data directory. */
  public static final String NAME = "settings.json";

  /** The versions of the file and the steps between them. */
  public static final Migrations MIGRATIONS = new Migrations(1, List.of());

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
    root.put("theme", Fields.text(stored.preferences().theme()));
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
            Fields.constant(obj, "numberFormat", NumberFormat.class));
    return new Stored(settings, new Preferences(Fields.constant(obj, "theme", Theme.class)));
  }
}
