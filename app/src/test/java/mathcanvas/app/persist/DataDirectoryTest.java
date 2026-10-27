package mathcanvas.app.persist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * Every path is built from segments under the temporary directory, which is absolute on every
 * platform, and compared as a {@code Path}: a POSIX literal such as {@code /tmp/a} is {@code
 * D:\tmp\a} on Windows, and {@code /x} is not absolute there.
 */
class DataDirectoryTest {

  private static final Path ROOT = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath();
  private static final Path HOME = ROOT.resolve("home").resolve("u");
  private static final UnaryOperator<String> NONE = name -> null;

  private static UnaryOperator<String> map(Map<String, String> m) {
    return m::get;
  }

  /** An absolute path under the root. */
  private static Path abs(String name) {
    return ROOT.resolve(name);
  }

  @Test
  void theArgumentComesFirst() throws Exception {
    Path p =
        DataDirectory.resolve(
            List.of("-x", "--data-dir=" + abs("a"), "--data-dir=" + abs("b")),
            map(Map.of(DataDirectory.PROPERTY, abs("p").toString())),
            NONE,
            "Mac OS X",
            HOME);

    assertThat(p).isEqualTo(abs("a"));
  }

  @Test
  void theSpaceSeparatedArgumentWorksToo() throws Exception {
    assertThat(
            DataDirectory.resolve(
                List.of("--data-dir", abs("s").toString(), "x"), NONE, NONE, "Linux", HOME))
        .isEqualTo(abs("s"));
  }

  @Test
  void thePropertyComesNext() throws Exception {
    Path p =
        DataDirectory.resolve(
            List.of("other"),
            map(Map.of(DataDirectory.PROPERTY, abs("p").toString())),
            NONE,
            "Linux",
            HOME);

    assertThat(p).isEqualTo(abs("p"));
  }

  @Test
  void aDirectoryAskedForButNotGivenIsAnErrorNeverThePlatformFolder() {
    UnaryOperator<String> property = map(Map.of(DataDirectory.PROPERTY, abs("p").toString()));
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
        .isEqualTo(HOME.resolve("Library").resolve("Application Support").resolve("math-canvas"));
  }

  @Test
  void windowsUsesAppDataOrRoaming() throws Exception {
    assertThat(
            DataDirectory.resolve(
                List.of(),
                NONE,
                map(Map.of("APPDATA", abs("appdata").toString())),
                "Windows 11",
                HOME))
        .isEqualTo(abs("appdata").resolve("math-canvas"));
    assertThat(DataDirectory.resolve(List.of(), NONE, NONE, "Windows 11", HOME))
        .isEqualTo(HOME.resolve("AppData").resolve("Roaming").resolve("math-canvas"));
  }

  @Test
  void linuxUsesAnAbsoluteXdgDataHomeOrLocalShare() throws Exception {
    assertThat(
            DataDirectory.resolve(
                List.of(),
                NONE,
                map(Map.of("XDG_DATA_HOME", abs("xdg").toString())),
                "Linux",
                HOME))
        .isEqualTo(abs("xdg").resolve("math-canvas"));
    assertThat(
            DataDirectory.resolve(
                List.of(), NONE, map(Map.of("XDG_DATA_HOME", "relative")), "Linux", HOME))
        .isEqualTo(HOME.resolve(".local").resolve("share").resolve("math-canvas"));
    assertThat(DataDirectory.resolve(List.of(), NONE, NONE, "FreeBSD", HOME))
        .isEqualTo(HOME.resolve(".local").resolve("share").resolve("math-canvas"));
  }

  @Test
  void theFirstArgumentNamingADirectoryIsFoundAmongOthers() throws Exception {
    assertThat(DataDirectory.argument(List.of("a", "--data-dir=d", "--data-dir=e"))).contains("d");
    assertThat(DataDirectory.argument(List.of("a", "--data-dirx"))).isEmpty();
  }

  @Test
  void theOtherArgumentsLeaveOutTheDataDirectory() {
    assertThat(
            DataDirectory.otherArguments(
                List.of("a", "--data-dir", "d", "b", "--data-dir=e", "--data-dirx")))
        .containsExactly("a", "b", "--data-dirx");
  }
}
