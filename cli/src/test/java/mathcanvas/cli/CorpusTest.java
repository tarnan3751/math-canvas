package mathcanvas.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The golden corpora checked in-process, the mirror of {@code tools/run_corpus.sh}, so that {@code
 * ./gradlew build} covers them: every {@code *.mc} file under the directory the build passes as
 * {@code mathcanvas.corpus} ({@code engine/src/test/corpus}), its subdirectories included, as the
 * script finds them.
 */
class CorpusTest {

  static Stream<Path> files() throws IOException {
    Path dir = Path.of(System.getProperty("mathcanvas.corpus"));
    try (Stream<Path> walk = Files.walk(dir)) {
      return walk
          .filter(p -> p.toString().endsWith(".mc") && Files.isRegularFile(p))
          .sorted()
          .toList()
          .stream();
    }
  }

  @ParameterizedTest
  @MethodSource("files")
  void corpusFilePasses(Path file) throws Exception {
    Checker.Report r =
        Checker.check(
            file.toString(),
            Main.read(file),
            Options.parse(new String[] {"--check", "--unicode", file.toString()}));
    assertThat(r.diff()).as(file.toString()).isEmpty();
    assertThat(r.statements()).as(file.toString()).isPositive();
  }
}
