package mathcanvas.canvas.sample;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.compile.Compiled;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;

/**
 * The document's samplers and their memo of compiled evaluators. One instance per document, as the
 * inspector's memo is, and only the document's worker touches it; never static. {@link #over} binds
 * a {@link Sampler} to the environment of the moment (the worksheet's definitions and the sliders'
 * values), so a slider's change makes a new sampler, and every sampler of the document shares this
 * memo: an expression the change leaves alone is not compiled again.
 *
 * <p>The memo is keyed by the evaluated value, the arguments and the angle mode, holds at most
 * {@value #CAPACITY} entries and drops the least recently used. A value that cannot be compiled is
 * remembered as such.
 */
public final class Samplers {

  /** The most evaluators the memo keeps. */
  public static final int CAPACITY = 256;

  private final Map<Key, Optional<Compiled>> memo =
      new LinkedHashMap<>(CAPACITY * 2, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Optional<Compiled>> eldest) {
          return size() > CAPACITY;
        }
      };

  /** An empty memo. */
  public Samplers() {}

  /** The nominal size of a window on the screen, in pixels, when the caller gives none. */
  public static final double NOMINAL_PIXELS = 1000;

  /**
   * A sampler bound to an environment, judging flatness as if every window were {@value
   * #NOMINAL_PIXELS} pixels on each side.
   *
   * @param environment the definitions the sampled expressions read; an object's own variables
   *     shadow them
   * @param settings the document's settings: the angle mode is honoured
   * @return the sampler
   */
  public Sampler over(Environment environment, Settings settings) {
    return over(environment, settings, NOMINAL_PIXELS, NOMINAL_PIXELS);
  }

  /**
   * A sampler bound to an environment and to nothing that stops it, judging flatness on a view of
   * the given size, for callers with nothing to cancel.
   *
   * @param environment the definitions the sampled expressions read; an object's own variables
   *     shadow them
   * @param settings the document's settings: the angle mode is honoured
   * @param pixelWidth the view's width in pixels, positive and finite
   * @param pixelHeight the view's height in pixels, positive and finite
   * @return the sampler
   * @throws IllegalArgumentException if a size is not positive and finite
   */
  public Sampler over(
      Environment environment, Settings settings, double pixelWidth, double pixelHeight) {
    return over(environment, settings, pixelWidth, pixelHeight, Cancellation.NEVER);
  }

  /**
   * A sampler bound to an environment and to a job's cancellation, judging flatness on a view of
   * the given size: the window a curve is sampled in fills it. Every exact evaluation and
   * compilation of an expression the sampler does (a path's body, a slope, a contour's equation)
   * runs under {@code cancellation}, so {@link Sampler#value} and {@link Sampler#point}, which take
   * none, stop with the job; {@link Sampler#sample}, {@link Sampler#contour} and {@link
   * Sampler#region} also check the token they are given, in their loops and in that evaluation.
   *
   * @param environment the definitions the sampled expressions read; an object's own variables
   *     shadow them
   * @param settings the document's settings: the angle mode is honoured
   * @param pixelWidth the view's width in pixels, positive and finite
   * @param pixelHeight the view's height in pixels, positive and finite
   * @param cancellation the job's request to stop
   * @return the sampler
   * @throws IllegalArgumentException if a size is not positive and finite
   */
  public Sampler over(
      Environment environment,
      Settings settings,
      double pixelWidth,
      double pixelHeight,
      Cancellation cancellation) {
    Objects.requireNonNull(cancellation, "cancellation");
    if (!(pixelWidth > 0 && pixelHeight > 0)
        || Double.isInfinite(pixelWidth)
        || Double.isInfinite(pixelHeight)) {
      throw new IllegalArgumentException("a view's size is positive and finite");
    }
    return new BoundSampler(
        this, environment.withSettings(settings), settings, pixelWidth, pixelHeight, cancellation);
  }

  /**
   * The number of evaluators in the memo.
   *
   * @return at most {@value #CAPACITY}
   */
  public int size() {
    synchronized (memo) {
      return memo.size();
    }
  }

  /** The memoised evaluator of a value, compiling it on a miss. */
  Optional<Compiled> compiled(
      Expr value, List<Symbol> arguments, Settings settings, Cancellation cancellation) {
    Key key = new Key(value, arguments, settings.angleMode());
    synchronized (memo) {
      Optional<Compiled> hit = memo.get(key);
      if (hit != null) {
        return hit;
      }
    }
    Optional<Compiled> made = BoundSampler.compile(value, arguments, settings, cancellation);
    synchronized (memo) {
      memo.put(key, made);
    }
    return made;
  }

  private record Key(Expr value, List<Symbol> arguments, AngleMode mode) {}
}
