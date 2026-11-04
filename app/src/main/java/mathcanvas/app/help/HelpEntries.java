package mathcanvas.app.help;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mathcanvas.app.json.Json;
import mathcanvas.app.json.JsonIo;

/**
 * The help entries the build generates from the language reference into the resource {@code
 * mathcanvas/app/help.json} (build-logic's {@code GenerateHelp}): the names of §12's tables, the
 * operators of §4.1, and their examples. Read once, on first use.
 */
public final class HelpEntries {

  /** The resource's path. */
  public static final String RESOURCE = "/mathcanvas/app/help.json";

  private HelpEntries() {}

  /**
   * Every entry, in the reference's order: the functions, the commands and the reserved names, then
   * the operators.
   *
   * @return the entries
   */
  public static List<HelpEntry> all() {
    return Standard.ENTRIES;
  }

  /**
   * The entry of a name: a function's, a command's or a reserved name's before an operator's of the
   * same spelling ({@code mod}).
   *
   * @param name the name
   * @return its entry, or empty when the reference has none
   */
  public static Optional<HelpEntry> lookup(String name) {
    return all().stream().filter(e -> e.name().equals(name)).findFirst();
  }

  /**
   * Reads the entries from the generated JSON.
   *
   * @param root the file's root value
   * @return the entries
   * @throws IllegalArgumentException if it is not the generated shape
   */
  static List<HelpEntry> decode(Json root) {
    List<HelpEntry> out = new ArrayList<>();
    for (Json item : array(member(root, "entries"))) {
      List<HelpEntry.Example> examples = new ArrayList<>();
      for (Json x : array(member(item, "examples"))) {
        examples.add(new HelpEntry.Example(text(member(x, "input")), text(member(x, "result"))));
      }
      out.add(
          new HelpEntry(
              text(member(item, "name")),
              text(member(item, "category")),
              text(member(item, "section")),
              text(member(item, "arity")),
              text(member(item, "alias")),
              text(member(item, "description")),
              examples));
    }
    return List.copyOf(out);
  }

  private static Json member(Json obj, String name) {
    if (!(obj instanceof Json.Obj o)) {
      throw new IllegalArgumentException("help.json: an object was expected");
    }
    return o.member(name)
        .orElseThrow(() -> new IllegalArgumentException("help.json: no member " + name));
  }

  private static List<Json> array(Json value) {
    if (!(value instanceof Json.Arr a)) {
      throw new IllegalArgumentException("help.json: an array was expected");
    }
    return a.items();
  }

  private static String text(Json value) {
    if (!(value instanceof Json.Str s)) {
      throw new IllegalArgumentException("help.json: a string was expected");
    }
    return s.value();
  }

  /** The generated resource, read once. */
  private static final class Standard {
    static final List<HelpEntry> ENTRIES = read();

    private Standard() {}

    private static List<HelpEntry> read() {
      try (InputStream in = HelpEntries.class.getResourceAsStream(RESOURCE)) {
        if (in == null) {
          throw new IllegalStateException("the build did not generate help.json");
        }
        return decode(JsonIo.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
