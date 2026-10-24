package mathcanvas.engine.expr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** The order is total, antisymmetric, transitive and consistent with {@code equals}. */
class ExprOrderProperties {

  private static final ExprOrder ORDER = ExprOrder.INSTANCE;

  /** Raw expressions of every kind. */
  @Provide
  Arbitrary<Expr> any() {
    return Arbitraries.oneOf(List.of(ExprArbitraries.exprs(), ExprArbitraries.exactExprs()));
  }

  /** Lists drawn with many duplicates and near-duplicates, so ties are common. */
  @Provide
  Arbitrary<List<Expr>> lists() {
    return Arbitraries.oneOf(List.of(ExprArbitraries.exprs(), ExprArbitraries.exactExprs()))
        .injectDuplicates(0.2)
        .list()
        .ofMinSize(3)
        .ofMaxSize(8);
  }

  @Property(tries = 2000)
  void consistentWithEquals(@ForAll("any") Expr u, @ForAll("any") Expr v) {
    assertThat(ORDER.compare(u, v) == 0).isEqualTo(u.equals(v));
    assertThat(ORDER.compare(u, u)).isZero();
  }

  @Property(tries = 2000)
  void antisymmetric(@ForAll("any") Expr u, @ForAll("any") Expr v) {
    assertThat(Integer.signum(ORDER.compare(u, v))).isEqualTo(-Integer.signum(ORDER.compare(v, u)));
  }

  /** After sorting, every pair is in order: a cycle among any three would show here. */
  @Property(tries = 2000)
  void transitive(@ForAll("lists") List<Expr> exprs) {
    List<Expr> sorted = new ArrayList<>(exprs);
    sorted.sort(ORDER);
    for (int i = 0; i < sorted.size(); i++) {
      for (int j = i + 1; j < sorted.size(); j++) {
        assertThat(ORDER.compare(sorted.get(i), sorted.get(j)))
            .as("%s vs %s", sorted.get(i), sorted.get(j))
            .isLessThanOrEqualTo(0);
      }
    }
    for (int i = 0; i + 2 < exprs.size(); i++) {
      Expr a = exprs.get(i);
      Expr b = exprs.get(i + 1);
      Expr c = exprs.get(i + 2);
      if (ORDER.compare(a, b) <= 0 && ORDER.compare(b, c) <= 0) {
        assertThat(ORDER.compare(a, c)).as("%s, %s, %s", a, b, c).isLessThanOrEqualTo(0);
      }
    }
  }
}
