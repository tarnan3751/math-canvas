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

/** The matcher's performance budget, with a margin: the median of repeated warm runs. */
class MatcherPerformanceTest {

  private static Symbol v(String name) {
    return Symbol.of(name);
  }

  /** The median time of {@code run} in nanoseconds, after a warm-up. */
  private static long median(Supplier<Object> run) {
    for (int i = 0; i < 200; i++) {
      run.get();
    }
    long[] times = new long[101];
    for (int i = 0; i < times.length; i++) {
      long start = System.nanoTime();
      run.get();
      times[i] = System.nanoTime() - start;
    }
    java.util.Arrays.sort(times);
    return times[times.length / 2];
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
    long budget = 1_000_000;
    assertThat(median(() -> Matcher.match(found, subject))).isLessThan(budget);
    assertThat(median(() -> Matcher.match(found, noSquare))).isLessThan(budget);
    assertThat(median(() -> Matcher.match(fixed, subject))).isLessThan(budget);
    assertThat(median(() -> Matcher.match(guarded, noSquare))).isLessThan(budget);
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
    assertThat(median(() -> Matcher.match(p, subject))).isLessThan(5_000_000);
  }
}
