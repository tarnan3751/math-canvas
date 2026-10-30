package mathcanvas.app;

import java.nio.file.Path;
import java.util.List;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import mathcanvas.app.persist.DataDirectory;
import mathcanvas.app.ui.Messages;
import mathcanvas.app.ui.WorksheetWindow;

/**
 * The application entry point. It finds the data directory ({@code --data-dir=<dir>} or {@code
 * --data-dir <dir>}, the {@code mathcanvas.dataDir} property, or the platform's folder: {@link
 * DataDirectory}), loads the session and the settings from it, and opens the worksheet window. A
 * data directory that is asked for but not given stops the start with a message on standard error
 * and status 2; other arguments are reported and ignored. On exit the evaluation stops and what is
 * unsaved is written.
 */
public final class Main extends Application {

  /** The status a bad command line exits with. */
  private static final int USAGE = 2;

  private Workspace workspace;

  /** Called by the JavaFX toolkit, which instantiates the application class itself. */
  public Main() {}

  @Override
  public void start(Stage stage) {
    List<String> args = getParameters() == null ? List.of() : getParameters().getRaw();
    Path directory;
    try {
      directory = DataDirectory.resolve(args);
    } catch (DataDirectory.InvalidException e) {
      // main checks this before launching; reached only when another launcher starts the class
      System.err.println(Messages.text("startup." + e.problem().name()));
      Platform.exit();
      return;
    }
    start(stage, directory);
  }

  /**
   * Opens the window on a data directory: how the UI tests start the app on a directory of their
   * own.
   *
   * @param stage the stage
   * @param directory the data directory
   * @return the window
   */
  WorksheetWindow start(Stage stage, Path directory) {
    workspace = Workspace.open(directory, Platform::runLater);
    return new WorksheetWindow(
        stage, workspace.document(), workspace.preferences(), workspace.noticeProperty());
  }

  /** Stops the evaluation and writes what is unsaved. */
  @Override
  public void stop() {
    if (workspace != null) {
      workspace.close();
      workspace = null;
    }
  }

  /**
   * Checks the command line and launches the application.
   *
   * @param args {@code --data-dir=<dir>} or {@code --data-dir <dir>}; anything else is ignored
   */
  public static void main(String[] args) {
    List<String> list = List.of(args);
    try {
      DataDirectory.resolve(list);
    } catch (DataDirectory.InvalidException e) {
      System.err.println(Messages.text("startup." + e.problem().name()));
      System.exit(USAGE);
    }
    for (String a : DataDirectory.otherArguments(list)) {
      System.err.println(Messages.text("startup.unknownArgument", a));
    }
    launch(args);
  }
}
