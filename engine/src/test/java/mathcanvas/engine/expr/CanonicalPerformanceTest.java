package mathcanvas.engine.expr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The performance budget of PLAN §8: each case finishes well under a second on the development
 * machine. The bound asserted is generous so that slower CI runners pass.
 */
class CanonicalPerformanceTest {

  // Each whole canonicalisation: typically milliseconds locally (docs/reference/engine.md, Timing
  // budgets).
  private static final Duration BOUND = Duration.ofSeconds(30);

  private static List<Symbol> symbols(int count) {
    List<Symbol> symbols = new ArrayList<>(count);
    for (int k = 0; k < count; k++) {
      symbols.add(Symbol.of("x" + k));
    }
    return symbols;
  }

  @Test
  void sumOfTenThousandDistinctSymbols() {
    Sum flat = Sum.of(symbols(10_000).reversed());
    Expr result = assertTimeoutPreemptively(BOUND, () -> Canonical.of(flat));
    assertThat(((Sum) result).terms()).hasSize(10_000);
  }

  @Test
  void leftNestedSumOfTenThousandSymbols() {
    Expr nested = Symbol.of("y");
    for (Symbol s : symbols(10_000)) {
      nested = Sum.of(nested, Product.of(Num.of(2), s));
    }
    Expr raw = nested;
    Expr result = assertTimeoutPreemptively(BOUND, () -> Canonical.of(raw));
    assertThat(((Sum) result).terms()).hasSize(10_001);
  }

  @Test
  void productOfAThousandPowers() {
    List<Expr> powers = new ArrayList<>();
    for (Symbol s : symbols(1_000).reversed()) {
      powers.add(Power.of(s, Num.of(s.name().length())));
    }
    Product raw = Product.of(powers);
    Expr result = assertTimeoutPreemptively(BOUND, () -> Canonical.of(raw));
    assertThat(((Product) result).factors()).hasSize(1_000);
  }

  @Test
  void productOfAThousandPowersOfTenBases() {
    List<Expr> powers = new ArrayList<>();
    List<Symbol> bases = symbols(10);
    for (int k = 0; k < 1_000; k++) {
      powers.add(Power.of(bases.get(k % 10), Num.of(k % 7 - 3)));
    }
    Product raw = Product.of(powers);
    Expr result = assertTimeoutPreemptively(BOUND, () -> Canonical.of(raw));
    assertThat(result).isInstanceOf(Product.class);
  }
}
