package mathcanvas.engine.discrete;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.SetExpr;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

/**
 * The set identities at random sets of small integers: commutativity, distributivity, De Morgan's
 * laws for the difference, the symmetric difference, inclusion and exclusion, and the sizes of the
 * power set and the Cartesian product.
 */
class SetProperties {

  private static SetExpr set(Random r) {
    List<Expr> items = new ArrayList<>();
    for (int i = 0; i < r.nextInt(7); i++) {
      items.add(Num.of(r.nextInt(9)));
    }
    return (SetExpr) Canonical.of(new SetExpr(items));
  }

  private static SetExpr op(String name, Expr a, Expr b) {
    return (SetExpr) Sets.apply(name, List.of(a, b), Cancellation.NEVER);
  }

  private static int size(Expr s) {
    return ((SetExpr) s).items().size();
  }

  @Property(tries = 300, seed = "8501")
  void theIdentitiesHold(@ForAll long seed) {
    Random r = new Random(seed);
    SetExpr a = set(r);
    SetExpr b = set(r);
    SetExpr c = set(r);
    assertThat(op("union", a, b)).isEqualTo(op("union", b, a));
    assertThat(op("intersect", a, b)).isEqualTo(op("intersect", b, a));
    assertThat(op("union", a, op("intersect", b, c)))
        .isEqualTo(op("intersect", op("union", a, b), op("union", a, c)));
    assertThat(op("intersect", a, op("union", b, c)))
        .isEqualTo(op("union", op("intersect", a, b), op("intersect", a, c)));
    assertThat(op("setdiff", a, op("union", b, c)))
        .isEqualTo(op("intersect", op("setdiff", a, b), op("setdiff", a, c)));
    assertThat(op("setdiff", a, op("intersect", b, c)))
        .isEqualTo(op("union", op("setdiff", a, b), op("setdiff", a, c)));
    assertThat(op("symdiff", a, b))
        .isEqualTo(op("setdiff", op("union", a, b), op("intersect", a, b)));
    assertThat(size(op("union", a, b))).isEqualTo(size(a) + size(b) - size(op("intersect", a, b)));
    assertThat(size(Sets.apply("subsets", List.of(a), Cancellation.NEVER))).isEqualTo(1 << size(a));
    assertThat(size(op("cartesian", a, b))).isEqualTo(size(a) * size(b));
    for (Expr x : a.items()) {
      assertThat(Sets.member(x, op("union", a, b))).isEqualTo(mathcanvas.engine.assume.Tri.TRUE);
      assertThat(Sets.member(x, op("setdiff", b, a))).isEqualTo(mathcanvas.engine.assume.Tri.FALSE);
    }
  }
}
