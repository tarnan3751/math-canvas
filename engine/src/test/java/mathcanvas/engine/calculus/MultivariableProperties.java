package mathcanvas.engine.calculus;

import static mathcanvas.engine.testing.Inputs.parse;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Random;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.rule.Context;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

/**
 * The vector calculus builtins ({@link Multivariable}): {@code grad}, {@code jacobian} and {@code
 * hessian} against central difference quotients at random points; on random polynomial fields
 * {@code div(curl F) = 0}, {@code curl(grad f) = 0} and {@code laplacian f = div(grad f)}, exactly.
 */
class MultivariableProperties {

  private static final Symbol X = Symbol.of("x");
  private static final Symbol Y = Symbol.of("y");
  private static final Symbol Z = Symbol.of("z");
  private static final List<Symbol> XYZ = List.of(X, Y, Z);
  private static final ListExpr VARS = ListExpr.of(X, Y, Z);

  private static final List<String> SMOOTH =
      List.of(
          "x^2 y + z",
          "sin(x y) + z^2",
          "e^(x - y z)",
          "x y z",
          "cos(x) sin(y) + x z^3",
          "(x + y^2)^3",
          "1/(1 + x^2 + y^2 z^2)",
          "atan(x y) + z",
          "x e^(y) cos(z)");

  private static Expr run(String command, Expr f) {
    return Multivariable.of(Apply.of(Symbol.of(command), f, VARS), Context.DEFAULT).expr();
  }

  private static Map<Symbol, Numeric> point(Random r) {
    return Map.of(
        X, Rational.of(r.nextInt(401) - 200, 100),
        Y, Rational.of(r.nextInt(401) - 200, 100),
        Z, Rational.of(r.nextInt(401) - 200, 100));
  }

  private static void quotient(Expr f, Expr derivative, Symbol v, Map<Symbol, Numeric> point) {
    if (DifferenceQuotient.defined(f, point)) {
      DifferenceQuotient.check(f, derivative, v, point, Assumptions.EMPTY.facts(v));
    }
  }

  @Property(tries = 40)
  void gradJacobianAndHessianAgreeWithDifferenceQuotients(@ForAll long seed) {
    Random r = new Random(seed);
    Expr f = parse(SMOOTH.get(r.nextInt(SMOOTH.size())));
    Expr g = parse(SMOOTH.get(r.nextInt(SMOOTH.size())));
    Map<Symbol, Numeric> point = point(r);
    Vector grad = (Vector) run("grad", f);
    Matrix hessian = (Matrix) run("hessian", f);
    Matrix jacobian = (Matrix) run("jacobian", ListExpr.of(f, g));
    for (int i = 0; i < 3; i++) {
      quotient(f, grad.items().get(i), XYZ.get(i), point);
      quotient(g, jacobian.rows().get(1).get(i), XYZ.get(i), point);
      for (int j = 0; j < 3; j++) {
        quotient(grad.items().get(i), hessian.rows().get(i).get(j), XYZ.get(j), point);
      }
    }
  }

  /** A random polynomial in x, y, z of total degree at most 3 with small integer coefficients. */
  private static Expr polynomial(Random r) {
    StringBuilder s = new StringBuilder("0");
    for (int k = 0; k < 5; k++) {
      s.append(" + (")
          .append(r.nextInt(11) - 5)
          .append(") x^")
          .append(r.nextInt(3))
          .append(" y^")
          .append(r.nextInt(3))
          .append(" z^")
          .append(r.nextInt(2));
    }
    return parse(s.toString());
  }

  private static Expr expand(Expr e) {
    return Algebra.expand(e, Context.DEFAULT).expr();
  }

  @Property(tries = 60)
  void theIdentitiesHoldOnPolynomialFields(@ForAll long seed) {
    Random r = new Random(seed);
    Expr f = polynomial(r);
    Vector field = Vector.of(polynomial(r), polynomial(r), polynomial(r));
    Vector curl = (Vector) run("curl", field);
    assertThat(expand(run("divergence", curl))).as("div curl %s", field).isEqualTo(Num.ZERO);
    Vector curlGrad = (Vector) run("curl", run("grad", f));
    for (Expr c : curlGrad.items()) {
      assertThat(expand(c)).as("curl grad %s", f).isEqualTo(Num.ZERO);
    }
    Expr difference =
        expand(
            Sum.of(
                run("laplacian", f), Product.of(Num.MINUS_ONE, run("divergence", run("grad", f)))));
    assertThat(difference).as("laplacian - div grad of %s", f).isEqualTo(Num.ZERO);
  }

  @Test
  void theTwoDimensionalCurlIsAScalar() {
    Expr c =
        Multivariable.of(
                Apply.of(
                    Symbol.of("curl"), ListExpr.of(parse("-y"), parse("x")), ListExpr.of(X, Y)),
                Context.DEFAULT)
            .expr();
    assertThat(c).isEqualTo(Num.of(2));
  }

  @Test
  void theTraceChains() {
    var r =
        Multivariable.of(
            Apply.of(Symbol.of("hessian"), parse("x^3 y^2"), VARS),
            Context.DEFAULT.withTrace(true));
    TraceChecks.assertChain(r);
    assertThat(r.trace().rewrites().getFirst().rule()).isEqualTo("hessian.partials");
  }
}
