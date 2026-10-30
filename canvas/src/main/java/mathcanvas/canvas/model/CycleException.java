package mathcanvas.canvas.model;

import java.util.List;

/**
 * An edit that would make an object depend on itself. A cycle is reported, never dropped: the edit
 * is refused and the state it was applied to stays as it was.
 */
public final class CycleException extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  /** The cycle. */
  private final transient List<ObjectId> cycle;

  /**
   * An exception for a cycle.
   *
   * @param cycle the cycle, as {@link CanvasState#cycleThrough} gives it
   */
  public CycleException(List<ObjectId> cycle) {
    super("a dependency cycle: " + cycle);
    this.cycle = List.copyOf(cycle);
  }

  /**
   * The cycle: the edited object first, then each object a parent of the one before it; the last
   * has the first as a parent.
   *
   * @return the ids
   */
  public List<ObjectId> cycle() {
    return cycle;
  }
}
