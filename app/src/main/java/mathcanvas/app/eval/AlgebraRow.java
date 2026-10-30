package mathcanvas.app.eval;

import java.util.Objects;
import java.util.Optional;
import mathcanvas.render.typeset.Box;

/**
 * One row of the algebra panel: an object's algebraic form, printed and typeset on the worker so
 * the FX thread only shows it.
 *
 * @param text the form printed as text, which the row's editor starts from
 * @param box the form typeset, or null when it is too large to typeset (the row shows the text)
 * @param error why the object is undefined, when that is something to say (a cycle through the
 *     worksheet), shown under the row
 */
public record AlgebraRow(String text, Box box, Optional<String> error) {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if the text or the error is null
   */
  public AlgebraRow {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(error, "error");
  }
}
