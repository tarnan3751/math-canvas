package mathcanvas.engine.solve;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.provider.Providers;
import mathcanvas.engine.provider.Solve;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;
import mathcanvas.engine.solve.Support.Declined;

/**
 * The language's {@code solve}: one equation in one unknown, or a system of equations in several,
 * solved exactly where a method applies and numerically otherwise.
 *
 * <p>The trace is a chain of steps in the rule set {@link #RULE_SET}, each at the root and each
 * replacing the whole state: the equations, then the forms each method passes through, then the set
 * of solutions (the step {@code roots}). When no method applies the result is the call itself,
 * unevaluated, and the last step, from the state reached to the call, names the reason.
 *
 * <p>The methods run through the provider interface as the operation {@link Solve}, whose default
 * provider is {@link SolveProvider}; a provider's result is normalised to its canonical form.
 */
public final class Solver {

  /** The rule set of the steps, and the narration bundle's key prefix. */
  public static final String RULE_SET = "solve";

  /** The rule set of the step that names a provider which recorded no steps of its own. */
  public static final String PROVIDER = "provider";

  private static final Symbol SOLVE = Symbol.of("solve");

  private Solver() {}

  /**
   * Solves equations.
   *
   * @param equations an equation, a truth value, or a set of them, evaluated and canonical
   * @param variables the unknown, or a list or vector of distinct unknowns
   * @param ctx the context: assumptions (without facts about the unknowns), precision, trace
   * @return a set of solutions (values for one unknown, lists of equations {@code x = v} for
   *     several), or the call unevaluated, with the trace
   * @throws IllegalArgumentException with the language's message, if the arguments have the wrong
   *     kind
   */
  public static Rewriter.Result solve(Expr equations, Expr variables, Context ctx) {
    List<Symbol> vars = variables(variables);
    List<Expr> items = equations instanceof SetExpr s ? s.items() : List.of(equations);
    List<Expr> eqs = new ArrayList<>();
    boolean contradiction = false;
    boolean inequality = false;
    for (Expr item : items) {
      Expr c = Canonical.of(item, ctx.precision());
      switch (c) {
        case Bool b -> contradiction |= !b.value();
        case Relation r when r.op() == Relation.Op.EQ -> eqs.add(r);
        case Relation r -> {
          inequality = true;
          eqs.add(r);
        }
        default ->
            throw new IllegalArgumentException("solve needs an equation or a set of equations");
      }
    }
    List<Rewrite> rewrites = new ArrayList<>();
    Steps sink =
        ctx.traceEnabled()
            ? (rule, before, after, b) ->
                rewrites.add(new Rewrite(before, after, RULE_SET, rule, Path.ROOT, b))
            : Steps.NONE;
    Expr initial = Canonical.of(equations, ctx.precision());
    Chain chain = new Chain(sink, initial);
    Expr result;
    if (contradiction) {
      result = Canonical.of(new SetExpr(List.of()), ctx.precision());
      chain.step("roots", result, Chain.bind("solutions_", result));
    } else if (eqs.isEmpty()) {
      result = unevaluated(initial, vars, ctx);
      chain.step("all-values", result, Bindings.EMPTY);
    } else if (inequality) {
      result = unevaluated(initial, vars, ctx);
      chain.step("not-an-equation", result, Bindings.EMPTY);
    } else {
      chain = new Chain(sink, form(eqs, ctx));
      Optional<Providers.Outcome<SetExpr>> outcome =
          ctx.providers().attempt(new Solve(eqs, vars, ctx.precision()), ctx, sink);
      Expr reached = rewrites.isEmpty() ? chain.initial() : rewrites.getLast().after();
      if (outcome.isPresent()) {
        result = outcome.get().value();
        if (ctx.traceEnabled() && !reached.equals(result)) {
          // a provider that recorded no steps, or a result its steps do not end in
          rewrites.add(
              new Rewrite(
                  reached, result, PROVIDER, outcome.get().provider(), Path.ROOT, Bindings.EMPTY));
        }
      } else {
        result = unevaluated(form(eqs, ctx), vars, ctx);
        if (ctx.traceEnabled() && !reached.equals(result)) {
          rewrites.add(
              new Rewrite(reached, result, RULE_SET, "unsolved", Path.ROOT, Bindings.EMPTY));
        }
      }
    }
    Trace trace =
        ctx.traceEnabled() ? new Trace(chain.initial(), result, rewrites, List.of()) : Trace.NONE;
    return new Rewriter.Result(result, trace);
  }

  /**
   * Runs the methods, recording their steps from the equations' state: the default provider.
   *
   * @return the set of solutions, or empty when no method applies, after the step that says why
   */
  static Optional<SetExpr> methods(List<Expr> eqs, List<Symbol> vars, Context ctx, Steps steps) {
    Chain chain = new Chain(steps, form(eqs, ctx));
    try {
      if (eqs.size() == 1 && vars.size() == 1) {
        return Optional.of(
            Univariate.solve((Relation) eqs.getFirst(), vars.getFirst(), ctx, chain));
      }
      if (LinearSystems.isLinear(eqs, vars, ctx)) {
        return Optional.of(LinearSystems.solve(eqs, vars, ctx, chain));
      }
      throw new Declined("not-linear-system");
    } catch (Declined d) {
      chain.step(d.reason, unevaluated(form(eqs, ctx), vars, ctx), d.bindings);
      return Optional.empty();
    }
  }

  /** The equations as one state: the equation, or the set of them. */
  static Expr form(List<Expr> eqs, Context ctx) {
    return Canonical.of(eqs.size() == 1 ? eqs.getFirst() : new SetExpr(eqs), ctx.precision());
  }

  /** The call {@code solve(equations, variables)}, canonical. */
  static Expr unevaluated(Expr equations, List<Symbol> vars, Context ctx) {
    Expr v = vars.size() == 1 ? vars.getFirst() : new Vector(List.copyOf(vars));
    return Canonical.of(Apply.of(SOLVE, equations, v), ctx.precision());
  }

  private static List<Symbol> variables(Expr e) {
    List<Expr> items =
        e instanceof ListExpr l ? l.items() : e instanceof Vector v ? v.items() : List.of(e);
    List<Symbol> out = new ArrayList<>();
    for (Expr item : items) {
      if (!(item instanceof Symbol s) || out.contains(s)) {
        throw new IllegalArgumentException(
            "solve needs a variable or a list of distinct variables");
      }
      out.add(s);
    }
    if (out.isEmpty()) {
      throw new IllegalArgumentException("solve needs a variable or a list of distinct variables");
    }
    return out;
  }
}
