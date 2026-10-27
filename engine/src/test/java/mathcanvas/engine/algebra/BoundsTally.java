package mathcanvas.engine.algebra;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.statistics.Statistics;

/**
 * The tries of a property run that met the algebra's bounds ({@link AlgebraLimitException}) and so
 * had nothing to check, capped as the ill-conditioned skips are: at most one in {@link #DIVISOR} of
 * the tries checked, so that a gcd that started throwing on ordinary inputs fails the property.
 */
final class BoundsTally {

  /** The most tries past the bounds a run may skip: one in this many of those checked (5%). */
  static final int DIVISOR = 20;

  private static final String LABEL = "beyond the algebra's bounds";

  private static int checked;
  private static int beyond;

  private BoundsTally() {}

  /** Starts a property run's tally. */
  static void begin() {
    checked = 0;
    beyond = 0;
  }

  /** Counts a try that was checked. */
  static void checked() {
    Statistics.label(LABEL).collect(false);
    checked++;
  }

  /** Counts a try past the algebra's bounds, skipped. */
  static void beyond() {
    Statistics.label(LABEL).collect(true);
    beyond++;
  }

  /** Asserts that the tries skipped are at most one in {@link #DIVISOR} of those checked. */
  static void end() {
    assertThat((long) beyond * DIVISOR)
        .as("tries beyond the algebra's bounds: %d of %d checked", beyond, checked)
        .isLessThanOrEqualTo(checked);
  }
}
