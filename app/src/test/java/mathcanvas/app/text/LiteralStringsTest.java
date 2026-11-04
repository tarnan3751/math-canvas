package mathcanvas.app.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every user-visible text goes through the bundles: no string literal with a letter in it is handed
 * to a control's text, title, prompt, tooltip or accessible name in the main sources, in a call
 * that does not read the bundle ({@code Messages.text}). A literal that must stay (a symbol, not
 * words) goes in the baseline with its file and text.
 */
class LiteralStringsTest {

  /**
   * A call that shows its text argument, a setter or a control's constructor, with its argument
   * list up to the parenthesis that closes it, across lines; a parenthesis inside a string literal
   * is skipped.
   */
  private static final Pattern SHOWN =
      Pattern.compile(
          "(?:\\.(?:setText|setPromptText|setTitle|setHeaderText|setContentText|setAccessibleText"
              + "|setAccessibleHelp|setAccessibleRoleDescription)"
              + "|new (?:Label|Button|ToggleButton|CheckBox|RadioButton|MenuItem|Menu|CheckMenuItem"
              + "|RadioMenuItem|Tooltip|Hyperlink|TitledPane|Tab|ButtonType|Notice))\\s*\\(");

  private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

  /** The literals allowed today, as {@code file: text}; none. */
  private static final Set<String> BASELINE = Set.of();

  @Test
  void noUserVisibleLiteralBypassesTheBundles() throws IOException {
    List<String> found = new ArrayList<>();
    try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
      for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        String source = Files.readString(f);
        Matcher call = SHOWN.matcher(source);
        while (call.find()) {
          // a call whose text comes from the bundle names keys, not texts
          String args = arguments(source, call.end());
          if (args.contains("Messages.text(")) {
            continue;
          }
          Matcher literal = LITERAL.matcher(args);
          while (literal.find()) {
            // letters, not escapes ("\\n")
            if (literal.group(1).replaceAll("\\\\.", "").matches("(?s).*\\p{L}.*")) {
              String entry = f.getFileName() + ": " + literal.group(1);
              if (!BASELINE.contains(entry)) {
                found.add(entry);
              }
            }
          }
        }
      }
    }

    assertThat(found).as("literal texts shown outside the bundles").isEmpty();
  }

  /** The text from an opening parenthesis to the one that closes it, string literals skipped. */
  private static String arguments(String source, int from) {
    int depth = 1;
    for (int k = from; k < source.length(); k++) {
      char c = source.charAt(k);
      if (c == '"') {
        for (k++; k < source.length() && source.charAt(k) != '"'; k++) {
          if (source.charAt(k) == '\\') {
            k++;
          }
        }
      } else if (c == '(') {
        depth++;
      } else if (c == ')' && --depth == 0) {
        return source.substring(from, k);
      }
    }
    return source.substring(from);
  }

  @Test
  void theCheckFindsALiteralOverSeveralLines() {
    String source =
        "    button.setText(\n        flag\n            ? \"Save (now)\"\n            : x);\n";
    Matcher call = SHOWN.matcher(source);
    assertThat(call.find()).isTrue();
    String args = arguments(source, call.end());
    assertThat(args).contains("Save (now)").endsWith(": x");
    Matcher literal = LITERAL.matcher(args);
    assertThat(literal.find()).isTrue();
    assertThat(literal.group(1)).isEqualTo("Save (now)");
  }
}
