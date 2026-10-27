package mathcanvas.app.persist;

import java.math.BigInteger;
import java.util.List;
import java.util.function.UnaryOperator;
import mathcanvas.app.json.Json;

/**
 * The migrations of one kind of file: the current schema version and, for each earlier version
 * {@code n}, the step that turns a version-{@code n} file into a version-{@code n + 1} one. A step
 * rewrites the JSON tree only, before anything is decoded, so decoding knows only the current
 * shape. A new version adds its step here and keeps the fixture of the version before it.
 *
 * @param current the version this release writes, at least 1
 * @param steps the step from version {@code n} to {@code n + 1} at index {@code n - 1}
 */
public record Migrations(int current, List<UnaryOperator<Json.Obj>> steps) {

  /** The member that holds a file's version. */
  public static final String VERSION = "version";

  /**
   * Copies the steps and checks there is one per earlier version.
   *
   * @throws IllegalArgumentException if the counts do not agree
   */
  public Migrations {
    steps = List.copyOf(steps);
    if (current < 1 || steps.size() != current - 1) {
      throw new IllegalArgumentException("one step per version before " + current);
    }
  }

  /**
   * Brings a file's tree to the current version.
   *
   * @param root the file's root value
   * @return the tree at the current version, its version member set to it
   * @throws FileFormatException if the root is not an object, its version is missing or not a
   *     positive integer, or it is newer than the current version
   */
  public Json.Obj migrate(Json root) throws FileFormatException {
    if (!(root instanceof Json.Obj obj)) {
      throw new FileFormatException("the file is not a JSON object");
    }
    int version = version(obj);
    if (version > current) {
      throw new FileFormatException(
          "the file is version " + version + ", newer than this release reads (" + current + ")");
    }
    Json.Obj out = obj;
    for (int v = version; v < current; v++) {
      out = steps.get(v - 1).apply(out).with(VERSION, Json.Num.of(v + 1));
    }
    return out;
  }

  private static int version(Json.Obj obj) throws FileFormatException {
    if (!(obj.member(VERSION).orElse(null) instanceof Json.Num(String text))
        || !text.matches("[1-9][0-9]*")) {
      throw new FileFormatException("the file has no version that is a positive integer");
    }
    BigInteger v = new BigInteger(text);
    return v.bitLength() < 31 ? v.intValue() : Integer.MAX_VALUE;
  }
}
