package mathcanvas.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import mathcanvas.app.document.Theme;
import mathcanvas.app.persist.DataDirectory;
import org.junit.jupiter.api.Test;

class MessagesTest {

  private static final ResourceBundle BUNDLE =
      ResourceBundle.getBundle("mathcanvas.app.messages", Locale.ROOT);

  /**
   * Every key the main sources name in a {@code Messages.text("…")} call, and the computed ones.
   */
  private static List<String> keysUsed() throws IOException {
    List<String> keys = new ArrayList<>();
    Pattern call = Pattern.compile("Messages\\.text\\(\\s*\"([^\"]+)\"");
    try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
      for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        Matcher m = call.matcher(Files.readString(f));
        while (m.find()) {
          if (!m.group(1).endsWith(".")) {
            keys.add(m.group(1)); // a prefix ("theme." + …) is covered by its enum below
          }
        }
      }
    }
    for (Theme t : Theme.values()) {
      keys.add("theme." + t.name());
    }
    for (DataDirectory.Problem p : DataDirectory.Problem.values()) {
      keys.add("startup." + p.name());
    }
    return keys;
  }

  @Test
  void everyKeyTheCodeUsesIsInTheBundle() throws IOException {
    List<String> keys = keysUsed();

    assertThat(keys).contains("window.title", "output.cancelled");
    assertThat(keys).allSatisfy(k -> assertThat(BUNDLE.containsKey(k)).as(k).isTrue());
  }

  @Test
  void argumentsArePutIn() {
    assertThat(Messages.text("window.title", "1.2.3")).isEqualTo("math-canvas 1.2.3");
    assertThat(Messages.text("toolbar.theme", Messages.text("theme.DARK")))
        .isEqualTo("Theme: Dark");
  }
}
