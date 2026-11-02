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
import mathcanvas.canvas.model.Approach;
import mathcanvas.canvas.model.Arrow;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.CriticalPoint;
import mathcanvas.canvas.model.Feature;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.ImplicitCurve;
import mathcanvas.canvas.model.InequalityRegion;
import mathcanvas.canvas.model.Intersection;
import mathcanvas.canvas.model.Line;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParamKind;
import mathcanvas.canvas.model.ParametricCurve;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.PolarCurve;
import mathcanvas.canvas.model.Projection;
import mathcanvas.canvas.model.RiemannSum;
import mathcanvas.canvas.model.Segment;
import mathcanvas.canvas.model.ShadedArea;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Text;
import mathcanvas.canvas.model.VectorSum;
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
 * name is one identifier that can be a variable ({@link Slider#isVariableName}), distinct from
 * every other name and every slider's variable. A point's coordinates and every other double are
 * written as {@link Double#toString} writes them, NaN as {@code null}; a slider's numbers are exact
 * decimals, written without trailing zeros as {@link BigDecimal#toString} writes them (an exponent
 * where the plain form would carry zeros: {@code 1E+2}, {@code 1E-7}). Ids are integers. The
 * viewport is read through its constructor, which squares a locked one: one zoomed in so far that
 * its spans are rounded in the last places of its coordinates (below about a ten-thousandth of
 * them) may come back squared again, wider by a few units in the last place.
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
    try {
      return new StoredCanvas(new CanvasState(objects, Map.of(), nextId), names, viewport);
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
        }
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
    }
    return new Json.Obj(m);
  }

  private static String kind(CanvasObject o) {
    return switch (o) {
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

  private static Line.Definition line(Json.Obj o) throws FileFormatException {
    boolean through = o.member("through").isPresent();
    if (through == o.member("point").isPresent()) {
      throw new FileFormatException("a line has either \"through\" or \"point\"");
    }
    if (through) {
      List<Json> ab = Fields.array(o, "through", 2);
      return new Line.Through(id(ab.get(0), "through"), id(ab.get(1), "through"));
    }
    return new Line.PointSlope(id(o, "point"), expression(o, "slope"), symbol(o, "at"));
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
