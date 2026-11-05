package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import mathcanvas.app.document.Cell;
import mathcanvas.app.document.Welcome;
import mathcanvas.app.persist.SessionFile;
import mathcanvas.app.persist.SettingsFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The first launch, an empty data directory, opens the welcome worksheet with the canvas shown,
 * once: the preference that says so outlives the worksheet.
 */
class WorkspaceWelcomeTest {

  /** The inputs of the worksheet a start opens; the results are never delivered. */
  private static List<String> start(Path directory) {
    Workspace w = Workspace.open(directory, r -> {}, Optional::empty);
    try {
      return w.document().state().cells().stream().map(Cell::input).toList();
    } finally {
      w.close();
    }
  }

  @Test
  void theFirstLaunchOpensTheWelcomeOnce(@TempDir Path directory) throws Exception {
    List<String> welcome = start(directory);
    assertThat(welcome.subList(0, welcome.size() - 1)).isEqualTo(Welcome.cells());
    assertThat(welcome.getLast()).isEmpty();
    assertThat(Files.readString(directory.resolve(SettingsFile.NAME)))
        .contains("\"welcomeShown\": true")
        .contains("\"canvasShown\": true");

    // it was saved: the next start opens it as any worksheet
    assertThat(start(directory)).isEqualTo(welcome);

    // with the worksheet gone, the next start is an empty worksheet, not the welcome again
    Files.delete(directory.resolve(SessionFile.NAME));
    assertThat(start(directory)).containsExactly("");
  }

  @Test
  void anUnreadableSessionIsNoFirstLaunch(@TempDir Path directory) throws Exception {
    Files.writeString(directory.resolve(SessionFile.NAME), "not json");
    assertThat(start(directory)).containsExactly("");
  }
}
