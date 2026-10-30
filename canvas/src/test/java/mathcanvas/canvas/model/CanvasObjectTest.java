package mathcanvas.canvas.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import mathcanvas.engine.expr.Symbol;
import org.junit.jupiter.api.Test;

class CanvasObjectTest {

  private static final Style S = Style.DEFAULT;
  private static final Symbol X = Symbol.of("x");
  private static final Symbol T = Symbol.of("t");

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  @Test
  void aGraphReadsItsFreeNamesAndNotItsVariable() {
    FunctionGraph g =
        new FunctionGraph(id(1), S, Parse.expr("a sin(x) + b"), X, Parse.interval("0", "c"));
    assertThat(g.reads()).containsExactly("a", "b", "c");
    assertThat(g.parents()).isEmpty();
  }

  @Test
  void builtinsAndConstantsAreNotRead() {
    FunctionGraph g =
        new FunctionGraph(id(1), S, Parse.expr("pi e^x + sqrt(x)"), X, FunctionGraph.ALL);
    assertThat(g.reads()).isEmpty();
  }

  @Test
  void aWorksheetFunctionIsReadByItsName() {
    FunctionGraph g = new FunctionGraph(id(1), S, Parse.expr("f(x) + 1"), X, FunctionGraph.ALL);
    assertThat(g.reads()).containsExactly("f");
  }

  @Test
  void curvesReadTheirExpressionsAndDomains() {
    ParametricCurve p =
        new ParametricCurve(
            id(1), S, Parse.expr("r cos(t)"), Parse.expr("r sin(t)"), T, Parse.interval("0", "b"));
    assertThat(p.reads()).containsExactly("b", "r");
    PolarCurve q = new PolarCurve(id(2), S, Parse.expr("k θ"), Symbol.of("θ"), PolarCurve.TURN);
    assertThat(q.reads()).containsExactly("k");
    ImplicitCurve c = new ImplicitCurve(id(3), S, Parse.relation("x^2 + y^2 = r^2"));
    assertThat(c.reads()).containsExactly("r");
    InequalityRegion r = new InequalityRegion(id(4), S, Parse.relation("y < m x"));
    assertThat(r.reads()).containsExactly("m");
  }

  @Test
  void aTangentReadsItsSlopeButNotItsVariable() {
    Line l = new Line(id(3), S, new Line.PointSlope(id(1), Parse.expr("2 a x"), X));
    assertThat(l.reads()).containsExactly("a");
    assertThat(l.parents()).containsExactly(id(1));
    Line m = new Line(id(4), S, new Line.Through(id(1), id(2)));
    assertThat(m.parents()).containsExactly(id(1), id(2));
    assertThat(m.reads()).isEmpty();
  }

  @Test
  void parentsAreListedInOrder() {
    assertThat(new Segment(id(3), S, id(2), id(1)).parents()).containsExactly(id(2), id(1));
    assertThat(Intersection.of(id(3), S, id(2), id(1), 0).parents()).containsExactly(id(2), id(1));
    assertThat(new PointOnPath(id(3), S, id(1), ParamKind.X, 0, Double.NaN, Double.NaN).parents())
        .containsExactly(id(1));
  }

  @Test
  void kindsAreClassified() {
    List<CanvasObject> points =
        List.of(
            new FreePoint(id(1), S, 0, 0),
            new PointOnPath(id(2), S, id(5), ParamKind.T, 0, Double.NaN, Double.NaN),
            Intersection.of(id(3), S, id(5), id(6), 0));
    assertThat(points).allMatch(CanvasObject::isPoint).noneMatch(CanvasObject::isPath);
    assertThat(new Segment(id(4), S, id(1), id(2)).isPath()).isTrue();
    assertThat(new Text(id(4), S, 0, 0, "hi").isPath()).isFalse();
  }

  @Test
  void invalidDefinitionsAreRefused() {
    assertThatThrownBy(() -> new ObjectId(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Style(8, 1, false, true, true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Style(0, 0, false, true, true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FreePoint(id(1), S, Double.NaN, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Segment(id(2), S, id(1), id(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Intersection.of(id(2), S, id(1), id(1), 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Intersection.of(id(2), S, id(1), id(3), -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ImplicitCurve(id(1), S, Parse.relation("y < x")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new InequalityRegion(id(1), S, Parse.relation("y = x")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new PointOnPath(id(2), S, id(1), ParamKind.X, Double.NaN, Double.NaN, Double.NaN))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PointOnPath(id(2), S, id(1), ParamKind.X, 0, 1, Double.NaN))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sliderNumbersAreExactAndChecked() {
    Slider s =
        new Slider(
            id(1),
            S,
            "a",
            new BigDecimal("0"),
            new BigDecimal("1"),
            new BigDecimal("0.1"),
            new BigDecimal("0.30"));
    assertThat(s.withValue(new BigDecimal("0.3")).value()).isEqualByComparingTo("0.30");
    assertThatThrownBy(() -> s.withValue(new BigDecimal("1.01")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new Slider(
                    id(1), S, "a", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new Slider(
                    id(1), S, " ", BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sliderNumbersIgnoreTheirScale() {
    Slider a =
        new Slider(
            id(1),
            S,
            "a",
            new BigDecimal("0"),
            new BigDecimal("1.0"),
            new BigDecimal("0.10"),
            new BigDecimal("0.5"));
    Slider b =
        new Slider(
            id(1),
            S,
            "a",
            new BigDecimal("0.00"),
            BigDecimal.ONE,
            new BigDecimal("0.1"),
            new BigDecimal("0.50"));
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
  }

  @Test
  void aSliderNamesAVariable() {
    assertThat(Slider.isVariableName("a")).isTrue();
    assertThat(Slider.isVariableName("x_1")).isTrue();
    assertThat(Slider.isVariableName("θ")).isTrue();
    assertThat(Slider.isVariableName("x")).isTrue();
    for (String bad :
        List.of("", " ", "2x", "a b", "a+b", "sin", "solve", "pi", "e", "x₁", "Reals", "if")) {
      assertThat(Slider.isVariableName(bad)).as(bad).isFalse();
    }
    assertThatThrownBy(
            () ->
                new Slider(
                    id(1),
                    S,
                    "sin",
                    BigDecimal.ZERO,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void withStyleKeepsTheDefinition() {
    Style red = new Style(1, 3, true, true, true);
    FreePoint p = new FreePoint(id(1), S, 1, 2);
    assertThat(p.withStyle(red)).isEqualTo(new FreePoint(id(1), red, 1, 2));
  }
}
