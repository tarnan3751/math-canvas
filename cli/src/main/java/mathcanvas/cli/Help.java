package mathcanvas.cli;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * The help a {@code help(name)} statement prints: the entry the build generates from the language
 * reference into the resource {@code mathcanvas/cli/help.json} (build-logic's {@code GenerateHelp},
 * the app's Help window's source), read with Jackson's streaming parser, once.
 */
final class Help {

  private static final ResourceBundle MESSAGES =
      ResourceBundle.getBundle("mathcanvas.cli.messages", Locale.ROOT);

  /** Each name's first entry: a function's or a command's before an operator's of its spelling. */
  private static final class Entries {
    static final Map<String, List<String>> BY_NAME = read();

    private Entries() {}
  }

  private Help() {}

  /**
   * The lines a name's help prints: its name and category, its arguments, alias and description,
   * and its examples, each {@code input → result}; one line saying there is none for a name the
   * reference does not list.
   *
   * @param name the name
   * @return the lines
   */
  static List<String> lines(String name) {
    List<String> lines = Entries.BY_NAME.get(name);
    return lines != null ? lines : List.of(text("help.none", name));
  }

  private static String text(String key, Object... args) {
    return new MessageFormat(MESSAGES.getString(key), Locale.ROOT).format(args);
  }

  private static Map<String, List<String>> read() {
    Map<String, List<String>> out = new LinkedHashMap<>();
    try (InputStream in = Help.class.getResourceAsStream("help.json");
        JsonParser p = new JsonFactory().createParser(in)) {
      expect(p.nextToken(), JsonToken.START_OBJECT);
      while (p.nextToken() == JsonToken.FIELD_NAME) {
        String field = p.currentName();
        p.nextToken();
        if (!field.equals("entries")) {
          p.skipChildren();
          continue;
        }
        expect(p.currentToken(), JsonToken.START_ARRAY);
        while (p.nextToken() == JsonToken.START_OBJECT) {
          Map<String, String> entry = new LinkedHashMap<>();
          List<String> examples = new ArrayList<>();
          while (p.nextToken() == JsonToken.FIELD_NAME) {
            String member = p.currentName();
            p.nextToken();
            if (member.equals("examples")) {
              while (p.nextToken() == JsonToken.START_OBJECT) {
                String input = "";
                String result = "";
                while (p.nextToken() == JsonToken.FIELD_NAME) {
                  String part = p.currentName();
                  p.nextToken();
                  if (part.equals("input")) {
                    input = p.getText();
                  } else if (part.equals("result")) {
                    result = p.getText();
                  }
                }
                examples.add("  " + input + " → " + result);
              }
            } else {
              entry.put(member, p.getText());
            }
          }
          out.putIfAbsent(entry.get("name"), shown(entry, examples));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return Map.copyOf(out);
  }

  private static List<String> shown(Map<String, String> entry, List<String> examples) {
    List<String> lines = new ArrayList<>();
    lines.add(text("help.entry", entry.get("name"), entry.get("category"), entry.get("section")));
    if (!entry.getOrDefault("arity", "").isEmpty()) {
      lines.add("  " + text("help.arity", entry.get("arity")));
    }
    if (!entry.getOrDefault("alias", "").isEmpty()) {
      lines.add("  " + text("help.alias", entry.get("alias")));
    }
    if (!entry.getOrDefault("description", "").isEmpty()) {
      lines.add("  " + entry.get("description"));
    }
    lines.add("  " + text("help.examples"));
    lines.addAll(examples);
    return List.copyOf(lines);
  }

  private static void expect(JsonToken token, JsonToken wanted) throws IOException {
    if (token != wanted) {
      throw new IOException("help.json: " + wanted + " expected, " + token + " found");
    }
  }
}
