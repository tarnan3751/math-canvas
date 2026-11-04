package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import mathcanvas.app.document.DocumentSettings;
import mathcanvas.app.document.Preferences;
import mathcanvas.app.json.JsonIo;
import mathcanvas.app.persist.SettingsFile;
import mathcanvas.app.update.UpdateChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The update check runs at the start only when the person turned it on, and then once. */
class WorkspaceUpdateTest {

  private static void settings(Path directory, boolean check) throws Exception {
    Preferences p = Preferences.DEFAULT;
    Files.createDirectories(directory);
    Files.writeString(
        directory.resolve(SettingsFile.NAME),
        JsonIo.text(
            SettingsFile.encode(
                new SettingsFile.Stored(
                    DocumentSettings.DEFAULT,
                    new Preferences(
                        p.theme(),
                        p.showSteps(),
                        p.keypadShown(),
                        p.canvasShown(),
                        p.exportFolder(),
                        check,
                        p.language())))));
  }

  private static int checks(Path directory) throws Exception {
    AtomicInteger asked = new AtomicInteger();
    UpdateChecker counting =
        () -> {
          asked.incrementAndGet();
          return Optional.empty();
        };
    Workspace w = Workspace.open(directory, Runnable::run, counting);
    try {
      if (w.updateThread() != null) {
        w.updateThread().join(5_000);
      }
    } finally {
      w.close();
    }
    return asked.get();
  }

  @Test
  void offByDefaultNothingIsAsked(@TempDir Path directory) throws Exception {
    assertThat(checks(directory.resolve("new"))).isZero();
    settings(directory.resolve("off"), false);
    assertThat(checks(directory.resolve("off"))).isZero();
  }

  @Test
  void onTheCheckRunsOnce(@TempDir Path directory) throws Exception {
    settings(directory, true);
    assertThat(checks(directory)).isEqualTo(1);
  }
}
