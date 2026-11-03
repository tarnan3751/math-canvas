package mathcanvas.canvas.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Expr;

/**
 * The geometry of the statistical plots (docs/reference/canvas.md, <b>Object model</b>): a
 * histogram's bins and their counts, a bar chart's bars, a box plot's summary and fences, a scatter
 * plot's points. The data are read through the sampler ({@link Sampler#table}); the counts and the
 * summaries are {@code double} arithmetic here, never exact, as every plotted quantity is.
 */
public final class Statistics {

  /**
   * Why a statistical plot is undefined, for its row.
   *
   * <ul>
   *   <li>{@code DATA}: its data is not a list of real numbers (a scatter plot's: not a matrix of
   *       two columns, or two lists of one length; a bar chart's: not as many values as
   *       categories);
   *   <li>{@code BINS}: a histogram's width is not a positive number, its start not a number, or
   *       the bins from its start past its greatest value are more than {@link Histogram#MAX_BINS};
   *   <li>{@code HEIGHT}: a box plot's height is not a number.
   * </ul>
   */
  public enum Problem {
    /** The data is not data of the plot's shape. */
    DATA,
    /** The bins are not numbers, or too many. */
    BINS,
    /** The height is not a number. */
    HEIGHT
  }

  /**
   * How far below a bin's edge, in widths, a value still counts as on it: the edges {@code start +
   * k·width} are rounded, so 0.3 by bins of 0.1 from 0 lands on the edge 0.30000000000000004 and
   * goes to the upper bin, as a value on an edge does.
   */
  static final double EDGE = 1e-9;

  /** A bar chart's bar's width, of the unit between two bars. */
  static final double BAR_WIDTH = 0.8;

  /** How many interquartile ranges beyond the box a box plot's fences are. */
  static final double FENCE = 1.5;

  private Statistics() {}

  /** A geometry, or why there is none. */
  private record Outcome(Geometry geometry, Optional<Problem> problem) {

    static Outcome of(Geometry g) {
      return new Outcome(g, Optional.empty());
    }

    static Outcome failed(Problem p) {
      return new Outcome(Geometry.UNDEFINED, Optional.of(p));
    }
  }

  /**
   * A histogram's bins: from its start (the least value by default) by its width, as many as reach
   * the greatest value. A value on an edge between two bins is counted in the upper one, and values
   * below the start in none. With no width, the bins are Sturges' count, {@code ⌈log₂ n⌉ + 1}, over
   * the range (one bin of width 1 when every value is one), and the greatest value, on the last
   * bin's upper edge, is in the last bin; with a width, a value on that edge is in a bin of its own
   * above, as on any edge.
   *
   * @param h the histogram
   * @param sampler reads its data and its numbers
   * @param cancellation checked as it goes
   * @return the bins, or undefined
   */
  static Geometry histogram(Histogram h, Sampler sampler, Cancellation cancellation) {
    return histogramOutcome(h, sampler, cancellation).geometry();
  }

  private static Outcome histogramOutcome(Histogram h, Sampler sampler, Cancellation cancellation) {
    Optional<double[]> data = column(h.data(), sampler, cancellation);
    if (data.isEmpty()) {
      return Outcome.failed(Problem.DATA);
    }
    double[] v = data.get();
    Summary s = Summary.of(v);
    double width =
        h.binWidth().isPresent()
            ? sampler.value(h.binWidth().get(), Map.of())
            : s.max() > s.min() ? (s.max() - s.min()) / sturges(s.n()) : 1;
    double start = h.start().isPresent() ? sampler.value(h.start().get(), Map.of()) : s.min();
    if (!(width > 0) || !Double.isFinite(width) || !Double.isFinite(start) || start > s.max()) {
      return Outcome.failed(Problem.BINS);
    }
    // Sturges' bins are as many as reach the greatest value, which the last holds on its upper
    // edge; bins of a given width as many as hold it in the upper bin of its edge
    double span =
        h.binWidth().isEmpty()
            ? Math.max(1, Math.ceil((s.max() - start) / width - EDGE))
            : Math.floor((s.max() - start) / width + EDGE) + 1;
    if (!(span <= Histogram.MAX_BINS)) {
      return Outcome.failed(Problem.BINS);
    }
    int count = (int) span;
    int[] counts = new int[count];
    for (double x : v) {
      double k = Math.floor((x - start) / width + EDGE);
      if (k >= 0) {
        counts[(int) Math.min(k, count - 1)]++;
      }
    }
    List<Geometry.Bar> bars = new ArrayList<>();
    for (int k = 0; k < count; k++) {
      bars.add(new Geometry.Bar(start + k * width, start + (k + 1) * width, counts[k]));
    }
    return Outcome.of(new Geometry.Bins(bars, width, s));
  }

  /**
   * Sturges' number of bins for {@code n} values: {@code ⌈log₂ n⌉ + 1}.
   *
   * @param n the number of values, at least 1
   * @return the number of bins
   */
  static int sturges(int n) {
    return (32 - Integer.numberOfLeadingZeros(n - 1)) + 1;
  }

  /**
   * A bar chart's bars: the {@code k}-th value's bar centred on {@code k}, {@link #BAR_WIDTH} wide,
   * labelled with its category when there are categories.
   *
   * @param b the bar chart
   * @param sampler reads its values
   * @param cancellation checked as it goes
   * @return the bars, or undefined
   */
  static Geometry barChart(BarChart b, Sampler sampler, Cancellation cancellation) {
    return barChartOutcome(b, sampler, cancellation).geometry();
  }

  private static Outcome barChartOutcome(BarChart b, Sampler sampler, Cancellation cancellation) {
    Optional<double[]> data = column(b.values(), sampler, cancellation);
    if (data.isEmpty() || !b.categories().isEmpty() && b.categories().size() != data.get().length) {
      return Outcome.failed(Problem.DATA);
    }
    double[] v = data.get();
    List<Geometry.Bar> bars = new ArrayList<>();
    for (int k = 0; k < v.length; k++) {
      bars.add(new Geometry.Bar(k + 1 - BAR_WIDTH / 2, k + 1 + BAR_WIDTH / 2, v[k]));
    }
    return Outcome.of(new Geometry.Bars(bars, b.categories()));
  }

  /**
   * A box plot: its data's summary, the fences {@link #FENCE} interquartile ranges below the first
   * quartile and above the third, the whiskers to the furthest values within them, and the values
   * beyond them, at its height.
   *
   * @param b the box plot
   * @param sampler reads its data and its height
   * @param cancellation checked as it goes
   * @return the box, or undefined
   */
  static Geometry boxPlot(BoxPlot b, Sampler sampler, Cancellation cancellation) {
    return boxPlotOutcome(b, sampler, cancellation).geometry();
  }

  private static Outcome boxPlotOutcome(BoxPlot b, Sampler sampler, Cancellation cancellation) {
    Optional<double[]> data = column(b.data(), sampler, cancellation);
    if (data.isEmpty()) {
      return Outcome.failed(Problem.DATA);
    }
    double y = sampler.value(b.y(), Map.of());
    if (!Double.isFinite(y)) {
      return Outcome.failed(Problem.HEIGHT);
    }
    Summary s = Summary.of(data.get());
    double lower = s.q1() - FENCE * s.iqr();
    double upper = s.q3() + FENCE * s.iqr();
    double low = Double.POSITIVE_INFINITY;
    double high = Double.NEGATIVE_INFINITY;
    List<Double> outliers = new ArrayList<>();
    for (double x : data.get()) {
      if (x < lower || x > upper) {
        outliers.add(x);
      } else {
        low = Math.min(low, x);
        high = Math.max(high, x);
      }
    }
    outliers.sort(null);
    return Outcome.of(new Geometry.Box(s, low, high, outliers, y));
  }

  /**
   * A scatter plot: a point per pair, from a matrix of two columns or two lists of one length.
   *
   * @param p the scatter plot
   * @param sampler reads its data
   * @param cancellation checked as it goes
   * @return the points, or undefined
   */
  static Geometry scatter(ScatterPlot p, Sampler sampler, Cancellation cancellation) {
    return scatterOutcome(p, sampler, cancellation).geometry();
  }

  private static Outcome scatterOutcome(ScatterPlot p, Sampler sampler, Cancellation cancellation) {
    double[] xs;
    double[] ys;
    switch (p.points()) {
      case ScatterPlot.Pairs pairs -> {
        Optional<double[][]> t = sampler.table(pairs.matrix(), cancellation);
        if (t.isEmpty() || t.get().length != 2) {
          return Outcome.failed(Problem.DATA);
        }
        xs = t.get()[0];
        ys = t.get()[1];
      }
      case ScatterPlot.Columns c -> {
        Optional<double[]> x = column(c.xs(), sampler, cancellation);
        Optional<double[]> y = column(c.ys(), sampler, cancellation);
        if (x.isEmpty() || y.isEmpty() || x.get().length != y.get().length) {
          return Outcome.failed(Problem.DATA);
        }
        xs = x.get();
        ys = y.get();
      }
    }
    List<Geometry.Point> points = new ArrayList<>();
    for (int k = 0; k < xs.length; k++) {
      points.add(new Geometry.Point(xs[k], ys[k]));
    }
    return Outcome.of(new Geometry.Scatter(points, Summary.of(xs), Summary.of(ys)));
  }

  /**
   * Why a statistical plot is undefined.
   *
   * @param o the object
   * @param sampler the sampler its geometry was computed with
   * @param cancellation checked as it goes
   * @return the problem, or empty when the object is no statistical plot or is defined
   */
  public static Optional<Problem> problem(
      CanvasObject o, Sampler sampler, Cancellation cancellation) {
    if (o instanceof Histogram h) {
      return histogramOutcome(h, sampler, cancellation).problem();
    }
    if (o instanceof BarChart b) {
      return barChartOutcome(b, sampler, cancellation).problem();
    }
    if (o instanceof BoxPlot b) {
      return boxPlotOutcome(b, sampler, cancellation).problem();
    }
    if (o instanceof ScatterPlot p) {
      return scatterOutcome(p, sampler, cancellation).problem();
    }
    return Optional.empty();
  }

  /** A data set of one column, or empty. */
  private static Optional<double[]> column(Expr e, Sampler sampler, Cancellation cancellation) {
    return sampler.table(e, cancellation).filter(t -> t.length == 1).map(t -> t[0]);
  }
}
