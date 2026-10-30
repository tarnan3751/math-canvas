package mathcanvas.canvas.view;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javax.imageio.ImageIO;

/** What the canvas UI tests share: screenshots and synthetic mouse events. */
final class UiHarness {

  /** Where the screenshots go. */
  static final Path SCREENSHOTS = Path.of("build", "ui-screenshots");

  private UiHarness() {}

  /** Writes an image as a PNG. */
  static void write(WritableImage image, Path file) throws IOException {
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

  /**
   * Fires a mouse event at a node's local point, with the primary button down for a press or a
   * drag. FX thread.
   */
  static void mouse(Node node, EventType<MouseEvent> type, double x, double y) {
    Point2D scene = node.localToScene(x, y);
    Point2D screen = node.localToScreen(x, y);
    boolean down = type == MouseEvent.MOUSE_PRESSED || type == MouseEvent.MOUSE_DRAGGED;
    node.fireEvent(
        new MouseEvent(
            type,
            scene.getX(),
            scene.getY(),
            screen.getX(),
            screen.getY(),
            down || type == MouseEvent.MOUSE_RELEASED ? MouseButton.PRIMARY : MouseButton.NONE,
            down ? 1 : 0,
            false,
            false,
            false,
            false,
            down,
            false,
            false,
            true,
            false,
            type != MouseEvent.MOUSE_DRAGGED,
            null));
  }
}
