package mathcanvas.engine.print.layout;

import java.util.Objects;
import mathcanvas.engine.rule.Path;

/**
 * A radical: {@code √x}, or with an index, {@code ∛x} and {@code ∜x}.
 *
 * @param index the index ({@code 3} or {@code 4}), or null for a square root
 * @param radicand the radicand
 * @param wrapped whether the text parenthesises the radicand, {@code √(x + 1)}
 * @param path the path, or null
 */
public record Radical(Layout index, Layout radicand, boolean wrapped, Path path) implements Layout {

  /** Checks the radicand. */
  public Radical {
    Objects.requireNonNull(radicand);
  }
}
