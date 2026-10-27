package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.parse.Script;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.statistics.Statistics;

/**
 * Cancelling a script at a random check: the statements before keep exactly their uncancelled
 * outcomes, the statement it stops and every later one are cancelled, the environment is the one
 * before the stopped statement, and nothing escapes the session.
 */
class CancellationProperties {

  private static final List<String> POOL =
      List.of(
          "a := 2",
          "f(x) := x^2 + a",
          "assume(y > 0)",
          "clear(a)",
          "f(3) + a",
          "simplify(sqrt(y^2) + log(2 y))",
          "factor(x^4 - a^4)",
          "expand((x + a)^6)",
          "solve(x^2 = a, x)",
          "numeric(pi + sin(1/3), 80)",
          "diff(sin(x) x^2, x)",
          "cancel((x^2 - 1)/(x - 1))");

  @Provide
  Arbitrary<List<String>> scripts() {
    return Arbitraries.of(POOL).list().ofMinSize(1).ofMaxSize(6);
  }

  @Property(tries = 300)
  void cancellingAnywhereKeepsTheSessionConsistent(
      @ForAll("scripts") List<String> statements, @ForAll @LongRange(min = 1, max = 3000) long n) {
    Script script = Parser.parseScript(String.join("\n", statements), new Session().names());
    // the uncancelled run, and the environment before each statement
    Session reference = new Session();
    List<Outcome> expected = new ArrayList<>();
    List<Environment> before = new ArrayList<>();
    for (var s : script.statements()) {
      before.add(reference.environment());
      expected.add(reference.run(s));
    }
    before.add(reference.environment());

    Session cancelled = new Session();
    List<Outcome> out = cancelled.run(script, new Tripping(n));
    assertThat(out).hasSize(expected.size());
    int stopped = 0;
    while (stopped < out.size() && !(out.get(stopped) instanceof Outcome.Cancelled)) {
      stopped++;
    }
    for (int i = 0; i < stopped; i++) {
      assertThat(sameOutcome(out.get(i), expected.get(i))).as("statement %d", i).isTrue();
    }
    for (int i = stopped; i < out.size(); i++) {
      assertThat(out.get(i)).isInstanceOf(Outcome.Cancelled.class);
    }
    assertThat(cancelled.environment()).isEqualTo(before.get(stopped));
    Statistics.label("cancelled").collect(stopped < out.size());
  }

  /** Equal outcomes; a failure by its message, since an exception has identity equality. */
  private static boolean sameOutcome(Outcome a, Outcome b) {
    if (a instanceof Outcome.Failed fa && b instanceof Outcome.Failed fb) {
      return fa.error().getMessage().equals(fb.error().getMessage());
    }
    return a.equals(b);
  }
}
