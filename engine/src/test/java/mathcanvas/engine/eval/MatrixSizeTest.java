package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import mathcanvas.engine.CancelledException;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.UnicodePrinter;
import org.junit.jupiter.api.Test;

/** The node cap is applied while vector and matrix arithmetic builds a value. */
class MatrixSizeTest {

  private static final String SIX =
      "M := [[a,b,c,d,e1,f],[g,h,i1,j,k,l],[m,n,o,p,q,r],[s,t,u,v,w,x1],"
          + "[y,z,a2,b2,c2,d2],[e2,f2,g2,h2,i2,j2]]";

  private static Outcome run(Session s, String statement) {
    return s.run(Parser.parseScript(statement, s.names())).getLast();
  }

  private static Outcome run(Session s, String statement, Tripping token) {
    return s.run(Parser.parseScript(statement, s.names()).statements().getFirst(), token);
  }

  private static Session six() {
    Session s = new Session();
    run(s, SIX);
    return s;
  }

  private static void assertTooLarge(Outcome o) {
    assertThat(o).isInstanceOf(Outcome.Failed.class);
    assertThat(((Outcome.Failed) o).error().getMessage()).isEqualTo("the result is too large");
  }

  /** Whether the failure was thrown while the matrix was built, not by the finished value. */
  private static boolean refusedWhileBuilt(Outcome o) {
    return Arrays.stream(((Outcome.Failed) o).error().getStackTrace())
        .anyMatch(
            f -> f.getClassName().endsWith("LinearAlgebra") && f.getMethodName().equals("counted"));
  }

  @Test
  void aChainOfSymbolicProductsIsRefusedWhileBuilt() {
    Session s = six();
    long start = System.nanoTime();
    Outcome o = run(s, "M*M*M*M*M*M*M*M");
    long millis = (System.nanoTime() - start) / 1_000_000;
    assertTooLarge(o);
    assertThat(refusedWhileBuilt(o)).isTrue();
    // typically 0.5 to 1 s locally, 15 s before the cap applied while building
    // (docs/reference/engine.md, Timing budgets)
    assertThat(millis).isLessThan(120_000);
    assertThat(run(s, "1 + 1"))
        .isEqualTo(new Outcome.Value(Num.of(2), mathcanvas.engine.rule.Trace.NONE, false));
  }

  @Test
  void productsThatFitAreUnchanged() throws NoSuchAlgorithmException {
    Session s = new Session();
    run(s, "N := [[a,b,c,d],[e1,f,g,h],[i1,j,k,l],[m,n,o,p]]");
    Outcome o = run(s, "N*N*N*N");
    assertThat(o).isInstanceOf(Outcome.Value.class);
    String shown = UnicodePrinter.INSTANCE.display(((Outcome.Value) o).value());
    // the display of the build before the cap applied while building (a491053)
    assertThat(shown).hasSize(7648);
    assertThat(
            HexFormat.of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256")
                        .digest(shown.getBytes(StandardCharsets.UTF_8))))
        .isEqualTo("117ce77002cc2fa1a0e37f3c73879e60151831340672c1c1b62af29a5ba1e74a");
    Session big = six();
    assertThat(run(big, "M*M*M*M")).isInstanceOf(Outcome.Value.class);
    assertThat(run(big, "M^12")).isInstanceOf(Outcome.Value.class);
  }

  @Test
  void aPowerIsRefusedWithinItsLastSquaring() {
    Tripping power = Tripping.counting();
    Outcome o = run(six(), "M^16", power);
    assertTooLarge(o);
    assertThat(refusedWhileBuilt(o)).isTrue();
    // M^12 builds every product and simplifies its value; the refusal of M^16 stops sooner
    Tripping fits = Tripping.counting();
    assertThat(run(six(), "M^12", fits)).isInstanceOf(Outcome.Value.class);
    assertThat(power.checks()).isLessThan(fits.checks());
  }

  @Test
  void theBackstopTurnsAnOutOfMemoryErrorIntoAnOutcome() {
    Symbol x = Symbol.of("x");
    Outcome o =
        Session.guarded(
            () -> {
              throw new OutOfMemoryError("Java heap space");
            },
            x);
    assertTooLarge(o);
    assertThat(((Outcome.Failed) o).error().node()).isSameAs(x);
    Outcome defect =
        Session.guarded(
            () -> {
              throw new IllegalStateException("bug");
            },
            x);
    assertThat(((Outcome.Failed) defect).error().getMessage())
        .startsWith("internal error: java.lang.IllegalStateException");
    assertThatThrownBy(
            () ->
                Session.guarded(
                    () -> {
                      throw new CancelledException();
                    },
                    x))
        .isInstanceOf(CancelledException.class);
  }
}
