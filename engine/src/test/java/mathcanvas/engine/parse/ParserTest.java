package mathcanvas.engine.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.print.AsciiPrinter;
import mathcanvas.engine.print.LatexPrinter;
import mathcanvas.engine.print.UnicodePrinter;
import org.junit.jupiter.api.Test;

class ParserTest {

  // Each whole parse: typically milliseconds locally (docs/reference/engine.md, Timing budgets).
  private static final Duration BUDGET = Duration.ofSeconds(30);

  private static final Symbol A = Symbol.of("a");
  private static final Symbol B = Symbol.of("b");
  private static final Symbol C = Symbol.of("c");
  private static final Symbol X = Symbol.of("x");

  private static Expr parse(String text) {
    return Parser.parseExpression(text, Names.NONE);
  }

  private static ParseException error(String text) {
    try {
      Parser.parseScript(text, Names.NONE);
    } catch (ParseException e) {
      return e;
    }
    throw new AssertionError("no error for " + text);
  }

  @Test
  void rawTreesMirrorTheInput() {
    assertThat(parse("a + b + c")).isEqualTo(Sum.of(A, B, C));
    assertThat(parse("(a + b) + c")).isEqualTo(Sum.of(Sum.of(A, B), C));
    assertThat(parse("a - b - c")).isEqualTo(Sum.of(A, Exprs.neg(B), Exprs.neg(C)));
    assertThat(parse("a b c")).isEqualTo(Product.of(A, B, C));
    assertThat(parse("a/b/c")).isEqualTo(Product.of(A, Exprs.inv(B), Exprs.inv(C)));
    assertThat(parse("-2")).isEqualTo(Exprs.neg(Num.of(2)));
    assertThat(parse("x + x")).isEqualTo(Sum.of(X, X));
    assertThat(parse("20%")).isEqualTo(Product.of(Num.of(20), Exprs.inv(Num.of(100))));
    assertThat(parse("30°")).isEqualTo(Product.of(Num.of(30), Constant.PI, Exprs.inv(Num.of(180))));
    assertThat(parse("√x")).isEqualTo(Power.of(X, Num.of(Rational.of(1, 2))));
    assertThat(parse("sqrt(x)")).isEqualTo(parse("√x"));
    assertThat(parse("sqrt x")).isEqualTo(parse("√x"));
    assertThat(parse("x^(1/2)")).isEqualTo(Power.of(X, Product.of(Num.ONE, Exprs.inv(Num.of(2)))));
    assertThat(parse("x⁻¹")).isEqualTo(Power.of(X, Num.MINUS_ONE));
    assertThat(parse("x^-1")).isEqualTo(Power.of(X, Exprs.neg(Num.ONE)));
    assertThat(parse("i")).isEqualTo(Num.of(Complex.I));
    assertThat(parse("e")).isEqualTo(Constant.E);
    assertThat(parse("false")).isEqualTo(Bool.FALSE);
    assertThat(parse("Integers")).isEqualTo(Constant.INTEGERS);
    assertThat(parse("ℂ")).isEqualTo(Constant.COMPLEXES);
    assertThat(parse("0.5")).isEqualTo(Num.of(Real.of("0.5")));
    assertThat(parse("[1, x]")).isEqualTo(Vector.of(Num.ONE, X));
    assertThat(parse("arcsin(x)")).isEqualTo(Apply.of(Symbol.of("asin"), X));
    assertThat(parse("\n x \n")).isEqualTo(X);
  }

  @Test
  void scriptsHaveStatements() {
    Script script = Parser.parseScript("a := 5;\nf(x) := x^2\nf(2)", Names.NONE);
    assertThat(script.statements()).hasSize(3);
    Statement.Definition a = (Statement.Definition) script.statements().getFirst();
    assertThat(a.name()).isEqualTo(A);
    assertThat(a.params()).isEmpty();
    assertThat(a.quiet()).isTrue();
    Statement.Definition f = (Statement.Definition) script.statements().get(1);
    assertThat(f.params()).containsExactly(X);
    assertThat(f.quiet()).isFalse();
    Statement.Expression call = (Statement.Expression) script.statements().get(2);
    assertThat(call.expr()).isEqualTo(Apply.of(Symbol.of("f"), Num.of(2)));
  }

  @Test
  void statementsAndNodesHavePositions() {
    Script script = Parser.parseScript("a := 5;  b\n  (x + 1)! * sin(y)\n[1,\n 2] - z", Names.NONE);
    List<Statement> statements = script.statements();
    assertThat(statements.stream().map(Statement::start))
        .containsExactly(
            new Position(1, 1, 0),
            new Position(1, 10, 9),
            new Position(2, 3, 13),
            new Position(3, 1, 31));
    Product product = (Product) ((Statement.Expression) statements.get(2)).expr();
    Apply factorial = (Apply) product.factors().get(0);
    Sum inner = (Sum) factorial.args().getFirst();
    Apply sin = (Apply) product.factors().get(1);
    assertThat(script.positionOf(product)).contains(new Position(2, 3, 13));
    assertThat(script.positionOf(factorial)).contains(new Position(2, 3, 13));
    assertThat(script.positionOf(inner)).contains(new Position(2, 4, 14));
    assertThat(script.positionOf(inner.terms().get(1))).contains(new Position(2, 8, 18));
    assertThat(script.positionOf(sin)).contains(new Position(2, 14, 24));
    assertThat(script.positionOf(sin.args().getFirst())).contains(new Position(2, 18, 28));
    Sum difference = (Sum) ((Statement.Expression) statements.get(3)).expr();
    assertThat(script.positionOf(difference)).contains(new Position(3, 1, 31));
    assertThat(script.positionOf(difference.terms().get(1))).contains(new Position(4, 5, 39));
    Statement.Definition a = (Statement.Definition) statements.getFirst();
    assertThat(script.positionOf(a.body())).contains(new Position(1, 6, 5));
  }

  @Test
  void aNodeSharedBetweenPlacesHasNoPosition() {
    Script script = Parser.parseScript("π + 2π\nπ", Names.NONE);
    Expr first = ((Statement.Expression) script.statements().get(1)).expr();
    assertThat(first).isSameAs(Constant.PI);
    assertThat(script.positionOf(Constant.PI)).isEmpty();
    assertThat(script.positionOf(Symbol.of("x"))).isEmpty();
    Script once = Parser.parseScript("x + π", Names.NONE);
    assertThat(once.positionOf(Constant.PI)).contains(new Position(1, 5, 4));
  }

  @Test
  void theEnvironmentDecidesCalls() {
    Names g = Set.of("g")::contains;
    assertThat(Parser.parseExpression("g(x)", g)).isEqualTo(Apply.of(Symbol.of("g"), X));
    assertThat(Parser.parseExpression("g(x)", Names.NONE)).isEqualTo(Product.of(Symbol.of("g"), X));
    Script redefined = Parser.parseScript("g := 3\ng(x)", g);
    assertThat(((Statement.Expression) redefined.statements().get(1)).expr())
        .isEqualTo(Product.of(Symbol.of("g"), X));
    Script cleared = Parser.parseScript("clear(g)\ng(x)", g);
    assertThat(((Statement.Expression) cleared.statements().get(1)).expr())
        .isEqualTo(Product.of(Symbol.of("g"), X));
  }

  @Test
  void parseExpressionTakesOneExpression() {
    assertThatThrownBy(() -> parse("a; b")).hasMessage("unexpected `;`");
    assertThatThrownBy(() -> parse("a\nb"))
        .isInstanceOf(ParseException.class)
        .hasMessage("unexpected `b`");
    assertThatThrownBy(() -> parse("a := 1")).hasMessage("`:=` defines at statement level only");
  }

  @Test
  void errorsComeInTextOrder() {
    ParseException e = error("(x + ) ∂");
    assertThat(e.getMessage()).isEqualTo("expected an operand");
    assertThat(e.column()).isEqualTo(6);
    assertThat(error("x ∂ +").getMessage()).isEqualTo("unknown operator `∂`");
    // A binder's variable is read ahead, but refused only after the text before it.
    assertThat(error("∫ (x + ) di").column()).isEqualTo(8);
    assertThat(error("solve(x +, pi)").column()).isEqualTo(10);
    assertThat(error("diff(2 3, i)").column()).isEqualTo(8);
    assertThat(error("∫ 2 3 di").column()).isEqualTo(5);
    assertThat(error("diff(x^2, i)").getMessage()).isEqualTo("`i` cannot be the variable here");
    assertThat(error("∫ (x + ) ∂ dx").getMessage()).isEqualTo("expected an operand");
  }

  @Test
  void expectedNamesWhatWasExpected() {
    assertThat(error("(x").expected()).contains("`)`");
    assertThat(error("2 +").expected()).contains("an operand");
    assertThat(error("x]").expected()).isEmpty();
    assertThat(error("(x").toString()).isEqualTo("1:3: expected `)`");
  }

  /**
   * A construct nested {@code levels} deep parses, and its tree prints, canonicalises and hashes
   * within the test's 1 MB stack; one more level is refused. {@code cost} is the levels one
   * repetition adds.
   */
  private static void nests(String name, IntFunction<String> text, int cost) {
    int n = Parser.MAX_NESTING / cost;
    Expr e = assertTimeoutPreemptively(BUDGET, () -> parse(text.apply(n)));
    assertThat(AsciiPrinter.INSTANCE.print(e)).as(name).isNotEmpty();
    assertThat(UnicodePrinter.INSTANCE.print(e)).as(name).isNotEmpty();
    assertThat(LatexPrinter.INSTANCE.print(e)).as(name).isNotEmpty();
    Expr canonical = Canonical.of(e);
    assertThat(UnicodePrinter.INSTANCE.display(canonical)).as(name).isNotEmpty();
    assertThat(e.hashCode()).as(name).isEqualTo(parse(text.apply(n)).hashCode());
    assertThat(canonical.hashCode()).as(name).isEqualTo(Canonical.of(e).hashCode());
    ParseException deep = error(text.apply(n + 1));
    assertThat(deep.getMessage()).as(name).isEqualTo("expression nested too deeply");
  }

  @Test
  void nestingIsCappedAtTwoHundredLevels() {
    assertThat(Parser.MAX_NESTING).isEqualTo(200);
    nests("parentheses", n -> "(".repeat(n) + "x" + ")".repeat(n), 1);
    nests("brackets", n -> "[".repeat(n) + "x" + "]".repeat(n), 1);
    nests("braces", n -> "{".repeat(n) + "x" + "}".repeat(n), 1);
    nests("signs", n -> "-".repeat(n) + "x", 1);
    nests("powers", n -> "2^".repeat(n) + "2", 1);
    nests("factorials", n -> "x" + "!".repeat(n), 1);
    nests("percents", n -> "x" + "%".repeat(n), 2);
    nests("superscripts", n -> "x" + " ²".repeat(n), 1);
    nests("prefix names", n -> "sin ".repeat(n) + "x", 1);
    nests("radicals", n -> "√".repeat(n) + "x", 1);
    nests("lambdas", n -> "x -> ".repeat(n) + "x", 1);
    nests("calls", n -> "sin(".repeat(n) + "x" + ")".repeat(n), 1);
    nests("cross products", n -> "u" + " × u".repeat(n), 1);
    nests("sums", n -> "Σ[k=1,n] ".repeat(n) + "k", 1);
    nests("limits", n -> "lim[x→0] ".repeat(n) + "x", 1);
    nests("derivatives", n -> "d/dx ".repeat(n) + "x", 1);
    nests("integrals", n -> "∫ ".repeat(n) + "x" + " dx".repeat(n), 1);
    nests("bars", n -> "|(".repeat(n) + "x" + ")|".repeat(n), 2);
    nests("relations in parentheses", n -> "(x = ".repeat(n) + "x" + ")".repeat(n), 2);
    assertThat(error("(".repeat(201) + "x" + ")".repeat(201)).column()).isEqualTo(201);
    // At the cap a tree echoes to text the parser accepts again: the parentheses the printers
    // put around a prefix operand add no level.
    for (String deepest :
        List.of("-".repeat(200) + "x", "√".repeat(200) + "x", "2^".repeat(200) + "x")) {
      Expr e = parse(deepest);
      assertThat(parse(AsciiPrinter.INSTANCE.print(e))).isEqualTo(e);
      assertThat(parse(UnicodePrinter.INSTANCE.print(e))).isEqualTo(e);
    }
    assertThat(parse("-(-(-x))")).isEqualTo(parse("---x"));
  }

  @Test
  void longChainsAreFlatAndFast() {
    String sum = "1" + "+1".repeat(9_999);
    Expr e = assertTimeoutPreemptively(BUDGET, () -> parse(sum));
    assertThat(((Sum) e).terms()).hasSize(10_000);
    assertThat(Canonical.of(e)).isEqualTo(Num.of(10_000));
    Expr product = parse("x" + " x".repeat(9_999));
    assertThat(((Product) product).factors()).hasSize(10_000);
    Expr difference = parse("a" + " - a".repeat(9_999));
    assertThat(((Sum) difference).terms()).hasSize(10_000);
    assertThat(AsciiPrinter.INSTANCE.print(difference)).hasSize(4 * 9_999 + 1);
  }
}
