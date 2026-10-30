package mathcanvas.canvas.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.Feature;
import mathcanvas.canvas.model.ObjectId;

/**
 * A change a tool asks for. A tool never touches the document: the app turns each edit into
 * commands on the command stack, or, for the edits that need the worker (finding features or
 * intersections), into a request that ends in commands.
 */
public sealed interface Edit {

  /**
   * Adds objects, as one undo step, with consecutive ids from the canvas's next one.
   *
   * @param objects how each is made, in order
   */
  record Add(List<New> objects) implements Edit {

    /**
     * Copies the list.
     *
     * @throws NullPointerException if the list or an element is null
     * @throws IllegalArgumentException if the list is empty
     */
    public Add {
      objects = List.copyOf(objects);
      if (objects.isEmpty()) {
        throw new IllegalArgumentException("an addition adds an object");
      }
    }

    /**
     * The objects, given the id the first one gets.
     *
     * @param first the first id
     * @return the objects, in order
     */
    public List<CanvasObject> made(ObjectId first) {
      List<ObjectId> ids = new ArrayList<>();
      List<CanvasObject> out = new ArrayList<>();
      for (New n : objects) {
        ids.add(new ObjectId(first.value() + ids.size()));
        out.add(n.make().apply(List.copyOf(ids)));
      }
      return out;
    }
  }

  /**
   * An object to add.
   *
   * @param make builds the object from the ids of the addition's objects up to it, its own last; it
   *     must use that id
   * @param named whether it gets the next free point name
   */
  record New(Function<List<ObjectId>, ? extends CanvasObject> make, boolean named) {

    /**
     * Checks the field.
     *
     * @throws NullPointerException if it is null
     */
    public New {
      Objects.requireNonNull(make, "make");
    }
  }

  /**
   * Moves a point: a free point to a position, a point on a path to a parameter.
   *
   * @param id the point
   * @param x the abscissa
   * @param y the ordinate
   * @param parameter a point on a path's parameter, NaN for a free point
   */
  record Move(ObjectId id, double x, double y, double parameter) implements Edit {

    /**
     * Checks the id.
     *
     * @throws NullPointerException if it is null
     */
    public Move {
      Objects.requireNonNull(id, "id");
    }
  }

  /**
   * Adds every intersection of two paths found now, one per place: the family of the ordered pair,
   * the lower id first (docs/reference/canvas.md, <b>Object model</b>).
   *
   * @param first the path with the lower id
   * @param second the other
   */
  record Intersect(ObjectId first, ObjectId second) implements Edit {

    /**
     * Checks the order.
     *
     * @throws NullPointerException if an id is null
     * @throws IllegalArgumentException unless the first id is the lower
     */
    public Intersect {
      Objects.requireNonNull(first, "first");
      Objects.requireNonNull(second, "second");
      if (first.compareTo(second) >= 0) {
        throw new IllegalArgumentException("an intersection's first path has the lower id");
      }
    }
  }

  /**
   * Adds every feature of the given kinds a function graph has now in the window.
   *
   * @param graph the graph
   * @param features the kinds: the roots, or the minima and the maxima
   */
  record Find(ObjectId graph, List<Feature> features) implements Edit {

    /**
     * Copies the list.
     *
     * @throws NullPointerException if a field or a feature is null
     */
    public Find {
      Objects.requireNonNull(graph, "graph");
      features = List.copyOf(features);
    }
  }
}
