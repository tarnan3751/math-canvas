package mathcanvas.app.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import mathcanvas.app.text.Messages;
import mathcanvas.canvas.model.Arrow;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.CriticalPoint;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ImplicitCurve;
import mathcanvas.canvas.model.InequalityRegion;
import mathcanvas.canvas.model.Intersection;
import mathcanvas.canvas.model.Line;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParametricCurve;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.PolarCurve;
import mathcanvas.canvas.model.Projection;
import mathcanvas.canvas.model.RiemannSum;
import mathcanvas.canvas.model.Segment;
import mathcanvas.canvas.model.ShadedArea;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Text;
import mathcanvas.canvas.model.VectorSum;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
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
 * it has none), words for the objects with no expression (a segment, a line, a text), and a
 * vector's components and its length ({@code u = ⟨3, 2⟩, length 3.605551275}), a sum's ({@code u +
 * v = ⟨…⟩}) and a projection's with its scalar component ({@code proj_v u = ⟨…⟩, component …}),
 * written as coordinates are. A vector is named by its variable when it is bound to one, else by
 * its tail's and its head's names ({@code AB}, the origin {@code O}), a sum and a projection by
 * their vectors' names.
 */
final class AlgebraRows {

  /** The most entries kept. */
  static final int MEMO = 512;

  private record Key(CanvasObject object, String name, Object drawn, Format format) {}

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
      Cancellation cancellation) {
    String name = names.get(o.id());
    Object drawn =
        switch (o) {
          case FreePoint _, PointOnPath _, Intersection _, CriticalPoint _ -> geometry;
          case ShadedArea _, RiemannSum _ -> geometry;
          case Segment s -> List.of(label(s.start(), names), label(s.end(), names));
          case Line l ->
              switch (l.definition()) {
                case Line.Through t -> List.of(label(t.a(), names), label(t.b(), names));
                case Line.PointSlope p -> List.of(label(p.point(), names));
              };
          case Arrow _, VectorSum _ -> List.of(geometry, vector(o, canvas, names));
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
              Text _ ->
              null;
        };
    AlgebraRow row =
        rows.computeIfAbsent(
            new Key(o, name, drawn, format),
            k -> print(k.object(), name, drawn, format, cancellation));
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
      CanvasObject o, String name, Object drawn, Format format, Cancellation cancellation) {
    List<?> labels = drawn instanceof List<?> l ? l : List.of();
    // an expression, or words for an object with none
    Object form =
        switch (o) {
          case FreePoint _, PointOnPath _, Intersection _, CriticalPoint _ ->
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
          case Line l ->
              switch (l.definition()) {
                case Line.Through _ -> Messages.text("row.line", labels.get(0), labels.get(1));
                case Line.PointSlope _ -> Messages.text("row.tangent", labels.get(0));
              };
        };
    if (!(form instanceof Expr e)) {
      return new AlgebraRow((String) form, null, Optional.empty());
    }
    // a point's coordinates in the document's format; every other form exactly as it is stored
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

  /** A shaded area's or a Riemann sum's value in the format, or "undefined". */
  private static String value(Object drawn, Format format) {
    return drawn instanceof Geometry.ValuedRegion r
        ? Coordinates.text(r.value(), format)
        : Messages.text("row.undefined");
  }

  /**
   * The text a shaded area or a Riemann sum is written with on the canvas, {@code A = …} or {@code
   * S = …} in the format; empty for any other object, and for one with no value.
   *
   * @param o the object
   * @param geometry its geometry
   * @param format the format of coordinates
   * @return the text
   */
  static Optional<String> canvasLabel(CanvasObject o, Geometry geometry, Format format) {
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
