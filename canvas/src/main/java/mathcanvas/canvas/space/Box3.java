package mathcanvas.canvas.space;

import java.util.List;

/**
 * The axes box: the world rectangle of the 3D view, mapped onto the cube {@code [−1, 1]³} one axis
 * at a time, so each axis fills the cube whatever its units (a tall surface is not a needle). It
 * fits the objects' bounds; an axis along which they have no extent is widened by 1 on each side,
 * and with no bounds at all it is {@link #DEFAULT}.
 *
 * @param xMin the least {@code x}
 * @param xMax the greatest {@code x}, above the least
 * @param yMin the least {@code y}
 * @param yMax the greatest {@code y}, above the least
 * @param zMin the least {@code z}
 * @param zMax the greatest {@code z}, above the least
 */
public record Box3(double xMin, double xMax, double yMin, double yMax, double zMin, double zMax) {

  /** The box with nothing in it, {@code [−5, 5]³}. */
  public static final Box3 DEFAULT = new Box3(-5, 5, -5, 5, -5, 5);

  /**
   * Checks the fields.
   *
   * @throws IllegalArgumentException if a bound is not finite or a side is empty
   */
  public Box3 {
    double[] b = {xMin, xMax, yMin, yMax, zMin, zMax};
    for (double v : b) {
      if (!Double.isFinite(v)) {
        throw new IllegalArgumentException("a box has finite bounds");
      }
    }
    if (!(xMin < xMax && yMin < yMax && zMin < zMax) || !Double.isFinite(xMax - xMin)) {
      throw new IllegalArgumentException("a box is not empty");
    }
  }

  /**
   * The box fitting some bounds.
   *
   * @param bounds each {@code {xMin, xMax, yMin, yMax, zMin, zMax}}, finite
   * @return their union, an empty axis widened; {@link #DEFAULT} when there are none
   */
  public static Box3 fit(List<double[]> bounds) {
    if (bounds.isEmpty()) {
      return DEFAULT;
    }
    double[] u = bounds.getFirst().clone();
    for (double[] b : bounds) {
      for (int k = 0; k < 6; k += 2) {
        u[k] = Math.min(u[k], b[k]);
        u[k + 1] = Math.max(u[k + 1], b[k + 1]);
      }
    }
    for (int k = 0; k < 6; k += 2) {
      // an extent below a millionth of the magnitude cannot be drawn apart from rounding
      if (!(u[k + 1] - u[k] > 1e-6 * Math.max(1, Math.max(Math.abs(u[k]), Math.abs(u[k + 1]))))) {
        double mid = (u[k] + u[k + 1]) / 2;
        u[k] = mid - 1;
        u[k + 1] = mid + 1;
      }
    }
    return new Box3(u[0], u[1], u[2], u[3], u[4], u[5]);
  }

  /**
   * The cube's coordinate of a world value along an axis.
   *
   * @param axis 0 for {@code x}, 1 for {@code y}, 2 for {@code z}
   * @param v the world value
   * @return its place in {@code [−1, 1]} when it is inside the box
   */
  public double toCube(int axis, double v) {
    double lo = min(axis);
    return 2 * (v - lo) / (max(axis) - lo) - 1;
  }

  /**
   * The world value at a cube coordinate along an axis: the inverse of {@link #toCube}.
   *
   * @param axis 0, 1 or 2
   * @param c the cube's coordinate
   * @return the world value
   */
  public double toWorld(int axis, double c) {
    double lo = min(axis);
    return lo + (c + 1) / 2 * (max(axis) - lo);
  }

  /**
   * The least value along an axis.
   *
   * @param axis 0, 1 or 2
   * @return it
   */
  public double min(int axis) {
    return switch (axis) {
      case 0 -> xMin;
      case 1 -> yMin;
      default -> zMin;
    };
  }

  /**
   * The greatest value along an axis.
   *
   * @param axis 0, 1 or 2
   * @return it
   */
  public double max(int axis) {
    return switch (axis) {
      case 0 -> xMax;
      case 1 -> yMax;
      default -> zMax;
    };
  }
}
