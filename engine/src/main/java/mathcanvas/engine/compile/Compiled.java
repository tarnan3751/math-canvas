package mathcanvas.engine.compile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;

/**
 * An expression compiled to a {@code double} function of its arguments, for plotting: the sampler
 * calls {@link #eval} millions of times a second, with no exact arithmetic and no allocation.
 * Immutable and safe to evaluate on any number of threads at once. Its value agrees with the
 * engine's wherever that value is real and within the {@code double} range, and is NaN wherever the
 * engine's value is undefined or not real, or any part of it is not real; a finite value beyond the
 * range is infinite. The sampler treats every non-finite value as a gap ({@code
 * docs/reference/engine.md}, <b>Compilation</b>).
 *
 * <p>The engine keeps no cache: a caller that compiles the same expression again keys its own memo
 * by the expression, the arguments and the angle mode.
 */
public final class Compiled {

  private final Node root;
  private final List<Symbol> arguments;

  private Compiled(Node root, List<Symbol> arguments) {
    this.root = root;
    this.arguments = arguments;
  }

  /**
   * Compiles an evaluated value as a function of {@code arguments}.
   *
   * @param e the value, canonical, as evaluation gives it in {@code settings}' angle mode
   * @param arguments the symbols the function takes, in order, distinct
   * @param settings the settings: the angle mode is honoured, the precision ignored
   * @return the compiled function
   * @throws UnsupportedException if {@code e} holds a function, a form, a free symbol or a complex
   *     value that has no real {@code double} value
   * @throws IllegalArgumentException if two arguments are equal
   */
  public static Compiled compile(Expr e, List<Symbol> arguments, Settings settings) {
    return compile(e, arguments, settings, Cancellation.NEVER);
  }

  /**
   * Compiles an evaluated value as a function of {@code arguments}, checking {@code cancellation}
   * at every node and while constant parts are folded.
   *
   * @param e the value, canonical, as evaluation gives it in {@code settings}' angle mode
   * @param arguments the symbols the function takes, in order, distinct
   * @param settings the settings: the angle mode is honoured, the precision ignored
   * @param cancellation the request to stop
   * @return the compiled function
   * @throws UnsupportedException if {@code e} holds a function, a form, a free symbol or a complex
   *     value that has no real {@code double} value
   * @throws IllegalArgumentException if two arguments are equal
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Compiled compile(
      Expr e, List<Symbol> arguments, Settings settings, Cancellation cancellation) {
    List<Symbol> args = List.copyOf(arguments);
    Map<String, Integer> index = new HashMap<>();
    for (int k = 0; k < args.size(); k++) {
      if (index.put(args.get(k).name(), k) != null) {
        throw new IllegalArgumentException("the argument " + args.get(k).name() + " is repeated");
      }
    }
    return new Compiled(new Compiler(index, settings.angleMode(), cancellation).compile(e), args);
  }

  /**
   * The symbols the function takes, in order.
   *
   * @return the arguments
   */
  public List<Symbol> arguments() {
    return arguments;
  }

  /**
   * The value at a point. Passing the same array on every call allocates nothing.
   *
   * @param args a value per argument, in order
   * @return the value; NaN where it is undefined or not real
   * @throws IllegalArgumentException if the count of values is not the count of arguments
   */
  public double eval(double... args) {
    if (args.length != arguments.size()) {
      throw new IllegalArgumentException(
          "expected " + arguments.size() + " values, got " + args.length);
    }
    return root.eval(args);
  }
}
