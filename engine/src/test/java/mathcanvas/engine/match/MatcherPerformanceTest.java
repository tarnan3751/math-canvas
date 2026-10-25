package mathcanvas.engine.match;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import org.junit.jupiter.api.Test;

/**
 * The matcher's performance budget: 301 runs of each match, timed as a whole run against 30 s
 * (docs/reference/engine.md, Timing budgets). Typically they take milliseconds in all locally (well
 * under a millisecond a match); an exponential search would take far longer.
 */
class MatcherPerformanceTest {

  private static Symbol v(String name) {
    return Symbol.of(name);
  }

  /** The whole run's budget in nanoseconds. */
  private static final long BUDGET = 30_000_000_000L;

  /** The time of 301 runs of {@code run} in nanoseconds, as one whole run. */
  private static long total(Supplier<Object> run) {
    long start = System.nanoTime();
    for (int i = 0; i < 301; i++) {
      run.get();
    }
    return System.nanoTime() - start;
  }

  private static Expr product(int count, Expr... extra) {
    List<Expr> factors = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      factors.add(v("x" + i));
    }
    factors.addAll(List.of(extra));
    return Canonical.of(Product.of(factors));
  }

  @Test
  void aProductOfTwentyFactorsAgainstThreeOperands() {
    Expr subject = product(18, Power.of(v("p"), Num.of(2)), Power.of(v("q"), Num.of(3)));
    Expr noSquare = product(20);
    Pattern found =
        Pattern.of(
            Product.of(Power.of(v("a_"), Num.of(2)), Power.of(v("b_"), Num.of(3)), v("rest___")));
    Pattern fixed = Pattern.of(Product.of(v("a_"), v("b_"), v("c_")));
    Pattern guarded =
        Pattern.of(Product.of(v("a_"), v("b_"), v("rest___")))
            .where("a_", e -> e instanceof Power)
            .where("b_", e -> e instanceof Power);
    assertThat(Matcher.match(found, subject)).isPresent();
    assertThat(Matcher.match(found, noSquare)).isEmpty();
    assertThat(Matcher.match(fixed, subject)).isEmpty();
    assertThat(Matcher.match(guarded, noSquare)).isEmpty();
    assertThat(total(() -> Matcher.match(found, subject))).isLessThan(BUDGET);
    assertThat(total(() -> Matcher.match(found, noSquare))).isLessThan(BUDGET);
    assertThat(total(() -> Matcher.match(fixed, subject))).isLessThan(BUDGET);
    assertThat(total(() -> Matcher.match(guarded, noSquare))).isLessThan(BUDGET);
  }

  @Test
  void aSumOfAThousandTermsAgainstAVariableAndARest() {
    List<Expr> terms = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
      terms.add(v("t" + i));
    }
    Expr subject = Canonical.of(Sum.of(terms));
    Pattern p = Pattern.of(Sum.of(v("a_"), v("rest___")));
    assertThat(Matcher.match(p, subject).orElseThrow().sequence("rest___")).hasSize(999);
    assertThat(total(() -> Matcher.match(p, subject))).isLessThan(BUDGET);
  }
}
