package mathcanvas.engine.solve;

import static mathcanvas.engine.solve.SolveCases.X;
import static mathcanvas.engine.solve.SolveCases.run;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Random;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Numerics;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.testing.NumericEvaluator;
import org.junit.jupiter.api.Test;

/** The numeric fallback: polynomials past the formulas, and equations that are not polynomials. */
class NumericSolveTest {

  /** The test tooling's evaluator as the context's numeric values. */
  static final Numerics NUMERICS = (e, mc) -> NumericEvaluator.eval(e, Map.of(), mc);

  static Context digits(int digits) {
    return new Context(
            Context.DEFAULT.assumptions(),
            new MathContext(digits, RoundingMode.HALF_EVEN),
            false,
            10_000)
        .withNumerics(NUMERICS);
  }

  private static String shown(String call) {
    return UnicodePrinter.INSTANCE.display(run(call, digits(34)).expr());
  }

  @Test
  void aQuinticHasOneRealAndFourComplexRoots() {
    SetExpr s = (SetExpr) run("solve(x^5 - x - 1 = 0, x)", digits(34)).expr();
    assertThat(UnicodePrinter.INSTANCE.display(s))
        .isEqualTo(
            "{1.167303978261418684256045899854842,"
                + " -0.7648844336005847260298231877085417 - 0.3524715460317262493179470914025811i,"
                + " -0.7648844336005847260298231877085417 + 0.3524715460317262493179470914025811i,"
                + " 0.1812324444698753839018002377811206 - 1.083954101317710668430344492980767i,"
                + " 0.1812324444698753839018002377811206 + 1.083954101317710668430344492980767i}");
    // against the same roots at 100 digits, rounded
    SetExpr fine = (SetExpr) run("solve(x^5 - x - 1 = 0, x)", digits(100)).expr();
    MathContext mc = new MathContext(34, RoundingMode.HALF_EVEN);
    assertThat(fine.items()).hasSize(5);
    for (int i = 0; i < 5; i++) {
      Numeric a = ((Num) s.items().get(i)).value();
      Numeric b = ((Num) fine.items().get(i)).value();
      assertThat(a.re().toReal(mc).value().subtract(b.re().toReal(mc).value()).abs())
          .isLessThanOrEqualTo(ulp(a.re().toReal(mc).value()));
      assertThat(a.im().toReal(mc).value().subtract(b.im().toReal(mc).value()).abs())
          .isLessThanOrEqualTo(ulp(a.im().toReal(mc).value()));
    }
    assertThat(SolveCases.steps("solve(x^5 - x - 1 = 0, x)", digits(34)))
        .containsExactly("numeric", "roots");
    Rewrite numeric = run("solve(x^5 - x - 1 = 0, x)", digits(34)).trace().rewrites().getFirst();
    assertThat(numeric.bindings().get("digits_")).isEqualTo(Num.of(34));
  }

  private static BigDecimal ulp(BigDecimal v) {
    return v.signum() == 0 ? BigDecimal.ONE.movePointLeft(34) : v.ulp();
  }

  @Test
  void exactAndNumericSolutionsTogether() {
    SetExpr s = (SetExpr) run("solve((x^2 - 2)(x^5 - x - 1) = 0, x)", digits(20)).expr();
    assertThat(s.items()).hasSize(7);
    assertThat(UnicodePrinter.INSTANCE.display(s)).contains("√2").contains("1.1673039782614186843");
    Rewrite roots =
        run("solve((x^2 - 2)(x^5 - x - 1) = 0, x)", digits(20)).trace().rewrites().getLast();
    assertThat(UnicodePrinter.INSTANCE.display(roots.bindings().get("methods_")))
        .isEqualTo("{numeric, quadratic}");
    assertThat(roots.bindings().get("digits_")).isEqualTo(Num.of(20));
  }

  @Test
  void polynomialSolutionsSubstituteBack() {
    Random random = new Random(2);
    for (String p :
        List.of(
            "x^5 - x - 1",
            "x^5 - 5x + 1",
            "x^6 - 2",
            "x^7 - 3x^3 + x - 1",
            "x^8 + x + 1",
            "2x^5 - 1000000x^2 + 3",
            "x^5 - 10^7",
            "x^3 - 2.5x + 1",
            "x^3 - π")) {
      SetExpr s = (SetExpr) run("solve(" + p + " = 0, x)", digits(34)).expr();
      Polynomial poly = Polynomial.of(Canonical.of(Parser.parseExpression(p, Names.NONE)));
      assertThat(s.items()).as(p).hasSize(poly.degree(X));
      if (poly.variables().equals(List.of(X))) {
        SolveCases.assertSolutions(poly, s, random);
      }
    }
    // the root beyond 10⁶ is found: Sturm's interval is Cauchy's bound
    SetExpr far = (SetExpr) run("solve(x^5 - 3*10^35 = 0, x)", digits(34)).expr();
    assertThat(UnicodePrinter.INSTANCE.display(far.items().getFirst())).startsWith("12457309.396");
  }

  @Test
  void complexCoefficients() {
    SetExpr s = (SetExpr) run("solve(x^7 + i x + 1 = 0, x)", digits(34)).expr();
    assertThat(s.items()).hasSize(7);
    for (Expr r : s.items()) {
      Expr residual = Canonical.of(Parser.parseExpression("r^7 + i r + 1", Names.NONE));
      Numeric v =
          NumericEvaluator.eval(
                  residual,
                  Map.of(mathcanvas.engine.expr.Symbol.of("r"), ((Num) r).value()),
                  new MathContext(34))
              .orElseThrow();
      assertThat(v.modulus(new MathContext(34)).toReal(new MathContext(34)).value())
          .isLessThan(new BigDecimal("1e-30"));
    }
  }

  @Test
  void equationsThatAreNotPolynomials() {
    assertThat(shown("solve(x = cos(x), x)")).isEqualTo("{0.7390851332151606416553120876738734}");
    assertThat(shown("solve(e^x = 3x, x)"))
        .isEqualTo("{0.6190612867359451121523269940209222, 1.512134551657842473896739678072039}");
    assertThat(shown("solve(sqrt(x) = 2, x)")).isEqualTo("{4.0}");
    assertThat(shown("solve(2^x = 8, x)")).isEqualTo("{3.0}");
    Rewrite numeric = run("solve(x = cos(x), x)", digits(34)).trace().rewrites().getFirst();
    assertThat(UnicodePrinter.INSTANCE.display(numeric.bindings().get("interval_")))
        .isEqualTo("[-1000000, 1000000]");
  }

  @Test
  void theScanSaysWhyItGivesUp() {
    assertThat(SolveCases.steps("solve(sin(x) = 1/2, x)", digits(34)))
        .containsExactly("too-many-roots");
    assertThat(SolveCases.steps("solve(e^x = 0, x)", digits(34))).containsExactly("no-root-found");
    // a periodic function of the unknown with several roots, one far out: a sample, not the set
    assertThat(SolveCases.steps("solve(tan(x) = 2x, x)", digits(34))).containsExactly("aliased");
    // a few roots near the origin are answered
    SetExpr near = (SetExpr) run("solve(sin(x) = x/2, x)", digits(34)).expr();
    assertThat(near.items()).hasSize(3);
    assertThat(UnicodePrinter.INSTANCE.display(near))
        .startsWith("{-1.89549426703398094714403573809");
    SetExpr two = (SetExpr) run("solve(cos(x) = x^2, x)", digits(34)).expr();
    assertThat(two.items()).hasSize(2);
    assertThat(UnicodePrinter.INSTANCE.display(two)).startsWith("{-0.82413231230252242296");
    assertThat(SolveCases.steps("solve(x = cos(x), x)", digits(34)))
        .containsExactly("numeric", "roots");
    // a pole and a jump are no roots
    assertThat(SolveCases.steps("solve(tan(x) = 0, x)", digits(34)).getFirst())
        .isEqualTo("too-many-roots");
    assertThat(shown("solve(x + sign(x - 1/3) = 0, x)"))
        .isEqualTo("solve(x + sign(x - 1/3) = 0, x)");
    // without numeric values, or in degrees
    assertThat(SolveCases.steps("solve(x = cos(x), x)", Context.DEFAULT))
        .containsExactly("no-numerics");
    assertThat(SolveCases.steps("solve(x^5 - x - 1 = 0, x)", Context.DEFAULT))
        .containsExactly("numeric", "roots");
    Numerics degrees =
        new Numerics() {
          @Override
          public java.util.Optional<Numeric> value(Expr e, MathContext mc) {
            return NUMERICS.value(e, mc);
          }

          @Override
          public boolean radians() {
            return false;
          }
        };
    assertThat(SolveCases.steps("solve(x = cos(x), x)", digits(34).withNumerics(degrees)))
        .containsExactly("angle-mode");
    assertThat(shown("solve(e^x = 2, x)"))
        .isEqualTo(
            "{"
                + new Real(new BigDecimal("0.6931471805599453094172321214581766"))
                    .value()
                    .toPlainString()
                + "}");
  }

  @Test
  void theDegreeBound() {
    assertThat(SolveCases.steps("solve(x^101 - x - 1 = 0, x)", digits(34)))
        .containsExactly("numeric-degree");
  }
}
