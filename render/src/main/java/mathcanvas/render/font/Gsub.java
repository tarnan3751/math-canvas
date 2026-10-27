package mathcanvas.render.font;

import java.util.HashMap;
import java.util.Map;

/**
 * The GSUB table, read by feature: the alternate substitutions (lookup type 3) a feature names,
 * through extension lookups (type 7). The math fonts' script-style feature {@code ssty} is one; the
 * single substitutions of {@code flac} and {@code dtls} are read the same way when accents land.
 */
final class Gsub {

  private static final int ALTERNATE = 3;
  private static final int EXTENSION = 7;

  private Gsub() {}

  /** The alternates of every glyph that a feature's alternate substitutions cover. */
  static Map<Integer, int[]> alternates(Bytes b, int at, String feature) {
    Map<Integer, int[]> out = new HashMap<>();
    int features = at + b.u16(at + 6);
    int lookups = at + b.u16(at + 8);
    for (int f = 0; f < b.u16(features); f++) {
      int record = features + 2 + 6 * f;
      if (!b.tag(record).equals(feature)) {
        continue;
      }
      int table = features + b.u16(record + 4);
      for (int k = 0; k < b.u16(table + 2); k++) {
        int lookup = lookups + b.u16(lookups + 2 + 2 * b.u16(table + 4 + 2 * k));
        int type = b.u16(lookup);
        for (int s = 0; s < b.u16(lookup + 4); s++) {
          int sub = lookup + b.u16(lookup + 6 + 2 * s);
          int subType = type;
          if (type == EXTENSION) {
            subType = b.u16(sub + 2);
            sub = sub + b.offset32(sub + 4);
          }
          if (subType == ALTERNATE) {
            alternateSubst(b, sub, out);
          }
        }
      }
    }
    return out;
  }

  private static void alternateSubst(Bytes b, int at, Map<Integer, int[]> out) {
    Map<Integer, Integer> coverage = Coverage.read(b, at + b.u16(at + 2));
    for (Map.Entry<Integer, Integer> e : coverage.entrySet()) {
      int set = at + b.u16(at + 6 + 2 * e.getValue());
      int[] glyphs = new int[b.u16(set)];
      for (int k = 0; k < glyphs.length; k++) {
        glyphs[k] = b.u16(set + 2 + 2 * k);
      }
      out.putIfAbsent(e.getKey(), glyphs);
    }
  }
}
