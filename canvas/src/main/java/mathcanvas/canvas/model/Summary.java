package mathcanvas.canvas.model;

import java.util.Arrays;

/**
 * The five-number summary of a sample with its size, mean and standard deviation, in {@code double}
 * as every plotted quantity is: what a histogram's and a box plot's geometry carry and the
 * statistics panel shows. The quartiles are the medians of the lower and the upper half, the median
 * itself left out of both when the size is odd (the TI convention, as the engine's {@code
 * quartiles}); the standard deviation is the sample's, over {@code n − 1}, NaN for one value. The
 * mean and the median do not overflow on finite values; the standard deviation is infinite when the
 * squared deviations overflow.
 *
 * @param n the number of values, at least 1
 * @param mean the mean
 * @param median the median
 * @param q1 the first quartile
 * @param q3 the third quartile
 * @param min the least value
 * @param max the greatest value
 * @param stdev the sample standard deviation
 */
public record Summary(
    int n, double mean, double median, double q1, double q3, double min, double max, double stdev) {

  /**
   * The summary of a sample.
   *
   * @param values the values, finite, at least one; not changed
   * @return the summary
   * @throws IllegalArgumentException if there are none
   */
  public static Summary of(double[] values) {
    if (values.length == 0) {
      throw new IllegalArgumentException("a summary has a value");
    }
    double[] v = values.clone();
    Arrays.sort(v);
    int n = v.length;
    double sum = 0;
    for (double x : v) {
      sum += x;
    }
    double mean = sum / n;
    if (!Double.isFinite(mean)) {
      // the sum overflowed: the mean of values near the largest double is a sum of fractions
      mean = 0;
      for (double x : v) {
        mean += x / n;
      }
    }
    double squares = 0;
    for (double x : v) {
      squares += (x - mean) * (x - mean);
    }
    int half = n / 2;
    double q1 = n == 1 ? v[0] : median(v, 0, half);
    double q3 = n == 1 ? v[0] : median(v, n - half, n);
    return new Summary(
        n,
        mean,
        median(v, 0, n),
        q1,
        q3,
        v[0],
        v[n - 1],
        n > 1 ? Math.sqrt(squares / (n - 1)) : Double.NaN);
  }

  /**
   * The interquartile range.
   *
   * @return {@code q3 − q1}
   */
  public double iqr() {
    return q3 - q1;
  }

  /** The median of the sorted values from {@code from} to {@code to}, exclusive. */
  private static double median(double[] sorted, int from, int to) {
    int n = to - from;
    int mid = from + n / 2;
    if (n % 2 == 1) {
      return sorted[mid];
    }
    double m = (sorted[mid - 1] + sorted[mid]) / 2;
    // two values near the largest double overflow their sum, not their halves'
    return Double.isFinite(m) ? m : sorted[mid - 1] / 2 + sorted[mid] / 2;
  }
}
