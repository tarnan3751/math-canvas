package mathcanvas.engine.expr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import mathcanvas.engine.number.Numeric;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;

/** The canonical form is sound, idempotent, independent of operand order, and well shaped. */
class CanonicalProperties {

  private static final ExprOrder ORDER = ExprOrder.INSTANCE;
  private static final int POINTS = 3;

  @Provide
  Arbitrary<Expr> exact() {
    return ExprArbitraries.exactExprs();
  }

  @Provide
  Arbitrary<Expr> any() {
    return ExprArbitraries.exprs();
  }

  @Provide
  Arbitrary<List<Num>> values() {
    return ExprArbitraries.exactNumbers().list().ofSize(POINTS * 6);
  }

  /**
   * At random exact points, an expression and its canonical form have the same exact value wherever
   * both have one. A point is skipped where either side is undefined (a division by zero: the
   * canonical form may be defined where the raw one is not, {@code x·x^-1} is 1) or not exact: the
   * tower recognises an exact power only in the shapes it documents, so a combined exponent can
   * hide one ({@code z^3·z^(1/2)} is exact for a Gaussian square {@code z}, {@code z^(7/2)} is
   * computed inexactly).
   */
  @Property(tries = 3000)
  void sound(@ForAll("exact") Expr e, @ForAll("values") List<Num> values) {
    Expr canonical = Canonical.of(e);
    for (int point = 0; point < POINTS; point++) {
      Map<Expr, Numeric> at = new HashMap<>();
      List<Expr> names = new ArrayList<>(ExprArbitraries.SYMBOLS);
      names.add(Constant.PI);
      names.add(Constant.E);
      for (int k = 0; k < names.size(); k++) {
        at.put(names.get(k), values.get(point * names.size() + k).value());
      }
      TestEvaluator evaluator = new TestEvaluator(at);
      Numeric raw = evaluator.eval(e);
      Numeric value = raw == null ? null : evaluator.eval(canonical);
      Statistics.label("compared").collect(value != null);
      if (value != null) {
        assertThat(value).as("%s -> %s at %s", e, canonical, at).isEqualTo(raw);
      }
    }
    Statistics.label("compared")
        .coverage(coverage -> coverage.check(true).percentage((Double p) -> p > 30));
  }

  @Property(tries = 20_000)
  void idempotent(@ForAll("any") Expr e) {
    Expr canonical = Canonical.of(e);
    assertThat(Canonical.of(canonical)).as("%s", e).isEqualTo(canonical);
  }

  @Property(tries = 3000)
  void wellShaped(@ForAll("any") Expr e) {
    checkShape(Canonical.of(e));
  }

  /**
   * Shuffling the operands of a sum or of a product without vectors or matrices changes nothing.
   */
  @Property(tries = 3000)
  void independentOfOperandOrder(@ForAll("any") Expr e, @ForAll Random random) {
    List<Expr> operands =
        switch (e) {
          case Sum s -> new ArrayList<>(s.terms());
          case Product p when p.factors().stream().noneMatch(CanonicalProperties::linear) ->
              new ArrayList<>(p.factors());
          default -> null;
        };
    if (operands == null) {
      return;
    }
    Collections.shuffle(operands, random);
    Expr shuffled = e instanceof Sum ? Sum.of(operands) : Product.of(operands);
    assertThat(Canonical.of(shuffled)).as("%s", e).isEqualTo(Canonical.of(e));
  }

  /**
   * A term with a non-commuting part never vanishes from a sum: {@code t - t} with a matrix factor
   * leaves the zero matrix {@code 0·M}, or undefined over infinity.
   */
  @Property(tries = 3000)
  void nonCommutingTermsDoNotVanish(@ForAll("any") Expr e, @ForAll("any") Expr s) {
    Matrix m = Matrix.of(List.of(List.of(Symbol.of("m"), Num.ONE)));
    Expr t = Product.of(e, m);
    Expr canonical = Canonical.of(Sum.of(t, Exprs.neg(t), s));
    assertThat(canonical.equals(Constant.UNDEFINED) || canonical.toString().contains(m.toString()))
        .as("%s, %s -> %s", e, s, canonical)
        .isTrue();
  }

  /** Whether an expression is built by arithmetic from a vector or a matrix. */
  private static boolean linear(Expr e) {
    return switch (e) {
      case Vector _, Matrix _ -> true;
      case Power p -> linear(p.base()) || linear(p.exponent());
      case Product p -> p.factors().stream().anyMatch(CanonicalProperties::linear);
      case Sum s -> s.terms().stream().anyMatch(CanonicalProperties::linear);
      default -> false;
    };
  }

  /** The canonical invariants, at every node. */
  private static void checkShape(Expr e) {
    switch (e) {
      case Sum s -> {
        checkOperands(s.terms(), Sum.class, s);
        assertSorted(s.terms(), s);
        s.terms().forEach(CanonicalProperties::checkShape);
      }
      case Product p -> {
        checkOperands(p.factors(), Product.class, p);
        List<Expr> commuting =
            p.factors().stream().filter(f -> !(f instanceof Num) && !linear(f)).toList();
        assertSorted(commuting, p);
        int firstLinear = indexOf(p.factors(), true);
        int lastCommuting = lastIndexOf(p.factors(), false);
        assertThat(firstLinear < 0 || lastCommuting < firstLinear).as("%s", p).isTrue();
        p.factors().forEach(CanonicalProperties::checkShape);
      }
      case Power p -> {
        assertThat(p.exponent()).as("%s", p).isNotEqualTo(Num.ONE).isNotEqualTo(Num.ZERO);
        assertThat(p.base()).as("%s", p).isNotEqualTo(Num.ONE).isNotEqualTo(Constant.UNDEFINED);
        assertThat(p.exponent()).as("%s", p).isNotEqualTo(Constant.UNDEFINED);
        checkShape(p.base());
        checkShape(p.exponent());
      }
      case SetExpr s -> {
        assertSorted(s.items(), s);
        s.items().forEach(CanonicalProperties::checkShape);
      }
      default -> {}
    }
  }

  private static void checkOperands(List<Expr> operands, Class<?> kind, Expr whole) {
    assertThat(operands).as("%s", whole).hasSizeGreaterThanOrEqualTo(2);
    assertThat(operands).as("%s", whole).noneMatch(kind::isInstance);
    assertThat(operands).as("%s", whole).doesNotContain(Constant.UNDEFINED);
    long numbers = operands.stream().filter(Num.class::isInstance).count();
    assertThat(numbers).as("%s", whole).isLessThanOrEqualTo(1);
    if (numbers == 1) {
      assertThat(operands.getFirst()).as("%s", whole).isInstanceOf(Num.class);
    }
  }

  private static void assertSorted(List<Expr> items, Expr whole) {
    for (int k = 1; k < items.size(); k++) {
      assertThat(ORDER.compare(items.get(k - 1), items.get(k))).as("%s", whole).isNegative();
    }
  }

  private static int indexOf(List<Expr> factors, boolean linear) {
    for (int k = 0; k < factors.size(); k++) {
      if (linear(factors.get(k)) == linear) {
        return k;
      }
    }
    return -1;
  }

  private static int lastIndexOf(List<Expr> factors, boolean linear) {
    for (int k = factors.size() - 1; k >= 0; k--) {
      if (linear(factors.get(k)) == linear && !(factors.get(k) instanceof Num)) {
        return k;
      }
    }
    return -1;
  }
}
