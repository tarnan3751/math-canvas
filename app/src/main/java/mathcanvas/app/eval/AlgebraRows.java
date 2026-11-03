package mathcanvas.app.eval;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import mathcanvas.app.text.Messages;
import mathcanvas.canvas.model.Angle;
import mathcanvas.canvas.model.Area;
import mathcanvas.canvas.model.Arrow;
import mathcanvas.canvas.model.BarChart;
import mathcanvas.canvas.model.BoxPlot;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.Circle;
import mathcanvas.canvas.model.Constructions;
import mathcanvas.canvas.model.ContourPlot;
import mathcanvas.canvas.model.CriticalPoint;
import mathcanvas.canvas.model.Distance;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.GradientArrow;
import mathcanvas.canvas.model.Histogram;
import mathcanvas.canvas.model.ImplicitCurve;
import mathcanvas.canvas.model.InequalityRegion;
import mathcanvas.canvas.model.Intersection;
import mathcanvas.canvas.model.Line;
import mathcanvas.canvas.model.Locus;
import mathcanvas.canvas.model.Midpoint;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParametricCurve;
import mathcanvas.canvas.model.ParametricSurface;
import mathcanvas.canvas.model.Parametrisation;
import mathcanvas.canvas.model.Path;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.PolarCurve;
import mathcanvas.canvas.model.Polygon;
import mathcanvas.canvas.model.Projection;
import mathcanvas.canvas.model.Ray;
import mathcanvas.canvas.model.ResidualPlot;
import mathcanvas.canvas.model.RiemannSum;
import mathcanvas.canvas.model.Sample;
import mathcanvas.canvas.model.ScatterPlot;
import mathcanvas.canvas.model.Segment;
import mathcanvas.canvas.model.ShadedArea;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Slope;
import mathcanvas.canvas.model.SlopeField;
import mathcanvas.canvas.model.SolutionCurve;
import mathcanvas.canvas.model.SpaceCurve;
import mathcanvas.canvas.model.SpaceObject;
import mathcanvas.canvas.model.Surface;
import mathcanvas.canvas.model.Text;
import mathcanvas.canvas.model.Transform;
import mathcanvas.canvas.model.VectorField;
import mathcanvas.canvas.model.VectorSum;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.print.Format;
import mathcanvas.engine.print.LayoutPrinter;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.print.layout.Layout;

/**
 * Prints the algebra panel's rows on the worker, each object once per form: an entry is kept per
 * object record, name and drawn position (the most recent {@link #MEMO} of them), so a row that did
 * not change is never printed again. One belongs to each {@link Document}, which hands it to every
 * canvas job, so only that document's worker thread touches it.
 *
 * <p>A row is the object's algebraic form: {@code y = x²} for a function graph, the relation of an
 * implicit curve or a region, {@code r = …} for a polar curve, the vector of a parametric curve,
 * {@code a = 5/2} for a slider, {@code A = ⟨1.5, -2.0⟩} for a point (its position as drawn, written
 * as {@link Coordinates} writes coordinates in the document's number format, {@code undefined} when
 * it has none), the equation of a line ({@code y = 2x + 1}, or {@code x = 3} when it is vertical)
 * and of a circle ({@code (x − 1)² + (y − 2)² = 9}) from their geometry, its numbers written as
 * coordinates are, words for the objects with no expression (a segment and a ray by their ends, a
 * polygon by its vertices with its area and perimeter, an angle by its vertices with its measure in
 * the angle mode, a text), and a vector's components and its length ({@code u = ⟨3, 2⟩, length
 * 3.605551275}), a sum's ({@code u + v = ⟨…⟩}) and a projection's with its scalar component ({@code
 * proj_v u = ⟨…⟩, component …}), written as coordinates are. A vector is named by its variable when
 * it is bound to one, else by its tail's and its head's names ({@code AB}, the origin {@code O}), a
 * sum and a projection by their vectors' names. A vector field is {@code F = ⟨P, Q⟩}, a slope field
 * {@code y' = f}, a solution curve {@code solution through A} by its point, a contour plot {@code
 * contours of f}, and the statistical plots words: {@code histogram of data, bins 2} ({@code
 * histogram of data} by Sturges' rule), {@code bar chart of v}, {@code box plot of data}, {@code
 * scatter of (x, y)}, {@code residuals of fit}.
 */
final class AlgebraRows {

  /** The most entries kept. */
  static final int MEMO = 512;

  private record Key(
      CanvasObject object, String name, Object drawn, Format format, AngleMode mode) {}

  /**
   * Below this fraction of a line's direction its abscissa's change is none: the line is vertical.
   */
  static final double VERTICAL = 1e-12;

  private final Map<Key, AlgebraRow> rows =
      new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, AlgebraRow> eldest) {
          return size() > MEMO;
        }
      };

  /** An empty memo. */
  AlgebraRows() {}

  /**
   * An object's row.
   *
   * @param o the object
   * @param canvas the canvas it is on, for the names and the geometry of a vector's vectors
   * @param names the points' names, for the object's own and its parents'
   * @param geometry the object's geometry, which a point's row shows
   * @param error why it is undefined, when there is something to say
   * @param format the format a point's coordinates are written in ({@link Coordinates#format})
   * @param mode the angle mode an angle's measure is written in
   * @param cancellation the request to stop
   * @return the row
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  AlgebraRow of(
      CanvasObject o,
      CanvasState canvas,
      Map<ObjectId, String> names,
      Geometry geometry,
      Optional<String> error,
      Format format,
      AngleMode mode,
      Cancellation cancellation) {
    String name = names.get(o.id());
    Object drawn =
        switch (o) {
          case FreePoint _, PointOnPath _, Intersection _, CriticalPoint _, Midpoint _ -> geometry;
          case ShadedArea _, RiemannSum _, Line _, Circle _ -> geometry;
          case Segment s -> List.of(label(s.start(), names), label(s.end(), names));
          case Ray r -> List.of(label(r.start(), names), label(r.through(), names));
          case Polygon p -> {
            StringBuilder vertices = new StringBuilder();
            for (ObjectId id : p.parents()) {
              vertices.append(label(id, names));
            }
            yield List.of(geometry, vertices.toString());
          }
          case Angle a ->
              List.of(
                  geometry, label(a.a(), names) + label(a.vertex(), names) + label(a.c(), names));
          case Arrow _, VectorSum _ -> List.of(geometry, vector(o, canvas, names));
          case Transform t ->
              t.shape() == Transform.Shape.POINT
                  ? geometry
                  : List.of(geometry, describe(t.source(), canvas, names));
          case Distance d ->
              List.of(
                  geometry,
                  label(d.a(), names),
                  canvas.object(d.b()).filter(CanvasObject::isPoint).isPresent()
                      ? label(d.b(), names)
                      : describe(d.b(), canvas, names));
          case Area a -> List.of(geometry, describe(a.of(), canvas, names));
          case Slope sl -> List.of(geometry, describe(sl.of(), canvas, names));
          case Locus l -> List.of(label(l.point(), names), label(l.driver(), names));
          case SolutionCurve s -> List.of(label(s.point(), names));
          case Projection p ->
              List.of(
                  geometry,
                  operand(p.u(), canvas, names),
                  operand(p.v(), canvas, names),
                  canvas.geometryOf(p.u()),
                  canvas.geometryOf(p.v()));
          case FunctionGraph _,
              ParametricCurve _,
              PolarCurve _,
              ImplicitCurve _,
              InequalityRegion _,
              Slider _,
              Text _,
              VectorField _,
              SlopeField _,
              ContourPlot _,
              BarChart _,
              BoxPlot _,
              ScatterPlot _,
              ResidualPlot _ ->
              null;
          case Histogram h -> h.binWidth().isEmpty() && h.start().isPresent() ? geometry : null;
          case GradientArrow _ -> geometry;
          case SpaceObject _ -> null;
        };
    AlgebraRow row =
        rows.computeIfAbsent(
            new Key(o, name, drawn, format, mode),
            k -> print(k.object(), name, drawn, format, mode, cancellation));
    return error.isEmpty() ? row : new AlgebraRow(row.text(), row.box(), error);
  }

  private static String label(ObjectId id, Map<ObjectId, String> names) {
    return names.getOrDefault(id, "#" + id.value());
  }

  /** The name of a vector: its variable, its ends' names, or its vectors' names. */
  private static String vector(CanvasObject o, CanvasState canvas, Map<ObjectId, String> names) {
    if (o instanceof VectorSum s) {
      return Messages.text(
          "row.vectorSum.name", operand(s.u(), canvas, names), operand(s.v(), canvas, names));
    }
    if (o instanceof Projection p) {
      return Messages.text(
          "row.projection.name", operand(p.u(), canvas, names), operand(p.v(), canvas, names));
    }
    if (o instanceof Arrow a) {
      return switch (a.head()) {
        case Arrow.Bound b -> b.variable();
        case Arrow.At h -> end(a.tail(), names) + end(h.point(), names);
      };
    }
    return label(o.id(), names);
  }

  /** A vector's name as an operand: in parentheses when it is a sum or a projection. */
  private static String operand(ObjectId id, CanvasState canvas, Map<ObjectId, String> names) {
    CanvasObject o = canvas.object(id).orElse(null);
    if (o == null) {
      return label(id, names);
    }
    String name = vector(o, canvas, names);
    return o instanceof Arrow ? name : "(" + name + ")";
  }

  /** A point's name, or the origin's. */
  private static String end(Optional<ObjectId> point, Map<ObjectId, String> names) {
    return point.map(id -> label(id, names)).orElse(Messages.text("row.origin"));
  }

  private static AlgebraRow print(
      CanvasObject o,
      String name,
      Object drawn,
      Format format,
      AngleMode mode,
      Cancellation cancellation) {
    List<?> labels = drawn instanceof List<?> l ? l : List.of();
    // an expression, or words for an object with none
    Object form =
        switch (o) {
          case FreePoint _, PointOnPath _, Intersection _, CriticalPoint _, Midpoint _ ->
              named(name, point(drawn));
          case FunctionGraph f -> Relation.of(Relation.Op.EQ, ImplicitCurve.Y, f.body());
          case ParametricCurve p -> Vector.of(p.x(), p.y());
          case PolarCurve p -> Relation.of(Relation.Op.EQ, Symbol.of("r"), p.radius());
          case ImplicitCurve c -> c.equation();
          case InequalityRegion r -> r.inequality();
          case Slider s -> named(s.variable(), CanvasBindings.exact(s.value()));
          case Text t -> t.text();
          case ShadedArea _ -> Messages.text("row.area", value(drawn, format));
          case RiemannSum s ->
              Messages.text(
                  "row.riemann",
                  value(drawn, format),
                  Messages.text("riemann." + s.method().name().toLowerCase(Locale.ROOT)),
                  drawn instanceof Geometry.ValuedRegion r
                      ? Integer.toString(r.rings().size())
                      : UnicodePrinter.INSTANCE.print(s.n()));
          case Segment _ -> Messages.text("row.segment", labels.get(0), labels.get(1));
          case Arrow _ -> vectorRow("row.vector", labels, format);
          case VectorSum _ -> vectorRow("row.vector", labels, format);
          case Projection _ -> {
            Geometry u = (Geometry) labels.get(3);
            Geometry v = (Geometry) labels.get(4);
            String proj = Messages.text("row.projection.name", labels.get(1), labels.get(2));
            yield labels.get(0) instanceof Geometry.Arrow a
                    && u instanceof Geometry.Arrow ua
                    && v instanceof Geometry.Arrow va
                ? Messages.text(
                    "row.projection",
                    proj,
                    Coordinates.text(a.dx(), format),
                    Coordinates.text(a.dy(), format),
                    Coordinates.text(ua.componentAlong(va), format))
                : Messages.text("row.vector.undefined", proj);
          }
          case Transform t -> image(t, name, drawn, labels, format, mode);
          case Distance _ -> measure("row.distance", labels, format, labels.get(1), labels.get(2));
          case Area _ -> measure("row.measureArea", labels, format, labels.get(1));
          case Slope _ -> measure("row.slope", labels, format, labels.get(1));
          case Locus _ -> Messages.text("row.locus", labels.get(0), labels.get(1));
          case Line _ -> line(drawn, format);
          case Circle _ -> circle(drawn, format);
          case Ray _ -> Messages.text("row.ray", labels.get(0), labels.get(1));
          case Polygon p -> polygon(p, labels, format);
          case Angle _ -> angle(labels, format, mode);
          case VectorField v ->
              Relation.of(Relation.Op.EQ, Symbol.of("F"), Vector.of(v.p(), v.q()));
          case SlopeField s -> Relation.of(Relation.Op.EQ, Symbol.of("y'"), s.slope());
          case SolutionCurve _ -> Messages.text("row.solution", labels.get(0));
          case ContourPlot c ->
              Messages.text("row.contours", UnicodePrinter.INSTANCE.print(c.function()));
          case Histogram h -> histogram(h, drawn, format);
          case BarChart b -> Messages.text("row.barChart", written(b.values()));
          case BoxPlot b ->
              b.y().equals(BoxPlot.DEFAULT_Y)
                  ? Messages.text("row.boxPlot", written(b.data()))
                  : Messages.text("row.boxPlot.at", written(b.data()), written(b.y()));
          case ScatterPlot p ->
              switch (p.points()) {
                case ScatterPlot.Pairs pairs ->
                    Messages.text("row.scatter", written(pairs.matrix()));
                case ScatterPlot.Columns c ->
                    Messages.text("row.scatter.columns", written(c.xs()), written(c.ys()));
              };
          case ResidualPlot p -> Messages.text("row.residuals", written(p.fit()));
          case SpaceObject s -> space(s, drawn, format);
        };
    if (!(form instanceof Expr e)) {
      return new AlgebraRow((String) form, null, Optional.empty());
    }
    // a point's coordinates in the document's format; every other form exactly as it is stored or
    // built (a line's or a circle's coefficients rounded already)
    boolean point = o.isPoint();
    LayoutPrinter layouts =
        point ? LayoutPrinter.INSTANCE.withFormat(format) : LayoutPrinter.INSTANCE;
    UnicodePrinter text =
        point ? UnicodePrinter.INSTANCE.withFormat(format) : UnicodePrinter.INSTANCE;
    Layout layout = Typeset.capped(layouts.print(e, cancellation));
    return new AlgebraRow(
        text.print(e, cancellation),
        Typeset.box(layout, Typeset.INSPECTOR_SIZE, false, cancellation),
        Optional.empty());
  }

  // Phase 8c: the statistical plots' rows.

  /**
   * A histogram's row: its data and its bins' width, {@code histogram of data, bins 2}, the width
   * as written, and its start when it has one; by Sturges' rule, {@code histogram of data}, so that
   * the row read back keeps the rule. A start with Sturges' width (from a file, not an entry) shows
   * the width as computed ("undefined" while it is).
   */
  private static String histogram(Histogram h, Object drawn, Format format) {
    if (h.binWidth().isEmpty() && h.start().isEmpty()) {
      return Messages.text("row.histogram.sturges", written(h.data()));
    }
    String width =
        h.binWidth().isPresent()
            ? written(h.binWidth().get())
            : drawn instanceof Geometry.Bins b
                ? shortest(b.width(), format)
                : Messages.text("row.undefined");
    return h.start().isPresent()
        ? Messages.text("row.histogram.start", written(h.data()), width, written(h.start().get()))
        : Messages.text("row.histogram", written(h.data()), width);
  }

  /** An expression as a word row writes it. */
  private static String written(Expr e) {
    return UnicodePrinter.INSTANCE.print(e);
  }

  // Phase 7d: the rows of the 3D view's objects.

  /** The height a surface's row equates its body to. */
  private static final Symbol Z = Symbol.of("z");

  /** The name a parametric surface's or a space curve's row gives it. */
  private static final Symbol R = Symbol.of("r");

  /**
   * An object in space's form: a surface's equation {@code z = …}, a parametric surface's {@code
   * r(u, v) = ⟨…⟩}, a space curve's {@code r(t) = ⟨…⟩}, a gradient's words {@code ∇f at (x₀, y₀) =
   * ⟨f_x, f_y⟩}.
   */
  private static Object space(SpaceObject o, Object drawn, Format format) {
    return switch (o) {
      case Surface s -> Relation.of(Relation.Op.EQ, Z, s.body());
      case ParametricSurface s ->
          Relation.of(
              Relation.Op.EQ,
              Apply.of(R, ParametricSurface.U, ParametricSurface.V),
              Vector.of(s.x(), s.y(), s.z()));
      case SpaceCurve c ->
          Relation.of(Relation.Op.EQ, Apply.of(R, SpaceCurve.T), Vector.of(c.x(), c.y(), c.z()));
      case GradientArrow g ->
          drawn instanceof Geometry.Gradient d
              ? Messages.text(
                  "row.gradient",
                  Coordinates.text(d.x(), format),
                  Coordinates.text(d.y(), format),
                  Coordinates.text(d.fx(), format),
                  Coordinates.text(d.fy(), format))
              : Messages.text(
                  "row.gradient.undefined",
                  UnicodePrinter.INSTANCE.print(g.x()),
                  UnicodePrinter.INSTANCE.print(g.y()));
    };
  }

  /** An arrow's or a sum's row: its name, its components and its length, or "undefined". */
  private static String vectorRow(String key, List<?> drawn, Format format) {
    String name = (String) drawn.get(1);
    return drawn.get(0) instanceof Geometry.Arrow a
        ? Messages.text(
            key,
            name,
            Coordinates.text(a.dx(), format),
            Coordinates.text(a.dy(), format),
            Coordinates.text(Math.hypot(a.dx(), a.dy()), format))
        : Messages.text("row.vector.undefined", name);
  }

  /**
   * An image's row: a point's coordinates, a line's or a circle's equation, a segment's or a ray's
   * ends, a polygon's area and perimeter, a vector's components, an angle's measure, as its
   * source's row would be, named as the image of its source.
   */
  private static Object image(
      Transform t, String name, Object drawn, List<?> labels, Format format, AngleMode mode) {
    if (t.shape() == Transform.Shape.POINT) {
      return named(name, point(drawn));
    }
    Object g = labels.get(0);
    String of = Messages.text("row.image", labels.get(1));
    return switch (t.shape()) {
      case POINT -> named(name, point(drawn));
      case LINEAR -> {
        if (g instanceof Geometry.Curve c
            && c.path() instanceof Path.Parametrised p
            && p.parametrisation() instanceof Parametrisation.Linear l
            && Double.isFinite(p.lo())) {
          Sample a = l.at(p.lo());
          Sample b = l.at(Double.isFinite(p.hi()) ? p.hi() : 1);
          yield Messages.text(
              Double.isFinite(p.hi()) ? "row.imageSegment" : "row.imageRay",
              of,
              Coordinates.text(a.x(), format),
              Coordinates.text(a.y(), format),
              Coordinates.text(b.x(), format),
              Coordinates.text(b.y(), format));
        }
        yield line(g, format);
      }
      case CIRCLE -> circle(g, format);
      case POLYGON ->
          g instanceof Geometry.Curve c
                  && c.path() instanceof Path.Parametrised p
                  && p.parametrisation() instanceof Parametrisation.Polygonal poly
              ? Messages.text(
                  "row.imagePolygon",
                  of,
                  shortest(Constructions.area(poly), format),
                  shortest(Constructions.perimeter(poly), format))
              : Messages.text("row.vector.undefined", of);
      case CURVE -> g instanceof Geometry.Curve ? of : Messages.text("row.vector.undefined", of);
      case VECTOR -> vectorRow("row.vector", List.of(g, of), format);
      case ANGLE ->
          g instanceof Geometry.Angle a
              ? Messages.text("row.imageAngle", of, measure(a.sweep(), format, mode))
              : Messages.text("row.vector.undefined", of);
    };
  }

  /** A measurement's row, its value in the format, or "undefined". */
  private static String measure(String key, List<?> labels, Format format, Object... names) {
    Object[] args = new Object[names.length + 1];
    System.arraycopy(names, 0, args, 0, names.length);
    args[names.length] =
        labels.get(0) instanceof Geometry.Measure m
            ? shortest(m.value(), format)
            : Messages.text("row.undefined");
    return Messages.text(key, args);
  }

  /** How a row names an object it is about: a point by its name, a polygon by its vertices. */
  private static String describe(ObjectId id, CanvasState canvas, Map<ObjectId, String> names) {
    CanvasObject o = canvas.object(id).orElse(null);
    if (o == null) {
      return label(id, names);
    }
    if (o.isPoint()) {
      return label(id, names);
    }
    if (o instanceof Polygon p && p.definition() instanceof Polygon.Vertices v) {
      StringBuilder out = new StringBuilder(Messages.text("row.name.polygon"));
      out.append(' ');
      for (ObjectId vertex : v.points()) {
        out.append(label(vertex, names));
      }
      return out.toString();
    }
    if (o instanceof Segment sg) {
      return Messages.text("row.segment", label(sg.start(), names), label(sg.end(), names));
    }
    if (o instanceof Ray r) {
      return Messages.text("row.ray", label(r.start(), names), label(r.through(), names));
    }
    return Messages.text(
        o.isCircle()
            ? "row.name.circle"
            : o.isPolygon()
                ? "row.name.polygon"
                : o.isLinear()
                    ? "row.name.line"
                    : o.isVector() ? "row.name.vector" : "row.name.curve");
  }

  /** A line's equation, {@code y = m·x + b} or {@code x = c}, or undefined. */
  private static Expr line(Object drawn, Format format) {
    if (!(drawn instanceof Geometry.Curve c
        && c.path() instanceof Path.Parametrised p
        && p.parametrisation() instanceof Parametrisation.Linear l)) {
      return Constant.UNDEFINED;
    }
    double scale = Math.max(Math.abs(l.px()), Math.abs(l.py()));
    if (Math.abs(l.dx()) <= VERTICAL * Math.hypot(l.dx(), l.dy())) {
      return Relation.of(Relation.Op.EQ, ImplicitCurve.X, signed(l.px(), scale, format));
    }
    double m = l.dy() / l.dx();
    double b = l.py() - m * l.px();
    // a negative slope as the parser reads −m·x: (−1·|m|)·x, or −1·x for −1
    Expr slope = coefficient(Math.abs(m), Math.abs(m), format);
    Expr term =
        slope.equals(Num.ZERO)
            ? null
            : slope.equals(Num.ONE)
                ? m < 0 ? Product.of(Num.MINUS_ONE, ImplicitCurve.X) : ImplicitCurve.X
                : Product.of(m < 0 ? Product.of(Num.MINUS_ONE, slope) : slope, ImplicitCurve.X);
    Expr intercept = signed(b, scale, format);
    Expr right =
        term == null ? intercept : intercept.equals(Num.ZERO) ? term : Sum.of(term, intercept);
    return Relation.of(Relation.Op.EQ, ImplicitCurve.Y, right);
  }

  /** A circle's equation, {@code (x − h)² + (y − k)² = r²}, or undefined. */
  private static Expr circle(Object drawn, Format format) {
    if (!(drawn instanceof Geometry.Curve c
        && c.path() instanceof Path.Parametrised p
        && p.parametrisation() instanceof Parametrisation.Circular k)) {
      return Constant.UNDEFINED;
    }
    double scale = Math.max(Math.max(Math.abs(k.cx()), Math.abs(k.cy())), k.r());
    return Relation.of(
        Relation.Op.EQ,
        Sum.of(
            square(ImplicitCurve.X, k.cx(), scale, format),
            square(ImplicitCurve.Y, k.cy(), scale, format)),
        coefficient(k.r() * k.r(), k.r() * k.r(), format));
  }

  /** {@code (v − c)²}, or {@code v²} when {@code c} is 0 at the scale. */
  private static Expr square(Symbol v, double c, double scale, Format format) {
    Expr shift = signed(-c, scale, format);
    return Power.of(shift.equals(Num.ZERO) ? v : Sum.of(v, shift), Num.of(2));
  }

  /**
   * A number as a term of a sum, written as the parser reads {@code − n}: a negative one is {@code
   * −1·|n|}, so the printers write a subtraction.
   */
  private static Expr signed(double value, double scale, Format format) {
    Expr magnitude = coefficient(Math.abs(value), scale, format);
    return value < 0 && !magnitude.equals(Num.ZERO) ? Product.of(Num.of(-1), magnitude) : magnitude;
  }

  /**
   * A measured number in a word row (an area, a perimeter, an angle's measure), written as an
   * equation's coefficients are: rounded to the format's digits, the shortest decimal that shows
   * it, a whole number as an integer ({@code area 6}, {@code 90°}).
   *
   * @param value the number, finite
   * @param format the format of coordinates
   * @return the text
   */
  static String shortest(double value, Format format) {
    return UnicodePrinter.INSTANCE.print(signed(value, Math.abs(value), format));
  }

  /**
   * A coefficient of an equation, rounded to the format's digits and written as the shortest
   * decimal that shows it: a whole number as an integer, so a line through lattice points reads
   * {@code y = 2x + 1}, not {@code y = 2.0x + 1.0} or {@code y = 1.9999999999995x}; 0 below the
   * coordinates' noise. The equation is then printed exactly as built.
   */
  private static Expr coefficient(double value, double scale, Format format) {
    Expr e = Coordinates.number(value, scale);
    if (e.equals(Num.ZERO) || !(Math.abs(value) < 0x1p53)) {
      return e;
    }
    BigDecimal shown =
        new BigDecimal(value)
            .round(new MathContext(Math.max(1, format.digits())))
            .stripTrailingZeros();
    return shown.scale() <= 0 ? Num.of(shown.longValueExact()) : new Num(Real.of(shown));
  }

  /** A polygon's row: its vertices' names, its area and its perimeter, or "undefined". */
  private static String polygon(Polygon p, List<?> drawn, Format format) {
    String vertices = (String) drawn.get(1);
    boolean regular = p.definition() instanceof Polygon.Regular;
    String n =
        p.definition() instanceof Polygon.Regular r ? UnicodePrinter.INSTANCE.print(r.n()) : "";
    if (!(drawn.get(0) instanceof Geometry.Curve c
        && c.path() instanceof Path.Parametrised q
        && q.parametrisation() instanceof Parametrisation.Polygonal g)) {
      return regular
          ? Messages.text("row.regularPolygon.undefined", vertices, n)
          : Messages.text("row.polygon.undefined", vertices);
    }
    String a = shortest(Constructions.area(g), format);
    String l = shortest(Constructions.perimeter(g), format);
    return regular
        ? Messages.text("row.regularPolygon", vertices, n, a, l)
        : Messages.text("row.polygon", vertices, a, l);
  }

  /** An angle's row: its points' names and its measure in the angle mode, or "undefined". */
  private static String angle(List<?> drawn, Format format, AngleMode mode) {
    String name = (String) drawn.get(1);
    return drawn.get(0) instanceof Geometry.Angle a
        ? Messages.text("row.angle", name, measure(a.sweep(), format, mode))
        : Messages.text("row.angle.undefined", name);
  }

  /**
   * An angle's measure in the angle mode: degrees with {@code °}, gradians with {@code gon},
   * radians plain.
   *
   * @param radians the measure in radians
   * @param format the format of coordinates
   * @param mode the angle mode
   * @return the text
   */
  static String measure(double radians, Format format, AngleMode mode) {
    return switch (mode) {
      case RADIANS -> shortest(radians, format);
      case DEGREES -> Messages.text("angle.degrees", shortest(radians * 180 / Math.PI, format));
      case GRADIANS -> Messages.text("angle.gradians", shortest(radians * 200 / Math.PI, format));
    };
  }

  /** A shaded area's or a Riemann sum's value in the format, or "undefined". */
  private static String value(Object drawn, Format format) {
    return drawn instanceof Geometry.ValuedRegion r
        ? Coordinates.text(r.value(), format)
        : Messages.text("row.undefined");
  }

  /**
   * The text a shaded area or a Riemann sum is written with on the canvas, {@code A = …} or {@code
   * S = …} in the format, an angle's measure in the angle mode, and a measurement's value ({@code d
   * = …}, {@code area = …}, {@code m = …}); empty for any other object, and for one with no value.
   *
   * @param o the object
   * @param geometry its geometry
   * @param format the format of coordinates
   * @param mode the angle mode
   * @return the text
   */
  static Optional<String> canvasLabel(
      CanvasObject o, Geometry geometry, Format format, AngleMode mode) {
    if (geometry instanceof Geometry.Angle a) {
      return Optional.of(measure(a.sweep(), format, mode));
    }
    if (geometry instanceof Geometry.Measure m) {
      String key =
          o instanceof Distance
              ? "label.distance"
              : o instanceof Area ? "label.area" : "label.slope";
      return Optional.of(Messages.text(key, shortest(m.value(), format)));
    }
    if (!(geometry instanceof Geometry.ValuedRegion r)) {
      return Optional.empty();
    }
    String value = Coordinates.text(r.value(), format);
    return o instanceof ShadedArea
        ? Optional.of(Messages.text("row.area", value))
        : o instanceof RiemannSum
            ? Optional.of(Messages.text("label.riemann", value))
            : Optional.empty();
  }

  private static Expr named(String name, Expr value) {
    return name == null ? value : Relation.of(Relation.Op.EQ, Symbol.of(name), value);
  }

  private static Expr point(Object drawn) {
    return drawn instanceof Geometry.Point p ? Coordinates.point(p.x(), p.y()) : Constant.UNDEFINED;
  }
}
