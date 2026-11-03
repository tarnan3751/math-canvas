package mathcanvas.canvas.sample;

import static mathcanvas.canvas.sample.SamplersTest.env;
import static mathcanvas.canvas.sample.SamplersTest.expr;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import mathcanvas.canvas.model.BarChart;
import mathcanvas.canvas.model.BoxPlot;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.Histogram;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Recomputes;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.ScatterPlot;
import mathcanvas.canvas.model.Statistics;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Summary;
import mathcanvas.canvas.model.Window;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Settings;
import org.junit.jupiter.api.Test;

/** Histograms, bar charts, box plots and scatter plots with the real sampler. */
class StatisticsKindsTest {

  private static final Style S = Style.DEFAULT;
  private static final Window W = new Window(-10, 10, -10, 10);

  private static Sampler sampler(Environment env) {
    return new Samplers().over(env, Settings.DEFAULT);
  }

  private static Geometry geometry(CanvasObject o, Environment env) {
    CanvasState s =
        CanvasState.EMPTY
            .add(i -> o)
            .recomputeAll(sampler(env), W, Recomputes.standard(), Cancellation.NEVER);
    return s.geometryOf(o.id());
  }

  private static final ObjectId ID = new ObjectId(1);

  private static Histogram histogram(String data, String width, String start) {
    return new Histogram(
        ID,
        S,
        expr(data),
        Optional.ofNullable(width).map(SamplersTest::expr),
        Optional.ofNullable(start).map(SamplersTest::expr));
  }

  private static List<Double> counts(Geometry g) {
    return ((Geometry.Bins) g).bars().stream().map(Geometry.Bar::height).toList();
  }

  @Test
  void aHistogramCountsEachBinAValueOnAnEdgeInTheUpperOne() {
    // bins of 2 from 0: 2, 4 and 6 are on edges and go up, 6 to a bin of its own
    Geometry g = geometry(histogram("[0, 1, 2, 2, 3, 4, 5, 6]", "2", "0"), Environment.EMPTY);
    Geometry.Bins b = (Geometry.Bins) g;
    assertThat(counts(b)).containsExactly(2.0, 3.0, 2.0, 1.0);
    assertThat(b.width()).isEqualTo(2);
    assertThat(b.bars().getFirst().x0()).isEqualTo(0);
    assertThat(b.bars().getLast().x1()).isEqualTo(8);
    assertThat(b.summary().n()).isEqualTo(8);
    // a rounded edge counts as the edge: 0.3 by 0.1 from 0 is in the fourth bin
    assertThat(
            counts(geometry(histogram("[0, 0.1, 0.2, 0.3, 0.35]", "0.1", "0"), Environment.EMPTY)))
        .containsExactly(1.0, 1.0, 1.0, 2.0);
    // values below the start are in no bin
    assertThat(counts(geometry(histogram("[-5, 1, 2]", "1", "1"), Environment.EMPTY)))
        .containsExactly(1.0, 1.0);
  }

  @Test
  void aHistogramWithNoWidthTakesSturgesCountOverTheRangeFromTheLeast() {
    // ten values: ⌈log₂ 10⌉ + 1 = 5 bins of (10 − 1)/5 from 1, the last holding 10 on its edge
    Geometry.Bins b =
        (Geometry.Bins)
            geometry(histogram("[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]", null, null), Environment.EMPTY);
    assertThat(b.bars()).hasSize(5);
    assertThat(b.width()).isCloseTo(1.8, within(1e-12));
    assertThat(b.bars().getFirst().x0()).isEqualTo(1);
    assertThat(counts(b)).containsExactly(2.0, 2.0, 2.0, 2.0, 2.0);
    // one value repeated: one bin of width 1
    Geometry.Bins one =
        (Geometry.Bins) geometry(histogram("[3, 3, 3]", null, null), Environment.EMPTY);
    assertThat(one.width()).isEqualTo(1);
    assertThat(counts(one)).containsExactly(3.0);
  }

  @Test
  void aHistogramReadsACellAndRecomputesWhenItChanges() {
    Histogram h = histogram("data", "1", null);
    assertThat(h.reads()).containsExactly("data");
    assertThat(counts(geometry(h, env("data", "[1, 1, 2]")))).containsExactly(2.0, 1.0);
    assertThat(counts(geometry(h, env("data", "[1, 2, 3, 3]")))).containsExactly(1.0, 1.0, 2.0);
    // a cell that is not a list of numbers leaves it undefined, and says so
    Sampler symbolic = sampler(env("data", "[1, a]"));
    assertThat(geometry(h, env("data", "[1, a]"))).isEqualTo(Geometry.UNDEFINED);
    assertThat(Statistics.problem(h, symbolic, Cancellation.NEVER))
        .contains(Statistics.Problem.DATA);
    assertThat(geometry(h, env("data", "[[1, 2], [3, 4]]"))).isEqualTo(Geometry.UNDEFINED);
  }

  @Test
  void aHistogramsBinsMustBeNumbersAndFew() {
    Sampler s = sampler(Environment.EMPTY);
    for (Histogram h :
        List.of(
            histogram("[1, 2]", "0", null),
            histogram("[1, 2]", "-1", null),
            histogram("[1, 2]", "a", null),
            histogram("[1, 2]", "1", "3"),
            histogram("[0, 5000]", "1", null))) {
      assertThat(geometry(h, Environment.EMPTY)).isEqualTo(Geometry.UNDEFINED);
      assertThat(Statistics.problem(h, s, Cancellation.NEVER)).contains(Statistics.Problem.BINS);
    }
    assertThat(Statistics.problem(histogram("[1, 2]", "1", null), s, Cancellation.NEVER)).isEmpty();
  }

  @Test
  void theSpinnersWidthsAreOneTwoAndFiveTimesAPowerOfTen() {
    assertThat(Histogram.steppedWidth(1.8, 1)).isEqualByComparingTo("2");
    assertThat(Histogram.steppedWidth(1.8, -1)).isEqualByComparingTo("1");
    assertThat(Histogram.steppedWidth(2, 1)).isEqualByComparingTo("5");
    assertThat(Histogram.steppedWidth(5, 1)).isEqualByComparingTo("10");
    assertThat(Histogram.steppedWidth(1, -1)).isEqualByComparingTo("0.5");
    assertThat(Histogram.steppedWidth(0.2, -1)).isEqualByComparingTo("0.1");
    assertThat(Histogram.steppedWidth(30, 2)).isEqualByComparingTo("100");
    Histogram h = histogram("[1]", null, null).withBinWidth(new BigDecimal("0.50"));
    assertThat(h.binWidth()).contains(mathcanvas.engine.expr.Canonical.of(expr("1/2")));
  }

  @Test
  void aBoxPlotHasItsSummaryWhiskersToTheFencesAndOutliers() {
    // 1..9 and 30: Q1 = 3, median 5.5, Q3 = 8, IQR 5, fences −4.5 and 15.5
    BoxPlot b = new BoxPlot(ID, S, expr("[30, 1, 2, 3, 4, 5, 6, 7, 8, 9]"), expr("2"));
    Geometry.Box g = (Geometry.Box) geometry(b, Environment.EMPTY);
    Summary s = g.summary();
    assertThat(s.n()).isEqualTo(10);
    assertThat(s.min()).isEqualTo(1);
    assertThat(s.max()).isEqualTo(30);
    assertThat(s.median()).isEqualTo(5.5);
    assertThat(s.q1()).isEqualTo(3);
    assertThat(s.q3()).isEqualTo(8);
    assertThat(s.mean()).isCloseTo(7.5, within(1e-12));
    assertThat(g.low()).isEqualTo(1);
    assertThat(g.high()).isEqualTo(9);
    assertThat(g.outliers()).containsExactly(30.0);
    assertThat(g.y()).isEqualTo(2);
    // an odd count leaves the median out of both halves (TI's quartiles)
    Summary odd = Summary.of(new double[] {1, 2, 3, 4, 5, 6, 7});
    assertThat(odd.q1()).isEqualTo(2);
    assertThat(odd.median()).isEqualTo(4);
    assertThat(odd.q3()).isEqualTo(6);
    assertThat(odd.stdev()).isCloseTo(Math.sqrt(28.0 / 6), within(1e-12));
    assertThat(Summary.of(new double[] {4}).stdev()).isNaN();
    // values near the largest double: the mean and the median do not overflow
    Summary huge = Summary.of(new double[] {1e308, 1e308});
    assertThat(huge.mean()).isEqualTo(1e308);
    assertThat(huge.median()).isEqualTo(1e308);
    assertThat(huge.stdev()).isZero();
    Summary wide = Summary.of(new double[] {1e200, -1e200});
    assertThat(wide.mean()).isZero();
    assertThat(wide.median()).isZero();
    assertThat(wide.stdev()).isInfinite();
    // the default height is under the axis; one that is not a number is a problem
    assertThat(
            ((Geometry.Box)
                    geometry(
                        new BoxPlot(ID, S, expr("[1, 2]"), BoxPlot.DEFAULT_Y), Environment.EMPTY))
                .y())
        .isEqualTo(-1);
    BoxPlot nowhere = new BoxPlot(ID, S, expr("[1, 2]"), expr("a"));
    assertThat(geometry(nowhere, Environment.EMPTY)).isEqualTo(Geometry.UNDEFINED);
    assertThat(Statistics.problem(nowhere, sampler(Environment.EMPTY), Cancellation.NEVER))
        .contains(Statistics.Problem.HEIGHT);
  }

  @Test
  void aScatterPlotHasAPointPerPairFromAMatrixOrTwoLists() {
    ScatterPlot pairs =
        new ScatterPlot(ID, S, new ScatterPlot.Pairs(expr("[[1, 2], [3, 5], [4, 4]]")));
    Geometry.Scatter g = (Geometry.Scatter) geometry(pairs, Environment.EMPTY);
    assertThat(g.points())
        .containsExactly(
            new Geometry.Point(1, 2), new Geometry.Point(3, 5), new Geometry.Point(4, 4));
    assertThat(g.x().mean()).isCloseTo(8.0 / 3, within(1e-12));
    assertThat(g.y().max()).isEqualTo(5);
    Environment env = env("x", "[1, 2, 3]").with("y", env("y", "[1, 4, 9]").definition("y").get());
    ScatterPlot columns = new ScatterPlot(ID, S, new ScatterPlot.Columns(expr("x"), expr("y")));
    assertThat(columns.reads()).containsExactlyInAnyOrder("x", "y");
    assertThat(((Geometry.Scatter) geometry(columns, env)).points())
        .containsExactly(
            new Geometry.Point(1, 1), new Geometry.Point(2, 4), new Geometry.Point(3, 9));
    // lists of two lengths, or a matrix of three columns, are no pairs
    Environment ragged = env.with("y", env("y", "[1, 4]").definition("y").get());
    assertThat(geometry(columns, ragged)).isEqualTo(Geometry.UNDEFINED);
    assertThat(
            geometry(
                new ScatterPlot(ID, S, new ScatterPlot.Pairs(expr("[[1, 2, 3]]"))),
                Environment.EMPTY))
        .isEqualTo(Geometry.UNDEFINED);
  }

  @Test
  void aBarChartHasABarPerValueAtOneTwoAndSoOnWithItsCategories() {
    BarChart b = new BarChart(ID, S, List.of("a", "b", "c"), expr("[3, -1, 2]"));
    Geometry.Bars g = (Geometry.Bars) geometry(b, Environment.EMPTY);
    assertThat(g.bars()).hasSize(3);
    assertThat(g.bars().get(1).x0()).isCloseTo(1.6, within(1e-12));
    assertThat(g.bars().get(1).x1()).isCloseTo(2.4, within(1e-12));
    assertThat(g.bars().get(1).height()).isEqualTo(-1);
    assertThat(g.labels()).containsExactly("a", "b", "c");
    assertThat(geometry(new BarChart(ID, S, List.of("a"), expr("[3, -1, 2]")), Environment.EMPTY))
        .isEqualTo(Geometry.UNDEFINED);
    assertThat(
            ((Geometry.Bars)
                    geometry(new BarChart(ID, S, List.of(), expr("[3]")), Environment.EMPTY))
                .labels())
        .isEmpty();
  }

  @Test
  void theStatisticalPlotsAreNotTransformedAndHaveNoParents() {
    for (CanvasObject o :
        Set.of(
            histogram("[1]", null, null),
            new BarChart(ID, S, List.of(), expr("[1]")),
            new BoxPlot(ID, S, expr("[1]"), BoxPlot.DEFAULT_Y),
            new ScatterPlot(ID, S, new ScatterPlot.Pairs(expr("[[1, 2]]"))))) {
      assertThat(o.parents()).isEmpty();
      assertThat(o.isPath() || o.isPoint()).isFalse();
      assertThat(mathcanvas.canvas.model.Transform.shapeOf(o)).isEmpty();
    }
  }
}
