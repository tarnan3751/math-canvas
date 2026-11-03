package mathcanvas.engine.stat.numeric;

import java.util.Optional;
import mathcanvas.engine.provider.Distribute;
import mathcanvas.engine.provider.Distribute.Kind;
import mathcanvas.engine.provider.DistributionValue;
import mathcanvas.engine.provider.DistributionValue.Status;
import mathcanvas.engine.provider.Provider;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import org.hipparchus.exception.MathIllegalArgumentException;
import org.hipparchus.exception.MathIllegalStateException;

/**
 * The default {@link Distribute} provider, named {@code default}: {@link DistributionMath} in
 * {@code double}, over Hipparchus Core. It always answers, with the value or with the status that
 * says why there is none: a value beyond the {@code double} range or not a number is {@link
 * Status#NOT_FINITE}, and so is a special function's non-zero value that underflows to 0 ({@code
 * beta(6000.0, 6000.0)}, {@code gamma(-200.5)}); an iteration of Hipparchus that does not converge
 * {@link Status#NO_CONVERGENCE}.
 */
public record HipparchusDistributions() implements Provider<Distribute, DistributionValue> {

  @Override
  public String name() {
    return "default";
  }

  @Override
  public Optional<DistributionValue> apply(Distribute input, Context ctx, Steps steps) {
    ctx.cancellation().check();
    double[] p = input.parameters();
    double[] at = input.points();
    double v;
    try {
      v =
          switch (input.kind()) {
            case DENSITY -> DistributionMath.density(input.law(), p, at[0]);
            case PROBABILITY -> DistributionMath.probability(input.law(), p, at[0], at[1]);
            case INVERSE -> DistributionMath.inverse(input.law(), p, at[0], false);
            case UPPER_INVERSE -> DistributionMath.inverse(input.law(), p, at[0], true);
            case VALUE -> DistributionMath.value(input.law(), p, at[0]);
          };
    } catch (MathIllegalStateException | MathIllegalArgumentException failed) {
      return Optional.of(DistributionValue.failed(Status.NO_CONVERGENCE));
    }
    // a special function's non-zero value below the double range is beyond it, as one above is
    boolean underflow =
        input.kind() == Kind.VALUE && v == 0 && DistributionMath.nonZero(input.law(), p, at[0]);
    return Optional.of(
        Double.isFinite(v) && !underflow
            ? new DistributionValue(Status.OK, v)
            : DistributionValue.failed(Status.NOT_FINITE));
  }
}
