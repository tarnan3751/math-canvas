package mathcanvas.app.ui;

import java.util.Objects;

/**
 * A message for the status line.
 *
 * @param text the message, from the bundle
 * @param detail the technical reason, shown in the tooltip, or empty
 */
public record Notice(String text, String detail) {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if an operand is null
   */
  public Notice {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(detail, "detail");
  }
}
