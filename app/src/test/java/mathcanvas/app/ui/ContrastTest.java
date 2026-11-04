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
 * The app's text colours of both themes against the backgrounds they are drawn on, read from {@code
 * app.css}: every pair at least 4.5:1, WCAG 2's contrast for normal text. The controls' own text
 * Modena derives from {@code -fx-base} for contrast itself.
 */
class ContrastTest {

  private static final List<String> TEXTS =
      List.of("-mc-text", "-mc-muted", "-mc-error", "-mc-link");

  /** Where the app's own text colours are drawn: the window and the fields and lists. */
  private static final List<String> BACKGROUNDS =
      List.of("-fx-background", "-fx-control-inner-background");

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
    }
  }
}
