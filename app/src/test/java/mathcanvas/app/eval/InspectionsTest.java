package mathcanvas.app.eval;

import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.app.document.Inspection;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Session;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.parse.Statement;
import mathcanvas.engine.print.LayoutPrinter;
import org.junit.jupiter.api.Test;

/** The inspector's rows are printed on the worker, each definition once. */
class InspectionsTest {

  /** The environment after running a script in a new session. */
  private static Environment after(String script) {
    Session session = new Session(Environment.EMPTY);
    for (String line : script.split("\n")) {
      for (Statement s : Parser.parseScript(line, session.names()).statements()) {
        session.run(s);
      }
    }
    return session.environment();
  }

  @Test
  void theNamesAndTheAssumptionsArePrinted() {
    Inspection i =
        Inspections.of(after("f(x) := x^2 + 1\na := 3; assume(x > 0)"), Cancellation.NEVER);

    assertThat(i.entries()).extracting(Inspection.Entry::name).containsExactly("a", "f");
    Inspection.Entry f = i.entries().get(1);
    assertThat(LayoutPrinter.flatten(f.layout())).isEqualTo("f(x):=x² + 1");
    assertThat(f.text()).isEqualTo("x² + 1");
    assertThat(i.assumptions()).containsExactly("x > 0");
  }

  @Test
  void anUnchangedDefinitionIsPrintedOnce() {
    Inspection first = Inspections.of(after("g(t) := t^3"), Cancellation.NEVER);
    Inspection later = Inspections.of(after("g(t) := t^3\nb := 1\nc := 2"), Cancellation.NEVER);

    Inspection.Entry g =
        later.entries().stream().filter(e -> e.name().equals("g")).findFirst().orElseThrow();
    assertThat(g).isSameAs(first.entries().getFirst());
  }
}
