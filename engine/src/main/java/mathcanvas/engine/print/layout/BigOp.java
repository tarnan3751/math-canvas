package mathcanvas.engine.print.layout;

import java.util.Objects;
import mathcanvas.engine.rule.Path;

/**
 * A big operator applied to the rest of its term: {@code ∫}, {@code ∑}, {@code ∏}, {@code lim} and
 * the derivative {@code d/dx}. Its linear form is the operator, the bounds in brackets ({@code
 * [lower, upper]}, either alone), a space and the body.
 *
 * @param operator the operator
 * @param lower the lower bound, or null
 * @param upper the upper bound, or null
 * @param body the body
 * @param limits whether the bounds go below and above in display style, rather than as scripts
 * @param path the path, or null
 */
public record BigOp(
    Layout operator, Layout lower, Layout upper, Layout body, boolean limits, Path path)
    implements Layout {

  /** Checks the operator and the body. */
  public BigOp {
    Objects.requireNonNull(operator);
    Objects.requireNonNull(body);
  }
}
