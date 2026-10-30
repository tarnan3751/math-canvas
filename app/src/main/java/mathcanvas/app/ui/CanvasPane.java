package mathcanvas.app.ui;

import java.util.Map;
import java.util.Optional;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import mathcanvas.app.document.Command;
import mathcanvas.app.eval.CanvasFrame;
import mathcanvas.app.eval.Document;
import mathcanvas.app.text.Messages;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.hit.Hit;
import mathcanvas.canvas.hit.HitIndex;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParamKind;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.Stroke;
import mathcanvas.canvas.tool.PathProjection;
import mathcanvas.canvas.view.CanvasView;
import mathcanvas.canvas.view.Layer;
import mathcanvas.canvas.view.PlotContent;

/**
 * The canvas side of the window: a small toolbar (the tools and the aspect lock) over the algebra
 * panel ({@link AlgebraPanel}) and the coordinate plane ({@link CanvasView}). One object can be
 * selected, in the panel or by a click on the canvas; the canvas shows the selection as a halo on
 * the overlay.
 *
 * <p>The tools: with either, pressing a free point or a point on a path drags it ({@link
 * Command.MovePoint}, whose moves merge into one undo step while each follows the last within the
 * stack's merge interval), a point on a path along its path, at the parameter of the nearest point
 * of the path's samples ({@link PathProjection}). With the select tool a drag anywhere else pans
 * and a click selects what is under the pointer. With the point tool a click on a curve places a
 * point on it at the parameter there ({@link PointOnPath}), and a click elsewhere a free point
 * ({@link FreePoint}), each named, when the command runs, with the next free letter ({@link
 * Command.AddObject#named}) and dragged on while the button is down: the placement and the drag are
 * two undo steps.
 *
 * <p>The pane follows the document: the view draws the content of the document's canvas frame, the
 * view's viewport (a pan, a zoom, a resize, the aspect lock) goes to the document through {@link
 * Document#setViewport}, which keeps it in the state without an undo step (the session file does
 * not hold it yet), and a viewport the document changes comes back to the view. The colours the
 * view's CSS gives it are sent to the document ({@link Document#setCanvasTheme}) whenever the theme
 * changes, so the content is built in them. Hovering gives the trace readout ({@link Readout}),
 * with the slope on a curve. Escape on the canvas stops the running jobs, as it does in a cell.
 * Touched only on the FX thread.
 */
public final class CanvasPane extends BorderPane {

  /** How much wider than the object its selection halo is, in pixels. */
  private static final double HALO_WIDTH = 6;

  private static final double HALO_OPACITY = 0.35;

  private final Document document;
  private final CanvasView view = new CanvasView();
  private final ReadOnlyStringWrapper readout = new ReadOnlyStringWrapper(this, "readout");
  private final ObjectProperty<ObjectId> selection = new SimpleObjectProperty<>(this, "selection");
  private final AlgebraPanel algebra;
  private final ToggleButton pointTool = new ToggleButton(Messages.text("canvas.tool.point"));
  private CanvasFrame frame = CanvasFrame.EMPTY;
  private CanvasTheme sentTheme;

  /** The point being dragged, or null. */
  private ObjectId dragging;

  /** Whether the last press was taken by a tool, so its click selects nothing. */
  private boolean pressTaken;

  /**
   * A pane following a document.
   *
   * @param document the document
   */
  public CanvasPane(Document document) {
    this.document = document;
    setId("canvas-pane");
    getStyleClass().add("canvas-pane");
    view.setId("canvas");
    view.setFocusTraversable(true);
    view.setViewport(document.state().viewport());

    ToggleButton aspect = new ToggleButton(Messages.text("canvas.aspect"));
    aspect.setId("canvas-aspect");
    aspect.setTooltip(new Tooltip(Messages.text("canvas.aspect.tip")));
    aspect.setSelected(view.getViewport().aspectLocked());
    aspect
        .selectedProperty()
        .addListener((o, was, now) -> view.setViewport(view.getViewport().withAspectLocked(now)));
    view.viewportProperty()
        .addListener(
            (o, was, now) -> {
              aspect.setSelected(now.aspectLocked());
              document.setViewport(now);
              showReadout();
            });
    ToggleButton selectTool = new ToggleButton(Messages.text("canvas.tool.select"));
    selectTool.setId("canvas-tool-select");
    selectTool.setTooltip(new Tooltip(Messages.text("canvas.tool.select.tip")));
    pointTool.setId("canvas-tool-point");
    pointTool.setTooltip(new Tooltip(Messages.text("canvas.tool.point.tip")));
    ToggleGroup tools = new ToggleGroup();
    tools.getToggles().addAll(selectTool, pointTool);
    tools.selectToggle(selectTool);
    tools
        .selectedToggleProperty()
        .addListener(
            (o, was, now) -> {
              if (now == null) {
                tools.selectToggle(was); // one tool is always chosen
              }
            });
    ToolBar toolbar = new ToolBar(selectTool, pointTool, aspect);
    toolbar.getStyleClass().add("canvas-toolbar");

    document.addListener(
        change -> {
          if (!change.after().viewport().equals(view.getViewport())) {
            view.setViewport(change.after().viewport());
          }
          if (selection.get() != null
              && change.after().canvas().object(selection.get()).isEmpty()) {
            selection.set(null);
          }
        });
    algebra = new AlgebraPanel(document, selection, view::theme);
    document.canvasFrameProperty().subscribe(this::show);
    view.hoveredHitProperty().subscribe(h -> showReadout());
    view.addEventFilter(MouseEvent.MOUSE_PRESSED, this::pressed);
    view.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::dragged);
    view.addEventFilter(
        MouseEvent.MOUSE_RELEASED,
        e -> {
          if (dragging != null) {
            dragging = null;
            document.endMerge();
          }
        });
    view.addEventHandler(
        MouseEvent.MOUSE_CLICKED,
        e -> {
          if (e.isStillSincePress() && !pressTaken) {
            selection.set(hitAt(e.getX(), e.getY()).map(Target::id).orElse(null));
          }
        });
    view.overlayProperty().set(this::paintSelection);
    selection.subscribe(id -> view.invalidate(Layer.OVERLAY));
    addEventHandler(
        KeyEvent.KEY_PRESSED,
        e -> {
          if (e.getCode() == KeyCode.ESCAPE) {
            document.stop();
            e.consume();
          }
        });
    sceneProperty()
        .subscribe(
            scene -> {
              if (scene != null) {
                sendTheme();
              } else {
                readout.set(null); // hidden: nothing is hovered
              }
            });

    setTop(toolbar);
    setLeft(algebra);
    setCenter(view);
  }

  /**
   * Shows a frame: its content on the plane. The document's frames come here; the tests show frames
   * of their own.
   *
   * @param frame the frame
   */
  public void show(CanvasFrame frame) {
    this.frame = frame;
    view.setContent(frame.content());
    algebra.show(frame);
    view.invalidate(Layer.OVERLAY);
  }

  /**
   * The frame shown.
   *
   * @return the frame
   */
  public CanvasFrame frame() {
    return frame;
  }

  /**
   * Sends the document the colours the view's CSS now gives it, if they changed: called when the
   * pane enters a scene and whenever the scene's theme changes.
   */
  void sendTheme() {
    view.applyCss();
    CanvasTheme theme = view.theme();
    if (!theme.equals(sentTheme)) {
      sentTheme = theme;
      document.setCanvasTheme(theme);
      algebra.recolour();
    }
  }

  /**
   * What is drawn under the pointer: a hit and its object.
   *
   * @param hit the hit
   * @param id the object
   */
  private record Target(Hit hit, ObjectId id) {}

  /**
   * The object drawn nearest a point of the view, within the hit tolerance, points first; nothing
   * when the item hit is no object's.
   */
  private Optional<Target> hitAt(double sx, double sy) {
    Viewport v = view.getViewport();
    return frame
        .content()
        .index()
        .nearest(v.toWorldX(sx), v.toWorldY(sy), v, HitIndex.DEFAULT_TOLERANCE)
        .flatMap(
            h -> Optional.ofNullable(frame.items().get(h.item())).map(id -> new Target(h, id)));
  }

  /**
   * A press: on a point that can be dragged, starts dragging it; else, with the point tool, places
   * a point. Either takes the press from the view, which would pan.
   */
  private void pressed(MouseEvent e) {
    view.requestFocus();
    pressTaken = false;
    if (e.getButton() != MouseButton.PRIMARY) {
      return;
    }
    Optional<Target> target = hitAt(e.getX(), e.getY());
    Optional<ObjectId> point =
        target
            .filter(t -> t.hit().kind() == Hit.Kind.POINT)
            .map(Target::id)
            .filter(
                id ->
                    document.state().canvas().object(id).filter(CanvasPane::draggable).isPresent());
    if (point.isPresent()) {
      dragging = point.get();
    } else if (pointTool.isSelected()) {
      dragging = place(target, e.getX(), e.getY());
    } else {
      return;
    }
    document.endMerge();
    selection.set(dragging);
    pressTaken = true;
    e.consume();
  }

  private static boolean draggable(CanvasObject o) {
    return o instanceof FreePoint || o instanceof PointOnPath;
  }

  /** A drag of a point: moves it to the pointer, or along its path to the pointer's projection. */
  private void dragged(MouseEvent e) {
    if (dragging == null) {
      return;
    }
    e.consume();
    Viewport v = view.getViewport();
    double x = v.toWorldX(e.getX());
    double y = v.toWorldY(e.getY());
    CanvasObject o = document.state().canvas().object(dragging).orElse(null);
    if (o instanceof FreePoint p) {
      document.execute(new Command.MovePoint(p.id(), x, y));
    } else if (o instanceof PointOnPath p) {
      if (frame.state().geometryOf(p.path()) instanceof Geometry.Curve c) {
        PathProjection.onto(c, x, y)
            .filter(r -> (r.kind() == ParamKind.NONE) == (p.kind() == ParamKind.NONE))
            .ifPresent(r -> document.execute(new Command.MovePoint(p.id(), r.x(), r.y(), r.t())));
      }
    } else {
      dragging = null;
    }
  }

  /**
   * Places a point where the point tool was pressed: on the curve pressed, at the parameter of the
   * nearest point of its samples, or else free at the pointer.
   *
   * @return the new point's id
   */
  private ObjectId place(Optional<Target> target, double sx, double sy) {
    ObjectId id = document.state().canvas().nextObjectId();
    Optional<PathProjection.Projection> on =
        target
            .filter(t -> t.hit().kind() == Hit.Kind.POLYLINE)
            .filter(t -> document.state().canvas().object(t.id()).isPresent())
            .flatMap(
                t ->
                    frame.state().geometryOf(t.id()) instanceof Geometry.Curve c
                        ? PathProjection.onto(c, t.hit().x(), t.hit().y())
                        : Optional.empty());
    if (on.isPresent()) {
      PathProjection.Projection r = on.get();
      ObjectId path = target.get().id();
      document.execute(
          Command.AddObject.named(
              n -> new PointOnPath(n, Style.DEFAULT, path, r.kind(), r.t(), r.x(), r.y())));
    } else {
      Viewport v = view.getViewport();
      double x = v.toWorldX(sx);
      double y = v.toWorldY(sy);
      document.execute(Command.AddObject.named(n -> new FreePoint(n, Style.DEFAULT, x, y)));
    }
    return id;
  }

  /** The selected object's halo: its polylines and points drawn again, wide and faint. */
  private void paintSelection(Painter p) {
    ObjectId id = selection.get();
    if (id == null) {
      return;
    }
    PlotContent content = frame.content();
    for (Map.Entry<Integer, ObjectId> e : frame.items().entrySet()) {
      if (!e.getValue().equals(id)) {
        continue;
      }
      int item = e.getKey();
      Rgba colour = content.colour(item).withOpacity(HALO_OPACITY);
      if (item < content.curves().size()) {
        PlotContent.Curve c = content.curves().get(item);
        p.beginPath();
        p.polyline(c.xs(), c.ys(), c.xs().length);
        p.stroke(Stroke.solid(colour, c.stroke().width() + HALO_WIDTH));
      } else {
        PlotContent.Marker m = content.points().get(item - content.curves().size());
        p.point(m.x(), m.y(), m.radius() + HALO_WIDTH / 2, colour);
      }
    }
  }

  /**
   * The selected object, shared with the algebra panel.
   *
   * @return the property
   */
  public ObjectProperty<ObjectId> selectionProperty() {
    return selection;
  }

  /**
   * The algebra panel, for the tests.
   *
   * @return the panel
   */
  public AlgebraPanel algebraPanel() {
    return algebra;
  }

  private void showReadout() {
    Hit h = view.getHoveredHit();
    readout.set(
        h == null
            ? null
            : Readout.text(h, slope(h), view.getViewport(), document.state().settings().format()));
  }

  /**
   * The slope at a hovered point of a curve: a function graph's derivative from the frame's
   * evaluators, another curve's secant over the segment hovered; NaN on a point.
   */
  private double slope(Hit h) {
    ObjectId id = frame.items().get(h.item());
    if (h.kind() != Hit.Kind.POLYLINE || id == null) {
      return Double.NaN;
    }
    if (frame.state().object(id).orElse(null) instanceof FunctionGraph) {
      return frame.slope(id, h.x());
    }
    PlotContent.Curve c = frame.content().curves().get(h.item());
    int k = h.segment();
    return (c.ys()[k + 1] - c.ys()[k]) / (c.xs()[k + 1] - c.xs()[k]);
  }

  /**
   * The trace readout: the coordinates of the hovered point, or null when nothing is hovered.
   *
   * @return the property
   */
  public ReadOnlyStringProperty readoutProperty() {
    return readout.getReadOnlyProperty();
  }

  /**
   * The coordinate plane, for the tests.
   *
   * @return the view
   */
  public CanvasView view() {
    return view;
  }
}
