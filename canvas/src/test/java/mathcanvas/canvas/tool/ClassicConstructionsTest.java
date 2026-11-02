package mathcanvas.canvas.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import mathcanvas.canvas.hit.Hit;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.Intersection;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Parametrisation;
import mathcanvas.canvas.model.Path;
import mathcanvas.canvas.model.Recomputes;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Window;
import mathcanvas.canvas.sample.Samplers;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Settings;
import org.junit.jupiter.api.Test;

/**
 * The classic constructions of a triangle built by the tools' clicks, as a user builds them: the
 * circumcentre where two perpendicular bisectors meet and the circumcircle through the vertices,
 * the incentre where two angle bisectors meet, and the nine-point circle through the sides'
 * midpoints with the feet of the altitudes on it; checked against the values computed by hand, and
 * again after a vertex is dragged.
 */
class ClassicConstructionsTest {

  private static final Window W = new Window(-10, 10, -10, 10);
  private static final Sampler SAMPLER = new Samplers().over(Environment.EMPTY, Settings.DEFAULT);
  private static final double EPS = 1e-12;

  private CanvasState canvas = CanvasState.EMPTY;

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  private void recompute() {
    canvas = canvas.recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
  }

  /** Applies an addition as the app does; returns the ids it gave. */
  private List<ObjectId> apply(Edit e) {
    List<ObjectId> ids = new ArrayList<>();
    for (CanvasObject o : ((Edit.Add) e).made(canvas.nextObjectId())) {
      canvas = canvas.add(i -> o);
      ids.add(o.id());
    }
    recompute();
    return ids;
  }

  private Pointer point(ObjectId id) {
    Geometry.Point p = (Geometry.Point) canvas.geometryOf(id);
    Hit hit = new Hit(0, Hit.Kind.POINT, -1, 0, p.x(), p.y(), 1);
    return new Pointer(p.x(), p.y(), Optional.of(new Pointer.Picked(id, hit)));
  }

  private Pointer curve(ObjectId id, double x, double y) {
    Hit hit = new Hit(0, Hit.Kind.POLYLINE, 0, 0, x, y, 1);
    return new Pointer(x, y, Optional.of(new Pointer.Picked(id, hit)));
  }

  /** Clicks a tool through its picks; returns the ids of what the last click added. */
  private List<ObjectId> clicks(Tool tool, Pointer... ps) {
    List<Edit> edits = new ArrayList<>();
    for (Pointer p : ps) {
      tool.press(new Scene(canvas, W, Scene.Slopes.NONE), p, edits::add);
    }
    assertThat(edits).hasSize(1);
    return apply(edits.getFirst());
  }

  /** The intersection of two lines, as the intersection tool's search adds it. */
  private ObjectId meet(ObjectId a, ObjectId b) {
    return apply(
            new Edit.Add(
                List.of(
                    new Edit.New(
                        ids -> Intersection.of(ids.getLast(), Style.DEFAULT, a, b, 0), true))))
        .getFirst();
  }

  private Geometry.Point at(ObjectId id) {
    return (Geometry.Point) canvas.geometryOf(id);
  }

  private Parametrisation.Circular circle(ObjectId id) {
    return (Parametrisation.Circular)
        ((Path.Parametrised) ((Geometry.Curve) canvas.geometryOf(id)).path()).parametrisation();
  }

  private static void near(Geometry.Point p, double x, double y) {
    assertThat(p.x()).isCloseTo(x, within(EPS));
    assertThat(p.y()).isCloseTo(y, within(EPS));
  }

  /** What the construction made. */
  private record Figure(
      ObjectId a,
      ObjectId b,
      ObjectId c,
      ObjectId circumcentre,
      ObjectId circumcircle,
      ObjectId incentre,
      ObjectId ninePoint,
      List<ObjectId> feet) {}

  /** Builds every construction on the triangle by clicks, its vertices new free points. */
  private Figure build(double[] a, double[] b, double[] c) {
    List<ObjectId> ab =
        clicks(ConstructionTool.segment(), Pointer.at(a[0], a[1]), Pointer.at(b[0], b[1]));
    ObjectId pa = ab.get(0);
    ObjectId pb = ab.get(1);
    ObjectId sideAb = ab.get(2);
    List<ObjectId> bc = clicks(ConstructionTool.segment(), point(pb), Pointer.at(c[0], c[1]));
    ObjectId pc = bc.get(0);
    ObjectId sideBc = bc.get(1);
    ObjectId sideCa = clicks(ConstructionTool.segment(), point(pc), point(pa)).getFirst();

    // the circumcentre: two perpendicular bisectors, one by its segment, one by its ends
    Geometry.Point onAb = at(pa);
    ObjectId bisectorAb =
        clicks(
                ConstructionTool.perpendicularBisector(),
                curve(sideAb, (a[0] + b[0]) / 2, (a[1] + b[1]) / 2))
            .getFirst();
    ObjectId bisectorCa =
        clicks(ConstructionTool.perpendicularBisector(), point(pc), point(pa)).getFirst();
    ObjectId circumcentre = meet(bisectorAb, bisectorCa);
    ObjectId circumcircle =
        clicks(ConstructionTool.circleThreePoints(), point(pa), point(pb), point(pc)).getFirst();

    // the incentre: two angle bisectors
    ObjectId atA =
        clicks(ConstructionTool.angleBisector(), point(pb), point(pa), point(pc)).getFirst();
    ObjectId atB =
        clicks(ConstructionTool.angleBisector(), point(pa), point(pb), point(pc)).getFirst();
    ObjectId incentre = meet(atA, atB);

    // the nine-point circle through the midpoints, and the feet of the altitudes
    ObjectId mab =
        clicks(ConstructionTool.midpoint(), curve(sideAb, onAb.x(), onAb.y())).getFirst();
    ObjectId mbc = clicks(ConstructionTool.midpoint(), point(pb), point(pc)).getFirst();
    ObjectId mca = clicks(ConstructionTool.midpoint(), point(pc), point(pa)).getFirst();
    ObjectId ninePoint =
        clicks(ConstructionTool.circleThreePoints(), point(mab), point(mbc), point(mca)).getFirst();
    List<ObjectId> feet = new ArrayList<>();
    ObjectId[][] altitudes = {{sideAb, pc}, {sideBc, pa}, {sideCa, pb}};
    for (ObjectId[] alt : altitudes) {
      Geometry.Point on = at(alt[1]);
      ObjectId perpendicular =
          clicks(ConstructionTool.perpendicular(), curve(alt[0], on.x(), on.y()), point(alt[1]))
              .getFirst();
      feet.add(
          meet(
              alt[0].compareTo(perpendicular) < 0 ? alt[0] : perpendicular,
              alt[0].compareTo(perpendicular) < 0 ? perpendicular : alt[0]));
    }
    return new Figure(pa, pb, pc, circumcentre, circumcircle, incentre, ninePoint, feet);
  }

  private void check(
      Figure f, double ox, double oy, double r2, double ix, double iy, double nx, double ny) {
    near(at(f.circumcentre()), ox, oy);
    Parametrisation.Circular circum = circle(f.circumcircle());
    assertThat(circum.cx()).isCloseTo(ox, within(EPS));
    assertThat(circum.cy()).isCloseTo(oy, within(EPS));
    assertThat(circum.r() * circum.r()).isCloseTo(r2, within(EPS));
    near(at(f.incentre()), ix, iy);
    Parametrisation.Circular nine = circle(f.ninePoint());
    assertThat(nine.cx()).isCloseTo(nx, within(EPS));
    assertThat(nine.cy()).isCloseTo(ny, within(EPS));
    assertThat(nine.r() * nine.r()).isCloseTo(r2 / 4, within(EPS));
    for (ObjectId foot : f.feet()) {
      Geometry.Point p = at(foot);
      assertThat(Math.hypot(p.x() - nx, p.y() - ny)).isCloseTo(Math.sqrt(r2) / 2, within(1e-12));
    }
  }

  @Test
  void theRightTriangle345() {
    Figure f = build(new double[] {0, 0}, new double[] {4, 0}, new double[] {0, 3});
    // circumcentre the hypotenuse's midpoint (2, 3/2), R = 5/2; incentre (1, 1), r = 1; the
    // nine-point centre halfway from the circumcentre to the orthocentre A: (1, 3/4)
    check(f, 2, 1.5, 6.25, 1, 1, 1, 0.75);
  }

  @Test
  void anAcuteTriangleAndAVertexDragged() {
    Figure f = build(new double[] {0, 0}, new double[] {6, 0}, new double[] {2, 4});
    // circumcentre (3, 1), R² = 10; orthocentre A + B + C − 2O = (2, 2), so the nine-point centre
    // (5/2, 3/2); the incentre (a A + b B + c C)/(a + b + c), a = |BC| = 4√2, b = |CA| = 2√5,
    // c = |AB| = 6
    double a = 4 * Math.sqrt(2);
    double b = 2 * Math.sqrt(5);
    double c = 6;
    check(f, 3, 1, 10, (6 * b + 2 * c) / (a + b + c), 4 * c / (a + b + c), 2.5, 1.5);

    // C dragged to (1, 5): O (3, 2), R² = 13, H (1, 1), N (2, 3/2); a = 5√2, b = √26, c = 6
    FreePoint pc = (FreePoint) canvas.object(f.c()).orElseThrow();
    canvas =
        canvas
            .replace(pc.at(1, 5))
            .recompute(
                Set.of(f.c()), Set.of(), SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
    a = 5 * Math.sqrt(2);
    b = Math.sqrt(26);
    check(f, 3, 2, 13, (6 * b + c) / (a + b + c), 5 * c / (a + b + c), 2, 1.5);
  }
}
