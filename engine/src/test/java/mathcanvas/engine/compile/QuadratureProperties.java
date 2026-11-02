package mathcanvas.engine.compile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Random;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.compile.Quadrature.Estimate;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.EvaluationException;
import mathcanvas.engine.eval.Evaluator;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprArbitraries;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Reporter;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.statistics.Statistics;

/**
 * The two quadratures agree: Gauss–Kronrod in {@code double} over the compiled integrand, and the
 * double-exponential rule at 20 digits through {@code numeric(integrate(…))}, on the shared
 * generator's elementary expressions in {@code x} over random intervals in {@code [−3, 3]}, within
 * {@code 1e-8} of the value or {@code 1e-10} of the integral of the absolute value. An integrand
 * that does not compile, or where either rule gives no value (a sample undefined or not finite, no
 * convergence), is skipped; the skips are counted and at most {@link #SKIPPED_PER_COMPARED} per
 * comparison.
 */
class QuadratureProperties {

  private static final Symbol X = Symbol.of("x");

  /** The most integrands skipped for each one compared. */
  static final int SKIPPED_PER_COMPARED = 1;

  private static int compared;
  private static int skipped;

  @BeforeProperty
  void begin() {
    compared = 0;
    skipped = 0;
  }

  @AfterProperty
  void end(Reporter reporter) {
    reporter.publishValue("integrands", compared + " compared, " + skipped + " skipped");
    assertThat(skipped)
        .as("skipped against compared")
        .isLessThanOrEqualTo(SKIPPED_PER_COMPARED * compared);
  }

  @Property(tries = 100)
  void theDoubleAndThePrecisionRulesAgree(@ForAll Random random) {
    Expr raw =
        CompiledProperties.rename(
            ExprArbitraries.elementaryExprs().generator(1000).next(random).value(),
            Map.of("a", X, "b", X, "y", X));
    double a = Math.round((random.nextDouble() * 6 - 3) * 100) / 100.0;
    double b = Math.round((random.nextDouble() * 6 - 3) * 100) / 100.0;
    if (Math.abs(b - a) < 0.1) {
      b = a + 0.5;
    }
    String outcome = compare(raw, a, b);
    Statistics.label("integrands").collect(outcome);
    if (outcome.equals("compared")) {
      compared++;
    } else {
      skipped++;
    }
  }

  private static String compare(Expr raw, double a, double b) {
    Expr value;
    Compiled f;
    Compiled abs;
    try {
      value = Evaluator.evaluate(raw, Environment.EMPTY, false).value();
      f = Compiled.compile(value, List.of(X), Settings.DEFAULT);
      Expr absolute =
          Evaluator.evaluate(new Apply(Symbol.of("abs"), List.of(value)), Environment.EMPTY, false)
              .value();
      abs = Compiled.compile(absolute, List.of(X), Settings.DEFAULT);
    } catch (EvaluationException | UnsupportedException refused) {
      return "not compiled";
    }
    Estimate quick = Quadrature.integrate(f, a, b, Cancellation.NEVER);
    Estimate l1 = Quadrature.integrate(abs, a, b, Cancellation.NEVER);
    if (!quick.converged() || !l1.converged()) {
      return "double: no value";
    }
    Expr call =
        new Apply(
            Symbol.of("numeric"),
            List.of(
                Integral.definite(raw, X, new Num(Real.of(a)), new Num(Real.of(b))), Num.of(20)));
    Expr precise;
    try {
      precise = Evaluator.evaluate(call, Environment.EMPTY, false).value();
    } catch (EvaluationException refused) {
      return "precision: refused";
    }
    if (!(precise instanceof Num n && n.value() instanceof RealNumeric r)) {
      return "precision: no value";
    }
    double exact = r.toDouble();
    assertThat(Math.abs(quick.value() - exact))
        .as("%s on [%s, %s]: %s against %s", value, a, b, quick.value(), exact)
        // the L1 norm is a magnitude: with the bounds reversed the rule returns it negated
        .isLessThanOrEqualTo(Math.max(1e-8 * Math.abs(exact), 1e-10 * Math.abs(l1.value())));
    return "compared";
  }
}
