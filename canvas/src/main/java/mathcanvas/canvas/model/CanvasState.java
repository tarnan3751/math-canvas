package mathcanvas.canvas.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import mathcanvas.engine.Cancellation;

/**
 * The canvas at one moment: its objects, the geometry computed for them, and the id the next object
 * gets. Immutable; every edit and every recompute returns a new state, so the document's command
 * stack keeps whole states and an undo restores objects, anchors and the id counter together.
 *
 * <p>The objects form a dependency DAG. An object depends on its {@linkplain CanvasObject#parents()
 * parents} and on every slider whose variable it {@linkplain CanvasObject#reads() reads}. The
 * constructor checks that every parent exists and is of the right kind, that slider variables are
 * distinct and that there is no cycle; an edit that would make one is refused with a {@link
 * CycleException}. {@link #recompute} recomputes the changed objects and their dependents in
 * topological order, each once, after its parents.
 *
 * <p>Geometry is derived, like a cell's output: an object has an entry once it has been recomputed,
 * and an edit leaves the old entry in place until the next recompute.
 *
 * @param objects the objects in creation order, which is also the drawing order
 * @param geometry the geometry of every object recomputed so far
 * @param nextId the id the next new object gets, above every object's
 */
public record CanvasState(
    List<CanvasObject> objects, Map<ObjectId, Geometry> geometry, long nextId) {

  /** No objects; the first gets id 1. */
  public static final CanvasState EMPTY = new CanvasState(List.of(), Map.of(), 1);

  /**
   * Copies the fields and checks the graph.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if {@code nextId} is below 1, two objects share an id or a
   *     slider variable, an id is not below {@code nextId}, a parent is missing or of the wrong
   *     kind, geometry is held for an object that does not exist or is not of the object's kind, or
   *     the graph has a cycle ({@link CycleException})
   */
  public CanvasState {
    objects = List.copyOf(objects);
    geometry = Map.copyOf(geometry);
    if (nextId < 1) {
      throw new IllegalArgumentException("nextId is at least 1");
    }
    Map<ObjectId, CanvasObject> byId = index(objects);
    if (byId.size() != objects.size()) {
      throw new IllegalArgumentException("two objects share an id");
    }
    for (CanvasObject o : objects) {
      if (o.id().value() >= nextId) {
        throw new IllegalArgumentException("an object id is not below nextId");
      }
      checkParents(o, byId);
    }
    for (Map.Entry<ObjectId, Geometry> e : geometry.entrySet()) {
      CanvasObject o = byId.get(e.getKey());
      if (o == null) {
        throw new IllegalArgumentException("geometry for an object that does not exist");
      }
      if (!fits(o, e.getValue())) {
        throw new IllegalArgumentException("geometry of the wrong kind for " + o.id());
      }
    }
    Map<String, ObjectId> sliders = new HashMap<>();
    for (CanvasObject o : objects) {
      if (o instanceof Slider s && sliders.put(s.variable(), s.id()) != null) {
        throw new IllegalArgumentException("two sliders set " + s.variable());
      }
    }
    Graph g = new Graph(objects, sliders);
    if (!g.acyclic()) {
      for (CanvasObject o : objects) {
        Optional<List<ObjectId>> cycle = g.pathTo(o.id(), g.edgesInto(o));
        if (cycle.isPresent()) {
          throw new CycleException(cycle.get());
        }
      }
    }
  }

  /** Whether a geometry is of the kind an object has: undefined fits every object. */
  private static boolean fits(CanvasObject o, Geometry g) {
    return switch (g) {
      case Geometry.Undefined _ -> true;
      case Geometry.Point _ -> o.isPoint() || o instanceof Text;
      case Geometry.Curve _ -> o.isPath();
      case Geometry.Region _ -> o instanceof InequalityRegion;
      case Geometry.Value _ -> o instanceof Slider;
    };
  }

  /**
   * The family an object belongs to: the ordered pair of paths of an intersection, or the object
   * itself.
   */
  private static Object family(CanvasObject o) {
    return o instanceof Intersection i ? List.of(i.first(), i.second()) : o.id();
  }

  private static void checkParents(CanvasObject o, Map<ObjectId, CanvasObject> byId) {
    boolean wantsPaths = o instanceof PointOnPath || o instanceof Intersection;
    for (ObjectId p : o.parents()) {
      CanvasObject parent = byId.get(p);
      if (parent == null) {
        throw new IllegalArgumentException(o.id() + " names a missing parent " + p);
      }
      if (wantsPaths ? !parent.isPath() : !parent.isPoint()) {
        throw new IllegalArgumentException(
            o.id() + " needs a " + (wantsPaths ? "path" : "point") + " for " + p);
      }
    }
  }

  private static Map<ObjectId, CanvasObject> index(Collection<CanvasObject> objects) {
    Map<ObjectId, CanvasObject> out = new LinkedHashMap<>();
    for (CanvasObject o : objects) {
      out.put(o.id(), o);
    }
    return out;
  }

  /**
   * The id the next added object gets.
   *
   * @return the id
   */
  public ObjectId nextObjectId() {
    return new ObjectId(nextId);
  }

  /**
   * An object.
   *
   * @param id the id
   * @return the object, or empty when there is none
   */
  public Optional<CanvasObject> object(ObjectId id) {
    for (CanvasObject o : objects) {
      if (o.id().equals(id)) {
        return Optional.of(o);
      }
    }
    return Optional.empty();
  }

  /**
   * An object's geometry.
   *
   * @param id the id
   * @return the geometry, or {@link Geometry#UNDEFINED} when it has not been recomputed (or there
   *     is no such object)
   */
  public Geometry geometryOf(ObjectId id) {
    return geometry.getOrDefault(id, Geometry.UNDEFINED);
  }

  /**
   * This state with an object added, given the id {@link #nextObjectId()}.
   *
   * @param make builds the object from its id
   * @return the state
   * @throws IllegalArgumentException if the object has another id, or a parent is missing or of the
   *     wrong kind
   */
  public CanvasState add(Function<ObjectId, ? extends CanvasObject> make) {
    ObjectId id = nextObjectId();
    CanvasObject o = make.apply(id);
    if (!o.id().equals(id)) {
      throw new IllegalArgumentException("a new object gets the id " + id);
    }
    List<CanvasObject> out = new ArrayList<>(objects);
    out.add(o);
    return new CanvasState(out, geometry, nextId + 1);
  }

  /**
   * This state with an object redefined, keeping its id and its place; unchanged when no object has
   * the id. Its geometry stays until the next recompute.
   *
   * @param replacement the new definition
   * @return the state
   * @throws CycleException if the new definition would depend on itself ({@link #cycleThrough})
   * @throws IllegalArgumentException if a parent is missing or of the wrong kind, or a dependent
   *     needs the object to be of its old kind
   */
  public CanvasState replace(CanvasObject replacement) {
    List<CanvasObject> out = new ArrayList<>(objects);
    for (int k = 0; k < out.size(); k++) {
      if (out.get(k).id().equals(replacement.id())) {
        Optional<List<ObjectId>> cycle = cycleThrough(replacement);
        if (cycle.isPresent()) {
          throw new CycleException(cycle.get());
        }
        out.set(k, replacement);
        return new CanvasState(out, geometry, nextId);
      }
    }
    return this;
  }

  /**
   * The cycle a redefinition would make, for the user interface to report before it edits.
   *
   * @param replacement the new definition of an existing object
   * @return the cycle, the object first and then each object a parent of the one before it, the
   *     last having the object as a parent; empty when there would be none
   */
  public Optional<List<ObjectId>> cycleThrough(CanvasObject replacement) {
    List<CanvasObject> after = new ArrayList<>();
    for (CanvasObject o : objects) {
      after.add(o.id().equals(replacement.id()) ? replacement : o);
    }
    Graph g = new Graph(after, sliders(after));
    return g.pathTo(replacement.id(), g.edgesInto(replacement));
  }

  /**
   * This state without an object and everything that depends on it through its parents; unchanged
   * when no object has the id. The readers of a removed slider's variable stay: the variable may be
   * defined elsewhere, and the document recomputes them as changed variables.
   *
   * @param id the id
   * @return the state
   */
  public CanvasState remove(ObjectId id) {
    if (object(id).isEmpty()) {
      return this;
    }
    Set<ObjectId> gone = new HashSet<>();
    gone.add(id);
    boolean grew = true;
    while (grew) {
      grew = false;
      for (CanvasObject o : objects) {
        if (!gone.contains(o.id()) && o.parents().stream().anyMatch(gone::contains)) {
          gone.add(o.id());
          grew = true;
        }
      }
    }
    List<CanvasObject> out = new ArrayList<>();
    for (CanvasObject o : objects) {
      if (!gone.contains(o.id())) {
        out.add(o);
      }
    }
    Map<ObjectId, Geometry> geo = new HashMap<>(geometry);
    geo.keySet().removeAll(gone);
    return new CanvasState(out, geo, nextId);
  }

  /**
   * The objects that read any of some variables.
   *
   * @param variables the names
   * @return the ids, in order
   */
  public Set<ObjectId> readers(Set<String> variables) {
    Set<ObjectId> out = new TreeSet<>();
    for (CanvasObject o : objects) {
      if (o.reads().stream().anyMatch(variables::contains)) {
        out.add(o.id());
      }
    }
    return out;
  }

  /**
   * The objects whose records differ from an earlier state's, or are new since it: what a command
   * changed, so the document knows what to recompute.
   *
   * @param before the earlier state
   * @return the ids, in order
   */
  public Set<ObjectId> changedSince(CanvasState before) {
    Map<ObjectId, CanvasObject> old = index(before.objects);
    Set<ObjectId> out = new TreeSet<>();
    for (CanvasObject o : objects) {
      if (!o.equals(old.get(o.id()))) {
        out.add(o.id());
      }
    }
    return out;
  }

  /**
   * Recomputes every object.
   *
   * @param sampler the sampler
   * @param window the window
   * @param recompute the recompute
   * @param cancellation checked before each object
   * @return the state
   */
  public CanvasState recomputeAll(
      Sampler sampler, Window window, Recompute recompute, Cancellation cancellation) {
    return recompute(index(objects).keySet(), Set.of(), sampler, window, recompute, cancellation);
  }

  /**
   * Recomputes what a new window changes: the objects sampled over the window (function graphs,
   * implicit curves, regions and lines) and their dependents.
   *
   * @param sampler the sampler
   * @param window the new window
   * @param recompute the recompute
   * @param cancellation checked before each object
   * @return the state
   */
  public CanvasState resample(
      Sampler sampler, Window window, Recompute recompute, Cancellation cancellation) {
    Set<ObjectId> seeds = new HashSet<>();
    for (CanvasObject o : objects) {
      if (o instanceof FunctionGraph
          || o instanceof ImplicitCurve
          || o instanceof InequalityRegion
          || o instanceof Line) {
        seeds.add(o.id());
      }
    }
    return recompute(seeds, Set.of(), sampler, window, recompute, cancellation);
  }

  /**
   * Recomputes the changed objects, the readers of the changed variables, and everything that
   * depends on them, in topological order (ties in drawing order), each once and after its parents.
   * The intersections of one ordered pair of paths are recomputed together, as one group, whenever
   * any of them is. A group with an undefined parent is undefined without being recomputed, and
   * keeps its tracking state for when the parent is defined again.
   *
   * @param changed the objects that changed (ids with no object are ignored)
   * @param changedVariables the engine variables whose values changed
   * @param sampler the sampler, bound to the variables' current values
   * @param window the window
   * @param recompute the recompute
   * @param cancellation checked before each group
   * @return the state
   * @throws mathcanvas.engine.CancelledException when cancelled
   */
  public CanvasState recompute(
      Set<ObjectId> changed,
      Set<String> changedVariables,
      Sampler sampler,
      Window window,
      Recompute recompute,
      Cancellation cancellation) {
    Objects.requireNonNull(sampler, "sampler");
    Objects.requireNonNull(recompute, "recompute");
    Map<ObjectId, Integer> order = new HashMap<>();
    Map<Object, List<ObjectId>> families = new HashMap<>();
    for (int k = 0; k < objects.size(); k++) {
      CanvasObject o = objects.get(k);
      order.put(o.id(), k);
      families.computeIfAbsent(family(o), f -> new ArrayList<>()).add(o.id());
    }
    Graph g = new Graph(objects, sliders(objects));
    Set<ObjectId> seeds = new HashSet<>(readers(changedVariables));
    for (ObjectId id : changed) {
      if (order.containsKey(id)) {
        seeds.add(id);
      }
    }
    Set<ObjectId> affected = new HashSet<>();
    for (ObjectId id : g.closure(seeds)) {
      affected.addAll(families.get(family(objects.get(order.get(id)))));
    }
    affected = g.closure(affected);
    Map<ObjectId, Integer> waiting = new HashMap<>();
    PriorityQueue<ObjectId> ready = new PriorityQueue<>((a, b) -> order.get(a) - order.get(b));
    for (ObjectId id : affected) {
      int n = 0;
      for (ObjectId p : g.parents.get(id)) {
        if (affected.contains(p)) {
          n++;
        }
      }
      waiting.put(id, n);
      if (n == 0) {
        ready.add(id);
      }
    }
    List<CanvasObject> out = new ArrayList<>(objects);
    Map<ObjectId, Geometry> geo = new HashMap<>(geometry);
    Set<ObjectId> done = new HashSet<>();
    while (!ready.isEmpty()) {
      ObjectId first = ready.poll();
      if (done.contains(first)) {
        continue;
      }
      cancellation.check();
      // a family's members have the same parents, so they are all ready now
      List<ObjectId> ids = families.get(family(out.get(order.get(first))));
      List<CanvasObject> group = new ArrayList<>();
      for (ObjectId id : ids) {
        group.add(out.get(order.get(id)));
      }
      CanvasObject head = group.getFirst();
      Map<ObjectId, Geometry> parents = new HashMap<>();
      boolean defined = true;
      for (ObjectId p : head.parents()) {
        Geometry pg = geo.getOrDefault(p, Geometry.UNDEFINED);
        defined &= !(pg instanceof Geometry.Undefined);
        parents.put(p, pg);
      }
      if (defined) {
        List<Recompute.Result> results =
            recompute.apply(List.copyOf(group), Map.copyOf(parents), sampler, window, cancellation);
        if (results.size() != group.size()) {
          throw new IllegalStateException("a recompute gave " + results.size() + " results");
        }
        for (int k = 0; k < group.size(); k++) {
          CanvasObject o = group.get(k);
          Recompute.Result r = results.get(k);
          if (!r.object().id().equals(o.id()) || !r.object().parents().equals(o.parents())) {
            throw new IllegalStateException("a recompute changed " + o.id() + "'s definition");
          }
          out.set(order.get(o.id()), r.object());
          geo.put(o.id(), r.geometry());
        }
      } else {
        for (CanvasObject o : group) {
          geo.put(o.id(), Geometry.UNDEFINED);
        }
      }
      for (CanvasObject o : group) {
        done.add(o.id());
        for (ObjectId child : g.children.get(o.id())) {
          if (affected.contains(child) && waiting.merge(child, -1, Integer::sum) == 0) {
            ready.add(child);
          }
        }
      }
    }
    return new CanvasState(out, geo, nextId);
  }

  private static Map<String, ObjectId> sliders(List<CanvasObject> objects) {
    Map<String, ObjectId> out = new HashMap<>();
    for (CanvasObject o : objects) {
      if (o instanceof Slider s) {
        out.putIfAbsent(s.variable(), s.id());
      }
    }
    return out;
  }

  /**
   * The dependency edges: an object's parents and the sliders of the variables it reads. Built per
   * operation; missing parents are left out, since the constructor reports them.
   */
  private static final class Graph {

    private final Map<String, ObjectId> sliders;
    private final Map<ObjectId, List<ObjectId>> parents = new HashMap<>();
    private final Map<ObjectId, List<ObjectId>> children = new HashMap<>();

    Graph(List<CanvasObject> objects, Map<String, ObjectId> sliders) {
      this.sliders = sliders;
      for (CanvasObject o : objects) {
        children.put(o.id(), new ArrayList<>());
      }
      for (CanvasObject o : objects) {
        List<ObjectId> ps = new ArrayList<>();
        for (ObjectId p : edgesInto(o)) {
          if (children.containsKey(p)) {
            ps.add(p);
            children.get(p).add(o.id());
          }
        }
        parents.put(o.id(), ps);
      }
    }

    /** An object's edges in: its parents, then the sliders it reads, without repeats. */
    List<ObjectId> edgesInto(CanvasObject o) {
      Set<ObjectId> out = new LinkedHashSet<>(o.parents());
      for (String v : o.reads()) {
        ObjectId s = sliders.get(v);
        if (s != null) {
          out.add(s);
        }
      }
      return List.copyOf(out);
    }

    /** Whether there is no cycle: Kahn's algorithm removes every object. */
    boolean acyclic() {
      Map<ObjectId, Integer> waiting = new HashMap<>();
      Deque<ObjectId> ready = new ArrayDeque<>();
      parents.forEach(
          (id, ps) -> {
            waiting.put(id, ps.size());
            if (ps.isEmpty()) {
              ready.add(id);
            }
          });
      int removed = 0;
      while (!ready.isEmpty()) {
        removed++;
        for (ObjectId c : children.get(ready.pop())) {
          if (waiting.merge(c, -1, Integer::sum) == 0) {
            ready.add(c);
          }
        }
      }
      return removed == parents.size();
    }

    /** The seeds and every object that depends on one. */
    Set<ObjectId> closure(Set<ObjectId> seeds) {
      Set<ObjectId> out = new HashSet<>(seeds);
      Deque<ObjectId> todo = new ArrayDeque<>(seeds);
      while (!todo.isEmpty()) {
        for (ObjectId c : children.get(todo.pop())) {
          if (out.add(c)) {
            todo.push(c);
          }
        }
      }
      return out;
    }

    /**
     * A path of parent edges from one of {@code from} up to {@code target}, as a cycle through the
     * target: the target, then the parents in order. Empty when the target is not an ancestor.
     */
    Optional<List<ObjectId>> pathTo(ObjectId target, List<ObjectId> from) {
      Map<ObjectId, ObjectId> cameFrom = new HashMap<>();
      Deque<ObjectId> todo = new ArrayDeque<>();
      for (ObjectId f : from) {
        if (parents.containsKey(f) && !cameFrom.containsKey(f)) {
          cameFrom.put(f, target);
          todo.add(f);
        }
      }
      while (!todo.isEmpty()) {
        ObjectId at = todo.poll();
        if (at.equals(target)) {
          List<ObjectId> path = new ArrayList<>();
          ObjectId step = cameFrom.get(at);
          while (!step.equals(target)) {
            path.addFirst(step);
            step = cameFrom.get(step);
          }
          path.addFirst(target);
          return Optional.of(path);
        }
        for (ObjectId p : parents.get(at)) {
          if (!cameFrom.containsKey(p)) {
            cameFrom.put(p, at);
            todo.add(p);
          }
        }
      }
      return Optional.empty();
    }
  }
}
