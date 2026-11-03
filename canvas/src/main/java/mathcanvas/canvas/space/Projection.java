package mathcanvas.canvas.space;

/**
 * The perspective projection of the cube's coordinates onto a view of a pixel size through a
 * camera: a field of view of {@link #FIELD_OF_VIEW} across the view's shorter side, so the cube
 * fits a narrow view as it fits a wide one, the target at the view's centre, the screen's {@code y}
 * down. A point's depth is its distance along the line of sight; a point nearer than {@link #NEAR}
 * is behind the eye for drawing, and a primitive with such a vertex is not drawn. Immutable.
 */
public final class Projection {

  /** The field of view across the view's shorter side, 30°. */
  public static final double FIELD_OF_VIEW = Math.toRadians(30);

  /** The least depth drawn. */
  public static final double NEAR = 0.1;

  private final Camera camera;
  private final double width;
  private final double height;
  private final double focal;
  private final double ex;
  private final double ey;
  private final double ez;
  private final double fx;
  private final double fy;
  private final double fz;
  private final double rx;
  private final double ry;
  private final double ux;
  private final double uy;
  private final double uz;

  /**
   * A projection.
   *
   * @param camera the camera
   * @param width the view's width in pixels, positive
   * @param height the view's height in pixels, positive
   * @throws IllegalArgumentException if a size is not positive and finite
   */
  public Projection(Camera camera, double width, double height) {
    if (!(width > 0 && height > 0) || Double.isInfinite(width) || Double.isInfinite(height)) {
      throw new IllegalArgumentException("a view's size is positive and finite");
    }
    this.camera = camera;
    this.width = width;
    this.height = height;
    this.focal = Math.min(width, height) / 2 / Math.tan(FIELD_OF_VIEW / 2);
    double[] e = camera.eye();
    double[] f = camera.forward();
    double[] r = camera.right();
    double[] u = camera.up();
    ex = e[0];
    ey = e[1];
    ez = e[2];
    fx = f[0];
    fy = f[1];
    fz = f[2];
    rx = r[0];
    ry = r[1];
    ux = u[0];
    uy = u[1];
    uz = u[2];
  }

  /**
   * The camera.
   *
   * @return it
   */
  public Camera camera() {
    return camera;
  }

  /**
   * The view's width.
   *
   * @return in pixels
   */
  public double width() {
    return width;
  }

  /**
   * The view's height.
   *
   * @return in pixels
   */
  public double height() {
    return height;
  }

  /**
   * The pixels one cube unit spans at a depth.
   *
   * @param depth the depth, positive
   * @return the scale
   */
  public double scale(double depth) {
    return focal / depth;
  }

  /**
   * Projects a point of the cube.
   *
   * @param x the point's {@code x}
   * @param y its {@code y}
   * @param z its {@code z}
   * @param out receives the screen's {@code x} and {@code y} in pixels and the depth, at 0, 1, 2
   */
  public void project(double x, double y, double z, double[] out) {
    double dx = x - ex;
    double dy = y - ey;
    double dz = z - ez;
    double depth = dx * fx + dy * fy + dz * fz;
    out[0] = width / 2 + focal * (dx * rx + dy * ry) / depth;
    out[1] = height / 2 - focal * (dx * ux + dy * uy + dz * uz) / depth;
    out[2] = depth;
  }

  /**
   * The point of the cube a screen point shows at a depth: the inverse of {@link #project}.
   *
   * @param px the screen's {@code x} in pixels
   * @param py the screen's {@code y} in pixels
   * @param depth the depth
   * @return {@code {x, y, z}}
   */
  public double[] unproject(double px, double py, double depth) {
    double a = (px - width / 2) * depth / focal;
    double b = (height / 2 - py) * depth / focal;
    return new double[] {
      ex + depth * fx + a * rx + b * ux, ey + depth * fy + a * ry + b * uy, ez + depth * fz + b * uz
    };
  }

  /**
   * The direction from a point of the cube toward the eye, not normalised.
   *
   * @param x the point's {@code x}
   * @param y its {@code y}
   * @param z its {@code z}
   * @param out receives the direction
   */
  public void toEye(double x, double y, double z, double[] out) {
    out[0] = ex - x;
    out[1] = ey - y;
    out[2] = ez - z;
  }

  /**
   * The direction toward the scene's light, fixed to the camera: from the upper left, behind the
   * viewer.
   *
   * @return a unit vector {@code {x, y, z}}
   */
  public double[] light() {
    double lx = -0.4 * rx + 0.6 * ux - 0.7 * fx;
    double ly = -0.4 * ry + 0.6 * uy - 0.7 * fy;
    double lz = 0.6 * uz - 0.7 * fz;
    double n = Math.sqrt(lx * lx + ly * ly + lz * lz);
    return new double[] {lx / n, ly / n, lz / n};
  }
}
