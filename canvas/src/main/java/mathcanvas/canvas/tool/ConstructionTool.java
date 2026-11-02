package mathcanvas.canvas.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import mathcanvas.canvas.hit.Hit;
import mathcanvas.canvas.model.Angle;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.Circle;
import mathcanvas.canvas.model.Constructions;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.Line;
import mathcanvas.canvas.model.Midpoint;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Parametrisation;
import mathcanvas.canvas.model.Polygon;
import mathcanvas.canvas.model.Ray;
import mathcanvas.canvas.model.Recomputes;
import mathcanvas.canvas.model.Segment;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Expr;

/**
 * A construction tool: a few picks, each a point (what a click would snap to: an existing point,
 * else a new named point on the curve under the pointer, else a new named free point) or an
 * existing line, segment, ray or circle, then, for some, a number typed in the entry or given by a
 * click; the last of them adds the construction and the new points as one step. A pick of a point
 * already picked is ignored, a click on nothing a line or circle pick wants passes to the view, and
 * Escape abandons the construction. Once every pick but the last is made, the preview draws the
 * object the pointer would complete, dashed, computed by the model's own recompute, so the preview
 * is the object a click adds; a tangent from inside its circle says so with no line at all.
 *
 * <p>The tools are made by the factories: segment, line, ray, the three circles, perpendicular,
 * parallel, perpendicular bisector, angle bisector, midpoint, tangents, regular polygon and angle.
 * The midpoint and the perpendicular bisector also take a click on a segment as its two ends.
 */
public final class ConstructionTool implements Bounded {

  /** What a pick is. */
  public enum Pick {
    /** A point, existing or new. */
    POINT,
    /** An existing line, segment or ray. */
    LINEAR,
    /** An existing circle. */
    CIRCLE
  }

  /**
   * One object a construction adds.
   *
   * @param make builds it from the picks' ids and its own id
   * @param named whether it is a point that gets the next free name
   */
  record Made(BiFunction<List<ObjectId>, ObjectId, CanvasObject> make, boolean named) {}

  /** What a construction adds, given its picks' geometry and its number. */
  @FunctionalInterface
  interface Maker {

    /**
     * The objects.
     *
     * @param picked the picks' geometry, in order
     * @param number the number, or null when the construction takes none
     * @return the objects, none to add nothing
     */
    List<Made> make(List<Geometry> picked, Expr number);
  }

  /** A pick: an existing object, or a point the construction adds. */
  private record Picked(Optional<ObjectId> existing, Snap snap, Geometry geometry) {}

  private final List<Pick> picks;
  private final Optional<String> number;
  private final boolean segmentAsEnds;
  private final Maker maker;
  private Optional<String> refusal = Optional.empty();
  private final Optional<String> refusalKey;

  private final List<Picked> picked = new ArrayList<>();
  private final List<CanvasObject> pickedAs = new ArrayList<>();
  private Scene scene;
  private Snap snap;
  private Pointer pointer;

  private ConstructionTool(
      List<Pick> picks, Optional<String> number, boolean segmentAsEnds, Maker maker) {
    this(picks, number, segmentAsEnds, maker, Optional.empty());
  }

  private ConstructionTool(
      List<Pick> picks,
      Optional<String> number,
      boolean segmentAsEnds,
      Maker maker,
      Optional<String> refusalKey) {
    this.picks = List.copyOf(picks);
    this.number = number;
    this.segmentAsEnds = segmentAsEnds;
    this.maker = maker;
    this.refusalKey = refusalKey;
  }

  /**
   * Why a click at the pointer would add nothing, as a key the app's bundle words (the tangents
   * tool's {@code inside}, a point inside the circle), or empty when it would add something.
   *
   * @return the key
   */
  public Optional<String> refusal() {
    return refusal;
  }

  private static ConstructionTool points(int n, Maker maker) {
    return new ConstructionTool(Collections.nCopies(n, Pick.POINT), Optional.empty(), false, maker);
  }

  private static Made one(BiFunction<List<ObjectId>, ObjectId, CanvasObject> make) {
    return new Made(make, false);
  }

  /**
   * The segment tool: two points.
   *
   * @return it
   */
  public static ConstructionTool segment() {
    return points(
        2, (g, n) -> List.of(one((p, id) -> new Segment(id, Style.DEFAULT, p.get(0), p.get(1)))));
  }

  /**
   * The line tool: two points.
   *
   * @return it
   */
  public static ConstructionTool line() {
    return points(
        2,
        (g, n) ->
            List.of(
                one((p, id) -> new Line(id, Style.DEFAULT, new Line.Through(p.get(0), p.get(1))))));
  }

  /**
   * The ray tool: its start, then a point it passes through.
   *
   * @return it
   */
  public static ConstructionTool ray() {
    return points(
        2, (g, n) -> List.of(one((p, id) -> new Ray(id, Style.DEFAULT, p.get(0), p.get(1)))));
  }

  /**
   * The circle tool by centre and point: the centre, then a point on it.
   *
   * @return it
   */
  public static ConstructionTool circleCenterPoint() {
    return points(
        2,
        (g, n) ->
            List.of(
                one(
                    (p, id) ->
                        new Circle(
                            id, Style.DEFAULT, new Circle.CenterPoint(p.get(0), p.get(1))))));
  }

  /**
   * The circle tool by centre and radius: the centre, then the radius {@code r}, typed (it may read
   * sliders) or clicked (the distance from the centre to the click).
   *
   * @return it
   */
  public static ConstructionTool circleCenterRadius() {
    return new ConstructionTool(
        List.of(Pick.POINT),
        Optional.of("r"),
        false,
        (g, n) ->
            List.of(
                one(
                    (p, id) ->
                        new Circle(id, Style.DEFAULT, new Circle.CenterRadius(p.get(0), n)))));
  }

  /**
   * The circle tool through three points.
   *
   * @return it
   */
  public static ConstructionTool circleThreePoints() {
    return points(
        3,
        (g, n) ->
            List.of(
                one(
                    (p, id) ->
                        new Circle(
                            id,
                            Style.DEFAULT,
                            new Circle.ThreePoints(p.get(0), p.get(1), p.get(2))))));
  }

  /**
   * The perpendicular tool: a line, segment or ray, then a point.
   *
   * @return it
   */
  public static ConstructionTool perpendicular() {
    return new ConstructionTool(
        List.of(Pick.LINEAR, Pick.POINT),
        Optional.empty(),
        false,
        (g, n) ->
            List.of(
                one(
                    (p, id) ->
                        new Line(id, Style.DEFAULT, new Line.Perpendicular(p.get(1), p.get(0))))));
  }

  /**
   * The parallel tool: a line, segment or ray, then a point.
   *
   * @return it
   */
  public static ConstructionTool parallel() {
    return new ConstructionTool(
        List.of(Pick.LINEAR, Pick.POINT),
        Optional.empty(),
        false,
        (g, n) ->
            List.of(
                one(
                    (p, id) ->
                        new Line(id, Style.DEFAULT, new Line.Parallel(p.get(1), p.get(0))))));
  }

  /**
   * The perpendicular bisector tool: two points, or a segment.
   *
   * @return it
   */
  public static ConstructionTool perpendicularBisector() {
    return new ConstructionTool(
        List.of(Pick.POINT, Pick.POINT),
        Optional.empty(),
        true,
        (g, n) ->
            List.of(
                one(
                    (p, id) ->
                        new Line(
                            id,
                            Style.DEFAULT,
                            new Line.PerpendicularBisector(p.get(0), p.get(1))))));
  }

  /**
   * The angle bisector tool: a point on one arm, the vertex, a point on the other arm.
   *
   * @return it
   */
  public static ConstructionTool angleBisector() {
    return points(
        3,
        (g, n) ->
            List.of(
                one(
                    (p, id) ->
                        new Line(
                            id,
                            Style.DEFAULT,
                            new Line.AngleBisector(p.get(0), p.get(1), p.get(2))))));
  }

  /**
   * The midpoint tool: two points, or a segment; the midpoint is named.
   *
   * @return it
   */
  public static ConstructionTool midpoint() {
    return new ConstructionTool(
        List.of(Pick.POINT, Pick.POINT),
        Optional.empty(),
        true,
        (g, n) ->
            List.of(
                new Made((p, id) -> new Midpoint(id, Style.DEFAULT, p.get(0), p.get(1)), true)));
  }

  /**
   * The tangents tool: a circle, then a point: the two tangents from a point outside it, the one at
   * a point on it, none from a point inside.
   *
   * @return it
   */
  public static ConstructionTool tangents() {
    return new ConstructionTool(
        List.of(Pick.CIRCLE, Pick.POINT),
        Optional.empty(),
        false,
        ConstructionTool::tangentLines,
        Optional.of("inside"));
  }

  /** The tangents from the point to the circle: two, one on the circle, none inside. */
  private static List<Made> tangentLines(List<Geometry> g, Expr n) {
    Optional<Parametrisation.Circular> c = Constructions.circle(g.get(0));
    if (c.isEmpty() || !(g.get(1) instanceof Geometry.Point at)) {
      return List.of();
    }
    Optional<Parametrisation.Linear> t0 = Constructions.tangent(c.get(), at, 0);
    if (t0.isEmpty()) {
      return List.of();
    }
    List<Made> out = new ArrayList<>();
    int count = Constructions.onCircle(c.get(), at) ? 1 : 2;
    for (int k = 0; k < count; k++) {
      int index = k;
      out.add(
          one(
              (p, id) ->
                  new Line(id, Style.DEFAULT, new Line.CircleTangent(p.get(0), p.get(1), index))));
    }
    return out;
  }

  /**
   * The regular polygon tool: two points, then the number of sides {@code n}, typed (it may read
   * sliders); the polygon lies to the left of the side from the first to the second.
   *
   * @return it
   */
  public static ConstructionTool regularPolygon() {
    return new ConstructionTool(
        List.of(Pick.POINT, Pick.POINT),
        Optional.of("n"),
        false,
        (g, n) ->
            List.of(
                one(
                    (p, id) ->
                        new Polygon(
                            id, Style.DEFAULT, new Polygon.Regular(p.get(0), p.get(1), n)))));
  }

  /**
   * The angle tool: a point on one arm, the vertex, a point on the other arm.
   *
   * @return it
   */
  public static ConstructionTool angle() {
    return points(
        3,
        (g, n) ->
            List.of(one((p, id) -> new Angle(id, Style.DEFAULT, p.get(0), p.get(1), p.get(2)))));
  }

  @Override
  public boolean press(Scene scene, Pointer pointer, Consumer<Edit> edits) {
    move(scene, pointer);
    if (picked.size() < picks.size()) {
      Pick want = picks.get(picked.size());
      if (want == Pick.POINT) {
        Optional<CanvasObject> segment = segmentEnds(scene, pointer);
        if (segment.isPresent() && picked.isEmpty()) {
          Segment s = (Segment) segment.get();
          pick(scene, new Snap.Existing(s.start(), 0, 0), s.start());
          pick(scene, new Snap.Existing(s.end(), 0, 0), s.end());
        } else if (snap instanceof Snap.Existing e && pickedIds().contains(e.point())) {
          return true;
        } else {
          pick(scene, snap, snap instanceof Snap.Existing e ? e.point() : null);
        }
      } else {
        Optional<ObjectId> o = object(scene, pointer, want);
        if (o.isEmpty()) {
          return false;
        }
        pick(scene, null, o.get());
      }
    } else if (number.isPresent() && number.get().equals("r")) {
      Geometry.Point c = (Geometry.Point) picked.getFirst().geometry();
      double[] at = Snaps.at(snap);
      double r = Math.hypot(at[0] - c.x(), at[1] - c.y());
      add(Picks.number(r, scene.window()), edits);
      return true;
    }
    if (picked.size() == picks.size() && number.isEmpty()) {
      add(null, edits);
    }
    return true;
  }

  private void pick(Scene scene, Snap s, ObjectId existing) {
    Geometry g =
        existing != null
            ? scene.canvas().geometryOf(existing)
            : new Geometry.Point(Snaps.at(s)[0], Snaps.at(s)[1]);
    picked.add(new Picked(Optional.ofNullable(existing), s, g));
    pickedAs.add(existing != null ? scene.canvas().object(existing).orElse(null) : null);
  }

  private Set<ObjectId> pickedIds() {
    Set<ObjectId> out = new HashSet<>();
    for (Picked p : picked) {
      p.existing().ifPresent(out::add);
    }
    return out;
  }

  /** A segment under the pointer's polyline, for the tools that take one as its two ends. */
  private Optional<CanvasObject> segmentEnds(Scene scene, Pointer pointer) {
    if (!segmentAsEnds || snap instanceof Snap.Existing) {
      return Optional.empty();
    }
    return pointer
        .picked()
        .filter(p -> p.hit().kind() == Hit.Kind.POLYLINE)
        .flatMap(p -> scene.canvas().object(p.id()))
        .filter(o -> o instanceof Segment);
  }

  /** The existing line, segment, ray or circle under the pointer that a pick wants. */
  private static Optional<ObjectId> object(Scene scene, Pointer pointer, Pick want) {
    return pointer
        .picked()
        .filter(p -> p.hit().kind() == Hit.Kind.POLYLINE)
        .map(Pointer.Picked::id)
        .filter(
            id ->
                scene
                    .canvas()
                    .object(id)
                    .filter(o -> want == Pick.LINEAR ? o.isLinear() : o.isCircle())
                    .isPresent());
  }

  @Override
  public Optional<String> wanted() {
    return picked.size() == picks.size() ? number : Optional.empty();
  }

  @Override
  public void typed(Expr e, double value, Consumer<Edit> edits) {
    if (picked.size() == picks.size() && number.isPresent()) {
      add(e, edits);
    }
  }

  /** Adds the construction with the new points it needs, as one step, and starts again. */
  private void add(Expr n, Consumer<Edit> edits) {
    List<Geometry> geometry = picked.stream().map(Picked::geometry).toList();
    List<Made> made = maker.make(geometry, n);
    if (made.isEmpty()) {
      cancel();
      return;
    }
    List<Edit.New> out = new ArrayList<>();
    int[] at = new int[picked.size()];
    for (int k = 0; k < at.length; k++) {
      Picked p = picked.get(k);
      at[k] = p.existing().isPresent() ? -1 : Snaps.point(p.snap(), out);
    }
    List<Picked> ps = List.copyOf(picked);
    for (Made m : made) {
      out.add(
          new Edit.New(
              ids -> {
                List<ObjectId> resolved = new ArrayList<>();
                for (int k = 0; k < ps.size(); k++) {
                  Optional<ObjectId> e = ps.get(k).existing();
                  resolved.add(e.isPresent() ? e.get() : ids.get(at[k]));
                }
                return m.make().apply(resolved, ids.getLast());
              },
              m.named()));
    }
    cancel();
    edits.accept(new Edit.Add(out));
  }

  @Override
  public void drag(Scene scene, Pointer pointer, Consumer<Edit> edits) {}

  @Override
  public void release(Scene scene, Pointer pointer, Consumer<Edit> edits) {}

  @Override
  public void move(Scene scene, Pointer pointer) {
    this.scene = scene;
    this.pointer = pointer;
    for (int k = 0; k < picked.size(); k++) {
      if (!stillThere(scene, k)) {
        picked.clear(); // a pick removed meanwhile by an undo, or its id taken since
        pickedAs.clear();
        break;
      }
    }
    snap = Snap.of(scene, pointer);
    refusal =
        refusalKey.isPresent()
                && picked.size() == picks.size() - 1
                && maker.make(withPointer(), null).isEmpty()
            ? refusalKey
            : Optional.empty();
  }

  /** The picks' geometry with the pointer's snap as the last point. */
  private List<Geometry> withPointer() {
    List<Geometry> out = new ArrayList<>(picked.stream().map(Picked::geometry).toList());
    double[] at = Snaps.at(snap);
    out.add(new Geometry.Point(at[0], at[1]));
    return out;
  }

  /** Whether an existing pick is still the object it was (a point still a point). */
  private boolean stillThere(Scene scene, int k) {
    Picked p = picked.get(k);
    if (p.existing().isEmpty()) {
      return p.snap() instanceof Snap.Free
          || p.snap() instanceof Snap.OnPath o && scene.canvas().object(o.path()).isPresent();
    }
    Optional<CanvasObject> now = scene.canvas().object(p.existing().get());
    CanvasObject then = pickedAs.get(k);
    return now.isPresent() && (then.isPoint() ? now.get().isPoint() : now.get().equals(then));
  }

  @Override
  public void cancel() {
    picked.clear();
    pickedAs.clear();
    refusal = Optional.empty();
  }

  @Override
  public boolean inProgress() {
    return !picked.isEmpty();
  }

  @Override
  public void paint(Painter painter, CanvasTheme theme) {
    if (scene == null || snap == null) {
      return;
    }
    for (Picked p : picked) {
      if (p.snap() != null) {
        Preview.snap(painter, theme, p.snap());
      } else {
        p.existing().ifPresent(id -> Preview.curve(painter, theme, scene.canvas(), id));
      }
    }
    if (picked.size() < picks.size()) {
      Pick want = picks.get(picked.size());
      if (want == Pick.POINT) {
        Preview.snap(painter, theme, snap);
      } else {
        object(scene, pointer, want)
            .ifPresent(id -> Preview.curve(painter, theme, scene.canvas(), id));
      }
    }
    for (Geometry g : preview()) {
      Preview.geometry(painter, theme, g);
    }
  }

  /**
   * The objects the pointer would complete, through the model's recompute on the canvas with the
   * picks and the pointer as free points; for a radius, the circle through the pointer.
   */
  private List<Geometry> preview() {
    boolean lastPoint = picked.size() == picks.size() - 1 && picks.getLast() == Pick.POINT;
    boolean radius = picked.size() == picks.size() && number.equals(Optional.of("r"));
    if (!(lastPoint && number.isEmpty()) && !radius) {
      return List.of();
    }
    double[] at = Snaps.at(snap);
    List<Picked> ps = new ArrayList<>(picked);
    ps.add(
        new Picked(
            snap instanceof Snap.Existing e ? Optional.of(e.point()) : Optional.empty(),
            snap,
            new Geometry.Point(at[0], at[1])));
    CanvasState s = scene.canvas();
    List<ObjectId> ids = new ArrayList<>();
    Set<ObjectId> fresh = new HashSet<>();
    for (Picked p : ps) {
      if (p.existing().isPresent()) {
        ids.add(p.existing().get());
      } else {
        Geometry.Point g = (Geometry.Point) p.geometry();
        fresh.add(s.nextObjectId());
        ids.add(s.nextObjectId());
        s = s.add(i -> new FreePoint(i, Style.DEFAULT, g.x(), g.y()));
      }
    }
    List<Made> made =
        radius
            ? List.of(
                one(
                    (p, self) ->
                        new Circle(
                            self, Style.DEFAULT, new Circle.CenterPoint(p.get(0), p.get(1)))))
            : maker.make(ps.stream().map(Picked::geometry).toList(), null);
    List<ObjectId> added = new ArrayList<>();
    List<ObjectId> resolved = List.copyOf(ids);
    try {
      for (Made m : made) {
        added.add(s.nextObjectId());
        s = s.add(i -> m.make().apply(resolved, i));
      }
    } catch (IllegalArgumentException e) {
      return List.of(); // the pointer on a point already picked
    }
    fresh.addAll(added);
    CanvasState done =
        s.recompute(
            fresh,
            Set.of(),
            Preview.NO_SAMPLER,
            scene.window(),
            Recomputes.standard(),
            Cancellation.NEVER);
    return added.stream().map(done::geometryOf).toList();
  }
}
