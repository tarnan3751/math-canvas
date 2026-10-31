package mathcanvas.app.eval;

import mathcanvas.app.document.DocumentSettings;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.print.Format;
import mathcanvas.engine.print.UnicodePrinter;

/**
 * How a point's coordinates are written where they are shown as numbers (an algebra panel row, the
 * coordinate entry's prompt): in the document's number format, as decimals, since a coordinate is a
 * {@code double} and never exact. The decimal and scientific formats keep their digits; the exact
 * and fraction formats write decimals with the document's digits. A coordinate below {@link #NOISE}
 * of the point's larger coordinate, or of 1 when both are smaller, is written {@code 0}: the
 * ordinate of a refined root is the function's rounding, not a value. The trace readout rounds to a
 * pixel instead, the precision of a hover ({@code ui.Readout}).
 */
public final class Coordinates {

  /** Below this fraction of the point's scale a coordinate is written {@code 0}. */
  static final double NOISE = 1e-12;

  private Coordinates() {}

  /**
   * The format coordinates are written in under a document's settings.
   *
   * @param settings the document settings
   * @return the format: the decimal or scientific one, else decimal with the document's digits
   */
  public static Format format(DocumentSettings settings) {
    return switch (settings.numberFormat()) {
      case DECIMAL, SCIENTIFIC -> settings.format();
      case EXACT, FRACTION -> Format.decimal(settings.numberDigits());
    };
  }

  /**
   * A point as the vector {@code ⟨x, y⟩} of its coordinates, each a decimal or {@code 0}.
   *
   * @param x the abscissa, finite
   * @param y the ordinate, finite
   * @return the vector
   */
  static Expr point(double x, double y) {
    double scale = Math.max(Math.abs(x), Math.abs(y));
    return Vector.of(number(x, scale), number(y, scale));
  }

  /**
   * One coordinate, written in the format of a document's settings.
   *
   * @param value the coordinate, finite
   * @param settings the document settings
   * @return the text
   */
  public static String text(double value, DocumentSettings settings) {
    return UnicodePrinter.INSTANCE
        .withFormat(format(settings))
        .print(number(value, Math.abs(value)));
  }

  /**
   * One number, written in a format of coordinates.
   *
   * @param value the number, finite
   * @param format the format ({@link #format})
   * @return the text
   */
  static String text(double value, Format format) {
    return UnicodePrinter.INSTANCE.withFormat(format).print(number(value, Math.abs(value)));
  }

  private static Expr number(double value, double scale) {
    return Math.abs(value) < NOISE * Math.max(scale, 1) ? Num.ZERO : Num.of(Real.of(value));
  }
}
