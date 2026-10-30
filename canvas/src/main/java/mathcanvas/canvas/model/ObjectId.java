package mathcanvas.canvas.model;

/**
 * The stable id of a canvas object, allocated in order from {@link CanvasState#nextId()}. Parents
 * name their children's parents by id, so ids are saved and never renumbered; an undo restores the
 * counter with the rest of the state.
 *
 * @param value the id, at least 1
 */
public record ObjectId(long value) implements Comparable<ObjectId> {

  /**
   * Checks the value.
   *
   * @throws IllegalArgumentException if the value is below 1
   */
  public ObjectId {
    if (value < 1) {
      throw new IllegalArgumentException("an object id is at least 1");
    }
  }

  @Override
  public int compareTo(ObjectId other) {
    return Long.compare(value, other.value);
  }

  @Override
  public String toString() {
    return "#" + value;
  }
}
