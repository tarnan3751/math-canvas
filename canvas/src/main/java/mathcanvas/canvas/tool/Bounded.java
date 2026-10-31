package mathcanvas.canvas.tool;

import java.util.Optional;
import java.util.function.Consumer;
import mathcanvas.engine.expr.Expr;

/**
 * A tool that takes numbers as well as clicks: the bounds of an area or a sum, the centre of a
 * Taylor polynomial. Each is clicked (its abscissa) or typed in the app's entry, which parses and
 * evaluates the text on the worker and hands the tool the expression, which may read sliders, with
 * its value.
 */
public interface Bounded extends Tool {

  /**
   * The name of the number the tool waits for next ({@code a}, {@code b}), shown as the entry's
   * prompt; empty when it waits for a click on a graph or for nothing.
   *
   * @return the name
   */
  Optional<String> wanted();

  /**
   * A typed number for {@link #wanted()}.
   *
   * @param e the expression as typed, canonical, which the object keeps
   * @param value its value now, for the preview; NaN when it has none
   * @param edits where the edits go
   */
  void typed(Expr e, double value, Consumer<Edit> edits);
}
