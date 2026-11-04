package mathcanvas.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * What the build says about the application, read from the resource {@code app.properties}, which
 * {@code app/build.gradle.kts} expands: the application's name, from the Gradle property {@code
 * appName} that also names the launcher and the installers, and the repository the update check
 * reads. The window title, the update notice and the other strings that name the application take
 * the name as an argument, so renaming the application is that property alone.
 */
public final class AppInfo {

  private AppInfo() {}

  /**
   * Returns the application's name.
   *
   * @return the name, {@code math-canvas} unless the build was given another
   */
  public static String name() {
    return Loaded.PROPERTIES.getProperty("name");
  }

  /**
   * Returns the GitHub repository whose releases the update check compares with.
   *
   * @return {@code <owner>/<repo>}
   */
  public static String repository() {
    return Loaded.PROPERTIES.getProperty("repository");
  }

  /** Reads the generated resource once, on first use. */
  private static final class Loaded {
    static final Properties PROPERTIES = read();

    private Loaded() {}

    private static Properties read() {
      Properties properties = new Properties();
      try (InputStream in = AppInfo.class.getResourceAsStream("/mathcanvas/app/app.properties")) {
        if (in == null) {
          throw new IllegalStateException("the build did not generate app.properties");
        }
        // processResources writes the expanded file in UTF-8, not load(InputStream)'s ISO-8859-1
        properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      return properties;
    }
  }
}
