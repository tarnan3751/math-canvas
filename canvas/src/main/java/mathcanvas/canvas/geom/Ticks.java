package mathcanvas.canvas.geom;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.ToDoubleFunction;

/**
 * The ticks of one axis: a major step {@code mantissa × 10^exponent} with the mantissa 1, 2 or 5,
 * chosen as the smallest such step at least {@link #MIN_SPACING} pixels apart whose widest visible
 * label, plus {@link #LABEL_GAP} pixels, fits between neighbouring ticks; and a minor step, a fifth
 * of the major for a mantissa of 1 or 5 and a half for 2, itself a 1-2-5 value. Labels of a few
 * digits are therefore 60 to 150 pixels apart (a 1-2-5 sequence cannot always land within a
 * narrower band); long ones, far from the origin or deep in a zoom, further. The label width is a
 * function given by the caller, real text measurement on the screen or {@link #estimate} without
 * one, so this package needs no font.
 *
 * <p>Tick {@code i} sits at {@code i × step}. Its label is that value computed exactly, written
 * with the fewest digits that show it (trailing zeros dropped, so neighbours always differ), with a
 * true minus sign, and in scientific form ({@code 2.5×10⁻⁵}) when its magnitude is at least 10⁶ or
 * below 10⁻⁴.
 *
 * @param mantissa the major step's mantissa: 1, 2 or 5
 * @param exponent the major step's power of ten
 * @param minorMantissa the minor step's mantissa
 * @param minorExponent the minor step's power of ten
 */
public record Ticks(int mantissa, int exponent, int minorMantissa, int minorExponent) {

  /** The least distance between labelled ticks, in pixels. */
  public static final double MIN_SPACING = 60;

  /** The least space between neighbouring labels, in pixels. */
  public static final double LABEL_GAP = 12;

  /** The label font size {@link #forAxis(double, double, double)} estimates widths at. */
  public static final double DEFAULT_LABEL_SIZE = 12;

  private static final BigDecimal SCIENTIFIC_ABOVE = BigDecimal.TEN.pow(6);
  private static final BigDecimal SCIENTIFIC_BELOW = new BigDecimal("1e-4");
  private static final String SUPERSCRIPT_DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹";
  private static final char MINUS = '−';
  private static final char SUPERSCRIPT_MINUS = '⁻';

  /**
   * Checks the steps.
   *
   * @throws IllegalArgumentException if a mantissa is not 1, 2 or 5
   */
  public Ticks {
    if (!isMantissa(mantissa) || !isMantissa(minorMantissa)) {
      throw new IllegalArgumentException(
          "mantissa not 1, 2 or 5: " + mantissa + ", " + minorMantissa);
    }
  }

  /**
   * The ticks for an axis showing {@code [min, max]} across {@code pixels}, labels measured by
   * {@link #estimate} at {@link #DEFAULT_LABEL_SIZE}.
   *
   * @param min the world value at one end
   * @param max the world value at the other, above {@code min}
   * @param pixels the axis length in pixels, positive
   * @return the ticks
   */
  public static Ticks forAxis(double min, double max, double pixels) {
    return forAxis(min, max, pixels, estimate(DEFAULT_LABEL_SIZE));
  }

  /**
   * The ticks for an axis showing {@code [min, max]} across {@code pixels}.
   *
   * @param min the world value at one end
   * @param max the world value at the other, above {@code min}
   * @param pixels the axis length in pixels, positive
   * @param labelWidth a label's extent along the axis in pixels: its width on a horizontal axis,
   *     its height on a vertical one
   * @return the ticks
   * @throws IllegalArgumentException if the span or the length is not positive and finite
   */
  public static Ticks forAxis(
      double min, double max, double pixels, ToDoubleFunction<String> labelWidth) {
    double span = max - min;
    if (!(Double.isFinite(span) && span > 0 && Double.isFinite(pixels) && pixels > 0)) {
      throw new IllegalArgumentException("bad axis: " + min + " .. " + max + " over " + pixels);
    }
    // In two logarithms: the product overflows for a span above about 3e306.
    int k = (int) Math.floor(Math.log10(span) + Math.log10(MIN_SPACING / pixels)) - 1;
    Ticks t = of(1, k);
    while (t.step() / span * pixels < MIN_SPACING) {
      t = t.next();
    }
    // Wider steps until the widest visible label fits, or at most one tick shows.
    while (true) {
      double spacing = t.step() / span * pixels;
      if (spacing >= pixels || t.widestLabel(min, max, labelWidth) + LABEL_GAP <= spacing) {
        return t;
      }
      t = t.next();
    }
  }

  /**
   * A label width from its character count, for drawing without text measurement: 0.6 of the font
   * size per character, about a digit's width in a sans-serif font.
   *
   * @param fontSize the font size in pixels
   * @return the estimate
   */
  public static ToDoubleFunction<String> estimate(double fontSize) {
    return label -> 0.6 * fontSize * label.codePointCount(0, label.length());
  }

  /**
   * The widest label of the ticks in {@code [min, max]}.
   *
   * @param min the world value at one end
   * @param max the world value at the other
   * @param labelWidth a label's width
   * @return the width in pixels, 0 when no tick is in range
   */
  public double widestLabel(double min, double max, ToDoubleFunction<String> labelWidth) {
    double widest = 0;
    for (long i = first(min); i <= last(max); i++) {
      widest = Math.max(widest, labelWidth.applyAsDouble(label(i)));
    }
    return widest;
  }

  /**
   * The next larger step of the 1-2-5 sequence.
   *
   * @return the ticks
   */
  public Ticks next() {
    return mantissa == 5 ? of(1, exponent + 1) : of(mantissa == 1 ? 2 : 5, exponent);
  }

  /**
   * The next smaller step of the 1-2-5 sequence.
   *
   * @return the ticks
   */
  public Ticks previous() {
    return mantissa == 1 ? of(5, exponent - 1) : of(mantissa == 5 ? 2 : 1, exponent);
  }

  private static Ticks of(int m, int k) {
    return m == 2 ? new Ticks(2, k, 1, k) : new Ticks(m, k, m == 1 ? 2 : 1, m == 1 ? k - 1 : k);
  }

  /**
   * The major step.
   *
   * @return {@code mantissa × 10^exponent}
   */
  public double step() {
    return step(mantissa, exponent);
  }

  /**
   * The minor step.
   *
   * @return {@code minorMantissa × 10^minorExponent}
   */
  public double minorStep() {
    return step(minorMantissa, minorExponent);
  }

  /**
   * The index of the first major tick at or above a value.
   *
   * @param value a world value
   * @return {@code ceil(value / step)}
   */
  public long first(double value) {
    return (long) Math.ceil(value / step());
  }

  /**
   * The index of the last major tick at or below a value.
   *
   * @param value a world value
   * @return {@code floor(value / step)}
   */
  public long last(double value) {
    return (long) Math.floor(value / step());
  }

  /**
   * The world value of a major tick.
   *
   * @param index the tick's index
   * @return {@code index × step}
   */
  public double value(long index) {
    return index * step();
  }

  /**
   * The exact value of a major tick.
   *
   * @param index the tick's index
   * @return {@code index × mantissa × 10^exponent}, exact
   */
  public BigDecimal exactValue(long index) {
    return BigDecimal.valueOf(index * mantissa, -exponent);
  }

  /**
   * The label of a major tick.
   *
   * @param index the tick's index
   * @return the label
   */
  public String label(long index) {
    BigDecimal v = exactValue(index);
    if (v.signum() == 0) {
      return "0";
    }
    BigDecimal abs = v.abs();
    String sign = v.signum() < 0 ? String.valueOf(MINUS) : "";
    if (abs.compareTo(SCIENTIFIC_ABOVE) < 0 && abs.compareTo(SCIENTIFIC_BELOW) >= 0) {
      return sign + plain(abs);
    }
    int e = abs.precision() - abs.scale() - 1;
    StringBuilder out = new StringBuilder(sign).append(plain(abs.movePointLeft(e))).append("×10");
    String digits = Integer.toString(e);
    for (char c : digits.toCharArray()) {
      out.append(c == '-' ? SUPERSCRIPT_MINUS : SUPERSCRIPT_DIGITS.charAt(c - '0'));
    }
    return out.toString();
  }

  private static String plain(BigDecimal v) {
    BigDecimal stripped = v.stripTrailingZeros();
    return (stripped.scale() < 0 ? stripped.setScale(0, RoundingMode.UNNECESSARY) : stripped)
        .toPlainString();
  }

  private static double step(int m, int k) {
    return Double.parseDouble(m + "e" + k);
  }

  private static boolean isMantissa(int m) {
    return m == 1 || m == 2 || m == 5;
  }
}
