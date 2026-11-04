package mathcanvas.app.document;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.eval.Environment;

/** Where a cell's evaluation stands, and what it gave. */
public sealed interface Output {

  /** Not evaluated: the cell was never submitted, or is blank. */
  record None() implements Output {

    /** The one value. */
    public static final None INSTANCE = new None();
  }

  /** Waiting for the worker to evaluate it. */
  record Pending() implements Output {

    /** The one value. */
    public static final Pending INSTANCE = new Pending();
  }

  /** Evaluation was stopped before this cell finished; it ran nothing that stays. */
  record Cancelled() implements Output {

    /** The one value. */
    public static final Cancelled INSTANCE = new Cancelled();
  }

  /**
   * Evaluated.
   *
   * @param results one per statement run, or the one parse error
   * @param after the environment after the cell, which the next cell is evaluated in, without the
   *     canvas's variables: a slider or a named point is bound again at its current value for each
   *     cell that has not defined the name itself
   * @param inspection what the variables inspector shows of that environment
   * @param reads the names the cell's expressions read, through the definitions they use: when a
   *     canvas variable among them changes, the cell is evaluated again
   * @param assumes whether the cell runs {@code assume}, whose effect on the environment depends on
   *     the values it reads
   * @param sliders the sliders the cell defines, in order
   * @param help the names the cell's {@code help(name)} statements asked for, in order: the window
   *     opens the Help on the last when the user submitted the cell
   */
  record Done(
      List<Result> results,
      Environment after,
      Inspection inspection,
      Set<String> reads,
      boolean assumes,
      List<SliderSpec> sliders,
      List<String> help)
      implements Output {

    /**
     * Copies the collections and checks the environment.
     *
     * @throws NullPointerException if an operand is null
     */
    public Done {
      results = List.copyOf(results);
      Objects.requireNonNull(after, "after");
      Objects.requireNonNull(inspection, "inspection");
      reads = Set.copyOf(reads);
      sliders = List.copyOf(sliders);
      help = List.copyOf(help);
    }

    /**
     * An output that reads nothing, defines no slider and asks for no help.
     *
     * @param results the results
     * @param after the environment after the cell
     * @param inspection what the variables inspector shows of it
     */
    public Done(List<Result> results, Environment after, Inspection inspection) {
      this(results, after, inspection, Set.of(), false, List.of(), List.of());
    }

    /**
     * An output with nothing to inspect, for the tests.
     *
     * @param results the results
     * @param after the environment after the cell
     */
    public Done(List<Result> results, Environment after) {
      this(results, after, Inspection.EMPTY);
    }
  }
}
