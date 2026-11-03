package mathcanvas.canvas.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;
import javafx.css.StyleablePropertyFactory;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.hit.Hit;
import mathcanvas.canvas.hit.HitIndex;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.GridPainter;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.canvas.paint.Rgba;

/**
 * The coordinate plane as a node: three stacked {@code Canvas} layers ({@link Layer}), the grid and
 * axes, the plotted objects ({@link PlotContent}), and an interactive overlay showing the hovered
 * point, each drawn immediate-mode through a {@link Painter}.
 *
 * <p>Each layer has a dirty flag, and a frame repaints only the dirty layers: the content dirties
 * the objects layer, a change of hover the overlay, and a change of viewport (a pan, a zoom, a
 * resize) or of theme all three. Frames are drawn once per pulse, after layout, so any number of
 * changes within a pulse cost one repaint. Dragging on empty space pans, the scroll wheel and the
 * pinch gesture zoom about the pointer, and moving the pointer hit-tests the content (points first,
 * {@link HitIndex#DEFAULT_TOLERANCE} pixels) and shows the nearest point of what it finds, unless
 * it is an area. The grid's tick labels are measured in the font they are drawn in, so the x axis's
 * step leaves room for them.
 *
 * <p>The colours are the CSS properties {@code -canvas-axis}, {@code -canvas-grid}, {@code
 * -canvas-minor-grid}, {@code -canvas-text} and {@code -canvas-series-1} to {@code -8}, which the
 * user-agent stylesheet {@code canvas-view.css} sets for the light theme on {@code .canvas-view}
 * and the dark one on {@code .root.dark .canvas-view}; a theme overrides them in the scene's
 * stylesheet. Painting and every property are FX thread only; content is built elsewhere and handed
 * over.
 */
public final class CanvasView extends Region {

  /** One repainted frame, for the tests and the benchmark: which layers, and how long. */
  record Frame(long number, Set<Layer> painted, Map<Layer, Long> nanos) {}

  private static final StyleablePropertyFactory<CanvasView> CSS =
      new StyleablePropertyFactory<>(Region.getClassCssMetaData());

  private static final double HOVER_RADIUS = 5;
  private static final double HOVER_OPACITY = 0.6;

  private final Map<Layer, Canvas> layers = new EnumMap<>(Layer.class);
  private final Set<Layer> dirty = EnumSet.allOf(Layer.class);
  private final Runnable pulse = this::paintDirty;

  private final ObjectProperty<Viewport> viewport =
      new SimpleObjectProperty<>(this, "viewport", Viewport.centred(0, 0, 1 / 40.0, 1, 1, true));
  private final ObjectProperty<PlotContent> content =
      new SimpleObjectProperty<>(this, "content", PlotContent.EMPTY);
  private final ReadOnlyObjectWrapper<Hit> hover = new ReadOnlyObjectWrapper<>(this, "hoveredHit");
  private final ObjectProperty<Consumer<Painter>> overlay =
      new SimpleObjectProperty<>(this, "overlay");

  private final StyleableProperty<Color> axis =
      CSS.createStyleableColorProperty(
          this, "axisColor", "-canvas-axis", v -> v.axis, fx(CanvasTheme.LIGHT.axis()));
  private final StyleableProperty<Color> grid =
      CSS.createStyleableColorProperty(
          this, "gridColor", "-canvas-grid", v -> v.grid, fx(CanvasTheme.LIGHT.grid()));
  private final StyleableProperty<Color> minorGrid =
      CSS.createStyleableColorProperty(
          this,
          "minorGridColor",
          "-canvas-minor-grid",
          v -> v.minorGrid,
          fx(CanvasTheme.LIGHT.minorGrid()));
  private final StyleableProperty<Color> text =
      CSS.createStyleableColorProperty(
          this, "textColor", "-canvas-text", v -> v.text, fx(CanvasTheme.LIGHT.text()));
  private final List<StyleableProperty<Color>> series = new ArrayList<>();

  private final Text measure = new Text();
  private CanvasTheme theme;
  private long frames;
  private Consumer<Frame> frameListener;
  private double pressX;
  private double pressY;
  private boolean panning;
  private double pointerX = Double.NaN;
  private double pointerY = Double.NaN;

  /** Creates an empty view of the plane, 40 pixels to a unit, square, centred on the origin. */
  public CanvasView() {
    getStyleClass().add("canvas-view");
    measure.setFont(Font.font(GridPainter.LABEL_SIZE));
    for (int i = 0; i < CanvasTheme.SERIES; i++) {
      int k = i;
      series.add(
          CSS.createStyleableColorProperty(
              this,
              "series" + (i + 1) + "Color",
              "-canvas-series-" + (i + 1),
              v -> v.series.get(k),
              fx(CanvasTheme.LIGHT.series().get(i))));
    }
    for (Layer layer : Layer.values()) {
      Canvas canvas = new Canvas();
      canvas.setMouseTransparent(true);
      layers.put(layer, canvas);
      getChildren().add(canvas);
    }
    List<StyleableProperty<Color>> colours = new ArrayList<>(List.of(axis, grid, minorGrid, text));
    colours.addAll(series);
    for (StyleableProperty<Color> c : colours) {
      ((ObservableValue<?>) c)
          .addListener(
              (o, was, is) -> {
                theme = null;
                invalidate(Layer.values());
              });
    }
    backgroundProperty().addListener((o, was, is) -> theme = null);
    viewport.addListener(
        (o, was, is) -> {
          Objects.requireNonNull(is, "viewport");
          invalidate(Layer.values());
          updateHover();
        });
    content.addListener(
        (o, was, is) -> {
          Objects.requireNonNull(is, "content");
          invalidate(Layer.OBJECTS);
          updateHover();
        });
    hover.addListener((o, was, is) -> invalidate(Layer.OVERLAY));
    overlay.addListener((o, was, is) -> invalidate(Layer.OVERLAY));
    sceneProperty()
        .addListener(
            (o, was, is) -> {
              if (was != null) {
                was.removePostLayoutPulseListener(pulse);
              }
              if (is != null) {
                is.addPostLayoutPulseListener(pulse);
                invalidate(Layer.values());
              }
            });
    addEventHandler(MouseEvent.MOUSE_PRESSED, this::pressed);
    addEventHandler(MouseEvent.MOUSE_DRAGGED, this::dragged);
    addEventHandler(MouseEvent.MOUSE_RELEASED, e -> panning = false);
    addEventHandler(MouseEvent.MOUSE_MOVED, e -> pointer(e.getX(), e.getY()));
    addEventHandler(MouseEvent.MOUSE_EXITED, e -> pointer(Double.NaN, Double.NaN));
    addEventHandler(
        ScrollEvent.SCROLL,
        e -> {
          if (e.getDeltaY() != 0) {
            setViewport(getViewport().zoom(Math.pow(1.0015, e.getDeltaY()), e.getX(), e.getY()));
            e.consume();
          }
        });
    addEventHandler(
        ZoomEvent.ZOOM,
        e -> {
          setViewport(getViewport().zoom(e.getZoomFactor(), e.getX(), e.getY()));
          e.consume();
        });
  }

  /**
   * The visible window of the plane; the view resizes it to its own size, keeping its centre and
   * scale.
   *
   * @return the property
   */
  public ObjectProperty<Viewport> viewportProperty() {
    return viewport;
  }

  /**
   * The visible window.
   *
   * @return the viewport
   */
  public Viewport getViewport() {
    return viewport.get();
  }

  /**
   * Shows another window of the plane.
   *
   * @param value the viewport, not null
   */
  public void setViewport(Viewport value) {
    viewport.set(value);
  }

  /**
   * What the objects layer draws.
   *
   * @return the property
   */
  public ObjectProperty<PlotContent> contentProperty() {
    return content;
  }

  /**
   * What the objects layer draws.
   *
   * @return the content
   */
  public PlotContent getContent() {
    return content.get();
  }

  /**
   * Draws other content.
   *
   * @param value the content, not null
   */
  public void setContent(PlotContent value) {
    content.set(value);
  }

  /**
   * What the pointer is over: the nearest point within the tolerance, else the nearest polyline, or
   * null.
   *
   * @return the property
   */
  public ReadOnlyObjectProperty<Hit> hoveredHitProperty() {
    return hover.getReadOnlyProperty();
  }

  /**
   * What the pointer is over.
   *
   * @return the hit, or null
   */
  public Hit getHoveredHit() {
    return hover.get();
  }

  /**
   * Extra drawing on the overlay, after the hovered point: selections and drag previews.
   *
   * @return the property
   */
  public ObjectProperty<Consumer<Painter>> overlayProperty() {
    return overlay;
  }

  /**
   * The colours the view draws with, as CSS set them; the background is the view's own.
   *
   * @return the theme
   */
  public CanvasTheme theme() {
    if (theme == null) {
      theme =
          new CanvasTheme(
              background(),
              rgba(axis.getValue()),
              rgba(grid.getValue()),
              rgba(minorGrid.getValue()),
              rgba(text.getValue()),
              series.stream().map(s -> rgba(s.getValue())).toList());
    }
    return theme;
  }

  /**
   * Marks layers to be repainted at the next pulse.
   *
   * @param which the layers
   */
  public void invalidate(Layer... which) {
    Collections.addAll(dirty, which);
    if (getScene() != null) {
      Platform.requestNextPulse();
    }
  }

  /**
   * The CSS properties of the class.
   *
   * @return Region's and the canvas colours
   */
  public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
    return CSS.getCssMetaData();
  }

  @Override
  public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
    return getClassCssMetaData();
  }

  @Override
  public String getUserAgentStylesheet() {
    return Objects.requireNonNull(CanvasView.class.getResource("canvas-view.css")).toExternalForm();
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
    for (Canvas canvas : layers.values()) {
      canvas.setWidth(w);
      canvas.setHeight(h);
    }
    setViewport(getViewport().resize(w, h));
  }

  /** Sets a listener told of every frame; package-private, for the tests and the benchmark. */
  void setFrameListener(Consumer<Frame> listener) {
    frameListener = listener;
  }

  /** The number of frames painted. */
  long frames() {
    return frames;
  }

  private void paintDirty() {
    if (dirty.isEmpty()) {
      return;
    }
    Set<Layer> painting = EnumSet.copyOf(dirty);
    dirty.clear();
    Map<Layer, Long> nanos = new EnumMap<>(Layer.class);
    for (Layer layer : painting) {
      long start = System.nanoTime();
      Canvas canvas = layers.get(layer);
      // A clear of the whole canvas also drops the commands buffered since the last render.
      canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
      FxPainter p = new FxPainter(canvas.getGraphicsContext2D(), getViewport());
      switch (layer) {
        case GRID -> GridPainter.paint(p, theme(), this::labelWidth);
        case OBJECTS -> getContent().paint(p);
        case OVERLAY -> paintOverlay(p);
      }
      nanos.put(layer, System.nanoTime() - start);
    }
    frames++;
    if (frameListener != null) {
      frameListener.accept(new Frame(frames, painting, nanos));
    }
  }

  /**
   * A tick label's width as the screen backend draws it, which the grid's x step leaves room for;
   * an export passes it to draw the grid the view draws. FX thread.
   *
   * @param label the label
   * @return its width in pixels at {@link GridPainter#LABEL_SIZE}
   */
  public double labelWidth(String label) {
    measure.setText(label);
    return measure.getLayoutBounds().getWidth();
  }

  private void paintOverlay(Painter p) {
    Hit h = getHoveredHit();
    // an area's nearest point is the pointer itself, so a hovered area shows no point
    if (h != null && h.kind() != Hit.Kind.AREA) {
      Rgba colour = getContent().colour(h.item()).withOpacity(HOVER_OPACITY);
      p.point(h.x(), h.y(), HOVER_RADIUS, colour);
    }
    Consumer<Painter> extra = overlay.get();
    if (extra != null) {
      extra.accept(p);
    }
  }

  private void pressed(MouseEvent e) {
    if (e.getButton() == MouseButton.PRIMARY) {
      pressX = e.getX();
      pressY = e.getY();
      panning = true;
    }
  }

  private void dragged(MouseEvent e) {
    if (panning) {
      double dx = e.getX() - pressX;
      double dy = e.getY() - pressY;
      pressX = e.getX();
      pressY = e.getY();
      pointerX = e.getX();
      pointerY = e.getY();
      setViewport(getViewport().pan(dx, dy));
    }
  }

  private void pointer(double x, double y) {
    pointerX = x;
    pointerY = y;
    updateHover();
  }

  private void updateHover() {
    Hit h = null;
    if (!Double.isNaN(pointerX)) {
      Viewport v = getViewport();
      h =
          getContent()
              .index()
              .nearest(v.toWorldX(pointerX), v.toWorldY(pointerY), v, HitIndex.DEFAULT_TOLERANCE)
              .orElse(null);
    }
    hover.set(h);
  }

  private static Color fx(Rgba c) {
    return new Color(c.red(), c.green(), c.blue(), c.opacity());
  }

  /** The first fill of the view's background when it is a colour, else the light theme's. */
  private Rgba background() {
    Background b = getBackground();
    if (b != null
        && !b.getFills().isEmpty()
        && b.getFills().getFirst().getFill() instanceof Color c) {
      return rgba(c);
    }
    return CanvasTheme.LIGHT.background();
  }

  private static Rgba rgba(Color c) {
    return new Rgba(c.getRed(), c.getGreen(), c.getBlue(), c.getOpacity());
  }
}
