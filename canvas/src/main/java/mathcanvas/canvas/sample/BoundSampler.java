package mathcanvas.canvas.sample;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import mathcanvas.canvas.model.ImplicitCurve;
import mathcanvas.canvas.model.Parametrisation;
import mathcanvas.canvas.model.Polyline;
import mathcanvas.canvas.model.Sample;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.Window;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.compile.Compiled;
import mathcanvas.engine.compile.UnsupportedException;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.EvaluationException;
import mathcanvas.engine.eval.Evaluator;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;

/**
 * A {@link Sampler} bound to one environment. An expression is evaluated by the engine in the
 * environment with its own variables unbound, then compiled through the document's memo; this
 * sampler keeps the result per expression as written, so a path's point is never evaluated twice.
 * Where evaluation fails or the value cannot be compiled, the expression is undefined everywhere:
 * NaN, an undefined point, no polylines.
 *
 * <p>The sampler is bound to a job's cancellation as well: every evaluation and compilation of an
 * expression runs under it, so {@link #value} and {@link #point}, which take no token, stop with
 * the job, and the calls that take one ({@link #sample}, {@link #contour}, {@link #region}) stop on
 * either, their loops included. An evaluation that stops leaves nothing in the memo.
 */
final class BoundSampler implements Sampler {

  private static final Symbol COS = Symbol.of("cos");
  private static final Symbol SIN = Symbol.of("sin");

  /**
   * The fraction of the window added on each side of a region's sampling window, as {@code
   * Recomputes} widens a contour's: the rings' edges along it lie off the screen, so stroking a
   * ring draws only the boundary.
   */
  static final double MARGIN = 0.5;

  private static final List<Symbol> PLANE = List.of(ImplicitCurve.X, ImplicitCurve.Y);

  private final Samplers samplers;
  private final Environment environment;
  private final Settings settings;
  private final double pixelWidth;
  private final double pixelHeight;
  private final Cancellation cancellation;
  private final Map<Raw, Optional<Compiled>> functions = new ConcurrentHashMap<>();
  private final Map<Parametrisation.Expressed, Optional<Traced>> paths = new ConcurrentHashMap<>();

  BoundSampler(
      Samplers samplers,
      Environment environment,
      Settings settings,
      double pixelWidth,
      double pixelHeight,
      Cancellation cancellation) {
    this.samplers = samplers;
    this.cancellation = cancellation;
    this.environment = environment;
    this.settings = settings;
    this.pixelWidth = pixelWidth;
    this.pixelHeight = pixelHeight;
  }

  @Override
  public double value(Expr e, Map<Symbol, Double> locals) {
    List<Symbol> arguments = new ArrayList<>(locals.keySet());
    arguments.sort((a, b) -> a.name().compareTo(b.name()));
    Optional<Compiled> f = function(e, arguments, cancellation);
    if (f.isEmpty()) {
      return Double.NaN;
    }
    double[] values = new double[arguments.size()];
    for (int k = 0; k < values.length; k++) {
      values[k] = locals.get(arguments.get(k));
    }
    return f.get().eval(values);
  }

  @Override
  public Sample point(Parametrisation.Expressed p, double t) {
    Optional<Traced> path = traced(p, cancellation);
    if (path.isEmpty()) {
      return Sample.UNDEFINED;
    }
    double[] xy = new double[2];
    path.get().at(t, new double[1], xy);
    return new Sample(xy[0], xy[1]);
  }

  @Override
  public List<Polyline> sample(
      Parametrisation.Expressed p, double lo, double hi, Window window, Cancellation cancellation) {
    Cancellation stop = either(cancellation);
    Optional<Traced> path = traced(p, stop);
    if (path.isEmpty()) {
      return List.of();
    }
    Traced curve = path.get();
    double[] argument = new double[1];
    return Adaptive.sample(
        (t, xy) -> curve.at(t, argument, xy), lo, hi, window, pixelWidth, pixelHeight, stop);
  }

  @Override
  public List<Polyline> contour(Relation equation, Window window, Cancellation cancellation) {
    Cancellation stop = either(cancellation);
    Optional<Compiled> f = function(difference(equation), PLANE, stop);
    if (f.isEmpty()) {
      return List.of();
    }
    return MarchingSquares.contour(field(f.get()), window, stop);
  }

  @Override
  public List<Polyline> region(Relation inequality, Window window, Cancellation cancellation) {
    Cancellation stop = either(cancellation);
    Optional<Compiled> f = function(difference(inequality), PLANE, stop);
    if (f.isEmpty()) {
      return List.of();
    }
    MarchingSquares.Field g = field(f.get());
    MarchingSquares.Field inside =
        switch (inequality.op()) {
          case LT, LE -> g;
          case GT, GE -> (x, y) -> -g.at(x, y);
          case EQ, NE, IN -> throw new IllegalArgumentException("a region is an inequality");
        };
    return MarchingSquares.region(inside, window.widened(MARGIN), stop);
  }

  /** The sampler's own token and a call's: requested when either is. */
  private Cancellation either(Cancellation call) {
    return call == cancellation ? call : () -> call.requested() || cancellation.requested();
  }

  /** {@code left − right}, raw. */
  private static Expr difference(Relation r) {
    return Sum.of(r.left(), Product.of(Num.MINUS_ONE, r.right()));
  }

  private static MarchingSquares.Field field(Compiled f) {
    double[] point = new double[2];
    return (x, y) -> {
      point[0] = x;
      point[1] = y;
      return f.eval(point);
    };
  }

  private Optional<Traced> traced(Parametrisation.Expressed p, Cancellation cancellation) {
    Optional<Traced> hit = paths.get(p);
    if (hit != null) {
      return hit;
    }
    Optional<Traced> made =
        switch (p) {
          case Parametrisation.Graph g ->
              function(g.body(), List.of(g.variable()), cancellation).map(y -> new Traced(null, y));
          case Parametrisation.Parametric q -> both(q.x(), q.y(), q.parameter(), cancellation);
          case Parametrisation.Polar q ->
              both(
                  Product.of(q.radius(), Apply.of(COS, q.angle())),
                  Product.of(q.radius(), Apply.of(SIN, q.angle())),
                  q.angle(),
                  cancellation);
        };
    paths.put(p, made);
    return made;
  }

  private Optional<Traced> both(Expr x, Expr y, Symbol t, Cancellation cancellation) {
    Optional<Compiled> fx = function(x, List.of(t), cancellation);
    Optional<Compiled> fy = function(y, List.of(t), cancellation);
    return fx.isPresent() && fy.isPresent()
        ? Optional.of(new Traced(fx.get(), fy.get()))
        : Optional.empty();
  }

  /** The evaluator of an expression as written, evaluating and compiling it on a miss. */
  private Optional<Compiled> function(Expr e, List<Symbol> arguments, Cancellation cancellation) {
    Raw key = new Raw(e, arguments);
    Optional<Compiled> hit = functions.get(key);
    if (hit != null) {
      return hit;
    }
    Environment local = environment;
    for (Symbol s : arguments) {
      local = local.without(s.name());
    }
    Optional<Compiled> made;
    try {
      Expr value = Evaluator.evaluate(e, local, false, cancellation).value();
      made = samplers.compiled(value, arguments, settings, cancellation);
    } catch (EvaluationException ex) {
      made = Optional.empty();
    }
    functions.put(key, made);
    return made;
  }

  /** Compiles a value, empty when the compiler refuses it. */
  static Optional<Compiled> compile(
      Expr value, List<Symbol> arguments, Settings settings, Cancellation cancellation) {
    try {
      return Optional.of(Compiled.compile(value, arguments, settings, cancellation));
    } catch (UnsupportedException ex) {
      return Optional.empty();
    }
  }

  private record Raw(Expr expr, List<Symbol> arguments) {}

  /**
   * A path's compiled coordinates: {@code x} is the parameter itself when absent (a function
   * graph).
   */
  private record Traced(Compiled x, Compiled y) {

    void at(double t, double[] argument, double[] xy) {
      argument[0] = t;
      xy[0] = x == null ? t : x.eval(argument);
      xy[1] = y.eval(argument);
    }
  }
}
