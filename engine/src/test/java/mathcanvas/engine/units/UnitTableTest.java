package mathcanvas.engine.units;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprOrder;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Unit;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import org.junit.jupiter.api.Test;

/** The unit table: the resolution of names, the exact factors, the dimensions and the order. */
class UnitTableTest {

  private static Expr factor(String name) {
    return Units.info(Units.unit(name)).factor();
  }

  private static Num q(long num, long den) {
    return Num.of(Rational.of(num, den));
  }

  @Test
  void namesResolveToTheTableThenToAPrefixAndAUnit() {
    assertThat(Units.of("m")).isPresent();
    assertThat(Units.of("km").orElseThrow().name()).isEqualTo("km");
    assertThat(Units.of("µm")).isEqualTo(Units.of("um")).isEqualTo(Units.of("μm"));
    assertThat(Units.of("kΩ")).isEqualTo(Units.of("kohm"));
    assertThat(Units.of("Ω").orElseThrow().name()).isEqualTo("ohm");
    assertThat(Units.of("kWh").orElseThrow().name()).isEqualTo("kWh");
    assertThat(Units.of("hPa").orElseThrow().name()).isEqualTo("hPa");
    assertThat(Units.of("dam").orElseThrow().name()).isEqualTo("dam");
    // a name of the table wins over a prefixed reading
    assertThat(Units.info(Units.unit("ft")).entry().name()).isEqualTo("ft");
    assertThat(Units.info(Units.unit("pt")).entry().name()).isEqualTo("pt");
    assertThat(Units.info(Units.unit("min")).entry().name()).isEqualTo("min");
    assertThat(Units.info(Units.unit("cd")).entry().name()).isEqualTo("cd");
    // unknown names, and prefixes on units that take none
    assertThat(Units.of("tmp")).isEmpty();
    assertThat(Units.of("kft")).isEmpty();
    assertThat(Units.of("kmin")).isEmpty();
    assertThat(Units.of("m2")).isEmpty();
    assertThat(Units.of("k")).isEmpty();
  }

  @Test
  void theFactorsAreExact() {
    assertThat(factor("m")).isEqualTo(Num.ONE);
    assertThat(factor("kg")).isEqualTo(Num.ONE);
    assertThat(factor("g")).isEqualTo(q(1, 1000));
    assertThat(factor("km")).isEqualTo(Num.of(1000));
    assertThat(factor("cm")).isEqualTo(q(1, 100));
    assertThat(factor("in")).isEqualTo(q(127, 5000));
    assertThat(factor("ft")).isEqualTo(q(381, 1250));
    assertThat(factor("mi")).isEqualTo(q(201168, 125));
    assertThat(factor("lb")).isEqualTo(q(45359237, 100000000));
    assertThat(factor("cal")).isEqualTo(q(523, 125));
    assertThat(factor("atm")).isEqualTo(Num.of(101325));
    assertThat(factor("kWh")).isEqualTo(Num.of(3600000));
    assertThat(factor("yr")).isEqualTo(Num.of(31557600));
    assertThat(factor("mph")).isEqualTo(q(1397, 3125));
    assertThat(factor("gal")).isEqualTo(q(473176473, 125000000000L));
    assertThat(factor("acre")).isEqualTo(q(316160658, 78125));
    assertThat(factor("psi")).isEqualTo(q(8896443230521L, 1290320000));
    assertThat(factor("hp")).isEqualTo(q(37284993579113511L, 50000000000000L));
    assertThat(factor("eV"))
        .isEqualTo(
            Num.of(
                Rational.of(
                    new java.math.BigInteger("801088317"),
                    new java.math.BigInteger("5000000000000000000000000000"))));
    assertThat(factor("deg")).isEqualTo(Canonical.of(Product.of(q(1, 180), Constant.PI)));
    assertThat(factor("um")).isEqualTo(q(1, 1000000));
  }

  @Test
  void everyFactorIsAnExactRationalButTheDegrees() {
    for (Unit u : Units.all()) {
      Expr f = Units.info(u).factor();
      if (u.name().equals("deg")) {
        continue;
      }
      assertThat(f).as(u.name()).isInstanceOf(Num.class);
      RealNumeric r = (RealNumeric) ((Num) f).value();
      assertThat(r.isExact()).as(u.name()).isTrue();
      assertThat(r.signum()).as(u.name()).isPositive();
    }
  }

  @Test
  void everyUnitReadsBackFromItsNameAndItsUnicodeSpelling() {
    Set<String> names = new HashSet<>();
    for (Unit u : Units.all()) {
      assertThat(names.add(u.name())).as(u.name()).isTrue();
      assertThat(Units.of(u.name())).as(u.name()).contains(u);
      assertThat(Units.of(Units.info(u).unicode())).as(u.name()).contains(u);
    }
  }

  @Test
  void theDimensions() {
    assertThat(Units.info(Units.unit("N")).dimension())
        .isEqualTo(Dimension.of(1, 1, -2, 0, 0, 0, 0));
    assertThat(Units.info(Units.unit("kWh")).dimension())
        .isEqualTo(Units.info(Units.unit("J")).dimension());
    assertThat(Units.info(Units.unit("rad")).dimension().isNone()).isTrue();
    assertThat(Dimension.of(0, 2, 0, 0, 0, 0, 0).pow(Rational.of(1, 2)))
        .isEqualTo(Dimension.of(0, 1, 0, 0, 0, 0, 0));
    assertThat(Dimension.of(1, 0, 0, 0, 0, 0, 0).times(Dimension.of(-1, 1, 0, 0, 0, 0, 0)))
        .isEqualTo(Dimension.of(0, 1, 0, 0, 0, 0, 0));
    assertThat(Units.info(Units.unit("degF")).offset()).isEqualTo(Rational.of(45967, 100));
    assertThat(Units.info(Units.unit("degC")).offset()).isEqualTo(Rational.of(5463, 20));
    assertThat(Units.info(Units.unit("K")).offset()).isNull();
  }

  @Test
  void theOrderPutsDerivedUnitsFirstAndTheCoherentUnitBeforeItsPrefixes() {
    List<String> names = List.of("km", "N", "g", "m", "cm", "kg", "s", "h", "J", "ft");
    List<String> sorted =
        names.stream().map(Units::unit).sorted(ExprOrder.INSTANCE).map(Unit::name).toList();
    assertThat(sorted).containsExactly("N", "J", "kg", "g", "m", "cm", "km", "s", "h", "ft");
  }
}
