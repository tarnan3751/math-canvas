package mathcanvas.engine.units;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;

/**
 * The unit table ({@code docs/language.md} §14.26): every unit with its dimension and its exact
 * factor to the coherent SI unit of that dimension. Sources: the SI Brochure, 9th edition (2019),
 * for the SI and accepted units and the electronvolt (the elementary charge is exact since 2019);
 * NIST SP 811 (2008), Appendix B, for the imperial and US units, with the international foot of
 * 0.3048 m (the US survey foot was retired in 2023), so the acre is 43,560 square feet and the
 * pound-force is 0.45359237 kg times the standard gravity 9.80665 m/s², both exact. Every factor is
 * a rational, except the degree's, which is π/180.
 *
 * <p>The order of the list is the units' order in a product ({@link Units#rank}): the SI derived
 * units, then the base units in the order {@code kg m s A K mol cd}, then the accepted units, then
 * the imperial and US units, then the temperatures in degrees Celsius and Fahrenheit.
 */
public final class UnitTable {

  private UnitTable() {}

  /**
   * A unit of the table.
   *
   * @param name the ASCII spelling without the underscore
   * @param unicode the spelling the Unicode printer writes ({@code Ω} for {@code ohm})
   * @param shown the spelling the layout shows ({@code °C} for {@code degC})
   * @param dimension its dimension
   * @param factor its exact factor to the coherent SI unit of its dimension
   * @param offset for an affine temperature, the value added before the factor (in the unit's own
   *     scale: 0 °C is 273.15 K, so {@code degC}'s offset is 5463/20); otherwise null
   * @param prefixable whether the SI prefixes apply
   * @param coherent the exponent of the prefix that makes the coherent unit: 3 for the gram, whose
   *     coherent unit is the kilogram; 0 otherwise
   * @param index its place in the table
   */
  public record Entry(
      String name,
      String unicode,
      String shown,
      Dimension dimension,
      Expr factor,
      RealNumeric offset,
      boolean prefixable,
      int coherent,
      int index) {}

  /** The entries, in the table's order. */
  static final List<Entry> ENTRIES;

  private static final Dimension MASS = Dimension.of(1, 0, 0, 0, 0, 0, 0);
  private static final Dimension LENGTH = Dimension.of(0, 1, 0, 0, 0, 0, 0);
  private static final Dimension TIME = Dimension.of(0, 0, 1, 0, 0, 0, 0);
  private static final Dimension TEMPERATURE = Dimension.of(0, 0, 0, 0, 1, 0, 0);
  private static final Dimension AREA = Dimension.of(0, 2, 0, 0, 0, 0, 0);
  private static final Dimension VOLUME = Dimension.of(0, 3, 0, 0, 0, 0, 0);
  private static final Dimension SPEED = Dimension.of(0, 1, -1, 0, 0, 0, 0);
  private static final Dimension ENERGY = Dimension.of(1, 2, -2, 0, 0, 0, 0);
  private static final Dimension POWER = Dimension.of(1, 2, -3, 0, 0, 0, 0);
  private static final Dimension PRESSURE = Dimension.of(1, -1, -2, 0, 0, 0, 0);

  /** The US gallon, 231 cubic inches, in cubic metres. */
  private static final RealNumeric GALLON = q("473176473", "125000000000");

  /** The avoirdupois pound in kilograms. */
  private static final RealNumeric POUND = q("45359237", "100000000");

  static {
    List<Entry> out = new ArrayList<>();
    // SI derived units
    add(out, "N", Dimension.of(1, 1, -2, 0, 0, 0, 0), one(), true);
    add(out, "J", ENERGY, one(), true);
    add(out, "W", POWER, one(), true);
    add(out, "Pa", PRESSURE, one(), true);
    add(out, "Hz", Dimension.of(0, 0, -1, 0, 0, 0, 0), one(), true);
    add(out, "C", Dimension.of(0, 0, 1, 1, 0, 0, 0), one(), true);
    add(out, "V", Dimension.of(1, 2, -3, -1, 0, 0, 0), one(), true);
    out.add(
        new Entry(
            "ohm",
            "Ω",
            "Ω",
            Dimension.of(1, 2, -3, -2, 0, 0, 0),
            one(),
            null,
            true,
            0,
            out.size()));
    add(out, "F", Dimension.of(-1, -2, 4, 2, 0, 0, 0), one(), true);
    add(out, "T", Dimension.of(1, 0, -2, -1, 0, 0, 0), one(), true);
    add(out, "Wb", Dimension.of(1, 2, -2, -1, 0, 0, 0), one(), true);
    add(out, "H", Dimension.of(1, 2, -2, -2, 0, 0, 0), one(), true);
    // SI base units; the gram's coherent form is the kilogram
    out.add(new Entry("g", "g", "g", MASS, num(q("1", "1000")), null, true, 3, out.size()));
    add(out, "m", LENGTH, one(), true);
    add(out, "s", TIME, one(), true);
    add(out, "A", Dimension.of(0, 0, 0, 1, 0, 0, 0), one(), true);
    add(out, "K", TEMPERATURE, one(), true);
    add(out, "mol", Dimension.of(0, 0, 0, 0, 0, 1, 0), one(), true);
    add(out, "cd", Dimension.of(0, 0, 0, 0, 0, 0, 1), one(), true);
    // accepted units
    add(out, "L", VOLUME, num(q("1", "1000")), true);
    add(out, "t", MASS, num(Int.of(1000)), true);
    add(out, "eV", ENERGY, num(q("1602176634", "10000000000000000000000000000")), true);
    add(out, "bar", PRESSURE, num(Int.of(100000)), true);
    add(out, "atm", PRESSURE, num(Int.of(101325)), false);
    add(out, "cal", ENERGY, num(q("4184", "1000")), true);
    add(out, "Wh", ENERGY, num(Int.of(3600)), true);
    add(out, "min", TIME, num(Int.of(60)), false);
    add(out, "h", TIME, num(Int.of(3600)), false);
    add(out, "d", TIME, num(Int.of(86400)), false);
    add(out, "yr", TIME, num(Int.of(31557600)), false);
    out.add(
        new Entry(
            "deg",
            "deg",
            "°",
            Dimension.NONE,
            Canonical.of(Product.of(Num.of(q("1", "180")), Constant.PI)),
            null,
            false,
            0,
            out.size()));
    add(out, "rad", Dimension.NONE, one(), true);
    add(out, "sr", Dimension.NONE, one(), false);
    // imperial and US units (NIST SP 811, Appendix B)
    add(out, "in", LENGTH, num(q("127", "5000")), false);
    add(out, "ft", LENGTH, num(q("381", "1250")), false);
    add(out, "yd", LENGTH, num(q("1143", "1250")), false);
    add(out, "mi", LENGTH, num(q("201168", "125")), false);
    add(out, "nmi", LENGTH, num(Int.of(1852)), false);
    add(out, "oz", MASS, num(times(POUND, q("1", "16"))), false);
    add(out, "lb", MASS, num(POUND), false);
    add(out, "st", MASS, num(times(POUND, Int.of(14))), false);
    add(out, "ton", MASS, num(times(POUND, Int.of(2000))), false);
    add(out, "floz", VOLUME, num(times(GALLON, q("1", "128"))), false);
    add(out, "pt", VOLUME, num(times(GALLON, q("1", "8"))), false);
    add(out, "qt", VOLUME, num(times(GALLON, q("1", "4"))), false);
    add(out, "gal", VOLUME, num(GALLON), false);
    add(out, "mph", SPEED, num(q("1397", "3125")), false);
    add(
        out,
        "acre",
        AREA,
        num(times(q("381", "1250"), times(q("381", "1250"), Int.of(43560)))),
        false);
    // the pound-force per square inch and the mechanical horsepower (550 ft·lbf/s)
    RealNumeric lbf = times(POUND, q("980665", "100000"));
    add(out, "psi", PRESSURE, num(times(lbf, q("25000000", "16129"))), false);
    add(out, "hp", POWER, num(times(lbf, times(q("381", "1250"), Int.of(550)))), false);
    // temperatures on an offset scale
    out.add(
        new Entry("degC", "degC", "°C", TEMPERATURE, one(), q("5463", "20"), false, 0, out.size()));
    out.add(
        new Entry(
            "degF",
            "degF",
            "°F",
            TEMPERATURE,
            num(q("5", "9")),
            q("45967", "100"),
            false,
            0,
            out.size()));
    ENTRIES = List.copyOf(out);
  }

  private static void add(
      List<Entry> out, String name, Dimension dimension, Expr factor, boolean prefixable) {
    out.add(new Entry(name, name, name, dimension, factor, null, prefixable, 0, out.size()));
  }

  private static Expr one() {
    return Num.ONE;
  }

  private static Num num(RealNumeric r) {
    return Num.of(r);
  }

  private static RealNumeric q(String num, String den) {
    return Rational.of(new BigInteger(num), new BigInteger(den));
  }

  private static RealNumeric times(RealNumeric a, RealNumeric b) {
    return (RealNumeric) a.multiply(b, java.math.MathContext.UNLIMITED);
  }
}
