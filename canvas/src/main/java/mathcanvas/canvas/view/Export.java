package mathcanvas.canvas.view;

import java.util.function.ToDoubleFunction;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.GridPainter;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.canvas.paint.SvgPainter;
import mathcanvas.canvas.space.Camera;
import mathcanvas.canvas.space.SpaceScene;

/**
 * The plane as a picture, drawn by the same calls as the screen: the theme's background, then the
 * grid, axes and labels ({@link GridPainter}, with the tick labels measured as the caller says, so
 * the view's own {@link CanvasView#labelWidth} gives the view's ticks), then the content. The
 * overlay (the hovered point, selections, drag previews) is interaction state and is left out.
 *
 * <p>{@link #svg} writes an SVG document through {@link SvgPainter}, on any thread the label
 * measure may run on (the FX thread for the view's). {@link #image} draws at a scale on a canvas of
 * its own, so the picture is as sharp at four times the view's size as at its size, and runs on the
 * FX thread; writing the image to a file is the caller's, off it. The 3D view is exported the same
 * way, its scene painted at rest through its camera over the theme's background.
 */
public final class Export {

  /** The longest side of an image, in pixels: larger canvases exceed the textures JavaFX has. */
  public static final int MAX_IMAGE_SIDE = 8192;

  private Export() {}

  /**
   * The plane as an SVG document the viewport's pixel size.
   *
   * @param viewport the viewport
   * @param theme the colours of the background, the grid and the labels
   * @param content the objects, drawn in their own colours
   * @param labelWidth a tick label's width in pixels at {@link GridPainter#LABEL_SIZE}
   * @return the document
   */
  public static String svg(
      Viewport viewport,
      CanvasTheme theme,
      PlotContent content,
      ToDoubleFunction<String> labelWidth) {
    SvgPainter p = new SvgPainter(viewport);
    paint(p, theme, content, labelWidth);
    return p.document();
  }

  /**
   * Whether an image at a scale fits {@link #MAX_IMAGE_SIDE}.
   *
   * @param viewport the viewport
   * @param scale the scale
   * @return true if both sides fit
   */
  public static boolean fits(Viewport viewport, double scale) {
    return pixels(viewport.width(), scale) <= MAX_IMAGE_SIDE
        && pixels(viewport.height(), scale) <= MAX_IMAGE_SIDE;
  }

  /**
   * The plane as an image, the viewport's pixel size times a scale (rounded up), drawn at that
   * scale. FX thread.
   *
   * @param viewport the viewport
   * @param theme the colours of the background, the grid and the labels
   * @param content the objects, drawn in their own colours
   * @param labelWidth a tick label's width in pixels at {@link GridPainter#LABEL_SIZE}
   * @param scale pixels of the image to a pixel of the viewport, positive
   * @return the image
   * @throws IllegalArgumentException if the scale is not positive or the image would not fit
   */
  public static WritableImage image(
      Viewport viewport,
      CanvasTheme theme,
      PlotContent content,
      ToDoubleFunction<String> labelWidth,
      double scale) {
    if (!(scale > 0) || !fits(viewport, scale)) {
      throw new IllegalArgumentException("no image at scale " + scale);
    }
    Canvas canvas = new Canvas(pixels(viewport.width(), scale), pixels(viewport.height(), scale));
    GraphicsContext gc = canvas.getGraphicsContext2D();
    gc.scale(scale, scale);
    paint(new FxPainter(gc, viewport), theme, content, labelWidth);
    SnapshotParameters parameters = new SnapshotParameters();
    parameters.setFill(Color.TRANSPARENT);
    return canvas.snapshot(parameters, null);
  }

  private static int pixels(double size, double scale) {
    return (int) Math.min(Integer.MAX_VALUE, Math.ceil(size * scale));
  }

  private static void paint(
      Painter p, CanvasTheme theme, PlotContent content, ToDoubleFunction<String> labelWidth) {
    Viewport v = p.viewport();
    p.beginPath();
    p.moveTo(v.xMin(), v.yMin());
    p.lineTo(v.xMax(), v.yMin());
    p.lineTo(v.xMax(), v.yMax());
    p.lineTo(v.xMin(), v.yMax());
    p.closePath();
    p.fill(theme.background());
    GridPainter.paint(p, theme, labelWidth);
    content.paint(p);
  }

  // The 3D view as a picture.

  /**
   * The viewport whose world coordinates are the pixels of a view of a size, which the 3D view's
   * scene is painted through.
   *
   * @param width the view's width in pixels
   * @param height the view's height in pixels
   * @return the viewport
   */
  public static Viewport pixelViewport(double width, double height) {
    return new Viewport(0, width, 0, height, width, height, false);
  }

  /**
   * The 3D view as an SVG document of a pixel size: the theme's background, then the scene at rest
   * (its full meshes) through the camera.
   *
   * @param scene the scene, its objects in their own colours
   * @param camera the camera
   * @param theme the colours of the background, the box and the labels
   * @param width the width in pixels
   * @param height the height in pixels
   * @param labelWidth a tick label's width in pixels
   * @return the document
   */
  public static String svg(
      SpaceScene scene,
      Camera camera,
      CanvasTheme theme,
      double width,
      double height,
      ToDoubleFunction<String> labelWidth) {
    SvgPainter p = new SvgPainter(pixelViewport(width, height));
    SpaceView.background(p, theme.background());
    scene.paint(p, camera, theme, false, labelWidth);
    return p.document();
  }

  /**
   * The 3D view as an image, its pixel size times a scale (rounded up), drawn at that scale. FX
   * thread.
   *
   * @param scene the scene
   * @param camera the camera
   * @param theme the colours of the background, the box and the labels
   * @param width the width in pixels
   * @param height the height in pixels
   * @param labelWidth a tick label's width in pixels
   * @param scale pixels of the image to a pixel of the view, positive
   * @return the image
   * @throws IllegalArgumentException if the scale is not positive or the image would not fit
   */
  public static WritableImage image(
      SpaceScene scene,
      Camera camera,
      CanvasTheme theme,
      double width,
      double height,
      ToDoubleFunction<String> labelWidth,
      double scale) {
    Viewport viewport = pixelViewport(width, height);
    if (!(scale > 0) || !fits(viewport, scale)) {
      throw new IllegalArgumentException("no image at scale " + scale);
    }
    Canvas canvas = new Canvas(pixels(width, scale), pixels(height, scale));
    GraphicsContext gc = canvas.getGraphicsContext2D();
    gc.scale(scale, scale);
    FxPainter p = new FxPainter(gc, viewport);
    SpaceView.background(p, theme.background());
    scene.paint(p, camera, theme, false, labelWidth);
    SnapshotParameters parameters = new SnapshotParameters();
    parameters.setFill(Color.TRANSPARENT);
    return canvas.snapshot(parameters, null);
  }
}
