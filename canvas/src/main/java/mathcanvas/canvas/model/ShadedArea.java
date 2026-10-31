package mathcanvas.canvas.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import mathcanvas.engine.expr.Expr;

/**
 * The area under a function graph, or between two, over {@code [from, to]}, shaded with its value:
 * the integral of the upper graph less the lower one (the x-axis when there is none), signed, by
 * Gauss–Kronrod over the compiled graphs. The bounds are expressions, which may read sliders; the
 * area is undefined unless they are numbers with {@code from < to} inside both graphs' domains, and
 * the integral converges.
 *
 * @param id the id
 * @param style the style
 * @param upper the function graph integrated
 * @param lower the function graph subtracted, or empty for the x-axis
 * @param from the lower bound
 * @param to the upper bound
 */
public record ShadedArea(
    ObjectId id, Style style, ObjectId upper, Optional<ObjectId> lower, Expr from, Expr to)
    implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if the two graphs are one
   */
  public ShadedArea {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(upper, "upper");
    Objects.requireNonNull(lower, "lower");
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    if (lower.isPresent() && lower.get().equals(upper)) {
      throw new IllegalArgumentException("an area between two graphs needs two graphs");
    }
  }

  @Override
  public List<ObjectId> parents() {
    List<ObjectId> out = new ArrayList<>(2);
    out.add(upper);
    lower.ifPresent(out::add);
    return List.copyOf(out);
  }

  @Override
  public Set<String> reads() {
    return Reads.of(Set.of(), from, to);
  }

  @Override
  public ShadedArea withStyle(Style style) {
    return new ShadedArea(id, style, upper, lower, from, to);
  }
}
