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
  /** The affine parameter of a line or segment: its first point at 0, its second at 1. */
  LINE,
  /** No parameter: a path known only by its polylines (an implicit curve). */
  NONE
}
