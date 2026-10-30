package mathcanvas.app.document;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A slider a cell defines with {@code name := slider(min, max, step)}: what the document makes of
 * it is a canvas {@code Slider} with this variable and range, created or updated when the cell's
 * output arrives.
 *
 * @param variable the name the cell binds
 * @param min the least value, exact
 * @param max the greatest value, above the least
 * @param step the step, positive
 */
public record SliderSpec(String variable, BigDecimal min, BigDecimal max, BigDecimal step) {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   */
  public SliderSpec {
    Objects.requireNonNull(variable, "variable");
    Objects.requireNonNull(min, "min");
    Objects.requireNonNull(max, "max");
    Objects.requireNonNull(step, "step");
  }
}
