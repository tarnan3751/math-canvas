package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import mathcanvas.engine.compile.Compiled;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Symbol;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * The triangle solver on random data: its output satisfies the laws of sines and cosines and the
 * angle sum, in radians and in degrees, and it gives as many triangles as the data allow.
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

  private static double number(Expr e) {
    return Compiled.compile(e, List.of(), Settings.DEFAULT).eval();
  }

  private static org.assertj.core.data.Offset<Double> within(double d) {
    return org.assertj.core.data.Offset.offset(d);
  }
}
