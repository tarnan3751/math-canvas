package mathcanvas.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The Material tokens' text colours of both themes against the backgrounds they are drawn on, read
 * from {@code app.css} ({@code docs/reference/design.md}, **Tokens**): every pair at least 4.5:1,
 * WCAG 2's contrast for normal text.
 */
class ContrastTest {

  private static final List<String> TEXTS =
      List.of("-mc-on-surface", "-mc-on-surface-variant", "-mc-error", "-mc-link", "-mc-primary");

  /**
   * Where those colours are drawn: the window, the cards, the digit keys and hovered cards, the
   * selected toggles, the canvas plane.
   */
  private static final List<String> BACKGROUNDS =
      List.of(
          "-mc-surface",
          "-mc-surface-container",
          "-mc-surface-container-high",
          "-mc-secondary-container",
          "-mc-plane");

  /** The text colours drawn only on their own container: filled and tonal buttons, error cells. */
  private static final List<List<String>> PAIRS =
      List.of(
          List.of("-mc-on-primary", "-mc-primary"),
          List.of("-mc-on-primary-container", "-mc-primary-container"),
          List.of("-mc-on-primary-container", "-mc-secondary-container"),
          List.of("-mc-error", "-mc-error-container"),
          List.of("-mc-on-surface", "-mc-primary-container"));

  private static String css() throws IOException {
    try (InputStream in = ContrastTest.class.getResourceAsStream("/mathcanvas/app/app.css")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  /** The colours a rule of the stylesheet defines, by name. */
  private static Map<String, String> colours(String css, String selector) {
    Matcher rule =
        Pattern.compile("(?m)^" + Pattern.quote(selector) + "\\s*\\{([^}]*)}").matcher(css);
    assertThat(rule.find()).as(selector).isTrue();
    Map<String, String> out = new HashMap<>();
    Matcher m = Pattern.compile("(-[\\w-]+)\\s*:\\s*(#[0-9a-fA-F]{6})\\s*;").matcher(rule.group(1));
    while (m.find()) {
      out.put(m.group(1), m.group(2));
    }
    return out;
  }

  /** WCAG 2's relative luminance of an sRGB colour. */
  static double luminance(String hex) {
    double[] c = new double[3];
    for (int k = 0; k < 3; k++) {
      double v = Integer.parseInt(hex.substring(1 + 2 * k, 3 + 2 * k), 16) / 255.0;
      c[k] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
  }

  static double contrast(String a, String b) {
    double la = luminance(a);
    double lb = luminance(b);
    return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
  }

  @Test
  void theFormulaIsWcags() {
    assertThat(contrast("#000000", "#ffffff"))
        .isCloseTo(21.0, org.assertj.core.data.Offset.offset(1e-9));
    assertThat(contrast("#777777", "#ffffff"))
        .isCloseTo(4.48, org.assertj.core.data.Offset.offset(0.01));
  }

  @Test
  void everyTextColourReadsOnEveryBackgroundOfBothThemes() throws IOException {
    String css = css();
    for (String theme : List.of(".root", ".root.dark")) {
      Map<String, String> colours = colours(css, theme);
      for (String text : TEXTS) {
        for (String background : BACKGROUNDS) {
          assertThat(colours).as(theme).containsKeys(text, background);
          assertThat(contrast(colours.get(text), colours.get(background)))
              .as("%s %s on %s", theme, text, background)
              .isGreaterThanOrEqualTo(4.5);
        }
      }
      for (List<String> pair : PAIRS) {
        assertThat(colours).as(theme).containsKeys(pair.get(0), pair.get(1));
        assertThat(contrast(colours.get(pair.get(0)), colours.get(pair.get(1))))
            .as("%s %s on %s", theme, pair.get(0), pair.get(1))
            .isGreaterThanOrEqualTo(4.5);
      }
    }
  }
}
