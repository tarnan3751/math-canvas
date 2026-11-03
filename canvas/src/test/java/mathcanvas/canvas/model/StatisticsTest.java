package mathcanvas.canvas.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

/** The statistical plots over the tests' sampler, which reads literal data only. */
class StatisticsTest {

  private static final StubSampler SAMPLER = new StubSampler(Map.of("w", 2.0));

  @Test
  void theDefaultTableReadsALiteralVectorOrMatrix() {
    double[][] t =
        SAMPLER
            .table(
                Parser.parseExpression("[[1, 2], [3, 4], [5, 6]]", Names.NONE), Cancellation.NEVER)
            .orElseThrow();
    assertThat(t[0]).containsExactly(1, 3, 5);
    assertThat(t[1]).containsExactly(2, 4, 6);
    assertThat(
            SAMPLER.table(Vector.of(Num.of(7), Symbol.of("w")), Cancellation.NEVER)
                .orElseThrow()[0])
        .containsExactly(7, 2);
    // a name has no value here, and a free symbol none anywhere
    assertThat(SAMPLER.table(Symbol.of("data"), Cancellation.NEVER)).isEmpty();
    assertThat(SAMPLER.table(Vector.of(Num.ONE, Symbol.of("q")), Cancellation.NEVER)).isEmpty();
  }

  @Test
  void aHistogramOverTheStubReadsItsWidthThroughTheSampler() {
    Histogram h =
        new Histogram(
            new ObjectId(1),
            Style.DEFAULT,
            Vector.of(Num.of(1), Num.of(2), Num.of(3), Num.of(6)),
            Optional.of(Symbol.of("w")),
            Optional.empty());
    Geometry.Bins b = (Geometry.Bins) Statistics.histogram(h, SAMPLER, Cancellation.NEVER);
    assertThat(b.bars().stream().map(Geometry.Bar::height).toList())
        .isEqualTo(List.of(2.0, 1.0, 1.0));
    assertThat(h.reads()).containsExactly("w");
  }

  @Test
  void sturgesCountIsTheCeilingOfLogTwoPlusOne() {
    assertThat(Statistics.sturges(1)).isEqualTo(1);
    assertThat(Statistics.sturges(8)).isEqualTo(4);
    assertThat(Statistics.sturges(9)).isEqualTo(5);
    assertThat(Statistics.sturges(10_000)).isEqualTo(15);
  }
}
