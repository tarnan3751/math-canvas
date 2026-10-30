package mathcanvas.canvas.model;

import java.util.Objects;

/** What a curve's geometry says about evaluating it away from its samples. */
public sealed interface Path {

  /**
   * A path with a parameter: any point of it can be evaluated, inside its domain.
   *
   * @param parametrisation the map from the parameter to the plane
   * @param lo the least parameter, possibly −∞
   * @param hi the greatest parameter, possibly +∞, not below the least
   * @param kind what the parameter means, never {@link ParamKind#NONE}
   */
  record Parametrised(Parametrisation parametrisation, double lo, double hi, ParamKind kind)
      implements Path {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     * @throws IllegalArgumentException if the domain is empty or NaN, or the kind is {@code NONE}
     */
    public Parametrised {
      Objects.requireNonNull(parametrisation, "parametrisation");
      Objects.requireNonNull(kind, "kind");
      if (!(lo <= hi)) {
        throw new IllegalArgumentException("a domain is not empty");
      }
      if (kind == ParamKind.NONE) {
        throw new IllegalArgumentException("a parametrised path has a parameter");
      }
    }

    /**
     * Whether a parameter is in the domain.
     *
     * @param t the parameter
     * @return true inside the closed domain
     */
    public boolean contains(double t) {
      return lo <= t && t <= hi;
    }
  }

  /** A path known only by its polylines: an implicit curve. */
  record PolylineOnly() implements Path {}

  /** The one path with no parameter. */
  PolylineOnly POLYLINE_ONLY = new PolylineOnly();
}
