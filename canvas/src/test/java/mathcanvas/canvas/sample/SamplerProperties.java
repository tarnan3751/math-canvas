package mathcanvas.canvas.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import mathcanvas.canvas.model.Parametrisation;
import mathcanvas.canvas.model.Polyline;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.Window;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.compile.Compiled;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Evaluator;
import mathcanvas.engine.eval.Settings;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;

/**
 * The adaptive sampler over random graphs (docs/reference/canvas.md, <b>Sampling</b>): every sample
 * of a random polynomial or trigonometric graph lies on the curve, the compiled value at its x
 * within 1e-9; no polyline crosses a pole of {@code c/(x − a)} or {@code c·tan x}, in windows up to
 * ±100 and for {@code c} from 0.01 to 1,000; and sampling stops at a domain's ends, within the
 * minimum step.
 */
class SamplerProperties {

  private static final Sampler SAMPLER = new Samplers().over(Environment.EMPTY, Settings.DEFAULT);

  @Provide
  Arbitrary<String> graphs() {
    Arbitrary<Integer> small = Arbitraries.integers().between(-5, 5);
    Arbitrary<String> polynomial =
        small
            .list()
            .ofMinSize(2)
            .ofMaxSize(6)
            .map(
                cs -> {
                  StringBuilder b = new StringBuilder("0");
                  for (int k = 0; k < cs.size(); k++) {
                    b.append(" + (").append(cs.get(k)).append(")*x^").append(k);
                  }
                  return b.toString();
                });
    Arbitrary<String> trig =
        Combinators.combine(small, small, small, small, small)
            .as(
                (a, b, c, d, e) ->
                    "(" + a + ")*sin((" + b + ")*x + " + c + ") + (" + d + ")*cos((" + e
                        + ")*x/2)");
    return Arbitraries.oneOf(polynomial, trig);
  }

  @Property(tries = 100)
  void everySampleLiesOnTheCurve(
      @ForAll("graphs") String body,
      @ForAll @DoubleRange(min = -20, max = 20) double centre,
      @ForAll @DoubleRange(min = 0.5, max = 40) double half) {
    Window w = new Window(centre - half, centre + half, -half, half);
    Compiled f =
        Compiled.compile(
            Evaluator.evaluate(SamplersTest.expr(body), Environment.EMPTY, false).value(),
            List.of(SamplersTest.X),
            Settings.DEFAULT);
    double lo = w.minX() - w.width() / 2;
    double hi = w.maxX() + w.width() / 2;
    for (Polyline p : SamplersTest.graph(SAMPLER, body, lo, hi, w)) {
      for (int k = 0; k < p.size(); k++) {
        double expected = f.eval(p.x(k));
        assertThat(p.x(k)).isEqualTo(p.t(k));
        assertThat(Math.abs(p.y(k) - expected))
            .as("%s at %s", body, p.x(k))
            .isLessThanOrEqualTo(1e-9 * Math.max(1, Math.abs(expected)));
        if (k > 0) {
          assertThat(p.t(k)).isGreaterThan(p.t(k - 1));
        }
      }
    }
  }

  @Property(tries = 200)
  void noPolylineCrossesThePoleOfAReciprocal(
      @ForAll @DoubleRange(min = -0.8, max = 0.8) double where,
      @ForAll @DoubleRange(min = 1, max = 100) double half,
      @ForAll @DoubleRange(min = -2, max = 3) double scale) {
    double a = where * half;
    double c = Math.pow(10, scale);
    String body =
        BigDecimal.valueOf(c).toPlainString()
            + "/(x - "
            + BigDecimal.valueOf(a).toPlainString()
            + ")";
    Window w = new Window(-half, half, -half, half);
    List<Polyline> out = SamplersTest.graph(SAMPLER, body, -2 * half, 2 * half, w);
    for (Polyline p : out) {
      for (int k = 1; k < p.size(); k++) {
        assertThat((p.x(k - 1) - a) * (p.x(k) - a)).as("%s at %s", body, p.x(k)).isPositive();
      }
    }
    if (c <= half * half / 4) {
      // both branches reach the window: within it at a quarter of the window from the pole
      assertThat(out).as(body).hasSizeGreaterThanOrEqualTo(2);
    }
  }

  @Property(tries = 200)
  void noPolylineCrossesAPoleOfTheTangent(
      @ForAll @DoubleRange(min = -30, max = 30) double centre,
      @ForAll @DoubleRange(min = 1, max = 100) double half,
      @ForAll @DoubleRange(min = -2, max = 3) double scale) {
    Window w = new Window(centre - half, centre + half, -half, half);
    String body = BigDecimal.valueOf(Math.pow(10, scale)).toPlainString() + " tan(x)";
    List<Polyline> out = SamplersTest.graph(SAMPLER, body, centre - 2 * half, centre + 2 * half, w);
    for (Polyline p : out) {
      for (int k = 1; k < p.size(); k++) {
        // the poles are where cos changes sign
        assertThat(Math.signum(Math.cos(p.x(k - 1))))
            .as("%s between %s and %s", body, p.x(k - 1), p.x(k))
            .isEqualTo(Math.signum(Math.cos(p.x(k))));
      }
    }
  }

  @Property(tries = 100)
  void samplingStopsAtTheDomainsEnds(
      @ForAll @DoubleRange(min = -5, max = 5) double a,
      @ForAll @DoubleRange(min = 0.1, max = 10) double reach) {
    String at = BigDecimal.valueOf(a).toPlainString();
    double lo = a - reach;
    double hi = a + reach;
    double step = (hi - lo) / (1 << Adaptive.DEPTH);
    Window w = new Window(lo, hi, -2 * reach - 4, 2 * reach + 4);
    List<Polyline> right = SamplersTest.graph(SAMPLER, "sqrt(x - " + at + ")", lo, hi, w);
    assertThat(right).hasSize(1);
    assertThat(right.getFirst().x(0) - a).isBetween(-1e-12, step);
    assertThat(right.getFirst().t(right.getFirst().size() - 1)).isEqualTo(hi);
    if (reach > 1) {
      List<Polyline> arc = SamplersTest.graph(SAMPLER, "acos(x - " + at + ")", lo, hi, w);
      assertThat(arc).hasSize(1);
      Polyline p = arc.getFirst();
      assertThat(p.x(0) - (a - 1)).isBetween(-1e-12, step);
      assertThat(a + 1 - p.x(p.size() - 1)).isBetween(-1e-12, step);
    }
    List<Polyline> left = SamplersTest.graph(SAMPLER, "sqrt(" + at + " - x)", lo, hi, w);
    Polyline last = left.getLast();
    assertThat(left.getFirst().t(0)).isEqualTo(lo);
    assertThat(a - last.x(last.size() - 1)).isBetween(-1e-12, step);
    assertThat(
            SAMPLER.sample(
                new Parametrisation.Graph(SamplersTest.expr("x^2"), SamplersTest.X),
                lo,
                hi,
                w,
                Cancellation.NEVER))
        .allSatisfy(p -> assertThat(p.t(0)).isGreaterThanOrEqualTo(lo));
  }
}
