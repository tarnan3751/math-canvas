package mathcanvas.engine.algebra;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;
import org.junit.jupiter.api.Test;

/** A trace may end in a terminal note that changes nothing, and nowhere else. */
class TraceChecksTest {

  private static final Expr X = Symbol.of("x");

  private static Rewriter.Result chain(Expr result, Rewrite... rewrites) {
    return new Rewriter.Result(result, new Trace(X, result, List.of(rewrites), List.of()));
  }

  private static Rewrite at(String set, String rule, Expr before, Expr after) {
    return new Rewrite(before, after, set, rule, Path.ROOT, Bindings.EMPTY);
  }

  @Test
  void aTerminalNoteMayEndATraceUnchanged() {
    for (Rewrite note :
        List.of(
            at("integrate", "none", X, X),
            at("integrate", "angle-mode", X, X),
            at("integrate", "singular", X, X),
            at("integrate", "improper", X, X),
            at("limit", "numeric", X, X),
            at("numeric", "digits", X, X))) {
      assertThatCode(() -> TraceChecks.assertChain(chain(X, note))).doesNotThrowAnyException();
    }
    assertThatCode(
            () ->
                TraceChecks.assertChain(
                    chain(
                        Num.ONE,
                        at("basic", "a", X, Num.ONE),
                        at("basic", "none", Num.ONE, Num.ONE))))
        .doesNotThrowAnyException();
  }

  @Test
  void theNumericPathMayFollowANote() {
    assertThatCode(
            () ->
                TraceChecks.assertChain(
                    chain(
                        Num.ONE,
                        at("integrate", "none", X, X),
                        at("numeric", "quadrature", X, Num.ONE))))
        .doesNotThrowAnyException();
  }

  @Test
  void anyOtherUnchangedRecordFails() {
    assertThatThrownBy(() -> TraceChecks.assertChain(chain(X, at("basic", "a", X, X))))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> TraceChecks.assertChain(chain(X, at("solve", "numeric", X, X))))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(
            () ->
                TraceChecks.assertChain(
                    chain(Num.ONE, at("integrate", "none", X, X), at("basic", "a", X, Num.ONE))))
        .isInstanceOf(AssertionError.class);
  }
}
