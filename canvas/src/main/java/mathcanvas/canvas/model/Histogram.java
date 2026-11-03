package mathcanvas.canvas.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;

/**
 * A histogram of a sample: bars from {@code start} by {@code binWidth}, each as tall as the count
 * of the values in it ({@link Statistics#histogram}). The data, the width and the start are
 * expressions, which may read cells and sliders; with no width the bins are Sturges' count over the
 * data's range, with no start they begin at the least value.
 *
 * @param id the id
 * @param style the style
 * @param data the sample, a list of real numbers
 * @param binWidth the bins' width, or empty for Sturges' rule
 * @param start where the first bin begins, or empty for the least value
 */
public record Histogram(
    ObjectId id, Style style, Expr data, Optional<Expr> binWidth, Optional<Expr> start)
    implements CanvasObject {

  /** The most bins a histogram is drawn with. */
  public static final int MAX_BINS = 1000;

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   */
  public Histogram {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(data, "data");
    Objects.requireNonNull(binWidth, "binWidth");
    Objects.requireNonNull(start, "start");
  }

  @Override
  public List<ObjectId> parents() {
    return List.of();
  }

  @Override
  public Set<String> reads() {
    List<Expr> exprs = new ArrayList<>();
    exprs.add(data);
    binWidth.ifPresent(exprs::add);
    start.ifPresent(exprs::add);
    return Reads.of(Set.of(), exprs.toArray(Expr[]::new));
  }

  @Override
  public Histogram withStyle(Style style) {
    return new Histogram(id, style, data, binWidth, start);
  }

  /**
   * This histogram with bins of another width, an exact decimal.
   *
   * @param width the width, positive
   * @return the histogram
   */
  public Histogram withBinWidth(BigDecimal width) {
    BigDecimal w = width.stripTrailingZeros();
    Num n =
        w.scale() <= 0
            ? Num.of(Int.of(w.toBigIntegerExact()))
            : Num.of(Rational.of(w.unscaledValue(), BigInteger.TEN.pow(w.scale())));
    return new Histogram(id, style, data, Optional.of(n), start);
  }

  /**
   * The width a step of the bin-width spinner goes to: the next of 1, 2 and 5 times a power of ten
   * above or below a width, so a step from Sturges' 1.8 goes to 2 or 1, and a step from 5 to 10 or
   * 2.
   *
   * @param width the width now, positive and finite
   * @param steps how many steps up (positive) or down (negative)
   * @return the width, an exact decimal
   */
  public static BigDecimal steppedWidth(double width, int steps) {
    int exponent = (int) Math.floor(Math.log10(width));
    // the ladder's rungs around the width: index 3·exponent + 0, 1, 2 for 1, 2 and 5
    int at = 3 * exponent;
    while (rung(at + 1).doubleValue() <= width) {
      at++;
    }
    while (rung(at).doubleValue() > width) {
      at--;
    }
    // at is the highest rung at or below the width
    boolean on = rung(at).doubleValue() == width;
    return rung(steps > 0 || on ? at + steps : at + steps + 1);
  }

  /** The ladder's rung {@code k}: 1, 2 or 5 times {@code 10^floor(k/3)}. */
  private static BigDecimal rung(int k) {
    int exponent = Math.floorDiv(k, 3);
    int mantissa = new int[] {1, 2, 5}[Math.floorMod(k, 3)];
    return BigDecimal.valueOf(mantissa).scaleByPowerOfTen(exponent);
  }
}
