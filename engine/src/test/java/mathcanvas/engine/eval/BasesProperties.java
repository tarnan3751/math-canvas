package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.print.AsciiPrinter;
import mathcanvas.engine.print.UnicodePrinter;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/**
 * Integers in other bases on random data (§3.1, §14.27): the digits of {@code n} in base {@code b}
 * reconstruct it, a literal in base 16, 2 or 8 reads as its integer, a value shown in base 16, 2 or
 * 8 prints to text that reads back as its integer and in another base to a call that evaluates to
 * it, and the base does not propagate.
 */
class BasesProperties {

  @Property(tries = 200)
  void theDigitsReconstructTheInteger(
      @ForAll BigInteger n, @ForAll @IntRange(min = 2, max = 36) int b) {
    BigInteger m = n.abs();
    Vector digits = (Vector) Run.value("digits(" + m + ", " + b + ")");
    BigInteger back = BigInteger.ZERO;
    for (Expr d : digits.items()) {
      back = back.multiply(BigInteger.valueOf(b)).add(((Int) ((Num) d).value()).value());
    }
    assertThat(back).isEqualTo(m);
  }

  @Property(tries = 200)
  void literalsReadAsTheirIntegers(@ForAll BigInteger n) {
    BigInteger m = n.abs();
    assertThat(Run.value("0x" + m.toString(16).toUpperCase())).isEqualTo(Num.of(Int.of(m)));
    assertThat(Run.value("0b" + m.toString(2))).isEqualTo(Num.of(Int.of(m)));
    assertThat(Run.value("0o" + m.toString(8))).isEqualTo(Num.of(Int.of(m)));
  }

  @Property(tries = 200)
  void aValueInABasePrintsToTextThatReadsBackAsItsInteger(
      @ForAll BigInteger n, @ForAll @IntRange(min = 2, max = 36) int b) {
    Expr v = Run.value("base(" + n + ", " + b + ")");
    Expr integer = Num.of(Int.of(n));
    if (b == 2 || b == 8 || b == 16 || b == 10) {
      assertThat(Run.value(AsciiPrinter.INSTANCE.display(v))).isEqualTo(integer);
      assertThat(Run.value(UnicodePrinter.INSTANCE.display(v))).isEqualTo(integer);
    } else {
      // base(n, b) in ASCII, which evaluates to the value itself
      assertThat(Run.value(AsciiPrinter.INSTANCE.display(v))).isEqualTo(v);
    }
  }

  @Property(tries = 100)
  void theBaseDoesNotPropagate(@ForAll BigInteger n, @ForAll @IntRange(min = 2, max = 36) int b) {
    String shown = "base(" + n + ", " + b + ")";
    assertThat(Run.value(shown + " + 1")).isEqualTo(Run.value(n + " + 1"));
    assertThat(Run.value("abs(" + shown + ")")).isEqualTo(Run.value("abs(" + n + ")"));
    assertThat(Run.value(shown + " = " + n)).isEqualTo(Run.value("true"));
  }
}
