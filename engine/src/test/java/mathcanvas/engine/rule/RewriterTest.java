package mathcanvas.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Lambda;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.testing.Contexts;
import org.junit.jupiter.api.Test;

class RewriterTest {

  static final Symbol A = Symbol.of("a");
  static final Symbol X = Symbol.of("x");
  static final Symbol Y = Symbol.of("y");
  static final Symbol XV = Symbol.of("x_");

  static Apply call(String head, Expr... args) {
    return Apply.of(Symbol.of(head), args);
  }

  static Rule rename(String name, String from, String to) {
    return Rule.of(name, Pattern.of(call(from, XV)), call(to, XV));
  }

  static final Context TRACED = Context.DEFAULT.withTrace(true);

  static Rewriter.Result run(Expr e, Context ctx, Rule... rules) {
    return Rewriter.rewrite(e, new RuleSet("fixture", List.of(rules)), ctx);
  }

  /** The subexpression at {@code path} of {@code e}. */
  static Expr at(Expr e, Path path) {
    for (int i : path.indices()) {
      e = Operands.of(e).get(i);
    }
    return e;
  }

  @Test
  void innermostFirstToAFixpoint() {
    Rewriter.Result r =
        run(
            call("f", call("f", A)),
            TRACED,
            rename("f-to-g", "f", "g"),
            rename("g-to-h", "g", "h"));
    assertThat(r.expr()).isEqualTo(call("h", call("h", A)));
    List<Rewrite> rw = r.trace().rewrites();
    assertThat(rw)
        .extracting(Rewrite::rule)
        .containsExactly("f-to-g", "g-to-h", "f-to-g", "g-to-h");
    assertThat(rw)
        .extracting(w -> w.path().indices())
        .containsExactly(List.of(0), List.of(0), List.of(), List.of());
    assertThat(rw.get(0).before()).isEqualTo(call("f", A));
    assertThat(rw.get(0).after()).isEqualTo(call("g", A));
    assertThat(rw.get(2).before()).isEqualTo(call("f", call("h", A)));
    assertThat(r.trace().roots())
        .containsExactly(
            call("f", call("g", A)),
            call("f", call("h", A)),
            call("g", call("h", A)),
            call("h", call("h", A)));
    assertThat(r.trace().initial()).isEqualTo(call("f", call("f", A)));
    assertThat(r.trace().result()).isEqualTo(r.expr());
  }

  @Test
  void aRewriteRestartsAtItsNodeOperandsFirst() {
    Rule wrap = Rule.of("p-to-q", Pattern.of(call("p", XV)), call("q", call("f", XV)));
    Rewriter.Result r = run(call("p", A), TRACED, wrap, rename("f-to-g", "f", "g"));
    assertThat(r.expr()).isEqualTo(call("q", call("g", A)));
    assertThat(r.trace().rewrites())
        .extracting(w -> w.rule() + " " + w.path().indices())
        .containsExactly("p-to-q []", "f-to-g [0]");
  }

  @Test
  void rulesAreTriedInOrderAndAnUnchangedOrEmptyReplacementDoesNotApply() {
    Rule same = Rule.of("same", Pattern.of(XV), XV);
    Rule none = Rule.computed("none", Pattern.of(XV), m -> Optional.empty());
    Rewriter.Result r =
        run(
            call("f", A),
            TRACED,
            same,
            none,
            rename("f-to-g", "f", "g"),
            rename("f-to-h", "f", "h"));
    assertThat(r.expr()).isEqualTo(call("g", A));
    assertThat(r.trace().rewrites()).extracting(Rewrite::rule).containsExactly("f-to-g");
  }

  @Test
  void computedReplacementsSeeTheMatch() {
    Rule twice =
        Rule.computed(
            "double-number",
            Pattern.of(call("d", XV)).where("x_", e -> e instanceof Num),
            m -> Optional.of(Product.of(Num.of(2), m.bindings().get("x_"))));
    assertThat(run(call("d", Num.of(21)), Context.DEFAULT, twice).expr()).isEqualTo(Num.of(42));
    assertThat(run(call("d", X), Context.DEFAULT, twice).expr()).isEqualTo(call("d", X));
  }

  @Test
  void boundPositionsAreNotVisited() {
    Rule xToY = Rule.of("x-to-y", Pattern.of(X), Y);
    Expr lambda = Lambda.of(X, call("f", X));
    assertThat(run(lambda, Context.DEFAULT, xToY).expr()).isEqualTo(Lambda.of(X, call("f", Y)));
    Expr derivative = Derivative.of(call("f", X), X, 1);
    assertThat(run(derivative, Context.DEFAULT, xToY).expr())
        .isEqualTo(Derivative.of(call("f", Y), X, 1));
    Expr relation = Relation.of(Relation.Op.EQ, X, Num.ONE);
    assertThat(run(relation, Context.DEFAULT, xToY).expr())
        .isEqualTo(Relation.of(Relation.Op.EQ, Y, Num.ONE));
  }

  @Test
  void aParentIsCanonicalisedAfterItsOperandsAndTheTraceRecordsIt() {
    Rule unwrap = Rule.of("unwrap", Pattern.of(call("f", XV)), XV);
    Expr e = Sum.of(call("f", A), A);
    Rewriter.Result r = run(e, TRACED, unwrap);
    assertThat(r.expr()).isEqualTo(Product.of(Num.of(2), A));
    assertThat(r.trace().rewrites()).hasSize(1);
    assertThat(r.trace().rewrites().getFirst().path().indices()).containsExactly(1);
    assertThat(r.trace().normalisations())
        .containsExactly(new Trace.Normalisation(1, Path.ROOT, Product.of(Num.of(2), A)));
    Expr root = r.trace().root(0);
    assertThat(root).isEqualTo(Sum.of(A, A));
    assertThat(Canonical.of(root)).isEqualTo(r.expr());
    // The input is canonicalised first, untraced.
    assertThat(r.trace().initial()).isEqualTo(Canonical.of(e));
  }

  @Test
  void conditionsConsultTheContextsAssumptions() {
    Rule positive =
        Rule.of(
            "abs-of-positive",
            Pattern.of(call("abs", XV))
                .where(List.of("x_"), (b, a) -> a.isPositive(b.get("x_")) == Tri.TRUE),
            XV);
    assertThat(run(call("abs", X), Context.DEFAULT, positive).expr()).isEqualTo(call("abs", X));
    Context xPositive =
        Context.DEFAULT.withAssumptions(
            Assumptions.EMPTY.with(Relation.of(Relation.Op.GT, X, Num.ZERO)));
    assertThat(run(call("abs", X), xPositive, positive).expr()).isEqualTo(X);
  }

  @Test
  void factsAboutABoundSymbolDoNotHoldInsideItsBinder() {
    Rule sqrtSquare =
        Rule.of(
            "sqrt-square-nonnegative",
            Pattern.of(Exprs.sqrt(Power.of(XV, Num.of(2))))
                .where(List.of("x_"), (b, a) -> a.isNonNegative(b.get("x_")) == Tri.TRUE),
            XV);
    Context xNonNegative =
        Context.DEFAULT.withAssumptions(
            Assumptions.EMPTY.with(Relation.of(Relation.Op.GE, X, Num.ZERO)));
    Expr root = Exprs.sqrt(Power.of(X, Num.of(2)));
    assertThat(run(root, xNonNegative, sqrtSquare).expr()).isEqualTo(X);
    // ∫₋₁¹ √(x²) dx, Σ_{x=-3}^{3} √(x²) and x -> √(x²) are about their own x.
    Expr integral = Integral.definite(root, X, Num.of(-1), Num.ONE);
    assertThat(run(integral, xNonNegative, sqrtSquare).expr()).isEqualTo(Canonical.of(integral));
    Expr sum = BigSum.of(root, X, Num.of(-3), Num.of(3));
    assertThat(run(sum, xNonNegative, sqrtSquare).expr()).isEqualTo(Canonical.of(sum));
    Expr lambda = Lambda.of(X, root);
    assertThat(run(lambda, xNonNegative, sqrtSquare).expr()).isEqualTo(Canonical.of(lambda));
    Expr derivative = Derivative.of(root, X, 1);
    assertThat(run(derivative, xNonNegative, sqrtSquare).expr())
        .isEqualTo(Canonical.of(derivative));
    // The bounds are outside the binder's scope, and a binder of another symbol keeps the facts.
    assertThat(run(BigSum.of(root, X, Num.ONE, root), xNonNegative, sqrtSquare).expr())
        .isEqualTo(Canonical.of(BigSum.of(root, X, Num.ONE, X)));
    Symbol k = Symbol.of("k");
    assertThat(run(BigSum.of(root, k, Num.of(-3), Num.of(3)), xNonNegative, sqrtSquare).expr())
        .isEqualTo(BigSum.of(X, k, Num.of(-3), Num.of(3)));
  }

  @Test
  void theCapStopsALoopNamingTheRuleAndPosition() {
    Context capped = Contexts.withRewriteCap(Context.DEFAULT, 10);
    assertThatThrownBy(
            () ->
                run(
                    call("k", call("f", A)),
                    capped,
                    rename("f-to-g", "f", "g"),
                    rename("g-to-f", "g", "f")))
        .isInstanceOfSatisfying(
            RewriteLimitException.class,
            ex -> {
              assertThat(ex.ruleSet()).isEqualTo("fixture");
              assertThat(ex.rule()).isIn("f-to-g", "g-to-f");
              assertThat(ex.path().indices()).containsExactly(0);
              assertThat(ex.getMessage()).contains("more than 10 rewrites");
            });
    // Exactly the cap is allowed.
    Rewriter.Result r =
        run(
            call("f", call("f", A)),
            Contexts.withRewriteCap(TRACED, 4),
            rename("f-to-g", "f", "g"),
            rename("g-to-h", "g", "h"));
    assertThat(r.trace().rewrites()).hasSize(4);
  }

  @Test
  void withoutTracingNothingIsRecorded() {
    Rewriter.Result r = run(call("f", call("f", A)), Context.DEFAULT, rename("f-to-g", "f", "g"));
    assertThat(r.expr()).isEqualTo(call("g", call("g", A)));
    assertThat(r.trace()).isSameAs(Trace.NONE);
    assertThat(r.trace().rewrites()).isEmpty();
    assertThat(r.trace().roots()).isEmpty();
  }

  @Test
  void rulesAndRuleSetsCheckTheirNames() {
    assertThatThrownBy(() -> Rule.of("Not Kebab", Pattern.of(XV), XV))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Rule.of("unbound", Pattern.of(XV), Symbol.of("y_")))
        .isInstanceOf(IllegalArgumentException.class);
    Rule r = rename("f-to-g", "f", "g");
    assertThatThrownBy(() -> new RuleSet("fixture", List.of(r, r)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RuleSet("Fixture", List.of(r)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Contexts.withRewriteCap(Context.DEFAULT, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void pathsAddressTheirRewritesInTheReplayedRoots() {
    Rule unwrap = Rule.of("unwrap", Pattern.of(call("f", XV)), Sum.of(XV, Num.ONE));
    Expr e = Product.of(call("f", call("f", X)), Sum.of(call("f", Y), call("f", X)));
    Rewriter.Result r = run(e, TRACED, unwrap);
    List<Expr> roots = r.trace().roots();
    for (int i = 0; i < roots.size(); i++) {
      Rewrite w = r.trace().rewrites().get(i);
      assertThat(at(roots.get(i), w.path())).isEqualTo(w.after());
    }
    assertThat(Canonical.of(roots.getLast())).isEqualTo(r.expr());
  }
}
