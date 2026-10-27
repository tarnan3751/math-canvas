package mathcanvas.engine.print.layout;

import java.util.Objects;
import mathcanvas.engine.rule.Path;

/**
 * A body between fences that stretch to it: parentheses, brackets, braces, bars, angle brackets.
 *
 * @param open the opening fence's code point
 * @param body the body
 * @param close the closing fence's code point
 * @param path the path, or null
 */
public record Delimited(int open, Layout body, int close, Path path) implements Layout {

  /** Checks the body. */
  public Delimited {
    Objects.requireNonNull(body);
  }
}
