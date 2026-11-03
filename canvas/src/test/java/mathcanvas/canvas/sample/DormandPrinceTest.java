package mathcanvas.canvas.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import mathcanvas.engine.Cancellation;
import mathcanvas.engine.CancelledException;
import org.junit.jupiter.api.Test;

/** The Dormand–Prince stepper against known solutions, and its stops. */
class DormandPrinceTest {

  private static final DormandPrince.Stop NEVER = (t, y) -> false;

  private static double[] last(DormandPrince.Trajectory r) {
    return r.ys()[r.ys().length - 1];
  }

  @Test
  void exponentialGrowthMatchesEToTheT() {
    DormandPrince.Trajectory r =
        DormandPrince.integrate(
            (t, y, d) -> d[0] = y[0],
            0,
            new double[] {1},
            1,
            1e-10,
            1e-10,
            1,
            10_000,
            NEVER,
            Cancellation.NEVER);

    assertThat(r.end()).isEqualTo(DormandPrince.End.REACHED);
    assertThat(r.ts()[r.ts().length - 1]).isEqualTo(1.0);
    assertThat(last(r)[0]).isCloseTo(Math.E, within(1e-6));
    // every accepted state on the solution
    for (int k = 0; k < r.ts().length; k++) {
      assertThat(r.ys()[k][0]).isCloseTo(Math.exp(r.ts()[k]), within(1e-6));
    }
  }

  @Test
  void theHarmonicOscillatorReturnsAfterOnePeriodForwardAndBack() {
    DormandPrince.Ode oscillator =
        (t, y, d) -> {
          d[0] = y[1];
          d[1] = -y[0];
        };
    DormandPrince.Trajectory ahead =
        DormandPrince.integrate(
            oscillator,
            0,
            new double[] {1, 0},
            2 * Math.PI,
            1e-10,
            1e-10,
            1,
            10_000,
            NEVER,
            Cancellation.NEVER);
    assertThat(last(ahead)[0]).isCloseTo(1, within(1e-6));
    assertThat(last(ahead)[1]).isCloseTo(0, within(1e-6));
    for (int k = 0; k < ahead.ts().length; k++) {
      assertThat(ahead.ys()[k][0]).isCloseTo(Math.cos(ahead.ts()[k]), within(1e-6));
      assertThat(ahead.ys()[k][1]).isCloseTo(-Math.sin(ahead.ts()[k]), within(1e-6));
    }

    // backwards in time, the same orbit the other way
    DormandPrince.Trajectory behind =
        DormandPrince.integrate(
            oscillator,
            0,
            new double[] {1, 0},
            -Math.PI / 2,
            1e-10,
            1e-10,
            1,
            10_000,
            NEVER,
            Cancellation.NEVER);
    assertThat(behind.end()).isEqualTo(DormandPrince.End.REACHED);
    assertThat(last(behind)[0]).isCloseTo(0, within(1e-6));
    assertThat(last(behind)[1]).isCloseTo(1, within(1e-6));
  }

  @Test
  void aSolutionRunningToInfinityIsABlowUpAtItsPole() {
    // y' = y², y(0) = 1: y = 1/(1 − t), infinite at t = 1
    DormandPrince.Trajectory r =
        DormandPrince.integrate(
            (t, y, d) -> d[0] = y[0] * y[0],
            0,
            new double[] {1},
            2,
            1e-9,
            1e-9,
            1,
            100_000,
            NEVER,
            Cancellation.NEVER);

    assertThat(r.end()).isEqualTo(DormandPrince.End.BLOW_UP);
    assertThat(r.ts()[r.ts().length - 1]).isCloseTo(1, within(1e-3)).isLessThan(1);
  }

  @Test
  void aDerivativeWithNoValueIsABlowUpAtItsEdge() {
    // y' = √(1 − t) has no real value past t = 1
    DormandPrince.Trajectory r =
        DormandPrince.integrate(
            (t, y, d) -> d[0] = Math.sqrt(1 - t),
            0,
            new double[] {0},
            2,
            1e-9,
            0,
            1,
            100_000,
            NEVER,
            Cancellation.NEVER);

    assertThat(r.end()).isEqualTo(DormandPrince.End.BLOW_UP);
    assertThat(r.ts()[r.ts().length - 1]).isCloseTo(1, within(1e-6));
    assertThat(last(r)[0]).isCloseTo(2.0 / 3, within(1e-6));
  }

  @Test
  void theStopAndTheBudgetEndIt() {
    DormandPrince.Trajectory stopped =
        DormandPrince.integrate(
            (t, y, d) -> d[0] = 1,
            0,
            new double[] {0},
            10,
            1e-9,
            0,
            0.1,
            10_000,
            (t, y) -> y[0] > 3,
            Cancellation.NEVER);
    assertThat(stopped.end()).isEqualTo(DormandPrince.End.STOPPED);
    assertThat(last(stopped)[0]).isGreaterThan(3).isLessThanOrEqualTo(3.1 + 1e-12);

    DormandPrince.Trajectory spent =
        DormandPrince.integrate(
            (t, y, d) -> d[0] = 1,
            0,
            new double[] {0},
            10,
            1e-9,
            0,
            0.1,
            20,
            NEVER,
            Cancellation.NEVER);
    assertThat(spent.end()).isEqualTo(DormandPrince.End.BUDGET);
    assertThat(spent.ts().length).isLessThanOrEqualTo(21);
  }

  @Test
  void aRequestToStopIsHonoured() {
    assertThatThrownBy(
            () ->
                DormandPrince.integrate(
                    (t, y, d) -> d[0] = 1,
                    0,
                    new double[] {0},
                    10,
                    1e-9,
                    0,
                    0.1,
                    10_000,
                    NEVER,
                    () -> true))
        .isInstanceOf(CancelledException.class);
  }
}
