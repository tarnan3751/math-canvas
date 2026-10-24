package mathcanvas.engine.expr;

import java.util.List;
import java.util.stream.Collectors;

/** The debug {@code toString} forms of the nodes: {@code Kind[operand, operand]}. */
final class Debug {

  private Debug() {}

  /** {@code kind[part; part]}, each part a comma-separated list. */
  static String of(String kind, Object... parts) {
    StringBuilder text = new StringBuilder(kind).append('[');
    for (int k = 0; k < parts.length; k++) {
      if (k > 0) {
        text.append("; ");
      }
      text.append(parts[k] instanceof List<?> list ? join(list) : String.valueOf(parts[k]));
    }
    return text.append(']').toString();
  }

  private static String join(List<?> items) {
    return items.stream().map(String::valueOf).collect(Collectors.joining(", "));
  }
}
