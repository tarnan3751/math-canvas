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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.animation.AnimationTimer;
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
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Piecewise;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.LayoutPrinter;
import mathcanvas.engine.print.layout.Layout;
import mathcanvas.engine.rule.Path;
import mathcanvas.render.MathView;
import mathcanvas.render.Typesetters;
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
    assertThat(took).isLessThan(Duration.ofSeconds(120));
  }

  private record Selection(Path path, Bounds highlight, Box box, Point2D origin) {}

  @Test
  void aBoxTypesetOnAnotherThreadDrawsAsTheLayoutDoes() throws Exception {
    Layout layout =
        display("∫[0, 1] x^2·e^(-x^2) dx + Σ[k=1,n] 1/k^2 + [[1/2, 2], [3, sqrt(x + 1)]]");
    // Typeset here, on the test thread, as a worker would, with a token.
    Box box = Typesetters.standard().typeset(layout, 20, true, Cancellation.NEVER);
    record Compared(
        int[] fromLayout,
        int[] fromBox,
        double width,
        double boxWidth,
        Path hitLayout,
        Path hitBox,
        Path selected) {}
    Compared c =
        waitFor(
            10,
            SECONDS,
            asyncFx(
                () -> {
                  MathView typeset = new MathView(layout);
                  MathView given = new MathView();
                  given.setBox(box);
                  root.getChildren().addAll(typeset, given);
                  root.applyCss();
                  root.layout();
                  Path hitLayout = typeset.hitTest(30, typeset.getHeight() / 2).orElse(null);
                  Path hitBox = given.hitTest(30, given.getHeight() / 2).orElse(null);
                  given.select(new Path(List.of(1)));
                  Path selected = given.getSelection();
                  given.select(null);
                  int[] a = pixels(typeset.snapshot(null, null));
                  int[] b = pixels(given.snapshot(null, null));
                  // A size change re-typesets a layout, not a box set from elsewhere.
                  given.fontSizeProperty().set(30);
                  double boxWidth = given.box().orElseThrow().width();
                  root.getChildren().removeAll(typeset, given);
                  return new Compared(a, b, box.width(), boxWidth, hitLayout, hitBox, selected);
                }));
    assertThat(c.fromBox()).isEqualTo(c.fromLayout());
    assertThat(c.hitBox()).isEqualTo(c.hitLayout());
    assertThat(c.selected()).isEqualTo(new Path(List.of(1)));
    assertThat(c.boxWidth()).isEqualTo(c.width());
  }

  @Test
  void aThousandNodeBoxBuildsItsNodesWithinTheBudget() throws Exception {
    // About 1,050 nodes, typeset off the FX thread; building the nodes typically takes 4-10 ms.
    String text = "x_1^2/(x + 1) + sqrt(y + 2)·sin(z) - [[1, 2], [3, 4]] + ".repeat(45) + "1";
    Layout layout =
        LayoutPrinter.INSTANCE.print(Parser.parseExpression(text, Set.of("f")::contains));
    Box box = Typesetters.standard().typeset(layout, 20, true, Cancellation.NEVER);
    Duration took =
        waitFor(
            30,
            SECONDS,
            asyncFx(
                () -> {
                  long start = System.nanoTime();
                  MathView view = new MathView();
                  view.setBox(box);
                  root.getChildren().add(view);
                  view.applyCss();
                  view.layout();
                  Duration d = Duration.ofNanos(System.nanoTime() - start);
                  root.getChildren().remove(view);
                  return d;
                }));
    assertThat(took).isLessThan(Duration.ofSeconds(120));
  }

  @Test
  void theLastOfALayoutAndABoxIsShown() throws Exception {
    Layout a = display("x/(2*pi) + sqrt(x + 1)");
    Layout b = display("1/3 + y^2");
    Box boxB = Typesetters.standard().typeset(b, 20, true, Cancellation.NEVER);
    record Seen(Layout layoutAfterBox, Box boxAfterBox, Layout layoutAfter, Box boxAfter) {}
    Seen seen =
        waitFor(
            10,
            SECONDS,
            asyncFx(
                () -> {
                  MathView view = new MathView();
                  view.setLayout(a);
                  view.setBox(boxB);
                  Layout layoutAfterBox = view.getLayout();
                  Box boxAfterBox = view.box().orElse(null);
                  view.setLayout(a);
                  return new Seen(
                      layoutAfterBox, boxAfterBox, view.getLayout(), view.box().orElse(null));
                }));
    assertThat(MathView.typesetter()).isSameAs(Typesetters.standard());
    assertThat(seen.layoutAfterBox()).isNull();
    assertThat(seen.boxAfterBox()).isSameAs(boxB);
    // Setting the same layout again after a box typesets it again.
    assertThat(seen.layoutAfter()).isSameAs(a);
    assertThat(seen.boxAfter()).isEqualTo(Typesetters.standard().typeset(a, 20, true));
  }

  @Test
  void workersTypesetMissingGlyphsWhileTheViewRetypesetsEveryPulse() throws Exception {
    // Text the font lacks is measured through JavaFX; the measurements are memoised, so workers
    // and the FX thread agree on them.
    Layout layout = display("一 + 二x/三");
    Box reference = Typesetters.standard().typeset(layout, 20, true);
    MathView view =
        waitFor(
            10,
            SECONDS,
            asyncFx(
                () -> {
                  MathView v = new MathView(layout);
                  root.getChildren().add(v);
                  return v;
                }));
    AnimationTimer pulse =
        new AnimationTimer() {
          private boolean big;

          @Override
          public void handle(long now) {
            big = !big;
            view.fontSizeProperty().set(big ? 21 : 20);
          }
        };
    waitFor(10, SECONDS, asyncFx(pulse::start));
    ExecutorService workers = Executors.newFixedThreadPool(4);
    AtomicInteger mismatches = new AtomicInteger();
    try {
      List<Future<?>> done = new ArrayList<>();
      for (int w = 0; w < 4; w++) {
        done.add(
            workers.submit(
                () -> {
                  for (int k = 0; k < 500; k++) {
                    if (!Typesetters.standard().typeset(layout, 20, true).equals(reference)) {
                      mismatches.incrementAndGet();
                    }
                  }
                }));
      }
      for (Future<?> f : done) {
        f.get(60, SECONDS);
      }
    } finally {
      workers.shutdownNow();
      waitFor(
          10,
          SECONDS,
          asyncFx(
              () -> {
                pulse.stop();
                root.getChildren().remove(view);
                return null;
              }));
    }
    assertThat(mismatches.get()).isZero();
  }

  private static int[] pixels(WritableImage image) {
    int width = (int) image.getWidth();
    int height = (int) image.getHeight();
    int[] argb = new int[width * height + 2];
    image
        .getPixelReader()
        .getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 2, width);
    argb[0] = width;
    argb[1] = height;
    return argb;
  }

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
