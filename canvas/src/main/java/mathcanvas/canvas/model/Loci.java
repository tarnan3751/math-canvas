package mathcanvas.canvas.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import mathcanvas.engine.Cancellation;

/**
 * Traces a locus (docs/reference/canvas.md, <b>Loci</b>): the canvas cut down to the point and the
 * objects it is computed from, the driver moved to each sample of its path's parameter range and
 * that canvas recomputed from the driver on, through the same {@link Recompute}, the point's
 * positions joined into polylines. The samples run in order, each canvas the one before moved on,
 * so an intersection between the driver and the point keeps its identity along the sweep; {@link
 * #FIRST} uniform samples, each segment halved while the point moves more than {@link #STEP} of the
 * window's diagonal across it or one end is undefined, down to {@link #DEPTH} halvings and at most
 * {@link #MOST} samples in all; an undefined sample breaks the polyline. A parameter range that is
 * infinite (a line, a ray, a graph) is cut to the part of the path in the window widened by half.
 */
final class Loci {

  /** The uniform samples first taken. */
  static final int FIRST = 256;

  /** The most samples of one locus. */
  static final int MOST = 4096;

  /** The most halvings of one first segment. */
  static final int DEPTH = 12;

  /** The largest step of the point between samples, relative to the window's diagonal. */
  static final double STEP = 1.0 / 200;

  private Loci() {}

  /**
   * A locus's geometry.
   *
   * @param locus the locus
   * @param canvas the canvas as recomputed so far, its parents up to date
   * @param sampler the sampler
   * @param window the window
   * @param recompute the recompute
   * @param cancellation checked at every sample
   * @return the traced polylines, or undefined
   */
  static Geometry trace(
      Locus locus,
      CanvasState canvas,
      Sampler sampler,
      Window window,
      Recompute recompute,
      Cancellation cancellation) {
    if (!(canvas.object(locus.driver()).orElse(null) instanceof PointOnPath driver)
        || driver.at().isPresent()
        || !(canvas.geometryOf(driver.path()) instanceof Geometry.Curve c)
        || !(c.path() instanceof Path.Parametrised q)
        || q.kind() != driver.kind()) {
      return Geometry.UNDEFINED;
    }
    Set<ObjectId> ancestors = ancestors(canvas, locus.point());
    if (!ancestors.contains(driver.id())) {
      return Geometry.UNDEFINED;
    }
    Optional<double[]> range = range(q, c, window.widened(Recomputes.MARGIN));
    if (range.isEmpty()) {
      return Geometry.UNDEFINED;
    }
    List<CanvasObject> kept = new ArrayList<>();
    Map<ObjectId, Geometry> geometry = new HashMap<>();
    for (CanvasObject o : canvas.objects()) {
      if (ancestors.contains(o.id())) {
        kept.add(o);
        geometry.put(o.id(), canvas.geometryOf(o.id()));
      }
    }
    Tracer t =
        new Tracer(
            locus.point(),
            driver,
            sampler,
            window,
            recompute,
            cancellation,
            STEP * window.diagonal());
    CanvasState start = new CanvasState(kept, geometry, canvas.nextId());
    double lo = range.get()[0];
    double hi = range.get()[1];
    Tracer.At previous = t.at(start, lo);
    t.emit(previous);
    for (int k = 1; k <= FIRST; k++) {
      double tk = k == FIRST ? hi : lo + (hi - lo) * k / FIRST;
      Tracer.At next = t.at(previous.state(), tk);
      t.segment(previous, next, DEPTH);
      previous = next;
    }
    return new Geometry.Curve(Path.POLYLINE_ONLY, t.polylines());
  }

  /** The point and every object it is computed from, through parents. */
  private static Set<ObjectId> ancestors(CanvasState canvas, ObjectId point) {
    Set<ObjectId> out = new HashSet<>();
    Deque<ObjectId> todo = new ArrayDeque<>(List.of(point));
    while (!todo.isEmpty()) {
      ObjectId id = todo.pop();
      if (out.add(id)) {
        canvas.object(id).ifPresent(o -> todo.addAll(o.parents()));
      }
    }
    return out;
  }

  /** The driver's parameter range: its path's domain, cut to the window where it is infinite. */
  private static Optional<double[]> range(Path.Parametrised q, Geometry.Curve c, Window wide) {
    double lo = q.lo();
    double hi = q.hi();
    if (Double.isInfinite(lo) || Double.isInfinite(hi)) {
      double[] cut = null;
      if (q.parametrisation() instanceof Parametrisation.Linear l) {
        cut =
            Recomputes.clip(l, lo, wide)
                .map(b -> new double[] {b.t(0), b.t(b.size() - 1)})
                .orElse(null);
      } else if (q.kind() == ParamKind.X) {
        cut = new double[] {wide.minX(), wide.maxX()};
      } else if (!c.branches().isEmpty()) {
        cut = new double[] {Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (Polyline b : c.branches()) {
          cut[0] = Math.min(cut[0], Math.min(b.t(0), b.t(b.size() - 1)));
          cut[1] = Math.max(cut[1], Math.max(b.t(0), b.t(b.size() - 1)));
        }
      }
      if (cut == null) {
        return Optional.empty();
      }
      lo = Math.max(lo, cut[0]);
      hi = Math.min(hi, cut[1]);
    }
    return lo < hi && Double.isFinite(lo) && Double.isFinite(hi)
        ? Optional.of(new double[] {lo, hi})
        : Optional.empty();
  }

  /** The sweep of one locus: the samples, and the polylines they make. */
  private static final class Tracer {

    /** A sample: the parameter, the canvas there, the point's position or null. */
    record At(double t, CanvasState state, Geometry.Point point) {}

    private final ObjectId point;
    private final ObjectId driver;
    private final Sampler sampler;
    private final Window window;
    private final Recompute recompute;
    private final Cancellation cancellation;
    private final double step;
    private final List<Polyline> out = new ArrayList<>();
    private final List<double[]> run = new ArrayList<>();
    private int samples;

    Tracer(
        ObjectId point,
        PointOnPath driver,
        Sampler sampler,
        Window window,
        Recompute recompute,
        Cancellation cancellation,
        double step) {
      this.point = point;
      this.driver = driver.id();
      this.sampler = sampler;
      this.window = window;
      this.recompute = recompute;
      this.cancellation = cancellation;
      this.step = step;
    }

    /** The canvas with the driver at a parameter, recomputed from it on. */
    At at(CanvasState from, double t) {
      cancellation.check();
      samples++;
      PointOnPath d = (PointOnPath) from.object(driver).orElseThrow();
      CanvasState s =
          from.replace(d.withParameter(d.kind(), t))
              .recompute(Set.of(driver), Set.of(), sampler, window, recompute, cancellation);
      return new At(t, s, s.geometryOf(point) instanceof Geometry.Point p ? p : null);
    }

    /** Emits the samples between two, halving where the point moves too far or vanishes. */
    void segment(At a, At b, int depth) {
      boolean split =
          depth > 0 && samples < MOST && (a.point() == null) != (b.point() == null)
              || depth > 0
                  && samples < MOST
                  && a.point() != null
                  && b.point() != null
                  && Math.hypot(b.point().x() - a.point().x(), b.point().y() - a.point().y())
                      > step;
      if (split) {
        At mid = at(a.state(), (a.t() + b.t()) / 2);
        segment(a, mid, depth - 1);
        segment(mid, b, depth - 1);
      } else {
        emit(b);
      }
    }

    void emit(At s) {
      if (s.point() == null) {
        flush();
        return;
      }
      run.add(new double[] {s.t(), s.point().x(), s.point().y()});
    }

    private void flush() {
      if (run.size() >= 2) {
        double[] xs = new double[run.size()];
        double[] ys = new double[run.size()];
        for (int k = 0; k < run.size(); k++) {
          xs[k] = run.get(k)[1];
          ys[k] = run.get(k)[2];
        }
        out.add(Polyline.unparametrised(xs, ys));
      }
      run.clear();
    }

    List<Polyline> polylines() {
      flush();
      return List.copyOf(out);
    }
  }
}
