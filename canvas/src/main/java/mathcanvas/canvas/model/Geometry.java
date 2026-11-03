package mathcanvas.canvas.model;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import mathcanvas.engine.expr.Expr;

/**
 * The geometric side of an object, computed from its definition by a {@link Recompute}: what the
 * painter draws and what hit testing and intersections work on. Undefined is a state, not an error:
 * an object whose geometry cannot be computed has {@link #UNDEFINED}, so do its dependents, and the
 * state clears when its parents are defined again.
 */
public sealed interface Geometry {

  /** The undefined state. */
  record Undefined() implements Geometry {}

  /** The one undefined geometry. */
  Undefined UNDEFINED = new Undefined();

  /**
   * A point.
   *
   * @param x the abscissa, finite
   * @param y the ordinate, finite
   */
  record Point(double x, double y) implements Geometry {

    /**
     * Checks the fields.
     *
     * @throws IllegalArgumentException if a coordinate is not finite
     */
    public Point {
      if (!Double.isFinite(x) || !Double.isFinite(y)) {
        throw new IllegalArgumentException("a point is finite");
      }
    }
  }

  /**
   * A curve: how to evaluate it, and its sampled branches. A curve that is defined may have no
   * branch in the sampled range.
   *
   * @param path how the curve is evaluated away from its samples
   * @param branches the polylines, split at gaps
   */
  record Curve(Path path, List<Polyline> branches) implements Geometry {

    /**
     * Copies the branches.
     *
     * @throws NullPointerException if a field is null
     */
    public Curve {
      Objects.requireNonNull(path, "path");
      branches = List.copyOf(branches);
    }
  }

  /**
   * A region, the inside of its rings under the even-odd rule.
   *
   * @param rings the closed boundary polylines
   */
  record Region(List<Polyline> rings) implements Geometry {

    /** Copies the rings. */
    public Region {
      rings = List.copyOf(rings);
    }
  }

  /**
   * A region with its value: a shaded area's integral, or a Riemann sum's rectangles and their sum.
   * The rings are closed polylines, the inside of the region under the even-odd rule.
   *
   * @param rings the closed boundary polylines
   * @param value the value, finite
   */
  record ValuedRegion(List<Polyline> rings, double value) implements Geometry {

    /**
     * Copies the rings.
     *
     * @throws IllegalArgumentException if the value is not finite
     */
    public ValuedRegion {
      rings = List.copyOf(rings);
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("a valued region has a finite value");
      }
    }
  }

  /**
   * A number with nothing to draw on the plane: a slider's value.
   *
   * @param value the value
   */
  record Value(double value) implements Geometry {}

  /**
   * A vector drawn as an arrow from its tail to its head, with the construction lines drawn with
   * it: a vector sum's translated vector and parallelogram sides, a projection's perpendicular.
   *
   * @param tail the tail
   * @param head the head
   * @param guides the construction lines
   */
  record Arrow(Point tail, Point head, List<Guide> guides) implements Geometry {

    /**
     * Copies the guides.
     *
     * @throws NullPointerException if a field is null
     */
    public Arrow {
      Objects.requireNonNull(tail, "tail");
      Objects.requireNonNull(head, "head");
      guides = List.copyOf(guides);
    }

    /**
     * The vector's abscissa, the head's less the tail's.
     *
     * @return it
     */
    public double dx() {
      return head.x() - tail.x();
    }

    /**
     * The vector's ordinate, the head's less the tail's.
     *
     * @return it
     */
    public double dy() {
      return head.y() - tail.y();
    }

    /**
     * The scalar component of this vector along another, {@code u·v / |v|}: the signed length of
     * this vector's projection onto it.
     *
     * @param v the vector along which
     * @return the component, NaN when {@code v} is the zero vector
     */
    public double componentAlong(Arrow v) {
      double length = Math.hypot(v.dx(), v.dy());
      return length > 0 ? (dx() * v.dx() + dy() * v.dy()) / length : Double.NaN;
    }
  }

  /**
   * A construction line drawn with an arrow: an arrow of its own (a vector sum's translated
   * vector), or a dashed line.
   *
   * @param from where it starts
   * @param to where it ends
   * @param arrow whether it is drawn solid with a head, else dashed with none
   */
  record Guide(Point from, Point to, boolean arrow) {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     */
    public Guide {
      Objects.requireNonNull(from, "from");
      Objects.requireNonNull(to, "to");
    }
  }

  /**
   * An angle at a vertex, drawn as an arc from the direction {@code start} counterclockwise through
   * {@code sweep}: the angle between two arms, never reflex, so the arc is on the smaller side.
   *
   * @param vertex the vertex
   * @param start the direction of the arc's first arm, in radians
   * @param sweep the measure, in radians, from 0 to π
   */
  record Angle(Point vertex, double start, double sweep) implements Geometry {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if the vertex is null
     * @throws IllegalArgumentException if the start is not finite or the sweep is outside [0, π]
     */
    public Angle {
      Objects.requireNonNull(vertex, "vertex");
      if (!Double.isFinite(start) || !(sweep >= 0 && sweep <= Math.PI)) {
        throw new IllegalArgumentException("an angle has a finite start and a sweep in [0, π]");
      }
    }
  }

  /**
   * A measurement's value with the point its label is written at: a distance, an area, a slope.
   *
   * @param value the value, finite
   * @param at where its label goes
   */
  record Measure(double value, Point at) implements Geometry {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if the point is null
     * @throws IllegalArgumentException if the value is not finite
     */
    public Measure {
      Objects.requireNonNull(at, "at");
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("a measure is finite");
      }
    }
  }

  /**
   * A field drawn as marks at a grid: a vector field's arrows, or a slope field's segments, with
   * the slope a solution curve through a slope field integrates.
   *
   * @param marks the marks, each from a point to another
   * @param arrows whether each mark has a head at its end (a vector field)
   * @param slope a slope field's {@code f} in {@code y' = f(x, y)}, empty for a vector field
   */
  record Field(List<Mark> marks, boolean arrows, Optional<Expr> slope) implements Geometry {

    /**
     * Copies the marks.
     *
     * @throws NullPointerException if a field is null
     */
    public Field {
      marks = List.copyOf(marks);
      Objects.requireNonNull(slope, "slope");
    }
  }

  /**
   * One mark of a field: a segment, or an arrow from its start to its end, with its place on the
   * field's colour ramp.
   *
   * @param from where it starts
   * @param to where it ends
   * @param weight its place on the ramp, 0 (the least magnitude) to 1 (the cap and above)
   */
  record Mark(Point from, Point to, double weight) {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a point is null
     * @throws IllegalArgumentException if the weight is outside [0, 1]
     */
    public Mark {
      Objects.requireNonNull(from, "from");
      Objects.requireNonNull(to, "to");
      if (!(weight >= 0 && weight <= 1)) {
        throw new IllegalArgumentException("a mark's weight is in [0, 1]");
      }
    }
  }

  /**
   * A contour plot's levels, in increasing order of value.
   *
   * @param levels the levels
   */
  record Contours(List<Level> levels) implements Geometry {

    /** Copies the levels. */
    public Contours {
      levels = List.copyOf(levels);
    }
  }

  /**
   * One level of a contour plot: where the function takes the value, and the point its label is
   * written at, if any part of it is in view.
   *
   * @param value the value, finite
   * @param lines the polylines, with NaN parameters
   * @param label where the label goes
   */
  record Level(double value, List<Polyline> lines, Optional<Point> label) {

    /**
     * Copies the lines.
     *
     * @throws NullPointerException if a field is null
     * @throws IllegalArgumentException if the value is not finite
     */
    public Level {
      lines = List.copyOf(lines);
      Objects.requireNonNull(label, "label");
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("a level is finite");
      }
    }
  }

  // Phase 7d: the geometry of the 3D view's objects.

  /**
   * The geometry of an object in space ({@link SpaceObject}), drawn by the 3D view only; code over
   * the plane's geometry matches it as one case.
   */
  sealed interface Spatial extends Geometry permits Mesh, Path3, Gradient {}

  /**
   * A sampled surface: a grid of {@code (nu + 1) × (nv + 1)} vertices in world coordinates, row by
   * row ({@code v} outer, {@code u} inner), NaN where the surface is undefined. A final class
   * rather than a record because it holds arrays: equality is by value, and the 3D view reads the
   * arrays through {@link #xs()}, {@link #ys()} and {@link #zs()} without a copy (read-only by
   * contract).
   *
   * <p>A surface {@code z = f(x, y)} keeps its height {@code f}, so its gradient can be evaluated
   * away from its samples ({@link #height()}); a parametric surface has none.
   *
   * <p>Its bounds are those of its finite vertices, except that a height range more than {@link
   * #SPIKE} times the range between the {@link #PERCENTILE} and the opposite percentile of the
   * heights (a pole, {@code 1/(x² + y²)}) is cut to that central range widened by a tenth on each
   * side, so one spike does not flatten the rest; the 3D view leaves undrawn a cell with a vertex
   * outside the box.
   */
  final class Mesh implements Spatial {

    /** How much wider than the central range the full height range may be before it is cut. */
    public static final double SPIKE = 10;

    /** The fraction of the heights left out at each end of the central range. */
    public static final double PERCENTILE = 0.01;

    private final int nu;
    private final int nv;
    private final float[] xs;
    private final float[] ys;
    private final float[] zs;
    private final double[] bounds;
    private final Optional<Expr> height;

    /**
     * A mesh with no height expression, over the given vertices kept without a copy.
     *
     * @param nu the cells along {@code u}, at least 1
     * @param nv the cells along {@code v}, at least 1
     * @param xs the abscissae
     * @param ys the ordinates
     * @param zs the heights
     * @throws IllegalArgumentException if a size is below 1 or an array is not {@code (nu + 1) ·
     *     (nv + 1)} long
     */
    public Mesh(int nu, int nv, float[] xs, float[] ys, float[] zs) {
      this(nu, nv, xs, ys, zs, Optional.empty());
    }

    /**
     * A mesh over the given vertices, kept without a copy: the caller hands them over.
     *
     * @param nu the cells along {@code u}, at least 1
     * @param nv the cells along {@code v}, at least 1
     * @param xs the abscissae
     * @param ys the ordinates
     * @param zs the heights
     * @param height the height {@code f} in {@code x} and {@code y} of a surface {@code z = f(x,
     *     y)}, or empty
     * @throws IllegalArgumentException if a size is below 1 or an array is not {@code (nu + 1) ·
     *     (nv + 1)} long
     */
    public Mesh(int nu, int nv, float[] xs, float[] ys, float[] zs, Optional<Expr> height) {
      if (nu < 1 || nv < 1) {
        throw new IllegalArgumentException("a mesh has a cell");
      }
      int size = (nu + 1) * (nv + 1);
      if (xs.length != size || ys.length != size || zs.length != size) {
        throw new IllegalArgumentException("a mesh has (nu + 1)(nv + 1) vertices");
      }
      this.nu = nu;
      this.nv = nv;
      this.xs = xs;
      this.ys = ys;
      this.zs = zs;
      this.bounds = bounds(xs, ys, zs);
      this.height = Objects.requireNonNull(height, "height");
    }

    /**
     * The height of a surface {@code z = f(x, y)}, in {@code x} and {@code y}.
     *
     * @return {@code f}, or empty for a parametric surface
     */
    public Optional<Expr> height() {
      return height;
    }

    private static double[] bounds(float[] xs, float[] ys, float[] zs) {
      double[] b = {
        Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY,
        Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY,
        Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY
      };
      float[] heights = new float[zs.length];
      int finite = 0;
      for (int k = 0; k < xs.length; k++) {
        if (Float.isFinite(xs[k]) && Float.isFinite(ys[k]) && Float.isFinite(zs[k])) {
          b[0] = Math.min(b[0], xs[k]);
          b[1] = Math.max(b[1], xs[k]);
          b[2] = Math.min(b[2], ys[k]);
          b[3] = Math.max(b[3], ys[k]);
          heights[finite++] = zs[k];
        }
      }
      if (finite == 0) {
        return null;
      }
      double[] cut = cut(heights, finite);
      b[4] = cut[0];
      b[5] = cut[1];
      return b;
    }

    /**
     * The range of some values, cut at a spike: their least and greatest, unless that range is more
     * than {@link #SPIKE} times their central one, which is then widened by a tenth on each side
     * and kept.
     *
     * @param values the values, sorted here in their first {@code n}
     * @param n how many there are, at least 1
     * @return {@code {low, high}}
     */
    static double[] cut(float[] values, int n) {
      Arrays.sort(values, 0, n);
      double low = values[0];
      double high = values[n - 1];
      int cut = (int) Math.floor(PERCENTILE * (n - 1));
      double centralLow = values[cut];
      double centralHigh = values[n - 1 - cut];
      double central = centralHigh - centralLow;
      if (central > 0 && high - low > SPIKE * central) {
        low = Math.max(low, centralLow - central / 10);
        high = Math.min(high, centralHigh + central / 10);
      }
      return new double[] {low, high};
    }

    /**
     * The cells along {@code u}.
     *
     * @return at least 1
     */
    public int nu() {
      return nu;
    }

    /**
     * The cells along {@code v}.
     *
     * @return at least 1
     */
    public int nv() {
      return nv;
    }

    /**
     * The abscissae, without a copy: read-only.
     *
     * @return the array
     */
    public float[] xs() {
      return xs;
    }

    /**
     * The ordinates, without a copy: read-only.
     *
     * @return the array
     */
    public float[] ys() {
      return ys;
    }

    /**
     * The heights, without a copy: read-only.
     *
     * @return the array
     */
    public float[] zs() {
      return zs;
    }

    /**
     * The bounds of the finite vertices, the heights cut at a spike.
     *
     * @return {@code {xMin, xMax, yMin, yMax, zMin, zMax}}, or empty when no vertex is finite
     */
    public Optional<double[]> bounds() {
      return Optional.ofNullable(bounds == null ? null : bounds.clone());
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Mesh m
          && m.nu == nu
          && m.nv == nv
          && Arrays.equals(m.xs, xs)
          && Arrays.equals(m.ys, ys)
          && Arrays.equals(m.zs, zs)
          && m.height.equals(height);
    }

    @Override
    public int hashCode() {
      return Objects.hash(nu, nv, Arrays.hashCode(zs));
    }

    @Override
    public String toString() {
      return "Mesh[" + nu + "x" + nv + "]";
    }
  }

  /**
   * A sampled curve in space: its points in order, NaN where it is undefined (a gap). A final class
   * rather than a record because it holds arrays: equality is by value, and the 3D view reads the
   * arrays without a copy (read-only by contract).
   *
   * <p>Its bounds are those of its finite points, each axis cut at a spike as a mesh's heights are
   * ({@link Mesh}), so a pole ({@code [t, tan t, 0]}) does not widen the box; the 3D view breaks
   * the curve at a point outside the box.
   */
  final class Path3 implements Spatial {

    private final float[] xs;
    private final float[] ys;
    private final float[] zs;
    private final double[] bounds;

    /**
     * A curve over the given points, kept without a copy: the caller hands them over.
     *
     * @param xs the abscissae
     * @param ys the ordinates
     * @param zs the heights
     * @throws IllegalArgumentException if the arrays differ in length
     */
    public Path3(float[] xs, float[] ys, float[] zs) {
      if (xs.length != ys.length || ys.length != zs.length) {
        throw new IllegalArgumentException("a curve's arrays have one length");
      }
      this.xs = xs;
      this.ys = ys;
      this.zs = zs;
      float[][] finite = {new float[xs.length], new float[xs.length], new float[xs.length]};
      int n = 0;
      for (int k = 0; k < xs.length; k++) {
        if (Float.isFinite(xs[k]) && Float.isFinite(ys[k]) && Float.isFinite(zs[k])) {
          finite[0][n] = xs[k];
          finite[1][n] = ys[k];
          finite[2][n] = zs[k];
          n++;
        }
      }
      if (n == 0) {
        this.bounds = null;
        return;
      }
      double[] b = new double[6];
      for (int axis = 0; axis < 3; axis++) {
        double[] range = Mesh.cut(finite[axis], n);
        b[2 * axis] = range[0];
        b[2 * axis + 1] = range[1];
      }
      this.bounds = b;
    }

    /**
     * The abscissae, without a copy: read-only.
     *
     * @return the array
     */
    public float[] xs() {
      return xs;
    }

    /**
     * The ordinates, without a copy: read-only.
     *
     * @return the array
     */
    public float[] ys() {
      return ys;
    }

    /**
     * The heights, without a copy: read-only.
     *
     * @return the array
     */
    public float[] zs() {
      return zs;
    }

    /**
     * The bounds of the finite points.
     *
     * @return {@code {xMin, xMax, yMin, yMax, zMin, zMax}}, or empty when no point is finite
     */
    public Optional<double[]> bounds() {
      return Optional.ofNullable(bounds == null ? null : bounds.clone());
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Path3 p
          && Arrays.equals(p.xs, xs)
          && Arrays.equals(p.ys, ys)
          && Arrays.equals(p.zs, zs);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(zs);
    }

    @Override
    public String toString() {
      return "Path3[" + xs.length + "]";
    }
  }

  /**
   * A gradient on a surface: the point {@code (x, y, z)} on it and the partial derivatives there.
   *
   * @param x the abscissa, finite
   * @param y the ordinate, finite
   * @param z the height, finite
   * @param fx the derivative along {@code x}, finite
   * @param fy the derivative along {@code y}, finite
   */
  record Gradient(double x, double y, double z, double fx, double fy) implements Spatial {

    /**
     * Checks the fields.
     *
     * @throws IllegalArgumentException if a field is not finite
     */
    public Gradient {
      if (!Double.isFinite(x)
          || !Double.isFinite(y)
          || !Double.isFinite(z)
          || !Double.isFinite(fx)
          || !Double.isFinite(fy)) {
        throw new IllegalArgumentException("a gradient is finite");
      }
    }
  }
}
