package mathcanvas.engine.script;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import mathcanvas.engine.eval.Definition;
import mathcanvas.engine.eval.EvaluationException;
import mathcanvas.engine.eval.Evaluator;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Based;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.parse.Brackets;
import mathcanvas.engine.print.UnicodePrinter;

/**
 * Runs the statements of a block ({@code docs/language.md} §19). Every expression goes through the
 * evaluator, simplified as a statement's value is, in a frame of the block's local values; the
 * interpreter adds only the control flow, the scopes and the guards.
 *
 * <ul>
 *   <li><b>Scopes.</b> A top-level block runs in the document: its assignments bind the document's
 *       names to their values ({@link Definition.Computed}). A function's body runs in a scope of
 *       its own: its parameters and every name its body assigns (but the names it declares {@code
 *       global}) are local, decided before it runs, and a local read before it is assigned is an
 *       error. A loop variable is local to its loop, in either.
 *   <li><b>Values.</b> A block's value is the value of the last statement it ran when that is an
 *       expression, or an if whose branch has one; an assignment, a loop, a {@code print} and a
 *       {@code global} have none. A {@code print} appends its value to the run's output. A call's
 *       value is its {@code return}, else its body's value.
 *   <li><b>Guards.</b> Every statement run and every loop iteration is a step of the {@link
 *       Execution}, which checks the request to stop and the budget of {@link Execution#MAX_STEPS};
 *       a condition that is no truth value is an error.
 *   <li><b>Errors.</b> An error inside a function's body is placed at the call, its message
 *       beginning "in f, line n: " with the line of the statement that failed in the text the
 *       function was defined in.
 * </ul>
 */
public final class Interpreter {

  private Interpreter() {}

  /**
   * Runs a top-level control statement in the document's scope.
   *
   * @param statement the statement
   * @param execution the run
   * @return its value, or empty when it has none
   * @throws EvaluationException for an evaluation error
   */
  public static Optional<Expr> top(Stmt statement, Execution execution) {
    Scope scope = new Scope(execution, false, new TreeMap<>(), new TreeSet<>(), Set.of());
    try {
      scope.run(new Program(List.of(statement)));
    } finally {
      execution.leaveScope();
    }
    return scope.last;
  }

  /**
   * Runs a call of a function defined with {@code function}.
   *
   * @param name the function's name
   * @param procedure the function
   * @param arguments the arguments' values, canonical, as many as its parameters
   * @param execution the run
   * @param call the call, at which an error is placed
   * @return its value, or empty when it has none
   * @throws EvaluationException for an evaluation error, placed at the call
   */
  public static Optional<Expr> call(
      String name,
      Definition.Procedure procedure,
      List<Expr> arguments,
      Execution execution,
      Apply call) {
    TreeMap<String, Expr> locals = new TreeMap<>();
    for (int k = 0; k < arguments.size(); k++) {
      locals.put(procedure.params().get(k).name(), arguments.get(k));
    }
    Set<String> globals = procedure.program().globals();
    Set<String> unassigned = new TreeSet<>(procedure.program().assigned());
    unassigned.removeAll(globals);
    unassigned.removeAll(locals.keySet());
    Scope scope = new Scope(execution, true, locals, unassigned, globals);
    try {
      Flow flow = scope.run(procedure.program());
      return flow == Flow.RETURN ? Optional.of(scope.returned) : scope.last;
    } catch (EvaluationException e) {
      if (execution.placed(e)) {
        throw execution.place(new EvaluationException(e.getMessage(), call));
      }
      String where = scope.current == null ? "" : ", line " + scope.current.start().line();
      throw execution.place(
          new EvaluationException("in " + name + where + ": " + e.getMessage(), call));
    } finally {
      execution.leaveScope();
    }
  }

  /** How a statement ended. */
  private enum Flow {
    NEXT,
    BREAK,
    CONTINUE,
    RETURN
  }

  /** One block's run: the scope it reads and writes, and its value so far. */
  private static final class Scope {

    private final Execution x;
    private final boolean function;
    private final TreeMap<String, Expr> locals;
    private final Set<String> unassigned;
    private final Set<String> globals;

    /** The loop variables of the loops open, by name. */
    private final TreeMap<String, Expr> loops = new TreeMap<>();

    /** The value of the last statement run, empty when it had none. */
    private Optional<Expr> last = Optional.empty();

    private Expr returned;

    /** The statement running, for an error's line. */
    private Stmt current;

    /** Where the events of the statements running go, or null when the scope records none. */
    private List<RunEvent> sink;

    Scope(
        Execution x,
        boolean function,
        TreeMap<String, Expr> locals,
        Set<String> unassigned,
        Set<String> globals) {
      this.x = x;
      this.function = function;
      this.locals = locals;
      this.unassigned = unassigned;
      this.globals = globals;
      this.sink = x.enterScope();
    }

    /** The loop turns open, whose events go to their turn and are counted when their loop is. */
    private int turnsOpen;

    private void record(RunEvent.Kind kind, Stmt s, Symbol name, Expr expr, Expr value) {
      if (sink != null) {
        keep(new RunEvent.Done(kind, s.start().line(), name, expr, value));
      }
    }

    /** An event: kept in its turn, or, outside every turn, by the run, which counts it. */
    private void keep(RunEvent event) {
      if (turnsOpen > 0) {
        sink.add(event);
      } else {
        x.keep(sink, event);
      }
    }

    Flow run(Program p) {
      for (Stmt s : p.statements()) {
        Flow f = run(s);
        if (f != Flow.NEXT) {
          return f;
        }
      }
      return Flow.NEXT;
    }

    private Flow run(Stmt s) {
      current = s;
      x.step(node(s));
      switch (s) {
        case Stmt.Assign a -> {
          Expr v = value(a.value());
          assign(a.name().name(), v);
          record(RunEvent.Kind.ASSIGN, s, a.name(), a.value(), v);
          last = Optional.empty();
        }
        case Stmt.Evaluate e -> {
          last = maybe(e.expr());
          last.ifPresent(v -> record(RunEvent.Kind.EVALUATE, s, null, e.expr(), v));
        }
        case Stmt.Print p -> {
          Expr v = value(p.value());
          x.print(v);
          record(RunEvent.Kind.PRINT, s, null, p.value(), v);
          last = Optional.empty();
        }
        case Stmt.If i -> {
          for (Stmt.Branch b : i.branches()) {
            boolean taken = decide(b.condition());
            record(RunEvent.Kind.CONDITION, s, null, b.condition(), Bool.of(taken));
            if (taken) {
              last = Optional.empty();
              return run(b.body());
            }
          }
          last = Optional.empty();
          return run(i.otherwise());
        }
        case Stmt.For f -> {
          return loop(f);
        }
        case Stmt.While w -> {
          return loop(w);
        }
        case Stmt.Return r -> {
          returned = value(r.value());
          record(RunEvent.Kind.RETURN, s, null, r.value(), returned);
          return Flow.RETURN;
        }
        case Stmt.Break _ -> {
          return Flow.BREAK;
        }
        case Stmt.Continue _ -> {
          return Flow.CONTINUE;
        }
        case Stmt.Global _ -> last = Optional.empty();
      }
      return Flow.NEXT;
    }

    /** A while loop: its condition before every turn, each turn after the first a step. */
    private Flow loop(Stmt.While w) {
      Turns turns = new Turns();
      try {
        for (int index = 1; ; index++) {
          if (index > 1) {
            x.step(w.condition());
          }
          if (!decide(w.condition())) {
            break;
          }
          Flow flow = turns.run(index, null, w.body());
          if (flow == Flow.BREAK) {
            break;
          }
          if (flow == Flow.RETURN) {
            return flow;
          }
        }
      } finally {
        turns.done(w, null);
      }
      last = Optional.empty();
      return Flow.NEXT;
    }

    /**
     * The turns of one loop being recorded: the first two and the last kept, the value the variable
     * had in the last turn not kept.
     */
    private final class Turns {
      private final List<RunEvent.Turn> kept = new ArrayList<>();
      private RunEvent.Turn latest;
      private Expr folded;
      private int count;

      Flow run(int index, Expr value, Program body) {
        count = index;
        if (sink == null) {
          return Scope.this.run(body);
        }
        List<RunEvent> outer = sink;
        List<RunEvent> mine = new ArrayList<>();
        sink = mine;
        turnsOpen++;
        try {
          return Scope.this.run(body);
        } finally {
          turnsOpen--;
          sink = outer;
          RunEvent.Turn t = new RunEvent.Turn(index, value, mine);
          if (index <= 2) {
            kept.add(t);
          } else {
            if (latest != null) {
              folded = latest.value();
            }
            latest = t;
          }
        }
      }

      void done(Stmt loop, Symbol variable) {
        if (sink == null || count == 0) {
          return;
        }
        if (latest != null) {
          kept.add(latest);
        }
        keep(new RunEvent.Loop(loop.start().line(), variable, count, kept, folded));
      }
    }

    private Flow loop(Stmt.For f) {
      String name = f.variable().name();
      List<Expr> values = null;
      BigInteger from = null;
      BigInteger to = null;
      BigInteger step = BigInteger.ONE;
      switch (f.source()) {
        case Stmt.Range r -> {
          from = integer(r.from());
          to = integer(r.to());
          if (r.step() != null) {
            step = integer(r.step());
            if (step.signum() == 0) {
              throw new EvaluationException("the step of a range must not be 0", r.step());
            }
          }
        }
        case Stmt.Items items -> values = items(items.items());
      }
      Expr outer = loops.get(name);
      Turns turns = new Turns();
      try {
        BigInteger k = from;
        for (int index = 0; ; index++) {
          Expr value;
          if (values != null) {
            if (index >= values.size()) {
              break;
            }
            value = values.get(index);
          } else {
            if (step.signum() > 0 ? k.compareTo(to) > 0 : k.compareTo(to) < 0) {
              break;
            }
            value = Num.of(Int.of(k));
            k = k.add(step);
          }
          if (index > 0) {
            x.step(f.variable());
          }
          loops.put(name, value);
          Flow flow = turns.run(index + 1, value, f.body());
          if (flow == Flow.BREAK) {
            break;
          }
          if (flow == Flow.RETURN) {
            return flow;
          }
        }
      } finally {
        turns.done(f, f.variable());
        if (outer == null) {
          loops.remove(name);
        } else {
          loops.put(name, outer);
        }
      }
      last = Optional.empty();
      return Flow.NEXT;
    }

    /** A range's bound or step: an integer. */
    private BigInteger integer(Expr raw) {
      Expr v = Based.unwrap(value(raw));
      if (v instanceof Num n && n.value() instanceof Int i) {
        return i.value();
      }
      throw new EvaluationException("a range needs integers", raw);
    }

    /** What a for loop iterates: the items of a list, a vector or a set, or a matrix's rows. */
    private List<Expr> items(Expr raw) {
      return switch (value(raw)) {
        case ListExpr l -> l.items();
        case Vector v -> v.items();
        case SetExpr s -> s.items();
        case Matrix m -> {
          List<Expr> rows = new ArrayList<>();
          for (List<Expr> row : m.rows()) {
            rows.add(Brackets.of(row));
          }
          yield rows;
        }
        case Num _,
            mathcanvas.engine.expr.Based _,
            mathcanvas.engine.expr.Constant _,
            Bool _,
            Symbol _,
            mathcanvas.engine.expr.Unit _,
            mathcanvas.engine.expr.Sum _,
            mathcanvas.engine.expr.Product _,
            mathcanvas.engine.expr.Power _,
            Apply _,
            mathcanvas.engine.expr.Lambda _,
            mathcanvas.engine.expr.Relation _,
            mathcanvas.engine.expr.Interval _,
            mathcanvas.engine.expr.Piecewise _,
            mathcanvas.engine.expr.Derivative _,
            mathcanvas.engine.expr.Integral _,
            mathcanvas.engine.expr.Limit _,
            mathcanvas.engine.expr.BigSum _,
            mathcanvas.engine.expr.BigProduct _ ->
            throw new EvaluationException(
                "for iterates a list, a vector, a set or a range a..b", raw);
      };
    }

    private void assign(String name, Expr value) {
      if (loops.containsKey(name)) {
        loops.put(name, value);
      } else if (function && !globals.contains(name)) {
        locals.put(name, value);
        unassigned.remove(name);
      } else {
        x.assign(name, value);
      }
    }

    private boolean decide(Expr condition) {
      Expr v = value(condition);
      if (v instanceof Bool b) {
        return b.value();
      }
      Optional<Boolean> bySign =
          Evaluator.bySign(
              v, x.environment().assumptions(), x.environment().settings().mathContext());
      if (bySign.isPresent()) {
        return bySign.get();
      }
      throw new EvaluationException(
          "the condition did not decide: " + UnicodePrinter.INSTANCE.display(v), condition);
    }

    /** An expression's value, which it must have. */
    private Expr value(Expr raw) {
      Optional<Expr> v = maybe(raw);
      if (v.isEmpty()) {
        String name = raw instanceof Apply a ? a.head().name() : "the call";
        throw new EvaluationException(name + " has no value", raw);
      }
      return v.get();
    }

    /** An expression's value, empty when it is a call of a function that ended with none. */
    private Optional<Expr> maybe(Expr raw) {
      SortedMap<String, Expr> frame = new TreeMap<>(locals);
      frame.putAll(loops);
      Set<String> unread = new TreeSet<>(unassigned);
      unread.removeAll(loops.keySet());
      Evaluator.Result r = Evaluator.evaluate(raw, frame, unread, x);
      x.numeric(r.numeric());
      return x.empty() ? Optional.empty() : Optional.of(r.value());
    }

    /** The node a step error is placed at. */
    private static Expr node(Stmt s) {
      return switch (s) {
        case Stmt.Assign a -> a.value();
        case Stmt.Evaluate e -> e.expr();
        case Stmt.If i -> i.branches().getFirst().condition();
        case Stmt.For f -> f.variable();
        case Stmt.While w -> w.condition();
        case Stmt.Print p -> p.value();
        case Stmt.Return r -> r.value();
        case Stmt.Break _, Stmt.Continue _, Stmt.Global _ -> Symbol.of("program");
      };
    }
  }
}
