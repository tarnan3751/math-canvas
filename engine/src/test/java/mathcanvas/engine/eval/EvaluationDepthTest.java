package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.value;
import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.parse.ParseException;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

/**
 * The evaluation limits, run on the tests' 1 MB thread stack: nothing deeper than the limits
 * overflows the stack, and the limits are errors, not crashes.
 */
class EvaluationDepthTest {

  /**
   * A value shown reads back: {@code 1/3^210000}, whose denominator has 100,199 digits, displays
   * and parses back to itself; a value holding a numeral of more than {@code Lexer.MAX_DIGITS}
   * digits, the statement's or a definition's, is "number too large to display", while an
   * intermediate value may be that large.
   */
  @Test
  void everyNumberShownReadsBack() {
    Expr third = value("1/3^210000");
    String shown = mathcanvas.engine.print.UnicodePrinter.INSTANCE.display(third);
    assertThat(shown.length()).isGreaterThan(100_000);
    assertThat(Run.canonical(shown)).isEqualTo(third);
    String eleven = "a := 3^200000; a a a a a a a a a a a";
    assertThat(error(eleven)).isEqualTo("number too large to display");
    assertThat(error("a := 3^200000; b := a a a a a a a a a a a; b/a"))
        .isEqualTo("number too large to display");
    assertThat(value(eleven + "/a")).isEqualTo(value("a := 3^200000; a a a a a a a a a a"));
    assertThat(error("a := 3^200000; 1/(a a a a a a a a a a a)"))
        .isEqualTo("number too large to display");
    // A fraction's terms are capped at the fold limit's digits, since reading it back costs their
    // gcd: three factors of 1/3^200000 (a denominator of 286,272 digits) show and read back, four
    // (381,697 digits) are past the cap.
    Expr cube = value("b := 1/3^200000; b b b");
    assertThat(Run.canonical(mathcanvas.engine.print.UnicodePrinter.INSTANCE.display(cube)))
        .isEqualTo(cube);
    assertThat(error("b := 1/3^200000; b b b b")).isEqualTo("number too large to display");
  }

  /** {@code f(f(…f(x)…))}, {@code n} calls deep. */
  static String nested(String f, int n, String inner) {
    return (f + "(").repeat(n) + inner + ")".repeat(n);
  }

  /** The deepest nesting of {@code f(…)} the parser accepts. */
  static String deepestParse(String f) {
    for (int n = Parser.MAX_NESTING + 2; ; n--) {
      String text = nested(f, n, "x");
      try {
        Parser.parseExpression(text, Environment.EMPTY.names());
        return text;
      } catch (ParseException tooDeep) {
        // one level less
      }
    }
  }

  @Test
  void theDeepestParseEvaluates() {
    String text = deepestParse("sin");
    Expr v = value(text);
    assertThat(Evaluator.depth(v)).isLessThanOrEqualTo(Parser.MAX_NESTING);
    assertThat(value(deepestParse("abs"))).isNotNull();
  }

  @Test
  void definitionsNestedTooDeeplyAreAnError() {
    StringBuilder ok = new StringBuilder("a0 := x");
    for (int k = 1; k < 150; k++) {
      ok.append("; a").append(k).append(" := a").append(k - 1);
    }
    assertThat(value(ok + "; a149")).isEqualTo(Run.canonical("x"));
    StringBuilder tooMany = new StringBuilder("a0 := x");
    for (int k = 1; k <= Evaluator.MAX_EXPANSIONS + 10; k++) {
      tooMany.append("; a").append(k).append(" := a").append(k - 1);
    }
    assertThat(error(tooMany + "; a" + (Evaluator.MAX_EXPANSIONS + 10)))
        .isEqualTo("definitions nested too deeply");
  }

  @Test
  void tooManyEvaluationStepsAreAnError() {
    // Each body is eight raw sums deep and canonicalises to one level, so the result stays shallow
    // while the evaluation steps add up.
    String wrap = "(((((((( a%d + 1) + 1) + 1) + 1) + 1) + 1) + 1) + 1)";
    StringBuilder fine = new StringBuilder("a0 := x");
    for (int k = 1; k <= 90; k++) {
      fine.append("; a").append(k).append(" := ").append(wrap.formatted(k - 1));
    }
    assertThat(value(fine + "; a90")).isEqualTo(Run.canonical("x + 720"));
    StringBuilder deep = new StringBuilder("a0 := x");
    for (int k = 1; k <= 150; k++) {
      deep.append("; a").append(k).append(" := ").append(wrap.formatted(k - 1));
    }
    assertThat(error(deep + "; a150")).isEqualTo("expression nested too deeply");
  }

  @Test
  void aValueDeeperThanTheParserAllowsIsAnError() {
    String a = nested("sin", 150, "x");
    String b = nested("cos", 100, "a");
    assertThat(error("a := " + a + "; b := " + b + "; b"))
        .isEqualTo("expression nested too deeply");
  }

  @Test
  void aLongSumEvaluatesInOneLevel() {
    StringBuilder text = new StringBuilder("x0");
    for (int k = 1; k < 10_000; k++) {
      text.append(" + x").append(k);
    }
    long start = System.nanoTime();
    Expr v = value("x5 := 1; " + text);
    assertThat(Evaluator.depth(v)).isLessThanOrEqualTo(2);
    assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(5_000);
  }

  @Test
  void theRewriteCapGrowsWithTheStatement() {
    // Several rewrites a term, more than the base cap of 10,000 in all, within the statement's cap
    // of 10,000 plus 20 per node.
    StringBuilder text = new StringBuilder("assume(x > 0, y > 0); steps(0");
    for (int k = 2; k < 5_002; k++) {
      text.append(" + 1/sqrt(").append(k).append("^3) + log(").append(2 * k).append(" x y)");
    }
    Outcome o = Run.last(text.append(")").toString());
    assertThat(o).isInstanceOf(Outcome.Value.class);
    Outcome.Value v = (Outcome.Value) o;
    assertThat(v.trace().rewrites()).hasSizeGreaterThan(Evaluator.REWRITE_CAP);
    assertThat(v.value().toString()).doesNotContain("Apply[log; Product");
  }

  /** {@code sum(sum(…sum(a, k, 1, 2)…, k, 1, 2), k, 1, 2)}, {@code n} deep, with {@code a := k}. */
  static Expr nestedSums(int n) {
    String text = "sum(".repeat(n) + "a" + ", k, 1, 2)".repeat(n);
    return mathcanvas.engine.parse.Parser.parseExpression(text, Environment.EMPTY.names());
  }

  @Test
  void nestedSameNameBindersAreRenamedOnceEach() {
    Environment env =
        Environment.EMPTY.with("a", new Definition.Value(mathcanvas.engine.expr.Symbol.of("k")));
    for (int n : new int[] {30, 120}) {
      Expr raw = nestedSums(n);
      long start = System.nanoTime();
      Expr v = Evaluator.evaluate(raw, env, false).value();
      long ms = (System.nanoTime() - start) / 1_000_000;
      assertThat(ms).as("%d levels took %d ms", n, ms).isLessThan(1_000);
      String shown = mathcanvas.engine.print.UnicodePrinter.INSTANCE.display(v);
      assertThat(shown).startsWith("∑[k₁ = 1, 2] ∑[k₂ = 1, 2]").endsWith(" k");
    }
  }

  @Test
  void identityCallsOnALargeValueAreCheap() {
    StringBuilder script = new StringBuilder("h0 := 1");
    for (int k = 1; k <= 18; k++) {
      script.append("; h").append(k).append(" := [h").append(k - 1).append(", h");
      script.append(k - 1).append("]");
    }
    script.append("; big := [h18, h17, h16]; id(t) := t");
    Session s = new Session();
    s.run(mathcanvas.engine.parse.Parser.parseScript(script.toString(), s.names()));
    String calls = "id(".repeat(150) + "big" + ")".repeat(150);
    Expr bare = mathcanvas.engine.parse.Parser.parseExpression("big", s.names());
    Expr nested = mathcanvas.engine.parse.Parser.parseExpression(calls, s.names());
    // A whole-run budget (docs/reference/engine.md, Timing budgets): a quadratic cost of the calls
    // would take minutes.
    Expr direct = Evaluator.evaluate(bare, s.environment(), false).value();
    long start = System.nanoTime();
    Expr passed = Evaluator.evaluate(nested, s.environment(), false).value();
    long passedMs = (System.nanoTime() - start) / 1_000_000;
    assertThat(passed).isEqualTo(direct);
    assertThat(passedMs).as("150 calls on the value took %d ms", passedMs).isLessThan(5_000);
  }
}
