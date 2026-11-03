package mathcanvas.engine.discrete;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Outcome;
import mathcanvas.engine.eval.Session;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.AsciiPrinter;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.statistics.Statistics;

/**
 * {@code rsolve}'s solutions are right: for random linear recurrences of order 1 to 3 with small
 * integer coefficients, a constant, polynomial or exponential forcing term or none, and initial
 * values at 0, …, d − 1, every solution returned agrees with the sequence computed by iterating the
 * recurrence at the first twelve indices.
 */
class RsolveProperties {

  private static Outcome run(String script) {
    Session s = new Session(Environment.EMPTY);
    return s.run(Parser.parseScript(script, s.names())).getLast();
  }

  private static BigDecimal decimal(String expr) {
    Outcome o = run("numeric(" + expr + ", 40)");
    Expr v = ((Outcome.Value) o).value();
    Numeric x = ((Num) v).value();
    assertThat(x.im().isZero()).as(expr).isTrue();
    return new BigDecimal(x.re().toString());
  }

  @Property(tries = 60, seed = "8701")
  void solutionsAgreeWithTheIteration(@ForAll long seed) {
    Random r = new Random(seed);
    int d = 1 + r.nextInt(3);
    long[] c = new long[d];
    for (int j = 0; j < d; j++) {
      c[j] = r.nextInt(7) - 3;
    }
    if (c[0] == 0) {
      c[0] = 1;
    }
    int forcing = r.nextInt(4);
    long[] init = new long[d];
    for (int j = 0; j < d; j++) {
      init[j] = r.nextInt(7) - 3;
    }
    // a(n + d) = Σ c_j a(n + j) + g(n)
    StringBuilder rhs = new StringBuilder();
    for (int j = 0; j < d; j++) {
      rhs.append(j > 0 ? " + " : "")
          .append("(")
          .append(c[j])
          .append(") a(n + ")
          .append(j)
          .append(")");
    }
    String g =
        switch (forcing) {
          case 1 -> "3";
          case 2 -> "n";
          case 3 -> "2^n";
          default -> "0";
        };
    StringBuilder script =
        new StringBuilder("rsolve(a(n + " + d + ") = " + rhs + " + " + g + ", a(n)");
    for (int j = 0; j < d; j++) {
      script.append(", a(").append(j).append(") = ").append(init[j]);
    }
    script.append(")");
    Outcome o = run(script.toString());
    boolean solved = o instanceof Outcome.Value v && v.value() instanceof Relation;
    Statistics.collect(solved ? "solved" : "stays");
    if (!solved) {
      return;
    }
    Expr solution = ((Relation) ((Outcome.Value) o).value()).right();
    String text = AsciiPrinter.INSTANCE.display(solution);
    List<BigInteger> seq = new ArrayList<>();
    for (long v : init) {
      seq.add(BigInteger.valueOf(v));
    }
    for (int k = 0; seq.size() < 12; k++) {
      BigInteger next = BigInteger.ZERO;
      for (int j = 0; j < d; j++) {
        next = next.add(BigInteger.valueOf(c[j]).multiply(seq.get(k + j)));
      }
      next =
          next.add(
              switch (forcing) {
                case 1 -> BigInteger.valueOf(3);
                case 2 -> BigInteger.valueOf(k);
                case 3 -> BigInteger.TWO.pow(k);
                default -> BigInteger.ZERO;
              });
      seq.add(next);
    }
    for (int k = 0; k < 12; k++) {
      BigDecimal value = decimal("subs(" + text + ", n = " + k + ")");
      BigDecimal expected = new BigDecimal(seq.get(k));
      BigDecimal tolerance =
          expected
              .abs()
              .max(BigDecimal.ONE)
              .multiply(new BigDecimal("1e-25"), MathContext.DECIMAL128);
      assertThat(value.subtract(expected).abs())
          .as("%s at %d: %s, expected %s", script, k, value, expected)
          .isLessThanOrEqualTo(tolerance);
    }
  }
}
