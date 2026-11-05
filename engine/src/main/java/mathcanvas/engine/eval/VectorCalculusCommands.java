package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Evaluator.REWRITE_CAP;
import static mathcanvas.engine.eval.Evaluator.REWRITE_CAP_PER_NODE;
import static mathcanvas.engine.eval.Evaluator.TAYLOR_ORDER;
import static mathcanvas.engine.eval.Evaluator.checkSize;
import static mathcanvas.engine.eval.Evaluator.unfinished;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import mathcanvas.engine.algebra.AlgebraLimitException;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.assume.Facts;
import mathcanvas.engine.calculus.Calculus;
import mathcanvas.engine.calculus.Lagrange;
import mathcanvas.engine.calculus.Laplace;
import mathcanvas.engine.calculus.Multivariable;
import mathcanvas.engine.calculus.TaylorException;
import mathcanvas.engine.calculus.VectorIntegrals;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Occurrence;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.provider.Providers;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.RewriteLimitException;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;

/**
 * The vector calculus commands and the typed nodes of the evaluator: line and surface integrals,
 * {@code laplace} and {@code ilaplace}, {@code lagrange}, the vector calculus operators (§14.20)
 * and {@code taylor}, evaluated in a statement's {@link Evaluator.State}.
 */
final class VectorCalculusCommands {

  private final Evaluator.State state;

  VectorCalculusCommands(Evaluator.State state) {
    this.state = state;
  }

  /**
   * {@code lineintegral(f, r, t, a, b)} and {@code surfaceintegral(f, r, [u, a, b], [v, c, d])}
   * ({@link VectorIntegrals}): the field evaluated with {@code x, y, z} bound, the path or surface
   * with its parameters bound, an inner bracket's bounds with the outer parameter bound, the other
   * bounds evaluated; the facts without those of the enclosing binders' names, the parameters and
   * the coordinates; the solver's providers and numerics. The reduced integral that stays goes on
   * to the numeric path.
   */
  Expr vectorIntegral(Apply a) {
    if (state.folding()) {
      return a;
    }
    String name = a.head().name();
    List<Expr> args = a.args();
    List<Symbol> coordinates = VectorIntegrals.COORDINATES;
    Expr field = state.scoped(args.getFirst(), coordinates);
    List<Symbol> parameters = new ArrayList<>();
    List<Expr> bounds = new ArrayList<>();
    Expr shape;
    if (name.equals("lineintegral")) {
      if (!(args.get(2) instanceof Symbol t)) {
        throw new EvaluationException("`lineintegral` needs a variable", a);
      }
      parameters.add(t);
      shape = state.scoped(args.get(1), List.of(t));
      bounds.add(state.eval(args.get(3)));
      bounds.add(state.eval(args.get(4)));
    } else {
      List<Expr> inner = bracket(args.get(2));
      List<Expr> outer = bracket(args.get(3));
      if (inner == null || outer == null) {
        throw new EvaluationException(
            "`surfaceintegral` needs square brackets [u, a, b] and [v, c, d]", a);
      }
      Symbol u = (Symbol) inner.getFirst();
      Symbol v = (Symbol) outer.getFirst();
      if (u.equals(v)) {
        throw new EvaluationException("`surfaceintegral` needs two parameters", a);
      }
      parameters.add(u);
      parameters.add(v);
      shape = state.scoped(args.get(1), List.of(u, v));
      bounds.add(state.scoped(inner.get(1), List.of(v)));
      bounds.add(state.scoped(inner.get(2), List.of(v)));
      bounds.add(state.eval(outer.get(1)));
      bounds.add(state.eval(outer.get(2)));
    }
    // a parameter the field mentions is a free symbol there, never the parameter: the parameter
    // takes a fresh name in the path or surface and the bounds, as integrate's capture check does
    for (int k = 0; k < parameters.size(); k++) {
      Symbol p = parameters.get(k);
      if (Occurrence.occurs(field, p)) {
        Symbol fresh = Evaluator.State.freshFor(Apply.of(p, field, shape, new ListExpr(bounds)), p);
        TreeMap<String, Expr> renaming = new TreeMap<>(Map.of(p.name(), fresh));
        shape = Substitution.apply(shape, renaming);
        bounds.replaceAll(bd -> Substitution.apply(bd, renaming));
        parameters.set(k, fresh);
      }
    }
    Assumptions facts = state.env().assumptions();
    for (String bn : state.bound()) {
      facts = facts.without(Symbol.of(bn));
    }
    for (Symbol s : parameters) {
      facts = facts.without(s);
    }
    for (Symbol s : coordinates) {
      facts = facts.without(s);
    }
    long nodes = checkSize(a, a);
    boolean whole = state.traced() && a == state.root();
    Context context =
        new Context(
            facts,
            state.mc(),
            whole,
            Math.toIntExact(REWRITE_CAP + REWRITE_CAP_PER_NODE * nodes),
            Providers.defaults(),
            new SessionNumerics(
                state.env().settings().angleMode().halfTurn(), state.cancellation()),
            state.cancellation());
    Rewriter.Result r;
    try {
      r =
          name.equals("lineintegral")
              ? VectorIntegrals.line(
                  field, shape, parameters.getFirst(), bounds.get(0), bounds.get(1), a, context)
              : VectorIntegrals.surface(
                  field,
                  shape,
                  parameters.get(0),
                  bounds.get(0),
                  bounds.get(1),
                  parameters.get(1),
                  bounds.get(2),
                  bounds.get(3),
                  a,
                  context);
    } catch (IllegalArgumentException wrong) {
      throw new EvaluationException(wrong.getMessage(), a);
    } catch (AlgebraLimitException limit) {
      throw new EvaluationException(limit.getMessage(), a);
    } catch (RewriteLimitException ex) {
      throw new EvaluationException(unfinished("`" + name + "`", ex), a);
    }
    if (whole) {
      state.commandTrace(r.trace());
    }
    return r.expr();
  }

  /**
   * {@code laplace(f, t, s)} or {@code ilaplace(F, s, t)} whose first argument has been evaluated
   * with its variable bound ({@link Laplace#of}): the facts without those of the enclosing binders'
   * names and the two variables. The result's variable, held, must be a name; it is free in the
   * result, as a derivative's variable is, so a value it has here is put in.
   */
  Expr transform(Expr node, Apply typed) {
    if (state.folding() || !(node instanceof Apply call)) {
      return node;
    }
    String name = call.head().name();
    if (!(call.args().get(1) instanceof Symbol v) || !(call.args().get(2) instanceof Symbol w)) {
      throw new EvaluationException("`" + name + "` needs two variables", node);
    }
    if (v.equals(w)) {
      throw new EvaluationException("`" + name + "` needs two different variables", node);
    }
    Assumptions facts = state.env().assumptions();
    for (String bn : state.bound()) {
      facts = facts.without(Symbol.of(bn));
    }
    facts = facts.without(v).without(w);
    long nodes = checkSize(call, call);
    boolean whole = state.traced() && typed == state.root();
    Context context =
        new Context(
                facts,
                state.mc(),
                whole,
                Math.toIntExact(REWRITE_CAP + REWRITE_CAP_PER_NODE * nodes))
            .withCancellation(state.cancellation());
    Rewriter.Result r;
    try {
      r = Laplace.of(call, context);
    } catch (AlgebraLimitException limit) {
      throw new EvaluationException(limit.getMessage(), node);
    } catch (RewriteLimitException ex) {
      throw new EvaluationException(unfinished("`" + name + "`", ex), node);
    }
    Expr result;
    Set<String> outer = state.unknownFunctions();
    if (name.equals("laplace")) {
      // the unknown functions and their initial values y(0), y'(0) stay applications
      state.unknownFunctions(new TreeSet<>(outer));
      state.unknownHeads(r.expr(), state.unknownFunctions());
    }
    try {
      result =
          r.expr() instanceof Apply stays && stays.head().equals(call.head())
              ? r.expr()
              : state.freeVariable(r.expr(), w, w);
    } finally {
      state.unknownFunctions(outer);
    }
    if (whole) {
      Trace t = r.trace();
      if (!result.equals(t.result())) {
        // the folded result: a normalisation at the root carries the trace to it
        List<Trace.Normalisation> ns = new ArrayList<>(t.normalisations());
        ns.add(new Trace.Normalisation(t.rewrites().size(), Path.ROOT, result));
        t = new Trace(t.initial(), result, t.rewrites(), ns);
      }
      state.commandTrace(t);
    }
    return result;
  }

  /** The items of a bracket {@code [name, a, b]}, or null. */
  private static List<Expr> bracket(Expr e) {
    List<Expr> items =
        e instanceof Vector v ? v.items() : e instanceof ListExpr l ? l.items() : List.of();
    return items.size() == 3 && items.getFirst() instanceof Symbol ? items : null;
  }

  /**
   * {@code lagrange(f, constraints, variables)} whose function and constraints have been evaluated
   * in the scope of its variables ({@link Lagrange#of}), under the solver's context: the facts
   * without those of the enclosing binders' names and the variables, the default providers and the
   * session's numerics. A variable the capture check renamed is named back in the points unless its
   * written name occurs in them from outside, as {@code solve} does.
   */
  Expr lagrange(Expr node, Apply typed) {
    if (state.folding() || !(node instanceof Apply call)) {
      return node;
    }
    Assumptions facts = state.env().assumptions();
    for (String name : state.bound()) {
      facts = facts.without(Symbol.of(name));
    }
    List<Symbol> vars = Substitution.boundBy(call);
    for (Symbol v : vars) {
      facts = facts.without(v);
    }
    long nodes = checkSize(call, call);
    boolean whole = state.traced() && typed == state.root();
    Context context =
        new Context(
            facts,
            state.mc(),
            whole,
            Math.toIntExact(REWRITE_CAP + REWRITE_CAP_PER_NODE * nodes),
            Providers.defaults(),
            new SessionNumerics(
                state.env().settings().angleMode().halfTurn(), state.cancellation()),
            state.cancellation());
    Rewriter.Result r;
    try {
      r =
          Lagrange.of(
              call,
              context,
              m ->
                  Occurrence.occurs(typed, m)
                      || state.bound().contains(m.name())
                      || state.env().definition(m.name()).isPresent()
                      || (state.frame() != null && state.frame().lookup(m.name()) != null)
                      || !state.env().assumptions().facts(m).equals(Facts.NONE));
    } catch (IllegalArgumentException wrong) {
      throw new EvaluationException(wrong.getMessage(), node);
    } catch (AlgebraLimitException limit) {
      throw new EvaluationException(limit.getMessage(), node);
    } catch (RewriteLimitException ex) {
      throw new EvaluationException(unfinished("`lagrange`", ex), node);
    }
    Expr result = r.expr();
    List<Symbol> written = Substitution.boundBy(typed);
    if (result instanceof SetExpr && written.size() == vars.size()) {
      for (int k = 0; k < vars.size(); k++) {
        if (!vars.get(k).equals(written.get(k)) && !Occurrence.occurs(result, written.get(k))) {
          result =
              state.canon(
                  Substitution.apply(
                      result, new TreeMap<>(Map.of(vars.get(k).name(), written.get(k)))));
        }
      }
    }
    if (whole) {
      state.commandTrace(r.trace());
    }
    return result;
  }

  /**
   * The value of a vector calculus command whose function or field has been evaluated in the scope
   * of its variables ({@link Multivariable#of}): the facts without those about the enclosing
   * binders' names and the variables, the cap for the call's size and the cancellation. A wrong
   * argument is the command's error. Each variable is free in the result, as a derivative's is: a
   * value its name as written has here is put in, and a fresh name the capture check gave it is
   * renamed back.
   */
  Expr vectorCalculus(Expr node, Apply typed) {
    if (state.folding() || !(node instanceof Apply call)) {
      return node;
    }
    Assumptions facts = state.env().assumptions();
    for (String name : state.bound()) {
      facts = facts.without(Symbol.of(name));
    }
    List<Symbol> vars = Substitution.boundBy(call);
    for (Symbol v : vars) {
      facts = facts.without(v);
    }
    long nodes = checkSize(call, call);
    boolean whole = state.traced() && typed == state.root();
    Context context =
        new Context(
                facts,
                state.mc(),
                whole,
                Math.toIntExact(REWRITE_CAP + REWRITE_CAP_PER_NODE * nodes))
            .withCancellation(state.cancellation());
    Rewriter.Result r;
    try {
      r = Multivariable.of(call, context);
    } catch (IllegalArgumentException wrong) {
      throw new EvaluationException(wrong.getMessage(), node);
    } catch (RewriteLimitException ex) {
      throw new EvaluationException(unfinished("`" + call.head().name() + "`", ex), node);
    }
    List<Symbol> written = Substitution.boundBy(typed);
    Expr result = r.expr();
    for (int k = 0; k < vars.size(); k++) {
      Symbol w = written.size() == vars.size() ? written.get(k) : vars.get(k);
      result = state.freeVariable(result, vars.get(k), w);
    }
    if (whole) {
      state.commandTrace(r.trace());
    }
    return result;
  }

  /**
   * {@code taylor(f, x, a, n)} after its binder evaluation: the Taylor polynomial ({@link
   * Calculus#taylor}) for an order that is a non-negative integer, the call itself for an order
   * with a symbol in it; any other order, an infinite point and a derivative that fails are errors.
   * The variable is free in the polynomial, as a derivative's is.
   */
  Expr taylor(Expr node, Apply typed) {
    if (state.folding()
        || !(node instanceof Apply call)
        || !(call.args().get(1) instanceof Symbol v)) {
      return node;
    }
    Expr order = call.args().get(3);
    Expr point = call.args().get(2);
    if (point.equals(Constant.UNDEFINED) || order.equals(Constant.UNDEFINED)) {
      return Constant.UNDEFINED;
    }
    if (!(order instanceof Num n && n.value() instanceof Int k && k.signum() >= 0)) {
      if (hasSymbol(order)) {
        return node;
      }
      throw new EvaluationException(TAYLOR_ORDER, typed);
    }
    if (k.value().bitLength() > 31) {
      throw new EvaluationException(TAYLOR_ORDER, typed);
    }
    if (!finite(point)) {
      throw new EvaluationException("`taylor`'s point must be finite", typed);
    }
    Assumptions facts = state.env().assumptions();
    for (String name : state.bound()) {
      facts = facts.without(Symbol.of(name));
    }
    boolean whole = state.traced() && typed == state.root();
    Context context =
        new Context(
                facts,
                state.mc(),
                whole,
                Math.toIntExact(REWRITE_CAP + REWRITE_CAP_PER_NODE * checkSize(call, typed)))
            .withNumerics(new SessionNumerics(0, state.cancellation()))
            .withCancellation(state.cancellation());
    Rewriter.Result r;
    try {
      r = Calculus.taylor(call.args().get(0), v, point, k.value().intValue(), context);
    } catch (TaylorException ex) {
      throw new EvaluationException(ex.getMessage(), typed);
    } catch (RewriteLimitException ex) {
      throw new EvaluationException(unfinished("differentiation", ex), typed);
    }
    Expr written = typed.args().get(1);
    Expr result = state.freeVariable(r.expr(), v, written instanceof Symbol s ? s : v);
    if (whole) {
      state.commandTrace(r.trace());
    }
    return result;
  }

  private static boolean hasSymbol(Expr e) {
    if (e instanceof Symbol) {
      return true;
    }
    for (Expr o : Operands.of(e)) {
      if (hasSymbol(o)) {
        return true;
      }
    }
    return false;
  }

  private static boolean finite(Expr e) {
    if (e.equals(Constant.INFINITY) || e.equals(Constant.UNDEFINED)) {
      return false;
    }
    for (Expr o : Operands.of(e)) {
      if (!finite(o)) {
        return false;
      }
    }
    return true;
  }
}
