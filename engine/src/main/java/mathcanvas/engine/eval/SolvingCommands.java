package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Evaluator.INTEGER;
import static mathcanvas.engine.eval.Evaluator.REAL;
import static mathcanvas.engine.eval.Evaluator.REWRITE_CAP;
import static mathcanvas.engine.eval.Evaluator.REWRITE_CAP_PER_NODE;
import static mathcanvas.engine.eval.Evaluator.checkSize;
import static mathcanvas.engine.eval.Evaluator.integer;
import static mathcanvas.engine.eval.Evaluator.unfinished;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import mathcanvas.engine.algebra.AlgebraLimitException;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.assume.Facts;
import mathcanvas.engine.builtin.Builtin;
import mathcanvas.engine.builtin.Builtins;
import mathcanvas.engine.discrete.Rsolve;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Occurrence;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.numbertheory.Diophantine;
import mathcanvas.engine.ode.Dsolve;
import mathcanvas.engine.ode.NumericOde;
import mathcanvas.engine.provider.Providers;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.RewriteLimitException;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;
import mathcanvas.engine.solve.Solver;

/**
 * The solving commands of the evaluator and the shaping of their arguments: {@code solve} and
 * {@code nsolve}, {@code diophantine}, {@code dsolve}, {@code rsolve} and {@code odesolve},
 * evaluated in a statement's {@link Evaluator.State}, with the integer parameters and the constants
 * of integration they introduce.
 */
final class SolvingCommands {

  private final Evaluator.State state;

  SolvingCommands(Evaluator.State state) {
    this.state = state;
  }

  /**
   * {@code solve(equations, variables)}: the equations evaluated in the scope of the unknowns, then
   * solved ({@link Solver}) under the assumptions without facts about the unknowns. An unknown the
   * capture check renamed is renamed back to its written name in a set of solutions, unless that
   * name occurs there free, from outside; the solutions are not evaluated again.
   */
  Expr solve(Apply a, Builtin b) {
    Apply current = a;
    List<Symbol> original = Substitution.boundBy(a);
    List<Symbol> names = new ArrayList<>(original);
    for (int k = 0; k < names.size(); k++) {
      Symbol fresh =
          state.captureFree(current, names.get(k), Evaluator.State.scopeArgs(current, 1));
      if (fresh != null) {
        current = (Apply) Substitution.renameBinder(current, names.get(k).name(), fresh);
        names.set(k, fresh);
      }
    }
    Expr equations = state.heldEquations(current.args().get(0), names);
    Assumptions facts = state.env().assumptions();
    for (Symbol v : names) {
      facts = facts.without(v);
    }
    Context ctx =
        new Context(
            facts,
            state.mc(),
            state.traced() && a == state.root(),
            Math.toIntExact(REWRITE_CAP + REWRITE_CAP_PER_NODE * checkSize(equations, a)),
            Providers.defaults(),
            new SessionNumerics(
                state.env().settings().angleMode().halfTurn(), state.cancellation()),
            state.cancellation());
    Symbol parameter = parameter(current.args().get(0), equations, names);
    if (state.env().assumptions().facts(parameter).equals(Facts.NONE)) {
      ctx = ctx.withAssumptions(facts.with(integer(parameter)));
    }
    Rewriter.Result r;
    try {
      r = Solver.solve(equations, current.args().get(1), parameter, ctx);
    } catch (IllegalArgumentException wrongKind) {
      throw new EvaluationException(wrongKind.getMessage(), a);
    } catch (AlgebraLimitException limit) {
      throw new EvaluationException(limit.getMessage(), a);
    } catch (RewriteLimitException ex) {
      throw new EvaluationException(unfinished("simplification", ex), a);
    }
    Expr value = r.expr();
    boolean renamed = false;
    if (value instanceof SetExpr) {
      for (int k = 0; k < names.size(); k++) {
        // the written name, unless it occurs free in the solutions: then the fresh one stays
        if (!names.get(k).equals(original.get(k)) && !Occurrence.occurs(value, original.get(k))) {
          value = Substitution.rename(value, names.get(k).name(), original.get(k));
          renamed = true;
        }
      }
    }
    if (ctx.traceEnabled() && !renamed) {
      state.commandTrace(r.trace());
    }
    if (Occurrence.occurs(value, parameter)) {
      Expr given = state.frame() == null ? null : state.frame().lookup(parameter.name());
      if (given != null) {
        // the parameter is free in the result: its value here is put in, as a derivative's
        value = Substitution.apply(value, new TreeMap<>(Map.of(parameter.name(), given)));
      } else {
        state.parameters().put(parameter.name(), parameter);
      }
    }
    return state.canon(value);
  }

  /**
   * {@code diophantine(equation, [x, y])} ({@link Diophantine}): the equation evaluated in the
   * scope of the unknowns, renamed as {@code solve}'s are when they would capture a name from
   * outside, with an integer parameter chosen as {@code solve}'s is.
   */
  Expr diophantine(Apply a) {
    Apply current = a;
    List<Symbol> original = Substitution.boundBy(a);
    if (original.size() != 2) {
      throw new EvaluationException(
          "`diophantine` solves for two unknowns, as in `diophantine(3x + 5y = 1, [x, y])`", a);
    }
    List<Symbol> names = new ArrayList<>(original);
    for (int k = 0; k < names.size(); k++) {
      Symbol fresh =
          state.captureFree(current, names.get(k), Evaluator.State.scopeArgs(current, 1));
      if (fresh != null) {
        current = (Apply) Substitution.renameBinder(current, names.get(k).name(), fresh);
        names.set(k, fresh);
      }
    }
    Expr equation = state.heldEquations(current.args().get(0), names);
    Symbol parameter = parameter(current.args().get(0), equation, names);
    Context ctx =
        new Context(
                state.env().assumptions(),
                state.mc(),
                state.traced() && a == state.root(),
                REWRITE_CAP)
            .withCancellation(state.cancellation());
    Expr call = state.held(a, List.of(equation, current.args().get(1)));
    Rewriter.Result r;
    try {
      r = Diophantine.solve(call, equation, names.get(0), names.get(1), parameter, ctx);
    } catch (IllegalArgumentException wrong) {
      throw new EvaluationException(wrong.getMessage(), a);
    } catch (AlgebraLimitException limit) {
      throw new EvaluationException(limit.getMessage(), a);
    }
    Expr value = r.expr();
    boolean renamed = false;
    if (value instanceof SetExpr) {
      for (int k = 0; k < names.size(); k++) {
        if (!names.get(k).equals(original.get(k)) && !Occurrence.occurs(value, original.get(k))) {
          value = Substitution.rename(value, names.get(k).name(), original.get(k));
          renamed = true;
        }
      }
    }
    if (ctx.traceEnabled() && !renamed && !r.trace().rewrites().isEmpty()) {
      state.commandTrace(r.trace());
    }
    if (Occurrence.occurs(value, parameter)) {
      Expr given = state.frame() == null ? null : state.frame().lookup(parameter.name());
      if (given != null) {
        value = Substitution.apply(value, new TreeMap<>(Map.of(parameter.name(), given)));
      } else {
        state.parameters().put(parameter.name(), parameter);
      }
    }
    return state.canon(value);
  }

  /**
   * {@code dsolve(equations, unknowns, conditions…)} ({@code docs/language.md} §14.22): the
   * unknowns read from the second argument as written, the equations evaluated with the unknown
   * functions declared and the variable bound, the initial conditions with the functions declared;
   * then {@link Dsolve} under the assumptions without facts about the functions, with fresh
   * constants of integration {@code C_1, C_2, …}. A constant left in the value is collected for the
   * statement's note, as a general solution's parameter is.
   */
  Expr dsolve(Apply a) {
    List<Expr> args = a.args();
    OdeArgs prepared = odeArgs(a, args.subList(2, args.size()));
    List<Symbol> functions = prepared.functions();
    Symbol variable = prepared.variable();
    Expr equations = prepared.equations();
    List<Expr> conditions = prepared.conditions();
    List<Relation> eqs = prepared.eqs();
    List<Relation> ics = prepared.ics();
    List<Expr> callArgs = new ArrayList<>();
    callArgs.add(equations);
    callArgs.add(args.get(1));
    callArgs.addAll(conditions);
    Expr call = state.held(a, callArgs);
    Assumptions facts = state.env().assumptions();
    for (Symbol f : functions) {
      for (int n = 0; n <= Dsolve.MAX_ORDER; n++) {
        facts = facts.without(Symbol.of(f.name() + "'".repeat(n)));
      }
    }
    Context ctx =
        new Context(
            facts,
            state.mc(),
            state.traced() && a == state.root(),
            Math.toIntExact(REWRITE_CAP + REWRITE_CAP_PER_NODE * checkSize(call, a)),
            Providers.defaults(),
            new SessionNumerics(0, state.cancellation()),
            state.cancellation());
    List<Symbol> introduced = new ArrayList<>();
    Supplier<Symbol> fresh =
        () -> {
          Symbol c = constant(args, call, functions, introduced);
          introduced.add(c);
          return c;
        };
    Rewriter.Result r;
    try {
      r = Dsolve.solve(call, new Dsolve.Problem(eqs, functions, variable, ics), fresh, ctx);
    } catch (IllegalArgumentException wrong) {
      throw new EvaluationException(wrong.getMessage(), a);
    } catch (AlgebraLimitException limit) {
      throw new EvaluationException(limit.getMessage(), a);
    } catch (RewriteLimitException ex) {
      throw new EvaluationException(unfinished("`dsolve`", ex), a);
    }
    Expr value = state.canon(r.expr());
    for (Symbol c : introduced) {
      if (Occurrence.occurs(value, c)) {
        Expr given =
            state.frame() == null || state.bound().contains(c.name())
                ? null
                : state.frame().lookup(c.name());
        if (given != null) {
          // the constant is free in the result: its value here is put in, as a parameter's
          value = state.canon(Substitution.apply(value, new TreeMap<>(Map.of(c.name(), given))));
        } else {
          state.constants().put(c.name(), c);
        }
      }
    }
    if (ctx.traceEnabled() && value.equals(r.expr())) {
      state.commandTrace(r.trace());
    }
    return value;
  }

  /**
   * {@code rsolve(recurrence, a(n), conditions…)} ({@code docs/language.md} §14.24): the arguments
   * read as {@code dsolve}'s, one recurrence in one unknown; then {@link Rsolve} with fresh
   * constants {@code C₁, C₂, …}, collected for the note as {@code dsolve}'s are.
   */
  Expr rsolve(Apply a) {
    List<Expr> args = a.args();
    OdeArgs prepared = odeArgs(a, args.subList(2, args.size()));
    if (prepared.functions().size() != 1 || prepared.eqs().size() != 1) {
      throw new EvaluationException("`rsolve` solves one recurrence in one unknown a(n)", a);
    }
    List<Symbol> functions = prepared.functions();
    List<Expr> callArgs = new ArrayList<>();
    callArgs.add(prepared.equations());
    callArgs.add(args.get(1));
    callArgs.addAll(prepared.conditions());
    Expr call = state.held(a, callArgs);
    Context ctx =
        new Context(
            state.env().assumptions().without(functions.getFirst()),
            state.mc(),
            state.traced() && a == state.root(),
            Math.toIntExact(REWRITE_CAP + REWRITE_CAP_PER_NODE * checkSize(call, a)),
            Providers.defaults(),
            new SessionNumerics(0, state.cancellation()),
            state.cancellation());
    List<Symbol> introduced = new ArrayList<>();
    Supplier<Symbol> fresh =
        () -> {
          Symbol c = constant(args, call, functions, introduced);
          introduced.add(c);
          return c;
        };
    Rewriter.Result r;
    try {
      r =
          Rsolve.solve(
              call,
              new Rsolve.Problem(
                  prepared.eqs().getFirst(),
                  functions.getFirst(),
                  prepared.variable(),
                  prepared.ics()),
              fresh,
              ctx);
    } catch (IllegalArgumentException wrong) {
      throw new EvaluationException(wrong.getMessage(), a);
    } catch (AlgebraLimitException limit) {
      throw new EvaluationException(limit.getMessage(), a);
    } catch (RewriteLimitException ex) {
      throw new EvaluationException(unfinished("`rsolve`", ex), a);
    }
    Expr value = state.canon(r.expr());
    for (Symbol c : introduced) {
      if (Occurrence.occurs(value, c)) {
        Expr given =
            state.frame() == null || state.bound().contains(c.name())
                ? null
                : state.frame().lookup(c.name());
        if (given != null) {
          value = state.canon(Substitution.apply(value, new TreeMap<>(Map.of(c.name(), given))));
        } else {
          state.constants().put(c.name(), c);
        }
      }
    }
    if (ctx.traceEnabled() && value.equals(r.expr()) && r.trace() != Trace.NONE) {
      state.commandTrace(r.trace());
    }
    return value;
  }

  /**
   * {@code odesolve(equations, unknowns, conditions…, a, b[, steps = n][, at = x₀])} ({@code
   * docs/language.md} §14.22): the trailing options read as written, the equations and conditions
   * as for {@code dsolve}, the ends and the options' values evaluated; then {@link NumericOde}
   * under the assumptions without facts about the functions, the session's numerics and settings.
   * The value is numeric, its step {@code numeric.ode}.
   */
  Expr odesolve(Apply a) {
    List<Expr> args = new ArrayList<>(a.args());
    Expr stepsArg = null;
    Expr atArg = null;
    while (args.getLast() instanceof Relation r
        && r.op() == Relation.Op.EQ
        && r.left() instanceof Symbol option
        && (option.name().equals("steps") || option.name().equals("at"))) {
      if (option.name().equals("steps") ? stepsArg != null : atArg != null) {
        throw new EvaluationException("`odesolve` takes " + option.name() + " once", a);
      }
      if (option.name().equals("steps")) {
        stepsArg = r.right();
      } else {
        atArg = r.right();
      }
      args.removeLast();
    }
    if (stepsArg != null && atArg != null) {
      throw new EvaluationException("`odesolve` takes steps or at, not both", a);
    }
    if (args.size() < 5) {
      throw new EvaluationException(
          "`odesolve` needs equations, unknowns, initial conditions and an interval a, b", a);
    }
    OdeArgs prepared = odeArgs(a, args.subList(2, args.size() - 2));
    Expr lo = state.eval(args.get(args.size() - 2));
    Expr hi = state.eval(args.get(args.size() - 1));
    int steps = NumericOde.DEFAULT_STEPS;
    Expr at = null;
    if (atArg != null) {
      steps = 0;
      at = state.eval(atArg);
    } else if (stepsArg != null) {
      Expr n = state.eval(stepsArg);
      if (!(n instanceof Num num
          && num.value() instanceof Int k
          && k.value().signum() > 0
          && k.value().compareTo(BigInteger.valueOf(NumericOde.MAX_STEPS)) <= 0)) {
        throw new EvaluationException(
            "`odesolve`'s steps must be an integer from 1 to " + NumericOde.MAX_STEPS, a);
      }
      steps = ((Int) ((Num) n).value()).value().intValueExact();
    }
    Assumptions facts = state.env().assumptions();
    for (Symbol f : prepared.functions()) {
      for (int n = 0; n <= Dsolve.MAX_ORDER; n++) {
        facts = facts.without(Symbol.of(f.name() + "'".repeat(n)));
      }
    }
    Context ctx =
        new Context(
            facts,
            state.mc(),
            false,
            REWRITE_CAP,
            Providers.defaults(),
            new SessionNumerics(0, state.cancellation()),
            state.cancellation());
    Expr value;
    try {
      value =
          NumericOde.solve(
              new Dsolve.Problem(
                  prepared.eqs(), prepared.functions(), prepared.variable(), prepared.ics()),
              lo,
              hi,
              steps,
              at,
              state.env().settings(),
              ctx);
    } catch (IllegalArgumentException wrong) {
      throw new EvaluationException(wrong.getMessage(), a);
    } catch (AlgebraLimitException limit) {
      throw new EvaluationException(limit.getMessage(), a);
    } catch (RewriteLimitException ex) {
      throw new EvaluationException(unfinished("`odesolve`", ex), a);
    }
    value = state.canon(value);
    state.markNumeric();
    if (state.traced() && state.atTop(a)) {
      Expr call = state.canon(a);
      Bindings digits =
          new Bindings(
              List.of(new Bindings.Binding("digits_", Num.of(NumericOde.DIGITS), null, null)));
      state.commandTrace(
          new Trace(
              call,
              value,
              List.of(
                  new Rewrite(
                      call, value, NumericOde.RULE_SET, NumericOde.STEP, Path.ROOT, digits)),
              List.of()));
    }
    return value;
  }

  /** The arguments of {@code dsolve} or {@code odesolve}, read and evaluated. */
  private record OdeArgs(
      List<Symbol> functions,
      Symbol variable,
      Expr equations,
      List<Relation> eqs,
      List<Expr> conditions,
      List<Relation> ics) {}

  /**
   * The unknowns read from the second argument as written; the equations evaluated with the unknown
   * functions declared and the variable bound; the initial conditions, {@code conditionArgs}, with
   * the functions declared.
   */
  private OdeArgs odeArgs(Apply a, List<Expr> conditionArgs) {
    List<Expr> args = a.args();
    List<Expr> specs =
        switch (args.get(1)) {
          case Apply one -> List.of(one);
          case Vector v -> v.items();
          case ListExpr l -> l.items();
          default -> List.of();
        };
    List<Symbol> functions = new ArrayList<>();
    Symbol variable = null;
    for (Expr spec : specs) {
      if (!(spec instanceof Apply f)
          || f.head().name().endsWith("'")
          || Builtins.lookup(f.head().name()).isPresent()
          || f.args().size() != 1
          || !(f.args().getFirst() instanceof Symbol v)
          || (variable != null && !variable.equals(v))
          || v.equals(f.head())
          || functions.contains(f.head())) {
        throw new EvaluationException(
            "`"
                + a.head().name()
                + "` needs an unknown function y(x), or a list of them in one variable",
            a);
      }
      variable = v;
      functions.add(f.head());
    }
    if (functions.isEmpty()) {
      throw new EvaluationException(
          "`"
              + a.head().name()
              + "` needs an unknown function y(x), or a list of them in one variable",
          a);
    }
    Set<String> outer = state.unknownFunctions();
    state.unknownFunctions(new TreeSet<>(outer));
    for (Symbol f : functions) {
      state.unknownFunctions().add(f.name());
    }
    Expr equations;
    List<Expr> conditions = new ArrayList<>();
    try {
      equations = state.scoped(args.get(0), List.of(variable));
      for (Expr c : conditionArgs) {
        conditions.add(state.eval(c));
      }
    } finally {
      state.unknownFunctions(outer);
    }
    List<Expr> items =
        switch (equations) {
          case ListExpr l -> l.items();
          case Vector v -> v.items();
          case SetExpr s -> s.items();
          default -> List.of(equations);
        };
    List<Relation> eqs = new ArrayList<>();
    for (Expr item : items) {
      if (!(item instanceof Relation r) || r.op() != Relation.Op.EQ) {
        throw new EvaluationException(
            "`" + a.head().name() + "` needs an equation or a list of equations", a);
      }
      eqs.add(r);
    }
    List<Relation> ics = new ArrayList<>();
    for (Expr c : conditions) {
      if (!(c instanceof Relation r
          && r.op() == Relation.Op.EQ
          && Dsolve.isCondition(r, functions, variable))) {
        throw new EvaluationException(
            "an initial condition is written y(a) = b or y'(a) = b, with a and b free of the"
                + " unknowns",
            a);
      }
      ics.add(r);
    }
    return new OdeArgs(functions, variable, equations, eqs, conditions, ics);
  }

  /**
   * A constant of integration: {@code C_1}, {@code C_2}, … for the first that is not taken. A name
   * is taken when the held call or its value mentions it, it is an unknown function or bound here,
   * it has a definition, it has a fact other than {@code C ∈ ℝ} alone, or this statement took it
   * already. As for {@link #parameter}, a name the held call does not mention that has a value in
   * the current frame (a {@code subs} pair, a function's parameter) is not taken: its value is put
   * in for it afterwards.
   */
  private Symbol constant(
      List<Expr> held, Expr call, List<Symbol> functions, List<Symbol> introduced) {
    for (int n = 1; ; n++) {
      Symbol c = Symbol.of("C_" + n);
      Facts f = state.env().assumptions().facts(c);
      boolean mentioned = false;
      for (Expr e : held) {
        mentioned |= Occurrence.occurs(e, c);
      }
      if (!mentioned
          && !introduced.contains(c)
          && !functions.contains(c)
          && state.frame() != null
          && state.frame().lookup(c.name()) != null
          && !state.bound().contains(c.name())) {
        return c;
      }
      boolean taken =
          mentioned
              || Occurrence.occurs(call, c)
              || functions.contains(c)
              || introduced.contains(c)
              || state.bound().contains(c.name())
              || state.constants().containsKey(c.name())
              || state.env().definition(c.name()).isPresent()
              || !(f.equals(Facts.NONE) || f.equals(REAL));
      if (!taken) {
        return c;
      }
    }
  }

  /**
   * The integer parameter of a general solution ({@code docs/language.md} §14.7): {@code k}, or
   * {@code k_1}, {@code k_2}, … for the first that is not taken. A name is taken when the held
   * equations or their values mention it, it is an unknown or bound here, it has a definition, it
   * has a fact other than {@code k ∈ ℤ} alone (so the parameter of an earlier statement's solve is
   * used again), or an earlier solve of this statement took it. A name the held equations do not
   * mention that has a value in the current frame (a {@code subs} pair, a function's parameter) is
   * not taken: its value is put in for it afterwards.
   */
  private Symbol parameter(Expr held, Expr equations, List<Symbol> unknowns) {
    for (int n = 0; ; n++) {
      Symbol k = Symbol.of(n == 0 ? "k" : "k_" + n);
      boolean mentioned = Occurrence.occurs(held, k);
      if (!mentioned
          && state.frame() != null
          && state.frame().lookup(k.name()) != null
          && !state.bound().contains(k.name())) {
        return k;
      }
      Facts f = state.env().assumptions().facts(k);
      boolean taken =
          mentioned
              || Occurrence.occurs(equations, k)
              || unknowns.contains(k)
              || state.bound().contains(k.name())
              || state.parameters().containsKey(k.name())
              || state.env().definition(k.name()).isPresent()
              || !(f.equals(Facts.NONE) || f.equals(INTEGER));
      if (!taken) {
        return k;
      }
    }
  }
}
