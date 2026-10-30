package mathcanvas.app.eval;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import mathcanvas.app.text.Messages;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ImplicitCurve;
import mathcanvas.canvas.model.InequalityRegion;
import mathcanvas.canvas.model.Line;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Parametrisation;
import mathcanvas.canvas.model.Polyline;
import mathcanvas.canvas.model.Recompute;
import mathcanvas.canvas.model.Sample;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Window;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.sample.Samplers;
import mathcanvas.canvas.view.CanvasContent;
import mathcanvas.canvas.view.PlotContent;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Definition;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;

/**
 * One recompute of the canvas, on the worker: the objects that changed, the readers of the
 * variables that changed and everything that depends on them get new geometry through the canvas's
 * recompute and a sampler bound to the environment after the last cell (with the canvas's
 * variables), and then the algebra panel's rows and the drawn content are built from the result.
 * The job works on a snapshot; the document decides what of the result still applies when it
 * arrives ({@link Document}).
 *
 * <p>The variables that changed are the ones the document names (a slider or a point moved) and the
 * worksheet definitions that differ from the environment the previous job used, widened to every
 * definition that reads one of them, so a graph of {@code f(x)} follows {@code a} through {@code
 * f(x) := a x}. An object that depends on itself through the worksheet (a graph reading a point on
 * that graph, through a definition) is a cycle the canvas's own graph cannot see: it is undefined,
 * its row says so, and it is never recomputed, so the document never loops between the canvas and
 * the cells.
 */
public final class CanvasJob {

  /** Binds a sampler to an environment: the document's sampling, one per document. */
  @FunctionalInterface
  public interface Sampling {

    /**
     * A sampler bound to an environment.
     *
     * @param environment the environment after the last cell, with the canvas's variables
     * @param settings the document's engine settings (the angle mode)
     * @param pixels the window's width in pixels, for the sampler's flatness
     * @return the sampler
     */
    Sampler over(Environment environment, Settings settings, double pixels);

    /**
     * A sampler bound to an environment, for a view of a given size; the job calls this one. By
     * default the width alone is passed on.
     *
     * @param environment the environment after the last cell, with the canvas's variables
     * @param settings the document's engine settings (the angle mode)
     * @param pixelWidth the view's width in pixels
     * @param pixelHeight the view's height in pixels
     * @return the sampler
     */
    default Sampler over(
        Environment environment, Settings settings, double pixelWidth, double pixelHeight) {
      return over(environment, settings, pixelWidth);
    }

    /**
     * The canvas's sampler with the document's memo of compiled evaluators: one {@link Samplers}
     * for the document's life, bound afresh to each job's environment and view.
     *
     * @param samplers the document's memo
     * @return the sampling
     */
    static Sampling of(Samplers samplers) {
      return new Sampling() {
        @Override
        public Sampler over(Environment environment, Settings settings, double pixels) {
          return samplers.over(environment, settings, pixels, pixels);
        }

        @Override
        public Sampler over(
            Environment environment, Settings settings, double pixelWidth, double pixelHeight) {
          return samplers.over(environment, settings, pixelWidth, pixelHeight);
        }
      };
    }

    /** No sampling: every expression is undefined, so only points and lines have geometry. */
    Sampling UNAVAILABLE = (environment, settings, pixels) -> Unavailable.INSTANCE;
  }

  /** Builds the drawn content of a canvas. */
  @FunctionalInterface
  public interface Content {

    /**
     * The content.
     *
     * @param canvas the canvas
     * @param labels the points' names
     * @param theme the colours
     * @param cancellation the job's request to stop
     * @return the content and which object each item is
     * @throws mathcanvas.engine.CancelledException if the cancellation is requested
     */
    Built build(
        CanvasState canvas,
        Map<ObjectId, String> labels,
        CanvasTheme theme,
        Cancellation cancellation);

    /** Nothing drawn. */
    Content NONE = (canvas, labels, theme, cancellation) -> new Built(PlotContent.EMPTY, Map.of());

    /**
     * The canvas's own content ({@link CanvasContent#build}), which takes no cancellation: the
     * job's is checked before and after it.
     */
    Content STANDARD =
        (canvas, labels, theme, cancellation) -> {
          cancellation.check();
          CanvasContent.Built b = CanvasContent.build(canvas, labels, theme);
          cancellation.check();
          return new Built(b.content(), b.items());
        };
  }

  /**
   * Drawn content.
   *
   * @param content the content
   * @param items the object of each item of its hit index
   */
  public record Built(PlotContent content, Map<Integer, ObjectId> items) {

    /**
     * Copies the map.
     *
     * @throws NullPointerException if a field is null
     */
    public Built {
      Objects.requireNonNull(content, "content");
      items = Map.copyOf(items);
    }
  }

  /**
   * What a job works on.
   *
   * @param canvas the canvas, as the job starts
   * @param names the points' names
   * @param dirty the objects to recompute with their dependents
   * @param changed the canvas variables that changed
   * @param held the objects whose first recompute was stopped, left out of the dirty and resampled
   *     objects (they are still recomputed when a variable they read changed)
   * @param resample whether the window changed, so the objects sampled over it are recomputed
   * @param environment the environment after the last cell, with the canvas's variables
   * @param previous the environment the previous job used, or null for none
   * @param window the window
   * @param pixelWidth the view's width in pixels
   * @param pixelHeight the view's height in pixels
   * @param settings the engine settings
   * @param theme the colours
   */
  public record Input(
      CanvasState canvas,
      Map<ObjectId, String> names,
      Set<ObjectId> dirty,
      Set<String> changed,
      Set<ObjectId> held,
      boolean resample,
      Environment environment,
      Environment previous,
      Window window,
      double pixelWidth,
      double pixelHeight,
      Settings settings,
      CanvasTheme theme) {}

  /**
   * What a job made.
   *
   * @param from the canvas it started from
   * @param result the canvas with the new geometry and tracking state
   * @param built the drawn content of the result
   * @param rows the algebra panel's row of each object
   */
  public record Output(
      CanvasState from, CanvasState result, Built built, Map<ObjectId, AlgebraRow> rows) {}

  private CanvasJob() {}

  /**
   * Runs a job.
   *
   * @param in what it works on
   * @param sampling binds the sampler
   * @param content builds the content
   * @param recompute the canvas's recompute
   * @param rows the document's memo of the rows, touched only on this thread
   * @param cancellation the request to stop
   * @return what it made
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  static Output run(
      Input in,
      Sampling sampling,
      Content content,
      Recompute recompute,
      AlgebraRows rows,
      Cancellation cancellation) {
    Environment env = in.environment();
    Set<String> changed = new HashSet<>(in.changed());
    if (in.previous() != null) {
      changed.addAll(differing(in.previous(), env));
    }
    for (String n : env.definitions().keySet()) {
      cancellation.check();
      Set<String> reads = ReadNames.closure(Set.of(n), env);
      reads.retainAll(changed);
      if (!reads.isEmpty()) {
        changed.add(n);
      }
    }
    Map<ObjectId, String> cycles = cycles(in.canvas(), in.names(), env, cancellation);
    Set<ObjectId> seeds = new HashSet<>(in.dirty());
    seeds.addAll(cycles.keySet());
    if (in.resample()) {
      for (CanvasObject o : in.canvas().objects()) {
        if (o instanceof FunctionGraph
            || o instanceof ImplicitCurve
            || o instanceof InequalityRegion
            || o instanceof Line) {
          if (!in.held().contains(o.id())) {
            seeds.add(o.id());
          }
        }
      }
    }
    Recompute guarded =
        (group, parents, sampler, window, c) -> {
          if (!cycles.containsKey(group.getFirst().id())) {
            return recompute.apply(group, parents, sampler, window, c);
          }
          List<Recompute.Result> out = new ArrayList<>();
          for (CanvasObject o : group) {
            out.add(new Recompute.Result(o, Geometry.UNDEFINED));
          }
          return out;
        };
    Sampler sampler = sampling.over(env, in.settings(), in.pixelWidth(), in.pixelHeight());
    CanvasState result =
        in.canvas().recompute(seeds, changed, sampler, in.window(), guarded, cancellation);
    Map<ObjectId, AlgebraRow> printed = new LinkedHashMap<>();
    for (CanvasObject o : result.objects()) {
      printed.put(
          o.id(),
          rows.of(
              o,
              in.names(),
              result.geometryOf(o.id()),
              Optional.ofNullable(cycles.get(o.id())),
              cancellation));
    }
    Built built = content.build(result, in.names(), in.theme(), cancellation);
    return new Output(in.canvas(), result, built, printed);
  }

  /** The names whose definitions differ between two environments, or that one has alone. */
  private static Set<String> differing(Environment a, Environment b) {
    Set<String> out = new HashSet<>();
    Map<String, Definition> da = a.definitions();
    Map<String, Definition> db = b.definitions();
    for (Map.Entry<String, Definition> e : db.entrySet()) {
      if (!e.getValue().equals(da.get(e.getKey()))) {
        out.add(e.getKey());
      }
    }
    for (String k : da.keySet()) {
      if (!db.containsKey(k)) {
        out.add(k);
      }
    }
    if (!a.assumptions().equals(b.assumptions()) || !a.settings().equals(b.settings())) {
      out.addAll(db.keySet());
    }
    return out;
  }

  /**
   * The objects on a cycle through the worksheet, each with its message: the canvas's own edges
   * (parents, and the sliders an object reads) together with an edge from each named point or
   * slider to every object whose expressions read its name through the worksheet's definitions.
   */
  private static Map<ObjectId, String> cycles(
      CanvasState canvas, Map<ObjectId, String> names, Environment env, Cancellation cancellation) {
    Map<String, ObjectId> named = new HashMap<>();
    names.forEach((id, n) -> named.put(n, id));
    for (CanvasObject o : canvas.objects()) {
      if (o instanceof Slider s) {
        named.put(s.variable(), s.id());
      }
    }
    Map<ObjectId, Set<ObjectId>> children = new HashMap<>();
    for (CanvasObject o : canvas.objects()) {
      children.computeIfAbsent(o.id(), k -> new HashSet<>());
      for (ObjectId p : o.parents()) {
        children.computeIfAbsent(p, k -> new HashSet<>()).add(o.id());
      }
      if (!o.reads().isEmpty()) {
        cancellation.check();
        for (String n : ReadNames.closure(o.reads(), env)) {
          ObjectId from = named.get(n);
          if (from != null) {
            children.computeIfAbsent(from, k -> new HashSet<>()).add(o.id());
          }
        }
      }
    }
    Map<ObjectId, String> out = new HashMap<>();
    for (CanvasObject o : canvas.objects()) {
      Set<ObjectId> seen = new HashSet<>();
      Deque<ObjectId> todo = new ArrayDeque<>(children.get(o.id()));
      boolean loops = false;
      while (!todo.isEmpty() && !loops) {
        ObjectId at = todo.pop();
        loops = at.equals(o.id());
        if (seen.add(at)) {
          todo.addAll(children.getOrDefault(at, Set.of()));
        }
      }
      if (loops) {
        Set<String> through = new TreeSet<>();
        for (Map.Entry<String, ObjectId> n : named.entrySet()) {
          if (seen.contains(n.getValue())) {
            through.add(n.getKey());
          }
        }
        out.put(o.id(), Messages.text("row.cycle", String.join(", ", through)));
      }
    }
    return out;
  }

  /** The sampler of {@link Sampling#UNAVAILABLE}. */
  private enum Unavailable implements Sampler {
    INSTANCE;

    @Override
    public double value(Expr e, Map<Symbol, Double> locals) {
      return Double.NaN;
    }

    @Override
    public Sample point(Parametrisation.Expressed p, double t) {
      return Sample.UNDEFINED;
    }

    @Override
    public List<Polyline> sample(
        Parametrisation.Expressed p,
        double lo,
        double hi,
        Window window,
        Cancellation cancellation) {
      return List.of();
    }

    @Override
    public List<Polyline> contour(Relation equation, Window window, Cancellation cancellation) {
      return List.of();
    }

    @Override
    public List<Polyline> region(Relation inequality, Window window, Cancellation cancellation) {
      return List.of();
    }
  }
}
