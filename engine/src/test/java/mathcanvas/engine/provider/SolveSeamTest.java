package mathcanvas.engine.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.simplify.Simplifier;
import mathcanvas.engine.solve.Solver;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

/**
 * The solve operation is a real seam: a deliberately trivial provider that answers linear equations
 * only, registered ahead of the default, receives them, the rest falls through, and the two
 * providers' answers are the same once normalised and simplified, as a statement's value is.
 */
class SolveSeamTest {

  /** Answers one equation of degree 1 with rational coefficients: {@code -b/a}, raw. */
  record LinearOnly(AtomicInteger calls) implements Provider<Solve, SetExpr> {

    @Override
    public String name() {
      return "linear-only";
    }

    @Override
    public Optional<SetExpr> apply(Solve input, Context ctx, Steps steps) {
      if (input.equations().size() != 1 || input.vars().size() != 1) {
        return Optional.empty();
      }
      Relation eq = (Relation) input.equations().getFirst();
      Symbol x = input.vars().getFirst();
      Polynomial p;
      try {
        p = Polynomial.of(Canonical.of(Exprs.sub(eq.left(), eq.right())));
      } catch (IllegalArgumentException refused) {
        return Optional.empty();
      }
      if (!p.variables().equals(List.of(x)) || p.degree(x) != 1) {
        return Optional.empty();
      }
      calls.incrementAndGet();
      Expr root =
          Product.of(Num.of(p.coefficient(0).negate()), Exprs.inv(Num.of(p.coefficient(1))));
      return Optional.of(new SetExpr(List.of(root)));
    }
  }

  private static Rewriter.Result solve(String eq, Context ctx) {
    return Solver.solve(Canonical.of(Parser.parseExpression(eq, Names.NONE)), Symbol.of("x"), ctx);
  }

  private static Context withLinearOnly(AtomicInteger calls) {
    return Context.DEFAULT
        .withProviders(Providers.defaults().register(Solve.class, new LinearOnly(calls), 0))
        .withTrace(true);
  }

  @Test
  void theEngineRoutesToTheProviderRegisteredFirst() {
    AtomicInteger calls = new AtomicInteger();
    Context ctx = withLinearOnly(calls);
    assertThat(ctx.providers().names(Solve.class)).containsExactly("linear-only", "default");

    Rewriter.Result r = solve("2x + 3 = 7", ctx);
    assertThat(calls.get()).isEqualTo(1);
    assertThat(r.expr()).isEqualTo(Canonical.of(Parser.parseExpression("{2}", Names.NONE)));
    assertThat(r.trace().rewrites()).hasSize(1);
    Rewrite only = r.trace().rewrites().getFirst();
    assertThat(only.ruleSet()).isEqualTo(Solver.PROVIDER);
    assertThat(only.rule()).isEqualTo("linear-only");
    TraceChecks.assertChain(r);

    Rewriter.Result s = solve("x^2 = 4", ctx);
    assertThat(calls.get()).isEqualTo(1);
    assertThat(s.expr()).isEqualTo(Canonical.of(Parser.parseExpression("{-2, 2}", Names.NONE)));
    assertThat(s.trace().rewrites()).extracting(Rewrite::ruleSet).containsOnly(Solver.RULE_SET);
    TraceChecks.assertChain(s);
  }

  @Test
  void whenEveryProviderDeclinesTheCallStays() {
    Context ctx =
        Context.DEFAULT
            .withProviders(
                Providers.defaults()
                    .unregister(Solve.class, "default")
                    .register(Solve.class, new LinearOnly(new AtomicInteger())))
            .withTrace(true);
    Rewriter.Result r = solve("x^2 = 4", ctx);
    assertThat(r.expr())
        .isEqualTo(Canonical.of(Parser.parseExpression("solve(x^2 = 4, x)", Names.NONE)));
    assertThat(r.trace().rewrites()).extracting(Rewrite::rule).containsExactly("unsolved");
    TraceChecks.assertChain(r);
  }

  @Property(tries = 500)
  void bothProvidersGiveTheSameValue(
      @ForAll @IntRange(min = -20, max = 20) int a,
      @ForAll @IntRange(min = -20, max = 20) int b,
      @ForAll @IntRange(min = -20, max = 20) int c,
      @ForAll @IntRange(min = 1, max = 9) int d) {
    if (a == 0) {
      return;
    }
    Expr eq =
        Canonical.of(
            new Relation(
                Relation.Op.EQ,
                Sum.of(Product.of(Num.of(a), Symbol.of("x")), Num.of(b)),
                Product.of(Num.of(c), Exprs.inv(Num.of(d)))));
    AtomicInteger calls = new AtomicInteger();
    Expr trivial =
        Simplifier.simplify(
                Solver.solve(eq, Symbol.of("x"), withLinearOnly(calls)).expr(), Context.DEFAULT)
            .expr();
    Expr own =
        Simplifier.simplify(
                Solver.solve(eq, Symbol.of("x"), Context.DEFAULT).expr(), Context.DEFAULT)
            .expr();
    assertThat(calls.get()).isEqualTo(1);
    assertThat(trivial).isEqualTo(own);
  }
}
