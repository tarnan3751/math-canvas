package mathcanvas.engine.eval;

import java.math.BigInteger;
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
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.algebra.AlgebraLimitException;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.builtin.Builtin;
import mathcanvas.engine.builtin.Builtins;
import mathcanvas.engine.calculus.Applications;
import mathcanvas.engine.calculus.Calculus;
import mathcanvas.engine.calculus.Limits;
import mathcanvas.engine.calculus.TaylorException;
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
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.parse.Brackets;
import mathcanvas.engine.parse.Lexer;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.parse.TypedNodes;
import mathcanvas.engine.provider.Providers;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.RewriteLimitException;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.RuleSet;
import mathcanvas.engine.rule.Trace;
import mathcanvas.engine.simplify.Simplifier;
import mathcanvas.engine.solve.Solver;

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
 * {@code v_k} first, once. A derivative is then computed ({@link Calculus#differentiate}); its
 * variable is free in the result, which takes the variable's value where it has one. The value is
 * then simplified with {@link Simplifier#standard()}, once, under a rewrite cap that grows with its
 * size (with {@link Simplifier#forExpandedForms()} when the statement is a call of {@code expand},
 * {@code apart} or {@code collect}, whose expanded form the full list could undo); a statement that
 * is one derivative (or {@code steps} of one) carries the derivative's trace before the
 * simplifier's.
 *
 * <p>Evaluation is pure: no I/O, no clock, no randomness. It is bounded: at most {@link #MAX_DEPTH}
 * nested evaluation steps (operands and expansions together), {@link #MAX_EXPANSIONS} nested
 * definitions, a statement's value and each definition's value of at most {@link #MAX_NODES} nodes,
 * and a value at most {@link Parser#MAX_NESTING} levels deep, the bound every later walk of a tree
 * relies on. An {@code ArithmeticException} from the number tower or the canonical form (a result
 * beyond {@code BigDecimal}'s range) is the error "number out of range at this precision". It can
 * be stopped: a {@link Cancellation} is checked at every evaluation step and carried by every
 * context it builds, and a request to stop throws {@code CancelledException} out of it.
 */
public final class Evaluator {

  /** The most terms of sums and products one statement takes one by one, nested ones included. */
  static final long TERM_BUDGET = 100_000;

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
   * @param numeric whether a definite integral in the statement was evaluated by quadrature, so the
   *     value holds a number that no closed form gave ({@code docs/language.md} §10.2)
   */
  public record Result(Expr value, Trace trace, boolean numeric) {

    /**
     * A value no quadrature gave.
     *
     * @param value the canonical value
     * @param trace the trace
     */
    public Result(Expr value, Trace trace) {
      this(value, trace, false);
    }
  }

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
    return evaluate(raw, env, trace, Cancellation.NEVER);
  }

  /**
   * Evaluates a parsed expression, checking {@code cancellation} at every evaluation step and in
   * every operation the statement runs.
   *
   * @param raw the expression, as parsed
   * @param env the environment
   * @param trace whether to record the simplifier's rewrites
   * @param cancellation the request to stop
   * @return the value and its trace
   * @throws EvaluationException for an evaluation error of §16
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Result evaluate(
      Expr raw, Environment env, boolean trace, Cancellation cancellation) {
    Expr e = raw;
    boolean traced = trace;
    if (raw instanceof Apply a && a.head().name().equals("steps") && a.args().size() == 1) {
      e = a.args().getFirst();
      traced = true;
    }
    try {
      State state = new State(env, traced, e, cancellation);
      Expr value = state.eval(e);
      long nodes = checkSize(value, raw, true);
      Context context =
          new Context(
                  env.assumptions(),
                  env.settings().mathContext(),
                  traced,
                  Math.toIntExact(REWRITE_CAP + REWRITE_CAP_PER_NODE * nodes))
              .withNumerics(
                  new SessionNumerics(env.settings().angleMode().halfTurn(), cancellation))
              .withCancellation(cancellation);
      Rewriter.Result r;
      try {
        r = Simplifier.simplify(value, rulesFor(e), context);
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
      return new Result(r.expr(), steps, state.quadrature);
    } catch (ArithmeticException outOfRange) {
      throw new EvaluationException(OUT_OF_RANGE, raw);
    }
  }

  /** The commands whose value is an expanded form, which the rational set would undo. */
  private static final Set<String> EXPANDED_FORMS = Set.of("expand", "apart", "collect");

  /**
   * The rule sets a statement's value is simplified with: {@link Simplifier#forExpandedForms()}
   * when the statement is a call of a command that produces an expanded form, else the standard
   * list.
   */
  private static List<RuleSet> rulesFor(Expr statement) {
    return statement instanceof Apply a && EXPANDED_FORMS.contains(a.head().name())
        ? Simplifier.forExpandedForms()
        : Simplifier.standard();
  }

  /**
   * Records the relations of an {@code assume} statement: each relation's left side is held, its
   * right side evaluated.
   *
   * @param call the {@code assume(…)} application, as parsed
   * @param env the environment
   * @param cancellation the request to stop
   * @return the new assumptions and the relations recorded
   * @throws EvaluationException if an argument is not a relation, or {@link Assumptions#with}
   *     rejects it
   */
  static Assumed assume(Apply call, Environment env, Cancellation cancellation) {
    State state = new State(env, false, null, cancellation);
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
    return checkSize(value, node, false);
  }

  /**
   * {@link #checkSize(Expr, Expr)}, and for a value that will be shown, "number too large to
   * display" when an exact integer in it has more digits than the parser reads ({@link
   * Lexer#MAX_DIGITS}), or a rational's numerator or denominator more than {@link
   * Canonical#MAX_FOLD_DIGITS} (a complex number's parts included), so every value shown reads back
   * in reasonable time. Intermediate values are not checked.
   *
   * @return the number of nodes, at most {@link #MAX_NODES}
   */
  static long checkSize(Expr value, Expr node, boolean shown) {
    long count = nodes(value, MAX_NODES, shown ? node : null);
    if (count > MAX_NODES) {
      throw new EvaluationException("result too large", node);
    }
    return count;
  }

  /**
   * The nodes of a tree, counting a shared subtree once per occurrence, as every walk of the tree
   * will; the walk stops past {@code limit}.
   *
   * @return the count, or {@code limit + 1} when the tree has more nodes than {@code limit}
   */
  static long nodes(Expr value, long limit) {
    return nodes(value, limit, null);
  }

  /** {@link #nodes(Expr, long)}, checking each number is displayable when {@code shown} is set. */
  private static long nodes(Expr value, long limit, Expr shown) {
    long count = 0;
    Deque<Expr> nodes = new ArrayDeque<>();
    nodes.push(value);
    while (!nodes.isEmpty()) {
      if (++count > limit) {
        return count;
      }
      Expr e = nodes.pop();
      if (shown != null && e instanceof Num n && !displayable(n.value())) {
        throw new EvaluationException("number too large to display", shown);
      }
      for (Expr op : Operands.of(e)) {
        nodes.push(op);
      }
    }
    return count;
  }

  /**
   * Whether an exact number reads back cheaply: an integer of at most {@link Lexer#MAX_DIGITS}
   * digits, or a rational whose numerator and denominator each have at most {@link
   * Canonical#MAX_FOLD_DIGITS}, since reading a fraction back costs the gcd of its terms, which is
   * quadratic in their length.
   */
  private static boolean displayable(Numeric x) {
    return switch (x) {
      case Int i -> within(i.value(), Lexer.MAX_DIGITS, () -> IntegerBound.VALUE);
      case Rational q ->
          within(q.num(), Canonical.MAX_FOLD_DIGITS, () -> FractionBound.VALUE)
              && within(q.den(), Canonical.MAX_FOLD_DIGITS, () -> FractionBound.VALUE);
      case Real _ -> true;
      case Complex c -> displayable(c.re()) && displayable(c.im());
    };
  }

  /**
   * Whether an integer has at most {@code digits} digits, that is {@code |v| < 10^digits}. Below
   * {@code 3·digits} bits it does, since {@code 2³ < 10}; only a longer integer is compared with
   * the bound, {@code 10^digits}.
   */
  private static boolean within(BigInteger v, int digits, Supplier<BigInteger> bound) {
    return v.bitLength() <= 3L * digits || v.abs().compareTo(bound.get()) < 0;
  }

  /** {@code 10^MAX_DIGITS}, computed when a longer integer first needs it. */
  private static final class IntegerBound {
    static final BigInteger VALUE = BigInteger.TEN.pow(Lexer.MAX_DIGITS);
  }

  /** {@code 10^MAX_FOLD_DIGITS}, computed when a longer fraction term first needs it. */
  private static final class FractionBound {
    static final BigInteger VALUE = BigInteger.TEN.pow(Canonical.MAX_FOLD_DIGITS);
  }

  /** A term-by-term evaluation given up, unwinding to the outermost one. */
  private static final class Abandoned extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String reason;

    Abandoned(String reason) {
      super(null, null, false, false);
      this.reason = reason;
    }
  }

  /**
   * The parameters of one call, the names of one {@code subs}, or the index of one term of a sum or
   * product: name to value. A call's frame has no parent (static scoping); a {@code subs} or term
   * frame's parent is the frame it is evaluated in.
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

    /** The request to stop, checked at every evaluation step and passed to every operation. */
    private final Cancellation cancellation;

    /** Whether the statement is traced, and its expression: a command there keeps its trace. */
    private final boolean traced;

    private final Expr root;

    /** The trace of the algebra command or derivative at the top of the statement, or null. */
    private Trace commandTrace;

    /** The terms the statement may still take one by one, over all its sums and products. */
    private long termsLeft = TERM_BUDGET;

    /** How many term-by-term evaluations are open, one inside another. */
    private int termsNesting;

    /** Why the last term-by-term evaluation was given up, for its note; null when it was not. */
    private String declined;

    /**
     * Whether a derivative's result is being folded: every symbol is itself, and functions take
     * their arguments in radians, so nothing evaluated before is looked up or converted again.
     */
    private boolean folding;

    /**
     * The digits a definite integral the exact path leaves is evaluated to by quadrature: the
     * settings' outside {@code numeric}, its digits inside; 0 to leave it.
     */
    private int quadratureDigits;

    /** Whether a definite integral was evaluated by quadrature in this statement. */
    private boolean quadrature;

    State(Environment env, boolean traced, Expr root, Cancellation cancellation) {
      this.env = env;
      this.cancellation = cancellation;
      this.mc = env.settings().mathContext();
      this.quadratureDigits = env.settings().quadratureDigits();
      this.traced = traced;
      this.root = root;
    }

    Expr canon(Expr e) {
      return Canonical.of(e, mc, cancellation);
    }

    Expr eval(Expr e) {
      cancellation.check();
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
          case Derivative d -> differentiated(binder(e, List.of(d.var())), d.var(), e);
          case Integral i ->
              numericIntegral(integrated(binder(e, List.of(i.var())), i.var(), e), e);
          case Limit l -> limit(binder(e, List.of(l.var())), e);
          case BigSum s -> sum(binder(e, List.of(s.var())), e);
          case BigProduct p -> sum(binder(e, List.of(p.var())), e);
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
      if (folding || bound.contains(name)) {
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
      checkSize(known, s, true);
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
      return LinearAlgebra.reduce(canon(Operands.rebuild(e, ops)), mc, cancellation, e);
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
                : Functions.apply(
                    a.head(),
                    args,
                    mc,
                    folding ? AngleMode.RADIANS : env.settings().angleMode(),
                    a,
                    cancellation);
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
        case "diff" -> differentiated(binder(a, b), args.get(1), a);
        case "solve" -> solve(a, b);
        case "integrate" -> numericIntegral(integrated(binder(a, b), args.get(1), a), a);
        case "arclength", "area" -> numericIntegral(applied(binder(a, b), b, a), a);
        case "taylor" -> taylor(binder(a, b), a);
        case "limit" -> limit(binder(a, b), a);
        case "sum", "product" -> sum(binder(a, b), a);
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

    /**
     * {@code solve(equations, variables)}: the equations evaluated in the scope of the unknowns,
     * then solved ({@link Solver}) under the assumptions without facts about the unknowns. An
     * unknown the capture check renamed is renamed back to its written name in a set of solutions,
     * unless that name occurs there free, from outside; the solutions are not evaluated again.
     */
    private Expr solve(Apply a, Builtin b) {
      Apply current = a;
      List<Symbol> original = Substitution.boundBy(a);
      List<Symbol> names = new ArrayList<>(original);
      for (int k = 0; k < names.size(); k++) {
        Symbol fresh = captureFree(current, names.get(k), scopeArgs(current, 1));
        if (fresh != null) {
          current = (Apply) Substitution.renameBinder(current, names.get(k).name(), fresh);
          names.set(k, fresh);
        }
      }
      Expr equations = scoped(current.args().get(0), names);
      Assumptions facts = env.assumptions();
      for (Symbol v : names) {
        facts = facts.without(v);
      }
      Context ctx =
          new Context(
              facts,
              mc,
              traced && a == root,
              Math.toIntExact(REWRITE_CAP + REWRITE_CAP_PER_NODE * checkSize(equations, a)),
              Providers.defaults(),
              new SessionNumerics(env.settings().angleMode().halfTurn(), cancellation),
              cancellation);
      Rewriter.Result r;
      try {
        r = Solver.solve(equations, current.args().get(1), ctx);
      } catch (IllegalArgumentException wrongKind) {
        throw new EvaluationException(wrongKind.getMessage(), a);
      } catch (AlgebraLimitException limit) {
        throw new EvaluationException(limit.getMessage(), a);
      } catch (RewriteLimitException ex) {
        throw new EvaluationException(
            "simplification did not finish (rule set " + ex.ruleSet() + ", rule " + ex.rule() + ")",
            a);
      }
      Expr value = r.expr();
      boolean renamed = false;
      if (value instanceof SetExpr) {
        for (int k = 0; k < names.size(); k++) {
          // the written name, unless it occurs free in the solutions: then the fresh one stays
          if (!names.get(k).equals(original.get(k)) && !occurs(value, original.get(k))) {
            value = Substitution.rename(value, names.get(k).name(), original.get(k));
            renamed = true;
          }
        }
      }
      if (ctx.traceEnabled() && !renamed) {
        commandTrace = r.trace();
      }
      return canon(value);
    }

    /** Whether the symbol {@code s} occurs in {@code e}. */
    private static boolean occurs(Expr e, Symbol s) {
      Deque<Expr> todo = new ArrayDeque<>();
      todo.push(e);
      while (!todo.isEmpty()) {
        Expr n = todo.pop();
        if (n.equals(s)) {
          return true;
        }
        for (Expr op : Operands.of(n)) {
          todo.push(op);
        }
      }
      return false;
    }

    // The algebra commands (§14.9).

    /**
     * The context of an algebra call: the assumptions, the precision, the trace only when the call
     * is the statement itself, the one place its trace is kept, and the rewrite cap a statement's
     * simplification has, {@link #REWRITE_CAP} plus {@link #REWRITE_CAP_PER_NODE} per node, here of
     * the call's arguments.
     */
    private Context algebraContext(Apply a, List<Expr> args) {
      long nodes = 0;
      for (Expr arg : args) {
        nodes += checkSize(arg, a);
      }
      long cap = REWRITE_CAP + REWRITE_CAP_PER_NODE * nodes;
      return new Context(
              env.assumptions(), mc, traced && a == root, (int) Math.min(cap, Integer.MAX_VALUE))
          .withCancellation(cancellation);
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
      Context ctx = algebraContext(a, List.of(e));
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
     * {@code gcd} and {@code lcm} of polynomials (or of exact numbers that are not all integers),
     * the call itself when an exact number stands beside a non-number, and the structural {@code
     * numerator} and {@code denominator}; null when the tower's numbers answer ({@link Functions}).
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
          // An exact number beside a non-number stays: gcd(x, 6) is the integer gcd at each integer
          // x, not the polynomial gcd 1, and gcd(2x, 4) is not 2. Two identities hold for integers
          // and polynomials alike, signs included, where every number is an integer: gcd(u, ±1) = 1
          // and lcm(u, 0) = 0. (A rational beside them makes it the gcd of contents: gcd(4, 1, 1/2)
          // is 1/2. gcd(u, 0) is |u| for an integer and stays, as lcm(u, ±1) does.)
          boolean numbers = args.stream().anyMatch(x -> x instanceof Num);
          boolean others = args.stream().anyMatch(x -> !(x instanceof Num));
          if (numbers && others) {
            boolean gcd = name.equals("gcd");
            boolean integerNumbers =
                args.stream().allMatch(x -> !(x instanceof Num n) || n.value() instanceof Int);
            boolean absorbing =
                integerNumbers
                    && args.stream()
                        .anyMatch(
                            x ->
                                x instanceof Num n
                                    && n.value() instanceof Int i
                                    && (gcd ? i.value().abs().equals(BigInteger.ONE) : i.isZero()));
            return absorbing ? (gcd ? Num.ONE : Num.ZERO) : canon(new Apply(a.head(), args));
          }
          try {
            Context ctx = algebraContext(a, args);
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
      int outerQuadrature = quadratureDigits;
      mc = new MathContext(digits, RoundingMode.HALF_EVEN);
      memo = new TreeMap<>();
      quadratureDigits = digits;
      try {
        Expr inner = eval(a.args().getFirst());
        Expr value = NumericForm.numeric(inner, digits, a, cancellation);
        // a command's steps at the top of numeric(…) end at its numeric form
        if (commandTrace != null && commandTrace.result().equals(inner) && !value.equals(inner)) {
          List<Trace.Normalisation> ns = new ArrayList<>(commandTrace.normalisations());
          ns.add(new Trace.Normalisation(commandTrace.rewrites().size(), Path.ROOT, value));
          commandTrace = new Trace(commandTrace.initial(), value, commandTrace.rewrites(), ns);
        }
        return value;
      } finally {
        mc = outer;
        memo = outerMemo;
        quadratureDigits = outerQuadrature;
      }
    }

    /**
     * A definite integral's value through the numeric path ({@link NumericIntegral}) when the
     * quadrature digits are set; its step is kept when the integral is the statement, or the
     * argument of a {@code numeric} that is.
     */
    private Expr numericIntegral(Expr value, Expr node) {
      if (quadratureDigits == 0) {
        return value;
      }
      boolean top =
          node == root
              || root instanceof Apply r
                  && r.head().name().equals("numeric")
                  && r.args().getFirst() == node;
      NumericIntegral.Outcome out =
          NumericIntegral.of(
              value,
              quadratureDigits,
              env.settings().angleMode() == AngleMode.RADIANS,
              traced && top,
              node,
              cancellation);
      // only a value the quadrature gave: an exact one the exact path gave is not marked
      quadrature |= out.numeric();
      if (out.step() != null) {
        commandTrace =
            commandTrace != null && commandTrace.result().equals(value)
                ? Trace.concat(commandTrace, out.step())
                : out.step();
      }
      return out.value();
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

    // Derivatives.

    /**
     * The value of a derivative whose scope has been evaluated: {@link Calculus#differentiate} of a
     * {@code Derivative} node, anything else (a {@code diff} call whose order is not a positive
     * integer) as it is. The derivative's variable is free in its result ({@code docs/language.md}
     * §14.10): when the name as written has a value here (a parameter of the current frame or a
     * value definition, outside any binder of it), the value is put in for the variable under its
     * current name, so {@code f(x) := diff(x^3, x)} gives {@code f(2) = 12}; then a fresh name the
     * capture check gave the variable is renamed back to the name as written, unless that name
     * occurs free in the result, where the fresh name stays ({@code f(y) := diff(x y + x^2, x)}
     * gives {@code f(x) = x + 2x₁}). The result is then folded: the functions the rules introduced
     * are evaluated on numbers ({@code log(2.0)}), as evaluation would, without looking anything up
     * again or converting an angle twice.
     *
     * @param node the node the binder evaluation built
     * @param written the variable as written, which may not be a symbol in a {@code diff} call
     * @param typed the derivative or the {@code diff} call as typed, which keeps its trace when it
     *     is the statement
     */
    private Expr differentiated(Expr node, Expr written, Expr typed) {
      if (folding || !(node instanceof Derivative d)) {
        // A derivative left in a result being folded is one no rule could compute.
        return node;
      }
      Assumptions facts = env.assumptions();
      for (String name : bound) {
        facts = facts.without(Symbol.of(name));
      }
      long nodes = checkSize(d, d);
      boolean whole = traced && typed == root;
      Context context =
          new Context(facts, mc, whole, Math.toIntExact(REWRITE_CAP + REWRITE_CAP_PER_NODE * nodes))
              .withCancellation(cancellation);
      Rewriter.Result r;
      try {
        r = Calculus.differentiate(d.expr(), d.var(), d.order(), context);
      } catch (RewriteLimitException ex) {
        throw new EvaluationException(
            "differentiation did not finish (rule set "
                + ex.ruleSet()
                + ", rule "
                + ex.rule()
                + ")",
            node);
      }
      Symbol v = d.var();
      Expr result = freeVariable(r.expr(), v, written instanceof Symbol s ? s : v);
      if (whole) {
        commandTrace = r.trace();
      }
      return result;
    }

    /**
     * A command's result in which its variable {@code v}, written {@code x}, is free: the value of
     * {@code x} where the command stands put in for {@code v}, a fresh {@code v} renamed back to
     * {@code x} unless that would merge it with an {@code x} from outside, then the result folded.
     */
    private Expr freeVariable(Expr result, Symbol v, Symbol x) {
      // The value first, under the variable's current name, so it never reaches a free x that
      // came in through another value; then a fresh name back to the written one, unless that
      // would merge it with such an x.
      if (hasValue(x)) {
        result = Substitution.apply(result, new TreeMap<>(Map.of(v.name(), eval(x))));
      }
      if (!x.equals(v) && !occursFree(x, result)) {
        result = Substitution.apply(result, new TreeMap<>(Map.of(v.name(), x)));
      }
      boolean outer = folding;
      folding = true;
      try {
        return inFrame(null, bound, result);
      } finally {
        folding = outer;
      }
    }

    // Integrals.

    /**
     * The value of an integral whose scope has been evaluated: {@link Calculus#integrate} of an
     * {@code Integral} node through the providers, anything else (an {@code integrate} call whose
     * variable is not a name) as it is. The context is the solver's: the facts without those about
     * the enclosing binders' names, the default providers, the session's numerics and angle unit,
     * the rewrite cap for the integral's size and the cancellation. An indefinite integral keeps
     * the facts of its variable, which is free in its result; a definite one drops them, its
     * variable first renamed to a fresh name when a bound names it ({@code assume(x > 1); ∫[1, x]
     * 1/x dx} keeps {@code x > 1} for the bound). An indefinite integral's variable is free in its
     * result, as a derivative's is ({@code docs/language.md} §14.13): a value the written name has
     * here is put in, and a fresh name the capture check gave it is renamed back unless that name
     * occurs free in the result; a definite integral binds its variable, so its value has none.
     *
     * @param node the node the binder evaluation built
     * @param written the variable as written, which may not be a symbol in an {@code integrate}
     *     call
     * @param typed the integral or the {@code integrate} call as typed, which keeps its trace when
     *     it is the statement
     */
    private Expr integrated(Expr node, Expr written, Expr typed) {
      if (folding || !(node instanceof Integral in)) {
        // An integral left in a result being folded is one no technique could compute.
        return node;
      }
      Assumptions facts = env.assumptions();
      for (String name : bound) {
        facts = facts.without(Symbol.of(name));
      }
      if (in.isDefinite()) {
        // the variable is bound in the integrand only; a bound may name an x of the outer scope,
        // so the bound variable takes a fresh name first and only that name's facts are dropped
        if (occurs(in.lower(), in.var()) || occurs(in.upper(), in.var())) {
          in = (Integral) Substitution.renameBinder(in, in.var().name(), freshFor(in, in.var()));
        }
        facts = facts.without(in.var());
      }
      long nodes = checkSize(in, in);
      boolean whole = traced && typed == root;
      Context context =
          new Context(
              facts,
              mc,
              whole,
              Math.toIntExact(REWRITE_CAP + REWRITE_CAP_PER_NODE * nodes),
              Providers.defaults(),
              new SessionNumerics(env.settings().angleMode().halfTurn(), cancellation),
              cancellation);
      Rewriter.Result r;
      try {
        r = Calculus.integrate(in.integrand(), in.var(), in.lower(), in.upper(), context);
      } catch (AlgebraLimitException limit) {
        throw new EvaluationException(limit.getMessage(), node);
      } catch (RewriteLimitException ex) {
        throw new EvaluationException(
            "integration did not finish (rule set " + ex.ruleSet() + ", rule " + ex.rule() + ")",
            node);
      }
      Expr result = r.expr();
      if (!in.isDefinite() && !(result instanceof Integral)) {
        Symbol v = in.var();
        Symbol x = written instanceof Symbol s ? s : v;
        if (hasValue(x)) {
          result = Substitution.apply(result, new TreeMap<>(Map.of(v.name(), eval(x))));
        }
        if (!x.equals(v) && !occursFree(x, result)) {
          result = Substitution.apply(result, new TreeMap<>(Map.of(v.name(), x)));
        }
        boolean outer = folding;
        folding = true;
        try {
          result = inFrame(null, bound, result);
        } finally {
          folding = outer;
        }
      }
      if (whole) {
        commandTrace = r.trace();
      }
      return result;
    }

    /**
     * The value of an application command whose scope and bounds have been evaluated ({@link
     * Applications#of}), its variable bound: the facts without those about the enclosing binders'
     * names and the variable (renamed to a fresh name first when a later argument names it), the
     * default providers, the session's numerics, the cap for the call's size and the cancellation.
     * A wrong argument is the command's error; an integral the command leaves goes on to the
     * numeric path.
     */
    private Expr applied(Expr node, Builtin b, Expr typed) {
      if (folding || !(node instanceof Apply call)) {
        return node;
      }
      int at = b.binderFor(call.args().size()).getAsInt() - 1;
      if (!(call.args().get(at) instanceof Symbol var)) {
        throw new EvaluationException(b.name() + " needs a variable", node);
      }
      for (Expr later : call.args().subList(at + 1, call.args().size())) {
        if (occurs(later, var)) {
          call = (Apply) Substitution.renameBinder(call, var.name(), freshFor(call, var));
          var = (Symbol) call.args().get(at);
          break;
        }
      }
      Assumptions facts = env.assumptions();
      for (String name : bound) {
        facts = facts.without(Symbol.of(name));
      }
      facts = facts.without(var);
      long nodes = checkSize(call, call);
      boolean whole = traced && typed == root;
      Context context =
          new Context(
              facts,
              mc,
              whole,
              Math.toIntExact(REWRITE_CAP + REWRITE_CAP_PER_NODE * nodes),
              Providers.defaults(),
              new SessionNumerics(env.settings().angleMode().halfTurn(), cancellation),
              cancellation);
      Rewriter.Result r;
      try {
        r = Applications.of(call, context);
      } catch (IllegalArgumentException wrong) {
        throw new EvaluationException(wrong.getMessage(), node);
      } catch (AlgebraLimitException limit) {
        throw new EvaluationException(limit.getMessage(), node);
      } catch (RewriteLimitException ex) {
        throw new EvaluationException(
            b.name() + " did not finish (rule set " + ex.ruleSet() + ", rule " + ex.rule() + ")",
            node);
      }
      if (whole) {
        commandTrace = r.trace();
      }
      return r.expr();
    }

    /**
     * {@code v_1}, {@code v_2}, …: the first name for {@code v} that occurs nowhere in {@code e}.
     */
    private static Symbol freshFor(Expr e, Symbol v) {
      String base = v.name().replaceAll("_\\d+$", "");
      for (int k = 1; ; k++) {
        Symbol s = Symbol.of(base + "_" + k);
        if (!occurs(e, s)) {
          return s;
        }
      }
    }

    /**
     * A limit after its binder evaluation: the {@code Limit} node through {@link Limits#limit},
     * under the assumptions without those of the enclosing binders' names and the session's
     * numerics in radians; a {@code limit} call whose arguments do not make a node stays. The value
     * is free of the variable.
     */
    private Expr limit(Expr node, Expr typed) {
      if (folding || !(node instanceof Limit l)) {
        return node;
      }
      Assumptions facts = env.assumptions();
      for (String name : bound) {
        facts = facts.without(Symbol.of(name));
      }
      boolean whole = traced && typed == root;
      Context context =
          new Context(
                  facts,
                  mc,
                  whole,
                  Math.toIntExact(REWRITE_CAP + REWRITE_CAP_PER_NODE * checkSize(l, typed)))
              .withNumerics(new SessionNumerics(0, cancellation))
              .withCancellation(cancellation);
      Rewriter.Result r;
      try {
        r = Limits.limit(l.expr(), l.var(), l.point(), l.direction(), context);
      } catch (IllegalArgumentException wrong) {
        throw new EvaluationException(wrong.getMessage(), typed);
      } catch (AlgebraLimitException limit) {
        throw new EvaluationException(limit.getMessage(), typed);
      } catch (RewriteLimitException ex) {
        throw new EvaluationException(
            "the limit did not finish (rule set " + ex.ruleSet() + ", rule " + ex.rule() + ")",
            typed);
      }
      if (whole) {
        commandTrace = r.trace() == Trace.NONE ? null : r.trace();
      }
      return canon(r.expr());
    }

    /**
     * A sum or product after its binder evaluation: the node through {@link Calculus#sum}, under
     * the assumptions without those of the enclosing binders' names and the session's numerics in
     * radians; a call whose arguments do not make a node stays. The value is free of the index.
     */
    private Expr sum(Expr node, Expr typed) {
      if (folding || !(node instanceof BigSum || node instanceof BigProduct)) {
        return node;
      }
      declined = null;
      Expr each = terms(node, typed);
      if (each != null) {
        return each;
      }
      String reason = declined;
      Assumptions facts = env.assumptions();
      for (String name : bound) {
        facts = facts.without(Symbol.of(name));
      }
      boolean whole = traced && typed == root;
      Context context =
          new Context(
                  facts,
                  mc,
                  whole,
                  Math.toIntExact(REWRITE_CAP + REWRITE_CAP_PER_NODE * checkSize(node, typed)))
              .withNumerics(new SessionNumerics(0, cancellation))
              .withCancellation(cancellation);
      Rewriter.Result r;
      try {
        r = Calculus.sum(node, context);
      } catch (AlgebraLimitException limit) {
        throw new EvaluationException(limit.getMessage(), typed);
      } catch (RewriteLimitException ex) {
        throw new EvaluationException(
            "the sum did not finish (rule set " + ex.ruleSet() + ", rule " + ex.rule() + ")",
            typed);
      }
      if (whole) {
        commandTrace = r.trace() == Trace.NONE ? null : r.trace();
        Expr stays = r.expr();
        if (reason != null && (stays instanceof BigSum || stays instanceof BigProduct)) {
          // the node stays because its terms were given up: the note says so
          String set = stays instanceof BigSum ? "sum" : "product";
          Rewrite note = new Rewrite(stays, stays, set, reason, Path.ROOT, Bindings.EMPTY);
          commandTrace = new Trace(stays, stays, List.of(note), List.of());
        }
      }
      return canon(r.expr());
    }

    /**
     * A sum or product with integer bounds and at most {@link Calculus#MAX_TERMS} terms, term by
     * term: the body as written evaluated in a frame of the index at each value, under the
     * session's settings (its angle mode; held commands in it evaluate), the values added or
     * multiplied in pairs. A body with sums of integer bounds in it that evaluated to arithmetic
     * over numbers and symbols gives its terms by substituting each value in the evaluated body
     * instead, so a nested sum is not taken again for each term. Null when the bounds are not such,
     * or a term is not a number's arithmetic (a lambda, a set), which leaves the node to {@link
     * Calculus#sum}.
     */
    private Expr terms(Expr node, Expr typed) {
      List<Expr> ops = Operands.of(node);
      if (!(ops.get(2) instanceof Num a && a.value() instanceof Int from)
          || !(ops.get(3) instanceof Num b && b.value() instanceof Int to)) {
        return null;
      }
      BigInteger count = to.value().subtract(from.value()).add(BigInteger.ONE);
      if (count.signum() < 0 || count.compareTo(BigInteger.valueOf(Calculus.MAX_TERMS)) > 0) {
        return null;
      }
      Expr body;
      Symbol index;
      if (typed instanceof Apply call) {
        body = call.args().get(0);
        index = (Symbol) call.args().get(1);
      } else {
        body = Operands.of(typed).get(0);
        index = (Symbol) Operands.of(typed).get(1);
      }
      boolean sum = node instanceof BigSum;
      Expr scoped = ops.get(0);
      Symbol current = (Symbol) ops.get(1);
      if (Calculus.isScalar(scoped) && !occurs(scoped, current)) {
        // a body free of the index is one term, repeated
        Expr n = Num.of(Int.of(count));
        Expr value = canon(sum ? Product.of(n, scoped) : Power.of(scoped, n));
        record(node, value, typed, sum);
        return value;
      }
      // the whole statement's terms, nested sums included, checked before any is taken; a body
      // whose nested sums evaluated to arithmetic gives its terms by substitution, each costing
      // its summands
      long nestedTerms = nested(body);
      boolean substituted = nestedTerms > 0 && plain(scoped);
      long each =
          substituted
              ? (scoped instanceof Sum s ? s.terms().size() : 1)
              : 1 + Math.min(nestedTerms, TERM_BUDGET);
      long estimate = count.longValue() * each;
      if (estimate > termsLeft) {
        return abandon("budget");
      }
      termsLeft -= substituted ? estimate : count.longValue();
      Set<String> names = new TreeSet<>(bound);
      names.remove(index.name());
      List<Expr> values = new ArrayList<>();
      termsNesting++;
      try {
        for (BigInteger j = from.value(); j.compareTo(to.value()) <= 0; j = j.add(BigInteger.ONE)) {
          cancellation.check();
          Expr v;
          if (substituted) {
            Expr term =
                Substitution.apply(
                    scoped, new TreeMap<>(Map.of(current.name(), Num.of(Int.of(j)))));
            boolean outer = folding;
            folding = true;
            try {
              v = inFrame(null, bound, term);
            } finally {
              folding = outer;
            }
          } else {
            TreeMap<String, Expr> at = new TreeMap<>();
            at.put(index.name(), Num.of(Int.of(j)));
            v = inFrame(new Frame(at, frame), names, body);
          }
          if (!Calculus.isScalar(v)) {
            return abandon("not-arithmetic");
          }
          values.add(v);
        }
      } catch (Abandoned inner) {
        // a nested sum that declined abandons every sum around it: no half-evaluated value
        if (termsNesting > 1) {
          throw inner;
        }
        declined = inner.reason;
        return null;
      } finally {
        termsNesting--;
      }
      // in pairs, so that exact sums of many fractions stay of balanced size
      while (values.size() > 1) {
        List<Expr> next = new ArrayList<>((values.size() + 1) / 2);
        for (int k = 0; k + 1 < values.size(); k += 2) {
          Expr x = values.get(k);
          Expr y = values.get(k + 1);
          next.add(canon(sum ? Sum.of(x, y) : Product.of(x, y)));
        }
        if (values.size() % 2 == 1) {
          next.add(values.getLast());
        }
        values = next;
        cancellation.check();
      }
      Expr value = values.isEmpty() ? (sum ? Num.ZERO : Num.ONE) : canon(values.getFirst());
      record(node, value, typed, sum);
      return value;
    }

    /**
     * Gives up a term-by-term evaluation for {@code reason}: inside another one by unwinding to the
     * outermost, which leaves its node; at the outermost by leaving the node with the reason.
     */
    private Expr abandon(String reason) {
      if (termsNesting > 0) {
        throw new Abandoned(reason);
      }
      declined = reason;
      return null;
    }

    /**
     * Whether {@code e} is arithmetic over numbers, constants and symbols alone: an evaluated body
     * whose terms substitution gives.
     */
    private static boolean plain(Expr e) {
      Deque<Expr> todo = new ArrayDeque<>();
      todo.push(e);
      while (!todo.isEmpty()) {
        Expr n = todo.pop();
        if (n instanceof Sum || n instanceof Product || n instanceof Power) {
          Operands.of(n).forEach(todo::push);
        } else if (!(n instanceof Num || n instanceof Constant || n instanceof Symbol)) {
          return false;
        }
      }
      return true;
    }

    /** The most terms the sums and products with integer bounds nested in {@code e} take. */
    private static long nested(Expr e) {
      List<Expr> ops = Operands.of(e);
      Expr from = null;
      Expr to = null;
      if (e instanceof BigSum || e instanceof BigProduct) {
        from = ops.get(2);
        to = ops.get(3);
      } else if (e instanceof Apply a
          && (a.head().name().equals("sum") || a.head().name().equals("product"))
          && a.args().size() == 4) {
        from = a.args().get(2);
        to = a.args().get(3);
      }
      long inner = 0;
      for (Expr o : ops) {
        inner = Math.min(inner + nested(o), TERM_BUDGET);
      }
      if (from instanceof Num f
          && f.value() instanceof Int i
          && to instanceof Num t
          && t.value() instanceof Int j) {
        long c = Math.max(0, Math.min(j.value().subtract(i.value()).longValue() + 1, TERM_BUDGET));
        return Math.min(c * (1 + inner), TERM_BUDGET + 1);
      }
      return inner;
    }

    /** The step of a sum or product taken term by term, when it is the statement. */
    private void record(Expr node, Expr value, Expr typed, boolean sum) {
      if (traced && typed == root) {
        Rewrite step =
            new Rewrite(
                node,
                value,
                sum ? "sum" : "product",
                sum ? "terms" : "factors",
                Path.ROOT,
                Bindings.EMPTY);
        commandTrace = value.equals(node) ? null : new Trace(node, value, List.of(step), List.of());
      }
    }

    /**
     * {@code taylor(f, x, a, n)} after its binder evaluation: the Taylor polynomial ({@link
     * Calculus#taylor}) for an order that is a non-negative integer, the call itself for an order
     * with a symbol in it; any other order, an infinite point and a derivative that fails are
     * errors. The variable is free in the polynomial, as a derivative's is.
     */
    private Expr taylor(Expr node, Apply typed) {
      if (folding || !(node instanceof Apply call) || !(call.args().get(1) instanceof Symbol v)) {
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
        throw new EvaluationException("taylor's order must be a non-negative integer", typed);
      }
      if (k.value().bitLength() > 31) {
        throw new EvaluationException("taylor's order must be a non-negative integer", typed);
      }
      if (!finite(point)) {
        throw new EvaluationException("taylor's point must be finite", typed);
      }
      Assumptions facts = env.assumptions();
      for (String name : bound) {
        facts = facts.without(Symbol.of(name));
      }
      boolean whole = traced && typed == root;
      Context context =
          new Context(
                  facts,
                  mc,
                  whole,
                  Math.toIntExact(REWRITE_CAP + REWRITE_CAP_PER_NODE * checkSize(call, typed)))
              .withNumerics(new SessionNumerics(0, cancellation))
              .withCancellation(cancellation);
      Rewriter.Result r;
      try {
        r = Calculus.taylor(call.args().get(0), v, point, k.value().intValue(), context);
      } catch (TaylorException ex) {
        throw new EvaluationException(ex.getMessage(), typed);
      } catch (RewriteLimitException ex) {
        throw new EvaluationException(
            "differentiation did not finish (rule set "
                + ex.ruleSet()
                + ", rule "
                + ex.rule()
                + ")",
            typed);
      }
      Expr written = typed.args().get(1);
      Expr result = freeVariable(r.expr(), v, written instanceof Symbol s ? s : v);
      if (whole) {
        commandTrace = r.trace();
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

    /** Whether {@code x} occurs free in {@code e}: substituting for it changes {@code e}. */
    private static boolean occursFree(Symbol x, Expr e) {
      Symbol marker = Symbol.of(x.name() + "\u0000");
      return !Substitution.apply(e, new TreeMap<>(Map.of(x.name(), marker))).equals(e);
    }

    /** Whether {@code x} evaluates to something other than itself here. */
    private boolean hasValue(Symbol x) {
      if (folding || bound.contains(x.name())) {
        return false;
      }
      if (frame != null && frame.lookup(x.name()) != null) {
        return true;
      }
      return env.definition(x.name()).map(def -> def instanceof Definition.Value).orElse(false);
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
     * A binder command's arguments: those before the variable (its scope) evaluated in that scope,
     * its other held arguments as parsed, its other arguments evaluated; the call is built again as
     * the parser would, so arguments that come to fit give the typed node ({@code diff(f, x, n)}
     * with {@code n := 2}).
     */
    private Expr binder(Apply a, Builtin b) {
      int variable = b.binderFor(a.args().size()).orElse(0) - 1;
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
        } else if (k < variable) {
          // the arguments before the variable are its scope, held or not
          out.add(scoped(arg, names));
        } else if (b.isHeld(k + 1)) {
          out.add(arg);
        } else {
          out.add(eval(arg));
        }
      }
      return canon(TypedNodes.of(new Apply(a.head(), out)));
    }

    private static List<Expr> scopeArgs(Apply a, int variable) {
      List<Expr> out = new ArrayList<>();
      for (int k = 0; k < Math.min(variable, a.args().size()); k++) {
        out.add(a.args().get(k));
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
