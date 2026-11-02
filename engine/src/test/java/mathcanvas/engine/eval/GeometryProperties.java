package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import mathcanvas.engine.compile.Compiled;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * The geometry solvers on random data: the triangle solver's output satisfies the laws of sines and
 * cosines and the angle sum, in radians and in degrees, and gives as many triangles as the data
 * allow; the coordinate-geometry functions agree with direct computation; and a point of a conic's
 * relation has the conic's defining property.
 */
class GeometryProperties {

  private static final Symbol X = Symbol.of("x");
  private static final Symbol Y = Symbol.of("y");
  private static final int[] ANGLES = {30, 45, 60, 90, 120, 135, 150, 20, 75};

  /** A call and the mode it runs in. */
  record Call(String text, boolean degrees) {}

  @Provide
  Arbitrary<Call> triangles() {
    return Arbitraries.randomValue(GeometryProperties::triangle);
  }

  static Call triangle(Random random) {
    boolean degrees = random.nextBoolean();
    String[] sides = {"a", "b", "c"};
    String[] angles = {"A", "B", "C"};
    int i = random.nextInt(3);
    int j = (i + 1 + random.nextInt(2)) % 3;
    String angle = angle(random, degrees);
    String text =
        switch (random.nextInt(4)) {
          case 0 -> {
            int a = 2 + random.nextInt(9);
            int b = 2 + random.nextInt(9);
            int c = Math.abs(a - b) + 1 + random.nextInt(a + b - Math.abs(a - b) - 1);
            yield "a = " + a + ", b = " + b + ", c = " + c;
          }
          case 1 -> {
            int k = 3 - i - j;
            yield sides[i]
                + " = "
                + (1 + random.nextInt(9))
                + ", "
                + sides[j]
                + " = "
                + (1 + random.nextInt(9))
                + ", "
                + angles[k]
                + " = "
                + angle;
          }
          case 2 -> {
            int[] small = {30, 45, 60, 20};
            String second =
                degrees ? String.valueOf(small[random.nextInt(4)]) : small[random.nextInt(4)] + "°";
            yield angles[i]
                + " = "
                + angle
                + ", "
                + angles[j]
                + " = "
                + second
                + ", "
                + sides[random.nextInt(3)]
                + " = "
                + (1 + random.nextInt(9));
          }
          default ->
              sides[i]
                  + " = "
                  + (1 + random.nextInt(9))
                  + ", "
                  + sides[j]
                  + " = "
                  + (1 + random.nextInt(9))
                  + ", "
                  + angles[i]
                  + " = "
                  + angle;
        };
    return new Call("triangle(" + text + ")", degrees);
  }

  private static String angle(Random random, boolean degrees) {
    int a = ANGLES[random.nextInt(ANGLES.length)];
    return degrees ? String.valueOf(a) : a + "°";
  }

  @Property(tries = 300)
  void trianglesObeyTheLaws(@ForAll("triangles") Call call) {
    Settings settings = call.degrees() ? Run.DEGREES : Settings.DEFAULT;
    Expr value = Run.value(call.text(), settings);
    List<Expr> triangles = value instanceof SetExpr s ? s.items() : List.of(value);
    double half = call.degrees() ? 180 : Math.PI;
    for (Expr t : triangles) {
      assertThat(t).as(call.text()).isInstanceOf(ListExpr.class);
      double[] p = new double[6];
      List<Expr> items = ((ListExpr) t).items();
      assertThat(items).hasSize(6);
      for (int k = 0; k < 6; k++) {
        p[k] = number(((Relation) items.get(k)).right());
        if (k >= 3) {
          p[k] = p[k] * Math.PI / half;
        }
      }
      String at = call + " gives " + t;
      assertThat(p[3] + p[4] + p[5]).as(at).isCloseTo(Math.PI, within(1e-9));
      for (int k = 0; k < 3; k++) {
        assertThat(p[k]).as(at).isPositive();
        assertThat(p[k + 3]).as(at).isPositive();
        assertThat(p[k] / Math.sin(p[k + 3])).as(at).isCloseTo(p[0] / Math.sin(p[3]), within(1e-9));
        double a = p[k];
        double b = p[(k + 1) % 3];
        double c = p[(k + 2) % 3];
        assertThat(a * a)
            .as(at)
            .isCloseTo(b * b + c * c - 2 * b * c * Math.cos(p[k + 3]), within(1e-8));
      }
    }
  }

  /** The number of triangles the SSA data allow, from the given parts in doubles. */
  @Property(tries = 200)
  void theAmbiguousCaseCountsItsTriangles(@ForAll("ssa") int[] data) {
    int s = data[0];
    int o = data[1];
    int angle = data[2];
    Expr value = Run.value("triangle(a = " + s + ", b = " + o + ", A = " + angle + "°)");
    assertThat(value).isInstanceOf(SetExpr.class);
    double a = Math.toRadians(angle);
    double h = o * Math.sin(a);
    int expected;
    if (angle >= 90) {
      expected = s > o ? 1 : 0;
    } else if (Math.abs(s - h) < 1e-12) {
      expected = 1;
    } else if (s < h) {
      expected = 0;
    } else {
      expected = s < o ? 2 : 1;
    }
    assertThat(((SetExpr) value).items()).as("%s %s %s", s, o, angle).hasSize(expected);
  }

  @Provide
  Arbitrary<int[]> ssa() {
    return Arbitraries.randomValue(
        r -> new int[] {1 + r.nextInt(9), 1 + r.nextInt(9), ANGLES[r.nextInt(ANGLES.length)]});
  }

  @Property(tries = 200)
  void coordinateGeometryAgreesWithDirectComputation(@ForAll("points") int[] c) {
    String p = "[" + c[0] + "/" + c[4] + ", " + c[1] + "]";
    String q = "[" + c[2] + ", " + c[3] + "/" + c[5] + "]";
    double px = (double) c[0] / c[4];
    double py = c[1];
    double qx = c[2];
    double qy = (double) c[3] / c[5];
    assertThat(number(Run.value("distance(" + p + ", " + q + ")")))
        .isCloseTo(Math.hypot(qx - px, qy - py), within(1e-12));
    Expr mid = Run.value("midpoint(" + p + ", " + q + ")");
    assertThat(number(((Vector) mid).items().get(0))).isCloseTo((px + qx) / 2, within(1e-12));
    assertThat(number(((Vector) mid).items().get(1))).isCloseTo((py + qy) / 2, within(1e-12));
    if (px != qx) {
      assertThat(number(Run.value("slope(" + p + ", " + q + ")")))
          .isCloseTo((qy - py) / (qx - px), within(1e-9));
    }
    if (px != qx || py != qy) {
      Expr line = Run.value("lineequation(" + p + ", " + q + ")");
      Compiled l = Compiled.compile(difference(line), List.of(X, Y), Settings.DEFAULT);
      assertThat(l.eval(px, py)).isCloseTo(0, within(1e-9));
      assertThat(l.eval(qx, qy)).isCloseTo(0, within(1e-9));
    }
  }

  @Provide
  Arbitrary<int[]> points() {
    return Arbitraries.randomValue(
        r ->
            new int[] {
              r.nextInt(13) - 6,
              r.nextInt(13) - 6,
              r.nextInt(13) - 6,
              r.nextInt(13) - 6,
              1 + r.nextInt(3),
              1 + r.nextInt(3)
            });
  }

  @Property(tries = 200)
  void conicsHaveTheirDefiningProperty(@ForAll("conics") int[] c) {
    int kind = c[0];
    double fx = c[1];
    double fy = c[2];
    double gx = c[3];
    double gy = c[4];
    String f = "[" + c[1] + ", " + c[2] + "]";
    String g = "[" + c[3] + ", " + c[4] + "]";
    double between = Math.hypot(gx - fx, gy - fy);
    String call;
    double length = 0;
    double e = 0;
    // the directrix αx + βy + γ = 0
    double alpha = c[5];
    double beta = c[6] == 0 && c[5] == 0 ? 1 : c[6];
    double gamma = c[7];
    String directrix = c[5] + "x + (" + (long) beta + ") y + (" + c[7] + ") = 0";
    switch (kind) {
      case 0 -> {
        e = 1;
        call = "parabola(" + f + ", " + directrix + ")";
      }
      case 1 -> {
        e = c[8] / 4.0;
        call = "conic(" + f + ", " + directrix + ", " + c[8] + "/4)";
      }
      case 2 -> {
        length = between + 1 + c[8];
        call =
            "ellipse("
                + f
                + ", "
                + g
                + ", "
                + "distance("
                + f
                + ", "
                + g
                + ") + "
                + (1 + c[8])
                + ")";
      }
      default -> {
        if (between < 1e-9) {
          return;
        }
        length = between * c[8] / 9.0;
        call = "hyperbola(" + f + ", " + g + ", " + c[8] + "/9 distance(" + f + ", " + g + "))";
      }
    }
    if (kind == 0 || kind == 1) {
      if (Math.abs(alpha * fx + beta * fy + gamma) < 1e-9) {
        return;
      }
    }
    Expr relation = Run.value(call);
    Compiled p = Compiled.compile(difference(relation), List.of(X, Y), Settings.DEFAULT);
    Random random = new Random(c[9]);
    int checked = 0;
    double[] scales = {4, 1, 0.25};
    for (int t = 0; t < 30; t++) {
      // a small conic (an eccentricity well under 1) sits close to its focus
      double x = fx + (random.nextDouble() * 2 - 1) * scales[t % 3];
      // p(x, y) is quadratic in y: its coefficients from three values
      double p0 = p.eval(x, 0);
      double p1 = p.eval(x, 1);
      double pm = p.eval(x, -1);
      double qa = (p1 + pm) / 2 - p0;
      double qb = (p1 - pm) / 2;
      List<Double> ys = new ArrayList<>();
      if (Math.abs(qa) < 1e-12) {
        if (Math.abs(qb) > 1e-12) {
          ys.add(-p0 / qb);
        }
      } else {
        double disc = qb * qb - 4 * qa * p0;
        if (disc >= 0) {
          ys.add((-qb + Math.sqrt(disc)) / (2 * qa));
          ys.add((-qb - Math.sqrt(disc)) / (2 * qa));
        }
      }
      for (double y : ys) {
        double d1 = Math.hypot(x - fx, y - fy);
        double scale = 1 + Math.abs(x) + Math.abs(y);
        switch (kind) {
          case 0, 1 -> {
            double toLine = Math.abs(alpha * x + beta * y + gamma) / Math.hypot(alpha, beta);
            assertThat(d1)
                .as("%s at (%s, %s)", call, x, y)
                .isCloseTo(e * toLine, within(1e-6 * scale));
          }
          case 2 -> {
            double d2 = Math.hypot(x - gx, y - gy);
            assertThat(d1 + d2)
                .as("%s at (%s, %s)", call, x, y)
                .isCloseTo(length, within(1e-6 * scale));
          }
          default -> {
            double d2 = Math.hypot(x - gx, y - gy);
            assertThat(Math.abs(d1 - d2))
                .as("%s at (%s, %s)", call, x, y)
                .isCloseTo(length, within(1e-6 * scale));
          }
        }
        checked++;
      }
    }
    assertThat(checked).as(call).isPositive();
  }

  @Provide
  Arbitrary<int[]> conics() {
    return Arbitraries.randomValue(
        r ->
            new int[] {
              r.nextInt(4),
              r.nextInt(7) - 3,
              r.nextInt(7) - 3,
              r.nextInt(7) - 3,
              r.nextInt(7) - 3,
              r.nextInt(5) - 2,
              r.nextInt(5) - 2,
              r.nextInt(7) - 3,
              1 + r.nextInt(8),
              r.nextInt()
            });
  }

  /** The left side minus the right side of a relation. */
  private static Expr difference(Expr relation) {
    Relation r = (Relation) relation;
    return Canonical.of(Exprs.sub(r.left(), r.right()));
  }

  private static double number(Expr e) {
    return Compiled.compile(e, List.of(), Settings.DEFAULT).eval();
  }

  private static org.assertj.core.data.Offset<Double> within(double d) {
    return org.assertj.core.data.Offset.offset(d);
  }
}
