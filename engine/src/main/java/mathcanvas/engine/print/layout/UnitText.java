package mathcanvas.engine.print.layout;

import java.util.Objects;
import mathcanvas.engine.rule.Path;

/**
 * A unit of measurement (§14.26), set upright and without its underscore: {@code km}, {@code µm},
 * {@code Ω}, {@code °C}. Its linear form, which {@link
 * mathcanvas.engine.print.LayoutPrinter#flatten} writes, is the Unicode printer's: {@code _km}.
 *
 * @param shown what is set
 * @param linear the printed text
 * @param path the path, or null
 */
public record UnitText(String shown, String linear, Path path) implements Layout {

  /** Checks the fields. */
  public UnitText {
    Objects.requireNonNull(shown);
    Objects.requireNonNull(linear);
  }
}
