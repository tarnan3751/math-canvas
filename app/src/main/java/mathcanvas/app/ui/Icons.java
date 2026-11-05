package mathcanvas.app.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;
import java.util.Set;
import javafx.scene.layout.Region;
import javafx.scene.shape.SVGPath;

/**
 * The window's icons: Material Symbols, outlined (Apache License 2.0, {@code
 * legal/Material-Symbols-LICENSE.txt}), as SVG path data in the resource {@code icons.properties},
 * drawn as shapes rather than from an icon font. An icon is a region the size of the icon whose
 * shape is the symbol's path, painted in the colour the stylesheet gives {@code .icon}, so it
 * follows the theme and the state of the control it is in.
 */
public final class Icons {

  /** The icon size, in pixels. */
  static final double SIZE = 20;

  private static final Properties PATHS = load();

  private Icons() {}

  private static Properties load() {
    Properties paths = new Properties();
    try (InputStream in = Icons.class.getResourceAsStream("/mathcanvas/app/icons.properties")) {
      if (in == null) {
        throw new IllegalStateException("the icons are missing: /mathcanvas/app/icons.properties");
      }
      paths.load(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return paths;
  }

  /**
   * An icon's SVG path data.
   *
   * @param name the symbol's name, {@code settings}
   * @return the path data, in the symbol's own coordinates
   * @throws IllegalArgumentException if there is no such icon
   */
  static String path(String name) {
    String path = PATHS.getProperty(name);
    if (path == null) {
      throw new IllegalArgumentException("no icon " + name);
    }
    return path;
  }

  /**
   * The names of the icons there are.
   *
   * @return the names
   */
  static Set<String> names() {
    return PATHS.stringPropertyNames();
  }

  /**
   * An icon's path as a shape.
   *
   * @param name the symbol's name
   * @return the path
   * @throws IllegalArgumentException if there is no such icon
   */
  public static SVGPath get(String name) {
    SVGPath shape = new SVGPath();
    shape.setContent(path(name));
    return shape;
  }

  /**
   * An icon to show: a {@value #SIZE} px region shaped as the symbol, with the style class {@code
   * icon}.
   *
   * @param name the symbol's name
   * @return the region
   * @throws IllegalArgumentException if there is no such icon
   */
  public static Region icon(String name) {
    Region icon = new Region();
    icon.setShape(get(name));
    icon.setScaleShape(true);
    icon.setMinSize(SIZE, SIZE);
    icon.setPrefSize(SIZE, SIZE);
    icon.setMaxSize(SIZE, SIZE);
    icon.getStyleClass().add("icon");
    return icon;
  }
}
