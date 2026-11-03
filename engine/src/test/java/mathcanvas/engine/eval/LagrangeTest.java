package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Random;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.print.AsciiPrinter;
import mathcanvas.engine.rule.Rewriter;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.statistics.Statistics;
import org.junit.jupiter.api.Test;

/**
 * {@code lagrange}: the critical points satisfy the constraints and the gradient condition, the
 * multipliers are named fresh, and a system the solver leaves keeps the call.
 */
class LagrangeTest {

  @Test
  void theTextbookCases() {
    assertThat(Run.shown("lagrange(x^2 + y^2, [x + y = 1], [x, y])"))
        .isEqualTo("{[x = 1/2, y = 1/2, λ = 1]}");
    assertThat(Run.shown("lagrange(x + y + z, [x^2 + y^2 + z^2 = 3], [x, y, z])"))
        .isEqualTo("{[x = -1, y = -1, z = -1, λ = -1/2], [x = 1, y = 1, z = 1, λ = 1/2]}");
    assertThat(Run.shown("lagrange(x y z, [x + y + z = 3, x - y = 0], [x, y, z])"))
        .isEqualTo(
            "{[x = 0, y = 0, z = 3, λ₁ = 0, λ₂ = 0], [x = 1, y = 1, z = 1, λ₁ = 1, λ₂ = 0]}");
  }

  @Test
  void aPointWithACoordinateThatIsNotRealIsDropped() {
    // √(−√5 + 2) is not real: two of the four solutions are dropped, numerically decided
    Expr points = Run.value("lagrange(x + y + z, [x^2 + y^2 = 1, z = x^2], [x, y, z])");
    assertThat(((SetExpr) points).items()).hasSize(2);
    assertThat(Run.shown("lagrange(x + y, [x^3 = 2, y = 0], [x, y])"))
        .isEqualTo("{[x = 2^(1/3), y = 0, λ₁ = 2^(1/3)/6, λ₂ = 1]}");
  }

  @Test
  void systemsWhoseLinearCoefficientDoesNotFoldAreSolvedWithTidyValues() {
    // review-26: Univariate's linear path on 3·2^(2/3) y = … once x is put in, and the values
    assertThat(((SetExpr) Run.value("solve({x^3 = 2, x y = 1}, [x, y])")).items()).hasSize(3);
    assertThat(((SetExpr) Run.value("solve({1 = a (4x^3 + 2x), x^4 + x^2 = 1}, [x, a])")).items())
        .hasSize(4);
    assertThat(Run.shown("solve({x^2 = y, y^2 = x}, [x, y])"))
        .isEqualTo(
            "{[x = 0, y = 0], [x = 1, y = 1], [x = -i √3/2 - 1/2, y = i √3/2 - 1/2],"
                + " [x = i √3/2 - 1/2, y = -i √3/2 - 1/2]}");
  }

  @Test
  void aMultiplierWithAValueIsRenamedAndTheUnsolvedCallStays() {
    assertThat(Run.shown("λ := 2\nlagrange(λ x y, [x + y = 2], [x, y])"))
        .isEqualTo("{[x = 1, y = 1, λ₁ = 2]}");
    assertThat(Run.value("lagrange(sin(x) y, [x + y = 1], [x, y])")).isInstanceOf(Apply.class);
    assertThat(Run.error("lagrange(x, [x], [x])"))
        .isEqualTo("lagrange needs constraints g = c in a list");
  }

  @Test
  void theStepsAreTheSystemThenThePoints() {
    assertThat(NarrationTest.narrated("steps(lagrange(x^2 + y^2, [x + y = 1], [x, y]))"))
        .containsExactly(
            "Set the gradient of the function to the multipliers times the constraints' gradients,"
                + " and keep the constraints: {2x = λ, 2y = λ, x + y = 1} | {2x = λ, 2y = λ, x + y"
                + " = 1}",
            "Solve the system; the real solutions are the critical points: {[x = 1/2, y = 1/2, λ ="
                + " 1]} | {[x = 1/2, y = 1/2, λ = 1]}");
    Outcome.Value v = (Outcome.Value) Run.last("steps(lagrange(x y, [x^2 + y^2 = 1], [x, y]))");
    TraceChecks.assertChain(new Rewriter.Result(v.value(), v.trace()));
  }

  /**
   * On random quadratic objectives and a linear or circular constraint, every point satisfies the
   * constraint and ∇f = λ∇g there.
   */
  @Property(tries = 40)
  void everyPointSatisfiesTheConstraintAndTheGradientCondition(@ForAll long seed) {
    Random r = new Random(seed);
    int a = r.nextInt(5) - 2;
    int b = r.nextInt(5) - 2;
    int c = r.nextInt(5) - 2;
    int p = 1 + r.nextInt(3);
    int q = r.nextInt(5) - 2;
    String f = "(" + a + ") x^2 + (" + b + ") x y + (" + c + ") y^2 + x";
    String g = r.nextBoolean() ? p + " x + (" + q + ") y" : "x^2 + " + p + " y^2";
    String k = Integer.toString(1 + r.nextInt(4));
    Expr points = Run.value("lagrange(" + f + ", [" + g + " = " + k + "], [x, y])");
    if (!(points instanceof SetExpr set)) {
      Statistics.label("outcome").collect("stays");
      return;
    }
    Statistics.label("outcome").collect("solved");
    for (Expr point : set.items()) {
      String at = point.toString();
      // an unknown the system leaves free has no equation of its own, and stays itself
      String subs = "";
      String lambda = "λ";
      for (Expr item : ((ListExpr) point).items()) {
        Relation rel = (Relation) item;
        String name = ((mathcanvas.engine.expr.Symbol) rel.left()).name();
        String value = "(" + AsciiPrinter.INSTANCE.display(rel.right()) + ")";
        if (name.equals("λ")) {
          lambda = value;
        } else {
          subs += (subs.isEmpty() ? "" : ", ") + name + " = " + value;
        }
      }
      if (subs.isEmpty()) {
        subs = "x = x";
      }
      String constraint = "expand(subs(" + g + " - " + k + ", " + subs + "))";
      assertThat(zero(constraint)).as("%s at %s", g, at).isTrue();
      for (String v : List.of("x", "y")) {
        String condition =
            "expand(subs(diff("
                + f
                + ", "
                + v
                + ") - ("
                + lambda
                + ") diff("
                + g
                + ", "
                + v
                + "), "
                + subs
                + "))";
        assertThat(zero(condition)).as("∂/∂%s at %s", v, at).isTrue();
      }
    }
  }

  /** Whether an exact constant is 0: it simplifies to 0, or is within 10⁻²⁰ of it at 40 digits. */
  private static boolean zero(String constant) {
    if (Run.shown("simplify(" + constant + ")").equals("0")) {
      return true;
    }
    Expr v = Run.value("numeric(abs(" + constant + "), 40)");
    return v instanceof Num n
        && ((RealNumeric) n.value()).toReal(MathContext.DECIMAL128).value().compareTo(TINY) < 0;
  }

  private static final BigDecimal TINY = new BigDecimal("1e-20");
}
