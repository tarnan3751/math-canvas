package mathcanvas.canvas.view;

import java.util.Objects;
import java.util.function.Consumer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.space.Camera;
import mathcanvas.canvas.space.Projection;
import mathcanvas.canvas.space.SpaceScene;

/**
 * The 3D view as a node (docs/reference/canvas.md, **The 3D view**): one {@code Canvas} on which a
 * {@link SpaceScene} is painted through the camera, immediate-mode through the screen painter, once
 * per pulse after layout when something changed (the scene, the camera, the theme, the size).
 *
 * <p>A drag with the primary button orbits the camera ({@value #ORBIT} radians a pixel), a
 * Shift-drag pans its target with the picture, and the scroll wheel and the pinch gesture zoom.
 * While a drag lasts, and for {@value #SETTLE_MILLIS} ms after the last wheel or pinch step, the
 * view is {@link #movingProperty() moving} and draws the scene's coarse mesh, so the camera follows
 * the pointer at the frame rate; at rest it draws the full mesh. The colours are the {@link
 * #themeProperty() theme} the owner sets (the plane's, as its CSS gives them), the background
 * included. A press takes the focus, so the keys go to the view and its owner. FX thread only; the
 * scene is built elsewhere and handed over.
 */
public final class SpaceView extends Region {

  /** One painted frame, for the tests and the benchmark: whether coarse, and how long. */
  record Frame(long number, boolean coarse, long nanos) {}

  /** The orbit's radians per pixel dragged. */
  static final double ORBIT = 0.01;

  /** How long after the last wheel or pinch step the full mesh comes back, in milliseconds. */
  static final int SETTLE_MILLIS = 150;

  private final Canvas canvas = new Canvas();
  private final ObjectProperty<SpaceScene> scene =
      new SimpleObjectProperty<>(this, "scene", SpaceScene.EMPTY);
  private final ObjectProperty<Camera> camera =
      new SimpleObjectProperty<>(this, "camera", Camera.DEFAULT);
  private final ObjectProperty<CanvasTheme> theme =
      new SimpleObjectProperty<>(this, "theme", CanvasTheme.LIGHT);
  private final ReadOnlyBooleanWrapper moving = new ReadOnlyBooleanWrapper(this, "moving");
  private final PauseTransition settle = new PauseTransition(Duration.millis(SETTLE_MILLIS));
  private final Runnable pulse = this::paintDirty;
  private final Text measure = new Text();
  private boolean dirty = true;
  private boolean dragging;
  private double pressX;
  private double pressY;
  private long frames;
  private Consumer<Frame> frameListener;

  /** An empty view from the default camera. */
  public SpaceView() {
    getStyleClass().add("space-view");
    canvas.setMouseTransparent(true);
    getChildren().add(canvas);
    measure.setFont(Font.font(12));
    scene.addListener(
        (o, was, is) -> {
          Objects.requireNonNull(is, "scene");
          invalidate();
        });
    camera.addListener(
        (o, was, is) -> {
          Objects.requireNonNull(is, "camera");
          invalidate();
        });
    theme.addListener(
        (o, was, is) -> {
          Objects.requireNonNull(is, "theme");
          invalidate();
        });
    moving.addListener((o, was, is) -> invalidate());
    settle.setOnFinished(e -> moving.set(dragging));
    sceneProperty()
        .addListener(
            (o, was, is) -> {
              if (was != null) {
                was.removePostLayoutPulseListener(pulse);
              }
              if (is != null) {
                is.addPostLayoutPulseListener(pulse);
                invalidate();
              }
            });
    addEventHandler(MouseEvent.MOUSE_PRESSED, this::pressed);
    addEventHandler(MouseEvent.MOUSE_DRAGGED, this::dragged);
    addEventHandler(
        MouseEvent.MOUSE_RELEASED,
        e -> {
          if (dragging) {
            dragging = false;
            moving.set(false);
          }
        });
    addEventHandler(
        ScrollEvent.SCROLL,
        e -> {
          if (e.getDeltaY() != 0) {
            zoom(Math.pow(1.0015, e.getDeltaY()));
            e.consume();
          }
        });
    addEventHandler(
        ZoomEvent.ZOOM,
        e -> {
          zoom(e.getZoomFactor());
          e.consume();
        });
  }

  /**
   * The scene drawn.
   *
   * @return the property
   */
  public ObjectProperty<SpaceScene> sceneContentProperty() {
    return scene;
  }

  /**
   * The camera the scene is seen through; a drag or the wheel changes it.
   *
   * @return the property
   */
  public ObjectProperty<Camera> cameraProperty() {
    return camera;
  }

  /**
   * The camera.
   *
   * @return it
   */
  public Camera getCamera() {
    return camera.get();
  }

  /**
   * Sees the scene through another camera.
   *
   * @param value the camera, not null
   */
  public void setCamera(Camera value) {
    camera.set(value);
  }

  /**
   * The colours: the background, the box, the labels and the objects' series.
   *
   * @return the property
   */
  public ObjectProperty<CanvasTheme> themeProperty() {
    return theme;
  }

  /**
   * Whether the camera is moving, so the coarse mesh is drawn.
   *
   * @return the property
   */
  public ReadOnlyBooleanProperty movingProperty() {
    return moving.getReadOnlyProperty();
  }

  /**
   * The projection of the view's current camera onto its size.
   *
   * @return the projection
   */
  public Projection projection() {
    return new Projection(getCamera(), Math.max(1, getWidth()), Math.max(1, getHeight()));
  }

  /**
   * A tick label's width as the screen draws it, for the box's ticks. FX thread.
   *
   * @param label the label
   * @return its width in pixels
   */
  public double labelWidth(String label) {
    measure.setText(label);
    return measure.getLayoutBounds().getWidth();
  }

  @Override
  protected double computePrefWidth(double height) {
    return 640;
  }

  @Override
  protected double computePrefHeight(double width) {
    return 480;
  }

  @Override
  protected void layoutChildren() {
    double w = Math.max(1, Math.round(getWidth()));
    double h = Math.max(1, Math.round(getHeight()));
    if (canvas.getWidth() != w || canvas.getHeight() != h) {
      canvas.setWidth(w);
      canvas.setHeight(h);
      invalidate();
    }
  }

  /** Sets a listener told of every frame; package-private, for the tests and the benchmark. */
  void setFrameListener(Consumer<Frame> listener) {
    frameListener = listener;
  }

  /** The number of frames painted. */
  long frames() {
    return frames;
  }

  /** Sets whether the view draws as when moving; package-private, for the benchmark. */
  void setMoving(boolean value) {
    moving.set(value);
  }

  /** Marks the view to be painted again at the next pulse. */
  void invalidate() {
    dirty = true;
    if (getScene() != null) {
      Platform.requestNextPulse();
    }
  }

  private void paintDirty() {
    if (!dirty) {
      return;
    }
    dirty = false;
    long start = System.nanoTime();
    double w = canvas.getWidth();
    double h = canvas.getHeight();
    GraphicsContext gc = canvas.getGraphicsContext2D();
    gc.clearRect(0, 0, w, h);
    Viewport pixels = new Viewport(0, w, 0, h, w, h, false);
    FxPainter p = new FxPainter(gc, pixels);
    CanvasTheme t = theme.get();
    background(p, t.background());
    boolean coarse = moving.get();
    scene.get().paint(p, getCamera(), t, coarse, this::labelWidth);
    frames++;
    if (frameListener != null) {
      frameListener.accept(new Frame(frames, coarse, System.nanoTime() - start));
    }
  }

  /** The background, a path filling the view, as the plane's export draws it. */
  static void background(Painter p, Rgba colour) {
    Viewport v = p.viewport();
    p.beginPath();
    p.moveTo(v.xMin(), v.yMin());
    p.lineTo(v.xMax(), v.yMin());
    p.lineTo(v.xMax(), v.yMax());
    p.lineTo(v.xMin(), v.yMax());
    p.closePath();
    p.fill(colour);
  }

  private void pressed(MouseEvent e) {
    // a click in the view gives it the keys, so Escape reaches its owner after one
    requestFocus();
    if (e.getButton() == MouseButton.PRIMARY) {
      pressX = e.getX();
      pressY = e.getY();
      dragging = true;
    }
  }

  private void dragged(MouseEvent e) {
    if (!dragging) {
      return;
    }
    double dx = e.getX() - pressX;
    double dy = e.getY() - pressY;
    pressX = e.getX();
    pressY = e.getY();
    moving.set(true);
    Camera c = getCamera();
    if (e.isShiftDown()) {
      double scale = projection().scale(c.distance());
      setCamera(c.pan(dx / scale, -dy / scale));
    } else {
      setCamera(c.orbit(-dx * ORBIT, dy * ORBIT));
    }
  }

  private void zoom(double factor) {
    moving.set(true);
    settle.playFromStart();
    setCamera(getCamera().zoom(factor));
  }
}
