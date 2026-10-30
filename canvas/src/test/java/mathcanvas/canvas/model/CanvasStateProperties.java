package mathcanvas.canvas.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

/**
 * The dependency graph over random constructions: recompute visits exactly the changed objects, the
 * readers of the changed variables and their dependents, each once and after its parents; a
 * redefinition that points an object at one of its dependents is refused with that cycle.
 */
class CanvasStateProperties {

  private static final Style S = Style.DEFAULT;
  private static final Window W = new Window(-1, 1, -1, 1);
  private static final StubSampler SAMPLER = new StubSampler(Map.of());

  /**
   * A random construction of up to 40 objects: free points, sliders, graphs reading random slider
   * variables, segments and lines of earlier points, points on earlier paths, intersections of
   * earlier paths.
   */
  static CanvasState construction(Random r) {
    CanvasState s = CanvasState.EMPTY;
    List<ObjectId> points = new ArrayList<>();
    List<ObjectId> paths = new ArrayList<>();
    List<String> variables = new ArrayList<>();
    int n = 1 + r.nextInt(40);
    for (int k = 0; k < n; k++) {
      ObjectId id = s.nextObjectId();
      int kind = r.nextInt(7);
      if (kind == 0 || points.size() < 2 && kind >= 3 && kind <= 4) {
        s = s.add(i -> new FreePoint(i, S, r.nextDouble(), r.nextDouble()));
        points.add(id);
      } else if (kind == 1) {
        String v = "v" + id.value();
        s =
            s.add(
                i ->
                    new Slider(
                        i, S, v, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
        variables.add(v);
      } else if (kind == 2 || paths.size() < 2 && kind >= 5) {
        List<Expr> terms = new ArrayList<>(List.of(Num.ONE, Symbol.of("x")));
        for (String v : variables) {
          if (r.nextInt(3) == 0) {
            terms.add(Symbol.of(v));
          }
        }
        s = s.add(i -> new FunctionGraph(i, S, Sum.of(terms), Symbol.of("x"), FunctionGraph.ALL));
        paths.add(id);
      } else if (kind == 3 || kind == 4) {
        ObjectId a = pick(r, points);
        ObjectId b = pick(r, points);
        while (b.equals(a)) {
          b = pick(r, points);
        }
        ObjectId bb = b;
        s =
            kind == 3
                ? s.add(i -> new Segment(i, S, a, bb))
                : s.add(i -> new Line(i, S, new Line.Through(a, bb)));
        paths.add(id);
      } else if (kind == 5) {
        ObjectId p = pick(r, paths);
        s = s.add(i -> new PointOnPath(i, S, p, ParamKind.X, 0, Double.NaN, Double.NaN));
        points.add(id);
      } else {
        ObjectId a = pick(r, paths);
        ObjectId b = pick(r, paths);
        while (b.equals(a)) {
          b = pick(r, paths);
        }
        ObjectId bb = b;
        s = s.add(i -> Intersection.of(i, S, a, bb, 0));
        points.add(id);
      }
    }
    return s;
  }

  private static ObjectId pick(Random r, List<ObjectId> from) {
    return from.get(r.nextInt(from.size()));
  }

  /** Every edge into an object, found the slow way. */
  private static Set<ObjectId> edgesInto(List<CanvasObject> objects, CanvasObject o) {
    Set<ObjectId> out = new HashSet<>(o.parents());
    for (CanvasObject x : objects) {
      if (x instanceof Slider sl && o.reads().contains(sl.variable())) {
        out.add(x.id());
      }
    }
    return out;
  }

  private static Set<ObjectId> union(Set<ObjectId> a, Set<ObjectId> b) {
    Set<ObjectId> out = new HashSet<>(a);
    out.addAll(b);
    return out;
  }

  private static Set<ObjectId> descendants(CanvasState s, Set<ObjectId> seeds) {
    Set<ObjectId> out = new HashSet<>(seeds);
    boolean grew = true;
    while (grew) {
      grew = false;
      for (CanvasObject o : s.objects()) {
        if (!out.contains(o.id()) && edgesInto(s.objects(), o).stream().anyMatch(out::contains)) {
          out.add(o.id());
          grew = true;
        }
      }
    }
    return out;
  }

  @Property(tries = 300)
  void recomputeVisitsTheClosureOnceInTopologicalOrder(@ForAll long seed) {
    Random r = new Random(seed);
    checkRecompute(r, construction(r));
  }

  /** The same, with the drawing order shuffled, so it is not a topological order. */
  @Property(tries = 300)
  void recomputeFollowsTheGraphNotTheDrawingOrder(@ForAll long seed) {
    Random r = new Random(seed);
    CanvasState s = construction(r);
    List<CanvasObject> shuffled = new ArrayList<>(s.objects());
    Collections.shuffle(shuffled, r);
    checkRecompute(r, new CanvasState(shuffled, Map.of(), s.nextId()));
  }

  private static void checkRecompute(Random r, CanvasState s) {
    Set<ObjectId> changed = new HashSet<>();
    for (CanvasObject o : s.objects()) {
      if (r.nextInt(5) == 0) {
        changed.add(o.id());
      }
    }
    Set<String> variables = new HashSet<>();
    for (CanvasObject o : s.objects()) {
      if (o instanceof Slider sl && r.nextInt(4) == 0) {
        variables.add(sl.variable());
      }
    }
    List<ObjectId> calls = new ArrayList<>();
    List<List<ObjectId>> groups = new ArrayList<>();
    Recompute counting =
        (group, parents, sampler, window, c) -> {
          groups.add(group.stream().map(CanvasObject::id).toList());
          List<Recompute.Result> out = new ArrayList<>();
          for (CanvasObject o : group) {
            calls.add(o.id());
            out.add(new Recompute.Result(o, CanvasStateTest.fitting(o)));
          }
          return out;
        };
    CanvasState before =
        s.recomputeAll(
            SAMPLER,
            W,
            (group, p, sa, w, c) ->
                group.stream()
                    .map(o -> new Recompute.Result(o, CanvasStateTest.fitting(o)))
                    .toList(),
            Cancellation.NEVER);
    CanvasState after =
        before.recompute(changed, variables, SAMPLER, W, counting, Cancellation.NEVER);

    // the seeds, and with an intersection every member of its family, then their dependents
    Set<ObjectId> seeds = descendants(s, union(changed, s.readers(variables)));
    for (CanvasObject o : s.objects()) {
      if (o instanceof Intersection i && seeds.contains(o.id())) {
        for (CanvasObject x : s.objects()) {
          if (x instanceof Intersection j
              && j.first().equals(i.first())
              && j.second().equals(i.second())) {
            seeds.add(x.id());
          }
        }
      }
    }
    Set<ObjectId> expected = descendants(s, seeds);
    for (List<ObjectId> group : groups) {
      CanvasObject head = s.object(group.getFirst()).orElseThrow();
      if (head instanceof Intersection i) {
        for (ObjectId id : group) {
          assertThat(s.object(id).orElseThrow())
              .isInstanceOfSatisfying(
                  Intersection.class, j -> assertThat(j.parents()).isEqualTo(i.parents()));
        }
      } else {
        assertThat(group).hasSize(1);
      }
    }
    assertThat(calls).doesNotHaveDuplicates();
    assertThat(new HashSet<>(calls)).isEqualTo(expected);
    for (int k = 0; k < calls.size(); k++) {
      CanvasObject o = s.object(calls.get(k)).orElseThrow();
      for (ObjectId p : edgesInto(s.objects(), o)) {
        if (expected.contains(p)) {
          assertThat(calls.indexOf(p)).isLessThan(k);
        }
      }
    }
    assertThat(after.geometry()).containsOnlyKeys(before.geometry().keySet());
  }

  @Property(tries = 300)
  void pointingAnObjectAtADependentIsACycle(@ForAll long seed) {
    Random r = new Random(seed);
    CanvasState s = construction(r);
    CanvasObject v = s.objects().get(r.nextInt(s.objects().size()));
    if (v instanceof Slider) {
      return; // redefining a slider as anything else removes its variable's edges
    }
    List<ObjectId> below = new ArrayList<>(descendants(s, Set.of(v.id())));
    below.remove(v.id());
    if (below.isEmpty()) {
      return;
    }
    ObjectId d = below.get(r.nextInt(below.size()));
    CanvasObject replacement;
    if (s.object(d).orElseThrow().isPath()) {
      replacement = new PointOnPath(v.id(), S, d, ParamKind.X, 0, Double.NaN, Double.NaN);
    } else {
      ObjectId x =
          s.objects().stream()
              .filter(o -> o.isPoint() && !o.id().equals(d) && !o.id().equals(v.id()))
              .map(CanvasObject::id)
              .findFirst()
              .orElse(null);
      if (x == null) {
        return;
      }
      replacement = new Segment(v.id(), S, d, x);
    }
    List<ObjectId> cycle = s.cycleThrough(replacement).orElseThrow();
    assertThat(cycle.getFirst()).isEqualTo(v.id());
    List<CanvasObject> after = new ArrayList<>();
    for (CanvasObject o : s.objects()) {
      after.add(o.id().equals(v.id()) ? replacement : o);
    }
    for (int k = 0; k < cycle.size(); k++) {
      CanvasObject child = find(after, cycle.get(k));
      assertThat(edgesInto(after, child)).contains(cycle.get((k + 1) % cycle.size()));
    }
    assertThatThrownBy(() -> s.replace(replacement)).isInstanceOf(CycleException.class);
  }

  private static CanvasObject find(List<CanvasObject> objects, ObjectId id) {
    return objects.stream().filter(o -> o.id().equals(id)).findFirst().orElseThrow();
  }
}
