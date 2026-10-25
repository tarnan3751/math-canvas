package mathcanvas.engine.print;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Lambda;
import mathcanvas.engine.expr.Limit;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Piecewise;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

class PrinterTest {

  private static final Symbol X = Symbol.of("x");
  private static final Symbol Y = Symbol.of("y");
  private static final Symbol U = Symbol.of("u");
  private static final Symbol V = Symbol.of("v");
  private static final Symbol K = Symbol.of("k");
  private static final Symbol N = Symbol.of("n");

  private static Num n(long v) {
    return Num.of(v);
  }

  private static Expr pow(Expr b, Expr e) {
    return Power.of(b, e);
  }

  private static Expr mul(Expr... f) {
    return Product.of(f);
  }

  private static Expr add(Expr... t) {
    return Sum.of(t);
  }

  private static Apply call(String head, Expr... args) {
    return Apply.of(Symbol.of(head), args);
  }

  /** The value's canonical form displayed by each printer. */
  private static void row(Expr raw, String ascii, String unicode, String latex) {
    Expr c = Canonical.of(raw);
    assertThat(AsciiPrinter.INSTANCE.display(c)).as("ascii of %s", raw).isEqualTo(ascii);
    assertThat(UnicodePrinter.INSTANCE.display(c)).as("unicode of %s", raw).isEqualTo(unicode);
    assertThat(LatexPrinter.INSTANCE.display(c)).as("latex of %s", raw).isEqualTo(latex);
  }

  /** A raw tree printed faithfully by each printer. */
  private static void held(Expr raw, String ascii, String unicode) {
    assertThat(AsciiPrinter.INSTANCE.print(raw)).as("ascii of %s", raw).isEqualTo(ascii);
    assertThat(UnicodePrinter.INSTANCE.print(raw)).as("unicode of %s", raw).isEqualTo(unicode);
  }

  private static String unicode(Expr canonical) {
    return UnicodePrinter.INSTANCE.display(Canonical.of(canonical));
  }

  @Test
  void theTableOfSection15() {
    row(mul(n(2), X, call("sin", X)), "2*x*sin(x)", "2x sin(x)", "2x\\sin(x)");
    row(pow(X, n(2)), "x^2", "x²", "x^{2}");
    row(pow(X, n(-1)), "1/x", "1/x", "\\frac{1}{x}");
    row(pow(X, n(-2)), "1/x^2", "1/x²", "\\frac{1}{x^{2}}");
    row(pow(Constant.E, X), "e^x", "e^x", "e^{x}");
    row(Exprs.sqrt(n(2)), "sqrt(2)", "√2", "\\sqrt{2}");
    row(Exprs.sqrt(add(X, n(1))), "sqrt(x + 1)", "√(x + 1)", "\\sqrt{x+1}");
    row(Constant.PI, "pi", "π", "\\pi");
    row(Num.of(Rational.of(1, 3)), "1/3", "1/3", "\\frac{1}{3}");
    row(Exprs.div(X, mul(n(2), Constant.PI)), "x/(2*pi)", "x/(2π)", "\\frac{x}{2\\pi}");
    row(Exprs.neg(pow(X, n(2))), "-x^2", "-x²", "-x^{2}");
    row(mul(X, add(X, n(1))), "x*(x + 1)", "x·(x + 1)", "x(x+1)");
    row(mul(add(X, n(-1)), add(X, n(1))), "(x - 1)*(x + 1)", "(x - 1)(x + 1)", "(x-1)(x+1)");
    row(call("abs", X), "abs(x)", "|x|", "\\lvert x\\rvert");
    row(
        call("abs", mul(Symbol.of("a"), call("abs", Symbol.of("b")))),
        "abs(a*abs(b))",
        "abs(a |b|)",
        "\\lvert a\\lvert b\\rvert\\rvert");
    row(Vector.of(mul(n(2), X), mul(n(2), Y)), "[2*x, 2*y]", "⟨2x, 2y⟩", "\\langle 2x, 2y\\rangle");
    row(
        Matrix.of(List.of(List.of(n(1), n(2)), List.of(n(3), n(4)))),
        "[[1, 2], [3, 4]]",
        "[[1, 2], [3, 4]]",
        "\\begin{pmatrix}1 & 2\\\\ 3 & 4\\end{pmatrix}");
    row(SetExpr.of(n(2), n(3)), "{2, 3}", "{2, 3}", "\\{2, 3\\}");
    row(Symbol.of("x_1"), "x_1", "x₁", "x_{1}");
    row(Num.of(Real.of("2e3")), "2000.0", "2000.0", "2000.0");
    row(Num.of(Complex.of(Int.of(2), Int.of(3))), "2 + 3*i", "2 + 3i", "2+3i");
    row(Relation.of(Relation.Op.LE, X, n(1)), "x <= 1", "x ≤ 1", "x \\le 1");
    row(
        Relation.of(Relation.Op.IN, N, Constant.INTEGERS),
        "n in Integers",
        "n ∈ ℤ",
        "n \\in \\mathbb{Z}");
    row(Lambda.of(X, pow(X, n(2))), "x -> x^2", "x → x²", "x \\mapsto x^{2}");
    row(call("cross", U, V), "cross(u, v)", "u × v", "u \\times v");
    row(call("dot", U, V), "dot(u, v)", "dot(u, v)", "\\operatorname{dot}(u, v)");
    row(Constant.UNDEFINED, "undefined", "undefined", "\\text{undefined}");
    row(Constant.INFINITY, "inf", "∞", "\\infty");
    row(
        Integral.indefinite(pow(Constant.E, pow(X, n(2))), X),
        "integrate(e^(x^2), x)",
        "∫ e^(x²) dx",
        "\\int e^{x^{2}}\\,dx");
    row(
        BigSum.of(pow(K, n(-2)), K, n(1), N),
        "sum(1/k^2, k, 1, n)",
        "∑[k = 1, n] 1/k²",
        "\\sum_{k=1}^{n}\\frac{1}{k^{2}}");
    row(
        Limit.of(Symbol.of("f"), X, n(0), Limit.Direction.RIGHT),
        "limit(f, x, 0, right)",
        "lim[x → 0+] f",
        "\\lim_{x\\to 0^{+}} f");
    row(
        Derivative.of(Symbol.of("f"), X, 2),
        "diff(f, x, 2)",
        "d²/dx² f",
        "\\frac{d^{2}}{dx^{2}} f");
  }

  /** A later term whose coefficient reads as negative, real, imaginary or complex, subtracts. */
  @Test
  void negativeCoefficientsSubtract() {
    Expr a = Symbol.of("a");
    Numeric minusI = Complex.I.negate();
    Numeric minusTwoI = new Complex(Int.ZERO, Int.of(-2));
    Numeric minusOnePlusTwoI = new Complex(Int.MINUS_ONE, Int.of(2));
    Numeric onePlusTwoI = new Complex(Int.ONE, Int.of(2));
    Object[][] cases = {
      {add(a, mul(Num.of(minusI), add(X, n(1)))), "a - i*(x + 1)", "a - i·(x + 1)"},
      {add(a, mul(Num.of(minusTwoI), X)), "a - 2*i*x", "a - 2i x"},
      {add(a, mul(Num.of(minusOnePlusTwoI), X)), "a - (1 - 2*i)*x", "a - (1 - 2i) x"},
      {add(a, mul(Num.of(onePlusTwoI.negate()), X)), "a - (1 + 2*i)*x", "a - (1 + 2i) x"},
      {add(a, mul(Num.of(onePlusTwoI), X)), "a + (1 + 2*i)*x", "a + (1 + 2i) x"},
      {add(a, mul(Num.of(minusI), Exprs.sqrt(Y))), "a - i*sqrt(y)", "a - i √y"},
    };
    for (Object[] c : cases) {
      Expr value = Canonical.of((Expr) c[0]);
      for (Printer p : List.of(AsciiPrinter.INSTANCE, UnicodePrinter.INSTANCE)) {
        String shown = p.display(value);
        assertThat(shown).isEqualTo(p == AsciiPrinter.INSTANCE ? c[1] : c[2]);
        assertThat(Canonical.of(Parser.parseExpression(shown, Names.NONE)))
            .as("round trip of %s", shown)
            .isEqualTo(value);
      }
    }
  }

  @Test
  void displayOrder() {
    Expr cube = add(pow(X, n(3)), mul(n(3), pow(X, n(2))), mul(n(3), X), n(1));
    assertThat(unicode(cube)).isEqualTo("x³ + 3x² + 3x + 1");
    assertThat(unicode(add(n(1), Exprs.neg(pow(X, n(2)))))).isEqualTo("-x² + 1");
    assertThat(unicode(add(X, n(-1)))).isEqualTo("x - 1");
    assertThat(unicode(add(mul(pow(X, n(2)), call("cos", X)), mul(n(2), X, call("sin", X)))))
        .isEqualTo("x² cos(x) + 2x sin(x)");
    assertThat(unicode(mul(add(X, n(-1)), pow(Constant.E, X)))).isEqualTo("(x - 1) e^x");
    assertThat(unicode(mul(add(X, n(1)), add(X, n(-1)), add(pow(X, n(2)), n(1)))))
        .isEqualTo("(x - 1)(x + 1)(x² + 1)");
    assertThat(unicode(mul(Num.of(Rational.of(1, 2)), pow(X, n(2))))).isEqualTo("x²/2");
    assertThat(unicode(mul(Num.of(Rational.of(-1, 2)), X))).isEqualTo("-x/2");
    assertThat(unicode(mul(n(-2), X))).isEqualTo("-2x");
    assertThat(unicode(add(Y, mul(n(-2), X)))).isEqualTo("-2x + y");
    assertThat(unicode(add(Y, mul(Num.of(Rational.of(-1, 2)), X)))).isEqualTo("-x/2 + y");
    assertThat(unicode(pow(X, Num.of(Rational.of(-1, 2))))).isEqualTo("1/√x");
    // A number's root folds before its reciprocal would, so it stays a power.
    assertThat(unicode(pow(n(2), Num.of(Rational.of(-1, 2))))).isEqualTo("2^(-1/2)");
    assertThat(unicode(mul(n(2), Exprs.sqrt(n(2))))).isEqualTo("2√2");
    assertThat(unicode(mul(n(3), pow(X, n(-1)), Y))).isEqualTo("3y/x");
    assertThat(unicode(add(X, Num.of(Complex.of(Int.of(1), Int.of(-2)))))).isEqualTo("x + 1 - 2i");
    assertThat(unicode(mul(Num.of(Complex.of(Int.of(0), Int.of(2))), X))).isEqualTo("2i x");
    assertThat(unicode(Num.of(Complex.of(Int.of(0), Rational.of(-1, 2))))).isEqualTo("-i/2");
    assertThat(unicode(add(mul(n(-2), pow(X, n(2))), mul(n(3), X), n(-1))))
        .isEqualTo("-2x² + 3x - 1");
    assertThat(unicode(add(n(3), mul(n(-2), call("sin", X))))).isEqualTo("-2 sin(x) + 3");
    assertThat(unicode(add(n(1), mul(Num.of(Rational.of(-1, 2)), X)))).isEqualTo("-x/2 + 1");
    Symbol a = Symbol.of("a");
    Symbol b = Symbol.of("b");
    assertThat(unicode(add(a, Exprs.neg(b)))).isEqualTo("a - b");
    assertThat(unicode(add(Y, X))).isEqualTo("x + y");
    assertThat(unicode(add(Symbol.of("x_2"), Symbol.of("x_1")))).isEqualTo("x₁ + x₂");
    assertThat(unicode(add(Exprs.neg(a), Exprs.neg(b)))).isEqualTo("-a - b");
    assertThat(unicode(add(mul(n(2), Constant.PI, K), mul(Num.of(Rational.of(1, 6)), Constant.PI))))
        .isEqualTo("2πk + π/6");
    assertThat(unicode(mul(n(2), Symbol.of("r"), Constant.PI))).isEqualTo("2πr");
    assertThat(unicode(mul(pow(Symbol.of("r"), n(2)), Constant.PI))).isEqualTo("πr²");
    assertThat(unicode(Exprs.neg(pow(X, n(-1))))).isEqualTo("-1/x");
    assertThat(unicode(Exprs.neg(pow(X, n(-2))))).isEqualTo("-1/x²");
    assertThat(unicode(mul(Num.of(Rational.of(-1, 2)), pow(X, n(-1))))).isEqualTo("-1/(2x)");
    assertThat(unicode(add(X, Exprs.neg(pow(X, n(-1)))))).isEqualTo("x - 1/x");
    assertThat(unicode(Num.of(Complex.of(Real.of("0.0"), Real.of("2.0"))))).isEqualTo("2.0i");
    assertThat(unicode(pow(Num.of(Real.of("2.0")), n(70))))
        .isEqualTo("1.180591620717411303424e+21");
    Matrix m = Matrix.of(List.of(List.of(X)));
    Matrix mb = Matrix.of(List.of(List.of(Y)));
    assertThat(unicode(mul(Num.of(Rational.of(1, 2)), m))).isEqualTo("[[x]]/2");
    assertThat(AsciiPrinter.INSTANCE.display(Canonical.of(mul(Num.of(Rational.of(2, 3)), m))))
        .isEqualTo("2*[[x]]/3");
    assertThat(unicode(mul(m, pow(mb, n(-1))))).isEqualTo("[[x]]·[[y]]^(-1)");
    assertThat(AsciiPrinter.INSTANCE.display(Canonical.of(mul(m, pow(mb, n(-1))))))
        .isEqualTo("[[x]]*[[y]]^(-1)");
    assertThat(unicode(mul(n(2), pow(m, n(-1))))).isEqualTo("2·[[x]]^(-1)");
    assertThat(unicode(pow(m, n(-2)))).isEqualTo("[[x]]^(-2)");
    assertThat(unicode(mul(n(-1), m))).isEqualTo("-[[x]]");
    assertThat(unicode(mul(Num.of(Rational.of(-1, 2)), X, m))).isEqualTo("-x·[[x]]/2");
  }

  @Test
  void setsOfConstantsInAscendingOrder() {
    assertThat(shown("{sqrt(2), -sqrt(2)}")).isEqualTo("{-√2, √2}");
    assertThat(shown("{i, -i, 2, -1}")).isEqualTo("{-1, 2, -i, i}");
    assertThat(shown("{1 + i, 1 - i, 1/2 + 2i}")).isEqualTo("{1/2 + 2i, 1 - i, 1 + i}");
    assertThat(shown("{pi, 3, e, cos(1), acos(-1/2)/3}"))
        .isEqualTo("{cos(1), acos(-1/2)/3, e, 3, π}");
    // ties keep the canonical order; a set with a symbol keeps its canonical order
    assertThat(shown("{1, 1.0}")).isEqualTo("{1, 1.0}");
    assertThat(shown("{x, -sqrt(2), sqrt(2)}")).isEqualTo("{√2, -√2, x}");
    // infinities at the ends of the reals; a tie at 30 digits decided at 60
    assertThat(shown("{i, inf, 2, -inf}")).isEqualTo("{-∞, 2, ∞, i}");
    assertThat(shown("{3.141592653589793238462643383279503, pi}"))
        .isEqualTo("{π, 3.141592653589793238462643383279503}");
    // values are inexact and bounded, so a huge power costs nothing and keeps canonical order
    long t = System.nanoTime();
    for (String huge : List.of("{3^1000000000, 2}", "{(1/3)^1000000000, 2}", "{2^1000, 1}")) {
      assertThat(shown(huge)).isNotEmpty();
    }
    // the three together: typically milliseconds locally (docs/reference/engine.md, Timing budgets)
    assertThat(System.nanoTime() - t).isLessThan(30_000_000_000L);
    assertThat(shown("{3^1000000000, 2}")).isEqualTo("{2, 3¹⁰⁰⁰⁰⁰⁰⁰⁰⁰}");
    assertThat(shown("{2^1000, 1}")).startsWith("{1, ");
    // the order is a display choice: the canonical set is unchanged
    Expr set = Canonical.of(Parser.parseExpression("{sqrt(2), -sqrt(2)}", Names.NONE));
    assertThat(
            Canonical.of(Parser.parseExpression(UnicodePrinter.INSTANCE.display(set), Names.NONE)))
        .isEqualTo(set);
  }

  @Test
  void aThousandConstantsOrderInMilliseconds() {
    List<Expr> items = new java.util.ArrayList<>();
    for (int k = 1; k <= 1000; k++) {
      items.add(Canonical.of(Parser.parseExpression("sqrt(" + k + ") - " + (k % 37), Names.NONE)));
    }
    List<Expr> members =
        ((mathcanvas.engine.expr.SetExpr) Canonical.of(new mathcanvas.engine.expr.SetExpr(items)))
            .items();
    ConstantOrder.of(members);
    long t = System.nanoTime();
    List<Expr> ordered = ConstantOrder.of(members);
    long ms = (System.nanoTime() - t) / 1_000_000;
    assertThat(ordered).hasSize(members.size());
    // typically milliseconds locally (docs/reference/engine.md, Timing budgets)
    assertThat(ms).as("ordering 1,000 constants").isLessThan(30_000);
  }

  private static String shown(String text) {
    return UnicodePrinter.INSTANCE.display(Canonical.of(Parser.parseExpression(text, Names.NONE)));
  }

  @Test
  void printingIsFaithfulToTheHeldTree() {
    held(Sum.of(n(1), X), "1 + x", "1 + x");
    held(Sum.of(Sum.of(X, Y), n(1)), "(x + y) + 1", "(x + y) + 1");
    held(mul(mul(X, Y), U), "(x*y)*u", "(x y) u");
    held(mul(n(1), Exprs.inv(n(2)), X), "1/2*x", "1/2x");
    held(Exprs.neg(pow(n(2), n(2))), "-2^2", "-2²");
    held(pow(Exprs.neg(X), n(2)), "(-x)^2", "(-x)²");
    held(Exprs.neg(Exprs.neg(X)), "-(-x)", "-(-x)");
    held(Sum.of(X, n(-3)), "x + -3", "x + -3");
    held(Exprs.sub(X, mul(n(5), X)), "x - 5*x", "x - 5x");
    held(mul(X, n(-2)), "x*(-2)", "x·(-2)");
    held(mul(n(2), Num.of(Rational.of(1, 2))), "2*(1/2)", "2(1/2)");
    held(pow(X, Num.of(Rational.of(1, 3))), "x^(1/3)", "x^(1/3)");
    held(pow(X, Exprs.neg(N)), "x^(-n)", "x^(-n)");
    held(pow(Constant.E, mul(n(2), X)), "e^(2*x)", "e^(2x)");
    held(pow(X, Num.of(Real.of("1.5"))), "x^(1.5)", "x^(1.5)");
    held(pow(X, call("f", X)), "x^f(x)", "x^f(x)");
    held(pow(X, n(-1)), "x^(-1)", "x^(-1)");
    held(Exprs.sqrt(X), "sqrt(x)", "√x");
    held(pow(Exprs.sqrt(X), n(2)), "sqrt(x)^2", "(√x)²");
    held(Exprs.sqrt(Num.of(Int.of(-1))), "sqrt(-1)", "√(-1)");
    held(call("cbrt", X), "cbrt(x)", "∛x");
    held(call("root", X, n(4)), "root(x, 4)", "∜x");
    held(call("factorial", add(X, n(1))), "factorial(x + 1)", "(x + 1)!");
    held(call("factorial", call("factorial", n(5))), "factorial(factorial(5))", "5!!");
    held(call("factorial", call("abs", X)), "factorial(abs(x))", "|x|!");
    held(Num.of(Real.of("1.5e-30")), "1.5e-30", "1.5e-30");
    held(Num.of(Real.of("1e21")), "1.0e+21", "1.0e+21");
    held(Num.of(Real.of("1e20")), "100000000000000000000.0", "100000000000000000000.0");
    held(mul(n(2), Constant.PI, K), "2*pi*k", "2πk");
    held(mul(Constant.E, K), "e*k", "e k");
    held(mul(n(2), Constant.E), "2*e", "2 e");
    held(mul(X, n(2)), "x*2", "x·2");
    Symbol d = Symbol.of("d");
    held(mul(d, Exprs.inv(Symbol.of("dx"))), "d / dx", "d / dx");
    held(mul(pow(d, n(2)), Exprs.inv(pow(Symbol.of("dx"), n(2)))), "d^2 / dx^2", "d² / dx²");
    held(mul(call("root", d, n(4)), Exprs.inv(Symbol.of("dx"))), "root(d, 4)/dx", "∜d / dx");
    held(mul(Constant.E, add(X, n(1))), "e*(x + 1)", "e·(x + 1)");
    held(mul(pow(X, n(2)), Y), "x^2*y", "x² y");
    held(mul(X, Vector.of(n(1), n(2))), "x*[1, 2]", "x ⟨1, 2⟩");
    held(mul(X, Matrix.of(List.of(List.of(n(1))))), "x*[[1]]", "x·[[1]]");
    held(mul(n(2), call("abs", X)), "2*abs(x)", "2|x|");
    held(mul(call("abs", X), call("abs", Y)), "abs(x)*abs(y)", "|x| |y|");
    held(call("abs", call("abs", X)), "abs(abs(x))", "abs(|x|)");
    held(call("abs", Exprs.sub(X, call("abs", Y))), "abs(x - abs(y))", "abs(x - |y|)");
    held(Lambda.of(List.of(X, Y), add(X, Y)), "(x, y) -> x + y", "(x, y) → x + y");
    Symbol left = Symbol.of("left");
    held(call("limit", X, X, n(0), left), "limit(x, x, 0, (left))", "limit(x, x, 0, (left))");
    held(Limit.of(X, X, n(0), Limit.Direction.LEFT), "limit(x, x, 0, left)", "lim[x → 0-] x");
    held(Sum.of(Lambda.of(X, X), n(1)), "(x -> x) + 1", "(x → x) + 1");
    held(
        Relation.of(Relation.Op.EQ, Relation.of(Relation.Op.LT, X, Y), n(1)),
        "(x < y) = 1",
        "(x < y) = 1");
    held(Interval.of(n(0), n(1), true, false), "[0, 1)", "[0, 1)");
    held(
        Piecewise.of(List.of(new Piecewise.Piece(X, Relation.of(Relation.Op.GT, X, n(0)))), n(0)),
        "piecewise(x, x > 0, 0)",
        "piecewise(x, x > 0, 0)");
  }

  @Test
  void crossProductParentheses() {
    Expr cross = call("cross", U, V);
    held(mul(n(2), cross), "2*cross(u, v)", "2(u × v)");
    held(call("cross", U, mul(n(2), V)), "cross(u, 2*v)", "u × (2v)");
    held(mul(X, Exprs.inv(cross)), "x/cross(u, v)", "x/(u × v)");
    held(mul(cross, Y), "cross(u, v)*y", "u × v y");
  }

  @Test
  void bigOperatorsTakeTheRestOfTheTerm() {
    Expr sum = BigSum.of(K, K, n(1), N);
    held(mul(sum, Y), "sum(k, k, 1, n)*y", "(∑[k = 1, n] k) y");
    held(mul(Y, sum), "y*sum(k, k, 1, n)", "y ∑[k = 1, n] k");
    held(pow(sum, n(2)), "sum(k, k, 1, n)^2", "(∑[k = 1, n] k)²");
    held(mul(sum, Exprs.inv(n(2))), "sum(k, k, 1, n)/2", "(∑[k = 1, n] k)/2");
    held(Derivative.of(add(X, n(1)), X, 1), "diff(x + 1, x)", "d/dx (x + 1)");
    Expr derivative = Derivative.of(call("factorial", X), X, 1);
    held(mul(n(2), derivative), "2*diff(factorial(x), x)", "2 d/dx x!");
    held(mul(Constant.PI, derivative), "pi*diff(factorial(x), x)", "π d/dx x!");
    held(mul(n(2), Derivative.of(X, X, 2)), "2*diff(x, x, 2)", "2 d²/dx² x");
    held(mul(n(2), sum), "2*sum(k, k, 1, n)", "2 ∑[k = 1, n] k");
    held(mul(n(2), BigProduct.of(K, K, n(1), N)), "2*product(k, k, 1, n)", "2 ∏[k = 1, n] k");
    held(mul(n(2), Integral.indefinite(X, X)), "2*integrate(x, x)", "2 ∫ x dx");
    held(
        mul(n(2), Limit.of(Symbol.of("f"), X, n(0), Limit.Direction.BOTH)),
        "2*limit(f, x, 0)",
        "2 lim[x → 0] f");
    held(
        mul(Constant.PI, Limit.of(Symbol.of("f"), X, n(0), Limit.Direction.BOTH)),
        "pi*limit(f, x, 0)",
        "π lim[x → 0] f");
    held(mul(n(2), Symbol.of("d"), Exprs.inv(Symbol.of("dx"))), "2*d / dx", "2d / dx");
    held(
        Limit.of(X, X, add(Y, n(1)), Limit.Direction.LEFT),
        "limit(x, x, y + 1, left)",
        "lim[x → y + 1-] x");
  }

  @Test
  void integrals() {
    Expr integral = Integral.indefinite(X, X);
    held(pow(integral, n(2)), "integrate(x, x)^2", "(∫ x dx)²");
    held(call("factorial", integral), "factorial(integrate(x, x))", "(∫ x dx)!");
    held(mul(integral, Y), "integrate(x, x)*y", "∫ x dx y");
    held(
        Integral.definite(X, X, n(0), Constant.INFINITY),
        "integrate(x, x, 0, inf)",
        "∫[0, ∞] x dx");
    held(Integral.indefinite(call("dist", X), X), "integrate(dist(x), x)", "integrate(dist(x), x)");
    held(
        Integral.indefinite(BigSum.of(Symbol.of("dk"), Symbol.of("dk"), n(1), N), X),
        "integrate(sum(dk, dk, 1, n), x)",
        "integrate(∑[dk = 1, n] dk, x)");
    held(Integral.indefinite(X, Symbol.of("ot")), "integrate(x, ot)", "integrate(x, ot)");
    held(Integral.indefinite(X, Symbol.of("dx")), "integrate(x, dx)", "integrate(x, dx)");
    held(Integral.indefinite(call("dot", U, V), X), "integrate(dot(u, v), x)", "∫ dot(u, v) dx");
    held(Integral.indefinite(ListExpr.of(), X), "integrate([], x)", "∫ ([]) dx");
  }

  @Test
  void subscriptsAndNames() {
    held(Symbol.of("a_n"), "a_n", "aₙ");
    held(Symbol.of("x_b"), "x_b", "x_b");
    held(Symbol.of("_1"), "_1", "_1");
    held(Symbol.of("x_12"), "x_12", "x₁₂");
    held(Symbol.of("θ"), "θ", "θ");
    assertThat(LatexPrinter.INSTANCE.print(Symbol.of("θ_1"))).isEqualTo("\\theta_{1}");
    assertThat(LatexPrinter.INSTANCE.print(Symbol.of("velocity"))).isEqualTo("\\mathit{velocity}");
    assertThat(LatexPrinter.INSTANCE.print(call("asin", X))).isEqualTo("\\arcsin(x)");
    assertThat(LatexPrinter.INSTANCE.print(call("f", X))).isEqualTo("f(x)");
    assertThat(LatexPrinter.INSTANCE.print(Num.of(Real.of("1.5e-30"))))
        .isEqualTo("1.5\\times 10^{-30}");
  }
}
