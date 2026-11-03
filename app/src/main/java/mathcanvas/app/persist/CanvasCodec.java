package mathcanvas.app.persist;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import mathcanvas.app.json.Json;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.model.Anchor;
import mathcanvas.canvas.model.Angle;
import mathcanvas.canvas.model.Approach;
import mathcanvas.canvas.model.Area;
import mathcanvas.canvas.model.Arrow;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.Circle;
import mathcanvas.canvas.model.ContourPlot;
import mathcanvas.canvas.model.CriticalPoint;
import mathcanvas.canvas.model.Dilation;
import mathcanvas.canvas.model.Distance;
import mathcanvas.canvas.model.Feature;
import mathcanvas.canvas.model.FieldGrid;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.GradientArrow;
import mathcanvas.canvas.model.ImplicitCurve;
import mathcanvas.canvas.model.InequalityRegion;
import mathcanvas.canvas.model.Intersection;
import mathcanvas.canvas.model.Line;
import mathcanvas.canvas.model.Locus;
import mathcanvas.canvas.model.Midpoint;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParamKind;
import mathcanvas.canvas.model.ParametricCurve;
import mathcanvas.canvas.model.ParametricSurface;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.PolarCurve;
import mathcanvas.canvas.model.Polygon;
import mathcanvas.canvas.model.Projection;
import mathcanvas.canvas.model.Ray;
import mathcanvas.canvas.model.Reflection;
import mathcanvas.canvas.model.RiemannSum;
import mathcanvas.canvas.model.Rotation;
import mathcanvas.canvas.model.Segment;
import mathcanvas.canvas.model.ShadedArea;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Slope;
import mathcanvas.canvas.model.SlopeField;
import mathcanvas.canvas.model.SolutionCurve;
import mathcanvas.canvas.model.SpaceCurve;
import mathcanvas.canvas.model.SpaceObject;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Surface;
import mathcanvas.canvas.model.Text;
import mathcanvas.canvas.model.Transform;
import mathcanvas.canvas.model.Translation;
import mathcanvas.canvas.model.VectorField;
import mathcanvas.canvas.model.VectorSum;
import mathcanvas.canvas.space.Camera;
import mathcanvas.engine.compile.Quadrature;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Lexer;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.ParseException;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.parse.Token;
import mathcanvas.engine.parse.TokenKind;
import mathcanvas.engine.print.AsciiPrinter;

/**
 * The session file's {@code canvas} member: the viewport (its world window, its pixel size,
 * restored exactly so the view's resize keeps its centre and scale, and its aspect lock), {@code
 * nextId} and the objects in drawing order, each {@code {id, kind, name, style, …}} with the fields
 * of its record, {@code name} only on a point the document names (a slider's name is its variable).
 * Geometry is never written.
 *
 * <pre>{@code
 * "canvas": {
 *   "viewport": { "xMin": -8.0, "xMax": 8.0, "yMin": -6.0, "yMax": 6.0,
 *                 "width": 640.0, "height": 480.0, "aspectLocked": true },
 *   "nextId": 4,
 *   "objects": [
 *     { "id": 1, "kind": "freePoint", "name": "A", "style": { … }, "x": 1.5, "y": -2.0 },
 *     { "id": 2, "kind": "slider",
 *       "style": { "colour": 0, "width": 2.0, "dashed": false, "visible": true, "label": false },
 *       "variable": "a", "min": 0, "max": 5, "step": 0.1, "value": 1.5 },
 *     { "id": 3, "kind": "functionGraph", "style": { … },
 *       "body": "a*x^2", "variable": "x", "domain": [null, null] }
 *   ]
 * }
 * }</pre>
 *
 * <p>Expressions are written by the ASCII printer, which writes {@code *} for every product, so a
 * name before {@code (} in the text is always a call; they are read with the names written so taken
 * as functions, which keeps a call of a function defined in a cell a call without knowing the
 * cells, and brought to canonical form at the default precision. A domain is {@code [lower,
 * upper]}, {@code null} for an infinite end; whether an end is closed is not written: an end
 * written as text reads closed, an infinite one open. An intersection's {@code anchor} is {@code
 * null} or an object whose {@code approach} is {@code null} or {@code {t, x, y}}; a critical
 * point's is written the same way, beside its {@code path}, its {@code feature} ({@code root},
 * {@code min} or {@code max}) and its {@code index}. An arrow's {@code tail} is a point's id or
 * {@code null} for the origin, and its head either {@code head}, likewise, or {@code variable}, the
 * name it is bound to; a vector sum and a projection name their vectors {@code u} and {@code v}. A
 * line has one of {@code through} ({@code [a, b]}), {@code point} with {@code slope} and {@code
 * at}, {@code perpendicular} or {@code parallel} ({@code {point, to}}), {@code bisector} ({@code
 * [a, b]}), {@code angleBisector} ({@code [a, vertex, c]}) or {@code tangent} ({@code {circle,
 * point, index}}); a ray has {@code start} and {@code through}; a circle one of {@code center} with
 * {@code through} (an id) or {@code radius} (an expression), or {@code points} ({@code [a, b, c]});
 * a midpoint {@code a} and {@code b}; a polygon one of {@code vertices} (three or more ids) or
 * {@code regular} ({@code {a, b, n}}, {@code n} an expression); an angle {@code points} ({@code [a,
 * vertex, c]}). A vector field has {@code p}, {@code q} and its {@code density}, a slope field its
 * {@code slope} and {@code density}, a solution curve its {@code field} and {@code point}, and a
 * contour plot its {@code function} with one of {@code count} (an integer) or {@code levels} (an
 * array of expressions). A name is one identifier that can be a variable ({@link
 * Slider#isVariableName}), distinct from every other name and every slider's variable. A point's
 * coordinates and every other double are written as {@link Double#toString} writes them, NaN as
 * {@code null}; a slider's numbers are exact decimals, written without trailing zeros as {@link
 * BigDecimal#toString} writes them (an exponent where the plain form would carry zeros: {@code
 * 1E+2}, {@code 1E-7}). Ids are integers. The viewport is read through its constructor, which
 * squares a locked one: one zoomed in so far that its spans are rounded in the last places of its
 * coordinates (below about a ten-thousandth of them) may come back squared again, wider by a few
 * units in the last place.
 *
 * <p>Reading is strict: a missing or ill-typed member, a kind this release does not know (each
 * kind's case is added here with the kind), an unknown parameter kind, text that does not parse to
 * what the member needs, a number that is not finite where one must be, a name that is not a
 * variable name, is taken twice or is on an object that is not a point, a viewport whose bounds are
 * not finite and increasing or whose size is not finite and positive, and anything the records or
 * {@link CanvasState} refuse (a parent that does not exist or is of the wrong kind, a cycle, an id
 * at or above {@code nextId}, two sliders of one variable) is a {@link FileFormatException}: no
 * object is ever dropped. Members it does not know are ignored.
 */
final class CanvasCodec {

  private static final Expr NEGATIVE_INFINITY = FunctionGraph.ALL.lower();
  private static final Expr INFINITY = FunctionGraph.ALL.upper();

  private CanvasCodec() {}

  /**
   * The {@code canvas} member for a canvas.
   *
   * @param canvas the canvas
   * @return the tree
   */
  static Json.Obj encode(StoredCanvas canvas) {
    Viewport w = canvas.viewport();
    LinkedHashMap<String, Json> viewport = new LinkedHashMap<>();
    viewport.put("xMin", number(w.xMin()));
    viewport.put("xMax", number(w.xMax()));
    viewport.put("yMin", number(w.yMin()));
    viewport.put("yMax", number(w.yMax()));
    viewport.put("width", number(w.width()));
    viewport.put("height", number(w.height()));
    viewport.put("aspectLocked", new Json.Bool(w.aspectLocked()));
    List<Json> objects = new ArrayList<>();
    for (CanvasObject o : canvas.state().objects()) {
      objects.add(object(o, canvas.names().get(o.id())));
    }
    LinkedHashMap<String, Json> root = new LinkedHashMap<>();
    root.put("viewport", new Json.Obj(viewport));
    root.put("nextId", Json.Num.of(canvas.state().nextId()));
    root.put("objects", new Json.Arr(objects));
    // Phase 7d: the 3D view's camera, written only when it is not the default one
    if (!canvas.camera().equals(Camera.DEFAULT)) {
      root.put("space", space(canvas.camera()));
    }
    return new Json.Obj(root);
  }

  /**
   * The canvas a {@code canvas} member holds, with no geometry.
   *
   * @param value the member's value
   * @return the canvas
   * @throws FileFormatException if it is not a canvas this release reads
   */
  static StoredCanvas decode(Json value) throws FileFormatException {
    if (!(value instanceof Json.Obj canvas)) {
      throw new FileFormatException("\"canvas\" is not an object");
    }
    Json.Obj v = Fields.object(canvas, "viewport");
    Viewport viewport;
    try {
      viewport =
          new Viewport(
              Fields.real(v, "xMin"),
              Fields.real(v, "xMax"),
              Fields.real(v, "yMin"),
              Fields.real(v, "yMax"),
              Fields.real(v, "width"),
              Fields.real(v, "height"),
              Fields.bool(v, "aspectLocked"));
    } catch (IllegalArgumentException e) {
      throw new FileFormatException("the viewport: " + e.getMessage());
    }
    long nextId = Fields.longInteger(canvas, "nextId", 1);
    List<Json> items = Fields.array(canvas, "objects", -1);
    List<CanvasObject> objects = new ArrayList<>();
    Map<ObjectId, String> names = new HashMap<>();
    Set<String> taken = new HashSet<>();
    for (int k = 0; k < items.size(); k++) {
      try {
        CanvasObject o = object(items.get(k));
        objects.add(o);
        if (o instanceof Slider s && !taken.add(s.variable())) {
          throw new FileFormatException("the name \"" + s.variable() + "\" is taken twice");
        }
        Optional<String> name = name((Json.Obj) items.get(k), o);
        if (name.isPresent()) {
          names.put(o.id(), name.get());
          if (!taken.add(name.get())) {
            throw new FileFormatException("the name \"" + name.get() + "\" is taken twice");
          }
        }
      } catch (FileFormatException e) {
        throw new FileFormatException("canvas object " + (k + 1) + ": " + e.getMessage());
      }
    }
    // Phase 7d: absent in a file with the default camera and in every older one
    Camera camera =
        canvas.member("space").isPresent()
            ? camera(Fields.object(canvas, "space"))
            : Camera.DEFAULT;
    try {
      return new StoredCanvas(new CanvasState(objects, Map.of(), nextId), names, viewport, camera);
    } catch (IllegalArgumentException e) {
      throw new FileFormatException("the canvas objects: " + e.getMessage());
    }
  }

  /**
   * The session file's migration from version 2 to 3: every point on a path gains {@code "at":
   * null}, a parameter of its own, as version 2 points all had. A canvas that is not of the shape
   * version 2 writes is left for decoding to refuse.
   *
   * @param root the version 2 file
   * @return the file with the member added
   */
  static Json.Obj pointsAtNothing(Json.Obj root) {
    if (!(root.member("canvas").orElse(null) instanceof Json.Obj canvas)
        || !(canvas.member("objects").orElse(null) instanceof Json.Arr objects)) {
      return root;
    }
    List<Json> out = new ArrayList<>(objects.items().size());
    for (Json item : objects.items()) {
      out.add(
          item instanceof Json.Obj o
                  && o.member("kind").orElse(null) instanceof Json.Str k
                  && k.value().equals("pointOnPath")
                  && o.member("at").isEmpty()
              ? o.with("at", Json.Null.INSTANCE)
              : item);
    }
    return root.with("canvas", canvas.with("objects", new Json.Arr(out)));
  }

  // Writing.

  private static Json.Obj object(CanvasObject o, String name) {
    LinkedHashMap<String, Json> m = new LinkedHashMap<>();
    m.put("id", id(o.id()));
    m.put("kind", new Json.Str(kind(o)));
    if (name != null) {
      m.put("name", new Json.Str(name));
    }
    m.put("style", style(o.style()));
    switch (o) {
      case FreePoint p -> {
        m.put("x", number(p.x()));
        m.put("y", number(p.y()));
      }
      case PointOnPath p -> {
        m.put("path", id(p.path()));
        m.put("param", Fields.text(p.kind()));
        m.put("parameter", number(p.parameter()));
        m.put("last", new Json.Arr(List.of(number(p.lastX()), number(p.lastY()))));
        m.put("at", p.at().map(CanvasCodec::expression).orElse(Json.Null.INSTANCE));
      }
      case Intersection i -> {
        m.put("first", id(i.first()));
        m.put("second", id(i.second()));
        m.put("index", Json.Num.of(i.index()));
        m.put("anchor", i.anchor().map(CanvasCodec::anchor).orElse(Json.Null.INSTANCE));
      }
      case CriticalPoint c -> {
        m.put("path", id(c.path()));
        m.put("feature", Fields.text(c.feature()));
        m.put("index", Json.Num.of(c.index()));
        m.put("anchor", c.anchor().map(CanvasCodec::anchor).orElse(Json.Null.INSTANCE));
      }
      case FunctionGraph f -> {
        m.put("body", expression(f.body()));
        m.put("variable", new Json.Str(f.variable().name()));
        m.put("domain", domain(f.domain()));
      }
      case ParametricCurve c -> {
        m.put("x", expression(c.x()));
        m.put("y", expression(c.y()));
        m.put("parameter", new Json.Str(c.parameter().name()));
        m.put("domain", domain(c.domain()));
      }
      case PolarCurve c -> {
        m.put("radius", expression(c.radius()));
        m.put("angle", new Json.Str(c.angle().name()));
        m.put("domain", domain(c.domain()));
      }
      case ImplicitCurve c -> m.put("equation", expression(c.equation()));
      case InequalityRegion r -> m.put("inequality", expression(r.inequality()));
      case ShadedArea a -> {
        m.put("upper", id(a.upper()));
        m.put("lower", a.lower().map(CanvasCodec::id).orElse(Json.Null.INSTANCE));
        m.put("from", expression(a.from()));
        m.put("to", expression(a.to()));
      }
      case RiemannSum s -> {
        m.put("graph", id(s.graph()));
        m.put("from", expression(s.from()));
        m.put("to", expression(s.to()));
        m.put("n", expression(s.n()));
        m.put("method", Fields.text(s.method()));
      }
      case Slider s -> {
        m.put("variable", new Json.Str(s.variable()));
        m.put("min", decimal(s.min()));
        m.put("max", decimal(s.max()));
        m.put("step", decimal(s.step()));
        m.put("value", decimal(s.value()));
      }
      case Text t -> {
        m.put("x", number(t.x()));
        m.put("y", number(t.y()));
        m.put("text", new Json.Str(t.text()));
      }
      case Segment s -> {
        m.put("start", id(s.start()));
        m.put("end", id(s.end()));
      }
      case Line l -> {
        switch (l.definition()) {
          case Line.Through t -> m.put("through", new Json.Arr(List.of(id(t.a()), id(t.b()))));
          case Line.PointSlope p -> {
            m.put("point", id(p.point()));
            m.put("slope", expression(p.slope()));
            m.put("at", new Json.Str(p.at().name()));
          }
          case Line.Perpendicular p -> m.put("perpendicular", pointTo(p.point(), p.to()));
          case Line.Parallel p -> m.put("parallel", pointTo(p.point(), p.to()));
          case Line.PerpendicularBisector b -> m.put("bisector", ids(b.a(), b.b()));
          case Line.AngleBisector b -> m.put("angleBisector", ids(b.a(), b.vertex(), b.c()));
          case Line.CircleTangent t -> {
            LinkedHashMap<String, Json> tangent = new LinkedHashMap<>();
            tangent.put("circle", id(t.circle()));
            tangent.put("point", id(t.point()));
            tangent.put("index", Json.Num.of(t.index()));
            m.put("tangent", new Json.Obj(tangent));
          }
        }
      }
      case Ray r -> {
        m.put("start", id(r.start()));
        m.put("through", id(r.through()));
      }
      case Circle c -> {
        switch (c.definition()) {
          case Circle.CenterPoint d -> {
            m.put("center", id(d.center()));
            m.put("through", id(d.through()));
          }
          case Circle.CenterRadius d -> {
            m.put("center", id(d.center()));
            m.put("radius", expression(d.radius()));
          }
          case Circle.ThreePoints d -> m.put("points", ids(d.a(), d.b(), d.c()));
        }
      }
      case Midpoint p -> {
        m.put("a", id(p.a()));
        m.put("b", id(p.b()));
      }
      case Polygon p -> {
        switch (p.definition()) {
          case Polygon.Vertices v -> m.put("vertices", ids(v.points().toArray(ObjectId[]::new)));
          case Polygon.Regular r -> {
            LinkedHashMap<String, Json> regular = new LinkedHashMap<>();
            regular.put("a", id(r.a()));
            regular.put("b", id(r.b()));
            regular.put("n", expression(r.n()));
            m.put("regular", new Json.Obj(regular));
          }
        }
      }
      case Angle a -> m.put("points", ids(a.a(), a.vertex(), a.c()));
      case Transform t -> {
        m.put("source", id(t.source()));
        m.put("shape", Fields.text(t.shape()));
        switch (t) {
          case Reflection r -> m.put("in", id(r.in()));
          case Rotation r -> {
            m.put("center", id(r.center()));
            m.put("angle", expression(r.angle()));
          }
          case Dilation r -> {
            m.put("center", id(r.center()));
            m.put("factor", expression(r.factor()));
          }
          case Translation r -> {
            switch (r.by()) {
              case Translation.Vector v -> m.put("vector", id(v.vector()));
              case Translation.Points p -> {
                m.put("from", id(p.from()));
                m.put("to", id(p.to()));
              }
            }
          }
        }
      }
      case Distance d -> {
        m.put("a", id(d.a()));
        m.put("b", id(d.b()));
      }
      case Area a -> m.put("of", id(a.of()));
      case Slope sl -> m.put("of", id(sl.of()));
      case Locus l -> {
        m.put("point", id(l.point()));
        m.put("driver", id(l.driver()));
      }
      case Arrow a -> {
        m.put("tail", optionalId(a.tail()));
        switch (a.head()) {
          case Arrow.At h -> m.put("head", optionalId(h.point()));
          case Arrow.Bound b -> m.put("variable", new Json.Str(b.variable()));
        }
      }
      case VectorSum v -> {
        m.put("u", id(v.u()));
        m.put("v", id(v.v()));
      }
      case Projection p -> {
        m.put("u", id(p.u()));
        m.put("v", id(p.v()));
      }
      // phase 7c: fields and contours
      case VectorField v -> {
        m.put("p", expression(v.p()));
        m.put("q", expression(v.q()));
        m.put("density", Json.Num.of(v.density()));
      }
      case SlopeField s -> {
        m.put("slope", expression(s.slope()));
        m.put("density", Json.Num.of(s.density()));
      }
      case SolutionCurve s -> {
        m.put("field", id(s.field()));
        m.put("point", id(s.point()));
      }
      case ContourPlot c -> {
        m.put("function", expression(c.function()));
        switch (c.levels()) {
          case ContourPlot.Count n -> m.put("count", Json.Num.of(n.n()));
          case ContourPlot.Values v -> {
            List<Json> values = new ArrayList<>();
            for (Expr e : v.values()) {
              values.add(expression(e));
            }
            m.put("levels", new Json.Arr(values));
          }
        }
      }
      case SpaceObject s -> space(m, s);
    }
    return new Json.Obj(m);
  }

  // Phase 7d: the 3D view's kinds.

  private static void space(LinkedHashMap<String, Json> m, SpaceObject o) {
    switch (o) {
      case Surface s -> {
        m.put("body", expression(s.body()));
        m.put("xDomain", domain(s.xDomain()));
        m.put("yDomain", domain(s.yDomain()));
        m.put("n", Json.Num.of(s.n()));
      }
      case ParametricSurface s -> {
        m.put("x", expression(s.x()));
        m.put("y", expression(s.y()));
        m.put("z", expression(s.z()));
        m.put("uDomain", domain(s.uDomain()));
        m.put("vDomain", domain(s.vDomain()));
        m.put("n", Json.Num.of(s.n()));
      }
      case SpaceCurve c -> {
        m.put("x", expression(c.x()));
        m.put("y", expression(c.y()));
        m.put("z", expression(c.z()));
        m.put("domain", domain(c.domain()));
      }
      case GradientArrow g -> {
        m.put("surface", id(g.surface()));
        m.put("x", expression(g.x()));
        m.put("y", expression(g.y()));
      }
    }
  }

  /**
   * The 3D view's camera: {@code {yaw, pitch, distance, target: [x, y, z], bounds}}, {@code bounds}
   * the axes box, {@code null} for the box fitting the objects (the only one this release has).
   */
  private static Json space(Camera c) {
    LinkedHashMap<String, Json> m = new LinkedHashMap<>();
    m.put("yaw", number(c.yaw()));
    m.put("pitch", number(c.pitch()));
    m.put("distance", number(c.distance()));
    m.put(
        "target",
        new Json.Arr(List.of(number(c.targetX()), number(c.targetY()), number(c.targetZ()))));
    m.put("bounds", Json.Null.INSTANCE);
    return new Json.Obj(m);
  }

  private static Camera camera(Json.Obj o) throws FileFormatException {
    List<Json> target = Fields.array(o, "target", 3);
    if (!(Fields.member(o, "bounds") instanceof Json.Null)) {
      throw new FileFormatException("\"bounds\" is not null, the box fitting the objects");
    }
    try {
      return new Camera(
          Fields.real(o, "yaw"),
          Fields.real(o, "pitch"),
          Fields.real(o, "distance"),
          Fields.realOrNaN(target.get(0), "target"),
          Fields.realOrNaN(target.get(1), "target"),
          Fields.realOrNaN(target.get(2), "target"));
    } catch (IllegalArgumentException e) {
      throw new FileFormatException("the camera: " + e.getMessage());
    }
  }

  private static String spaceKind(SpaceObject o) {
    return switch (o) {
      case Surface _ -> "surface";
      case ParametricSurface _ -> "parametricSurface";
      case SpaceCurve _ -> "spaceCurve";
      case GradientArrow _ -> "gradient";
    };
  }

  private static Json.Arr ids(ObjectId... ids) {
    List<Json> out = new ArrayList<>();
    for (ObjectId id : ids) {
      out.add(id(id));
    }
    return new Json.Arr(out);
  }

  private static Json.Obj pointTo(ObjectId point, ObjectId to) {
    LinkedHashMap<String, Json> m = new LinkedHashMap<>();
    m.put("point", id(point));
    m.put("to", id(to));
    return new Json.Obj(m);
  }

  private static String kind(CanvasObject o) {
    return switch (o) {
      case Ray _ -> "ray";
      case Circle _ -> "circle";
      case Midpoint _ -> "midpoint";
      case Polygon _ -> "polygon";
      case Angle _ -> "angle";
      case Reflection _ -> "reflection";
      case Rotation _ -> "rotation";
      case Translation _ -> "translation";
      case Dilation _ -> "dilation";
      case Distance _ -> "distance";
      case Area _ -> "area";
      case Slope _ -> "slope";
      case Locus _ -> "locus";
      case FreePoint _ -> "freePoint";
      case PointOnPath _ -> "pointOnPath";
      case ShadedArea _ -> "shadedArea";
      case RiemannSum _ -> "riemannSum";
      case Intersection _ -> "intersection";
      case CriticalPoint _ -> "criticalPoint";
      case FunctionGraph _ -> "functionGraph";
      case ParametricCurve _ -> "parametricCurve";
      case PolarCurve _ -> "polarCurve";
      case ImplicitCurve _ -> "implicitCurve";
      case InequalityRegion _ -> "inequalityRegion";
      case Slider _ -> "slider";
      case Text _ -> "text";
      case Segment _ -> "segment";
      case Line _ -> "line";
      case Arrow _ -> "arrow";
      case VectorSum _ -> "vectorSum";
      case Projection _ -> "projection";
      // phase 7c: fields and contours
      case VectorField _ -> "vectorField";
      case SlopeField _ -> "slopeField";
      case SolutionCurve _ -> "solutionCurve";
      case ContourPlot _ -> "contourPlot";
      case SpaceObject s -> spaceKind(s);
    };
  }

  private static Json style(Style s) {
    LinkedHashMap<String, Json> m = new LinkedHashMap<>();
    m.put("colour", Json.Num.of(s.colour()));
    m.put("width", number(s.width()));
    m.put("dashed", new Json.Bool(s.dashed()));
    m.put("visible", new Json.Bool(s.visible()));
    m.put("label", new Json.Bool(s.labelVisible()));
    return new Json.Obj(m);
  }

  private static Json anchor(Anchor a) {
    LinkedHashMap<String, Json> m = new LinkedHashMap<>();
    m.put("x", number(a.x()));
    m.put("y", number(a.y()));
    m.put("t1", number(a.t1()));
    m.put("t2", number(a.t2()));
    m.put("tangent", new Json.Bool(a.tangent()));
    m.put("approach", a.approach().map(CanvasCodec::approach).orElse(Json.Null.INSTANCE));
    return new Json.Obj(m);
  }

  private static Json approach(Approach a) {
    LinkedHashMap<String, Json> m = new LinkedHashMap<>();
    m.put("t", number(a.t()));
    m.put("x", number(a.x()));
    m.put("y", number(a.y()));
    return new Json.Obj(m);
  }

  private static Json domain(Interval d) {
    return new Json.Arr(
        List.of(
            d.lower().equals(NEGATIVE_INFINITY) ? Json.Null.INSTANCE : expression(d.lower()),
            d.upper().equals(INFINITY) ? Json.Null.INSTANCE : expression(d.upper())));
  }

  private static Json id(ObjectId id) {
    return Json.Num.of(id.value());
  }

  private static Json optionalId(Optional<ObjectId> id) {
    return id.map(CanvasCodec::id).orElse(Json.Null.INSTANCE);
  }

  private static Json expression(Expr e) {
    return new Json.Str(AsciiPrinter.INSTANCE.print(e));
  }

  /** A double as {@link Double#toString} writes it, NaN as {@code null}. */
  private static Json number(double v) {
    return Double.isNaN(v) ? Json.Null.INSTANCE : new Json.Num(Double.toString(v));
  }

  /**
   * An exact decimal without trailing zeros, as {@link BigDecimal#toString} writes it: {@code
   * 0.05}, {@code 1E+2}, so a slider at {@code 1e1001} is a few characters, not a thousand digits.
   */
  private static Json decimal(BigDecimal v) {
    return new Json.Num(v.stripTrailingZeros().toString());
  }

  // Reading.

  private static CanvasObject object(Json value) throws FileFormatException {
    if (!(value instanceof Json.Obj o)) {
      throw new FileFormatException("it is not an object");
    }
    ObjectId id = new ObjectId(Fields.longInteger(o, "id", 1));
    String kind = Fields.string(o, "kind");
    Style style = style(Fields.object(o, "style"));
    try {
      return switch (kind) {
        case "freePoint" -> new FreePoint(id, style, Fields.real(o, "x"), Fields.real(o, "y"));
        case "pointOnPath" -> {
          List<Json> last = Fields.array(o, "last", 2);
          yield new PointOnPath(
              id,
              style,
              id(o, "path"),
              Fields.constant(o, "param", ParamKind.class),
              Fields.realOrNaN(o, "parameter"),
              Fields.realOrNaN(last.get(0), "last"),
              Fields.realOrNaN(last.get(1), "last"),
              optionalExpression(o, "at"));
        }
        case "intersection" ->
            new Intersection(
                id,
                style,
                id(o, "first"),
                id(o, "second"),
                Fields.integer(o, "index", 0, Integer.MAX_VALUE),
                anchor(Fields.member(o, "anchor")));
        case "criticalPoint" ->
            new CriticalPoint(
                id,
                style,
                id(o, "path"),
                Fields.constant(o, "feature", Feature.class),
                Fields.integer(o, "index", 0, Integer.MAX_VALUE),
                anchor(Fields.member(o, "anchor")));
        case "functionGraph" ->
            new FunctionGraph(
                id, style, expression(o, "body"), symbol(o, "variable"), domain(o, "domain"));
        case "parametricCurve" ->
            new ParametricCurve(
                id,
                style,
                expression(o, "x"),
                expression(o, "y"),
                symbol(o, "parameter"),
                domain(o, "domain"));
        case "polarCurve" ->
            new PolarCurve(
                id, style, expression(o, "radius"), symbol(o, "angle"), domain(o, "domain"));
        case "implicitCurve" -> new ImplicitCurve(id, style, relation(o, "equation"));
        case "inequalityRegion" -> new InequalityRegion(id, style, relation(o, "inequality"));
        case "shadedArea" ->
            new ShadedArea(
                id,
                style,
                id(o, "upper"),
                Fields.member(o, "lower") instanceof Json.Null
                    ? Optional.empty()
                    : Optional.of(id(o, "lower")),
                expression(o, "from"),
                expression(o, "to"));
        case "riemannSum" ->
            new RiemannSum(
                id,
                style,
                id(o, "graph"),
                expression(o, "from"),
                expression(o, "to"),
                expression(o, "n"),
                Fields.constant(o, "method", Quadrature.Method.class));
        case "slider" ->
            new Slider(
                id,
                style,
                Fields.string(o, "variable"),
                Fields.decimal(o, "min"),
                Fields.decimal(o, "max"),
                Fields.decimal(o, "step"),
                Fields.decimal(o, "value"));
        case "text" ->
            new Text(id, style, Fields.real(o, "x"), Fields.real(o, "y"), Fields.string(o, "text"));
        case "segment" -> new Segment(id, style, id(o, "start"), id(o, "end"));
        case "line" -> new Line(id, style, line(o));
        case "arrow" -> new Arrow(id, style, optionalId(o, "tail"), head(o));
        case "vectorSum" -> new VectorSum(id, style, id(o, "u"), id(o, "v"));
        case "projection" -> new Projection(id, style, id(o, "u"), id(o, "v"));
        case "ray" -> new Ray(id, style, id(o, "start"), id(o, "through"));
        case "circle" -> new Circle(id, style, circle(o));
        case "midpoint" -> new Midpoint(id, style, id(o, "a"), id(o, "b"));
        case "polygon" -> new Polygon(id, style, polygon(o));
        case "angle" -> {
          List<ObjectId> p = ids(o, "points", 3);
          yield new Angle(id, style, p.get(0), p.get(1), p.get(2));
        }
        case "reflection" -> new Reflection(id, style, id(o, "source"), shape(o), id(o, "in"));
        case "rotation" ->
            new Rotation(
                id, style, id(o, "source"), shape(o), id(o, "center"), expression(o, "angle"));
        case "dilation" ->
            new Dilation(
                id, style, id(o, "source"), shape(o), id(o, "center"), expression(o, "factor"));
        case "translation" ->
            new Translation(
                id,
                style,
                id(o, "source"),
                shape(o),
                oneOf(o, "a translation", List.of("vector", "from")).equals("vector")
                    ? new Translation.Vector(id(o, "vector"))
                    : new Translation.Points(id(o, "from"), id(o, "to")));
        case "distance" -> new Distance(id, style, id(o, "a"), id(o, "b"));
        case "area" -> new Area(id, style, id(o, "of"));
        case "slope" -> new Slope(id, style, id(o, "of"));
        case "locus" -> new Locus(id, style, id(o, "point"), id(o, "driver"));
        // phase 7c: fields and contours
        case "vectorField" ->
            new VectorField(
                id,
                style,
                expression(o, "p"),
                expression(o, "q"),
                Fields.integer(o, "density", FieldGrid.MIN_DENSITY, FieldGrid.MAX_DENSITY));
        case "slopeField" ->
            new SlopeField(
                id,
                style,
                expression(o, "slope"),
                Fields.integer(o, "density", FieldGrid.MIN_DENSITY, FieldGrid.MAX_DENSITY));
        case "solutionCurve" -> new SolutionCurve(id, style, id(o, "field"), id(o, "point"));
        case "contourPlot" -> new ContourPlot(id, style, expression(o, "function"), levels(o));
        // Phase 7d: the 3D view's kinds.
        case "surface" ->
            new Surface(
                id,
                style,
                expression(o, "body"),
                domain(o, "xDomain"),
                domain(o, "yDomain"),
                Fields.integer(o, "n", 1, Integer.MAX_VALUE));
        case "parametricSurface" ->
            new ParametricSurface(
                id,
                style,
                expression(o, "x"),
                expression(o, "y"),
                expression(o, "z"),
                domain(o, "uDomain"),
                domain(o, "vDomain"),
                Fields.integer(o, "n", 1, Integer.MAX_VALUE));
        case "spaceCurve" ->
            new SpaceCurve(
                id,
                style,
                expression(o, "x"),
                expression(o, "y"),
                expression(o, "z"),
                domain(o, "domain"));
        case "gradient" ->
            new GradientArrow(id, style, id(o, "surface"), expression(o, "x"), expression(o, "y"));
        default ->
            throw new FileFormatException(
                "the kind \"" + kind + "\" is not one this release reads");
      };
    } catch (IllegalArgumentException e) {
      throw new FileFormatException(e.getMessage());
    }
  }

  private static Optional<String> name(Json.Obj o, CanvasObject object) throws FileFormatException {
    if (o.member("name").isEmpty()) {
      return Optional.empty();
    }
    String name = Fields.string(o, "name");
    if (object instanceof Slider) {
      throw new FileFormatException("a slider is named by its variable, not \"name\"");
    }
    if (!object.isPoint()) {
      throw new FileFormatException("only a point has a \"name\"");
    }
    if (!Slider.isVariableName(name)) {
      throw new FileFormatException("\"" + name + "\" is not a variable name");
    }
    return Optional.of(name);
  }

  /** The forms of a line, each named by the member that gives it. */
  private static final List<String> LINE_FORMS =
      List.of(
          "through", "point", "perpendicular", "parallel", "bisector", "angleBisector", "tangent");

  private static Line.Definition line(Json.Obj o) throws FileFormatException {
    String form = oneOf(o, "a line", LINE_FORMS);
    return switch (form) {
      case "through" -> {
        List<ObjectId> ab = ids(o, "through", 2);
        yield new Line.Through(ab.get(0), ab.get(1));
      }
      case "point" -> new Line.PointSlope(id(o, "point"), expression(o, "slope"), symbol(o, "at"));
      case "perpendicular" -> {
        Json.Obj p = Fields.object(o, "perpendicular");
        yield new Line.Perpendicular(id(p, "point"), id(p, "to"));
      }
      case "parallel" -> {
        Json.Obj p = Fields.object(o, "parallel");
        yield new Line.Parallel(id(p, "point"), id(p, "to"));
      }
      case "bisector" -> {
        List<ObjectId> ab = ids(o, "bisector", 2);
        yield new Line.PerpendicularBisector(ab.get(0), ab.get(1));
      }
      case "angleBisector" -> {
        List<ObjectId> p = ids(o, "angleBisector", 3);
        yield new Line.AngleBisector(p.get(0), p.get(1), p.get(2));
      }
      default -> {
        Json.Obj t = Fields.object(o, "tangent");
        yield new Line.CircleTangent(
            id(t, "circle"), id(t, "point"), Fields.integer(t, "index", 0, 1));
      }
    };
  }

  private static Circle.Definition circle(Json.Obj o) throws FileFormatException {
    if (oneOf(o, "a circle", List.of("center", "points")).equals("points")) {
      List<ObjectId> p = ids(o, "points", 3);
      return new Circle.ThreePoints(p.get(0), p.get(1), p.get(2));
    }
    return oneOf(o, "a circle's centre", List.of("through", "radius")).equals("through")
        ? new Circle.CenterPoint(id(o, "center"), id(o, "through"))
        : new Circle.CenterRadius(id(o, "center"), expression(o, "radius"));
  }

  private static Polygon.Definition polygon(Json.Obj o) throws FileFormatException {
    if (oneOf(o, "a polygon", List.of("vertices", "regular")).equals("vertices")) {
      return new Polygon.Vertices(ids(o, "vertices", -1));
    }
    Json.Obj r = Fields.object(o, "regular");
    return new Polygon.Regular(id(r, "a"), id(r, "b"), expression(r, "n"));
  }

  /** A contour plot's levels: a count, or a list of values. */
  private static ContourPlot.Levels levels(Json.Obj o) throws FileFormatException {
    if (oneOf(o, "a contour plot", List.of("count", "levels")).equals("count")) {
      return new ContourPlot.Count(Fields.integer(o, "count", 1, ContourPlot.MAX_LEVELS));
    }
    if (!(Fields.member(o, "levels") instanceof Json.Arr a)) {
      throw new FileFormatException("\"levels\" is not an array");
    }
    List<Expr> values = new ArrayList<>();
    for (Json j : a.items()) {
      if (!(j instanceof Json.Str(String text))) {
        throw new FileFormatException("a level of \"levels\" is not a string");
      }
      values.add(parse(text, "levels"));
    }
    return new ContourPlot.Values(values);
  }

  private static Transform.Shape shape(Json.Obj o) throws FileFormatException {
    return Fields.constant(o, "shape", Transform.Shape.class);
  }

  /** The one member of some that an object has. */
  private static String oneOf(Json.Obj o, String what, List<String> forms)
      throws FileFormatException {
    String found = null;
    for (String f : forms) {
      if (o.member(f).isPresent()) {
        if (found != null) {
          throw new FileFormatException(what + " has both \"" + found + "\" and \"" + f + "\"");
        }
        found = f;
      }
    }
    if (found == null) {
      throw new FileFormatException(what + " has none of " + forms);
    }
    return found;
  }

  /** An array of ids: of a length, or of any length for −1. */
  private static List<ObjectId> ids(Json.Obj o, String name, int length)
      throws FileFormatException {
    List<Json> items;
    if (length < 0) {
      if (!(Fields.member(o, name) instanceof Json.Arr a)) {
        throw new FileFormatException("\"" + name + "\" is not an array");
      }
      items = a.items();
    } else {
      items = Fields.array(o, name, length);
    }
    List<ObjectId> out = new ArrayList<>();
    for (Json j : items) {
      out.add(id(j, name));
    }
    return out;
  }

  private static Arrow.Head head(Json.Obj o) throws FileFormatException {
    boolean bound = o.member("variable").isPresent();
    if (bound == o.member("head").isPresent()) {
      throw new FileFormatException("an arrow has either \"head\" or \"variable\"");
    }
    if (!bound) {
      return new Arrow.At(optionalId(o, "head"));
    }
    String variable = Fields.string(o, "variable");
    if (!Slider.isVariableName(variable)) {
      throw new FileFormatException("\"variable\" is not a variable name");
    }
    return new Arrow.Bound(variable);
  }

  /** An id or {@code null}, which must be present. */
  private static Optional<ObjectId> optionalId(Json.Obj o, String name) throws FileFormatException {
    return Fields.member(o, name) instanceof Json.Null
        ? Optional.empty()
        : Optional.of(id(o, name));
  }

  private static Style style(Json.Obj s) throws FileFormatException {
    try {
      return new Style(
          Fields.integer(s, "colour", 0, Style.COLOURS - 1),
          Fields.real(s, "width"),
          Fields.bool(s, "dashed"),
          Fields.bool(s, "visible"),
          Fields.bool(s, "label"));
    } catch (IllegalArgumentException e) {
      throw new FileFormatException("the style: " + e.getMessage());
    }
  }

  private static Optional<Anchor> anchor(Json value) throws FileFormatException {
    if (value instanceof Json.Null) {
      return Optional.empty();
    }
    if (!(value instanceof Json.Obj a)) {
      throw new FileFormatException("\"anchor\" is not an object or null");
    }
    return Optional.of(
        new Anchor(
            Fields.real(a, "x"),
            Fields.real(a, "y"),
            Fields.realOrNaN(a, "t1"),
            Fields.realOrNaN(a, "t2"),
            Fields.bool(a, "tangent"),
            approach(Fields.member(a, "approach"))));
  }

  private static Optional<Approach> approach(Json value) throws FileFormatException {
    if (value instanceof Json.Null) {
      return Optional.empty();
    }
    if (!(value instanceof Json.Obj a)) {
      throw new FileFormatException("\"approach\" is not an object or null");
    }
    return Optional.of(
        new Approach(Fields.realOrNaN(a, "t"), Fields.real(a, "x"), Fields.real(a, "y")));
  }

  private static Interval domain(Json.Obj o, String name) throws FileFormatException {
    List<Json> ends = Fields.array(o, name, 2);
    Expr lower = end(ends.get(0), name, NEGATIVE_INFINITY);
    Expr upper = end(ends.get(1), name, INFINITY);
    return Interval.of(lower, upper, lower != NEGATIVE_INFINITY, upper != INFINITY);
  }

  private static Expr end(Json value, String name, Expr infinite) throws FileFormatException {
    if (value instanceof Json.Null) {
      return infinite;
    }
    if (value instanceof Json.Str(String text)) {
      return parse(text, name);
    }
    throw new FileFormatException("an end of \"" + name + "\" is not a string or null");
  }

  private static ObjectId id(Json.Obj o, String name) throws FileFormatException {
    return new ObjectId(Fields.longInteger(o, name, 1));
  }

  private static ObjectId id(Json value, String name) throws FileFormatException {
    if (value instanceof Json.Num(String text) && text.matches("[1-9][0-9]{0,18}")) {
      try {
        return new ObjectId(Long.parseLong(text));
      } catch (NumberFormatException e) {
        // beyond a long: reported below
      }
    }
    throw new FileFormatException("\"" + name + "\" holds an id that is not a positive integer");
  }

  private static Expr expression(Json.Obj o, String name) throws FileFormatException {
    return parse(Fields.string(o, name), name);
  }

  /** An expression or {@code null}, which must be present. */
  private static Optional<Expr> optionalExpression(Json.Obj o, String name)
      throws FileFormatException {
    return Fields.member(o, name) instanceof Json.Null
        ? Optional.empty()
        : Optional.of(expression(o, name));
  }

  private static Relation relation(Json.Obj o, String name) throws FileFormatException {
    if (expression(o, name) instanceof Relation r) {
      return r;
    }
    throw new FileFormatException("\"" + name + "\" is not a relation");
  }

  private static Expr parse(String text, String name) throws FileFormatException {
    try {
      return Canonical.of(Parser.parseExpression(text, calls(text)));
    } catch (ParseException e) {
      throw new FileFormatException("\"" + name + "\" does not parse: " + e.getMessage());
    }
  }

  /**
   * The names written before {@code (}: calls, since the ASCII printer writes {@code *} for every
   * product. Only these are functions, so a name the text uses as a variable stays one.
   */
  private static Names calls(String text) {
    Set<String> out = new HashSet<>();
    Lexer lexer = new Lexer(text);
    Token before = null;
    for (Token t = lexer.next(); !t.is(TokenKind.EOF); t = lexer.next()) {
      if (t.is(TokenKind.LPAREN) && before != null && before.is(TokenKind.IDENT)) {
        out.add(before.text());
      }
      before = t;
    }
    return out::contains;
  }

  /** A variable: one identifier as the lexer reads it. */
  private static Symbol symbol(Json.Obj o, String name) throws FileFormatException {
    String text = Fields.string(o, name);
    try {
      Lexer lexer = new Lexer(text);
      Token t = lexer.next();
      if (t.is(TokenKind.IDENT) && t.text().equals(text) && lexer.next().is(TokenKind.EOF)) {
        return Symbol.of(text);
      }
    } catch (ParseException e) {
      // not one identifier: reported below
    }
    throw new FileFormatException("\"" + name + "\" is not a variable name");
  }
}
