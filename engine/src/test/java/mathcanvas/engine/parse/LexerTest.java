package mathcanvas.engine.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LexerTest {

  private static List<Token> tokens(String source) {
    Lexer lexer = new Lexer(source);
    List<Token> out = new ArrayList<>();
    for (Token t = lexer.next(); !t.is(TokenKind.EOF); t = lexer.next()) {
      out.add(t);
    }
    return out;
  }

  private static String kinds(String source) {
    return String.join(
        " ",
        tokens(source).stream()
            .map(
                t ->
                    t.kind()
                        + (t.is(TokenKind.IDENT)
                                || t.is(TokenKind.INT)
                                || t.is(TokenKind.DECIMAL)
                                || t.is(TokenKind.SUPERSCRIPT)
                                || t.is(TokenKind.DOMAIN)
                                || t.is(TokenKind.DERIVATIVE)
                                || t.is(TokenKind.PARTIAL)
                            ? ":" + t.text()
                            : ""))
            .toList());
  }

  private static ParseException error(String source) {
    try {
      tokens(source);
    } catch (ParseException e) {
      return e;
    }
    throw new AssertionError("no error for " + source);
  }

  @Test
  void numbers() {
    assertThat(kinds("0 123 007")).isEqualTo("INT:0 INT:123 INT:007");
    assertThat(kinds("1.5 .5 2.0")).isEqualTo("DECIMAL:1.5 DECIMAL:.5 DECIMAL:2.0");
    assertThat(kinds("1.5e3 1e-3 2E10 .5e+2"))
        .isEqualTo("DECIMAL:1.5e3 DECIMAL:1e-3 DECIMAL:2E10 DECIMAL:.5e+2");
    assertThat(kinds("2e")).isEqualTo("INT:2 IDENT:e");
    assertThat(kinds("2e+x")).isEqualTo("INT:2 IDENT:e PLUS IDENT:x");
    assertThat(kinds("2ex")).isEqualTo("INT:2 IDENT:ex");
    assertThat(kinds("1.5.2")).isEqualTo("DECIMAL:1.5 DECIMAL:.2");
  }

  @Test
  void identifiers() {
    assertThat(kinds("x xy x1 x_1 θ _tmp"))
        .isEqualTo("IDENT:x IDENT:xy IDENT:x1 IDENT:x_1 IDENT:θ IDENT:_tmp");
    assertThat(kinds("x₁ x₁₂ aₙ x₁y₂")).isEqualTo("IDENT:x_1 IDENT:x_12 IDENT:a_n IDENT:x_1y_2");
    assertThat(kinds("2πr")).isEqualTo("INT:2 PI IDENT:r");
    assertThat(kinds("πi")).isEqualTo("PI IDENT:i");
    assertThat(error("xⁿ").getMessage()).isEqualTo("unknown operator `ⁿ`");
    assertThat(kinds("x in ℤ")).isEqualTo("IDENT:x IN DOMAIN:ℤ");
    assertThat(kinds("in2")).isEqualTo("IDENT:in2");
  }

  @Test
  void operatorsTakeTheLongestMatch() {
    assertThat(kinds("5!=3")).isEqualTo("INT:5 NE INT:3");
    assertThat(kinds("5!!")).isEqualTo("INT:5 BANG BANG");
    assertThat(kinds("x<-1")).isEqualTo("IDENT:x LT MINUS INT:1");
    assertThat(kinds("x->y")).isEqualTo("IDENT:x ARROW IDENT:y");
    assertThat(kinds("a**b")).isEqualTo("IDENT:a CARET IDENT:b");
    assertThat(kinds("a := b")).isEqualTo("IDENT:a DEFINE IDENT:b");
    assertThat(kinds("a·b⋅c÷d−e×f"))
        .isEqualTo("IDENT:a STAR IDENT:b STAR IDENT:c SLASH IDENT:d MINUS IDENT:e CROSS IDENT:f");
    assertThat(kinds("≤≥≠∈→")).isEqualTo("LE GE NE IN ARROW");
    assertThat(kinds("√∛∜∫Σ∑Π∏∞"))
        .isEqualTo("SQRT CBRT QRT INTEGRAL SIGMA SIGMA CAPITAL_PI CAPITAL_PI INFINITY");
    assertThat(kinds("x²⁻¹")).isEqualTo("IDENT:x SUPERSCRIPT:2 SUPERSCRIPT:-1");
    assertThat(kinds("x¹⁰")).isEqualTo("IDENT:x SUPERSCRIPT:10");
  }

  @Test
  void operatorErrorsCarryHints() {
    assertThat(error("x == 1").getMessage()).isEqualTo("use `=`");
    assertThat(error("x =< 1").getMessage()).isEqualTo("use `<=`");
    assertThat(error("x => 1").getMessage()).isEqualTo("use `>=`");
    assertThat(error("x <> 1").getMessage()).isEqualTo("use `!=`");
    assertThat(error("∇f").getMessage()).isEqualTo("unknown operator `∇`");
    assertThat(error("a ⊗ b").getMessage()).isEqualTo("unknown operator `⊗`");
    assertThat(error("a​b").getMessage()).isEqualTo("unknown operator U+200B");
    assertThat(error("5.").getMessage()).isEqualTo("expected a digit after `.`");
    assertThat(error("x⁻").getMessage()).isEqualTo("expected superscript digits");
  }

  @Test
  void partialToken() {
    Token t = tokens("∂/∂x").getFirst();
    assertThat(t.kind()).isEqualTo(TokenKind.PARTIAL);
    assertThat(t.text()).isEqualTo("|x");
    assertThat(t.order()).isEqualTo(BigInteger.ONE);
    assertThat(kinds("∂²/∂x∂y ∂^3/∂x^2∂y ∂f/∂θ ∂²g/∂x² ∂/∂x₁"))
        .isEqualTo("PARTIAL:|y x PARTIAL:|y x x PARTIAL:f|θ PARTIAL:g|x x PARTIAL:|x_1");
    String shape = "the partial derivative notation is ∂/∂x, ∂ⁿ/∂xⁿ, ∂ⁿ/∂x∂y or ∂f/∂x";
    String orders = "the order must be the sum of the denominator's orders";
    assertThat(error("∂").getMessage()).isEqualTo(shape);
    assertThat(error("∂x").getMessage()).isEqualTo(shape);
    assertThat(error("∂/x").getMessage()).isEqualTo(shape);
    assertThat(error("∂/∂").getMessage()).isEqualTo(shape);
    assertThat(error("∂/∂x²∂").getMessage()).isEqualTo(shape);
    assertThat(error("∂²/∂x f").getMessage()).isEqualTo(orders);
    assertThat(error("∂/∂x∂y f").getMessage()).isEqualTo(orders);
    assertThat(error("∂/∂x^0 f").getMessage()).isEqualTo(orders);
    assertThat(error("x + ∂²/∂x f").column()).isEqualTo(5);
  }

  @Test
  void derivativeToken() {
    Token t = tokens("d/dx").getFirst();
    assertThat(t.kind()).isEqualTo(TokenKind.DERIVATIVE);
    assertThat(t.text()).isEqualTo("x");
    assertThat(t.order()).isEqualTo(BigInteger.ONE);
    assertThat(kinds("d^2/dx^2 d²/dx² d/dθ d/dx₁"))
        .isEqualTo("DERIVATIVE:x DERIVATIVE:x DERIVATIVE:θ DERIVATIVE:x_1");
    assertThat(tokens("d^12/dy^12").getFirst().order()).isEqualTo(BigInteger.valueOf(12));
    assertThat(kinds("d / dx")).isEqualTo("IDENT:d SLASH IDENT:dx");
    assertThat(kinds("d^2 + 1")).isEqualTo("IDENT:d CARET INT:2 PLUS INT:1");
    assertThat(kinds("d(x)")).isEqualTo("IDENT:d LPAREN IDENT:x RPAREN");
    String shape = "the derivative notation is d/dx, d^n/dx^n or dⁿ/dxⁿ";
    assertThat(error("d^2/dx² f").getMessage()).isEqualTo(shape);
    assertThat(error("d/dx² f").getMessage()).isEqualTo(shape);
    assertThat(error("d/dx^2 f").getMessage()).isEqualTo(shape);
    assertThat(error("d^2/dx f").getMessage()).isEqualTo(shape);
    assertThat(error("d²/dx f").getMessage()).isEqualTo(shape);
    String orders = "the orders must be equal positive integers";
    assertThat(error("d^2/dx^3 f").getMessage()).isEqualTo(orders);
    assertThat(error("d^0/dx^0 f").getMessage()).isEqualTo(orders);
  }

  @Test
  void newlinesEndStatementsOnlyOutsideBrackets() {
    assertThat(kinds("a\nb")).isEqualTo("IDENT:a NEWLINE IDENT:b");
    assertThat(kinds("(a\nb)")).isEqualTo("LPAREN IDENT:a IDENT:b RPAREN");
    assertThat(kinds("a\r\nb\rc")).isEqualTo("IDENT:a NEWLINE IDENT:b NEWLINE IDENT:c");
    assertThat(kinds("a # comment\nb")).isEqualTo("IDENT:a NEWLINE IDENT:b");
    assertThat(kinds("a)\nb")).isEqualTo("IDENT:a RPAREN NEWLINE IDENT:b");
  }

  @Test
  void positionsAreInCodePointsOfTheTextAsTyped() {
    List<Token> ts = tokens("a\tb\n  𝑥 + c");
    assertThat(ts.get(1).column()).isEqualTo(3);
    Token astral = ts.get(3);
    assertThat(astral.line()).isEqualTo(2);
    assertThat(astral.column()).isEqualTo(3);
    Token plus = ts.get(4);
    assertThat(plus.column()).isEqualTo(5);
    assertThat(plus.offset()).isEqualTo(9);
    ts = tokens("a\r\nb");
    assertThat(ts.get(2).line()).isEqualTo(2);
    assertThat(ts.get(2).column()).isEqualTo(1);
  }

  @Test
  void normalisationIsNfcAndKeepsTypedPositions() {
    // e + combining acute is é, one identifier; ≠ written as = + U+0338 is ≠.
    List<Token> ts = tokens("é ≠ x");
    assertThat(ts.get(0).text()).isEqualTo("é");
    assertThat(ts.get(1).kind()).isEqualTo(TokenKind.NE);
    assertThat(ts.get(1).column()).isEqualTo(4);
    assertThat(ts.get(2).column()).isEqualTo(7);
    ParseException e = error("é + ∇");
    assertThat(e.column()).isEqualTo(6);
    assertThat(e.offset()).isEqualTo(5);
    // The Ångström sign normalises to Å, a letter.
    assertThat(tokens("Å").getFirst().text()).isEqualTo("Å");
  }

  @Test
  void aLeadingByteOrderMarkIsSkipped() {
    List<Token> ts = tokens("\uFEFFx + 1");
    assertThat(ts.getFirst().text()).isEqualTo("x");
    assertThat(ts.getFirst().column()).isEqualTo(2);
    assertThat(ts.getFirst().offset()).isEqualTo(1);
    assertThat(error("\uFEFF∇").column()).isEqualTo(2);
    assertThat(error("x\uFEFF").getMessage()).isEqualTo("unknown operator U+FEFF");
  }

  @Test
  void numeralsAreKeptUpToTheirBound() {
    String longest = "7".repeat(Lexer.MAX_DIGITS);
    assertThat(tokens(longest).getFirst().text()).isEqualTo(longest);
    assertThat(error("1" + longest).getMessage()).isEqualTo("number out of range");
    assertThat(error("0." + longest).getMessage()).isEqualTo("number out of range");
    assertThat(error("x" + "²".repeat(Lexer.MAX_DIGITS + 1)).getMessage())
        .isEqualTo("number out of range");
    assertThat(error("d^" + "2".repeat(Lexer.MAX_DIGITS + 1) + "/dx^2").getMessage())
        .isEqualTo("number out of range");
  }

  @Test
  void endOfInputHasThePositionAfterTheText() {
    Lexer lexer = new Lexer("ab\ncd");
    Token t;
    do {
      t = lexer.next();
    } while (!t.is(TokenKind.EOF));
    assertThat(t.line()).isEqualTo(2);
    assertThat(t.column()).isEqualTo(3);
    assertThat(t.offset()).isEqualTo(5);
    assertThatThrownBy(() -> tokens(":")).isInstanceOf(ParseException.class);
  }
}
