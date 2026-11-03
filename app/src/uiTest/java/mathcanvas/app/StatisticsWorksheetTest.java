package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The statistics' results typeset in the worksheet: a summary's list of relations, exact, and a
 * test's list with its numeric p-value, which carries the numeric badge.
 */
class StatisticsWorksheetTest extends WorksheetHarness {

  @Test
  void aSummaryAndATestAreTypeset() throws Exception {
    enter("summary([1, 2, 4])");
    waitForOutput(
        0, "[n = 3, mean = 7/3, median = 2, min = 1, Q1 = 1, Q3 = 4, max = 4, stdev = √21/3]");
    assertThat(get(() -> window.cellViews().get(0).lookup(".numeric-badge"))).isNull();
    enter("ttest([4, 5, 6, 7, 8], 5)");
    waitForOutput(1, "[t = √2, df = 4, p = 0.2301996410804988]");
    assertThat(get(() -> window.cellViews().get(1).lookup(".numeric-badge"))).isNotNull();
    enter("linreg([1, 2, 3], [1, 2, 2])");
    waitForOutput(2, "[y = x/2 + 2/3, r² = 3/4, r = √3/2]");
    screenshot("worksheet-statistics.png");
  }
}
