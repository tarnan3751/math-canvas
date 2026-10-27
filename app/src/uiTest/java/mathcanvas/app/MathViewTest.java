package mathcanvas.app;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.testfx.util.WaitForAsyncUtils.asyncFx;
import static org.testfx.util.WaitForAsyncUtils.waitFor;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Piecewise;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.LayoutPrinter;
import mathcanvas.engine.print.layout.Layout;
import mathcanvas.engine.rule.Path;
import mathcanvas.render.MathView;
import mathcanvas.render.typeset.Box;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * Typeset expressions in a harness window: each construct drawn from the font's outlines, a
 * screenshot written to {@code build/ui-screenshots/math-view.png}, and a click on a subexpression
 * selecting it. The click is a synthetic mouse event fired at the view, which runs the view's own
 * handler without the robot, so the test needs no macOS Accessibility permission; the screenshot is
 * a scene snapshot, as in {@link WorksheetWindowTest}.
 */
@ExtendWith(ApplicationExtension.class)
class MathViewTest {

  private static final java.nio.file.Path SCREENSHOTS =
      java.nio.file.Path.of("build", "ui-screenshots");

  private static final List<String> SAMPLES =
      List.of(
          "x_1^2 - cbrt(y) + 1/(x + 1)^2",
          "∫[0, 1] x^2·e^(-x^2) dx + Σ[k=1,n] 1/k^2",
          "lim[x → 0+] sin(x)/x + d/dx (x^2 sin(x))",
          "[[1, 2], [3, 4]]·⟨a, b⟩ + abs(1/(x + 1))");

  private Stage stage;
  private final VBox root = new VBox(18);
  private MathView first;

  private static Layout display(String text) {
    Expr e = Parser.parseExpression(text, Set.of("f")::contains);
    return LayoutPrinter.INSTANCE.display(Canonical.of(e));
  }

  @Start
  void start(Stage stage) {
    this.stage = stage;
    // The first view is a held tree, whose paths the click below names.
    root.getChildren()
        .add(
            new MathView(
                LayoutPrinter.INSTANCE.print(
                    Parser.parseExpression("x/(2*pi) + sqrt(x + 1)", n -> false))));
    for (String sample : SAMPLES) {
      root.getChildren().add(new MathView(display(sample)));
    }
    Expr piecewise =
        Piecewise.of(
            List.of(
                new Piecewise.Piece(Symbol.of("x"), Parser.parseExpression("x >= 0", n -> false))),
            Parser.parseExpression("-x", n -> false));
    root.getChildren().add(new MathView(LayoutPrinter.INSTANCE.print(piecewise)));
    first = (MathView) root.getChildren().getFirst();
    root.setPadding(new Insets(24));
    root.setStyle("-fx-background-color: white;");
    stage.setTitle("MathView");
    stage.setScene(new Scene(root, 900, 620));
    stage.show();
  }

  @Test
  void theViewsDrawTheirExpressionsAndAClickSelectsASubexpression() throws Exception {
    WritableImage image = waitFor(10, SECONDS, asyncFx(() -> stage.getScene().snapshot(null)));
    java.nio.file.Path screenshot = SCREENSHOTS.resolve("math-view.png");
    write(image, screenshot);
    assertThat(screenshot).isNotEmptyFile();

    // The first view is x/(2π) + √(x + 1): the fraction is path [0], its denominator 2π is
    // [0, 1, 0] and the π in it [0, 1, 0, 1]. A click at π's centre selects π; selecting the
    // denominator by its path highlights the denominator's box.
    Path pi = new Path(List.of(0, 1, 0, 1));
    Path denominator = new Path(List.of(0, 1, 0));
    Selection selection =
        waitFor(
            10,
            SECONDS,
            asyncFx(
                () -> {
                  Box box = first.box().orElseThrow().find(pi).orElseThrow();
                  Point2D origin = first.origin();
                  double x = origin.getX() + box.x() + box.width() / 2;
                  double y = origin.getY() + (box.top() + box.bottom()) / 2;
                  Point2D scene = first.localToScene(x, y);
                  Point2D screen = first.localToScreen(x, y);
                  first.fireEvent(
                      new MouseEvent(
                          MouseEvent.MOUSE_CLICKED,
                          scene.getX(),
                          scene.getY(),
                          screen.getX(),
                          screen.getY(),
                          MouseButton.PRIMARY,
                          1,
                          false,
                          false,
                          false,
                          false,
                          false,
                          false,
                          false,
                          true,
                          false,
                          true,
                          null));
                  Path clicked = first.getSelection();
                  first.select(denominator);
                  Box selected = first.box().orElseThrow().find(denominator).orElseThrow();
                  Bounds highlight = first.selectionBounds().orElse(null);
                  return new Selection(clicked, highlight, selected, origin);
                }));
    assertThat(selection.path()).isEqualTo(pi);
    assertThat(selection.highlight()).isNotNull();
    // The scene graph keeps bounds in single precision.
    assertThat(selection.highlight().getMinX())
        .isCloseTo(selection.origin().getX() + selection.box().x(), within(1e-3));
    assertThat(selection.highlight().getWidth()).isCloseTo(selection.box().width(), within(1e-3));
    WritableImage selected = waitFor(10, SECONDS, asyncFx(() -> stage.getScene().snapshot(null)));
    write(selected, SCREENSHOTS.resolve("math-view-selected.png"));

    // A click on empty space clears the selection.
    Path cleared =
        waitFor(
            10,
            SECONDS,
            asyncFx(
                () -> {
                  Point2D scene = first.localToScene(first.getWidth() + 5, 0);
                  first.fireEvent(
                      new MouseEvent(
                          MouseEvent.MOUSE_CLICKED,
                          scene.getX(),
                          scene.getY(),
                          scene.getX(),
                          scene.getY(),
                          MouseButton.PRIMARY,
                          1,
                          false,
                          false,
                          false,
                          false,
                          false,
                          false,
                          false,
                          true,
                          false,
                          true,
                          null));
                  return first.getSelection();
                }));
    assertThat(cleared).isNull();
  }

  @Test
  void aNewLayoutClearsTheSelectionAndATinySizeIsClamped() throws Exception {
    record State(Path afterBinding, double width, double clampedWidth) {}
    State state =
        waitFor(
            10,
            SECONDS,
            asyncFx(
                () -> {
                  MathView view = new MathView();
                  ObjectProperty<Layout> source = new SimpleObjectProperty<>(display("x + 1"));
                  view.layoutProperty().bind(source);
                  root.getChildren().add(view);
                  view.select(new Path(List.of(0)));
                  source.set(display("y + 2"));
                  Path after = view.getSelection();
                  view.fontSizeProperty().set(0);
                  double small = view.box().orElseThrow().width();
                  view.fontSizeProperty().set(MathView.MIN_FONT_SIZE);
                  double minimum = view.box().orElseThrow().width();
                  return new State(after, small, minimum);
                }));
    assertThat(state.afterBinding()).isNull();
    assertThat(state.width()).isPositive().isEqualTo(state.clampedWidth());
  }

  @Test
  void aThousandNodeViewBuildsWellWithinTheBudget() throws Exception {
    // About 1,050 nodes; typically 15-30 ms including the paths, each glyph scaled from the
    // font's outline cache with no pixel cache.
    String text = "x_1^2/(x + 1) + sqrt(y + 2)·sin(z) - [[1, 2], [3, 4]] + ".repeat(45) + "1";
    Layout layout =
        LayoutPrinter.INSTANCE.print(Parser.parseExpression(text, Set.of("f")::contains));
    Duration took =
        waitFor(
            30,
            SECONDS,
            asyncFx(
                () -> {
                  long start = System.nanoTime();
                  MathView view = new MathView(layout);
                  root.getChildren().add(view);
                  view.applyCss();
                  view.layout();
                  return Duration.ofNanos(System.nanoTime() - start);
                }));
    assertThat(took).isLessThan(Duration.ofSeconds(30));
  }

  private record Selection(Path path, Bounds highlight, Box box, Point2D origin) {}

  private static void write(WritableImage image, java.nio.file.Path file) throws IOException {
    int width = (int) image.getWidth();
    int height = (int) image.getHeight();
    int[] argb = new int[width * height];
    image
        .getPixelReader()
        .getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 0, width);
    BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    out.setRGB(0, 0, width, height, argb, 0, width);
    Files.createDirectories(file.getParent());
    if (!ImageIO.write(out, "png", file.toFile())) {
      throw new IOException("no PNG writer");
    }
  }
}
