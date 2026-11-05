package mathcanvas.engine.simplify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.RewriteLimitException;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;
import mathcanvas.engine.rule.Trace;
import mathcanvas.engine.testing.Contexts;
import org.junit.jupiter.api.Test;

class SimplifierTest {

  static Expr parse(String text) {
    return Parser.parseExpression(text, Names.NONE);
  }

  static Expr call(String head, Expr arg) {
    return Apply.of(Symbol.of(head), arg);
  }

  /** {@code from(x_) -> to(x_)}. */
  static Rule rule(String name, String from, String to) {
    Symbol x = Symbol.of("x_");
    return Rule.of(name, Pattern.of(call(from, x)), call(to, x));
  }

  @Test
  void aValueNoRuleAppliesToIsCanonical() {
    Rewriter.Result r = Simplifier.simplify(parse("x + x + 1/2 + 1/3"), Context.DEFAULT);
    assertThat(r.expr()).isEqualTo(Canonical.of(parse("2x + 5/6")));
    assertThat(r.trace()).isEqualTo(Trace.NONE);
  }

  @Test
  void anEmptyTraceWhenTracing() {
    Rewriter.Result r = Simplifier.simplify(parse("x + x"), Context.DEFAULT.withTrace(true));
    assertThat(r.trace().rewrites()).isEmpty();
    assertThat(r.trace().initial()).isEqualTo(r.expr());
    assertThat(r.trace().result()).isEqualTo(r.expr());
  }

  @Test
  void theSetsShareOneFixpointAndKeepTheirNames() {
    RuleSet first = new RuleSet("first", List.of(rule("f-to-g", "f", "g")));
    RuleSet second = new RuleSet("second", List.of(rule("g-to-h", "g", "h")));
    Expr e = call("f", Num.ONE);
    Rewriter.Result r =
        Simplifier.simplify(e, List.of(second, first), Context.DEFAULT.withTrace(true));
    assertThat(r.expr()).isEqualTo(call("h", Num.ONE));
    assertThat(r.trace().rewrites())
        .extracting(w -> w.ruleSet() + "." + w.rule())
        .containsExactly("first.f-to-g", "second.g-to-h");
    assertThat(r.trace().roots().getLast()).isEqualTo(r.expr());
  }

  @Test
  void aLoopAcrossSetsHitsTheCap() {
    RuleSet there = new RuleSet("there", List.of(rule("f-to-g", "f", "g")));
    RuleSet back = new RuleSet("back", List.of(rule("g-to-f", "g", "f")));
    Expr e = call("f", Num.ONE);
    assertThatThrownBy(
            () ->
                Simplifier.simplify(
                    e, List.of(there, back), Contexts.withRewriteCap(Context.DEFAULT, 50)))
        .isInstanceOf(RewriteLimitException.class);
  }

  @Test
  void ruleNamesAreUniqueAcrossTheList() {
    RuleSet a = new RuleSet("a", List.of(rule("same", "f", "g")));
    RuleSet b = new RuleSet("b", List.of(rule("same", "g", "h")));
    assertThatThrownBy(() -> Simplifier.simplify(parse("x"), List.of(a, b), Context.DEFAULT))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
