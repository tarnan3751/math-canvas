package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.List;
import mathcanvas.engine.compile.Compiled;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

/**
 * The bitwise functions on random integers (§14.27): exactly {@code BigInteger}'s operations, and
 * the compiled forms agree with them within 2⁵³.
 */
class BitwiseProperties {

  private static final long LIMIT = 1L << 52;

  private static Num n(BigInteger v) {
    return Num.of(Int.of(v));
  }

  @Property(tries = 300)
  void theyAreBigIntegersOperations(
      @ForAll BigInteger a, @ForAll BigInteger b, @ForAll @IntRange(min = 0, max = 300) int k) {
    assertThat(Run.value("bitand(" + a + ", " + b + ")")).isEqualTo(n(a.and(b)));
    assertThat(Run.value("bitor(" + a + ", " + b + ")")).isEqualTo(n(a.or(b)));
    assertThat(Run.value("bitxor(" + a + ", " + b + ")")).isEqualTo(n(a.xor(b)));
    assertThat(Run.value("bitnot(" + a + ")")).isEqualTo(n(a.not()));
    assertThat(Run.value("shiftleft(" + a + ", " + k + ")")).isEqualTo(n(a.shiftLeft(k)));
    assertThat(Run.value("shiftright(" + a + ", " + k + ")")).isEqualTo(n(a.shiftRight(k)));
    assertThat(Run.value("bit(" + a + ", " + k + ")"))
        .isEqualTo(n(a.testBit(k) ? BigInteger.ONE : BigInteger.ZERO));
    assertThat(Run.value("bitcount(" + a.abs() + ")"))
        .isEqualTo(n(BigInteger.valueOf(a.abs().bitCount())));
  }

  @Property(tries = 300)
  void theCompiledFormsAgreeWithinTwoToTheFiftyThree(
      @ForAll @LongRange(min = -LIMIT, max = LIMIT) long a,
      @ForAll @LongRange(min = -LIMIT, max = LIMIT) long b,
      @ForAll @IntRange(min = 0, max = 40) int k) {
    Symbol x = Symbol.of("x");
    Symbol y = Symbol.of("y");
    for (String f : List.of("bitand", "bitor", "bitxor")) {
      Compiled c = Compiled.compile(Run.value(f + "(x, y)"), List.of(x, y), Settings.DEFAULT);
      Num exact = (Num) Run.value(f + "(" + a + ", " + b + ")");
      assertThat(c.eval(a, b)).as(f).isEqualTo(((Int) exact.value()).toDouble());
    }
    Compiled shift =
        Compiled.compile(Run.value("shiftright(x, y)"), List.of(x, y), Settings.DEFAULT);
    assertThat(shift.eval(a, k)).isEqualTo((double) (a >> k));
  }
}
