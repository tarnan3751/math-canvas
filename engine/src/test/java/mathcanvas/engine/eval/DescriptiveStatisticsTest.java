package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The descriptive statistics ({@code docs/language.md} §14.25): exact values on exact data, the
 * order statistics by the TI and R conventions, counts, decimal and constant data, the call left
 * for a symbol, and the errors.
 */
class DescriptiveStatisticsTest {

  @Test
  void momentsAreExact() {
    assertThat(shown("mean([1, 2, 4])")).isEqualTo("7/3");
    assertThat(shown("variance([1, 2, 4])")).isEqualTo("7/3");
    assertThat(shown("pvariance([1, 2, 4])")).isEqualTo("14/9");
    assertThat(shown("stdev([1, 2, 4])")).isEqualTo("√21/3");
    assertThat(shown("pstdev([1, 2, 4])")).isEqualTo("√14/3");
    assertThat(shown("skewness([1, 2, 4])")).isEqualTo("10√21/49");
    assertThat(shown("kurtosis([1, 2, 3, 4])")).isEqualTo("-6/5");
    assertThat(shown("cov([1, 2, 3], [2, 4, 7])")).isEqualTo("5/2");
    assertThat(shown("cov([[1, 2], [2, 4], [3, 7]])")).isEqualTo("5/2");
    assertThat(shown("cor([1, 2, 3], [2, 4, 7])")).isEqualTo("5√57/38");
    assertThat(shown("zscore(4, [1, 2, 4])")).isEqualTo("5√21/21");
    assertThat(shown("zscore(x, 2, 3)")).isEqualTo("(x - 2)/3");
  }

  @Test
  void orderStatisticsFollowTheirConventions() {
    assertThat(shown("median([3, 1, 2])")).isEqualTo("2");
    assertThat(shown("median([1, 2, 3, 4])")).isEqualTo("5/2");
    // TI: the median is left out of the halves for an odd count
    assertThat(shown("quartiles([1, 2, 3, 4, 5, 6, 7])")).isEqualTo("⟨2, 4, 6⟩");
    assertThat(shown("quartiles([1, 2, 3, 4, 5, 6, 7, 8])")).isEqualTo("⟨5/2, 9/2, 13/2⟩");
    assertThat(shown("iqr([1, 2, 3, 4, 5, 6, 7])")).isEqualTo("4");
    // R's type 7: h = (n − 1)p/100
    assertThat(shown("percentile([1, 2, 3, 4, 5], 25)")).isEqualTo("2");
    assertThat(shown("percentile([1, 2, 3, 4], 50)")).isEqualTo("5/2");
    assertThat(shown("percentile([4, 1, 3], 100)")).isEqualTo("4");
    assertThat(shown("percentile([1, 2, 3, 4], 0)")).isEqualTo("1");
    assertThat(shown("range([3, 9, 1])")).isEqualTo("8");
    assertThat(shown("mode([1, 2, 2, 3])")).isEqualTo("2");
    assertThat(shown("mode([1, 1, 2, 2, 3])")).isEqualTo("{1, 2}");
    assertThat(shown("mode([3, 1, 2])")).isEqualTo("{1, 2, 3}");
    assertThat(shown("sort([3, 1.5, 2])")).isEqualTo("⟨1.5, 2, 3⟩");
    assertThat(shown("cumsum([1, 2, 3])")).isEqualTo("⟨1, 3, 6⟩");
    assertThat(shown("frequencies([2, 1, 2])")).isEqualTo("[[1, 1], [2, 2]]");
  }

  @Test
  void summaryIsAListOfRelations() {
    assertThat(shown("summary([1, 2, 4])"))
        .isEqualTo(
            "[n = 3, mean = 7/3, median = 2, min = 1, Q1 = 1, Q3 = 4, max = 4, stdev = √21/3]");
  }

  @Test
  void countsWeighTheValues() {
    assertThat(shown("mean([1, 2], [3, 1])")).isEqualTo("5/4");
    assertThat(shown("median([1, 2], [3, 1])")).isEqualTo("1");
    assertThat(shown("variance([1, 2], [3, 1])")).isEqualTo("1/4");
    assertThat(shown("frequencies([1, 2, 1], [1, 2, 3])")).isEqualTo("[[1, 4], [2, 2]]");
    assertThat(shown("mean([1, 2, 3], [0, 0, 2])")).isEqualTo("3");
  }

  @Test
  void decimalDataIsRoundedOnce() {
    assertThat(shown("mean([1.5, 2.5])")).isEqualTo("2.0");
    assertThat(shown("mean([0.1, 0.2])")).isEqualTo("0.15");
    assertThat(shown("numeric(stdev([1.0, 2, 4]), 10)")).isEqualTo("1.527525232");
    assertThat(shown("summary([1.0, 2, 4])"))
        .startsWith("[n = 3, mean = 2.333333333333333333333333333333333,");
  }

  @Test
  void exactConstantsAreData() {
    assertThat(shown("mean([π, 1])")).isEqualTo("(π + 1)/2");
    assertThat(shown("median([π, 3, 4])")).isEqualTo("π");
    assertThat(shown("sort([4, π, √2])")).isEqualTo("⟨√2, π, 4⟩");
    // a tie by value is a tie
    assertThat(shown("mode([√2, 2/√2, 1])")).isEqualTo("√2");
  }

  @Test
  void aSymbolLeavesTheCall() {
    assertThat(shown("mean([1, a])")).isEqualTo("mean(⟨1, a⟩)");
    assertThat(shown("variance(d)")).isEqualTo("variance(d)");
    assertThat(shown("percentile([1, 2], p)")).isEqualTo("percentile(⟨1, 2⟩, p)");
  }

  @Test
  void wrongDataIsAnError() {
    assertThat(error("mean([])")).isEqualTo("mean needs at least 1 value");
    assertThat(error("variance([1])")).isEqualTo("variance needs at least 2 values");
    assertThat(error("zscore(1, 2, 0)")).isEqualTo("zscore needs σ > 0");
    assertThat(error("zscore(1, 2, -1/2)")).isEqualTo("zscore needs σ > 0");
    assertThat(shown("zscore(x, m, s)")).isEqualTo("(-m + x)/s");
    assertThat(error("kurtosis([1, 2, 3])")).isEqualTo("kurtosis needs at least 4 values");
    assertThat(error("mean(5)")).isEqualTo("mean needs data: a list or vector of real numbers");
    assertThat(error("mean([[1, 2], [3, 4]])"))
        .isEqualTo("mean needs data: a list or vector of real numbers");
    assertThat(error("mean([1, i])")).isEqualTo("mean needs real data");
    assertThat(error("mean([1, 2], [1])")).isEqualTo("mean needs one count per value");
    assertThat(error("mean([1, 2], [1, -1])"))
        .isEqualTo("mean needs counts that are non-negative integers");
    assertThat(error("percentile([1, 2], 150)")).isEqualTo("percentile needs 0 ≤ p ≤ 100");
    assertThat(error("cov([1, 2], [1, 2, 3])"))
        .isEqualTo("cov needs two lists of the same length or a matrix of two columns");
  }
}
