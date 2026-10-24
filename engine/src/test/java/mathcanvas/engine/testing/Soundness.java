package mathcanvas.engine.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.assume.Facts;
import mathcanvas.engine.assume.Facts.Bound;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

/**
 * Numeric soundness checks for rule sets: a rewrite and its source must agree at points drawn
 * within the assumptions.
 *
 * <p>Values are compared at 34 digits within a relative tolerance: {@code 1e-20} when both
 * expressions are exact, {@code 1e-12} when either holds an inexact number, whose arithmetic
 * rounds; with an absolute floor of {@code 1e-25} for values that cancel to nearly zero. A point
 * where either side is undefined is skipped.
 */
public final class Soundness {

  /** The precision of the comparison. */
  public static final MathContext PRECISION = MathContext.DECIMAL128;

  private static final BigDecimal EXACT_TOLERANCE = new BigDecimal("1e-20");
  private static final BigDecimal INEXACT_TOLERANCE = new BigDecimal("1e-12");
  private static final BigDecimal FLOOR = new BigDecimal("1e-25");

  private Soundness() {}

  /**
   * Asserts that {@code before} and {@code after} agree at every point where both are defined.
   *
   * @param before the expression rewritten
   * @param after its rewrite
   * @param points the points, each a value per symbol
   * @return how many points were compared
   */
  public static int check(Expr before, Expr after, List<Map<Symbol, Numeric>> points) {
    BigDecimal tolerance = inexact(before) || inexact(after) ? INEXACT_TOLERANCE : EXACT_TOLERANCE;
    int compared = 0;
    for (Map<Symbol, Numeric> point : points) {
      Optional<Numeric> a = NumericEvaluator.eval(before, point, PRECISION);
      Optional<Numeric> b = NumericEvaluator.eval(after, point, PRECISION);
      if (a.isEmpty() || b.isEmpty()) {
        continue;
      }
      BigDecimal difference = magnitude(a.get().subtract(b.get(), PRECISION));
      BigDecimal scale = magnitude(a.get()).max(magnitude(b.get()));
      assertThat(difference)
          .as("%s and %s at %s: %s vs %s", before, after, point, a.get(), b.get())
          .isLessThanOrEqualTo(tolerance.multiply(scale).add(FLOOR));
      compared++;
    }
    return compared;
  }

  /**
   * Returns points for {@code symbols} that satisfy {@code assumptions}: integers in their window
   * and parity, rationals within their bounds for the other real symbols, and Gaussian rationals
   * for the symbols assumed complex.
   *
   * @param symbols the symbols
   * @param assumptions the facts the points satisfy
   * @return the generator of points
   */
  public static Arbitrary<Map<Symbol, Numeric>> points(
      Collection<Symbol> symbols, Assumptions assumptions) {
    List<Symbol> list = List.copyOf(symbols);
    return Arbitraries.randomValue(
        random -> {
          Map<Symbol, Numeric> point = new LinkedHashMap<>();
          for (Symbol s : list) {
            point.put(s, value(assumptions.facts(s), random));
          }
          return point;
        });
  }

  /**
   * Returns a list of {@code count} points for {@code symbols} within {@code assumptions}.
   *
   * @param symbols the symbols
   * @param assumptions the facts the points satisfy
   * @param count how many
   * @param random the source
   * @return the points
   */
  public static List<Map<Symbol, Numeric>> points(
      Collection<Symbol> symbols, Assumptions assumptions, int count, Random random) {
    List<Map<Symbol, Numeric>> out = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Map<Symbol, Numeric> point = new LinkedHashMap<>();
      for (Symbol s : symbols) {
        point.put(s, value(assumptions.facts(s), random));
      }
      out.add(point);
    }
    return out;
  }

  private static Numeric value(Facts f, Random random) {
    if (f.domain() == Facts.Domain.COMPLEXES) {
      Numeric z = Complex.of(rational(random, 10), rational(random, 10));
      return f.nonZero() && z.isZero() ? Int.ONE : z;
    }
    for (int attempt = 0; attempt < 1000; attempt++) {
      RealNumeric candidate = candidate(f, random);
      if (satisfies(f, candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("no point found for " + f);
  }

  private static RealNumeric candidate(Facts f, Random random) {
    boolean integer = f.domain() == Facts.Domain.INTEGERS;
    RealNumeric offset = integer ? Int.of(random.nextInt(41)) : positive(random);
    if (f.lower() != null && (f.upper() == null || random.nextBoolean())) {
      return (RealNumeric) start(f.lower().value(), integer).add(offset, PRECISION);
    }
    if (f.upper() != null) {
      return (RealNumeric) start(f.upper().value(), integer).subtract(offset, PRECISION);
    }
    return integer ? Int.of(random.nextInt(41) - 20) : rational(random, 10);
  }

  /** The bound, as an integer for an integer symbol. */
  private static RealNumeric start(RealNumeric bound, boolean integer) {
    return integer ? Int.of(floor(bound)) : bound;
  }

  private static BigInteger floor(RealNumeric v) {
    RealNumeric f = v.floor();
    return switch (f) {
      case Int i -> i.value();
      case mathcanvas.engine.number.Real r -> r.value().toBigIntegerExact();
      case Rational q -> throw new IllegalStateException("the floor of a rational is an integer");
    };
  }

  private static RealNumeric positive(Random random) {
    return Rational.of(random.nextInt(200) + 1, random.nextInt(10) + 1);
  }

  private static RealNumeric rational(Random random, int range) {
    return Rational.of(random.nextInt(2 * range * 8 + 1) - range * 8, random.nextInt(8) + 1);
  }

  private static boolean satisfies(Facts f, RealNumeric v) {
    if (!holds(f.lower(), v, 1) || !holds(f.upper(), v, -1)) {
      return false;
    }
    if (f.nonZero() && v.isZero()) {
      return false;
    }
    if (f.parity() != Facts.Parity.NONE) {
      boolean odd = v instanceof Int i && i.value().testBit(0);
      return odd == (f.parity() == Facts.Parity.ODD);
    }
    return true;
  }

  private static boolean holds(Bound b, RealNumeric v, int direction) {
    if (b == null) {
      return true;
    }
    int order = v.compareValue(b.value()) * direction;
    return order > 0 || (order == 0 && !b.strict());
  }

  private static BigDecimal magnitude(Numeric v) {
    return v.modulus(PRECISION).toReal(PRECISION).value().abs();
  }

  private static boolean inexact(Expr e) {
    if (e instanceof Num n) {
      return !n.value().isExact();
    }
    for (Expr o : Operands.of(e)) {
      if (inexact(o)) {
        return true;
      }
    }
    return false;
  }
}
