package mathcanvas.engine.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * The parser corpus, {@code language.corpus}: every example of {@code docs/language.md} with its
 * parse form or its error and position. The file's header describes the format.
 */
class LanguageCorpusTest {

  private static final Pattern ERROR = Pattern.compile("error (\\d+):(\\d+): (.*)");
  private static final Pattern AFTER = Pattern.compile("\\[after ([^\\]]*)\\] (.*)");

  /** One corpus entry. */
  record Entry(int line, String setup, String input, String expected) {}

  /** The entries of the corpus, in file order. */
  static List<Entry> entries() {
    List<Entry> out = new ArrayList<>();
    try (InputStream in = LanguageCorpusTest.class.getResourceAsStream("language.corpus");
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String line;
      int number = 0;
      while ((line = reader.readLine()) != null) {
        number++;
        if (line.isBlank() || line.startsWith("#")) {
          continue;
        }
        int arrow = line.indexOf('⇒');
        String input = line.substring(0, arrow).strip();
        String expected = line.substring(arrow + 1).strip();
        String setup = "";
        Matcher after = AFTER.matcher(input);
        if (after.matches()) {
          setup = after.group(1);
          input = after.group(2);
        }
        out.add(new Entry(number, unescape(setup), unescape(input), expected));
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return out;
  }

  private static String unescape(String text) {
    StringBuilder out = new StringBuilder();
    for (int k = 0; k < text.length(); k++) {
      char c = text.charAt(k);
      if (c == '\\' && k + 1 < text.length() && text.charAt(k + 1) == 'n') {
        out.append('\n');
        k++;
      } else if (c == '\\' && k + 1 < text.length() && text.charAt(k + 1) == 't') {
        out.append('\t');
        k++;
      } else if (c == '\\' && k + 1 < text.length() && text.charAt(k + 1) == 'u') {
        out.append((char) Integer.parseInt(text.substring(k + 2, k + 6), 16));
        k += 5;
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  /** The user functions a setup script defines. */
  static Names names(String setup) {
    Set<String> functions = new HashSet<>();
    for (Statement s : Parser.parseScript(setup, Names.NONE).statements()) {
      if (s instanceof Statement.Definition d
          && (!d.params().isEmpty() || d.body() instanceof mathcanvas.engine.expr.Lambda)) {
        functions.add(d.name().name());
      }
    }
    return functions::contains;
  }

  /** The outcome of an entry in the corpus's own notation. */
  static String outcome(Entry entry) {
    try {
      Script script = Parser.parseScript(entry.input(), names(entry.setup()));
      if (entry.expected().startsWith("tree: ")) {
        return "tree: " + ((Statement.Expression) script.statements().getFirst()).expr().toString();
      }
      return script.statements().stream()
          .map(ParseFormPrinter::print)
          .collect(Collectors.joining(" | "));
    } catch (ParseException e) {
      return "error " + e.line() + ":" + e.column() + ": " + e.getMessage();
    }
  }

  @TestFactory
  List<DynamicTest> corpus() {
    return entries().stream()
        .map(
            entry ->
                DynamicTest.dynamicTest(
                    "line " + entry.line() + ": " + entry.input(),
                    () -> assertThat(outcome(entry)).isEqualTo(entry.expected())))
        .toList();
  }

  @Test
  void theCorpusIsLargeAndItsErrorsWellFormed() {
    List<Entry> entries = entries();
    assertThat(entries).hasSizeGreaterThan(400);
    for (Entry e : entries) {
      if (e.expected().startsWith("error")) {
        assertThat(ERROR.matcher(e.expected()).matches()).as(e.expected()).isTrue();
      }
    }
  }
}
