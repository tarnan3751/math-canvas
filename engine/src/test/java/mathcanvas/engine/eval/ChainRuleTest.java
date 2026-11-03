package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Rewriter;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

/**
 * The chain rule in several variables ({@code calculus.ChainRule}) and the narration of partial
 * derivatives: the value is the plain derivative's, and the steps name the partials.
 */
class ChainRuleTest {

  private static final String DEFINITIONS = "f(u, v) := u^2 v\nu(x, y) := x y\nv(x, y) := x + y\n";

  @Test
  void theChainRuleNamesThePartialsAndTheInnerDerivatives() {
    List<String> steps =
        NarrationTest.narrated(DEFINITIONS + "steps(diff(f(u(x, y), v(x, y)), x))");
    assertThat(steps.getFirst())
        .startsWith("By the chain rule in several variables, the derivative of f(u, v) is")
        .contains("fᵤ = 2u v");
    assertThat(steps.get(1)).startsWith("Differentiate the arguments, [uₓ = y, vₓ = 1]");
    assertThat(Run.shown(DEFINITIONS + "expand(diff(f(u(x, y), v(x, y)), x))"))
        .isEqualTo(Run.shown("expand(diff(x^2 y^2 (x + y), x))"));
  }

  @Test
  void theTraceChains() {
    Outcome.Value v = (Outcome.Value) Run.last(DEFINITIONS + "steps(∂/∂x f(u(x, y), v(x, y)))");
    TraceChecks.assertChain(new Rewriter.Result(v.value(), v.trace()));
    assertThat(v.trace().rewrites().getFirst().ruleSet()).isEqualTo("chain");
  }

  @Test
  void aParameterNamedAsTheVariableIsRenamedAndTheValueIsFree() {
    assertThat(Run.shown("g(x, y) := x y\ndiff(g(x^2, sin x), x)"))
        .isEqualTo("x² cos(x) + 2x sin(x)");
    assertThat(Run.shown("g(x, y) := x y\nx := 2\ndiff(g(x^2, sin x), x)"))
        .isEqualTo(Run.shown("4 cos(2) + 4 sin(2)"));
    // arguments that are all names: the plain derivative
    assertThat(Run.shown("g(a, b) := a b\nsteps(diff(g(x, y), x))")).isEqualTo("y");
  }

  @Test
  void aPartialDerivativeSaysWhatIsHeldConstant() {
    assertThat(NarrationTest.narrated("steps(diff(x^2 y^3, x))").getFirst())
        .startsWith("Differentiate with respect to x, holding y constant. ");
    assertThat(NarrationTest.narrated("steps(diff(x^2 y^3 z, y))").getFirst())
        .startsWith("Differentiate with respect to y, holding x, z constant. ");
    assertThat(NarrationTest.narrated("steps(diff(x^3, x))").getFirst())
        .startsWith("Apply the power rule");
  }

  /** The chain rule's value is the plain derivative's at random points. */
  @Property(tries = 60)
  void theValueIsThePlainDerivatives(
      @ForAll @IntRange(min = 0, max = 4) int body,
      @ForAll @IntRange(min = 0, max = 4) int first,
      @ForAll @IntRange(min = 0, max = 4) int second,
      @ForAll @IntRange(min = -20, max = 20) int x,
      @ForAll @IntRange(min = -20, max = 20) int y) {
    String[] bodies = {"u^2 v", "sin(u) v", "e^(u v)", "u/(1 + v^2)", "u^3 - 2u v + v^2"};
    String[] args = {"x y", "x + y", "x^2", "cos(x y)", "3x - y^2"};
    String define = "f(u, v) := " + bodies[body] + "\n";
    String a = args[first];
    String b = args[second];
    String composed = bodies[body].replace("u", "(" + a + ")").replace("v", "(" + b + ")");
    Expr d =
        Run.value(
            define
                + "numeric(subs(diff(f("
                + a
                + ", "
                + b
                + "), x) - diff("
                + composed
                + ", x), x = "
                + x
                + "/10, y = "
                + y
                + "/10))");
    assertThat(d)
        .isInstanceOfSatisfying(
            Num.class,
            n ->
                assertThat(((RealNumeric) n.value()).toReal(MathContext.DECIMAL64).value().abs())
                    .isLessThan(new BigDecimal("1e-20")));
  }
}
