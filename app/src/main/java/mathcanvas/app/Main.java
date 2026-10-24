package mathcanvas.app;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import mathcanvas.engine.Version;
import mathcanvas.render.MathFont;

/**
 * The application entry point. In Phase 0 it opens one window showing the name and a line set in
 * the bundled math font; it ignores its arguments and reads and writes no files.
 */
public final class Main extends Application {

  /** Called by the JavaFX toolkit, which instantiates the application class itself. */
  public Main() {}

  @Override
  public void start(Stage stage) {
    Label name = new Label("math-canvas");
    Label sample = new Label("∫ x² dx");
    sample.setFont(MathFont.at(32));
    VBox root = new VBox(16, name, sample);
    root.setAlignment(Pos.CENTER);
    stage.setTitle("math-canvas " + Version.current());
    stage.setScene(new Scene(root, 640, 400));
    stage.show();
  }

  /**
   * Launches the application.
   *
   * @param args ignored
   */
  public static void main(String[] args) {
    launch(args);
  }
}
