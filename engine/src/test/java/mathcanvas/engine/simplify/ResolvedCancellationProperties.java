package mathcanvas.engine.simplify;

import static mathcanvas.engine.testing.Inputs.parse;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import net.jqwik.api.Example;

/**
 * The recheck's resolution of a sum that cancels past its precision at the size the standard list's
 * property met it, which takes the evaluator about 20 seconds at some 2,200 digits: a class the
 * quick gate leaves out. {@code SoundnessChecksTest} pins the same shape at a size that resolves at
 * once.
 */
class ResolvedCancellationProperties {

  /**
   * {@code e^(S − S)} is 1, while its simplification {@code e^(…)/3375} holds {@code log 3375}
   * beside a term near {@code 10^2066} and its negation, which lose it at 34 and at 100 digits
   * alike; evaluated again at the digits that resolve it, the sum is {@code log 3375} and the point
   * agrees.
   */
  @Example
  void theStandardListsCancellingSumIsResolved() {
    String s = "a^(-4) (a^4)^(54^(3/2)) + log((e a/225)^(3/2))";
    SoundnessChecks.begin();
    SoundnessChecks.check(
        Simplifier.standard(),
        parse("e^(" + s + " - (" + s + "))"),
        Assumptions.EMPTY,
        List.of(Map.of(Symbol.of("a"), Int.of(-20))));
    assertThat(SoundnessChecks.tally()).containsExactly(1, 0);
  }
}
