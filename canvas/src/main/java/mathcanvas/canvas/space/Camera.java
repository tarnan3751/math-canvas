package mathcanvas.canvas.space;

/**
 * The orbit camera of the 3D view, in the coordinates of the axes box's cube {@code [−1, 1]³}: it
 * looks at the target from {@code distance} away, at the azimuth {@code yaw} about the vertical
 * axis (from the {@code x} axis toward {@code y}) and the elevation {@code pitch} above the
 * horizontal plane, with {@code z} up on the screen. A drag orbits ({@link #orbit}), the wheel
 * zooms ({@link #zoom}) and a Shift-drag pans the target ({@link #pan}); each keeps the camera
 * within its limits, so the eye never reaches the vertical (where {@code z} up has no meaning) and
 * never enters the cube, whose corners are {@code √3} from its centre: the distance is at least
 * {@link #MIN_DISTANCE} beyond the target's own distance from the centre, so the eye is always at
 * least {@link #MIN_DISTANCE} from the centre.
 *
 * @param yaw the azimuth in radians, in {@code (−π, π]}
 * @param pitch the elevation in radians, within {@link #MAX_PITCH} of the horizontal
 * @param distance from the target, from {@link #MIN_DISTANCE} plus the target's distance from the
 *     centre to {@link #MAX_DISTANCE}
 * @param targetX the target's {@code x}, within {@link #MAX_TARGET} of the cube's centre
 * @param targetY the target's {@code y}, likewise
 * @param targetZ the target's {@code z}, likewise
 */
public record Camera(
    double yaw, double pitch, double distance, double targetX, double targetY, double targetZ) {

  /** The steepest elevation, 89°, above or below. */
  public static final double MAX_PITCH = Math.toRadians(89);

  /** The nearest the eye comes to the target. */
  public static final double MIN_DISTANCE = 2.5;

  /** The farthest the eye goes from the target. */
  public static final double MAX_DISTANCE = 40;

  /** How far the target may move from the cube's centre along each axis. */
  public static final double MAX_TARGET = 1;

  /** The camera of a new document: from the front right, a little above, the whole cube in view. */
  public static final Camera DEFAULT =
      new Camera(Math.toRadians(-60), Math.toRadians(25), 8, 0, 0, 0);

  /**
   * Checks the fields.
   *
   * @throws IllegalArgumentException if a field is not finite or is outside its limits
   */
  public Camera {
    if (!(yaw > -Math.PI && yaw <= Math.PI)) {
      throw new IllegalArgumentException("a camera's yaw is in (−π, π]");
    }
    if (!(Math.abs(pitch) <= MAX_PITCH)) {
      throw new IllegalArgumentException("a camera's pitch is within 89° of the horizontal");
    }
    if (!(distance >= MIN_DISTANCE && distance <= MAX_DISTANCE)) {
      throw new IllegalArgumentException("a camera's distance is from 2.5 to 40");
    }
    if (!(distance >= nearest(targetX, targetY, targetZ))) {
      throw new IllegalArgumentException(
          "a camera's distance is at least 2.5 beyond its target's from the centre");
    }
    if (!(Math.abs(targetX) <= MAX_TARGET
        && Math.abs(targetY) <= MAX_TARGET
        && Math.abs(targetZ) <= MAX_TARGET)) {
      throw new IllegalArgumentException("a camera's target is within 1 of the centre");
    }
  }

  /**
   * The camera turned about its target.
   *
   * @param dYaw the change of azimuth in radians
   * @param dPitch the change of elevation in radians
   * @return the camera, its yaw wrapped into {@code (−π, π]} and its pitch held within {@link
   *     #MAX_PITCH}
   */
  public Camera orbit(double dYaw, double dPitch) {
    if (!Double.isFinite(dYaw) || !Double.isFinite(dPitch)) {
      return this;
    }
    double y = Math.IEEEremainder(yaw + dYaw, 2 * Math.PI);
    if (y <= -Math.PI) {
      y += 2 * Math.PI;
    }
    double p = Math.clamp(pitch + dPitch, -MAX_PITCH, MAX_PITCH);
    return new Camera(y, p, distance, targetX, targetY, targetZ);
  }

  /**
   * The camera moved toward its target or away from it.
   *
   * @param factor above 1 moves in: the distance is divided by it
   * @return the camera, its distance held from {@link #MIN_DISTANCE} beyond the target's distance
   *     from the centre to {@link #MAX_DISTANCE}
   */
  public Camera zoom(double factor) {
    if (!(factor > 0) || Double.isInfinite(factor)) {
      return this;
    }
    double d = Math.clamp(distance / factor, nearest(targetX, targetY, targetZ), MAX_DISTANCE);
    return new Camera(yaw, pitch, d, targetX, targetY, targetZ);
  }

  /**
   * The camera and its target moved across the line of sight, as a drag moves the picture.
   *
   * @param right the move to the right on the screen, in cube units
   * @param up the move up the screen, in cube units
   * @return the camera, its target held within {@link #MAX_TARGET} of the centre and its distance
   *     moved out where the target's move away from the centre needs it
   */
  public Camera pan(double right, double up) {
    if (!Double.isFinite(right) || !Double.isFinite(up)) {
      return this;
    }
    double[] r = right();
    double[] u = up();
    // the picture follows the pointer, so the target moves the other way
    double x = Math.clamp(targetX - right * r[0] - up * u[0], -MAX_TARGET, MAX_TARGET);
    double y = Math.clamp(targetY - right * r[1] - up * u[1], -MAX_TARGET, MAX_TARGET);
    double z = Math.clamp(targetZ - right * r[2] - up * u[2], -MAX_TARGET, MAX_TARGET);
    // the eye moves out with a target moving away from the centre, so it stays out of the cube
    return new Camera(yaw, pitch, Math.max(distance, nearest(x, y, z)), x, y, z);
  }

  /**
   * The least distance from a target: {@link #MIN_DISTANCE} beyond its distance from the centre.
   */
  private static double nearest(double x, double y, double z) {
    return MIN_DISTANCE + Math.sqrt(x * x + y * y + z * z);
  }

  /**
   * The eye's position.
   *
   * @return {@code {x, y, z}} in cube units
   */
  public double[] eye() {
    double c = Math.cos(pitch);
    return new double[] {
      targetX + distance * c * Math.cos(yaw),
      targetY + distance * c * Math.sin(yaw),
      targetZ + distance * Math.sin(pitch)
    };
  }

  /**
   * The unit direction the eye looks in, from the eye to the target.
   *
   * @return {@code {x, y, z}}
   */
  public double[] forward() {
    double c = Math.cos(pitch);
    return new double[] {-c * Math.cos(yaw), -c * Math.sin(yaw), -Math.sin(pitch)};
  }

  /**
   * The unit direction to the right on the screen, horizontal.
   *
   * @return {@code {x, y, z}}
   */
  public double[] right() {
    return new double[] {-Math.sin(yaw), Math.cos(yaw), 0};
  }

  /**
   * The unit direction up the screen, {@code right × forward}.
   *
   * @return {@code {x, y, z}}
   */
  public double[] up() {
    double s = Math.sin(pitch);
    return new double[] {-s * Math.cos(yaw), -s * Math.sin(yaw), Math.cos(pitch)};
  }
}
