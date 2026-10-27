package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.parse.Script;
import mathcanvas.engine.parse.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A statement cancelled at any of its checks gives {@link Outcome.Cancelled}, leaves the
 * environment as it was, and returns soon after the request: each case exercises one of the loops
 * that check.
 */
class SessionCancellationTest {

  /**
   * One statement after its setup, at a precision.
   *
   * @param name what it exercises
   * @param precision the session's digits
   * @param setup statements run first, uncancelled
   * @param statement the statement cancelled
   */
  record Case(String name, int precision, String setup, String statement) {
    @Override
    public String toString() {
      return name;
    }
  }

  static Stream<Case> cases() {
    String radicals = radicals(1000);
    return Stream.of(
        new Case("the rewriter", 34, "assume(x > 0)", radicals),
        new Case("an expansion", 34, "", "expand((x + y + z + w + 1)^20)"),
        new Case("a symbolic matrix power", 34, MATRIX, "M^12"),
        new Case("partial fractions", 34, "", "apart(1/((x-1)^20 (x+2)^20 (x^2+1)^10), x)"),
        new Case("a binder's body", 34, "assume(x > 0)", "sum(" + radicals(200) + ", x, 1, 3)"),
        new Case("Kronecker's search", 34, "", "factor(x^8 + 30x + 1440)"),
        new Case(
            "a multivariate gcd", 34, "", "gcd(expand((x+y+1)^9 (x-y)), expand((x+y+1)^9 (x+2y)))"),
        new Case("a numeric solve", 34, "", "solve(x^60 - 3x + 1 = 0, x)"),
        new Case("the scan", 34, "", "solve(sin(x) = x/1000, x)"),
        new Case("the agreement loop", 34, "", "numeric(e, 2000)"),
        new Case("an inverse hyperbolic function", 34, "", "numeric(asinh(1/3), 500)"),
        new Case("a fold in the canonical form", 1000, "", "2.0^(1/3)"),
        new Case("assume", 34, "a := 2", "assume(x > numeric(pi, 400) + a)"));
  }

  /** A symbolic 6×6 matrix. */
  private static final String MATRIX =
      "M := [[a,b,c,d,e1,f],[g,h,i1,j,k,l],[m,n,o,p,q,r],[s,t,u,v,w,x1],"
          + "[y,z,a2,b2,c2,d2],[e2,f2,g2,h2,i2,j2]]";

  /**
   * A sum of {@code n} terms of radicals, absolute values and logarithms in {@code x}, {@code y}.
   */
  private static String radicals(int n) {
    return IntStream.rangeClosed(1, n)
        .mapToObj(k -> "sqrt(" + k + "^3) + abs(-" + k + " y) + log(" + 2 * k + " x)")
        .collect(Collectors.joining(" + "));
  }

  private static Session session(Case c) {
    Session s =
        new Session(Environment.EMPTY.withSettings(Settings.DEFAULT.withPrecision(c.precision())));
    if (!c.setup().isEmpty()) {
      s.run(Parser.parseScript(c.setup(), s.names()));
    }
    return s;
  }

  // From the request to the return: typically 20 to 150 µs locally (docs/reference/engine.md,
  // Timing budgets); the budget catches a loop that does not check, not a slow machine.
  private static final long BUDGET_NANOS = 30_000_000_000L;

  @ParameterizedTest
  @MethodSource("cases")
  void stopsAtEveryCheckAndRestoresTheEnvironment(Case c) {
    Session s = session(c);
    Environment before = s.environment();
    Statement statement = Parser.parseScript(c.statement(), s.names()).statements().getFirst();
    Tripping counting = Tripping.counting();
    Outcome full = new Session(before).run(statement, counting);
    assertThat(full).as(c.name()).isNotInstanceOf(Outcome.Cancelled.class);
    long slowest = 0;
    for (long n : Tripping.points(counting.checks())) {
      Session cancelled = new Session(before);
      Tripping trip = new Tripping(n);
      Outcome o = cancelled.run(statement, trip);
      long returned = System.nanoTime();
      assertThat(o).as(c.name() + " at check " + n).isInstanceOf(Outcome.Cancelled.class);
      assertThat(cancelled.environment()).isSameAs(before);
      slowest = Math.max(slowest, returned - trip.trippedAt());
      // the session goes on as if the statement had never run
      assertThat(cancelled.run(statement)).isEqualTo(full);
    }
    assertThat(slowest).as(c.name()).isLessThan(BUDGET_NANOS);
  }

  /**
   * Inside a binder's body the rewriter runs under a context derived by {@code withAssumptions}
   * (the bound variable's facts dropped): with a fact about {@code x} the body of a Σ over {@code
   * x} is rewritten under such a context, without one under the statement's own. Both make the same
   * checks, so a derived context that lost the token would show as missing checks.
   */
  @Test
  void aBindersBodyChecksUnderItsDerivedContext() {
    String sum = "sum(" + radicals(200) + ", x, 1, 3)";
    long withFact = checks("assume(x > 0)", sum);
    long withoutFact = checks("", sum);
    assertThat(withoutFact).isGreaterThan(10_000);
    assertThat(withFact).isGreaterThanOrEqualTo(withoutFact);
  }

  private static long checks(String setup, String statement) {
    Session s = session(new Case("", 34, setup, statement));
    Tripping counting = Tripping.counting();
    Outcome o = s.run(Parser.parseScript(statement, s.names()).statements().getFirst(), counting);
    assertThat(o).isInstanceOf(Outcome.Value.class);
    return counting.checks();
  }

  @Test
  void aStatementCancelledBeforeItStartsDoesNothing() {
    Session s = new Session();
    Environment before = s.environment();
    Script script = Parser.parseScript("a := 1", s.names());
    assertThat(s.run(script.statements().getFirst(), () -> true))
        .isEqualTo(new Outcome.Cancelled());
    assertThat(s.environment()).isSameAs(before);
  }

  @Test
  void aCancelledScriptKeepsTheStatementsBeforeAndCancelsTheRest() {
    Session s = new Session();
    Script script =
        Parser.parseScript("a := 1\nb := a + 1\nnumeric(pi, 400)\nc := 3\nb", s.names());
    // trip inside the third statement: past the checks of the first two
    Session probe = new Session();
    Tripping first = Tripping.counting();
    probe.run(script.statements().get(0), first);
    probe.run(script.statements().get(1), first);
    List<Outcome> out = s.run(script, new Tripping(first.checks() + 50));
    assertThat(out).hasSize(5);
    assertThat(out.get(0)).isInstanceOf(Outcome.Defined.class);
    assertThat(out.get(1)).isInstanceOf(Outcome.Defined.class);
    assertThat(out.subList(2, 5)).containsOnly(new Outcome.Cancelled());
    assertThat(s.environment().definitions()).containsOnlyKeys("a", "b");
  }

  @Test
  void aThreadInterruptCancels() throws InterruptedException {
    Session s = new Session();
    Statement statement =
        Parser.parseScript("numeric(pi, 10000)", s.names()).statements().getFirst();
    AtomicReference<Outcome> outcome = new AtomicReference<>();
    AtomicReference<Boolean> stillInterrupted = new AtomicReference<>();
    Thread worker =
        new Thread(
            () -> {
              outcome.set(s.run(statement, Cancellation.NEVER));
              stillInterrupted.set(Thread.currentThread().isInterrupted());
            });
    worker.start();
    Thread.sleep(200);
    long interrupted = System.nanoTime();
    worker.interrupt();
    worker.join(30_000);
    long joined = System.nanoTime();
    assertThat(worker.isAlive()).isFalse();
    assertThat(outcome.get()).isEqualTo(new Outcome.Cancelled());
    assertThat(stillInterrupted.get()).isTrue();
    assertThat(s.environment()).isSameAs(Environment.EMPTY);
    // typically under a millisecond locally (docs/reference/engine.md, Timing budgets)
    assertThat(joined - interrupted).isLessThan(BUDGET_NANOS);
  }
}
