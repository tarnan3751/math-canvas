package mathcanvas.app.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import mathcanvas.app.document.Command;
import mathcanvas.app.eval.CanvasFrame;
import mathcanvas.app.eval.Coordinates;
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
import mathcanvas.canvas.model.ParametricCurve;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.PolarCurve;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Window;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.Stroke;
import mathcanvas.canvas.tool.AreaTool;
import mathcanvas.canvas.tool.Bounded;
import mathcanvas.canvas.tool.ConstructionTool;
import mathcanvas.canvas.tool.Edit;
import mathcanvas.canvas.tool.FeatureTool;
import mathcanvas.canvas.tool.IntersectionTool;
import mathcanvas.canvas.tool.PathProjection;
import mathcanvas.canvas.tool.PointOnCurveTool;
import mathcanvas.canvas.tool.Pointer;
import mathcanvas.canvas.tool.PolygonTool;
import mathcanvas.canvas.tool.ProjectionTool;
import mathcanvas.canvas.tool.RiemannTool;
import mathcanvas.canvas.tool.Scene;
import mathcanvas.canvas.tool.SecantTool;
import mathcanvas.canvas.tool.TangentTool;
import mathcanvas.canvas.tool.TaylorTool;
import mathcanvas.canvas.tool.Tool;
import mathcanvas.canvas.tool.VectorSumTool;
import mathcanvas.canvas.tool.VectorTool;
import mathcanvas.canvas.view.CanvasView;
import mathcanvas.canvas.view.Layer;
import mathcanvas.canvas.view.PlotContent;

/**
 * The canvas side of the window: a small toolbar wrapping onto as many rows as it needs (the tools,
 * the aspect lock and the coordinate entry) over the algebra panel ({@link AlgebraPanel}) and the
 * coordinate plane ({@link CanvasView}). One object can be selected, in the panel or by a click on
 * the canvas; the canvas shows the selection as a halo on the overlay.
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
 * <p>The analysis tools ({@link Tool}: point on curve, tangent, intersections, roots, extrema, the
 * calculus tools, and the vector, vector sum and projection tools) take the pointer's events
 * themselves, with the document's canvas and the frame's slopes, and paint their preview on the
 * overlay; their edits become commands here, an addition one {@link Command.Sequence} with the last
 * object it adds selected; a search for intersections or features goes to the document, which
 * counts on the worker and adds a member per place ({@link Document#addIntersections}, {@link
 * Document#addFeatures}), its refusal shown beside the tools. Escape abandons a tool's construction
 * in progress, and stops the running jobs when there is none. With the point on curve tool, a
 * selected point on a path shows the coordinate entry, its parameter typed and read on the worker
 * ({@link Document#submitEntry}).
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

  /** The Geometry drop-down's state while one of its tools is chosen. */
  private static final PseudoClass CHOSEN = PseudoClass.getPseudoClass("chosen");

  /** The space between the toolbar's controls, across and down, in pixels. */
  private static final double TOOLBAR_GAP = 4;

  /** How much wider than the object its selection halo is, in pixels. */
  private static final double HALO_WIDTH = 6;

  private static final double HALO_OPACITY = 0.35;

  private final Document document;
  private final CanvasView view = new CanvasView();
  private final ReadOnlyStringWrapper readout = new ReadOnlyStringWrapper(this, "readout");
  private final ObjectProperty<ObjectId> selection = new SimpleObjectProperty<>(this, "selection");
  private final AlgebraPanel algebra;
  private final ToggleButton pointTool = new ToggleButton(Messages.text("canvas.tool.point"));
  private final Map<Toggle, Tool> analysis = new HashMap<>();
  private final TextField coordinate = new TextField();
  private final Label toolMessage = new Label();

  /** The analysis tool chosen, or null with Select or Point. */
  private Tool tool;

  /** Whether the analysis tool took the last press, so it gets the drag and the release. */
  private boolean toolPress;

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
    ToggleButton onCurveTool =
        toolButton(
            "canvas-tool-on-curve",
            Messages.text("canvas.tool.onCurve"),
            Messages.text("canvas.tool.onCurve.tip"),
            new PointOnCurveTool());
    ToggleButton tangentTool =
        toolButton(
            "canvas-tool-tangent",
            Messages.text("canvas.tool.tangent"),
            Messages.text("canvas.tool.tangent.tip"),
            new TangentTool());
    ToggleButton intersectTool =
        toolButton(
            "canvas-tool-intersect",
            Messages.text("canvas.tool.intersect"),
            Messages.text("canvas.tool.intersect.tip"),
            new IntersectionTool());
    ToggleButton rootsTool =
        toolButton(
            "canvas-tool-roots",
            Messages.text("canvas.tool.roots"),
            Messages.text("canvas.tool.roots.tip"),
            FeatureTool.roots());
    ToggleButton extremaTool =
        toolButton(
            "canvas-tool-extrema",
            Messages.text("canvas.tool.extrema"),
            Messages.text("canvas.tool.extrema.tip"),
            FeatureTool.extrema());
    ToggleButton areaTool =
        toolButton(
            "canvas-tool-area",
            Messages.text("canvas.tool.area"),
            Messages.text("canvas.tool.area.tip"),
            new AreaTool());
    ToggleButton riemannTool =
        toolButton(
            "canvas-tool-riemann",
            Messages.text("canvas.tool.riemann"),
            Messages.text("canvas.tool.riemann.tip"),
            new RiemannTool());
    ToggleButton secantTool =
        toolButton(
            "canvas-tool-secant",
            Messages.text("canvas.tool.secant"),
            Messages.text("canvas.tool.secant.tip"),
            new SecantTool());
    ToggleButton taylorTool =
        toolButton(
            "canvas-tool-taylor",
            Messages.text("canvas.tool.taylor"),
            Messages.text("canvas.tool.taylor.tip"),
            new TaylorTool());
    ToggleButton vectorTool =
        toolButton(
            "canvas-tool-vector",
            Messages.text("canvas.tool.vector"),
            Messages.text("canvas.tool.vector.tip"),
            new VectorTool());
    ToggleButton vectorSumTool =
        toolButton(
            "canvas-tool-vector-sum",
            Messages.text("canvas.tool.vectorSum"),
            Messages.text("canvas.tool.vectorSum.tip"),
            new VectorSumTool());
    ToggleButton projectionTool =
        toolButton(
            "canvas-tool-projection",
            Messages.text("canvas.tool.projection"),
            Messages.text("canvas.tool.projection.tip"),
            new ProjectionTool());
    // the construction tools, in a drop-down of their own: the toolbar already wraps
    MenuButton geometry = new MenuButton(Messages.text("canvas.geometry"));
    geometry.setId("canvas-geometry");
    geometry.setTooltip(new Tooltip(Messages.text("canvas.geometry.tip")));
    List<RadioMenuItem> constructions =
        List.of(
            menuTool("segment", ConstructionTool.segment()),
            menuTool("line", ConstructionTool.line()),
            menuTool("ray", ConstructionTool.ray()),
            menuTool("circleCenterPoint", ConstructionTool.circleCenterPoint()),
            menuTool("circleCenterRadius", ConstructionTool.circleCenterRadius()),
            menuTool("circleThreePoints", ConstructionTool.circleThreePoints()),
            menuTool("perpendicular", ConstructionTool.perpendicular()),
            menuTool("parallel", ConstructionTool.parallel()),
            menuTool("perpendicularBisector", ConstructionTool.perpendicularBisector()),
            menuTool("angleBisector", ConstructionTool.angleBisector()),
            menuTool("midpoint", ConstructionTool.midpoint()),
            menuTool("tangents", ConstructionTool.tangents()),
            menuTool("polygon", new PolygonTool()),
            menuTool("regularPolygon", ConstructionTool.regularPolygon()),
            menuTool("angle", ConstructionTool.angle()));
    geometry.getItems().addAll(constructions);
    // the transformations, and the measurements with the locus, in two more
    MenuButton transform = new MenuButton(Messages.text("canvas.transform"));
    transform.setId("canvas-transform");
    transform.setTooltip(new Tooltip(Messages.text("canvas.transform.tip")));
    transform
        .getItems()
        .addAll(
            menuTool("reflect", ConstructionTool.reflect()),
            menuTool("rotate", ConstructionTool.rotate()),
            menuTool("translateByVector", ConstructionTool.translateByVector()),
            menuTool("translateByPoints", ConstructionTool.translateByPoints()),
            menuTool("dilate", ConstructionTool.dilate()));
    MenuButton measure = new MenuButton(Messages.text("canvas.measure"));
    measure.setId("canvas-measure");
    measure.setTooltip(new Tooltip(Messages.text("canvas.measure.tip")));
    measure
        .getItems()
        .addAll(
            menuTool("distance", ConstructionTool.distance()),
            menuTool("measureAngle", ConstructionTool.angle()),
            menuTool("measureArea", ConstructionTool.area()),
            menuTool("slope", ConstructionTool.slope()),
            menuTool("locus", ConstructionTool.locus()));
    List<MenuButton> dropDowns = List.of(geometry, transform, measure);
    ToggleGroup tools = new ToggleGroup();
    tools
        .getToggles()
        .addAll(
            selectTool,
            pointTool,
            onCurveTool,
            tangentTool,
            intersectTool,
            rootsTool,
            extremaTool,
            areaTool,
            riemannTool,
            secantTool,
            taylorTool,
            vectorTool,
            vectorSumTool,
            projectionTool);
    for (MenuButton menu : dropDowns) {
      for (MenuItem item : menu.getItems()) {
        tools.getToggles().add((RadioMenuItem) item);
      }
    }
    tools.selectToggle(selectTool);
    tools
        .selectedToggleProperty()
        .addListener(
            (o, was, now) -> {
              if (now == null) {
                tools.selectToggle(was); // one tool is always chosen
                return;
              }
              if (tool != null) {
                tool.cancel();
              }
              tool = analysis.get(now);
              toolPress = false;
              // a drop-down names the tool chosen from it
              for (MenuButton menu : dropDowns) {
                String key = menu.getId().substring("canvas-".length());
                boolean chosen =
                    now instanceof RadioMenuItem item && menu.getItems().contains(item);
                menu.setText(
                    chosen
                        ? Messages.text(
                            "canvas." + key + ".chosen", ((RadioMenuItem) now).getText())
                        : Messages.text("canvas." + key));
                menu.pseudoClassStateChanged(CHOSEN, chosen);
              }
              tell("");
              showCoordinate();
              view.invalidate(Layer.OVERLAY);
            });
    coordinate.setId("canvas-coordinate");
    coordinate.setPrefColumnCount(8);
    coordinate.setOnAction(e -> submitCoordinate());
    toolMessage.setId("canvas-tool-message");
    tell("");
    toolMessage.getStyleClass().add("algebra-error");
    // a toolbar that wraps: the canvas side is narrow, and a tool in an overflow menu is hidden
    FlowPane toolbar =
        new FlowPane(
            TOOLBAR_GAP,
            TOOLBAR_GAP,
            selectTool,
            pointTool,
            onCurveTool,
            tangentTool,
            intersectTool,
            rootsTool,
            extremaTool,
            areaTool,
            riemannTool,
            secantTool,
            taylorTool,
            vectorTool,
            vectorSumTool,
            projectionTool,
            geometry,
            transform,
            measure,
            aspect,
            coordinate,
            toolMessage);
    toolbar.getStyleClass().addAll("tool-bar", "canvas-toolbar");

    document.addListener(
        change -> {
          if (!change.after().viewport().equals(view.getViewport())) {
            view.setViewport(change.after().viewport());
          }
          if (selection.get() != null
              && change.after().canvas().object(selection.get()).isEmpty()) {
            selection.set(null);
          }
          showCoordinate();
        });
    algebra = new AlgebraPanel(document, selection, view::theme);
    document.canvasFrameProperty().subscribe(this::show);
    view.hoveredHitProperty().subscribe(h -> showReadout());
    view.addEventFilter(MouseEvent.MOUSE_PRESSED, this::pressed);
    view.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::dragged);
    view.addEventFilter(
        MouseEvent.MOUSE_MOVED,
        e -> {
          if (tool != null) {
            tool.move(scene(), pointer(e.getX(), e.getY()));
            if (tool instanceof ConstructionTool c) {
              tell(c.refusal().map(k -> Messages.text("canvas.refusal." + k)).orElse(""));
            }
            view.invalidate(Layer.OVERLAY);
          }
        });
    view.addEventFilter(
        MouseEvent.MOUSE_RELEASED,
        e -> {
          if (toolPress) {
            toolPress = false;
            tool.release(scene(), pointer(e.getX(), e.getY()), this::apply);
            document.endMerge();
            e.consume();
            return;
          }
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
    view.overlayProperty()
        .set(
            p -> {
              paintSelection(p);
              if (tool != null) {
                tool.paint(p, view.theme());
              }
            });
    selection.subscribe(
        id -> {
          view.invalidate(Layer.OVERLAY);
          tell(""); // a refusal is about what it was asked of
          showCoordinate();
        });
    addEventHandler(
        KeyEvent.KEY_PRESSED,
        e -> {
          if (e.getCode() == KeyCode.ESCAPE) {
            if (tool != null && tool.inProgress()) {
              tool.cancel(); // the construction in progress, not the jobs
              toolPress = false;
              showCoordinate();
              view.invalidate(Layer.OVERLAY);
            } else {
              document.stop();
            }
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
    if (tool != null) {
      document.endMerge();
      tell("");
      toolPress = tool.press(scene(), pointer(e.getX(), e.getY()), this::apply);
      pressTaken = toolPress;
      showCoordinate(); // a calculus tool may now wait for a number
      if (toolPress) {
        e.consume();
      }
      view.invalidate(Layer.OVERLAY);
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
    return o instanceof FreePoint || o instanceof PointOnPath p && p.at().isEmpty();
  }

  /** A drag of a point: moves it to the pointer, or along its path to the pointer's projection. */
  private void dragged(MouseEvent e) {
    if (toolPress) {
      e.consume();
      tool.drag(scene(), pointer(e.getX(), e.getY()), this::apply);
      view.invalidate(Layer.OVERLAY);
      return;
    }
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

  /** A construction tool's item in the Geometry drop-down, its id {@code canvas-tool-<name>}. */
  private RadioMenuItem menuTool(String name, Tool t) {
    RadioMenuItem item = new RadioMenuItem(Messages.text("canvas.tool." + name));
    item.setId("canvas-tool-" + name);
    // chosen however it is fired (the menu's own click selects it too), never unchosen by it
    item.setOnAction(e -> item.setSelected(true));
    analysis.put(item, t);
    return item;
  }

  /** A toggle for an analysis tool. */
  private ToggleButton toolButton(String id, String text, String tip, Tool t) {
    ToggleButton b = new ToggleButton(text);
    b.setId(id);
    b.setTooltip(new Tooltip(tip));
    analysis.put(b, t);
    return b;
  }

  /** What the tools see: the document's canvas, the visible window, the frame's slopes. */
  private Scene scene() {
    Viewport v = view.getViewport();
    return new Scene(
        document.state().canvas(),
        new Window(v.xMin(), v.xMax(), v.yMin(), v.yMax()),
        frame::slope);
  }

  /** The pointer at a point of the view, with what is drawn under it. */
  private Pointer pointer(double sx, double sy) {
    Viewport v = view.getViewport();
    return new Pointer(
        v.toWorldX(sx),
        v.toWorldY(sy),
        hitAt(sx, sy).filter(t -> t.id() != null).map(t -> new Pointer.Picked(t.id(), t.hit())));
  }

  /**
   * Makes a tool's edit: an addition as one undo step, each object in the least-used series colour
   * as it is added, the last object it adds selected; a move; a search for intersections or
   * features, through the document.
   */
  private void apply(Edit edit) {
    switch (edit) {
      case Edit.Add a -> {
        List<Command> commands = new ArrayList<>();
        for (int k = 0; k < a.objects().size(); k++) {
          Edit.New n = a.objects().get(k);
          int before = k;
          Function<ObjectId, CanvasObject> make =
              id -> {
                List<ObjectId> ids = new ArrayList<>();
                for (int j = before; j >= 0; j--) {
                  ids.add(new ObjectId(id.value() - j));
                }
                return n.make().apply(ids);
              };
          commands.add(n.named() ? Command.AddObject.named(make) : Command.AddObject.inTurn(make));
        }
        ObjectId first = document.state().canvas().nextObjectId();
        document.execute(new Command.Sequence(commands));
        selection.set(new ObjectId(first.value() + commands.size() - 1));
      }
      case Edit.Move m ->
          document.execute(new Command.MovePoint(m.id(), m.x(), m.y(), m.parameter()));
      case Edit.Intersect i ->
          document.addIntersections(i.first(), i.second(), selection::set, this::tell);
      case Edit.Find f -> document.addFeatures(f.graph(), f.features(), selection::set, this::tell);
    }
  }

  /**
   * Shows the coordinate entry while the point on curve tool is chosen and a point on a path with a
   * parameter is selected, its prompt naming the parameter and its value now; or while a calculus
   * tool waits for a number, its prompt naming it ({@code a =}).
   */
  private void showCoordinate() {
    if (tool instanceof Bounded b) {
      Optional<String> wanted = b.wanted();
      coordinate.setVisible(wanted.isPresent());
      coordinate.setManaged(wanted.isPresent());
      wanted.ifPresent(
          name -> coordinate.setPromptText(Messages.text("canvas.number.prompt", name)));
      return;
    }
    boolean shown =
        tool instanceof PointOnCurveTool
            && selection.get() != null
            && document.state().canvas().object(selection.get()).orElse(null)
                instanceof PointOnPath p
            && p.kind() != ParamKind.NONE;
    coordinate.setVisible(shown);
    coordinate.setManaged(shown);
    if (shown) {
      PointOnPath p = (PointOnPath) document.state().canvas().object(selection.get()).get();
      coordinate.setPromptText(
          Messages.text(
              "canvas.coordinate.prompt",
              parameterName(p),
              Coordinates.text(p.parameter(), document.state().settings())));
    }
  }

  /** The name of a point's parameter on its path: the path's variable, or {@code t}. */
  private String parameterName(PointOnPath p) {
    CanvasObject path = document.state().canvas().object(p.path()).orElse(null);
    if (path instanceof FunctionGraph g) {
      return g.variable().name();
    }
    if (path instanceof ParametricCurve c) {
      return c.parameter().name();
    }
    return path instanceof PolarCurve c ? c.angle().name() : "t";
  }

  /**
   * Submits the coordinate entry: the number a calculus tool waits for, or the selected point's new
   * parameter, read on the worker.
   */
  private void submitCoordinate() {
    String text = coordinate.getText();
    if (tool instanceof Bounded b && b.wanted().isPresent()) {
      if (text.isBlank()) {
        return;
      }
      coordinate.clear();
      tell("");
      document.readNumber(
          text,
          (e, value) -> {
            if (tool == b) {
              b.typed(e, value, this::apply);
              showCoordinate();
              view.invalidate(Layer.OVERLAY);
            }
          },
          message -> {
            coordinate.setText(text);
            tell(message);
          });
      return;
    }
    ObjectId id = selection.get();
    if (text.isBlank() || id == null) {
      return;
    }
    coordinate.clear();
    tell("");
    document.submitEntry(
        Optional.of(id),
        text,
        message -> {
          coordinate.setText(text);
          tell(message);
        });
  }

  /**
   * Shows a tool's message beside the toolbar's tools: why a coordinate or a search was refused;
   * nothing for an empty one.
   */
  private void tell(String message) {
    toolMessage.setText(message);
    toolMessage.setVisible(!message.isEmpty());
    toolMessage.setManaged(!message.isEmpty());
  }

  /**
   * The tool message, for the tests.
   *
   * @return the label
   */
  public Label toolMessage() {
    return toolMessage;
  }

  /**
   * The coordinate entry, for the tests.
   *
   * @return the field
   */
  public TextField coordinateEntry() {
    return coordinate;
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
