package mathcanvas.engine.number;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * No non-normal number escapes a factory or an operation: random sequences of operations, every
 * intermediate checked. Normal form here means no {@code Rational} with denominator 1, no {@code
 * Complex} with an exact-zero imaginary part, and no unstripped {@code Real}.
 */
class DemotionProperties {

  private static final MathContext MC = MathContext.DECIMAL64;

  enum Op {
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    NEGATE,
    CONJ,
    SQUARE,
    INVERSE,
    SQRT,
    TO_REAL,
    RE,
    IM,
    MODULUS,
    POWER,
    ARGUMENT
  }

  /** Exponents for {@link Op#POWER}: fixed and small, so a sequence cannot grow without bound. */
  private static final List<Numeric> EXPONENTS =
      List.of(
          Rational.of(1, 2),
          Rational.of(-1, 2),
          Rational.of(1, 3),
          Rational.of(2, 3),
          Real.of("0.5"),
          Real.of("2"),
          Complex.I);

  record Step(Op op, Numeric operand) {}

  @Property
  void operationsPreserveNormalForm(
      @ForAll("small") Numeric start, @ForAll("steps") List<Step> steps) {
    Numeric x = start;
    assertThat(NumberArbitraries.normal(x)).isTrue();
    for (Step step : steps) {
      try {
        x = apply(step, x);
      } catch (ArithmeticException undefined) {
        continue;
      }
      assertThat(NumberArbitraries.normal(x)).as("%s after %s", x, step).isTrue();
      if (x.isExact()) {
        assertThat(x).isNotInstanceOf(Real.class);
      }
    }
  }

  /** {@code root(x^n, n) == x}, exact and of the same kind, under the odd-root sign rule. */
  @Property
  void rootUndoesPower(
      @ForAll("smallExacts") RealNumeric x, @ForAll @IntRange(min = 1, max = 7) int n) {
    RealNumeric base = n % 2 == 0 ? x.abs() : x;
    RealNumeric power = (RealNumeric) base.pow(n, MC);
    assertThat(power.root(n, MC)).isEqualTo(base);
  }

  @Property
  void sqrtOfASquareIsExact(@ForAll("smallExacts") RealNumeric x) {
    Numeric square = x.multiply(x, MC);
    assertThat(square.sqrt(MC)).isEqualTo(x.abs());
    assertThat(x.negate().multiply(x, MC).sqrt(MC)).isEqualTo(Complex.of(Int.ZERO, x.abs()));
  }

  @Provide
  Arbitrary<List<Step>> steps() {
    Arbitrary<Step> step = Combinators.combine(Arbitraries.of(Op.class), small()).as(Step::new);
    return step.list().ofMaxSize(10);
  }

  /** Small values of every kind, so long sequences keep their operands of modest size. */
  @Provide
  Arbitrary<Numeric> small() {
    Arbitrary<RealNumeric> part =
        Arbitraries.oneOf(
            smallExacts(),
            Arbitraries.integers().between(-50, 50).map(i -> Real.of(BigDecimal.valueOf(i, 1))));
    return Arbitraries.oneOf(
        part.map(Numeric.class::cast), Combinators.combine(part, part).as(Complex::of));
  }

  @Provide
  Arbitrary<RealNumeric> smallExacts() {
    Arbitrary<Integer> numerator = Arbitraries.integers().between(-12, 12);
    Arbitrary<Integer> denominator = Arbitraries.integers().between(1, 6);
    return Combinators.combine(numerator, denominator).as((n, d) -> Rational.of(n, d));
  }

  private static Numeric apply(Step step, Numeric x) {
    Numeric y = step.operand();
    return switch (step.op()) {
      case ADD -> x.add(y, MC);
      case SUBTRACT -> x.subtract(y, MC);
      case MULTIPLY -> x.multiply(y, MC);
      case DIVIDE -> x.divide(y, MC);
      case NEGATE -> x.negate();
      case CONJ -> x.conj();
      case SQUARE -> x.pow(2, MC);
      case INVERSE -> x.pow(-1, MC);
      case SQRT -> x.sqrt(MC);
      case TO_REAL -> x.toReal(MC);
      case RE -> x.re();
      case IM -> x.im();
      case MODULUS -> x.modulus(MC);
      case POWER -> x.pow(EXPONENTS.get(Math.floorMod(y.hashCode(), EXPONENTS.size())), MC);
      case ARGUMENT -> x.argument(MC);
    };
  }
}
