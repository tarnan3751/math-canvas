package mathcanvas.app;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/**
 * The startup benchmark as a person meets it: the app opened as {@link StartupBenchmarkTest} opens
 * it, but {@code 1 + 1} typed a second after the first cell is ready, and the first result timed
 * from the typing. The engine loads while the window waits ({@code Document.warmUp}), so this is
 * the figure the warm-up is for. A class of its own, since only the first launch in a JVM is cold:
 * {@code tools/measure.sh} runs it in five JVMs of its own. The first launch is <b>cold</b>; five
 * more in the same JVM are <b>warm</b>, with their median. Tagged {@code benchmark}, it runs only
 * when asked ({@code -Pbenchmarks}) and asserts no budget; the figures are printed and written to
 * {@code build/ui-screenshots/startup-typed-later.txt}.
 */
@Tag("benchmark")
@ExtendWith(ApplicationExtension.class)
class StartupTypedLaterBenchmarkTest {

  private static final Path REPORT = Path.of("build", "ui-screenshots", "startup-typed-later.txt");
  private static final int WARM = 5;
  private static final long PAUSE_MILLIS = 1000;

  @Test
  void theFirstResultOfInputTypedASecondLater() throws Exception {
    long cold = StartupBenchmarkTest.launch(System.nanoTime(), PAUSE_MILLIS)[3];
    long[] warm = new long[WARM];
    for (int k = 0; k < WARM; k++) {
      warm[k] = StartupBenchmarkTest.launch(System.nanoTime(), PAUSE_MILLIS)[3];
    }
    Arrays.sort(warm);
    String report =
        String.join(
            "\n",
            line("cold, typed a second after ready", cold),
            line("warm, median of " + WARM + ", typed a second after ready", warm[WARM / 2]));
    System.out.println(report);
    Files.createDirectories(REPORT.getParent());
    Files.writeString(REPORT, report + "\n", StandardCharsets.UTF_8);
  }

  private static String line(String what, long nanos) {
    return String.format(
        Locale.ROOT,
        "startup-typed-later: %-48s first result since the typing %8.1f ms",
        what,
        nanos / 1e6);
  }
}
