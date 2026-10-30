package mathcanvas.app.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import mathcanvas.app.text.Messages;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.hit.Hit;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.print.Format;
import mathcanvas.engine.print.UnicodePrinter;

/**
 * The trace readout: the coordinates of the point the pointer is nearest on what it hovers, for the
 * status line. Each coordinate is rounded to the decimal place of one pixel at the viewport's scale
 * (a hit is only as precise as the pointer) and printed as a decimal in the document's number
 * format, so it reads like the worksheet's decimals ({@code 2.0}, {@code -0.5}). On a curve it also
 * gives the slope there, rounded to the finer of the two coordinates' places.
 */
final class Readout {

  private Readout() {}

  /**
   * The readout of a hit.
   *
   * @param hit the hit
   * @param viewport the viewport the pointer is over
   * @param format the document's number format
   * @return {@code x = …, y = …}, or null when a coordinate is not finite (nothing to read out)
   */
  static String text(Hit hit, Viewport viewport, Format format) {
    return text(hit, Double.NaN, viewport, format);
  }

  /**
   * The readout of a hit on a curve with its slope there.
   *
   * @param hit the hit
   * @param slope the slope at the hit, NaN for none
   * @param viewport the viewport the pointer is over
   * @param format the document's number format
   * @return {@code x = …, y = …, slope = …}, without the slope when it is not finite; null when a
   *     coordinate is not finite
   */
  static String text(Hit hit, double slope, Viewport viewport, Format format) {
    if (!Double.isFinite(hit.x()) || !Double.isFinite(hit.y())) {
      return null;
    }
    UnicodePrinter printer = UnicodePrinter.INSTANCE.withFormat(format);
    int px = places(viewport.pixelsPerUnitX());
    int py = places(viewport.pixelsPerUnitY());
    String x = print(hit.x(), px, printer);
    String y = print(hit.y(), py, printer);
    return Double.isFinite(slope)
        ? Messages.text("canvas.readout.slope", x, y, print(slope, Math.max(px, py), printer))
        : Messages.text("canvas.readout", x, y);
  }

  /** The decimal places of one pixel. */
  private static int places(double pixelsPerUnit) {
    return Math.max(0, (int) Math.ceil(Math.log10(pixelsPerUnit)));
  }

  private static String print(double value, int places, UnicodePrinter printer) {
    BigDecimal rounded =
        BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_EVEN).stripTrailingZeros();
    return printer.display(Num.of(Real.of(rounded)));
  }
}
