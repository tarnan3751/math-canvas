package mathcanvas.app.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.canvas.model.BarChart;
import mathcanvas.canvas.model.BoxPlot;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.Histogram;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ScatterPlot;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Summary;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

/** The statistics panel's lines: one per data cell a plot reads, from the plot's geometry. */
class StatisticsSummariesTest {

  private static Expr expr(String text) {
    return Parser.parseExpression(text, Names.NONE);
  }

  private static final Summary DATA = Summary.of(new double[] {1, 2, 2, 3, 4, 4, 4, 5, 6, 10});
  private static final Summary SMALL = Summary.of(new double[] {0.000012345, 2_000_000});

  @Test
  void eachDataCellAPlotReadsHasOneLineFromItsFirstPlot() {
    CanvasState s =
        CanvasState.EMPTY
            .add(
                i ->
                    new Histogram(
                        i, Style.DEFAULT, expr("data"), Optional.empty(), Optional.empty()))
            .add(i -> new BoxPlot(i, Style.DEFAULT, expr("data"), BoxPlot.DEFAULT_Y))
            .add(i -> new BoxPlot(i, Style.DEFAULT, expr("[1, 2]"), BoxPlot.DEFAULT_Y))
            .add(i -> new ScatterPlot(i, Style.DEFAULT, new ScatterPlot.Pairs(expr("m"))))
            .add(
                i ->
                    new ScatterPlot(
                        i, Style.DEFAULT, new ScatterPlot.Columns(expr("xs"), expr("[1, 2]"))))
            .add(i -> new BarChart(i, Style.DEFAULT, List.of(), expr("v")));
    Map<ObjectId, Geometry> g =
        Map.of(
            new ObjectId(1),
            new Geometry.Bins(List.of(), 1, DATA),
            new ObjectId(2),
            new Geometry.Box(SMALL, 0, 0, List.of(), -1),
            new ObjectId(3),
            new Geometry.Box(SMALL, 0, 0, List.of(), -1),
            new ObjectId(4),
            new Geometry.Scatter(List.of(), DATA, Summary.of(new double[] {7})),
            new ObjectId(5),
            new Geometry.Scatter(List.of(), SMALL, DATA),
            new ObjectId(6),
            new Geometry.Bars(List.of(), List.of()));
    List<StatisticsSummaries.Line> lines =
        StatisticsSummaries.of(new CanvasState(s.objects(), g, s.nextId()));
    assertThat(lines)
        .extracting(StatisticsSummaries.Line::data)
        .containsExactly("data", "m (x)", "m (y)", "xs");
    assertThat(lines.getFirst().text())
        .isEqualTo("n = 10, mean 4.1, median 4, Q1 2, Q3 5, min 1, max 10, stdev 2.558");
    assertThat(lines.get(2).text()).endsWith("stdev undefined");
    assertThat(lines.get(3).text()).contains("min 1.234E-5, max 2E6");
    // an undefined plot has none
    assertThat(StatisticsSummaries.of(s)).isEmpty();
  }

  @Test
  void numbersAreWrittenToFourSignificantDigits() {
    assertThat(StatisticsSummaries.number(2.0 / 3)).isEqualTo("0.6667");
    assertThat(StatisticsSummaries.number(-1234.5)).isEqualTo("-1235");
    assertThat(StatisticsSummaries.number(0)).isEqualTo("0");
    assertThat(StatisticsSummaries.number(5)).isEqualTo("5");
    assertThat(StatisticsSummaries.number(Double.NaN)).isEqualTo("undefined");
    assertThat(StatisticsSummaries.number(Double.POSITIVE_INFINITY)).isEqualTo("undefined");
    assertThat(StatisticsSummaries.number(Double.NEGATIVE_INFINITY)).isEqualTo("undefined");
    // the notation is the rounded number's
    assertThat(StatisticsSummaries.number(999999.7)).isEqualTo("1E6");
    assertThat(StatisticsSummaries.number(-999999.7)).isEqualTo("-1E6");
    assertThat(StatisticsSummaries.number(999949)).isEqualTo("999900");
    assertThat(StatisticsSummaries.number(0.000099996)).isEqualTo("0.0001");
    assertThat(StatisticsSummaries.number(0.000099994)).isEqualTo("9.999E-5");
  }

  @Test
  void dataNearTheLargestDoubleHasASummaryLine() {
    // two values near the largest double: no overflow in the mean or the median
    assertThat(text(new double[] {1e308, 1e308}))
        .isEqualTo(
            "n = 2, mean 1E308, median 1E308, Q1 1E308, Q3 1E308, min 1E308, max 1E308, stdev 0");
    // the squared deviations overflow: the standard deviation is undefined, not a crash
    assertThat(text(new double[] {1e200, -1e200}))
        .isEqualTo(
            "n = 2, mean 0, median 0, Q1 -1E200, Q3 1E200, min -1E200, max 1E200, stdev"
                + " undefined");
  }

  private static String text(double[] values) {
    CanvasState s =
        CanvasState.EMPTY.add(i -> new BoxPlot(i, Style.DEFAULT, expr("data"), BoxPlot.DEFAULT_Y));
    Map<ObjectId, Geometry> g =
        Map.of(new ObjectId(1), new Geometry.Box(Summary.of(values), 0, 0, List.of(), -1));
    return StatisticsSummaries.of(new CanvasState(s.objects(), g, s.nextId())).getFirst().text();
  }
}
