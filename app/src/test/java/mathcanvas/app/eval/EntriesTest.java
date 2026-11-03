package mathcanvas.app.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import mathcanvas.app.document.Command;
import mathcanvas.app.document.DocumentState;
import mathcanvas.canvas.model.Arrow;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.ContourPlot;
import mathcanvas.canvas.model.FieldGrid;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ImplicitCurve;
import mathcanvas.canvas.model.InequalityRegion;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParamKind;
import mathcanvas.canvas.model.Parametrisation;
import mathcanvas.canvas.model.Path;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.SlopeField;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.VectorField;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Definition;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

class EntriesTest {

  private static final Environment ENV =
      Environment.EMPTY
          .with("a", new Definition.Value(Parser.parseExpression("5", Names.NONE)))
          .with("u", new Definition.Value(Parser.parseExpression("[3, a/2]", Names.NONE)))
          .with("w", new Definition.Value(Parser.parseExpression("⟨1, 2, 3⟩", Names.NONE)))
          .with("z", new Definition.Value(Parser.parseExpression("[i, 1]", Names.NONE)))
          .with(
              "f",
              new Definition.Function(
                  List.of(Symbol.of("t")), Parser.parseExpression("t^2", Names.NONE)));

  private static Entries.Read read(String text) {
    return Entries.read(text, Optional.empty(), ENV, Set.of(), Cancellation.NEVER);
  }

  private static CanvasObject made(String text) {
    Entries.Read r = read(text);
    assertThat(r.error()).isNull();
    DocumentState s = r.command().apply(DocumentState.EMPTY);
    return s.canvas().objects().getFirst();
  }

  private static Expr canonical(String text) {
    return Canonical.of(Parser.parseExpression(text, Names.NONE), Canonical.DEFAULT_CONTEXT);
  }

  @Test
  void anEquationInYOfXIsAGraphStoredCanonicalAndUnevaluated() {
    FunctionGraph g = (FunctionGraph) made("y = x + 2x");
    assertThat(g.body()).isEqualTo(canonical("3x"));
    assertThat(g.variable()).isEqualTo(Symbol.of("x"));

    // a reads the worksheet's 5 only when it is sampled
    assertThat(((FunctionGraph) made("y = a x")).body()).isEqualTo(canonical("a x"));
    // a function the worksheet defines is called
    assertThat(((FunctionGraph) made("y = f(x)")).body()).isInstanceOf(Apply.class);
  }

  @Test
  void otherRelationsAreCurvesAndRegions() {
    assertThat(made("x^2 + y^2 = 1")).isInstanceOf(ImplicitCurve.class);
    assertThat(made("y = y^2 + x")).isInstanceOf(ImplicitCurve.class);
    assertThat(made("y < x")).isInstanceOf(InequalityRegion.class);
    assertThat(made("x^2 + y^2 >= 4")).isInstanceOf(InequalityRegion.class);
  }

  @Test
  void aVectorIsAFreePointNamedNextOrAsWritten() {
    Entries.Read r = read("[1/2, a]");
    DocumentState s = r.command().apply(DocumentState.EMPTY);
    assertThat(s.canvas().objects().getFirst())
        .isEqualTo(new FreePoint(new ObjectId(1), Style.DEFAULT, 0.5, 5));
    assertThat(s.names()).containsValue("A");

    DocumentState b = read("B = ⟨pi, 0⟩").command().apply(DocumentState.EMPTY);
    assertThat(((FreePoint) b.canvas().objects().getFirst()).x()).isEqualTo(Math.PI);
    assertThat(b.names()).containsValue("B");
  }

  @Test
  void aSliderRowTakesAnExactDecimal() {
    Slider s =
        new Slider(
            new ObjectId(4),
            Style.DEFAULT,
            "a",
            BigDecimal.ZERO,
            BigDecimal.TEN,
            BigDecimal.ONE,
            BigDecimal.ONE);

    assertThat(Entries.read("3", Optional.of(s), ENV, Set.of(), Cancellation.NEVER).command())
        .isEqualTo(new Command.SetSlider(s.id(), new BigDecimal(3)));
    assertThat(Entries.read("a = 1/4", Optional.of(s), ENV, Set.of(), Cancellation.NEVER).command())
        .isEqualTo(new Command.SetSlider(s.id(), new BigDecimal("0.25")));
    assertThat(Entries.read("pi", Optional.of(s), ENV, Set.of(), Cancellation.NEVER).error())
        .isEqualTo("a slider's value is a decimal number");
  }

  @Test
  void aNameHoldingATwoVectorIsAnArrowBoundToItFromTheOrigin() {
    assertThat(made("u"))
        .isEqualTo(
            new Arrow(new ObjectId(1), Style.DEFAULT, Optional.empty(), new Arrow.Bound("u")));
    for (String not : new String[] {"w", "z", "a", "q"}) {
      assertThat(read(not).error()).as(not).isEqualTo(not + " is not a vector of two real numbers");
    }
    assertThat(Entries.read("u", Optional.empty(), ENV, Set.of("u"), Cancellation.NEVER).error())
        .startsWith("a vector follows variables, not a point");
  }

  @Test
  void aBoundArrowsRowRebindsItAndKeepsItsTail() {
    Arrow a =
        new Arrow(
            new ObjectId(3), Style.DEFAULT, Optional.of(new ObjectId(1)), new Arrow.Bound("v"));
    assertThat(Entries.read("u", Optional.of(a), ENV, Set.of(), Cancellation.NEVER).command())
        .isEqualTo(
            new Command.ReplaceObject(
                new Arrow(a.id(), a.style(), Optional.of(new ObjectId(1)), new Arrow.Bound("u"))));
    assertThat(Entries.read("y = x", Optional.of(a), ENV, Set.of(), Cancellation.NEVER).error())
        .isEqualTo("a vector's row takes the name of a variable holding a vector");
    Arrow points =
        new Arrow(
            new ObjectId(3),
            Style.DEFAULT,
            Optional.of(new ObjectId(1)),
            new Arrow.At(Optional.empty()));
    assertThat(Entries.read("u", Optional.of(points), ENV, Set.of(), Cancellation.NEVER).error())
        .isEqualTo("a vector's row takes the name of a variable holding a vector");
  }

  @Test
  void whatIsNotAnObjectIsRefusedWithAReason() {
    assertThat(read("1 +").error()).isNotBlank();
    assertThat(read("x + 1").error())
        .isEqualTo("this does not describe a graph, a curve, a region or a point");
    assertThat(read("[1]").error()).isEqualTo("a point is [x, y] with two real numbers");
    assertThat(read("[1, i]").error()).isEqualTo("a point is [x, y] with two real numbers");
  }

  @Test
  void theCurvesOwnVariablesNameNoPoint() {
    for (String name : List.of("x", "y", "t", "θ")) {
      assertThat(read(name + " = [1, 2]").error())
          .isEqualTo(
              name + " is a variable of the canvas's curves and cannot name a slider or a point");
    }
  }

  @Test
  void anUnnamedPointIsNamedWhenItsCommandRuns() {
    Command first = read("[1, 2]").command();
    Command second = read("[3, 4]").command();

    DocumentState s = second.apply(first.apply(DocumentState.EMPTY));

    assertThat(s.names()).containsValues("A", "B");
  }

  @Test
  void aPointOnAPathTakesItsParameter() {
    ObjectId id = new ObjectId(2);
    PointOnPath p = new PointOnPath(id, Style.DEFAULT, new ObjectId(1), ParamKind.X, 1, 1, 1);
    Geometry graph =
        new Geometry.Curve(
            new Path.Parametrised(
                new Parametrisation.Graph(
                    Parser.parseExpression("x^2", Names.NONE), Symbol.of("x")),
                -10,
                10,
                ParamKind.X),
            List.of());
    var sampler =
        new TestSampling()
            .over(ENV, mathcanvas.engine.eval.Settings.DEFAULT, 640, 480, Cancellation.NEVER);

    assertThat(
            Entries.onPath("5/2", p, graph, ENV, java.util.Set.of("P"), sampler, Cancellation.NEVER)
                .command())
        .isEqualTo(new Command.MovePoint(id, 2.5, 6.25, 2.5));
    assertThat(
            Entries.onPath(
                    "x = f(2)", p, graph, ENV, java.util.Set.of("P"), sampler, Cancellation.NEVER)
                .command())
        .isEqualTo(new Command.MovePoint(id, 4, 16, 4));
    assertThat(
            Entries.onPath("11", p, graph, ENV, java.util.Set.of("P"), sampler, Cancellation.NEVER)
                .error())
        .isEqualTo("the path has no point at that parameter");
    assertThat(
            Entries.onPath(
                    "[1, 2]", p, graph, ENV, java.util.Set.of("P"), sampler, Cancellation.NEVER)
                .error())
        .isEqualTo("a point on a path takes its parameter, a real number");
    assertThat(
            Entries.onPath("1 +", p, graph, ENV, java.util.Set.of("P"), sampler, Cancellation.NEVER)
                .error())
        .isNotNull();

    Geometry line =
        new Geometry.Curve(
            new Path.Parametrised(
                new Parametrisation.Linear(1, 1, 2, 0),
                Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY,
                ParamKind.LINE),
            List.of());
    PointOnPath q = new PointOnPath(id, Style.DEFAULT, new ObjectId(1), ParamKind.LINE, 0, 1, 1);
    assertThat(
            Entries.onPath("3", q, line, ENV, java.util.Set.of("P"), sampler, Cancellation.NEVER)
                .command())
        .isEqualTo(new Command.MovePoint(id, 7, 1, 3));
    assertThat(
            Entries.onPath("3", p, line, ENV, java.util.Set.of("P"), sampler, Cancellation.NEVER)
                .error())
        .isEqualTo("the path has no point at that parameter");

    // an expression reading a point's name is refused: no edge would order the recompute
    assertThat(
            Entries.onPath(
                    "a + nth(P, 1)",
                    p,
                    graph,
                    ENV.with(
                        "P", new Definition.Value(Parser.parseExpression("[1, 2]", Names.NONE))),
                    java.util.Set.of("P"),
                    sampler,
                    Cancellation.NEVER)
                .error())
        .isEqualTo("a point on a path follows sliders and definitions, not another point");
    // an expression reading a definition places the point at it; a number takes it off again
    PointOnPath at =
        p.withAt(java.util.Optional.of(Canonical.of(Parser.parseExpression("a/2", Names.NONE))));
    assertThat(
            Entries.onPath(
                    "x = a/2", p, graph, ENV, java.util.Set.of("P"), sampler, Cancellation.NEVER)
                .command())
        .isEqualTo(new Command.ReplaceObject(at));
    assertThat(
            Entries.onPath("2", at, graph, ENV, java.util.Set.of("P"), sampler, Cancellation.NEVER)
                .command())
        .isEqualTo(
            new Command.ReplaceObject(
                at.withAt(java.util.Optional.empty())
                    .withParameter(ParamKind.X, 2)
                    .landedAt(2, 4)));
  }

  @Test
  void aPointAtAnExpressionIsNotMovedByADrag() {
    ObjectId id = new ObjectId(2);
    PointOnPath p =
        new PointOnPath(
            id,
            Style.DEFAULT,
            new ObjectId(1),
            ParamKind.X,
            1,
            1,
            1,
            java.util.Optional.of(Parser.parseExpression("t", Names.NONE)));
    DocumentState s =
        new Command.AddObject(i -> p)
            .apply(
                new Command.AddObject(
                        i ->
                            new mathcanvas.canvas.model.FunctionGraph(
                                i,
                                Style.DEFAULT,
                                Parser.parseExpression("x^2", Names.NONE),
                                Symbol.of("x"),
                                mathcanvas.canvas.model.FunctionGraph.ALL))
                    .apply(DocumentState.EMPTY));
    assertThat(new Command.MovePoint(id, 3, 9, 3).apply(s)).isEqualTo(s);
  }

  @Test
  void aSlopeFieldIsEnteredByItsEquationOrByName() {
    SlopeField f = (SlopeField) made("dy/dx = x - y");
    assertThat(f.slope()).isEqualTo(canonical("x - y"));
    assertThat(f.density()).isEqualTo(FieldGrid.DEFAULT_DENSITY);
    assertThat(made("slopefield(x - y)")).isEqualTo(f);
    // its row's text reads back
    assertThat(made("y' = x - y")).isEqualTo(f);
    assertThat(made("y′ = x - y")).isEqualTo(f);
    // a worksheet function is still called inside
    assertThat(((SlopeField) made("slopefield(f(x))")).slope()).isInstanceOf(Apply.class);
  }

  @Test
  void aVectorOfExpressionsInXAndYIsAVectorField() {
    VectorField f = (VectorField) made("[-y, x]");
    assertThat(f.p()).isEqualTo(canonical("-y"));
    assertThat(f.q()).isEqualTo(canonical("x"));
    assertThat(made("vectorfield([-y, x])")).isEqualTo(f);
    // its row's text reads back
    assertThat(made("F = ⟨-y, x⟩")).isEqualTo(f);
    // a vector of numbers stays a point
    assertThat(made("[1, a]")).isInstanceOf(FreePoint.class);
    assertThat(read("vectorfield(x)").error()).contains("vector field");
  }

  @Test
  void aContourPlotTakesACountOrValues() {
    ContourPlot c = (ContourPlot) made("contours(x^2 + y^2, 5)");
    assertThat(c.function()).isEqualTo(canonical("x^2 + y^2"));
    assertThat(c.levels()).isEqualTo(new ContourPlot.Count(5));
    assertThat(((ContourPlot) made("contours(x y, a + 1)")).levels())
        .isEqualTo(new ContourPlot.Count(6));
    assertThat(((ContourPlot) made("contours(x y, [1, a/2])")).levels())
        .isEqualTo(new ContourPlot.Values(List.of(canonical("1"), canonical("a/2"))));
    assertThat(((ContourPlot) made("contours of x y")).levels())
        .isEqualTo(new ContourPlot.Count(Entries.DEFAULT_LEVELS));
    for (String bad :
        List.of(
            "contours(x y, 0)", "contours(x y, 31)", "contours(x y, 2.5)", "contours(x y, [i])")) {
      assertThat(read(bad).error()).as(bad).contains("1 to 30");
    }
  }

  @Test
  void aFieldsRowKeepsItsDensityAndAContourPlotsItsLevels() {
    Optional<CanvasObject> field =
        Optional.of(new SlopeField(new ObjectId(3), Style.DEFAULT, canonical("x"), 33));
    Entries.Read r = Entries.read("y' = y", field, ENV, Set.of(), Cancellation.NEVER);
    assertThat(((Command.ReplaceObject) r.command()).replacement())
        .isEqualTo(new SlopeField(new ObjectId(3), Style.DEFAULT, canonical("y"), 33));

    ContourPlot.Levels levels = new ContourPlot.Values(List.of(canonical("2")));
    Optional<CanvasObject> plot =
        Optional.of(new ContourPlot(new ObjectId(4), Style.DEFAULT, canonical("x"), levels));
    Entries.Read q = Entries.read("contours of x + y", plot, ENV, Set.of(), Cancellation.NEVER);
    assertThat(((Command.ReplaceObject) q.command()).replacement())
        .isEqualTo(new ContourPlot(new ObjectId(4), Style.DEFAULT, canonical("x + y"), levels));
  }

  @Test
  void aPointsRowRefusesAFieldAndASlopeFieldTakesOneSlope() {
    Optional<CanvasObject> point = Optional.of(new FreePoint(new ObjectId(2), Style.DEFAULT, 1, 1));
    for (String field : List.of("[x, y]", "A = [-y, x]", "dy/dx = x", "contours(x y, 3)")) {
      assertThat(Entries.read(field, point, ENV, Set.of(), Cancellation.NEVER).error())
          .as(field)
          .isEqualTo("this object cannot become a point");
    }
    assertThat(read("slopefield(x, y)").error())
        .isEqualTo("a slope field is slopefield(f), f in x and y");
    assertThat(read("slopefield()").error())
        .isEqualTo("a slope field is slopefield(f), f in x and y");
  }
}
