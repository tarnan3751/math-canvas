package mathcanvas.app.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import mathcanvas.app.document.Language;
import mathcanvas.app.document.NumberFormat;
import mathcanvas.app.document.Theme;
import mathcanvas.app.persist.DataDirectory;
import mathcanvas.app.ui.KeypadKeys;
import mathcanvas.engine.eval.AngleMode;
import org.junit.jupiter.api.Test;

class MessagesTest {

  private static final ResourceBundle BUNDLE =
      ResourceBundle.getBundle("mathcanvas.app.messages", Locale.ROOT);

  /**
   * Every key the main sources name in a {@code Messages.text("…")} call, and the computed ones.
   */
  private static List<String> keysUsed() throws IOException {
    List<String> keys = new ArrayList<>();
    // a text call, or an entry refused by its key
    Pattern call = Pattern.compile("(?:Messages\\.text|refused)\\(\\s*\"([^\"]+)\"");
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
    for (AngleMode m : AngleMode.values()) {
      keys.add("angle." + m.name());
    }
    for (NumberFormat f : NumberFormat.values()) {
      keys.add("format." + f.name());
    }
    for (KeypadKeys.Key k : KeypadKeys.keys()) {
      keys.add("key." + k.id());
    }
    for (Language l : Language.values()) {
      keys.add("language." + l.name());
    }
    for (DataDirectory.Problem p : DataDirectory.Problem.values()) {
      keys.add("startup." + p.name());
    }
    return keys;
  }

  @Test
  void everyKeyTheCodeUsesIsInTheBundle() throws IOException {
    List<String> keys = keysUsed();

    assertThat(keys).contains("window.title", "output.cancelled", "entry.point", "row.cycle");
    assertThat(keys).allSatisfy(k -> assertThat(BUNDLE.containsKey(k)).as(k).isTrue());
  }

  @Test
  void theFrenchBundleTranslatesTheToolbarAndTheMenusAndFallsBackForTheRest() throws IOException {
    ResourceBundle french = ResourceBundle.getBundle("mathcanvas.app.messages", Locale.FRENCH);
    Properties own = new Properties();
    try (InputStream in =
        MessagesTest.class.getResourceAsStream("/mathcanvas/app/messages_fr.properties")) {
      own.load(new InputStreamReader(in, StandardCharsets.ISO_8859_1));
    }
    // every French key is a key of the root bundle, with the same arguments
    for (String key : own.stringPropertyNames()) {
      assertThat(BUNDLE.containsKey(key)).as(key).isTrue();
      assertThat(arguments(own.getProperty(key)))
          .as(key)
          .isEqualTo(arguments(BUNDLE.getString(key)));
    }
    for (String key : Collections.list(BUNDLE.getKeys())) {
      if (key.startsWith("toolbar.") || key.startsWith("menu.")) {
        assertThat(own.stringPropertyNames()).as("French %s", key).contains(key);
      }
    }
    assertThat(french.getString("menu.file")).isEqualTo("Fichier");
    assertThat(french.getString("cell.prompt")).isEqualTo(BUNDLE.getString("cell.prompt"));
    assertThat(Language.ENGLISH.locale()).isEqualTo(Locale.ROOT);
    assertThat(Language.FRENCH.locale()).isEqualTo(Locale.FRENCH);
  }

  /** The argument indexes a pattern names. */
  private static java.util.Set<String> arguments(String pattern) {
    java.util.Set<String> out = new java.util.TreeSet<>();
    Matcher m = Pattern.compile("\\{(\\d+)").matcher(pattern);
    while (m.find()) {
      out.add(m.group(1));
    }
    return out;
  }

  @Test
  void argumentsArePutIn() {
    assertThat(Messages.text("window.title", "1.2.3")).isEqualTo("math-canvas 1.2.3");
    assertThat(Messages.text("toolbar.theme", Messages.text("theme.DARK")))
        .isEqualTo("Theme: Dark");
  }
}
