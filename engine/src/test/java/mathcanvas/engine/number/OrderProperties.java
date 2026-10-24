package mathcanvas.engine.number;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * The total order on the non-complex kinds, over pools rich in ties: the same value built
 * differently, the same value in another kind, and values one tiny step apart.
 */
class OrderProperties {

  @Property
  void compareToIsConsistentWithEquals(
      @ForAll("pool") RealNumeric a, @ForAll("pool") RealNumeric b) {
    assertThat(a.compareTo(b) == 0).isEqualTo(a.equals(b));
    assertThat(Integer.signum(a.compareTo(b))).isEqualTo(-Integer.signum(b.compareTo(a)));
    if (a.equals(b)) {
      assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
  }

  @Property
  void compareToIsTransitive(
      @ForAll("pool") RealNumeric a, @ForAll("pool") RealNumeric b, @ForAll("pool") RealNumeric c) {
    if (a.compareTo(b) <= 0 && b.compareTo(c) <= 0) {
      assertThat(a.compareTo(c)).isLessThanOrEqualTo(0);
    }
    if (a.compareValue(b) <= 0 && b.compareValue(c) <= 0) {
      assertThat(a.compareValue(c)).isLessThanOrEqualTo(0);
    }
  }

  /** {@code compareValue} is the order of the exact values; {@code compareTo} refines its ties. */
  @Property
  void compareValueIsTheMathematicalOrder(
      @ForAll("pool") RealNumeric a, @ForAll("pool") RealNumeric b) {
    int expected = exactly(a).compareTo(exactly(b));
    assertThat(Integer.signum(a.compareValue(b))).isEqualTo(Integer.signum(expected));
    if (expected != 0) {
      assertThat(Integer.signum(a.compareTo(b))).isEqualTo(Integer.signum(expected));
    } else if (a.isExact() != b.isExact()) {
      assertThat(a.compareTo(b)).isEqualTo(a.isExact() ? -1 : 1);
    }
  }

  /** The same value built two ways is one record: promotion never yields a second form. */
  @Property
  void equalValuesOfOneKindAreOneRecord(@ForAll("pool") RealNumeric a) {
    RealNumeric rebuilt = NumberArbitraries.rebuilt(a);
    assertThat(rebuilt).isEqualTo(a);
    assertThat(rebuilt.hashCode()).isEqualTo(a.hashCode());
    assertThat(rebuilt.getClass()).isEqualTo(a.getClass());
  }

  @Provide
  Arbitrary<RealNumeric> pool() {
    return NumberArbitraries.neighbours(NumberArbitraries.realNumerics());
  }

  /** The value to 400 digits: enough to separate every pair the pool produces. */
  private static BigDecimal exactly(RealNumeric x) {
    return x.toReal(new MathContext(400)).value();
  }
}
