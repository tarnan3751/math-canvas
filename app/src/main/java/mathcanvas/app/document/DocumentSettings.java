package mathcanvas.app.document;

import java.util.Objects;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.eval.Settings;

/**
 * The settings that belong to the document and change through its commands, so an undo reverts
 * them: the precision and the angle mode evaluation uses, and the number format results are shown
 * in.
 *
 * @param precision the digits of inexact arithmetic, from {@link Settings#MIN_PRECISION} to {@link
 *     Settings#MAX_PRECISION}
 * @param angleMode the angle mode
 * @param numberFormat how numeric results are shown
 */
public record DocumentSettings(int precision, AngleMode angleMode, NumberFormat numberFormat) {

  /** The engine's defaults (34 digits, radians), exact results. */
  public static final DocumentSettings DEFAULT =
      new DocumentSettings(
          Settings.DEFAULT.precision(), Settings.DEFAULT.angleMode(), NumberFormat.EXACT);

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if the angle mode or the number format is null
   * @throws IllegalArgumentException if the precision is out of range
   */
  public DocumentSettings {
    if (!Settings.isPrecision(precision)) {
      throw new IllegalArgumentException("precision out of range: " + precision);
    }
    Objects.requireNonNull(angleMode, "angleMode");
    Objects.requireNonNull(numberFormat, "numberFormat");
  }

  /**
   * The engine settings evaluation runs under.
   *
   * @return the precision and the angle mode
   */
  public Settings engine() {
    return new Settings(precision, angleMode);
  }
}
