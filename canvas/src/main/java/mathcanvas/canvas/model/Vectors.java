package mathcanvas.canvas.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;

/**
 * The geometry of the vector kinds (docs/reference/canvas.md, <b>Object model</b>): an arrow from
 * its tail to its head, a vector sum drawn tip to tail with its parallelogram, and a projection
 * with its perpendicular. Components are doubles, as every plotted quantity is; a bound arrow's
 * value is read through the sampler, component by component.
 */
final class Vectors {

  private static final Symbol LENGTH = Symbol.of("length");
  private static final Symbol NTH = Symbol.of("nth");
  private static final Geometry.Point ORIGIN = new Geometry.Point(0, 0);

  private Vectors() {}

  /**
   * An arrow: from its tail (a point, or the origin) to its head (a point, the origin, or the tail
   * moved by its variable's value, undefined unless that is a 2-vector of finite real numbers).
   */
  static Geometry arrow(Arrow a, Map<ObjectId, Geometry> parents, Sampler sampler) {
    Geometry.Point tail = at(a.tail(), parents);
    return switch (a.head()) {
      case Arrow.At h -> new Geometry.Arrow(tail, at(h.point(), parents), List.of());
      case Arrow.Bound b -> {
        Symbol name = Symbol.of(b.variable());
        if (sampler.value(Apply.of(LENGTH, name), Map.of()) != 2) {
          yield Geometry.UNDEFINED;
        }
        yield arrow(
            tail,
            tail.x() + sampler.value(component(name, 1), Map.of()),
            tail.y() + sampler.value(component(name, 2), Map.of()));
      }
    };
  }

  /**
   * A vector sum: from {@code u}'s tail {@code T} to {@code T + u + v}, with {@code v} translated
   * to {@code u}'s head as an arrow, the side from {@code T + v} dashed, and the side from {@code
   * T} to {@code T + v} dashed unless {@code v} is drawn there already (its tail is {@code T}).
   */
  static Geometry sum(VectorSum s, Map<ObjectId, Geometry> parents) {
    Geometry.Arrow u = (Geometry.Arrow) parents.get(s.u());
    Geometry.Arrow v = (Geometry.Arrow) parents.get(s.v());
    Geometry.Point t = u.tail();
    double hx = t.x() + u.dx() + v.dx();
    double hy = t.y() + u.dy() + v.dy();
    double vx = t.x() + v.dx();
    double vy = t.y() + v.dy();
    if (!finite(hx, hy, vx, vy)) {
      return Geometry.UNDEFINED;
    }
    Geometry.Point head = new Geometry.Point(hx, hy);
    Geometry.Point tv = new Geometry.Point(vx, vy);
    List<Geometry.Guide> guides = new ArrayList<>();
    guides.add(new Geometry.Guide(u.head(), head, true));
    guides.add(new Geometry.Guide(tv, head, false));
    if (!v.tail().equals(t)) {
      guides.add(new Geometry.Guide(t, tv, false));
    }
    return new Geometry.Arrow(t, head, guides);
  }

  /**
   * A projection of {@code u} onto {@code v}: from {@code v}'s tail {@code P} to {@code P + (u·v /
   * v·v) v}, with the perpendicular from {@code P + u} dashed, and {@code u} translated to {@code
   * P} dashed unless {@code u} is drawn there already (its tail is {@code P}); undefined while
   * {@code v} is the zero vector.
   */
  static Geometry projection(Projection p, Map<ObjectId, Geometry> parents) {
    Geometry.Arrow u = (Geometry.Arrow) parents.get(p.u());
    Geometry.Arrow v = (Geometry.Arrow) parents.get(p.v());
    double vv = v.dx() * v.dx() + v.dy() * v.dy();
    double k = (u.dx() * v.dx() + u.dy() * v.dy()) / vv;
    Geometry.Point t = v.tail();
    double hx = t.x() + k * v.dx();
    double hy = t.y() + k * v.dy();
    double ux = t.x() + u.dx();
    double uy = t.y() + u.dy();
    if (!(vv > 0) || !finite(hx, hy, ux, uy)) {
      return Geometry.UNDEFINED;
    }
    Geometry.Point head = new Geometry.Point(hx, hy);
    Geometry.Point tu = new Geometry.Point(ux, uy);
    List<Geometry.Guide> guides = new ArrayList<>();
    guides.add(new Geometry.Guide(tu, head, false));
    if (!u.tail().equals(t)) {
      guides.add(new Geometry.Guide(t, tu, false));
    }
    return new Geometry.Arrow(t, head, guides);
  }

  private static Expr component(Symbol name, int k) {
    return Apply.of(NTH, name, Num.of(k));
  }

  private static Geometry.Point at(Optional<ObjectId> point, Map<ObjectId, Geometry> parents) {
    return point.map(id -> (Geometry.Point) parents.get(id)).orElse(ORIGIN);
  }

  private static Geometry arrow(Geometry.Point tail, double x, double y) {
    return finite(x, y)
        ? new Geometry.Arrow(tail, new Geometry.Point(x, y), List.of())
        : Geometry.UNDEFINED;
  }

  private static boolean finite(double... values) {
    for (double v : values) {
      if (!Double.isFinite(v)) {
        return false;
      }
    }
    return true;
  }
}
