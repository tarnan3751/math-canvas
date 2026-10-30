package mathcanvas.app.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mathcanvas.app.document.Inspection;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Definition;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.print.LayoutPrinter;
import mathcanvas.engine.print.UnicodePrinter;

/**
 * Prints what the variables inspector shows of an environment, on the worker, each definition once:
 * an entry is kept per name and definition (the most recent {@link #MEMO} of them), so a definition
 * that did not change is never printed again. Used only on the worker's one thread.
 */
final class Inspections {

  /** The most entries kept. */
  static final int MEMO = 512;

  private static final Map<Map.Entry<String, Definition>, Inspection.Entry> ENTRIES =
      new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(
            Map.Entry<Map.Entry<String, Definition>, Inspection.Entry> eldest) {
          return size() > MEMO;
        }
      };

  private Inspections() {}

  /**
   * What the inspector shows of an environment.
   *
   * @param environment the environment
   * @param cancellation the request to stop
   * @return the inspection
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  static Inspection of(Environment environment, Cancellation cancellation) {
    List<Inspection.Entry> entries = new ArrayList<>();
    for (Map.Entry<String, Definition> d : environment.definitions().entrySet()) {
      cancellation.check();
      entries.add(
          ENTRIES.computeIfAbsent(
              Map.entry(d.getKey(), d.getValue()), k -> entry(k.getKey(), k.getValue())));
    }
    List<String> facts = new ArrayList<>();
    for (Relation r : environment.assumptions().recorded()) {
      facts.add(UnicodePrinter.INSTANCE.print(r));
    }
    return new Inspection(entries, facts);
  }

  private static Inspection.Entry entry(String name, Definition definition) {
    return new Inspection.Entry(
        name,
        definition,
        UnicodePrinter.INSTANCE.print(definition.body()),
        Typeset.definition(LayoutPrinter.INSTANCE, name, definition));
  }
}
