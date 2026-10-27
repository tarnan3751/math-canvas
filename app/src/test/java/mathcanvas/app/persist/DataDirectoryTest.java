package mathcanvas.app.persist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class DataDirectoryTest {

  private static final Path HOME = Path.of("/home/u");
  private static final UnaryOperator<String> NONE = name -> null;

  private static UnaryOperator<String> map(Map<String, String> m) {
    return m::get;
  }

  @Test
  void theArgumentComesFirst() throws Exception {
    Path p =
        DataDirectory.resolve(
            List.of("-x", "--data-dir=/tmp/a", "--data-dir=/tmp/b"),
            map(Map.of(DataDirectory.PROPERTY, "/tmp/p")),
            NONE,
            "Mac OS X",
            HOME);

    assertThat(p).isEqualTo(Path.of("/tmp/a"));
  }

  @Test
  void theSpaceSeparatedArgumentWorksToo() throws Exception {
    assertThat(
            DataDirectory.resolve(List.of("--data-dir", "/tmp/s", "x"), NONE, NONE, "Linux", HOME))
        .isEqualTo(Path.of("/tmp/s"));
  }

  @Test
  void thePropertyComesNext() throws Exception {
    Path p =
        DataDirectory.resolve(
            List.of("other"), map(Map.of(DataDirectory.PROPERTY, "/tmp/p")), NONE, "Linux", HOME);

    assertThat(p).isEqualTo(Path.of("/tmp/p"));
  }

  @Test
  void aDirectoryAskedForButNotGivenIsAnErrorNeverThePlatformFolder() {
    UnaryOperator<String> property = map(Map.of(DataDirectory.PROPERTY, "/tmp/p"));
    assertProblem(List.of("--data-dir="), property, DataDirectory.Problem.EMPTY_ARGUMENT);
    assertProblem(List.of("--data-dir=  "), property, DataDirectory.Problem.EMPTY_ARGUMENT);
    assertProblem(List.of("--data-dir", ""), property, DataDirectory.Problem.EMPTY_ARGUMENT);
    assertProblem(List.of("a", "--data-dir"), property, DataDirectory.Problem.MISSING_ARGUMENT);
    assertProblem(List.of("--data-dir", "--foo"), property, DataDirectory.Problem.MISSING_ARGUMENT);
    assertProblem(
        List.of(), map(Map.of(DataDirectory.PROPERTY, "")), DataDirectory.Problem.BLANK_PROPERTY);
    assertProblem(
        List.of(), map(Map.of(DataDirectory.PROPERTY, " ")), DataDirectory.Problem.BLANK_PROPERTY);
  }

  private static void assertProblem(
      List<String> args, UnaryOperator<String> property, DataDirectory.Problem problem) {
    assertThatThrownBy(() -> DataDirectory.resolve(args, property, NONE, "Mac OS X", HOME))
        .as(args.toString())
        .isInstanceOfSatisfying(
            DataDirectory.InvalidException.class, e -> assertThat(e.problem()).isEqualTo(problem));
  }

  @Test
  void aRelativeDirectoryIsResolvedAgainstTheWorkingDirectory() throws Exception {
    Path p = DataDirectory.resolve(List.of("--data-dir=rel"), NONE, NONE, "Linux", HOME);

    assertThat(p).isAbsolute().isEqualTo(Path.of("rel").toAbsolutePath());
  }

  @Test
  void macOsUsesApplicationSupport() throws Exception {
    assertThat(DataDirectory.resolve(List.of(), NONE, NONE, "Mac OS X", HOME))
        .isEqualTo(Path.of("/home/u/Library/Application Support/math-canvas"));
  }

  @Test
  void windowsUsesAppDataOrRoaming() throws Exception {
    assertThat(
            DataDirectory.resolve(
                List.of(), NONE, map(Map.of("APPDATA", "/appdata")), "Windows 11", HOME))
        .isEqualTo(Path.of("/appdata/math-canvas"));
    assertThat(DataDirectory.resolve(List.of(), NONE, NONE, "Windows 11", HOME))
        .isEqualTo(Path.of("/home/u/AppData/Roaming/math-canvas"));
  }

  @Test
  void linuxUsesAnAbsoluteXdgDataHomeOrLocalShare() throws Exception {
    assertThat(
            DataDirectory.resolve(
                List.of(), NONE, map(Map.of("XDG_DATA_HOME", "/xdg")), "Linux", HOME))
        .isEqualTo(Path.of("/xdg/math-canvas"));
    assertThat(
            DataDirectory.resolve(
                List.of(), NONE, map(Map.of("XDG_DATA_HOME", "relative")), "Linux", HOME))
        .isEqualTo(Path.of("/home/u/.local/share/math-canvas"));
    assertThat(DataDirectory.resolve(List.of(), NONE, NONE, "FreeBSD", HOME))
        .isEqualTo(Path.of("/home/u/.local/share/math-canvas"));
  }

  @Test
  void theFirstArgumentNamingADirectoryIsFoundAmongOthers() throws Exception {
    assertThat(DataDirectory.argument(List.of("a", "--data-dir=d", "--data-dir=e"))).contains("d");
    assertThat(DataDirectory.argument(List.of("a", "--data-dirx"))).isEmpty();
  }
}
