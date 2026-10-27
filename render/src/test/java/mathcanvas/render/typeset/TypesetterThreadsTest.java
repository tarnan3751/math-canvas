package mathcanvas.render.typeset;

import static mathcanvas.render.typeset.Boxes.TYPESETTER;
import static mathcanvas.render.typeset.Boxes.all;
import static mathcanvas.render.typeset.Boxes.held;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.CancelledException;
import mathcanvas.engine.print.layout.Cls;
import mathcanvas.engine.print.layout.Layout;
import mathcanvas.engine.print.layout.Row;
import mathcanvas.engine.print.layout.Style;
import mathcanvas.engine.print.layout.Text;
import mathcanvas.render.font.OpenTypeFont;
import org.junit.jupiter.api.Test;

/** Typesetting from many threads at once, stopping on request, and the node cap. */
class TypesetterThreadsTest {

  /** About 1,050 layout nodes of every common construct. */
  private static final Layout LARGE =
      held("x_1^2/(x + 1) + sqrt(y + 2)·sin(z) - [[1, 2], [3, 4]] + ".repeat(45) + "1");

  @Test
  void eightThreadsTypesetTheSameTree() throws Exception {
    Box expected = TYPESETTER.typeset(LARGE, 20, true);
    // A fresh typesetter, so the threads also race on the font's outline cache.
    Typesetter shared = new Typesetter(OpenTypeFont.stix(), Boxes.FIXED);
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<Box>> results = new ArrayList<>();
      for (int k = 0; k < 8; k++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return shared.typeset(LARGE, 20, true);
                }));
      }
      start.countDown();
      for (Future<Box> result : results) {
        assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo(expected);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void aRequestStopsTypesettingAtEveryBox() {
    long[] checks = {0};
    Cancellation counting =
        () -> {
          checks[0]++;
          return false;
        };
    Box root = TYPESETTER.typeset(LARGE, 20, true, counting);
    assertThat(root).isEqualTo(TYPESETTER.typeset(LARGE, 20, true));
    // At least one check per box built.
    assertThat(checks[0]).isGreaterThanOrEqualTo(all(root).size());
    for (long n = 1; n < checks[0]; n *= 3) {
      long tripAt = n;
      long[] seen = {0};
      Cancellation trip = () -> ++seen[0] >= tripAt;
      assertThatThrownBy(() -> TYPESETTER.typeset(LARGE, 20, true, trip))
          .isInstanceOf(CancelledException.class);
      assertThat(seen[0]).isEqualTo(tripAt);
    }
    // An interrupt counts as a request.
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> TYPESETTER.typeset(LARGE, 20, true, Cancellation.NEVER))
          .isInstanceOf(CancelledException.class);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void theNodeCapCountsLayoutNodes() {
    assertThat(Typesetter.wouldExceed(LARGE)).isFalse();
    List<Layout> items =
        new ArrayList<>(
            Collections.nCopies(
                Typesetter.MAX_TYPESET_NODES - 1, new Text("1", Style.UPRIGHT, Cls.ORD, null)));
    // The row and its items: exactly the cap.
    assertThat(Typesetter.wouldExceed(new Row(items, null))).isFalse();
    items.add(new Text("2", Style.UPRIGHT, Cls.ORD, null));
    assertThat(Typesetter.wouldExceed(new Row(items, null))).isTrue();
  }
}
