package mathcanvas.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The engine's printed {@code double} values are the same on every platform: outside the compiled
 * plotting evaluators ({@code compile/}), whose values are never printed, no source calls a {@code
 * java.lang.Math} function that HotSpot may replace by a platform intrinsic differing in the last
 * place ({@code StrictMath}, fdlibm, is specified bit for bit). The golden corpora pin 16 digits.
 */
class StrictMathTest {

  private static final Pattern INTRINSIC =
      Pattern.compile(
          "(?<![\\w.])Math\\.(pow|exp|log|log10|log1p|expm1|sin|cos|tan|asin|acos|atan|atan2"
              + "|sinh|cosh|tanh|cbrt|hypot)\\(");

  @Test
  void noPlatformMathOutsideTheCompiledEvaluators() throws IOException {
    Path root = Path.of("src", "main", "java");
    Path compile = root.resolve(Path.of("mathcanvas", "engine", "compile"));
    assertThat(root).isDirectory();
    List<String> hits = new ArrayList<>();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
        if (file.startsWith(compile)) {
          continue;
        }
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
          if (INTRINSIC.matcher(lines.get(i)).find()) {
            hits.add(root.relativize(file) + ":" + (i + 1) + ": " + lines.get(i).strip());
          }
        }
      }
    }
    assertThat(hits).isEmpty();
  }
}
