package mathcanvas.engine.calculus;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.statistics.Statistics;

/**
 * {@link Signs.Range} against sampled values: for random bounds on a few symbols, closed and open,
 * of every sign, and random sums, numeric multiples, products and powers of them, every value the
 * expression takes at points at, near and between the symbols' ends lies in the range found, an
 * open end never taken, and the sign the range gives is the value's.
 */
class RangeProperties {

  private static final MathContext MC = MathContext.UNLIMITED;
  private static final List<Symbol> SYMBOLS =
      List.of(Symbol.of("a"), Symbol.of("b"), Symbol.of("c"));

  /** One symbol's bounds: ends from −2 to 2 in halves, each open or closed. */
  private record Bounds(RealNumeric lo, boolean loOpen, RealNumeric hi, boolean hiOpen) {}

  private static Bounds bounds(Random r) {
    int a = r.nextInt(9) - 4;
    int b = r.nextInt(9) - 4;
    if (a > b) {
      int t = a;
      a = b;
      b = t;
    }
    boolean lo = r.nextBoolean();
    boolean hi = r.nextBoolean();
    if (a == b) {
      lo = false;
      hi = false;
    }
    return new Bounds(Rational.of(a, 2), lo, Rational.of(b, 2), hi);
  }

  private static Expr expr(Random r, int depth) {
    if (depth == 0 || r.nextInt(4) == 0) {
      return r.nextInt(4) == 0
          ? Num.of(Rational.of(r.nextInt(7) - 3, 1 + r.nextInt(2)))
          : SYMBOLS.get(r.nextInt(SYMBOLS.size()));
    }
    return switch (r.nextInt(4)) {
      case 0 -> Sum.of(expr(r, depth - 1), expr(r, depth - 1));
      case 1 ->
          Product.of(Num.of(Rational.of(r.nextInt(7) - 3, 1 + r.nextInt(2))), expr(r, depth - 1));
      case 2 -> Product.of(expr(r, depth - 1), expr(r, depth - 1));
      default -> Power.of(expr(r, depth - 1), Num.of(1 + r.nextInt(3)));
    };
  }

  /** A value of a symbol: an end it takes, a point near an end, or one between. */
  private static RealNumeric sample(Bounds b, Random r) {
    RealNumeric tiny = Rational.of(1, 1000);
    List<RealNumeric> options = new ArrayList<>();
    if (!b.loOpen()) {
      options.add(b.lo());
    }
    if (!b.hiOpen()) {
      options.add(b.hi());
    }
    if (b.lo().compareTo(b.hi()) < 0) {
      options.add((RealNumeric) b.lo().add(tiny, MC));
      options.add((RealNumeric) b.hi().subtract(tiny, MC));
      options.add((RealNumeric) b.lo().add(b.hi(), MC).divide(Num.of(2).value(), MC));
    }
    return options.get(r.nextInt(options.size()));
  }

  @Property(tries = 3000)
  void everySampledValueLiesInTheRange(@ForAll Random r) {
    Assumptions facts = Assumptions.EMPTY;
    List<Bounds> all = new ArrayList<>();
    for (Symbol s : SYMBOLS) {
      Bounds b = bounds(r);
      all.add(b);
      facts =
          facts.with(Relation.of(b.loOpen() ? Relation.Op.GT : Relation.Op.GE, s, Num.of(b.lo())));
      facts =
          facts.with(Relation.of(b.hiOpen() ? Relation.Op.LT : Relation.Op.LE, s, Num.of(b.hi())));
    }
    Expr e = Canonical.of(expr(r, 3));
    Optional<Signs.Range> range = Signs.Range.of(e, facts);
    Statistics.label("range found").collect(range.isPresent());
    if (range.isEmpty()) {
      return;
    }
    Signs.Range g = range.get();
    for (int k = 0; k < 6; k++) {
      Expr v = e;
      for (int i = 0; i < SYMBOLS.size(); i++) {
        v = Subst.of(v, SYMBOLS.get(i), Num.of(sample(all.get(i), r)), MC).orElseThrow();
      }
      RealNumeric value = (RealNumeric) ((Num) Canonical.of(v)).value();
      if (g.lo() != null) {
        int c = value.compareTo(g.lo());
        assertThat(g.loOpen() ? c > 0 : c >= 0).as("%s = %s above %s", e, value, g).isTrue();
      }
      if (g.hi() != null) {
        int c = value.compareTo(g.hi());
        assertThat(g.hiOpen() ? c < 0 : c <= 0).as("%s = %s below %s", e, value, g).isTrue();
      }
      g.sign()
          .ifPresent(
              s ->
                  assertThat(value.signum()).as("sign of %s = %s in %s", e, value, g).isEqualTo(s));
    }
  }
}
