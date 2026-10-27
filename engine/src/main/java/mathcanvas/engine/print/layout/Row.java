package mathcanvas.engine.print.layout;

import java.util.List;
import mathcanvas.engine.rule.Path;

/**
 * Parts side by side. A row groups for selection only: its items are spaced as if they stood in the
 * enclosing row.
 *
 * @param items the parts
 * @param path the path, or null
 */
public record Row(List<Layout> items, Path path) implements Layout {

  /** Copies the items. */
  public Row {
    items = List.copyOf(items);
  }
}
