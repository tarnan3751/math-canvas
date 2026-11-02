package mathcanvas.canvas.model;

/**
 * What a path's parameter means, so a point on a path knows whether its parameter still applies
 * after the path is redefined.
 */
public enum ParamKind {
  /** The abscissa of a function graph. */
  X,
  /** The parameter of a parametric curve. */
  T,
  /** The angle of a polar curve. */
  THETA,
  /**
   * The affine parameter of a line, segment or ray: its first point at 0, its second (or, for a
   * line through a point in a direction, the point moved by the direction) at 1.
   */
  LINE,
  /** The angle of a circle, from 0 to 2π, measured from its own starting point. */
  ANGLE,
  /** The perimeter of a polygon: side k from vertex k at k to vertex k + 1 at k + 1, closed. */
  PERIMETER,
  /** No parameter: a path known only by its polylines (an implicit curve). */
  NONE
}
