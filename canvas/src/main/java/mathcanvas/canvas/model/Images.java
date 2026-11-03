package mathcanvas.canvas.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Symbol;
import org.apache.commons.geometry.euclidean.twod.AffineTransformMatrix2D;
import org.apache.commons.geometry.euclidean.twod.Vector2D;

/**
 * The geometry of a transformation's image: the map, from the transformation's parents (Apache
 * Commons Geometry's affine matrices), applied to the source's geometry. A point maps to a point; a
 * line, segment or ray to its image, a line clipped to the window again; a circle to a circle (its
 * phase and direction following the map, so a point at a parameter on the image is the image of the
 * point at that parameter on the source); a polygon vertex by vertex; any other curve as a {@link
 * Parametrisation.Transformed} of its own, its sampled polylines mapped point by point, so an image
 * shows the part of the source sampled over the widened window (a graph rotated by a quarter turn
 * shows what was sampled of the graph, not its whole image); an arrow and an angle by their points
 * and directions. An image is undefined where its map is (an angle or a factor that is not a real
 * number) and, as every construction, where it is degenerate (a circle or a polygon dilated by 0).
 */
final class Images {

  private static final Symbol COS = Symbol.of("cos");
  private static final Symbol SIN = Symbol.of("sin");

  private Images() {}

  /**
   * An image's geometry.
   *
   * @param t the transformation
   * @param parents the parents' geometry
   * @param sampler evaluates an angle or a factor
   * @param window the visible window
   * @param wide the widened window a line is clipped to
   * @return the geometry
   */
  static Geometry of(
      Transform t, Map<ObjectId, Geometry> parents, Sampler sampler, Window window, Window wide) {
    Optional<Affine> map = map(t, parents, sampler);
    if (map.isEmpty() || !map.get().isFinite()) {
      return Geometry.UNDEFINED;
    }
    return image(parents.get(t.source()), map.get(), window, wide);
  }

  private static Optional<Affine> map(
      Transform t, Map<ObjectId, Geometry> parents, Sampler sampler) {
    return switch (t) {
      case Reflection r -> reflection(parents.get(r.in()));
      case Rotation r -> {
        Geometry.Point c = (Geometry.Point) parents.get(r.center());
        double cos = sampler.value(Apply.of(COS, r.angle()), Map.of());
        double sin = sampler.value(Apply.of(SIN, r.angle()), Map.of());
        yield Double.isFinite(cos) && Double.isFinite(sin)
            ? Optional.of(
                Affine.of(
                    AffineTransformMatrix2D.createTranslation(-c.x(), -c.y())
                        .premultiply(AffineTransformMatrix2D.of(cos, -sin, 0, sin, cos, 0))
                        .translate(c.x(), c.y())))
            : Optional.empty();
      }
      case Translation r -> {
        double[] v =
            switch (r.by()) {
              case Translation.Vector by ->
                  parents.get(by.vector()) instanceof Geometry.Arrow a
                      ? new double[] {a.dx(), a.dy()}
                      : null;
              case Translation.Points by -> {
                Geometry.Point a = (Geometry.Point) parents.get(by.from());
                Geometry.Point b = (Geometry.Point) parents.get(by.to());
                yield new double[] {b.x() - a.x(), b.y() - a.y()};
              }
            };
        yield v == null
            ? Optional.empty()
            : Optional.of(Affine.of(AffineTransformMatrix2D.createTranslation(v[0], v[1])));
      }
      case Dilation r -> {
        Geometry.Point c = (Geometry.Point) parents.get(r.center());
        double k = sampler.value(r.factor(), Map.of());
        yield Double.isFinite(k)
            ? Optional.of(
                Affine.of(
                    AffineTransformMatrix2D.createTranslation(-c.x(), -c.y())
                        .scale(k)
                        .translate(c.x(), c.y())))
            : Optional.empty();
      }
    };
  }

  /** The reflection in a point (a half turn) or in the line a straight path lies on. */
  private static Optional<Affine> reflection(Geometry mirror) {
    if (mirror instanceof Geometry.Point p) {
      return Optional.of(
          Affine.of(
              AffineTransformMatrix2D.createTranslation(-p.x(), -p.y())
                  .scale(-1)
                  .translate(p.x(), p.y())));
    }
    if (!(mirror instanceof Geometry.Curve c
        && c.path() instanceof Path.Parametrised q
        && q.parametrisation() instanceof Parametrisation.Linear l)) {
      return Optional.empty();
    }
    return Constructions.direction(mirror)
        .map(
            u -> {
              // reflecting in a line through the origin along u is 2uuᵀ − I
              double ux = u.getX();
              double uy = u.getY();
              AffineTransformMatrix2D flip =
                  AffineTransformMatrix2D.of(
                      2 * ux * ux - 1, 2 * ux * uy, 0, 2 * ux * uy, 2 * uy * uy - 1, 0);
              return Affine.of(
                  AffineTransformMatrix2D.createTranslation(-l.px(), -l.py())
                      .premultiply(flip)
                      .translate(l.px(), l.py()));
            });
  }

  /**
   * A geometry's image under a map.
   *
   * @param g the source's geometry
   * @param m the map
   * @param window the visible window
   * @param wide the widened window a line is clipped to
   * @return the image
   */
  static Geometry image(Geometry g, Affine m, Window window, Window wide) {
    return switch (g) {
      case Geometry.Point p -> point(m.apply(p.x(), p.y()));
      case Geometry.Curve c -> curve(c, m, window, wide);
      case Geometry.Arrow a -> {
        Geometry tail = point(m.apply(a.tail().x(), a.tail().y()));
        Geometry head = point(m.apply(a.head().x(), a.head().y()));
        if (!(tail instanceof Geometry.Point t) || !(head instanceof Geometry.Point h)) {
          yield Geometry.UNDEFINED;
        }
        List<Geometry.Guide> guides = new ArrayList<>();
        for (Geometry.Guide gd : a.guides()) {
          if (point(m.apply(gd.from().x(), gd.from().y())) instanceof Geometry.Point f
              && point(m.apply(gd.to().x(), gd.to().y())) instanceof Geometry.Point to) {
            guides.add(new Geometry.Guide(f, to, gd.arrow()));
          }
        }
        yield new Geometry.Arrow(t, h, guides);
      }
      case Geometry.Angle a -> {
        Sample u = m.applyVector(Math.cos(a.start()), Math.sin(a.start()));
        Sample w = m.applyVector(Math.cos(a.start() + a.sweep()), Math.sin(a.start() + a.sweep()));
        if (!(point(m.apply(a.vertex().x(), a.vertex().y())) instanceof Geometry.Point v)
            || Math.hypot(u.x(), u.y()) == 0
            || Math.hypot(w.x(), w.y()) == 0) {
          yield Geometry.UNDEFINED;
        }
        // the arms mapped, the arc again on the smaller side, from the arm it turns
        // counterclockwise from (the far one once a reflection reverses the turn)
        double sweep = Vector2D.of(u.x(), u.y()).angle(Vector2D.of(w.x(), w.y()));
        Sample first = u.x() * w.y() - u.y() * w.x() >= 0 ? u : w;
        yield new Geometry.Angle(v, Math.atan2(first.y(), first.x()), Math.min(sweep, Math.PI));
      }
      case Geometry.Undefined u -> Geometry.UNDEFINED;
      case Geometry.Region r -> Geometry.UNDEFINED;
      case Geometry.ValuedRegion r -> Geometry.UNDEFINED;
      case Geometry.Value v -> Geometry.UNDEFINED;
      case Geometry.Measure v -> Geometry.UNDEFINED;
      case Geometry.Field f -> Geometry.UNDEFINED;
      case Geometry.Contours c -> Geometry.UNDEFINED;
    };
  }

  private static Geometry point(Sample s) {
    return s.isFinite() ? new Geometry.Point(s.x(), s.y()) : Geometry.UNDEFINED;
  }

  private static Geometry curve(Geometry.Curve c, Affine m, Window window, Window wide) {
    if (!(c.path() instanceof Path.Parametrised p)) {
      return new Geometry.Curve(Path.POLYLINE_ONLY, mapped(c.branches(), m));
    }
    return switch (p.parametrisation()) {
      case Parametrisation.Linear l -> {
        Sample at = m.apply(l.px(), l.py());
        Sample d = m.applyVector(l.dx(), l.dy());
        Parametrisation.Linear image = new Parametrisation.Linear(at.x(), at.y(), d.x(), d.y());
        Path.Parametrised path = new Path.Parametrised(image, p.lo(), p.hi(), p.kind());
        if (Double.isInfinite(p.lo()) || Double.isInfinite(p.hi())) {
          yield d.x() == 0 && d.y() == 0
              ? Geometry.UNDEFINED
              : new Geometry.Curve(
                  path, Recomputes.clip(image, p.lo(), wide).map(List::of).orElse(List.of()));
        }
        yield new Geometry.Curve(path, mapped(c.branches(), m));
      }
      case Parametrisation.Circular k -> {
        Sample centre = m.apply(k.cx(), k.cy());
        double scale = Math.sqrt(Math.abs(m.determinant()));
        Sample first = m.applyVector(Math.cos(k.phase()), Math.sin(k.phase()));
        Optional<Parametrisation.Circular> base =
            Constructions.circle(new Geometry.Point(centre.x(), centre.y()), k.r() * scale);
        if (base.isEmpty()) {
          yield Geometry.UNDEFINED;
        }
        Parametrisation.Circular image =
            new Parametrisation.Circular(
                centre.x(),
                centre.y(),
                k.r() * scale,
                Math.atan2(first.y(), first.x()),
                Math.signum(m.determinant()) * k.dir());
        yield new Geometry.Curve(
            new Path.Parametrised(image, p.lo(), p.hi(), p.kind()),
            Shapes.circle(image, window, wide));
      }
      case Parametrisation.Polygonal g -> {
        double[] xs = new double[g.size()];
        double[] ys = new double[g.size()];
        List<Geometry.Point> vertices = new ArrayList<>();
        for (int i = 0; i < xs.length; i++) {
          Sample s = m.apply(g.x(i), g.y(i));
          xs[i] = s.x();
          ys[i] = s.y();
          vertices.add(new Geometry.Point(s.x(), s.y()));
        }
        if (Constructions.fewerThanThree(vertices)) {
          yield Geometry.UNDEFINED;
        }
        Parametrisation.Polygonal image = new Parametrisation.Polygonal(xs, ys);
        yield new Geometry.Curve(
            new Path.Parametrised(image, p.lo(), p.hi(), p.kind()), List.of(Shapes.polygon(image)));
      }
      case Parametrisation.Expressed e ->
          new Geometry.Curve(
              new Path.Parametrised(
                  new Parametrisation.Transformed(e, m), p.lo(), p.hi(), p.kind()),
              mapped(c.branches(), m));
      case Parametrisation.Transformed e ->
          new Geometry.Curve(
              new Path.Parametrised(
                  new Parametrisation.Transformed(e.source(), m.after(e.map())),
                  p.lo(),
                  p.hi(),
                  p.kind()),
              mapped(c.branches(), m));
    };
  }

  private static List<Polyline> mapped(List<Polyline> branches, Affine m) {
    List<Polyline> out = new ArrayList<>();
    for (Polyline b : branches) {
      double[] ts = new double[b.size()];
      double[] xs = new double[b.size()];
      double[] ys = new double[b.size()];
      for (int k = 0; k < b.size(); k++) {
        Sample s = m.apply(b.x(k), b.y(k));
        ts[k] = b.t(k);
        xs[k] = s.x();
        ys[k] = s.y();
      }
      out.add(new Polyline(ts, xs, ys));
    }
    return out;
  }
}
