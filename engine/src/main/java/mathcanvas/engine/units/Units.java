package mathcanvas.engine.units;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Unit;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;

/**
 * The resolution of a written unit name and what a unit means ({@code docs/language.md} §14.26).
 *
 * <p>A name resolves to a unit of the table when it is one of its spellings ({@code ft}, {@code
 * min}, {@code cd}, {@code Ω}); otherwise to an SI prefix and a unit that takes prefixes, the
 * longest prefix first ({@code da} before {@code d}): {@code km}, {@code µs}, {@code kWh}, {@code
 * hPa}. An exact name always wins, so {@code ft} is the foot, never a femtotonne. The micro prefix
 * is {@code u}, {@code µ} or {@code μ}, and the node's name normalises it and {@code Ω} to ASCII
 * ({@code um}, {@code kohm}).
 *
 * <p>The rank orders units by the table's order, the coherent unit of an entry first ({@code kg}
 * before {@code g}, {@code m} before {@code cm} and {@code km}), then by the prefix's exponent.
 */
public final class Units {

  /**
   * An SI prefix.
   *
   * @param ascii its ASCII spelling, which the node's name holds
   * @param unicode the spelling the Unicode printer and the layout write
   * @param exponent its power of ten
   */
  public record Prefix(String ascii, String unicode, int exponent) {}

  /**
   * What a unit is: its table entry and its prefix.
   *
   * @param entry the table entry
   * @param prefix the prefix, or null
   */
  public record Info(UnitTable.Entry entry, Prefix prefix) {

    /**
     * The unit's dimension.
     *
     * @return the dimension
     */
    public Dimension dimension() {
      return entry.dimension();
    }

    /**
     * The unit's exact factor to the coherent SI unit of its dimension, canonical.
     *
     * @return the factor
     */
    public Expr factor() {
      if (prefix == null) {
        return entry.factor();
      }
      return Canonical.of(Product.of(entry.factor(), Num.of(power(prefix.exponent()))));
    }

    /**
     * The offset of an affine temperature (°C, °F) in the unit's own scale, or null.
     *
     * @return the offset
     */
    public RealNumeric offset() {
      return entry.offset();
    }

    /**
     * The spelling the Unicode printer writes after the underscore: {@code µm}, {@code kΩ}.
     *
     * @return the spelling
     */
    public String unicode() {
      return (prefix == null ? "" : prefix.unicode()) + entry.unicode();
    }

    /**
     * The spelling the layout shows: {@code km}, {@code °C}, {@code °}.
     *
     * @return the spelling
     */
    public String shown() {
      return (prefix == null ? "" : prefix.unicode()) + entry.shown();
    }

    /**
     * Whether this is a unit of angle, radians or degrees.
     *
     * @return whether it is
     */
    public boolean isAngle() {
      return entry.name().equals("rad") || entry.name().equals("deg");
    }
  }

  /** The prefixes from 10⁻²⁴ to 10²⁴, longest spelling first where two share a start. */
  private static final List<Prefix> PREFIXES =
      List.of(
          new Prefix("da", "da", 1),
          new Prefix("y", "y", -24),
          new Prefix("z", "z", -21),
          new Prefix("a", "a", -18),
          new Prefix("f", "f", -15),
          new Prefix("p", "p", -12),
          new Prefix("n", "n", -9),
          new Prefix("u", "µ", -6),
          new Prefix("m", "m", -3),
          new Prefix("c", "c", -2),
          new Prefix("d", "d", -1),
          new Prefix("h", "h", 2),
          new Prefix("k", "k", 3),
          new Prefix("M", "M", 6),
          new Prefix("G", "G", 9),
          new Prefix("T", "T", 12),
          new Prefix("P", "P", 15),
          new Prefix("E", "E", 18),
          new Prefix("Z", "Z", 21),
          new Prefix("Y", "Y", 24));

  private static final Map<String, UnitTable.Entry> BY_SPELLING = new HashMap<>();

  private static final Map<String, Optional<Info>> RESOLVED = new ConcurrentHashMap<>();

  static {
    for (UnitTable.Entry e : UnitTable.ENTRIES) {
      BY_SPELLING.put(e.name(), e);
      BY_SPELLING.put(e.unicode(), e);
    }
  }

  private Units() {}

  /**
   * Resolves a written unit name, without its underscore.
   *
   * @param written the name as typed: {@code km}, {@code µm}, {@code kΩ}
   * @return the unit, or empty when the name is no unit
   */
  public static Optional<Unit> of(String written) {
    return resolve(written).map(Units::node);
  }

  /**
   * The unit of a known ASCII name, for code that builds units.
   *
   * @param name the name: {@code m}, {@code km}
   * @return the unit
   * @throws IllegalArgumentException if the name is no unit
   */
  public static Unit unit(String name) {
    return of(name).orElseThrow(() -> new IllegalArgumentException("unknown unit " + name));
  }

  /**
   * What a unit is.
   *
   * @param u a unit the table built
   * @return its entry and prefix
   * @throws IllegalArgumentException if the unit's name is no unit
   */
  public static Info info(Unit u) {
    return resolve(u.name())
        .orElseThrow(() -> new IllegalArgumentException("unknown unit " + u.name()));
  }

  private static Optional<Info> resolve(String written) {
    return RESOLVED.computeIfAbsent(written, Units::lookup);
  }

  private static Optional<Info> lookup(String written) {
    UnitTable.Entry exact = BY_SPELLING.get(written);
    if (exact != null) {
      return Optional.of(new Info(exact, null));
    }
    String normal = written.replace('μ', 'µ');
    for (Prefix p : PREFIXES) {
      for (String spelling : List.of(p.ascii(), p.unicode())) {
        if (normal.length() > spelling.length() && normal.startsWith(spelling)) {
          UnitTable.Entry e = BY_SPELLING.get(normal.substring(spelling.length()));
          if (e != null && e.prefixable()) {
            return Optional.of(new Info(e, p));
          }
        }
      }
    }
    return Optional.empty();
  }

  private static Unit node(Info info) {
    String name = (info.prefix() == null ? "" : info.prefix().ascii()) + info.entry().name();
    return new Unit(name, rank(info));
  }

  /**
   * The order key of a unit: the table index, then the coherent unit before the other prefixes,
   * then the prefix's exponent.
   */
  static int rank(Info info) {
    int exponent = info.prefix() == null ? 0 : info.prefix().exponent();
    int slot = exponent == info.entry().coherent() ? 0 : 1 + exponent + 24;
    return info.entry().index() * 64 + slot;
  }

  private static RealNumeric power(int exponent) {
    BigInteger ten = BigInteger.TEN.pow(Math.abs(exponent));
    return exponent >= 0 ? Int.of(ten) : Rational.of(BigInteger.ONE, ten);
  }

  /**
   * Every unit the table builds without a prefix, and every prefix on each unit that takes them
   * (but a prefixed name that is another unit's spelling), for the tests.
   *
   * @return the units
   */
  public static List<Unit> all() {
    List<Unit> out = new ArrayList<>();
    for (UnitTable.Entry e : UnitTable.ENTRIES) {
      out.add(node(new Info(e, null)));
      if (e.prefixable()) {
        for (Prefix p : PREFIXES) {
          // pt and ft are the pint and the foot, never a picotonne or a femtotonne
          if (!BY_SPELLING.containsKey(p.ascii() + e.name())) {
            out.add(node(new Info(e, p)));
          }
        }
      }
    }
    return out;
  }
}
