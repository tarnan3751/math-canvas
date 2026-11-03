package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import mathcanvas.engine.expr.Unit;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.units.Dimension;
import mathcanvas.engine.units.Units;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Conversions round-trip exactly (the acceptance criterion of Phase 8, §14.26): every unit of the
 * table, prefixed or not, converts to the coherent SI base units and back to itself, and converting
 * through any unit of a dimension is converting directly.
 */
class ConvertProperties {

  private static final List<Unit> UNITS = Units.all();

  /** Units and unit products of one dimension each. */
  private static final List<List<String>> GROUPS =
      List.of(
          List.of("_m", "_cm", "_km", "_in", "_ft", "_yd", "_mi", "_nmi", "_um", "_dam"),
          List.of("_s", "_ms", "_min", "_h", "_d", "_yr", "_ns"),
          List.of("_J", "_kWh", "_eV", "_cal", "_kcal", "_N _m", "_W _h", "_MeV", "_kg _m^2/_s^2"),
          List.of("_m/_s", "_km/_h", "_mph", "_ft/_s", "_nmi/_h"),
          List.of("_kg", "_g", "_lb", "_oz", "_st", "_t", "_ton", "_mg"),
          List.of("_L", "_gal", "_qt", "_pt", "_floz", "_m^3", "_mL", "_in^3"),
          List.of("_Pa", "_bar", "_atm", "_psi", "_hPa", "_N/_m^2"),
          List.of("_W", "_hp", "_kW", "_J/_s"),
          List.of("_K", "_degC", "_degF", "_mK"),
          List.of("_rad", "_deg", "_mrad"));

  /** The coherent base units of a dimension, as typed: {@code _kg _m^2 _s^-2}. */
  private static String base(Dimension d) {
    if (d.isNone()) {
      return "_rad";
    }
    StringBuilder out = new StringBuilder();
    for (int k = 0; k < Dimension.BASE.size(); k++) {
      RealNumeric e = d.exponents().get(k);
      if (!e.isZero()) {
        out.append(out.isEmpty() ? "" : " ").append('_').append(Dimension.BASE.get(k));
        out.append("^(").append(e).append(')');
      }
    }
    return out.toString();
  }

  @Provide
  Arbitrary<Integer> units() {
    return Arbitraries.integers().between(0, UNITS.size() - 1);
  }

  @Property(tries = 400)
  void everyUnitConvertsToBaseAndBackExactly(@ForAll("units") int index) {
    Unit u = UNITS.get(index);
    String q = "7/3 _" + u.name();
    String back =
        "convert(convert(" + q + ", " + base(Units.info(u).dimension()) + "), _" + u.name() + ")";
    assertThat(Run.value(back)).as(back).isEqualTo(Run.value(q));
  }

  record Path(String q, String via, String to) {}

  @Provide
  Arbitrary<Path> paths() {
    return Arbitraries.randomValue(
        (Random r) -> {
          List<String> g = GROUPS.get(r.nextInt(GROUPS.size()));
          String magnitude =
              r.nextBoolean()
                  ? String.valueOf(1 + r.nextInt(500))
                  : (1 + r.nextInt(99)) + "/" + (1 + r.nextInt(97));
          return new Path(
              magnitude + " " + g.get(r.nextInt(g.size())),
              g.get(r.nextInt(g.size())),
              g.get(r.nextInt(g.size())));
        });
  }

  @Property(tries = 300)
  void convertingThroughAUnitIsConvertingDirectly(@ForAll("paths") Path p) {
    String through = "convert(convert(" + p.q() + ", " + p.via() + "), " + p.to() + ")";
    String direct = "convert(" + p.q() + ", " + p.to() + ")";
    assertThat(Run.value(through)).as(through).isEqualTo(Run.value(direct));
  }
}
