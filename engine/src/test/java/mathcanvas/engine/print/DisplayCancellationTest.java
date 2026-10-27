package mathcanvas.engine.print;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.CancelledException;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Symbol;
import org.junit.jupiter.api.Test;

/** Ordering a set of constants for display stops at a request. */
class DisplayCancellationTest {

  @Test
  void aThousandMemberSetStopsAtEveryMember() {
    List<Expr> members = new ArrayList<>();
    for (int k = 1; k <= 1000; k++) {
      members.add(Apply.of(Symbol.of("sin"), List.of(Num.of(k))));
    }
    Expr set = Canonical.of(new SetExpr(members));
    long[] checks = {0};
    Cancellation counting =
        () -> {
          checks[0]++;
          return false;
        };
    assertThat(Display.of(set, counting)).isEqualTo(Display.of(set));
    assertThat(checks[0]).isGreaterThanOrEqualTo(1000);
    for (long n = 1; n < checks[0]; n *= 3) {
      long tripAt = n;
      long[] seen = {0};
      Cancellation trip = () -> ++seen[0] >= tripAt;
      assertThatThrownBy(() -> UnicodePrinter.INSTANCE.display(set, trip))
          .isInstanceOf(CancelledException.class);
    }
  }
}
