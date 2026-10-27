package mathcanvas.render.font;

import java.util.HashMap;
import java.util.Map;

/** An OpenType coverage table read into a map from glyph to coverage index. */
final class Coverage {

  private Coverage() {}

  static Map<Integer, Integer> read(Bytes b, int at) {
    Map<Integer, Integer> index = new HashMap<>();
    int format = b.u16(at);
    int count = b.u16(at + 2);
    if (format == 1) {
      for (int k = 0; k < count; k++) {
        index.put(b.u16(at + 4 + 2 * k), k);
      }
    } else if (format == 2) {
      for (int k = 0; k < count; k++) {
        int record = at + 4 + 6 * k;
        int start = b.u16(record);
        int end = b.u16(record + 2);
        int first = b.u16(record + 4);
        for (int g = start; g <= end; g++) {
          index.put(g, first + g - start);
        }
      }
    } else {
      throw new IllegalArgumentException("coverage format " + format);
    }
    return index;
  }
}
