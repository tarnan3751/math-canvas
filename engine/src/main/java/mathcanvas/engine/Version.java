package mathcanvas.engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A release version of the application: the window title, the CLI's {@code --version}, the
 * persistence schema migrations and the update check all read it.
 *
 * <p>The grammar is semantic versioning 2.0 without build metadata: {@code major.minor.patch},
 * optionally followed by {@code -qualifier}, where the qualifier is dot-separated identifiers of
 * {@code [0-9A-Za-z-]} and no number has a leading zero. Versions order by their components, a
 * qualified version before its release ({@code 0.1.0-SNAPSHOT < 0.1.0}), and qualifiers by semver
 * pre-release precedence. An empty {@code qualifier} means a release.
 *
 * @param major the major version, never negative
 * @param minor the minor version, never negative
 * @param patch the patch version, never negative
 * @param qualifier the pre-release qualifier, or the empty string for a release
 */
public record Version(int major, int minor, int patch, String qualifier)
    implements Comparable<Version> {

  private static final String NUMBER = "0|[1-9][0-9]*";
  // One identifier at a time: a repeated group over the whole qualifier recurses per
  // identifier and overflows the stack on a long one.
  private static final Pattern IDENTIFIER =
      Pattern.compile("(?:" + NUMBER + "|[0-9]*[A-Za-z-][0-9A-Za-z-]*)");
  private static final Pattern VERSION =
      Pattern.compile("(" + NUMBER + ")\\.(" + NUMBER + ")\\.(" + NUMBER + ")(?:-(.+))?");
  private static final Pattern NUMERIC = Pattern.compile("[0-9]+");

  /**
   * Checks the components.
   *
   * @throws IllegalArgumentException if a number is negative or the qualifier is malformed
   */
  public Version {
    if (major < 0 || minor < 0 || patch < 0) {
      throw new IllegalArgumentException(
          "negative version component: " + major + "." + minor + "." + patch);
    }
    if (qualifier == null) {
      throw new IllegalArgumentException("qualifier is null; a release has an empty qualifier");
    }
    if (!qualifier.isEmpty()) {
      for (String identifier : qualifier.split("\\.", -1)) {
        if (!IDENTIFIER.matcher(identifier).matches()) {
          throw new IllegalArgumentException("malformed version qualifier: " + qualifier);
        }
      }
    }
  }

  /**
   * Parses {@code major.minor.patch} or {@code major.minor.patch-qualifier}.
   *
   * @param text the version text
   * @return the version it denotes
   * @throws IllegalArgumentException if the text is not a version in that form
   */
  public static Version parse(String text) {
    Matcher matcher = VERSION.matcher(text);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("not a version (major.minor.patch[-qualifier]): " + text);
    }
    try {
      return new Version(
          Integer.parseInt(matcher.group(1)),
          Integer.parseInt(matcher.group(2)),
          Integer.parseInt(matcher.group(3)),
          matcher.group(4) == null ? "" : matcher.group(4));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("version component out of range: " + text, e);
    }
  }

  /**
   * Returns the version this build was made from, read from the resource the build generates.
   *
   * @return the running application's version
   */
  public static Version current() {
    return Current.VERSION;
  }

  @Override
  public int compareTo(Version other) {
    int order = Integer.compare(major, other.major);
    if (order == 0) {
      order = Integer.compare(minor, other.minor);
    }
    if (order == 0) {
      order = Integer.compare(patch, other.patch);
    }
    if (order == 0) {
      order = compareQualifiers(qualifier, other.qualifier);
    }
    return order;
  }

  @Override
  public String toString() {
    String release = major + "." + minor + "." + patch;
    return qualifier.isEmpty() ? release : release + "-" + qualifier;
  }

  private static int compareQualifiers(String left, String right) {
    if (left.isEmpty() || right.isEmpty()) {
      return Boolean.compare(left.isEmpty(), right.isEmpty());
    }
    List<String> lefts = List.of(left.split("\\.", -1));
    List<String> rights = List.of(right.split("\\.", -1));
    for (int i = 0; i < Math.min(lefts.size(), rights.size()); i++) {
      int order = compareIdentifiers(lefts.get(i), rights.get(i));
      if (order != 0) {
        return order;
      }
    }
    return Integer.compare(lefts.size(), rights.size());
  }

  private static int compareIdentifiers(String left, String right) {
    boolean leftNumeric = NUMERIC.matcher(left).matches();
    boolean rightNumeric = NUMERIC.matcher(right).matches();
    if (leftNumeric && rightNumeric) {
      // No leading zeros, so the longer number is the larger one.
      int order = Integer.compare(left.length(), right.length());
      return order != 0 ? order : left.compareTo(right);
    }
    if (leftNumeric != rightNumeric) {
      return leftNumeric ? -1 : 1;
    }
    return left.compareTo(right);
  }

  /** Reads the generated resource once, on first use. */
  private static final class Current {
    static final Version VERSION = read();

    private Current() {}

    private static Version read() {
      try (InputStream in = Version.class.getResourceAsStream("version.properties")) {
        if (in == null) {
          throw new IllegalStateException("the build did not generate version.properties");
        }
        Properties properties = new Properties();
        properties.load(in);
        return parse(properties.getProperty("version", ""));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
