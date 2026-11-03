package mathcanvas.app.eval;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mathcanvas.app.text.Messages;
import mathcanvas.canvas.model.BoxPlot;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.Histogram;
import mathcanvas.canvas.model.ScatterPlot;
import mathcanvas.canvas.model.Summary;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;

/**
 * The statistics panel's lines (Phase 8c): for each data cell a plot reads, by its name, the
 * summary its plot's geometry carries ({@code n}, the mean, the median, the quartiles, the least
 * and the greatest value, the sample standard deviation), each number to four significant digits. A
 * histogram's and a box plot's data are one cell; a scatter plot's two lists are two, and its
 * matrix is one cell with a line for each column. Data written in place, not a cell's name, has no
 * line; a cell two plots read has one, from the first. Pure arithmetic and text over the canvas, no
 * engine work, so the panel builds it on the FX thread from the frame.
 */
public final class StatisticsSummaries {

  private StatisticsSummaries() {}

  /**
   * One line: the data it is of and its summary.
   *
   * @param data the cell's name, with the column for a matrix's
   * @param text the summary
   */
  public record Line(String data, String text) {}

  /**
   * The lines of a canvas, in its drawing order.
   *
   * @param state the canvas and its geometry
   * @return the lines, possibly none
   */
  public static List<Line> of(CanvasState state) {
    Map<String, Summary> out = new LinkedHashMap<>();
    for (CanvasObject o : state.objects()) {
      Geometry g = state.geometryOf(o.id());
      if (o instanceof Histogram h && g instanceof Geometry.Bins b) {
        put(out, h.data(), b.summary());
      } else if (o instanceof BoxPlot p && g instanceof Geometry.Box b) {
        put(out, p.data(), b.summary());
      } else if (o instanceof ScatterPlot p && g instanceof Geometry.Scatter s) {
        switch (p.points()) {
          case ScatterPlot.Columns c -> {
            put(out, c.xs(), s.x());
            put(out, c.ys(), s.y());
          }
          case ScatterPlot.Pairs pairs -> {
            if (pairs.matrix() instanceof Symbol m) {
              out.putIfAbsent(Messages.text("stats.of.x", m.name()), s.x());
              out.putIfAbsent(Messages.text("stats.of.y", m.name()), s.y());
            }
          }
        }
      }
    }
    List<Line> lines = new ArrayList<>();
    out.forEach((data, s) -> lines.add(new Line(data, text(s))));
    return lines;
  }

  private static void put(Map<String, Summary> out, Expr data, Summary s) {
    if (data instanceof Symbol name) {
      out.putIfAbsent(name.name(), s);
    }
  }

  /** A summary's text. */
  private static String text(Summary s) {
    return Messages.text(
        "stats.summary",
        Integer.toString(s.n()),
        number(s.mean()),
        number(s.median()),
        number(s.q1()),
        number(s.q3()),
        number(s.min()),
        number(s.max()),
        number(s.stdev()));
  }

  /**
   * A number to four significant digits, trailing zeros dropped, in scientific form ({@code
   * 1.235E-5}, {@code 2E6}) when the rounded number is 10⁶ or above or below 10⁻⁴ ({@code 999999.7}
   * is {@code 1E6}); "undefined" for a number that is not finite (the standard deviation of one
   * value, or of values whose squares overflow). The bin-width spinner writes its widths so too.
   *
   * @param value the number
   * @return the text
   */
  public static String number(double value) {
    if (!Double.isFinite(value)) {
      return Messages.text("row.undefined");
    }
    if (value == 0) {
      return "0";
    }
    BigDecimal d = new BigDecimal(value).round(new MathContext(4)).stripTrailingZeros();
    int exponent = d.precision() - d.scale() - 1;
    if (exponent >= 6 || exponent < -4) {
      return d.movePointLeft(exponent).toPlainString() + "E" + exponent;
    }
    return d.toPlainString();
  }
}
