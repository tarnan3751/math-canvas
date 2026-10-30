package mathcanvas.canvas.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.RealNumeric;

/**
 * The tests' sampler: a small {@code double} interpreter over numbers, π and e, symbols bound in a
 * map, sums, products, powers, and {@code sin}, {@code cos}, {@code sqrt}, {@code exp}, sampled
 * uniformly. Enough for polynomials and the trigonometric cases; the real one compiles through the
 * engine. Contours and regions are the fixed polylines it is given.
 */
final class StubSampler implements Sampler {

  private final Map<String, Double> values;
  private final int steps;
  private final List<Polyline> contour;
  private final List<Polyline> region;
  int points;

  StubSampler(Map<String, Double> values) {
    this(values, 512, List.of(), List.of());
  }

  StubSampler(
      Map<String, Double> values, int steps, List<Polyline> contour, List<Polyline> region) {
    this.values = Map.copyOf(values);
    this.steps = steps;
    this.contour = List.copyOf(contour);
    this.region = List.copyOf(region);
  }

  StubSampler with(String name, double value) {
    Map<String, Double> m = new HashMap<>(values);
    m.put(name, value);
    return new StubSampler(m, steps, contour, region);
  }

  @Override
  public double value(Expr e, Map<Symbol, Double> locals) {
    return eval(e, locals);
  }

  @Override
  public Sample point(Parametrisation.Expressed p, double t) {
    points++;
    return switch (p) {
      case Parametrisation.Graph g -> new Sample(t, eval(g.body(), Map.of(g.variable(), t)));
      case Parametrisation.Parametric q -> {
        Map<Symbol, Double> at = Map.of(q.parameter(), t);
        yield new Sample(eval(q.x(), at), eval(q.y(), at));
      }
      case Parametrisation.Polar q -> {
        double r = eval(q.radius(), Map.of(q.angle(), t));
        yield new Sample(r * Math.cos(t), r * Math.sin(t));
      }
    };
  }

  @Override
  public List<Polyline> sample(
      Parametrisation.Expressed p, double lo, double hi, Window window, Cancellation cancellation) {
    List<Polyline> out = new ArrayList<>();
    List<double[]> run = new ArrayList<>();
    int n = hi > lo ? steps : 0;
    for (int k = 0; k <= n; k++) {
      cancellation.check();
      double t = n == 0 ? lo : k == n ? hi : lo + (hi - lo) * k / n;
      Sample s = point(p, t);
      if (s.isFinite()) {
        run.add(new double[] {t, s.x(), s.y()});
      } else {
        flush(run, out);
      }
    }
    flush(run, out);
    return out;
  }

  private static void flush(List<double[]> run, List<Polyline> out) {
    if (run.isEmpty()) {
      return;
    }
    double[] ts = new double[run.size()];
    double[] xs = new double[run.size()];
    double[] ys = new double[run.size()];
    for (int k = 0; k < run.size(); k++) {
      ts[k] = run.get(k)[0];
      xs[k] = run.get(k)[1];
      ys[k] = run.get(k)[2];
    }
    out.add(new Polyline(ts, xs, ys));
    run.clear();
  }

  @Override
  public List<Polyline> contour(Relation equation, Window window, Cancellation cancellation) {
    return contour;
  }

  @Override
  public List<Polyline> region(Relation inequality, Window window, Cancellation cancellation) {
    return region;
  }

  private double eval(Expr e, Map<Symbol, Double> locals) {
    return switch (e) {
      case Num n when n.value() instanceof RealNumeric r -> r.toDouble();
      case Constant c ->
          switch (c.kind()) {
            case PI -> Math.PI;
            case E -> Math.E;
            case INFINITY -> Double.POSITIVE_INFINITY;
            default -> Double.NaN;
          };
      case Symbol s -> {
        Double v = locals.get(s);
        yield v != null ? v : values.getOrDefault(s.name(), Double.NaN);
      }
      case Sum s -> {
        double v = 0;
        for (Expr t : s.terms()) {
          v += eval(t, locals);
        }
        yield v;
      }
      case Product p -> {
        double v = 1;
        for (Expr f : p.factors()) {
          v *= eval(f, locals);
        }
        yield v;
      }
      case Power p -> Math.pow(eval(p.base(), locals), eval(p.exponent(), locals));
      case Apply a when a.args().size() == 1 -> {
        double x = eval(a.args().getFirst(), locals);
        yield switch (a.head().name()) {
          case "sin" -> Math.sin(x);
          case "cos" -> Math.cos(x);
          case "sqrt" -> Math.sqrt(x);
          case "exp" -> Math.exp(x);
          default -> Double.NaN;
        };
      }
      default -> Double.NaN;
    };
  }
}
