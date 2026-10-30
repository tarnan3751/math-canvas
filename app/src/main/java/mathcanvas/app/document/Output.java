package mathcanvas.app.document;

import java.util.List;
import java.util.Objects;
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
   * @param after the environment after the cell, which the next cell is evaluated in
   * @param inspection what the variables inspector shows of that environment
   */
  record Done(List<Result> results, Environment after, Inspection inspection) implements Output {

    /**
     * Copies the results and checks the environment.
     *
     * @throws NullPointerException if an operand is null
     */
    public Done {
      results = List.copyOf(results);
      Objects.requireNonNull(after, "after");
      Objects.requireNonNull(inspection, "inspection");
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
