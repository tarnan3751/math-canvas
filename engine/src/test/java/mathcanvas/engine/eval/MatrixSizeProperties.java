package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.parse.Parser;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.RandomDistribution;
import net.jqwik.api.Tuple;
import net.jqwik.api.statistics.Statistics;

/**
 * Random symbolic matrices to random powers and chains of products either evaluate or are refused
 * as too large: nothing else escapes, and the whole run stays within the budget.
 */
class MatrixSizeProperties {

  private static final List<String> ENTRIES =
      List.of("a", "b", "c", "d", "x", "y", "z", "0", "1", "-2", "a + b", "x - 2", "2y + 1", "c d");

  @Provide
  Arbitrary<String> statements() {
    // mostly 4×4 and 5×5, whose high powers and long chains pass the cap
    Arbitrary<Integer> size =
        Arbitraries.frequency(
            Tuple.of(1, 1), Tuple.of(1, 2), Tuple.of(2, 3), Tuple.of(4, 4), Tuple.of(4, 5));
    return size.flatMap(
        n ->
            Combinators.combine(
                    Arbitraries.of(ENTRIES).list().ofSize(n * n),
                    Arbitraries.integers()
                        .between(1, 16)
                        .withDistribution(RandomDistribution.uniform()),
                    Arbitraries.integers().between(2, 8),
                    Arbitraries.of(true, false))
                .as(
                    (entries, power, chain, usePower) -> {
                      List<String> rows = new ArrayList<>();
                      for (int i = 0; i < n; i++) {
                        rows.add("[" + String.join(", ", entries.subList(i * n, i * n + n)) + "]");
                      }
                      String m = "[" + String.join(", ", rows) + "]";
                      return usePower
                          ? "(" + m + ")^" + power
                          : String.join(" * ", java.util.Collections.nCopies(chain, m));
                    }));
  }

  // The whole run: typically 5 to 15 s locally (docs/reference/engine.md, Timing budgets).
  private static long total;

  @Property(tries = 50)
  void evaluatesOrRefuses(@ForAll("statements") String statement) {
    long start = System.nanoTime();
    Session s = new Session();
    Outcome o = s.run(Parser.parseScript(statement, s.names())).getFirst();
    total += System.nanoTime() - start;
    if (o instanceof Outcome.Failed f) {
      assertThat(f.error().getMessage()).as(statement).isEqualTo("result too large");
    } else {
      assertThat(o).as(statement).isInstanceOf(Outcome.Value.class);
    }
    Statistics.label("outcome").collect(o.getClass().getSimpleName());
    assertThat(total).isLessThan(30_000_000_000L);
  }
}
