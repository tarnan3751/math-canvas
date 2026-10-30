package mathcanvas.app.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;

/**
 * A plain sampler for the tests, standing in for the canvas's real one: each expression evaluated
 * in the environment (its own variables left free), compiled, and sampled at 64 even steps. Counts
 * the samplers it binds.
 */
final class TestSampling implements CanvasJob.Sampling {

  final AtomicInteger bound = new AtomicInteger();

  @Override
  public Sampler over(
      Environment environment,
      Settings settings,
      double pixelWidth,
      double pixelHeight,
      Cancellation cancellation) {
    bound.incrementAndGet();
    return new Sampler() {
      private double eval(Expr e, Symbol own, double at) {
        Environment env = own == null ? environment : environment.without(own.name());
        try {
          Expr value = Evaluator.evaluate(e, env, false, cancellation).value();
          List<Symbol> args = own == null ? List.of() : List.of(own);
          return Compiled.compile(value, args, settings)
              .eval(own == null ? new double[0] : new double[] {at});
        } catch (EvaluationException | UnsupportedException x) {
          return Double.NaN;
        }
      }

      @Override
      public double value(Expr e, Map<Symbol, Double> locals) {
        return eval(e, null, 0);
      }

      @Override
      public Sample point(Parametrisation.Expressed p, double t) {
        if (p instanceof Parametrisation.Graph g) {
          return new Sample(t, eval(g.body(), g.variable(), t));
        }
        return Sample.UNDEFINED;
      }

      @Override
      public List<Polyline> sample(
          Parametrisation.Expressed p,
          double lo,
          double hi,
          Window window,
          Cancellation cancellation) {
        int n = 64;
        List<Double> ts = new ArrayList<>();
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        for (int k = 0; k <= n; k++) {
          cancellation.check();
          double t = lo + (hi - lo) * k / n;
          Sample s = point(p, t);
          if (Double.isFinite(s.x()) && Double.isFinite(s.y())) {
            ts.add(t);
            xs.add(s.x());
            ys.add(s.y());
          }
        }
        if (ts.size() < 2) {
          return List.of();
        }
        return List.of(
            new Polyline(
                ts.stream().mapToDouble(Double::doubleValue).toArray(),
                xs.stream().mapToDouble(Double::doubleValue).toArray(),
                ys.stream().mapToDouble(Double::doubleValue).toArray()));
      }

      @Override
      public List<Polyline> contour(Relation equation, Window window, Cancellation cancellation) {
        return List.of();
      }

      @Override
      public List<Polyline> region(Relation inequality, Window window, Cancellation cancellation) {
        return List.of();
      }
    };
  }
}
