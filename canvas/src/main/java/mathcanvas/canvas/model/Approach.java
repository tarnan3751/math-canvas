package mathcanvas.canvas.model;

/**
 * Where an intersection last was a simple root (not a tangency): the side it approached a tangency
 * or a vanishing point from. Part of its {@link Anchor}, so saved and undone with it; three
 * numbers.
 *
 * @param t the parameter on the first path there, or NaN on a path with no parameter
 * @param x the abscissa there
 * @param y the ordinate there
 */
public record Approach(double t, double x, double y) {

  /**
   * Checks the fields.
   *
   * @throws IllegalArgumentException if the position is not finite or the parameter is infinite
   */
  public Approach {
    if (!Double.isFinite(x) || !Double.isFinite(y)) {
      throw new IllegalArgumentException("an approach has a finite position");
    }
    if (Double.isInfinite(t)) {
      throw new IllegalArgumentException("an approach's parameter is finite or NaN");
    }
  }
}
