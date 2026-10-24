package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.parse.Position;
import mathcanvas.engine.parse.Script;
import mathcanvas.engine.parse.Statement;
import mathcanvas.engine.rule.Trace;
import org.junit.jupiter.api.Test;

class SessionTest {

  static List<Outcome> run(Session s, String script) {
    return s.run(Parser.parseScript(script, s.names()));
  }

  @Test
  void definitionsEchoAndBind() {
    Session s = new Session();
    List<Outcome> out = run(s, "f(x) := x^2 + 1\nf(2)");
    assertThat(out.get(0)).isInstanceOf(Outcome.Defined.class);
    Statement.Definition echo = ((Outcome.Defined) out.get(0)).echo();
    assertThat(echo.name()).isEqualTo(Symbol.of("f"));
    assertThat(out.get(1)).isEqualTo(new Outcome.Value(Num.of(5), Trace.NONE, false));
    assertThat(s.environment().isFunction("f")).isTrue();
    assertThat(s.names().isUserFunction("f")).isTrue();
  }

  @Test
  void laterStatementsParseWithEarlierDefinitions() {
    Session s = new Session();
    run(s, "g(t) := 2t");
    List<Outcome> out = run(s, "g(4)");
    assertThat(((Outcome.Value) out.getFirst()).value()).isEqualTo(Num.of(8));
  }

  @Test
  void quietStatementsAreMarked() {
    Session s = new Session();
    List<Outcome> out = run(s, "1 + 1; 2 + 2");
    assertThat(((Outcome.Value) out.get(0)).quiet()).isTrue();
    assertThat(((Outcome.Value) out.get(1)).quiet()).isFalse();
  }

  @Test
  void aFailureLeavesTheEnvironmentAndTheRunGoesOn() {
    Session s = new Session();
    List<Outcome> out = run(s, "a := b\nb := a\na\nc := 1\nc");
    assertThat(out.get(2)).isInstanceOf(Outcome.Failed.class);
    assertThat(((Outcome.Failed) out.get(2)).error().getMessage())
        .isEqualTo("a is defined in terms of itself");
    assertThat(((Outcome.Value) out.get(4)).value()).isEqualTo(Num.ONE);
  }

  @Test
  void theFailingNodeHasItsPositionInTheScript() {
    Session s = new Session();
    Script script = Parser.parseScript("f(x) := x\n  1 + f(1, 2)", s.names());
    List<Outcome> out = s.run(script);
    EvaluationException e = ((Outcome.Failed) out.get(1)).error();
    assertThat(e.getMessage()).isEqualTo("f takes 1 argument");
    assertThat(script.positionOf(e.node())).contains(new Position(2, 7, 16));
  }

  @Test
  void tracingTracesEveryStatement() {
    Session s = new Session();
    assertThat(((Outcome.Value) run(s, "1 + 1").getFirst()).trace()).isSameAs(Trace.NONE);
    s.setTracing(true);
    assertThat(((Outcome.Value) run(s, "1 + 1").getFirst()).trace()).isNotSameAs(Trace.NONE);
    s.setTracing(false);
    assertThat(((Outcome.Value) run(s, "1 + 1").getFirst()).trace()).isSameAs(Trace.NONE);
  }

  @Test
  void domainsCarryNoArithmetic() {
    for (String text : List.of("x + ℝ + 1", "2ℤ", "ℚ^2", "2^ℂ")) {
      List<Outcome> out = run(new Session(), text);
      assertThat(out.getFirst()).as(text).isInstanceOf(Outcome.Failed.class);
      assertThat(((Outcome.Failed) out.getFirst()).error().getMessage())
          .as(text)
          .isEqualTo("domains carry no arithmetic");
    }
    assertThat(((Outcome.Value) run(new Session(), "2 ∈ ℤ").getFirst()).value())
        .isEqualTo(Bool.TRUE);
  }

  @Test
  void aCollectionIsNeverAnExponent() {
    for (String[] c :
        new String[][] {
          {"π^[1, 2]", "a vector cannot be an exponent"},
          {"x^[1, 2] x", "a vector cannot be an exponent"},
          {"2^[[1, 2], [3, 4]]", "a matrix cannot be an exponent"},
          {"[[1, 2], [3, 4]]^[[1, 0], [0, 1]]", "a matrix cannot be an exponent"},
          {"π^{1, 2}", "sets carry no arithmetic"},
          {"2^[x = 1]", "lists carry no arithmetic"}
        }) {
      List<Outcome> out = run(new Session(), c[0]);
      assertThat(out.getFirst()).as(c[0]).isInstanceOf(Outcome.Failed.class);
      assertThat(((Outcome.Failed) out.getFirst()).error().getMessage()).as(c[0]).isEqualTo(c[1]);
    }
  }

  @Test
  void assumeRecordsAboutTheSymbol() {
    Session s = new Session();
    List<Outcome> out = run(s, "x := 3\nassume(x > 0, x <= 1)\nassumptions()");
    Expr recorded = ((Outcome.Value) out.get(1)).value();
    assertThat(recorded).isEqualTo(Run.canonical("{x > 0, x <= 1}"));
    assertThat(((Outcome.Value) out.get(2)).value()).isEqualTo(recorded);
    assertThat(s.environment().assumptions().isPositive(Symbol.of("x")))
        .isEqualTo(mathcanvas.engine.assume.Tri.TRUE);
  }

  @Test
  void assumeEvaluatesTheBound() {
    Session s = new Session();
    List<Outcome> out = run(s, "a := 2\nassume(x > a)");
    assertThat(((Outcome.Value) out.get(1)).value()).isEqualTo(Run.canonical("{x > 2}"));
  }

  @Test
  void assumeHoldsTheLeftSide() {
    Session s = new Session();
    List<Outcome> out = run(s, "a := 5\nassume(x < a)\nx := 3\nassume(0 < x)");
    assertThat(((Outcome.Value) out.get(1)).value()).isEqualTo(Run.canonical("{x < 5}"));
    assertThat(((Outcome.Failed) out.get(3)).error().getMessage())
        .isEqualTo("not an assumption about a symbol: 0 < 3");
  }

  @Test
  void nothingButItsOwnOutcomesLeavesASession() {
    Session s = new Session();
    List<Outcome> out =
        run(
            s,
            "1e999999999 * 1e999999999 * 1e999999999\nassume(x < 1e999999999 * 1e999999999 * 1e999999999)");
    assertThat(out).allMatch(o -> o instanceof Outcome.Failed);
  }

  @Test
  void aRejectedAssumptionIsAnError() {
    Session s = new Session();
    List<Outcome> out = run(s, "assume(x > 1)\nassume(x < 0)\nassume(x = 2)\nassume(2)");
    assertThat(out.get(1)).isInstanceOf(Outcome.Failed.class);
    assertThat(out.get(2)).isInstanceOf(Outcome.Failed.class);
    assertThat(((Outcome.Failed) out.get(3)).error().getMessage())
        .isEqualTo("assume takes relations");
    assertThat(s.environment().assumptions().relations()).isEqualTo(Run.canonical("{x > 1}"));
  }

  @Test
  void clearUnbinds() {
    Session s = new Session();
    List<Outcome> out = run(s, "a := 5\nclear(a)\na\nclear(2)");
    assertThat(((Outcome.Value) out.get(1)).value()).isEqualTo(Symbol.of("a"));
    assertThat(((Outcome.Value) out.get(2)).value()).isEqualTo(Symbol.of("a"));
    assertThat(((Outcome.Failed) out.get(3)).error().getMessage()).isEqualTo("clear needs a name");
  }

  @Test
  void settingsChangeTheEvaluation() {
    Session s = new Session();
    s.setPrecision(5);
    assertThat(((Outcome.Value) run(s, "numeric(1/3)").getFirst()).value())
        .isEqualTo(new Num(mathcanvas.engine.number.Real.of("0.33333")));
    s.setAngleMode(AngleMode.DEGREES);
    assertThat(((Outcome.Value) run(s, "sin(30)").getFirst()).value())
        .isEqualTo(Run.canonical("1/2"));
    assertThat(s.environment().settings()).isEqualTo(new Settings(5, AngleMode.DEGREES));
  }

  @Test
  void stepsCarryTheTrace() {
    Session s = new Session();
    Outcome.Value v = (Outcome.Value) run(s, "steps(x + x)").getFirst();
    assertThat(v.value()).isEqualTo(Run.canonical("2x"));
    assertThat(v.trace().result()).isEqualTo(v.value());
  }

  @Test
  void theExamplesOfTheLanguageReference() {
    Session s = new Session();
    List<Outcome> out =
        run(s, "x := 3\nsolve(x^2 = 4, x)\na := 5\nsubs(y^2 + a, a = 2)\nclear(x)\nx");
    assertThat(((Outcome.Value) out.get(1)).value()).isEqualTo(Run.canonical("solve(x^2 = 4, x)"));
    assertThat(((Outcome.Value) out.get(3)).value()).isEqualTo(Run.canonical("y^2 + 2"));
    assertThat(((Outcome.Value) out.get(5)).value()).isEqualTo(Symbol.of("x"));
  }

  @Test
  void noProbeIsAnInternalError() {
    List<String> probes =
        List.of(
            "1/0",
            "0^0",
            "0^-1",
            "inf - inf",
            "undefined^0",
            "(-8)^(1/3)",
            "sqrt(-4)",
            "sin(1e10000)",
            "exp(1e20)",
            "log(0.0)",
            "atanh(1.0)",
            "acoth(1.0)",
            "asech(0.0)",
            "acsch(0)",
            "acosh(-2.0)",
            "asech(2.0)",
            "mod(1e999999, 7.0)",
            "factorial(1e400)",
            "binomial(1e400, 2)",
            "root(-8.0, 3)",
            "root(2, 0)",
            "cbrt(-8.0)",
            "nth([], 1)",
            "length([])",
            "[[1, 2], [3, 4]]^0",
            "[[1, 2], [3, 4]]^1e3",
            "[1, 2]^0.5",
            "{1, 2}^2",
            "true + false",
            "2 ∈ x",
            "i ∈ ℤ",
            "numeric(i^i, 5)",
            "exact(1e99999)",
            "exact(1e-99999)",
            "numeric([[1, 2], [3, 4]]^2)",
            "diff(x, x, 2.5)",
            "limit(x, x, 0, up)",
            "sum(k, k, 1, inf)",
            "subs(x, x = x)",
            "assume(x)",
            "x -> x + 1",
            "1e999999999 * 1e999999999 * 1e999999999");
    Session s = new Session();
    for (String probe : probes) {
      for (Outcome o : run(s, probe)) {
        if (o instanceof Outcome.Failed f) {
          assertThat(f.error().getMessage()).as(probe).doesNotStartWith("internal error");
        }
      }
    }
  }
}
