package mathcanvas.engine.print;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.CancelledException;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import org.junit.jupiter.api.Test;

/** Printing a large tree, as text or as a layout, stops at a request at any node. */
class PrintCancellationTest {

  /** A sum of 10,000 powers of x. */
  private static Expr large() {
    List<Expr> terms = new ArrayList<>();
    for (int k = 1; k <= 10_000; k++) {
      terms.add(Power.of(Symbol.of("x"), Num.of(k)));
    }
    return Canonical.of(Sum.of(terms.toArray(Expr[]::new)));
  }

  @Test
  void anUnrequestedTokenPrintsWhatThePrinterPrints() {
    Expr e = large();
    long[] checks = {0};
    Cancellation counting =
        () -> {
          checks[0]++;
          return false;
        };

    assertThat(UnicodePrinter.INSTANCE.print(e, counting))
        .isEqualTo(UnicodePrinter.INSTANCE.print(e));
    assertThat(checks[0]).isGreaterThanOrEqualTo(10_000);
    assertThat(AsciiPrinter.INSTANCE.print(e, Cancellation.NEVER))
        .isEqualTo(AsciiPrinter.INSTANCE.print(e));
    assertThat(LayoutPrinter.INSTANCE.print(e, Cancellation.NEVER))
        .isEqualTo(LayoutPrinter.INSTANCE.print(e));
  }

  @Test
  void aRequestedTokenStopsALargePrintAtAnyNode() {
    Expr e = large();
    for (long tripAt : new long[] {1, 2, 100, 9_999, 20_000}) {
      assertThatThrownBy(() -> UnicodePrinter.INSTANCE.print(e, trip(tripAt)))
          .isInstanceOf(CancelledException.class);
      assertThatThrownBy(() -> AsciiPrinter.INSTANCE.print(e, trip(tripAt)))
          .isInstanceOf(CancelledException.class);
      assertThatThrownBy(() -> LayoutPrinter.INSTANCE.print(e, trip(tripAt)))
          .isInstanceOf(CancelledException.class);
      assertThatThrownBy(
              () -> UnicodePrinter.INSTANCE.withFormat(Format.decimal(5)).print(e, trip(tripAt)))
          .isInstanceOf(CancelledException.class);
    }
    // a display checks while it prints too, past the shape it builds
    assertThatThrownBy(() -> UnicodePrinter.INSTANCE.display(e, trip(5_000)))
        .isInstanceOf(CancelledException.class);
    assertThatThrownBy(() -> LayoutPrinter.INSTANCE.display(e, trip(5_000)))
        .isInstanceOf(CancelledException.class);
  }

  /** A token requested from its {@code n}th check on. */
  private static Cancellation trip(long n) {
    long[] seen = {0};
    return () -> ++seen[0] >= n;
  }
}
