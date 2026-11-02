package mathcanvas.app.persist;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.model.Anchor;
import mathcanvas.canvas.model.Approach;
import mathcanvas.canvas.model.Arrow;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.CriticalPoint;
import mathcanvas.canvas.model.Feature;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.ImplicitCurve;
import mathcanvas.canvas.model.InequalityRegion;
import mathcanvas.canvas.model.Intersection;
import mathcanvas.canvas.model.Line;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParamKind;
import mathcanvas.canvas.model.ParametricCurve;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.PolarCurve;
import mathcanvas.canvas.model.Projection;
import mathcanvas.canvas.model.RiemannSum;
import mathcanvas.canvas.model.Segment;
import mathcanvas.canvas.model.ShadedArea;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Text;
import mathcanvas.canvas.model.VectorSum;
import mathcanvas.engine.compile.Quadrature;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Parser;

/**
 * Canvas states for the codec's tests: expressions in canonical form, as the document keeps them,
 * with {@code f} and {@code g} user functions; and random valid states built with the model's
 * constructors.
 */
final class CanvasStates {

  private CanvasStates() {}

  /** Canonical parsed input, with {@code f} and {@code g} defined as functions. */
  static Expr expr(String text) {
    return Canonical.of(Parser.parseExpression(text, name -> name.equals("f") || name.equals("g")));
  }

  static Relation relation(String text) {
    return (Relation) expr(text);
  }

  static Style style(int colour) {
    return new Style(colour, 2, false, true, false);
  }

  /** One object of every kind and form. */
  static StoredCanvas everyKind() {
    Style s = Style.DEFAULT;
    ObjectId p7 = new ObjectId(7);
    ObjectId p8 = new ObjectId(8);
    List<CanvasObject> objects =
        List.of(
            new Slider(
                new ObjectId(1),
                s,
                "a",
                BigDecimal.ZERO,
                new BigDecimal("100"),
                new BigDecimal("0.10"),
                new BigDecimal("1.5")),
            new FunctionGraph(
                new ObjectId(2), style(1), expr("a x^2 + f(x)"), Symbol.of("x"), FunctionGraph.ALL),
            new ParametricCurve(
                new ObjectId(3),
                s,
                expr("cos(t)"),
                expr("sin(2t)"),
                Symbol.of("t"),
                Interval.of(expr("0"), expr("2π"), true, true)),
            new PolarCurve(
                new ObjectId(4),
                new Style(2, 1.5, true, false, true),
                expr("1 + cos θ"),
                Symbol.of("θ"),
                PolarCurve.TURN),
            new ImplicitCurve(new ObjectId(5), s, relation("x^2 + y^2 = 4")),
            new InequalityRegion(new ObjectId(6), s, relation("y < x/3")),
            new FreePoint(p7, s, 1.5, -2.0E-7),
            new PointOnPath(p8, s, new ObjectId(2), ParamKind.X, 0.5, Double.NaN, Double.NaN),
            new Intersection(
                new ObjectId(9),
                s,
                new ObjectId(2),
                new ObjectId(5),
                1,
                Optional.of(
                    new Anchor(
                        1.25,
                        1.5,
                        1.25,
                        Double.NaN,
                        true,
                        Optional.of(new Approach(1.2, 1.2, 1.44))))),
            new Intersection(
                new ObjectId(10),
                s,
                new ObjectId(2),
                new ObjectId(5),
                0,
                Optional.of(
                    new Anchor(
                        -1.0,
                        1.0,
                        -1.0,
                        Double.NaN,
                        false,
                        Optional.of(new Approach(Double.NaN, -1.0, 1.0))))),
            new Segment(new ObjectId(11), s, p7, p8),
            new Line(new ObjectId(12), s, new Line.Through(p7, p8)),
            new Line(new ObjectId(13), s, new Line.PointSlope(p8, expr("2a x"), Symbol.of("x"))),
            new Text(new ObjectId(14), s, 0, 3, "vertex \"V\""),
            new PointOnPath(
                new ObjectId(15), s, new ObjectId(5), ParamKind.NONE, Double.NaN, 2.0, 0.0),
            new FunctionGraph(
                new ObjectId(16),
                s,
                expr("sqrt(x)"),
                Symbol.of("x"),
                Interval.of(expr("0"), FunctionGraph.ALL.upper(), true, false)),
            Intersection.of(new ObjectId(17), s, new ObjectId(2), new ObjectId(5), 2),
            new Intersection(
                new ObjectId(18),
                s,
                new ObjectId(2),
                new ObjectId(5),
                3,
                Optional.of(new Anchor(0, 0, 0, Double.NaN, false, Optional.empty()))),
            new CriticalPoint(
                new ObjectId(20),
                style(3),
                new ObjectId(2),
                Feature.MIN,
                0,
                Optional.of(
                    new Anchor(
                        -0.5,
                        0.25,
                        -0.5,
                        Double.NaN,
                        false,
                        Optional.of(new Approach(-0.5, -0.5, 0.25))))),
            new PointOnPath(
                new ObjectId(21),
                s,
                new ObjectId(16),
                ParamKind.X,
                2.5,
                2.5,
                Math.sqrt(2.5),
                Optional.of(expr("a + 1"))),
            new ShadedArea(
                new ObjectId(22),
                style(4),
                new ObjectId(2),
                Optional.empty(),
                expr("0"),
                expr("a")),
            new ShadedArea(
                new ObjectId(23),
                s,
                new ObjectId(2),
                Optional.of(new ObjectId(16)),
                expr("1/2"),
                expr("2π")),
            new RiemannSum(
                new ObjectId(24),
                s,
                new ObjectId(16),
                expr("0"),
                expr("4"),
                expr("a"),
                Quadrature.Method.TRAPEZOID),
            new Arrow(new ObjectId(25), style(5), Optional.of(p7), new Arrow.At(Optional.of(p8))),
            new Arrow(new ObjectId(26), s, Optional.empty(), new Arrow.At(Optional.of(p7))),
            new Arrow(new ObjectId(27), s, Optional.of(p8), new Arrow.At(Optional.empty())),
            new Arrow(new ObjectId(28), s, Optional.empty(), new Arrow.Bound("u")),
            new VectorSum(new ObjectId(29), s, new ObjectId(25), new ObjectId(28)),
            new Projection(new ObjectId(30), s, new ObjectId(29), new ObjectId(26)));
    return new StoredCanvas(
        new CanvasState(objects, Map.of(), 31),
        Map.of(p7, "A", p8, "B", new ObjectId(15), "P_1"),
        new Viewport(-10, 10, -7.5, 7.5, 800, 600, true));
  }

  private static final List<String> ATOMS =
      List.of("x", "a", "b", "2", "1/3", "0.25", "π", "e", "f(x)", "g(x, a)", "3x", "-x");
  private static final List<String> FUNCTIONS =
      List.of("sin", "cos", "exp", "ln", "sqrt", "abs", "tan", "atan");
  private static final List<String> OPERATORS = List.of(" + ", " - ", "*", "/", "^");

  /** A random expression in canonical form, from text built of atoms, operators and functions. */
  static Expr randomExpr(Random r, int depth) {
    while (true) {
      try {
        return expr(randomText(r, depth));
      } catch (RuntimeException e) {
        // a text whose canonical form fails (such as 0^-1): draw another
      }
    }
  }

  private static String randomText(Random r, int depth) {
    if (depth == 0 || r.nextInt(3) == 0) {
      return ATOMS.get(r.nextInt(ATOMS.size()));
    }
    if (r.nextInt(3) == 0) {
      return FUNCTIONS.get(r.nextInt(FUNCTIONS.size())) + "(" + randomText(r, depth - 1) + ")";
    }
    return "("
        + randomText(r, depth - 1)
        + ")"
        + OPERATORS.get(r.nextInt(OPERATORS.size()))
        + "("
        + randomText(r, depth - 1)
        + ")";
  }

  /** A random relation in canonical form (one whose sides are equal folds to a truth value). */
  private static Relation randomRelation(Random r, Relation.Op op) {
    while (true) {
      if (Canonical.of(Relation.of(op, randomExpr(r, 2), randomExpr(r, 2))) instanceof Relation c
          && c.op() == op) {
        return c;
      }
    }
  }

  static double randomDouble(Random r) {
    return switch (r.nextInt(5)) {
      case 0 -> r.nextDouble() * 20 - 10;
      case 1 -> (r.nextDouble() - 0.5) * Math.pow(10, r.nextInt(600) - 300);
      case 2 -> List.of(0.0, -0.0, Double.MIN_VALUE, -Double.MAX_VALUE, 0.1).get(r.nextInt(5));
      case 3 -> r.nextInt(201) - 100;
      default -> Double.longBitsToDouble(r.nextLong() & 0x7fefffffffffffffL);
    };
  }

  private static double randomOrNaN(Random r) {
    return r.nextInt(4) == 0 ? Double.NaN : randomDouble(r);
  }

  private static BigDecimal randomDecimal(Random r) {
    return BigDecimal.valueOf(r.nextLong() % 1_000_000_000L, r.nextInt(10) - 3);
  }

  private static Interval randomDomain(Random r) {
    Expr lower = r.nextBoolean() ? FunctionGraph.ALL.lower() : randomExpr(r, 1);
    Expr upper = r.nextBoolean() ? FunctionGraph.ALL.upper() : randomExpr(r, 1);
    return Interval.of(
        lower,
        upper,
        !lower.equals(FunctionGraph.ALL.lower()),
        !upper.equals(FunctionGraph.ALL.upper()));
  }

  private static final List<String> SLIDERS = List.of("a", "b", "k", "m", "s", "w");
  private static final List<String> NAMES = List.of("A", "B", "C", "P", "Q", "R_1", "T");

  /** A random valid canvas: every kind, parents among the objects before, ids with gaps. */
  static StoredCanvas random(Random r) {
    List<CanvasObject> objects = new ArrayList<>();
    List<ObjectId> points = new ArrayList<>();
    List<ObjectId> paths = new ArrayList<>();
    List<ObjectId> vectors = new ArrayList<>();
    List<String> sliders = new ArrayList<>(SLIDERS);
    long next = 1;
    int count = r.nextInt(14);
    for (int k = 0; k < count; k++) {
      next += r.nextInt(4) == 0 ? 2 : 1;
      ObjectId id = new ObjectId(next - 1);
      Style style =
          new Style(
              r.nextInt(Style.COLOURS),
              0.25 + r.nextInt(16) / 4.0,
              r.nextBoolean(),
              r.nextBoolean(),
              r.nextBoolean());
      CanvasObject o =
          switch (r.nextInt(17)) {
            case 0 -> new FreePoint(id, style, randomDouble(r), randomDouble(r));
            case 1 ->
                paths.isEmpty()
                    ? new Text(id, style, randomDouble(r), randomDouble(r), "t" + k)
                    : r.nextBoolean()
                        ? new PointOnPath(
                            id,
                            style,
                            paths.get(r.nextInt(paths.size())),
                            ParamKind.values()[r.nextInt(4)],
                            randomDouble(r),
                            Double.NaN,
                            Double.NaN,
                            r.nextBoolean() ? Optional.empty() : Optional.of(randomExpr(r, 2)))
                        : new PointOnPath(
                            id,
                            style,
                            paths.get(r.nextInt(paths.size())),
                            ParamKind.NONE,
                            randomOrNaN(r),
                            randomDouble(r),
                            randomDouble(r));
            case 2 -> {
              if (paths.size() < 2) {
                yield new FreePoint(id, style, randomDouble(r), randomDouble(r));
              }
              int i = r.nextInt(paths.size());
              int j = (i + 1 + r.nextInt(paths.size() - 1)) % paths.size();
              Optional<Anchor> anchor =
                  r.nextBoolean()
                      ? Optional.empty()
                      : Optional.of(
                          new Anchor(
                              randomDouble(r),
                              randomDouble(r),
                              randomOrNaN(r),
                              randomOrNaN(r),
                              r.nextBoolean(),
                              r.nextBoolean()
                                  ? Optional.empty()
                                  : Optional.of(
                                      new Approach(
                                          randomOrNaN(r), randomDouble(r), randomDouble(r)))));
              yield new Intersection(id, style, paths.get(i), paths.get(j), r.nextInt(4), anchor);
            }
            case 3 ->
                new FunctionGraph(id, style, randomExpr(r, 3), Symbol.of("x"), randomDomain(r));
            case 4 ->
                new ParametricCurve(
                    id, style, randomExpr(r, 2), randomExpr(r, 2), Symbol.of("t"), randomDomain(r));
            case 5 -> new PolarCurve(id, style, randomExpr(r, 2), Symbol.of("θ"), randomDomain(r));
            case 6 -> new ImplicitCurve(id, style, randomRelation(r, Relation.Op.EQ));
            case 7 ->
                new InequalityRegion(
                    id,
                    style,
                    randomRelation(
                        r,
                        List.of(Relation.Op.LT, Relation.Op.LE, Relation.Op.GT, Relation.Op.GE)
                            .get(r.nextInt(4))));
            case 8 -> {
              if (sliders.isEmpty()) {
                yield new Text(id, style, randomDouble(r), randomDouble(r), "");
              }
              BigDecimal a = randomDecimal(r);
              BigDecimal b = randomDecimal(r);
              if (a.compareTo(b) == 0) {
                b = b.add(BigDecimal.ONE);
              }
              BigDecimal min = a.min(b);
              BigDecimal max = a.max(b);
              yield new Slider(
                  id,
                  style,
                  sliders.remove(r.nextInt(sliders.size())),
                  min,
                  max,
                  randomDecimal(r).abs().add(new BigDecimal("0.001")),
                  r.nextBoolean() ? min : max);
            }
            case 9 -> new Text(id, style, randomDouble(r), randomDouble(r), "½ \"x\" \\ ⟨1⟩");
            case 11 -> {
              List<ObjectId> graphs =
                  objects.stream()
                      .filter(g -> g instanceof FunctionGraph)
                      .map(g -> g.id())
                      .toList();
              if (graphs.isEmpty()) {
                yield new FreePoint(id, style, randomDouble(r), randomDouble(r));
              }
              Optional<Anchor> anchor =
                  r.nextBoolean()
                      ? Optional.empty()
                      : Optional.of(
                          new Anchor(
                              randomDouble(r),
                              randomDouble(r),
                              randomOrNaN(r),
                              Double.NaN,
                              r.nextBoolean(),
                              r.nextBoolean()
                                  ? Optional.empty()
                                  : Optional.of(
                                      new Approach(
                                          randomOrNaN(r), randomDouble(r), randomDouble(r)))));
              yield new CriticalPoint(
                  id,
                  style,
                  graphs.get(r.nextInt(graphs.size())),
                  Feature.values()[r.nextInt(Feature.values().length)],
                  r.nextInt(4),
                  anchor);
            }
            case 13, 14 -> {
              List<ObjectId> graphs =
                  objects.stream()
                      .filter(g -> g instanceof FunctionGraph)
                      .map(g -> g.id())
                      .toList();
              if (graphs.isEmpty()) {
                yield new FreePoint(id, style, randomDouble(r), randomDouble(r));
              }
              ObjectId graph = graphs.get(r.nextInt(graphs.size()));
              if (r.nextBoolean()) {
                yield new RiemannSum(
                    id,
                    style,
                    graph,
                    randomExpr(r, 1),
                    randomExpr(r, 1),
                    randomExpr(r, 1),
                    Quadrature.Method.values()[r.nextInt(4)]);
              }
              Optional<ObjectId> lower =
                  graphs.size() > 1 && r.nextBoolean()
                      ? graphs.stream().filter(g -> !g.equals(graph)).findFirst()
                      : Optional.empty();
              yield new ShadedArea(id, style, graph, lower, randomExpr(r, 1), randomExpr(r, 1));
            }
            case 15 -> {
              Optional<ObjectId> tail =
                  points.isEmpty() || r.nextBoolean()
                      ? Optional.empty()
                      : Optional.of(points.get(r.nextInt(points.size())));
              Optional<ObjectId> head =
                  points.isEmpty() || r.nextInt(3) == 0
                      ? Optional.empty()
                      : Optional.of(points.get(r.nextInt(points.size())));
              yield r.nextBoolean() || head.equals(tail)
                  ? new Arrow(id, style, tail, new Arrow.Bound(r.nextBoolean() ? "u" : "a"))
                  : new Arrow(id, style, tail, new Arrow.At(head));
            }
            case 16 -> {
              if (vectors.size() < 2) {
                yield new Arrow(id, style, Optional.empty(), new Arrow.At(Optional.empty()));
              }
              int i = r.nextInt(vectors.size());
              int j = (i + 1 + r.nextInt(vectors.size() - 1)) % vectors.size();
              yield r.nextBoolean()
                  ? new VectorSum(id, style, vectors.get(i), vectors.get(j))
                  : new Projection(id, style, vectors.get(i), vectors.get(j));
            }
            case 10 ->
                points.size() < 2
                    ? new FreePoint(id, style, randomDouble(r), randomDouble(r))
                    : new Segment(id, style, points.get(0), points.get(points.size() - 1));
            default ->
                points.isEmpty()
                    ? new FreePoint(id, style, 0, 0)
                    : points.size() >= 2 && r.nextBoolean()
                        ? new Line(
                            id,
                            style,
                            new Line.Through(points.get(points.size() - 2), points.getLast()))
                        : new Line(
                            id,
                            style,
                            new Line.PointSlope(
                                points.get(r.nextInt(points.size())),
                                randomExpr(r, 2),
                                Symbol.of("x")));
          };
      objects.add(o);
      if (o.isPoint()) {
        points.add(id);
      }
      if (o.isPath()) {
        paths.add(id);
      }
      if (o.isVector()) {
        vectors.add(id);
      }
    }
    Map<ObjectId, String> names = new HashMap<>();
    List<String> free = new ArrayList<>(NAMES);
    for (ObjectId p : points) {
      if (!free.isEmpty() && r.nextBoolean()) {
        names.put(p, free.remove(r.nextInt(free.size())));
      }
    }
    // centres to 10^15 and half-spans from 10^-12 of the centre (as deep as zoom goes) up to a
    // hundred times it, in wide and tall windows
    double x = (r.nextDouble() * 2 - 1) * Math.pow(10, r.nextInt(16));
    double y = (r.nextDouble() * 2 - 1) * Math.pow(10, r.nextInt(16));
    double w = Math.max(1, Math.max(Math.abs(x), Math.abs(y))) * Math.pow(10, r.nextInt(15) - 12);
    double h = w * (0.25 + r.nextDouble() * 2);
    return new StoredCanvas(
        new CanvasState(objects, Map.of(), next + r.nextInt(3)),
        names,
        new Viewport(
            x - w,
            x + w,
            y - h,
            y + h,
            100 + r.nextInt(2001),
            100 + r.nextInt(1500) + r.nextDouble(),
            r.nextBoolean()));
  }
}
