package mathcanvas.engine.eval;

import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.algebra.AlgebraLimitException;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.builtin.Builtin;
import mathcanvas.engine.builtin.Builtins;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Lambda;
import mathcanvas.engine.expr.Limit;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Piecewise;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.parse.Brackets;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.parse.TypedNodes;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.RewriteLimitException;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;
import mathcanvas.engine.simplify.Simplifier;

/**
 * Turns a parsed expression into its value in an environment ({@code docs/language.md} §11, §12,
 * §14).
 *
 * <p>Evaluation is bottom-up and every intermediate value is canonical. A symbol is looked up in
 * order: a name a binder around it binds is itself; a parameter of the current <b>frame</b> is its
 * value, returned as it is and never evaluated again; a name bound to a value definition is the
 * evaluation of the definition's body, delayed and memoised per call, with a stack of the names
 * being expanded that turns a cycle into an error; any other symbol is itself. A call of a user
 * function evaluates its arguments, then evaluates the function's raw body in a frame of its own
 * that maps the parameters to those values (static scoping: the frame holds only that call's
 * parameters, and a definition's body is evaluated with none). {@code subs} evaluates its held
 * expression in a frame of its names. Builtin functions evaluate on numbers ({@link Functions}),
 * vectors and matrices combine ({@link LinearAlgebra}), relations between numbers are decided,
 * brackets are classified again.
 *
 * <p>A binder's variable is a bound name in its scope, neither looked up nor taken from a frame.
 * Before the scope is evaluated a static check decides whether the variable could capture a name
 * from outside the scope: a frame value that holds a symbol of that name, or a definition the scope
 * reaches through the environment whose bodies hold one. If so, the binder is renamed to a fresh
 * {@code v_k} first, once. The value is then simplified with {@link Simplifier#standard()}, once,
 * under a rewrite cap that grows with its size.
 *
 * <p>Evaluation is pure: no I/O, no clock, no randomness. It is bounded: at most {@link #MAX_DEPTH}
 * nested evaluation steps (operands and expansions together), {@link #MAX_EXPANSIONS} nested
 * definitions, a statement's value and each definition's value of at most {@link #MAX_NODES} nodes,
 * and a value at most {@link Parser#MAX_NESTING} levels deep, the bound every later walk of a tree
 * relies on. An {@code ArithmeticException} from the number tower or the canonical form (a result
 * beyond {@code BigDecimal}'s range) is the error "number out of range at this precision".
 */
public final class Evaluator {

  /** The most nested evaluation steps: descents into operands and expansions together. */
  public static final int MAX_DEPTH = 1_000;

  /** The most definitions and calls being expanded at once. */
  public static final int MAX_EXPANSIONS = 200;

  /** The most nodes of a value, counting a shared subtree once per occurrence. */
  public static final int MAX_NODES = 1_000_000;

  /** The rewrite cap of the simplifier for a statement, before {@link #REWRITE_CAP_PER_NODE}. */
  public static final int REWRITE_CAP = 10_000;

  /** The rewrites a statement's cap grows by per node of the value simplified. */
  public static final int REWRITE_CAP_PER_NODE = 20;

  /** The message of a result beyond the tower's range. */
  static final String OUT_OF_RANGE = "number out of range at this precision";

  /**
   * A value and the trace of its simplification.
   *
   * @param value the canonical value
   * @param trace the trace, {@link Trace#NONE} when tracing was off
   */
  public record Result(Expr value, Trace trace) {}

  /**
   * What {@code assume} recorded.
   *
   * @param assumptions the assumptions with the new facts
   * @param recorded the relations recorded, as a canonical set
   */
  record Assumed(Assumptions assumptions, SetExpr recorded) {}

  private Evaluator() {}

  /**
   * Evaluates a parsed expression. A top-level {@code steps(e)} evaluates {@code e} with the trace
   * on.
   *
   * @param raw the expression, as parsed
   * @param env the environment
   * @param trace whether to record the simplifier's rewrites
   * @return the value and its trace
   * @throws EvaluationException for an evaluation error of §16
   */
  public static Result evaluate(Expr raw, Environment env, boolean trace) {
    Expr e = raw;
    boolean traced = trace;
    if (raw instanceof Apply a && a.head().name().equals("steps") && a.args().size() == 1) {
      e = a.args().getFirst();
      traced = true;
    }
    try {
      State state = new State(env, traced, e);
      Expr value = state.eval(e);
      long nodes = checkSize(value, raw);
      Context context =
          new Context(
              env.assumptions(),
              env.settings().mathContext(),
              traced,
              Math.toIntExact(REWRITE_CAP + REWRITE_CAP_PER_NODE * nodes));
      Rewriter.Result r;
      try {
        r = Simplifier.simplify(value, context);
      } catch (RewriteLimitException ex) {
        throw new EvaluationException(
            "simplification did not finish (rule set " + ex.ruleSet() + ", rule " + ex.rule() + ")",
            raw);
      }
      if (depth(r.expr()) > Parser.MAX_NESTING) {
        throw new EvaluationException("expression nested too deeply", raw);
      }
      // an algebra command at the top of the statement: its steps, then the simplifier's
      Trace steps =
          state.commandTrace != null && state.commandTrace.result().equals(r.trace().initial())
              ? Trace.concat(state.commandTrace, r.trace())
              : r.trace();
      return new Result(r.expr(), steps);
    } catch (ArithmeticException outOfRange) {
      throw new EvaluationException(OUT_OF_RANGE, raw);
    }
  }

  /**
   * Records the relations of an {@code assume} statement: each relation's left side is held, its
   * right side evaluated.
   *
   * @param call the {@code assume(…)} application, as parsed
   * @param env the environment
   * @return the new assumptions and the relations recorded
   * @throws EvaluationException if an argument is not a relation, or {@link Assumptions#with}
   *     rejects it
   */
  static Assumed assume(Apply call, Environment env) {
    State state = new State(env, false, null);
    Assumptions a = env.assumptions();
    List<Expr> recorded = new ArrayList<>();
    for (Expr arg : call.args()) {
      if (!(arg instanceof Relation r)) {
        throw new EvaluationException("assume takes relations", arg);
      }
      try {
        Relation fact = new Relation(r.op(), r.left(), state.eval(r.right()));
        a = a.with(fact);
        recorded.add(state.canon(fact));
      } catch (IllegalArgumentException rejected) {
        throw new EvaluationException(rejected.getMessage(), arg);
      } catch (ArithmeticException outOfRange) {
        throw new EvaluationException(OUT_OF_RANGE, arg);
      }
    }
    return new Assumed(a, (SetExpr) state.canon(new SetExpr(recorded)));
  }

  /** The depth of a tree, a leaf being 0, without recursion. */
  static int depth(Expr e) {
    int max = 0;
    Deque<Expr> nodes = new ArrayDeque<>();
    Deque<Integer> depths = new ArrayDeque<>();
    nodes.push(e);
    depths.push(0);
    while (!nodes.isEmpty()) {
      Expr n = nodes.pop();
      int d = depths.pop();
      max = Math.max(max, d);
      for (Expr op : Operands.of(n)) {
        nodes.push(op);
        depths.push(d + 1);
      }
    }
    return max;
  }

  /**
   * Throws "result too large" when a value has more than {@link #MAX_NODES} nodes, counting a
   * shared subtree once per occurrence, as every walk of the tree will; stops counting there.
   *
   * @return the number of nodes, at most {@link #MAX_NODES}
   */
  static long checkSize(Expr value, Expr node) {
    long count = 0;
    Deque<Expr> nodes = new ArrayDeque<>();
    nodes.push(value);
    while (!nodes.isEmpty()) {
      if (++count > MAX_NODES) {
        throw new EvaluationException("result too large", node);
      }
      for (Expr op : Operands.of(nodes.pop())) {
        nodes.push(op);
      }
    }
    return count;
  }

  /**
   * The parameters of one call, or the names of one {@code subs}: name to value. A call's frame has
   * no parent (static scoping); a {@code subs} frame's parent is the frame it is evaluated in.
   */
  private record Frame(SortedMap<String, Expr> values, Frame parent) {

    /** The value of a name in this frame or its parents, or null. */
    Expr lookup(String name) {
      for (Frame f = this; f != null; f = f.parent) {
        Expr v = f.values.get(name);
        if (v != null) {
          return v;
        }
      }
      return null;
    }
  }

  /** One evaluation: the environment, the precision in force, and the bookkeeping. */
  private static final class State {

    private final Environment env;
    private MathContext mc;
    private TreeMap<String, Expr> memo = new TreeMap<>();
    private final List<String> expanding = new ArrayList<>();

    /** The current frame, null in a definition's body and at the top. */
    private Frame frame;

    /** The names bound by the binders around the expression being evaluated, in this body. */
    private Set<String> bound = new TreeSet<>();

    /** The names bound by every binder being evaluated, across bodies. */
    private final List<String> enclosing = new ArrayList<>();

    /**
     * The names occurring in each frame value, by identity. An identity map is used here only as a
     * traversal memo, so that a value shared by many frames is walked once; it holds no meaning.
     */
    private final Map<Expr, Set<String>> valueNames = new IdentityHashMap<>();

    /** The transitive names of each definition, by name: the names its body reaches. */
    private final Map<String, Reach> reach = new TreeMap<>();

    private int depth;

    /** Whether the statement is traced, and its expression: a command there keeps its trace. */
    private final boolean traced;

    private final Expr root;

    /** The trace of the algebra command at the top of the statement, or null. */
    private Trace commandTrace;

    State(Environment env, boolean traced, Expr root) {
      this.env = env;
      this.mc = env.settings().mathContext();
      this.traced = traced;
      this.root = root;
    }

    Expr canon(Expr e) {
      return Canonical.of(e, mc);
    }

    Expr eval(Expr e) {
      if (++depth > MAX_DEPTH) {
        throw new EvaluationException("expression nested too deeply", e);
      }
      try {
        return switch (e) {
          case Num _, Constant _, Bool _ -> e;
          case Lambda l -> closed(l);
          case Symbol s -> symbol(s);
          case Sum _, Product _, Power _ -> arithmetic(e);
          case Apply a -> apply(a);
          case Relation r -> relation(r);
          case ListExpr l -> Brackets.of(all(l.items()));
          case Vector v -> Brackets.of(all(v.items()));
          case Matrix m ->
              Brackets.of(m.rows().stream().map(row -> Brackets.of(all(row))).toList());
          case SetExpr s -> canon(new SetExpr(all(s.items())));
          case Interval _, Piecewise _ -> canon(Operands.rebuild(e, all(Operands.of(e))));
          case Derivative d -> binder(e, List.of(d.var()));
          case Integral i -> binder(e, List.of(i.var()));
          case Limit l -> binder(e, List.of(l.var()));
          case BigSum s -> binder(e, List.of(s.var()));
          case BigProduct p -> binder(e, List.of(p.var()));
        };
      } finally {
        depth--;
      }
    }

    private List<Expr> all(List<Expr> items) {
      List<Expr> out = new ArrayList<>(items.size());
      for (Expr item : items) {
        out.add(eval(item));
      }
      return out;
    }

    // Names.

    private Expr symbol(Symbol s) {
      String name = s.name();
      if (bound.contains(name)) {
        return s;
      }
      if (frame != null) {
        Expr value = frame.lookup(name);
        if (value != null) {
          return value;
        }
      }
      Optional<Definition> d = env.definition(name);
      if (d.isEmpty()) {
        return s;
      }
      return switch (d.get()) {
        case Definition.Value v -> expand(s, v.body());
        case Definition.Function _ ->
            throw new EvaluationException("a function is not a factor", s);
      };
    }

    /**
     * A lambda is held; in a frame, the frame's values are put into its body (capture-avoiding), so
     * the parameters of the call it was built in do not escape.
     */
    private Expr closed(Lambda l) {
      if (frame == null) {
        return l;
      }
      TreeMap<String, Expr> values = new TreeMap<>();
      for (Frame f = frame; f != null; f = f.parent()) {
        for (Map.Entry<String, Expr> entry : f.values().entrySet()) {
          if (!bound.contains(entry.getKey())) {
            values.putIfAbsent(entry.getKey(), entry.getValue());
          }
        }
      }
      return Substitution.apply(l, values);
    }

    /**
     * The value of a value definition, its body evaluated with no frame and no binder: memoised,
     * since the environment is fixed during a call.
     */
    private Expr expand(Symbol s, Expr body) {
      Expr known = memo.get(s.name());
      if (known != null) {
        return known;
      }
      push(s.name(), s);
      try {
        known = inFrame(null, new TreeSet<>(), body);
      } finally {
        expanding.removeLast();
      }
      checkSize(known, s);
      memo.put(s.name(), known);
      return known;
    }

    /** Evaluates {@code body} in another frame and binder scope, restoring both afterwards. */
    private Expr inFrame(Frame f, Set<String> names, Expr body) {
      Frame outerFrame = frame;
      Set<String> outerBound = bound;
      frame = f;
      bound = names;
      try {
        return eval(body);
      } finally {
        frame = outerFrame;
        bound = outerBound;
      }
    }

    private void push(String name, Expr node) {
      if (expanding.contains(name)) {
        throw new EvaluationException(name + " is defined in terms of itself", node);
      }
      if (expanding.size() >= MAX_EXPANSIONS) {
        throw new EvaluationException("definitions nested too deeply", node);
      }
      expanding.add(name);
    }

    // Arithmetic, relations.

    private Expr arithmetic(Expr e) {
      List<Expr> ops = all(Operands.of(e));
      for (Expr op : ops) {
        if (op instanceof Lambda) {
          throw new EvaluationException("a function is not a factor", e);
        }
        if (op instanceof SetExpr) {
          throw new EvaluationException("sets carry no arithmetic", e);
        }
        if (op instanceof ListExpr) {
          throw new EvaluationException("lists carry no arithmetic", e);
        }
        if (op instanceof Bool) {
          throw new EvaluationException("truth values carry no arithmetic", e);
        }
        if (op instanceof Constant k && isDomain(k)) {
          throw new EvaluationException("domains carry no arithmetic", e);
        }
      }
      return LinearAlgebra.reduce(canon(Operands.rebuild(e, ops)), mc, e);
    }

    private Expr relation(Relation r) {
      Expr c = canon(new Relation(r.op(), eval(r.left()), eval(r.right())));
      if (!(c instanceof Relation decided)) {
        return c;
      }
      switch (decided.op()) {
        case IN -> {
          if (!(decided.right() instanceof Constant k && isDomain(k))) {
            throw new EvaluationException("membership in a set is not supported yet", r);
          }
          if (decided.left() instanceof Num n) {
            return Bool.of(member(n, k));
          }
        }
        case LT, LE, GT, GE -> {
          if (isComplex(decided.left()) || isComplex(decided.right())) {
            throw new EvaluationException("complex numbers are not ordered", r);
          }
        }
        case EQ, NE -> {}
      }
      return c;
    }

    // Applications.

    private Expr apply(Apply a) {
      String name = a.head().name();
      Optional<Builtin> found = Builtins.lookup(name);
      if (found.isPresent()) {
        Builtin b = found.get();
        if (!b.accepts(a.args().size())) {
          throw new EvaluationException(name + " takes " + b.arityText(), a);
        }
        return switch (b.kind()) {
          case FUNCTION -> {
            List<Expr> args = all(a.args());
            if (args.contains(Constant.UNDEFINED)) {
              yield Constant.UNDEFINED;
            }
            Expr algebraic = algebraFunction(a, args);
            yield algebraic != null
                ? algebraic
                : Functions.apply(a.head(), args, mc, env.settings().angleMode(), a);
          }
          case COMMAND -> command(a, b);
          case RESERVED -> throw new EvaluationException(name + " is not available yet", a);
          case NOTATION -> throw new EvaluationException(name + " is not a function", a);
        };
      }
      if (env.definition(name).orElse(null) instanceof Definition.Function f) {
        return call(a, f);
      }
      throw new EvaluationException(name + " is not a function", a);
    }

    /** A call: the arguments evaluated here, the raw body evaluated in a frame of their values. */
    private Expr call(Apply a, Definition.Function f) {
      String name = a.head().name();
      int n = f.params().size();
      if (a.args().size() != n) {
        throw new EvaluationException(
            name + " takes " + n + (n == 1 ? " argument" : " arguments"), a);
      }
      List<Expr> values = all(a.args());
      TreeMap<String, Expr> params = new TreeMap<>();
      for (int k = 0; k < n; k++) {
        params.put(f.params().get(k).name(), values.get(k));
      }
      push(name, a);
      try {
        return inFrame(new Frame(params, null), new TreeSet<>(), f.body());
      } finally {
        expanding.removeLast();
      }
    }

    private Expr command(Apply a, Builtin b) {
      List<Expr> args = a.args();
      return switch (b.name()) {
        case "solve", "diff", "integrate", "limit", "sum", "product", "taylor" -> binder(a, b);
        case "expand", "factor", "collect", "cancel", "apart" -> algebra(a);
        case "simplify", "steps" -> eval(args.getFirst());
        case "exact" -> canon(NumericForm.exact(eval(args.getFirst()), a));
        case "numeric" -> numeric(a);
        case "subs" -> subs(a);
        case "assumptions" -> env.assumptions().relations();
        case "assume", "clear" ->
            throw new EvaluationException(b.name() + " works only as a statement", a);
        case "help" -> throw new EvaluationException("help is not available yet", a);
        default -> throw new IllegalStateException("no evaluation for the command " + b.name());
      };
    }

    // The algebra commands (§14.9).

    /**
     * The context of an algebra call: the assumptions, the precision, and the trace only when the
     * call is the statement itself, the one place its trace is kept.
     */
    private Context algebraContext(Apply a) {
      return new Context(env.assumptions(), mc, traced && a == root, REWRITE_CAP);
    }

    /**
     * {@code expand}, {@code factor}, {@code collect}, {@code cancel} and {@code apart}: the
     * algebra on the evaluated expression, a relation, list, set, vector or matrix item by item
     * (but for {@code apart}). The call stays as it is when the expression or an item is not a
     * polynomial in scalar kernels, or the algebra refuses it. A limit of the algebra is an
     * evaluation error at the call.
     */
    private Expr algebra(Apply a) {
      List<Expr> args = a.args();
      if (a.head().name().equals("apart") && !(args.get(1) instanceof Symbol)) {
        throw new EvaluationException("apart needs a variable", args.get(1));
      }
      Expr e = eval(args.getFirst());
      List<Expr> evaluated = new ArrayList<>(args);
      evaluated.set(0, e);
      Expr unevaluated = canon(new Apply(a.head(), evaluated));
      if (!scalarItems(e)) {
        return unevaluated;
      }
      Context ctx = algebraContext(a);
      try {
        return switch (a.head().name()) {
          case "expand" -> keepTrace(a, Algebra.expand(e, ctx));
          case "factor" -> Algebra.factor(e, ctx).map(r -> keepTrace(a, r)).orElse(unevaluated);
          case "collect" -> itemwise(e, item -> Algebra.collect(item, args.get(1), ctx));
          case "cancel" -> itemwise(e, item -> Algebra.cancel(item, ctx));
          case "apart" -> Algebra.apart(e, args.get(1), ctx).orElse(unevaluated);
          default -> throw new IllegalStateException("not an algebra command: " + a.head());
        };
      } catch (AlgebraLimitException limit) {
        throw new EvaluationException(limit.getMessage(), a);
      } catch (RewriteLimitException ex) {
        throw new EvaluationException(
            "simplification did not finish (rule set " + ex.ruleSet() + ", rule " + ex.rule() + ")",
            a);
      }
    }

    /**
     * Applies {@code f} to each item of a relation, list, set, vector or matrix (entry by entry,
     * the rows rebuilt), or to {@code e}.
     */
    private Expr itemwise(Expr e, UnaryOperator<Expr> f) {
      if (!isContainer(e)) {
        return f.apply(e);
      }
      List<Expr> items = new ArrayList<>();
      for (Expr item : Operands.of(e)) {
        items.add(itemwise(item, f));
      }
      return canon(Operands.rebuild(e, items));
    }

    /** The value of an algebra result, its trace kept when the call is the statement itself. */
    private Expr keepTrace(Apply a, Rewriter.Result r) {
      if (traced && a == root) {
        commandTrace = r.trace();
      }
      return r.expr();
    }

    /**
     * {@code gcd} and {@code lcm} of polynomials, and the structural {@code numerator} and {@code
     * denominator}; null when the tower's numbers answer ({@link Functions}).
     */
    private Expr algebraFunction(Apply a, List<Expr> args) {
      String name = a.head().name();
      switch (name) {
        case "gcd", "lcm" -> {
          boolean integers =
              args.stream().allMatch(x -> x instanceof Num n && n.value() instanceof Int);
          boolean inexact = args.stream().anyMatch(x -> x instanceof Num n && !n.value().isExact());
          if (integers || inexact) {
            return null;
          }
          try {
            Context ctx = algebraContext(a);
            Optional<Rewriter.Result> r =
                name.equals("gcd") ? Algebra.gcd(args, ctx) : Algebra.lcm(args, ctx);
            return r.map(x -> keepTrace(a, x)).orElseGet(() -> canon(new Apply(a.head(), args)));
          } catch (AlgebraLimitException limit) {
            throw new EvaluationException(limit.getMessage(), a);
          }
        }
        case "numerator", "denominator" -> {
          Expr x = args.getFirst();
          if (x instanceof Num) {
            return null;
          }
          if (!Polynomial.isScalar(x)) {
            return canon(new Apply(a.head(), args));
          }
          return canon(fraction(x)[name.equals("numerator") ? 0 : 1]);
        }
        default -> {
          return null;
        }
      }
    }

    private Expr numeric(Apply a) {
      int digits = env.settings().precision();
      if (a.args().size() == 2) {
        Expr d = eval(a.args().get(1));
        if (!(d instanceof Num n
            && n.value() instanceof Int k
            && k.value().bitLength() < 32
            && Settings.isPrecision(k.value().intValue()))) {
          throw new EvaluationException(
              "the number of digits must be an integer from "
                  + Settings.MIN_PRECISION
                  + " to "
                  + Settings.MAX_PRECISION,
              a.args().get(1));
        }
        digits = k.value().intValue();
      }
      MathContext outer = mc;
      TreeMap<String, Expr> outerMemo = memo;
      mc = new MathContext(digits, RoundingMode.HALF_EVEN);
      memo = new TreeMap<>();
      try {
        return NumericForm.numeric(eval(a.args().getFirst()), digits, a);
      } finally {
        mc = outer;
        memo = outerMemo;
      }
    }

    /**
     * {@code subs(e, x = v, …)}: the values evaluated here, then the held {@code e} evaluated in a
     * frame of the names, inside the current one; the names are free in {@code e}, so the binders
     * around the call do not bind them there.
     */
    private Expr subs(Apply a) {
      TreeMap<String, Expr> values = new TreeMap<>();
      for (Expr pair : a.args().subList(1, a.args().size())) {
        if (!(pair instanceof Relation r
            && r.op() == Relation.Op.EQ
            && r.left() instanceof Symbol name)) {
          throw new EvaluationException("subs needs name = value", pair);
        }
        values.put(name.name(), eval(r.right()));
      }
      Set<String> names = new TreeSet<>(bound);
      names.removeAll(values.keySet());
      return inFrame(new Frame(values, frame), names, a.args().getFirst());
    }

    // Binders.

    /**
     * An unevaluated form: its body evaluated in the scope of its variable, its bounds, point and
     * order as they are.
     */
    private Expr binder(Expr node, List<Symbol> vars) {
      Expr current = node;
      List<Symbol> names = new ArrayList<>(vars);
      for (int k = 0; k < names.size(); k++) {
        Symbol fresh = captureFree(current, names.get(k), List.of(Operands.of(current).getFirst()));
        if (fresh != null) {
          current = Substitution.renameBinder(current, names.get(k).name(), fresh);
          names.set(k, fresh);
        }
      }
      List<Expr> ops = Operands.of(current);
      List<Expr> out = new ArrayList<>(ops.size());
      for (int k = 0; k < ops.size(); k++) {
        Expr op = ops.get(k);
        if (Operands.isBound(current, k)) {
          out.add(op);
        } else if (k == 0) {
          out.add(scoped(op, names));
        } else {
          out.add(eval(op));
        }
      }
      return canon(Operands.rebuild(current, out));
    }

    /**
     * A command whose implementation is a later brief's: its held arguments in the variable's scope
     * (the first two) evaluated in that scope, its other held arguments as parsed, its other
     * arguments evaluated; the call is built again as the parser would, so arguments that come to
     * fit give the typed node ({@code diff(f, x, n)} with {@code n := 2}).
     */
    private Expr binder(Apply a, Builtin b) {
      int variable = b.binder().orElse(0) - 1;
      Apply current = a;
      List<Symbol> names = new ArrayList<>(Substitution.boundBy(a));
      for (int k = 0; k < names.size(); k++) {
        Symbol fresh = captureFree(current, names.get(k), scopeArgs(current, variable));
        if (fresh != null) {
          current = (Apply) Substitution.renameBinder(current, names.get(k).name(), fresh);
          names.set(k, fresh);
        }
      }
      List<Expr> args = current.args();
      List<Expr> out = new ArrayList<>(args.size());
      for (int k = 0; k < args.size(); k++) {
        Expr arg = args.get(k);
        if (k == variable) {
          out.add(arg);
        } else if (b.isHeld(k + 1)) {
          out.add(k < 2 ? scoped(arg, names) : arg);
        } else {
          out.add(eval(arg));
        }
      }
      return canon(TypedNodes.of(new Apply(a.head(), out)));
    }

    private static List<Expr> scopeArgs(Apply a, int variable) {
      List<Expr> out = new ArrayList<>();
      for (int k = 0; k < Math.min(2, a.args().size()); k++) {
        if (k != variable) {
          out.add(a.args().get(k));
        }
      }
      return out;
    }

    /** Evaluates a binder's scope with its variables bound. */
    private Expr scoped(Expr e, List<Symbol> vars) {
      Set<String> outer = bound;
      bound = new TreeSet<>(outer);
      for (Symbol v : vars) {
        bound.add(v.name());
        enclosing.add(v.name());
      }
      try {
        return eval(e);
      } finally {
        for (int k = 0; k < vars.size(); k++) {
          enclosing.removeLast();
        }
        bound = outer;
      }
    }

    /**
     * The static capture check, made once before a binder's scope is evaluated. The variable {@code
     * v} could capture a name from outside the scope when a value of the current frames holds a
     * symbol named {@code v}, or a definition the scope reaches through the environment (its
     * symbols and heads, and theirs, transitively) holds one in its body, a name bound to a value
     * definition excepted, since it is always expanded, unless it occurs in a lambda's body, which
     * is held and keeps it free. The check is conservative: it looks at every occurrence, free or
     * bound. The fresh name is {@code base_k}, the base being the variable's name without a {@code
     * _digits} suffix, for the least {@code k} that occurs in none of the sets the check looked at,
     * the binder, the enclosing binders and the environment's definitions.
     *
     * @return the fresh variable to rename the binder to, or null when no capture is possible
     */
    private Symbol captureFree(Expr binder, Symbol v, List<Expr> scope) {
      Set<String> reached = new TreeSet<>();
      Set<String> held = new TreeSet<>();
      for (Expr e : scope) {
        held.addAll(namesUnderLambda(e));
        for (String name : names(e)) {
          if (!name.equals(v.name())) {
            Reach r = reach(name);
            reached.addAll(r.names());
            held.addAll(r.underLambda());
          }
        }
      }
      // A name bound to a value definition is expanded, never free, so it cannot be captured
      // (its own reach is already in the set), unless it occurs in a lambda's body, which is held
      // and keeps the name free.
      reached.removeIf(
          name ->
              env.definition(name).orElse(null) instanceof Definition.Value
                  && !held.contains(name));
      Set<String> framed = frameNames();
      if (!reached.contains(v.name()) && !framed.contains(v.name())) {
        return null;
      }
      String base = v.name().replaceFirst("_[0-9]+$", "");
      for (int k = 1; ; k++) {
        String name = base + "_" + k;
        if (!reached.contains(name)
            && !framed.contains(name)
            && !bound.contains(name)
            && !enclosing.contains(name)
            && env.definition(name).isEmpty()
            && !Substitution.occurs(binder, name)) {
          return Symbol.of(name);
        }
      }
    }

    /** The names occurring in the values of the current frames. */
    private Set<String> frameNames() {
      Set<String> out = new TreeSet<>();
      for (Frame f = frame; f != null; f = f.parent()) {
        for (Expr value : f.values().values()) {
          out.addAll(valueNames.computeIfAbsent(value, Evaluator::names));
        }
      }
      return out;
    }

    /**
     * The transitive names of a name: for a definition, the names occurring in its raw body
     * (without a function's own parameters) and, transitively, in the bodies of the definitions
     * those name; for any other name, nothing. Memoised per call; a cycle among the definitions is
     * harmless here and remains the evaluation-time error.
     */
    private Reach reach(String name) {
      Reach known = reach.get(name);
      if (known != null) {
        return known;
      }
      Set<String> out = new TreeSet<>();
      Set<String> underLambda = new TreeSet<>();
      Set<String> visited = new TreeSet<>();
      Deque<String> pending = new ArrayDeque<>();
      pending.push(name);
      while (!pending.isEmpty()) {
        String next = pending.pop();
        if (!visited.add(next)) {
          continue;
        }
        Definition d = env.definition(next).orElse(null);
        if (d == null) {
          continue;
        }
        Set<String> body = names(d.body());
        Set<String> held = namesUnderLambda(d.body());
        if (d instanceof Definition.Function f) {
          f.params().forEach(p -> body.remove(p.name()));
          f.params().forEach(p -> held.remove(p.name()));
        }
        underLambda.addAll(held);
        for (String n : body) {
          out.add(n);
          pending.push(n);
        }
      }
      Reach r = new Reach(out, underLambda);
      reach.put(name, r);
      return r;
    }
  }

  /**
   * What a name reaches through the environment.
   *
   * @param names the names in the bodies reached
   * @param underLambda those of them that occur in a lambda's body, held and so left free
   */
  private record Reach(Set<String> names, Set<String> underLambda) {}

  /** The names occurring in the bodies of the lambdas in a tree. */
  static Set<String> namesUnderLambda(Expr e) {
    Set<String> out = new TreeSet<>();
    Set<Expr> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    Deque<Expr> nodes = new ArrayDeque<>();
    nodes.push(e);
    while (!nodes.isEmpty()) {
      Expr n = nodes.pop();
      if (!visited.add(n)) {
        continue;
      }
      if (n instanceof Lambda l) {
        out.addAll(names(l.body()));
        continue;
      }
      for (Expr op : Operands.of(n)) {
        nodes.push(op);
      }
    }
    return out;
  }

  /**
   * The names occurring in a tree: its symbols and the heads of its applications. The walk keeps an
   * identity set of the nodes visited, a traversal memo only, so a shared subtree is walked once.
   */
  static Set<String> names(Expr e) {
    Set<String> out = new TreeSet<>();
    Set<Expr> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    Deque<Expr> nodes = new ArrayDeque<>();
    nodes.push(e);
    while (!nodes.isEmpty()) {
      Expr n = nodes.pop();
      if (!visited.add(n)) {
        continue;
      }
      if (n instanceof Symbol s) {
        out.add(s.name());
      } else if (n instanceof Apply a) {
        out.add(a.head().name());
      }
      for (Expr op : Operands.of(n)) {
        nodes.push(op);
      }
    }
    return out;
  }

  /** A relation, list, set, vector or matrix: the algebra commands take its items one by one. */
  private static boolean isContainer(Expr e) {
    return e instanceof Relation
        || e instanceof ListExpr
        || e instanceof SetExpr
        || e instanceof Vector
        || e instanceof Matrix;
  }

  /** Whether every item of {@code e} (threading relations and collections) is scalar. */
  private static boolean scalarItems(Expr e) {
    if (isContainer(e)) {
      return Operands.of(e).stream().allMatch(Evaluator::scalarItems);
    }
    return Polynomial.isScalar(e);
  }

  /**
   * The structural numerator and denominator of a canonical value: of a product, the factors with
   * non-negative exponents (the coefficient's numerator among them) and the bases of the others to
   * their positive exponents (with the coefficient's denominator); of a negative power {@code
   * b^-n}, 1 and {@code b^n}; of anything else, the value and 1.
   */
  static Expr[] fraction(Expr e) {
    List<Expr> factors = e instanceof Product p ? p.factors() : List.of(e);
    List<Expr> num = new ArrayList<>();
    List<Expr> den = new ArrayList<>();
    for (Expr f : factors) {
      if (f instanceof Num n && n.value() instanceof Rational q) {
        num.add(new Num(q.numerator()));
        den.add(new Num(q.denominator()));
      } else if (f instanceof Power w
          && w.exponent() instanceof Num n
          && n.value() instanceof RealNumeric r
          && r.signum() < 0) {
        den.add(new Power(w.base(), new Num(r.negate())));
      } else {
        num.add(f);
      }
    }
    return new Expr[] {product(num), product(den)};
  }

  private static Expr product(List<Expr> factors) {
    return switch (factors.size()) {
      case 0 -> Num.ONE;
      case 1 -> factors.getFirst();
      default -> new Product(factors);
    };
  }

  private static boolean isDomain(Constant k) {
    return switch (k.kind()) {
      case INTEGERS, RATIONALS, REALS, COMPLEXES -> true;
      case PI, E, INFINITY, UNDEFINED -> false;
    };
  }

  /** Membership of a number in a domain, by value: {@code 2.0 ∈ ℤ}, a decimal is rational. */
  private static boolean member(Num n, Constant domain) {
    boolean real = n.value().im().isZero();
    return switch (domain.kind()) {
      case INTEGERS -> real && n.value().re().isInteger();
      case RATIONALS, REALS -> real;
      case COMPLEXES -> true;
      case PI, E, INFINITY, UNDEFINED -> throw new IllegalArgumentException("not a domain");
    };
  }

  private static boolean isComplex(Expr e) {
    return e instanceof Num n && n.value() instanceof Complex;
  }
}
