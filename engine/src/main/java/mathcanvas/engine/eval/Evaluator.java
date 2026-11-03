package mathcanvas.engine.eval;

import java.math.BigDecimal;
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
import mathcanvas.engine.assume.Facts;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.bases.Bases;
import mathcanvas.engine.bases.Bitwise;
import mathcanvas.engine.builtin.Builtin;
import mathcanvas.engine.builtin.Builtins;
import mathcanvas.engine.calculus.Applications;
import mathcanvas.engine.calculus.Calculus;
import mathcanvas.engine.calculus.ChainRule;
import mathcanvas.engine.calculus.Lagrange;
import mathcanvas.engine.calculus.Laplace;
import mathcanvas.engine.calculus.Limits;
import mathcanvas.engine.calculus.Multivariable;
import mathcanvas.engine.calculus.TaylorException;
import mathcanvas.engine.calculus.VectorIntegrals;
import mathcanvas.engine.discrete.Rsolve;
import mathcanvas.engine.discrete.Sets;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Based;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
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
import mathcanvas.engine.finance.Cashflows;
import mathcanvas.engine.finance.Newton;
import mathcanvas.engine.finance.Tvm;
import mathcanvas.engine.geometry.Shapes;
import mathcanvas.engine.geometry.Triangle;
import mathcanvas.engine.linalg.Linalg;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.numbertheory.Diophantine;
import mathcanvas.engine.numbertheory.NumberTheory;
import mathcanvas.engine.ode.Dsolve;
import mathcanvas.engine.ode.NumericOde;
import mathcanvas.engine.parse.Brackets;
import mathcanvas.engine.parse.Lexer;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.parse.TypedNodes;
import mathcanvas.engine.print.UnicodePrinter;
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
import mathcanvas.engine.stat.Statistics;
import mathcanvas.engine.units.Convert;
import mathcanvas.engine.units.Dimension;
import mathcanvas.engine.units.DimensionException;
import mathcanvas.engine.units.Quantities;

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

  /** The finance functions (§14.28). */
  private static final Set<String> FINANCE =
      Set.of(
          "pv", "fv", "pmt", "nper", "effective", "nominal", "npv", "irr", "rate", "amortization");

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
   * @param numeric whether a definite integral in the statement was evaluated by quadrature,
   *     eigenvalues were found only numerically, or a numeric decomposition computed, so the value
   *     holds a number that no closed form gave ({@code docs/language.md} §10.2, §14.16, §14.17)
   * @param parameters the integer parameters of the general solutions in the value, as a canonical
   *     set of relations {@code k ∈ ℤ}, empty when there are none ({@code docs/language.md} §14.7)
   */
  public record Result(Expr value, Trace trace, boolean numeric, SetExpr parameters) {

    /**
     * A value with no general solution in it.
     *
     * @param value the canonical value
     * @param trace the trace
     * @param numeric whether a value was computed numerically
     */
    public Result(Expr value, Trace trace, boolean numeric) {
      this(value, trace, numeric, NO_PARAMETERS);
    }

    /**
     * A value no quadrature gave, with no general solution in it.
     *
     * @param value the canonical value
     * @param trace the trace
     */
    public Result(Expr value, Trace trace) {
      this(value, trace, false);
    }
  }

  /** The facts of a symbol assumed only to be an integer. */
  private static final Facts INTEGER =
      Assumptions.EMPTY.with(integer(Symbol.of("k"))).facts(Symbol.of("k"));

  /** The relation {@code k ∈ ℤ}. */
  static Relation integer(Symbol k) {
    return new Relation(Relation.Op.IN, k, Constant.INTEGERS);
  }

  /** The facts of a symbol assumed only to be real. */
  private static final Facts REAL =
      Assumptions.EMPTY.with(real(Symbol.of("C"))).facts(Symbol.of("C"));

  /** The relation {@code C ∈ ℝ} of a constant of integration (Phase 7b). */
  static Relation real(Symbol c) {
    return new Relation(Relation.Op.IN, c, Constant.REALS);
  }

  /** The empty set of parameters. */
  static final SetExpr NO_PARAMETERS = new SetExpr(List.of());

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
      SetExpr parameters = state.parameters(value);
      Context context =
          new Context(
                  withParameters(env.assumptions(), parameters),
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
      return new Result(r.expr(), steps, state.quadrature, state.parameters(r.expr()));
    } catch (ArithmeticException outOfRange) {
      throw new EvaluationException(OUT_OF_RANGE, raw);
    }
  }

  /**
   * The assumptions with the facts {@code k ∈ ℤ} of the parameters added, for each parameter that
   * has no fact yet (a parameter has none or exactly that one, {@link State#parameter}).
   *
   * @param a the assumptions
   * @param parameters the relations {@code k ∈ ℤ}
   * @return the assumptions with the parameters integer
   */
  static Assumptions withParameters(Assumptions a, SetExpr parameters) {
    for (Expr p : parameters.items()) {
      Relation r = (Relation) p;
      if (a.facts((Symbol) r.left()).equals(Facts.NONE)) {
        a = a.with(r);
      }
    }
    return a;
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

    /**
     * Whether a value in this statement was computed numerically: a definite integral by
     * quadrature, eigenvalues found only numerically, or a numeric decomposition.
     */
    private boolean quadrature;

    /** The integer parameters the statement's general solutions introduced, by name. */
    private final SortedMap<String, Symbol> parameters = new TreeMap<>();

    /** Phase 7b: the constants of integration the statement's {@code dsolve} calls introduced. */
    private final SortedMap<String, Symbol> constants = new TreeMap<>();

    /** Phase 7b: the unknown functions the {@code dsolve} calls being evaluated declare. */
    private Set<String> unknownFunctions = new TreeSet<>();

    /**
     * The inner derivative of a mixed derivative being evaluated, by identity: its scope is
     * evaluated and the node kept, so the outer derivative differentiates the whole chain at once.
     */
    private Expr deferred;

    /**
     * The inner levels of the iterated integral that is the statement, by identity of their typed
     * node: each one's integral as it was integrated and its value, for the {@code
     * integrate.iterated} steps.
     */
    private final Map<Expr, Expr[]> levels = new IdentityHashMap<>();

    /**
     * The ranges {@code a ≤ x ≤ b} of the variables of the definite integrals being evaluated whose
     * bounds are constants, by name: facts of an inner level of an iterated integral.
     */
    private final Map<String, Relation[]> ranges = new TreeMap<>();

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

    /**
     * The relations {@code k ∈ ℤ} of the parameters and {@code C ∈ ℝ} of the constants of
     * integration that occur in {@code value}.
     */
    SetExpr parameters(Expr value) {
      List<Expr> out = new ArrayList<>();
      for (Symbol k : parameters.values()) {
        if (occurs(value, k)) {
          out.add(integer(k));
        }
      }
      for (Symbol c : constants.values()) {
        if (occurs(value, c)) {
          out.add(real(c));
        }
      }
      return out.isEmpty() ? NO_PARAMETERS : (SetExpr) canon(new SetExpr(out));
    }

    Expr eval(Expr e) {
      cancellation.check();
      if (++depth > MAX_DEPTH) {
        throw new EvaluationException("expression nested too deeply", e);
      }
      try {
        return switch (e) {
          case Num _,
              mathcanvas.engine.expr.Based _,
              Constant _,
              Bool _,
              mathcanvas.engine.expr.Unit _ ->
              e;
          case Lambda l -> closed(l);
          case Symbol s -> symbol(s);
          case Sum _, Product _, Power _ -> arithmetic(e);
          case Apply a -> apply(a);
          case Relation r -> relation(r);
          case ListExpr l -> Brackets.of(all(l.items()));
          case Vector v -> Brackets.of(all(v.items()));
          case Matrix m ->
              Brackets.of(m.rows().stream().map(row -> Brackets.of(all(row))).toList());
          case SetExpr s -> canon(new SetExpr(plain(all(s.items()))));
          case Interval _, Piecewise _ -> canon(Operands.rebuild(e, all(Operands.of(e))));
          case Derivative d -> {
            if (e == deferred) {
              deferred = d.expr() instanceof Derivative ? d.expr() : null;
              yield binder(e, List.of(d.var()));
            }
            yield derivative(d, e);
          }
          case Integral i -> {
            // an iterated integral's inner levels see the outer variable's numeric range as facts
            Relation[] range = range(i);
            Relation[] outer = range == null ? null : ranges.put(i.var().name(), range);
            try {
              yield numericIntegral(integrated(binder(e, List.of(i.var())), i.var(), e), e);
            } finally {
              if (range != null) {
                if (outer == null) {
                  ranges.remove(i.var().name());
                } else {
                  ranges.put(i.var().name(), outer);
                }
              }
            }
          }
          case Limit l -> limit(binder(e, List.of(l.var())), e);
          case BigSum s -> sum(binder(e, List.of(s.var())), e);
          case BigProduct p -> sum(binder(e, List.of(p.var())), e);
        };
      } finally {
        depth--;
      }
    }

    /**
     * Phase 8a: the values with {@code ifactor}'s display-only products replaced by their integers
     * ({@link NumberTheory#plain}), as every use of a value but a list's or a vector's needs them;
     * a set's items are plain too, so that equal items are one.
     */
    private static List<Expr> plain(List<Expr> values) {
      List<Expr> out = new ArrayList<>(values.size());
      for (Expr v : values) {
        out.add(NumberTheory.plain(v));
      }
      return out;
    }

    /**
     * Phase 8a: a number-theory function ({@link NumberTheory}); its steps are kept when the call
     * is the statement, or the argument of a {@code numeric} that is.
     */
    private Expr numberTheory(Apply a, List<Expr> args) {
      boolean top = traced && atTop(a);
      Context ctx =
          new Context(env.assumptions(), mc, top, REWRITE_CAP).withCancellation(cancellation);
      try {
        Rewriter.Result r = NumberTheory.apply(new Apply(a.head(), args), ctx);
        if (top && !r.trace().rewrites().isEmpty()) {
          commandTrace = r.trace();
        }
        return canon(r.expr());
      } catch (IllegalArgumentException wrong) {
        throw new EvaluationException(wrong.getMessage(), a);
      }
    }

    /** The boolean operators (Phase 8a, §8.4). */
    private static final Set<String> LOGIC = Set.of("and", "or", "xor", "not", "implies");

    /**
     * Phase 8a: a boolean operator: its operands must be truth values, or what may stand for one (a
     * name, a relation, a boolean operator, {@code isprime}, or an application of a function that
     * is not built in); the identities are the simplifier's ({@code logic}).
     */
    private Expr logic(Apply a, List<Expr> args) {
      for (Expr arg : args) {
        boolean truth =
            arg instanceof Bool
                || arg instanceof Symbol
                || arg instanceof Relation
                || (arg instanceof Apply f
                    && (LOGIC.contains(f.head().name())
                        || f.head().name().equals("isprime")
                        || Builtins.lookup(f.head().name()).isEmpty()));
        if (!truth) {
          throw new EvaluationException(a.head().name() + " takes truth values", a);
        }
      }
      return canon(new Apply(a.head(), args));
    }

    /** Phase 8a: a set operation ({@link Sets}); the call stays when an item is undecided. */
    private Expr sets(Apply a, List<Expr> args) {
      try {
        Expr value = Sets.apply(a.head().name(), args, cancellation);
        return canon(value == null ? new Apply(a.head(), args) : value);
      } catch (IllegalArgumentException wrong) {
        throw new EvaluationException(wrong.getMessage(), a);
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
      List<Expr> ops = plain(all(Operands.of(e)));
      for (Expr op : ops) {
        if (op instanceof Lambda) {
          throw new EvaluationException("a function is not a factor", e);
        }
        if (op instanceof SetExpr || Sets.isSetOperation(op)) {
          throw new EvaluationException("sets carry no arithmetic", e);
        }
        if (op instanceof ListExpr) {
          throw new EvaluationException("lists carry no arithmetic", e);
        }
        if (op instanceof Bool || op instanceof Apply f && LOGIC.contains(f.head().name())) {
          throw new EvaluationException("truth values carry no arithmetic", e);
        }
        if (op instanceof Constant k && isDomain(k)) {
          throw new EvaluationException("domains carry no arithmetic", e);
        }
      }
      Expr value =
          LinearAlgebra.reduce(
              canon(Operands.rebuild(e, ops)), env.assumptions(), mc, cancellation, e);
      dimension(value, e);
      return value;
    }

    /**
     * The dimension of a value with units (§14.26), its dimensions checked: a sum of two
     * dimensions, an exponent with units or a quantity to a power that is no rational number is an
     * error at {@code node}. The items of a list, a vector or a matrix are checked each, and the
     * collection has no dimension.
     */
    private Dimension dimension(Expr value, Expr node) {
      List<Expr> items =
          switch (value) {
            case ListExpr l -> l.items();
            case Vector v -> v.items();
            case Matrix m -> m.rows().stream().flatMap(List::stream).toList();
            default -> null;
          };
      if (items != null) {
        for (Expr item : items) {
          dimension(item, node);
        }
        return Dimension.NONE;
      }
      if (!Quantities.hasUnit(value)) {
        return Dimension.NONE;
      }
      try {
        return Quantities.dimension(value);
      } catch (DimensionException x) {
        throw new EvaluationException(
            switch (x.kind()) {
              case ADD -> "cannot add " + unitText(x.first()) + " and " + unitText(x.second());
              case COMPARE ->
                  "cannot compare " + unitText(x.first()) + " and " + unitText(x.second());
              case EXPONENT_UNITS -> "an exponent cannot have units";
              case EXPONENT -> "a quantity's exponent must be a rational number";
              case FUNCTION ->
                  x.function() + " needs a number, not a quantity in " + unitText(x.first());
            },
            node);
      }
    }

    /**
     * A term's units as a message names them: {@code m}, {@code km/h}, {@code a number}; a sum's,
     * whose terms have one dimension, are those of its first term with units.
     */
    private static String unitText(Expr term) {
      if (term instanceof Sum s) {
        return unitText(s.terms().stream().filter(Quantities::hasUnit).findFirst().orElse(Num.ONE));
      }
      Expr part = Quantities.unitPart(term);
      return part.equals(Num.ONE)
          ? "a number"
          : UnicodePrinter.INSTANCE.display(part).replace("_", "");
    }

    /**
     * A relation of quantities (§14.26): the sides must have one dimension, an exact 0 having any,
     * and two numbers times units are compared in the coherent SI units, two bare temperatures on
     * the absolute scale.
     */
    private Expr quantities(Relation decided, Expr c, Relation node) {
      if (!Quantities.hasUnit(decided.left()) && !Quantities.hasUnit(decided.right())) {
        return c;
      }
      Dimension left = dimension(decided.left(), node);
      Dimension right = dimension(decided.right(), node);
      if (!left.equals(right)
          && !Quantities.isZero(decided.left())
          && !Quantities.isZero(decided.right())) {
        throw new EvaluationException(
            "cannot compare " + unitText(decided.left()) + " and " + unitText(decided.right()),
            node);
      }
      boolean absolute =
          Quantities.isBareTemperature(decided.left())
              && Quantities.isBareTemperature(decided.right());
      Expr a = Quantities.inBase(decided.left(), absolute);
      Expr b = Quantities.inBase(decided.right(), absolute);
      if (a instanceof Num && b instanceof Num) {
        return canon(new Relation(decided.op(), a, b));
      }
      return c;
    }

    private Expr relation(Relation r) {
      Expr c =
          canon(
              new Relation(
                  r.op(), NumberTheory.plain(eval(r.left())), NumberTheory.plain(eval(r.right()))));
      if (!(c instanceof Relation decided)) {
        return c;
      }
      switch (decided.op()) {
        case IN -> {
          // Phase 8a: membership in a set, decided when the items decide it
          if (decided.right() instanceof SetExpr s) {
            Tri in = Sets.member(decided.left(), s);
            return in == Tri.UNKNOWN ? c : Bool.of(in == Tri.TRUE);
          }
          if (Sets.isValue(decided.right()) && !(decided.right() instanceof Constant)
              || decided.right() instanceof Apply f && LOGIC.contains(f.head().name())) {
            throw new EvaluationException("∈ needs a set or a domain on its right", r);
          }
          if (decided.right() instanceof Constant k
              && isDomain(k)
              && Quantities.hasUnit(decided.left())) {
            throw new EvaluationException("a quantity is not a member of a number domain", r);
          }
          if (decided.right() instanceof Constant k
              && isDomain(k)
              && decided.left() instanceof Num n) {
            return Bool.of(member(n, k));
          }
        }
        case LT, LE, GT, GE -> {
          if (decided.left() instanceof Bool || decided.right() instanceof Bool) {
            throw new EvaluationException("truth values are not ordered", r);
          }
          if (isComplex(decided.left()) || isComplex(decided.right())) {
            throw new EvaluationException("complex numbers are not ordered", r);
          }
        }
        case EQ, NE -> {}
      }
      return quantities(decided, c, r);
    }

    // Applications.

    private Expr apply(Apply a) {
      String name = a.head().name();
      // Phase 7b: an unknown function a dsolve call declares, or the prime notation
      String base = name.endsWith("'") ? name.substring(0, name.indexOf('\'')) : name;
      if (unknownFunctions.contains(base)) {
        return canon(new Apply(a.head(), all(a.args())));
      }
      if (!base.equals(name)) {
        return primed(a, base);
      }
      Optional<Builtin> found = Builtins.lookup(name);
      if (found.isPresent()) {
        Builtin b = found.get();
        if (!b.accepts(a.args().size())) {
          throw new EvaluationException(name + " takes " + b.arityText(), a);
        }
        return switch (b.kind()) {
          case FUNCTION -> {
            List<Expr> args = plain(all(a.args()));
            // §14.27: an integer in a base is its integer to a function
            args.replaceAll(Based::unwrap);
            if (args.contains(Constant.UNDEFINED)) {
              yield Constant.UNDEFINED;
            }
            // §14.26: a function needs numbers; a dimensionless quantity is its number, an angle
            // in radians or degrees evaluated in radians
            boolean radians = false;
            if (name.equals("units") || name.equals("magnitude")) {
              Expr q = Quantities.single(args.getFirst(), mc);
              yield name.equals("units") ? Quantities.unitPart(q) : Quantities.magnitude(q);
            }
            if (Quantities.FUNCTIONS.contains(name)) {
              if ((name.equals("min") || name.equals("max"))
                  && args.stream().anyMatch(Quantities::hasUnit)) {
                yield extremum(a, args);
              }
            } else {
              for (int k = 0; k < args.size(); k++) {
                Expr arg = args.get(k);
                if (Quantities.hasUnit(arg)) {
                  if (!dimension(arg, a).isNone()) {
                    throw new EvaluationException(
                        name + " needs a number, not a quantity in " + unitText(arg), a);
                  }
                  radians |= Quantities.hasAngle(arg);
                  args.set(k, Quantities.number(arg));
                }
              }
            }
            if (Bases.NAMES.contains(name) || Bitwise.NAMES.contains(name)) {
              try {
                Optional<Expr> v =
                    Bases.NAMES.contains(name)
                        ? Bases.apply(name, args)
                        : Bitwise.apply(name, args);
                yield v.orElseGet(() -> canon(new Apply(a.head(), args)));
              } catch (Bases.BaseException x) {
                throw new EvaluationException(x.getMessage(), a);
              }
            }
            if (FINANCE.contains(name)) {
              yield finance(a, args);
            }
            if (NumberTheory.FUNCTIONS.contains(name)) {
              yield numberTheory(a, args);
            }
            if (Sets.FUNCTIONS.contains(name)) {
              yield sets(a, args);
            }
            if (LOGIC.contains(name)) {
              yield logic(a, args);
            }
            if (Linalg.FUNCTIONS.contains(name)) {
              yield linalg(a, args);
            }
            if (AngleFunctions.NAMES.contains(name)) {
              yield AngleFunctions.apply(a, args, mc);
            }
            if (Shapes.NAMES.contains(name)) {
              yield shape(a, args);
            }
            if (NumericDecompositions.handles(name)) {
              yield decomposition(a, args);
            }
            if (Statistics.FUNCTIONS.contains(name)) {
              yield statistics(a, args);
            }
            Expr algebraic = algebraFunction(a, args);
            yield algebraic != null
                ? algebraic
                : Functions.apply(
                    a.head(),
                    args,
                    mc,
                    folding || radians ? AngleMode.RADIANS : env.settings().angleMode(),
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

    /**
     * The time value of money (§14.28): the closed forms evaluated as the expressions {@link Tvm}
     * builds, so exact data give exact values; {@code npv} likewise; the schedule of {@code
     * amortization}; and {@code rate} and {@code irr} by Newton's method, their value marked
     * numeric with the step {@code numeric.rate} or {@code numeric.irr}. A call with an argument
     * that is not a real number (or, for {@code npv} and {@code irr}, a list of them) stays.
     */
    private Expr finance(Apply a, List<Expr> args) {
      String name = a.head().name();
      Expr unevaluated = canon(new Apply(a.head(), args));
      try {
        if (Tvm.NAMES.contains(name)) {
          Expr f = Tvm.apply(name, args);
          return f == null ? unevaluated : eval(f);
        }
        List<Expr> flows = null;
        int flowsAt = name.equals("npv") ? 1 : name.equals("irr") ? 0 : -1;
        if (flowsAt >= 0) {
          if (!(args.get(flowsAt) instanceof Vector v)
              || !v.items().stream().allMatch(State::isRealNumber)) {
            return unevaluated;
          }
          flows = v.items();
        }
        for (int k = 0; k < args.size(); k++) {
          if (k != flowsAt && !isRealNumber(args.get(k))) {
            return unevaluated;
          }
        }
        return switch (name) {
          case "npv" ->
              Tvm.real(args.getFirst()).compareValue(Int.MINUS_ONE) <= 0
                  ? Constant.UNDEFINED
                  : eval(Cashflows.npv(args.getFirst(), flows));
          case "amortization" ->
              Tvm.real(args.getFirst()).compareValue(Int.MINUS_ONE) <= 0
                  ? Constant.UNDEFINED
                  : Cashflows.amortization(
                      Tvm.real(args.get(0)), Tvm.real(args.get(1)), Tvm.real(args.get(2)), mc);
          default -> rate(a, name, args, flows);
        };
      } catch (Tvm.FinanceException x) {
        throw new EvaluationException(x.getMessage(), a);
      }
    }

    /** The parts of {@code tvm} in the order its value lists them. */
    private static final List<String> TVM_PARTS = List.of("n", "i", "pv", "pmt", "fv");

    /**
     * {@code tvm(n = …, i = …, pv = …, pmt = …, fv = …[, type = …])} (§14.28): four of the five
     * parts by name, the fifth found through {@code fv}, {@code pv}, {@code pmt}, {@code nper} or
     * {@code rate}; the value is the list of the five relations.
     */
    private Expr tvm(Apply a) {
      TreeMap<String, Expr> parts = new TreeMap<>();
      for (Expr arg : a.args()) {
        if (!(arg instanceof Relation r
            && r.op() == Relation.Op.EQ
            && r.left() instanceof Symbol s
            && (TVM_PARTS.contains(s.name()) || s.name().equals("type")))) {
          throw new EvaluationException("tvm's parts are n, i, pv, pmt, fv and type", arg);
        }
        if (parts.containsKey(s.name())) {
          throw new EvaluationException("tvm's parts must be distinct", arg);
        }
        Expr value = Based.unwrap(eval(r.right()));
        if (!isRealNumber(value)) {
          throw new EvaluationException("tvm needs numbers: " + s.name(), arg);
        }
        parts.put(s.name(), value);
      }
      List<String> missing = TVM_PARTS.stream().filter(p -> !parts.containsKey(p)).toList();
      if (missing.size() != 1) {
        throw new EvaluationException("tvm needs four of n, i, pv, pmt, fv", a);
      }
      Expr type = parts.getOrDefault("type", Num.ZERO);
      String unknown = missing.getFirst();
      Expr n = parts.get("n");
      Expr i = parts.get("i");
      Expr pv = parts.get("pv");
      Expr pmt = parts.get("pmt");
      Expr fv = parts.get("fv");
      List<Expr> args =
          switch (unknown) {
            case "fv" -> List.of(i, n, pmt, pv, type);
            case "pv" -> List.of(i, n, pmt, fv, type);
            case "pmt" -> List.of(i, n, pv, fv, type);
            case "n" -> List.of(i, pmt, pv, fv, type);
            default -> List.of(n, pmt, pv, fv, type);
          };
      String function =
          switch (unknown) {
            case "n" -> "nper";
            case "i" -> "rate";
            default -> unknown;
          };
      Apply call = new Apply(Symbol.of(function), args);
      parts.put(unknown, finance(call, args));
      List<Expr> out = new ArrayList<>();
      for (String p : TVM_PARTS) {
        out.add(new Relation(Relation.Op.EQ, Symbol.of(p), parts.get(p)));
      }
      ListExpr value = new ListExpr(out);
      if (unknown.equals("i") && traced && atTop(a)) {
        // the rate is numeric: its step, as rate's
        commandTrace = numericStep(a, value, "rate");
      }
      return value;
    }

    private static boolean isRealNumber(Expr e) {
      return e instanceof Num n && n.value() instanceof RealNumeric;
    }

    /** {@code rate} or {@code irr}: numeric, marked, with its step. */
    private Expr rate(Apply a, String name, List<Expr> args, List<Expr> flows) {
      RealNumeric value;
      if (name.equals("irr")) {
        RealNumeric guess =
            args.size() > 1 ? Tvm.real(args.get(1)) : Real.of(new BigDecimal("0.1"));
        value = Newton.irr(flows.stream().map(Tvm::real).toList(), guess, mc, cancellation);
      } else {
        RealNumeric type = args.size() > 4 ? Tvm.real(args.get(4)) : Int.ZERO;
        if (!(type.isZero() || type.isOne())) {
          throw new EvaluationException("rate's type must be 0 (end) or 1 (beginning)", a);
        }
        RealNumeric guess =
            args.size() > 5 ? Tvm.real(args.get(5)) : Real.of(new BigDecimal("0.1"));
        value =
            Newton.rate(
                Tvm.real(args.get(0)),
                Tvm.real(args.get(1)),
                Tvm.real(args.get(2)),
                args.size() > 3 ? Tvm.real(args.get(3)) : Int.ZERO,
                type,
                guess,
                mc,
                cancellation);
      }
      Expr result = Num.of(value);
      quadrature = true;
      if (traced && atTop(a)) {
        commandTrace = numericStep(canon(new Apply(a.head(), args)), result, name);
      }
      return result;
    }

    /** The one step of a numeric rate, {@code numeric.rate} or {@code numeric.irr}. */
    private Trace numericStep(Expr call, Expr result, String name) {
      Bindings digits =
          new Bindings(
              List.of(new Bindings.Binding("digits_", Num.of(mc.getPrecision()), null, null)));
      return new Trace(
          call,
          result,
          List.of(new Rewrite(call, result, Newton.RULE_SET, name, Path.ROOT, digits)),
          List.of());
    }

    /**
     * {@code min} or {@code max} of quantities of one dimension (§14.26): the argument with the
     * least or greatest value in the coherent SI units, or the call when a value is not a number.
     */
    private Expr extremum(Apply a, List<Expr> args) {
      // an exact 0 has every dimension: max(0, 3 _m) is 3 _m
      Expr first = args.stream().filter(e -> !Quantities.isZero(e)).findFirst().orElseThrow();
      Dimension d = dimension(first, a);
      Expr best = null;
      RealNumeric bestValue = null;
      for (Expr arg : args) {
        if (!Quantities.isZero(arg) && !dimension(arg, a).equals(d)) {
          throw new EvaluationException(
              "cannot compare " + unitText(first) + " and " + unitText(arg), a);
        }
        if (!(Quantities.inBase(arg, false) instanceof Num n
            && n.value() instanceof RealNumeric value)) {
          return canon(new Apply(a.head(), args));
        }
        int order = bestValue == null ? 0 : value.compareValue(bestValue);
        if (best == null || (a.head().name().equals("max") ? order > 0 : order < 0)) {
          best = arg;
          bestValue = value;
        }
      }
      return best;
    }

    /**
     * Phase 7b: the prime notation outside {@code dsolve}, {@code f'(a)}: the derivative of the
     * user function {@code f} of one argument, of the order the primes say, at {@code a}; the body
     * is differentiated in the function's parameter, and the parameter's value put in, as for
     * {@code f(x) := diff(…, x)}.
     */
    private Expr primed(Apply a, String base) {
      String name = a.head().name();
      if (!(env.definition(base).orElse(null) instanceof Definition.Function f)) {
        throw new EvaluationException(base + " is not a function", a);
      }
      if (f.params().size() != 1) {
        throw new EvaluationException(
            "the prime notation needs a function of one argument: write diff", a);
      }
      if (a.args().size() != 1) {
        throw new EvaluationException(name + " takes 1 argument", a);
      }
      Expr value = eval(a.args().getFirst());
      Symbol p = f.params().getFirst();
      Expr raw =
          Derivative.of(new Apply(Symbol.of(base), List.of(p)), p, name.length() - base.length());
      return inFrame(new Frame(new TreeMap<>(Map.of(p.name(), value)), null), new TreeSet<>(), raw);
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
        case "diff" -> {
          Expr rule =
              args.size() == 2 && args.get(1) instanceof Symbol x
                  ? chainRule(args.getFirst(), x, a)
                  : null;
          yield rule != null ? rule : differentiated(binder(a, b), args.get(1), a);
        }
        case "solve" -> solve(a, b);
        case "diophantine" -> diophantine(a);
        case "dsolve" -> dsolve(a);
        case "rsolve" -> rsolve(a);
        case "odesolve" -> odesolve(a);
        case "triangle" -> triangle(a);
        case "integrate" -> numericIntegral(integrated(binder(a, b), args.get(1), a), a);
        case "arclength", "area", "volume", "shellvolume", "extrema", "maximize", "minimize" ->
            numericIntegral(applied(binder(a, b), b, a), a);
        case "taylor" -> taylor(binder(a, b), a);
        // Phase 7a-1: multivariable calculus
        case "grad", "jacobian", "hessian", "divergence", "curl", "laplacian" ->
            vectorCalculus(binder(a, b), a);
        case "lagrange" -> lagrange(binder(a, b), a);
        // Phase 7a-2: line and surface integrals
        case "lineintegral", "surfaceintegral" -> numericIntegral(vectorIntegral(a), a);
        case "laplace", "ilaplace" -> transform(binder(a, b), a);
        case "limit" -> limit(binder(a, b), a);
        case "sum", "product" -> sum(binder(a, b), a);
        case "expand", "factor", "collect", "cancel", "apart" -> algebra(a);
        case "convert" -> convert(a);
        case "tvm" -> tvm(a);
        case "det", "charpoly" ->
            linalg(
                a,
                args.size() == 1
                    ? List.of(eval(args.getFirst()))
                    : List.of(eval(args.getFirst()), args.get(1)));
        case "simplify", "steps" -> eval(args.getFirst());
        case "exact" -> canon(NumericForm.exact(NumberTheory.plain(eval(args.getFirst())), a));
        case "numeric" -> numeric(a);
        case "subs" -> subs(a);
        case "truthtable" -> truthtable(a);
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
      Symbol parameter = parameter(current.args().get(0), equations, names);
      if (env.assumptions().facts(parameter).equals(Facts.NONE)) {
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
      if (occurs(value, parameter)) {
        Expr given = frame == null ? null : frame.lookup(parameter.name());
        if (given != null) {
          // the parameter is free in the result: its value here is put in, as a derivative's
          value = Substitution.apply(value, new TreeMap<>(Map.of(parameter.name(), given)));
        } else {
          parameters.put(parameter.name(), parameter);
        }
      }
      return canon(value);
    }

    /**
     * Phase 8a: {@code diophantine(equation, [x, y])} ({@link Diophantine}): the equation evaluated
     * in the scope of the unknowns, renamed as {@code solve}'s are when they would capture a name
     * from outside, with an integer parameter chosen as {@code solve}'s is.
     */
    private Expr diophantine(Apply a) {
      Apply current = a;
      List<Symbol> original = Substitution.boundBy(a);
      if (original.size() != 2) {
        throw new EvaluationException("diophantine solves for two unknowns, [x, y]", a);
      }
      List<Symbol> names = new ArrayList<>(original);
      for (int k = 0; k < names.size(); k++) {
        Symbol fresh = captureFree(current, names.get(k), scopeArgs(current, 1));
        if (fresh != null) {
          current = (Apply) Substitution.renameBinder(current, names.get(k).name(), fresh);
          names.set(k, fresh);
        }
      }
      Expr equation = scoped(current.args().get(0), names);
      Symbol parameter = parameter(current.args().get(0), equation, names);
      Context ctx =
          new Context(env.assumptions(), mc, traced && a == root, REWRITE_CAP)
              .withCancellation(cancellation);
      Expr call = canon(new Apply(a.head(), List.of(equation, current.args().get(1))));
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
          if (!names.get(k).equals(original.get(k)) && !occurs(value, original.get(k))) {
            value = Substitution.rename(value, names.get(k).name(), original.get(k));
            renamed = true;
          }
        }
      }
      if (ctx.traceEnabled() && !renamed && !r.trace().rewrites().isEmpty()) {
        commandTrace = r.trace();
      }
      if (occurs(value, parameter)) {
        Expr given = frame == null ? null : frame.lookup(parameter.name());
        if (given != null) {
          value = Substitution.apply(value, new TreeMap<>(Map.of(parameter.name(), given)));
        } else {
          parameters.put(parameter.name(), parameter);
        }
      }
      return canon(value);
    }

    // Phase 7b: differential equations.

    /**
     * {@code dsolve(equations, unknowns, conditions…)} ({@code docs/language.md} §14.22): the
     * unknowns read from the second argument as written, the equations evaluated with the unknown
     * functions declared and the variable bound, the initial conditions with the functions
     * declared; then {@link Dsolve} under the assumptions without facts about the functions, with
     * fresh constants of integration {@code C_1, C_2, …}. A constant left in the value is collected
     * for the statement's note, as a general solution's parameter is.
     */
    private Expr dsolve(Apply a) {
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
      Expr call = canon(new Apply(a.head(), callArgs));
      Assumptions facts = env.assumptions();
      for (Symbol f : functions) {
        for (int n = 0; n <= Dsolve.MAX_ORDER; n++) {
          facts = facts.without(Symbol.of(f.name() + "'".repeat(n)));
        }
      }
      Context ctx =
          new Context(
              facts,
              mc,
              traced && a == root,
              Math.toIntExact(REWRITE_CAP + REWRITE_CAP_PER_NODE * checkSize(call, a)),
              Providers.defaults(),
              new SessionNumerics(0, cancellation),
              cancellation);
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
        throw new EvaluationException(
            "dsolve did not finish (rule set " + ex.ruleSet() + ", rule " + ex.rule() + ")", a);
      }
      Expr value = canon(r.expr());
      for (Symbol c : introduced) {
        if (occurs(value, c)) {
          Expr given = frame == null || bound.contains(c.name()) ? null : frame.lookup(c.name());
          if (given != null) {
            // the constant is free in the result: its value here is put in, as a parameter's
            value = canon(Substitution.apply(value, new TreeMap<>(Map.of(c.name(), given))));
          } else {
            constants.put(c.name(), c);
          }
        }
      }
      if (ctx.traceEnabled() && value.equals(r.expr())) {
        commandTrace = r.trace();
      }
      return value;
    }

    /**
     * Phase 8a: {@code rsolve(recurrence, a(n), conditions…)} ({@code docs/language.md} §14.24):
     * the arguments read as {@code dsolve}'s, one recurrence in one unknown; then {@link Rsolve}
     * with fresh constants {@code C₁, C₂, …}, collected for the note as {@code dsolve}'s are.
     */
    private Expr rsolve(Apply a) {
      List<Expr> args = a.args();
      OdeArgs prepared = odeArgs(a, args.subList(2, args.size()));
      if (prepared.functions().size() != 1 || prepared.eqs().size() != 1) {
        throw new EvaluationException("rsolve solves one recurrence in one unknown a(n)", a);
      }
      List<Symbol> functions = prepared.functions();
      List<Expr> callArgs = new ArrayList<>();
      callArgs.add(prepared.equations());
      callArgs.add(args.get(1));
      callArgs.addAll(prepared.conditions());
      Expr call = canon(new Apply(a.head(), callArgs));
      Context ctx =
          new Context(
              env.assumptions().without(functions.getFirst()),
              mc,
              traced && a == root,
              Math.toIntExact(REWRITE_CAP + REWRITE_CAP_PER_NODE * checkSize(call, a)),
              Providers.defaults(),
              new SessionNumerics(0, cancellation),
              cancellation);
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
        throw new EvaluationException(
            "rsolve did not finish (rule set " + ex.ruleSet() + ", rule " + ex.rule() + ")", a);
      }
      Expr value = canon(r.expr());
      for (Symbol c : introduced) {
        if (occurs(value, c)) {
          Expr given = frame == null || bound.contains(c.name()) ? null : frame.lookup(c.name());
          if (given != null) {
            value = canon(Substitution.apply(value, new TreeMap<>(Map.of(c.name(), given))));
          } else {
            constants.put(c.name(), c);
          }
        }
      }
      if (ctx.traceEnabled() && value.equals(r.expr()) && r.trace() != Trace.NONE) {
        commandTrace = r.trace();
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
    private Expr odesolve(Apply a) {
      List<Expr> args = new ArrayList<>(a.args());
      Expr stepsArg = null;
      Expr atArg = null;
      while (args.getLast() instanceof Relation r
          && r.op() == Relation.Op.EQ
          && r.left() instanceof Symbol option
          && (option.name().equals("steps") || option.name().equals("at"))) {
        if (option.name().equals("steps") ? stepsArg != null : atArg != null) {
          throw new EvaluationException("odesolve takes " + option.name() + " once", a);
        }
        if (option.name().equals("steps")) {
          stepsArg = r.right();
        } else {
          atArg = r.right();
        }
        args.removeLast();
      }
      if (stepsArg != null && atArg != null) {
        throw new EvaluationException("odesolve takes steps or at, not both", a);
      }
      if (args.size() < 5) {
        throw new EvaluationException(
            "odesolve needs equations, unknowns, initial conditions and an interval a, b", a);
      }
      OdeArgs prepared = odeArgs(a, args.subList(2, args.size() - 2));
      Expr lo = eval(args.get(args.size() - 2));
      Expr hi = eval(args.get(args.size() - 1));
      int steps = NumericOde.DEFAULT_STEPS;
      Expr at = null;
      if (atArg != null) {
        steps = 0;
        at = eval(atArg);
      } else if (stepsArg != null) {
        Expr n = eval(stepsArg);
        if (!(n instanceof Num num
            && num.value() instanceof Int k
            && k.value().signum() > 0
            && k.value().compareTo(BigInteger.valueOf(NumericOde.MAX_STEPS)) <= 0)) {
          throw new EvaluationException(
              "odesolve's steps must be an integer from 1 to " + NumericOde.MAX_STEPS, a);
        }
        steps = ((Int) ((Num) n).value()).value().intValueExact();
      }
      Assumptions facts = env.assumptions();
      for (Symbol f : prepared.functions()) {
        for (int n = 0; n <= Dsolve.MAX_ORDER; n++) {
          facts = facts.without(Symbol.of(f.name() + "'".repeat(n)));
        }
      }
      Context ctx =
          new Context(
              facts,
              mc,
              false,
              REWRITE_CAP,
              Providers.defaults(),
              new SessionNumerics(0, cancellation),
              cancellation);
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
                env.settings(),
                ctx);
      } catch (IllegalArgumentException wrong) {
        throw new EvaluationException(wrong.getMessage(), a);
      } catch (AlgebraLimitException limit) {
        throw new EvaluationException(limit.getMessage(), a);
      } catch (RewriteLimitException ex) {
        throw new EvaluationException(
            "odesolve did not finish (rule set " + ex.ruleSet() + ", rule " + ex.rule() + ")", a);
      }
      value = canon(value);
      quadrature = true;
      if (traced && atTop(a)) {
        Expr call = canon(a);
        Bindings digits =
            new Bindings(
                List.of(new Bindings.Binding("digits_", Num.of(NumericOde.DIGITS), null, null)));
        commandTrace =
            new Trace(
                call,
                value,
                List.of(
                    new Rewrite(
                        call, value, NumericOde.RULE_SET, NumericOde.STEP, Path.ROOT, digits)),
                List.of());
      }
      return value;
    }

    /** The arguments of {@code dsolve} or {@code odesolve}, read and evaluated (Phase 7b). */
    private record OdeArgs(
        List<Symbol> functions,
        Symbol variable,
        Expr equations,
        List<Relation> eqs,
        List<Expr> conditions,
        List<Relation> ics) {}

    /**
     * The unknowns read from the second argument as written; the equations evaluated with the
     * unknown functions declared and the variable bound; the initial conditions, {@code
     * conditionArgs}, with the functions declared.
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
              a.head().name()
                  + " needs an unknown function y(x), or a list of them in one variable",
              a);
        }
        variable = v;
        functions.add(f.head());
      }
      if (functions.isEmpty()) {
        throw new EvaluationException(
            a.head().name() + " needs an unknown function y(x), or a list of them in one variable",
            a);
      }
      Set<String> outer = unknownFunctions;
      unknownFunctions = new TreeSet<>(outer);
      for (Symbol f : functions) {
        unknownFunctions.add(f.name());
      }
      Expr equations;
      List<Expr> conditions = new ArrayList<>();
      try {
        equations = scoped(args.get(0), List.of(variable));
        for (Expr c : conditionArgs) {
          conditions.add(eval(c));
        }
      } finally {
        unknownFunctions = outer;
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
              a.head().name() + " needs an equation or a list of equations", a);
        }
        eqs.add(r);
      }
      List<Relation> ics = new ArrayList<>();
      for (Expr c : conditions) {
        if (!(c instanceof Relation r
            && r.op() == Relation.Op.EQ
            && Dsolve.isCondition(r, functions, variable))) {
          throw new EvaluationException(
              "an initial condition is y(a) = b or y'(a) = b, with a and b free of the unknowns",
              a);
        }
        ics.add(r);
      }
      return new OdeArgs(functions, variable, equations, eqs, conditions, ics);
    }

    /**
     * A constant of integration: {@code C_1}, {@code C_2}, … for the first that is not taken. A
     * name is taken when the held call or its value mentions it, it is an unknown function or bound
     * here, it has a definition, it has a fact other than {@code C ∈ ℝ} alone, or this statement
     * took it already. As for {@link #parameter}, a name the held call does not mention that has a
     * value in the current frame (a {@code subs} pair, a function's parameter) is not taken: its
     * value is put in for it afterwards.
     */
    private Symbol constant(
        List<Expr> held, Expr call, List<Symbol> functions, List<Symbol> introduced) {
      for (int n = 1; ; n++) {
        Symbol c = Symbol.of("C_" + n);
        Facts f = env.assumptions().facts(c);
        boolean mentioned = false;
        for (Expr e : held) {
          mentioned |= occurs(e, c);
        }
        if (!mentioned
            && !introduced.contains(c)
            && !functions.contains(c)
            && frame != null
            && frame.lookup(c.name()) != null
            && !bound.contains(c.name())) {
          return c;
        }
        boolean taken =
            mentioned
                || occurs(call, c)
                || functions.contains(c)
                || introduced.contains(c)
                || bound.contains(c.name())
                || constants.containsKey(c.name())
                || env.definition(c.name()).isPresent()
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
     * has a fact other than {@code k ∈ ℤ} alone (so the parameter of an earlier statement's solve
     * is used again), or an earlier solve of this statement took it. A name the held equations do
     * not mention that has a value in the current frame (a {@code subs} pair, a function's
     * parameter) is not taken: its value is put in for it afterwards.
     */
    private Symbol parameter(Expr held, Expr equations, List<Symbol> unknowns) {
      for (int n = 0; ; n++) {
        Symbol k = Symbol.of(n == 0 ? "k" : "k_" + n);
        boolean mentioned = occurs(held, k);
        if (!mentioned
            && frame != null
            && frame.lookup(k.name()) != null
            && !bound.contains(k.name())) {
          return k;
        }
        Facts f = env.assumptions().facts(k);
        boolean taken =
            mentioned
                || occurs(equations, k)
                || unknowns.contains(k)
                || bound.contains(k.name())
                || parameters.containsKey(k.name())
                || env.definition(k.name()).isPresent()
                || !(f.equals(Facts.NONE) || f.equals(INTEGER));
        if (!taken) {
          return k;
        }
      }
    }

    /** The context of a geometry solver: the facts, the precision, the session's numerics. */
    private Context geometryContext(boolean traced) {
      return new Context(
          env.assumptions(),
          mc,
          traced,
          REWRITE_CAP,
          Providers.defaults(),
          new SessionNumerics(env.settings().angleMode().halfTurn(), cancellation),
          cancellation);
    }

    /** A shape formula, coordinate-geometry or conic function ({@link Shapes}). */
    private Expr shape(Apply a, List<Expr> args) {
      try {
        Expr value = new Shapes(geometryContext(false)).apply(a.head().name(), args);
        return value == null ? canon(Apply.of(a.head(), args)) : value;
      } catch (IllegalArgumentException wrong) {
        throw new EvaluationException(wrong.getMessage(), a);
      }
    }

    /**
     * {@code triangle(p = v, …)}: each part's name held, its value evaluated; a plain number given
     * for an angle is in the session's unit, any other angle in radians. A decimal among the values
     * makes every part a decimal at the session's precision. A value with a symbol, or a comparison
     * that cannot be decided, leaves the call ({@link Triangle}).
     */
    private Expr triangle(Apply a) {
      Map<String, Expr> given = new TreeMap<>();
      List<Expr> evaluated = new ArrayList<>();
      boolean symbolic = false;
      boolean inexact = false;
      int half = env.settings().angleMode().halfTurn();
      for (Expr arg : a.args()) {
        if (!(arg instanceof Relation r
            && r.op() == Relation.Op.EQ
            && r.left() instanceof Symbol name
            && List.of("a", "b", "c", "A", "B", "C").contains(name.name()))) {
          throw new EvaluationException(
              "triangle takes three parts such as a = 3 or C = 90°, from a b c A B C", arg);
        }
        if (given.containsKey(name.name())) {
          throw new EvaluationException("triangle's parts must be distinct", arg);
        }
        Expr value = eval(r.right());
        evaluated.add(new Relation(Relation.Op.EQ, name, value));
        if (value instanceof Num n && !(n.value() instanceof RealNumeric)) {
          throw new EvaluationException("triangle's parts must be real numbers", arg);
        }
        symbolic |= occursSymbol(value);
        inexact |= hasInexact(value);
        boolean angle = Character.isUpperCase(name.name().charAt(0));
        if (angle && half != 0 && value instanceof Num) {
          value = canon(Exprs.div(Product.of(value, Constant.PI), Num.of(half)));
        }
        given.put(name.name(), value);
      }
      Expr stays = canon(Apply.of(a.head(), evaluated));
      if (symbolic) {
        return stays;
      }
      Context ctx = geometryContext(traced && a == root);
      Optional<Rewriter.Result> r;
      try {
        r = Triangle.solve(given, ctx);
      } catch (IllegalArgumentException wrong) {
        throw new EvaluationException(wrong.getMessage(), a);
      }
      if (r.isEmpty()) {
        return stays;
      }
      if (ctx.traceEnabled()) {
        commandTrace = r.get().trace();
      }
      Expr value = canon(r.get().expr());
      // decimal data give decimal parts, as solving a decimal polynomial does
      return inexact ? NumericForm.numeric(value, mc.getPrecision(), a, cancellation) : value;
    }

    /** Whether {@code e} holds an inexact number. */
    private static boolean hasInexact(Expr e) {
      Deque<Expr> todo = new ArrayDeque<>();
      todo.push(e);
      while (!todo.isEmpty()) {
        Expr n = todo.pop();
        if (n instanceof Num num && !num.value().isExact()) {
          return true;
        }
        for (Expr op : Operands.of(n)) {
          todo.push(op);
        }
      }
      return false;
    }

    /** Whether {@code e} holds a symbol. */
    private static boolean occursSymbol(Expr e) {
      Deque<Expr> todo = new ArrayDeque<>();
      todo.push(e);
      while (!todo.isEmpty()) {
        Expr n = todo.pop();
        if (n instanceof Symbol) {
          return true;
        }
        for (Expr op : Operands.of(n)) {
          todo.push(op);
        }
      }
      return false;
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
     * {@code convert(q, unit)} (§14.26): the quantity in the target's units with exact factors
     * ({@link Convert}), its step kept when the call is the statement itself.
     */
    private Expr convert(Apply a) {
      Expr q = eval(a.args().getFirst());
      Expr target = eval(a.args().get(1));
      dimension(q, a);
      try {
        return keepTrace(a, Convert.of(q, target, algebraContext(a, List.of(q, target))));
      } catch (Convert.ConvertException x) {
        throw new EvaluationException(x.getMessage(), a);
      }
    }

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
     * A linear-algebra function on its evaluated arguments ({@link Linalg}), its steps kept when
     * the call is the statement itself; a wrong argument or a limit of the algebra is an evaluation
     * error at the call.
     */
    private Expr linalg(Apply a, List<Expr> args) {
      Context ctx = algebraContext(a, args).withNumerics(new SessionNumerics(0, cancellation));
      // the numeric rule's steps are kept at the top of a numeric(…) too, as an integral's are
      boolean top =
          a == root
              || root instanceof Apply r
                  && r.head().name().equals("numeric")
                  && r.args().getFirst() == a;
      try {
        Linalg.Outcome out =
            Linalg.apply(new Apply(a.head(), args), ctx.withTrace(traced && top), quadratureDigits);
        quadrature |= out.numeric();
        if (traced && top) {
          commandTrace = out.result().trace();
        }
        return out.result().expr();
      } catch (IllegalArgumentException wrong) {
        throw new EvaluationException(wrong.getMessage(), a);
      } catch (AlgebraLimitException limit) {
        throw new EvaluationException(limit.getMessage(), a);
      }
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
        Expr value = NumericForm.numeric(NumberTheory.plain(inner), digits, a, cancellation);
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
      NumericIntegral.Outcome out =
          NumericIntegral.of(
              value,
              quadratureDigits,
              env.settings().angleMode() == AngleMode.RADIANS,
              traced && atTop(node),
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

    /** Whether a node is the statement, or the argument of a {@code numeric} that is. */
    private boolean atTop(Expr node) {
      return node == root
          || root instanceof Apply r
              && r.head().name().equals("numeric")
              && r.args().getFirst() == node;
    }

    /**
     * A numeric decomposition ({@link NumericDecompositions}): its value marks the statement as
     * numeric, and its step, or the note when the call stays, is kept when the call is the
     * statement, or the argument of a {@code numeric} that is.
     */
    private Expr decomposition(Apply a, List<Expr> args) {
      NumericDecompositions.Outcome out =
          NumericDecompositions.of(
              a.head(),
              args,
              mc,
              new SessionNumerics(env.settings().angleMode().halfTurn(), cancellation),
              traced && atTop(a),
              a,
              cancellation);
      quadrature |= out.numeric();
      if (out.step() != null) {
        commandTrace = out.step();
      }
      return out.value();
    }

    /** The most names a truth table takes (Phase 8a). */
    static final int MAX_TRUTH_TABLE_NAMES = 10;

    /**
     * Phase 8a: {@code truthtable(e, [p, q, …])} ({@code docs/language.md} §14.24): a matrix whose
     * first row is the names and {@code e}, evaluated with the names bound, and then a row for each
     * assignment of truth values, every name {@code true} first and the last name changing fastest:
     * the values and {@code e}'s value under them, which must simplify to a truth value.
     */
    private Expr truthtable(Apply a) {
      List<Expr> spec =
          switch (a.args().get(1)) {
            case ListExpr l -> l.items();
            case Vector v -> v.items();
            default -> null;
          };
      List<Symbol> names = new ArrayList<>();
      if (spec != null) {
        for (Expr item : spec) {
          if (!(item instanceof Symbol s) || names.contains(s)) {
            spec = null;
            break;
          }
          names.add(s);
        }
      }
      if (spec == null) {
        throw new EvaluationException(
            "truthtable takes an expression and a list of distinct names [p, q, …]", a);
      }
      if (names.size() > MAX_TRUTH_TABLE_NAMES) {
        throw new EvaluationException(
            "truthtable takes at most " + MAX_TRUTH_TABLE_NAMES + " names", a);
      }
      Expr held = a.args().getFirst();
      List<Expr> header = new ArrayList<>(names);
      header.add(scoped(held, names));
      List<List<Expr>> rows = new ArrayList<>(List.of(header));
      Context ctx =
          new Context(env.assumptions(), mc, false, REWRITE_CAP).withCancellation(cancellation);
      int n = names.size();
      for (int row = 0; row < 1 << n; row++) {
        cancellation.check();
        TreeMap<String, Expr> values = new TreeMap<>();
        List<Expr> cells = new ArrayList<>();
        for (int i = 0; i < n; i++) {
          Bool b = Bool.of((row >> (n - 1 - i) & 1) == 0);
          values.put(names.get(i).name(), b);
          cells.add(b);
        }
        Set<String> others = new TreeSet<>(bound);
        others.removeAll(values.keySet());
        Expr value = inFrame(new Frame(values, frame), others, held);
        Expr decided = Simplifier.simplify(canon(value), ctx).expr();
        if (!(decided instanceof Bool)) {
          throw new EvaluationException(
              "truthtable: the names do not decide "
                  + mathcanvas.engine.print.UnicodePrinter.INSTANCE.display(decided),
              a);
        }
        cells.add(decided);
        rows.add(cells);
      }
      return Matrix.of(rows);
    }

    /**
     * A statistics function on its evaluated arguments ({@link Statistics}): a numeric value marks
     * the statement as numeric, and the steps are kept when the call is the statement, or the
     * argument of a {@code numeric} that is; under {@code numeric} or a decimal format a discrete
     * law past its exact bound is evaluated through the provider, with its step and its mark; a
     * wrong argument is an evaluation error at the call.
     */
    private Expr statistics(Apply a, List<Expr> args) {
      Context ctx = algebraContext(a, args).withNumerics(new SessionNumerics(0, cancellation));
      boolean top = traced && atTop(a);
      try {
        Statistics.Outcome out =
            Statistics.apply(new Apply(a.head(), args), ctx.withTrace(top), quadratureDigits != 0);
        quadrature |= out.numeric();
        if (top && out.result().trace() != Trace.NONE) {
          commandTrace = out.result().trace();
        }
        return out.result().expr();
      } catch (IllegalArgumentException wrong) {
        throw new EvaluationException(wrong.getMessage(), a);
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

    // Derivatives.

    /**
     * A derivative typed as a node: a mixed one ({@code ∂²/∂y∂x f}, a derivative of a derivative)
     * has its inner derivatives' scopes evaluated without differentiating them ({@link #deferred}),
     * so the whole chain is differentiated in one run of the rules, with one trace.
     */
    private Expr derivative(Derivative d, Expr typed) {
      if (d.order() == 1) {
        Expr rule = chainRule(d.expr(), d.var(), typed);
        if (rule != null) {
          return rule;
        }
      }
      if (d.expr() instanceof Derivative) {
        deferred = d.expr();
      }
      try {
        return differentiated(binder(d, List.of(d.var())), d.var(), typed, chainVars(d.expr()));
      } finally {
        deferred = null;
      }
    }

    /**
     * The derivative in {@code x} of a call of a defined function of two or more parameters with an
     * argument that is not a name, by the chain rule in several variables ({@link ChainRule}), or
     * null when the held expression is not such a call or {@code x} could capture a name in it. The
     * arguments are evaluated in the scope of {@code x}, the body with each parameter itself (a
     * fresh name when an argument holds it); the variable is free in the result.
     */
    private Expr chainRule(Expr held, Symbol x, Expr typed) {
      if (folding
          || !(held instanceof Apply call)
          || Builtins.lookup(call.head().name()).isPresent()
          || !(env.definition(call.head().name()).orElse(null) instanceof Definition.Function fn)
          || fn.params().size() < 2
          || call.args().size() != fn.params().size()
          || call.args().stream().allMatch(arg -> arg instanceof Symbol)
          || captureFree(Derivative.of(held, x, 1), x, List.of(held)) != null) {
        return null;
      }
      List<Expr> args = new ArrayList<>();
      for (Expr arg : call.args()) {
        args.add(scoped(arg, List.of(x)));
      }
      if (args.stream().noneMatch(arg -> !(arg instanceof Symbol) && occurs(arg, x))) {
        // no argument but a name depends on x: the plain derivative says it all
        return null;
      }
      List<Symbol> params = new ArrayList<>();
      TreeMap<String, Expr> frameValues = new TreeMap<>();
      Expr all = new ListExpr(args);
      for (Symbol p : fn.params()) {
        Symbol q = occurs(all, p) || p.equals(x) ? freshFor(Apply.of(p, all, x), p) : p;
        params.add(q);
        frameValues.put(p.name(), q);
      }
      Expr body;
      push(call.head().name(), call);
      try {
        body = inFrame(new Frame(frameValues, null), new TreeSet<>(), fn.body());
      } finally {
        expanding.removeLast();
      }
      Assumptions facts = env.assumptions();
      for (String name : bound) {
        facts = facts.without(Symbol.of(name));
      }
      facts = facts.without(x);
      for (Symbol q : params) {
        facts = facts.without(q);
      }
      long nodes = checkSize(body, held);
      boolean whole = traced && typed == root;
      Context context =
          new Context(facts, mc, whole, Math.toIntExact(REWRITE_CAP + REWRITE_CAP_PER_NODE * nodes))
              .withCancellation(cancellation);
      Rewriter.Result r;
      try {
        r = ChainRule.of(call.head(), params, body, args, x, context);
      } catch (RewriteLimitException ex) {
        throw new EvaluationException(
            "differentiation did not finish (rule set "
                + ex.ruleSet()
                + ", rule "
                + ex.rule()
                + ")",
            held);
      }
      if (whole) {
        commandTrace = r.trace();
      }
      return freeVariable(r.expr(), x, x);
    }

    /** The variables of a chain of derivatives, outermost first. */
    private static List<Symbol> chainVars(Expr e) {
      List<Symbol> out = new ArrayList<>();
      while (e instanceof Derivative d) {
        out.add(d.var());
        e = d.expr();
      }
      return out;
    }

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
      return differentiated(node, written, typed, List.of());
    }

    /**
     * {@link #differentiated(Expr, Expr, Expr)} with the inner variables of a mixed derivative as
     * written, outermost first, each free in the result as the outer one is.
     */
    private Expr differentiated(Expr node, Expr written, Expr typed, List<Symbol> writtenInner) {
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
      // the variables of a mixed derivative's inner derivatives are free in the result too
      List<Symbol> inner = chainVars(d.expr());
      for (int k = 0; k < inner.size(); k++) {
        Symbol w = writtenInner.size() == inner.size() ? writtenInner.get(k) : inner.get(k);
        result = freeVariable(result, inner.get(k), w);
      }
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
     * occurs free in the result; a definite integral binds its variable, so its value has none, and
     * its value is folded as evaluation leaves one (functions of decimals evaluated).
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
      // the ranges of the outer levels of an iterated integral
      for (Map.Entry<String, Relation[]> r : ranges.entrySet()) {
        if (bound.contains(r.getKey()) && !r.getKey().equals(in.var().name())) {
          try {
            facts = facts.with(r.getValue()[0]).with(r.getValue()[1]);
          } catch (IllegalArgumentException contradicts) {
            // a fact about the name already: the range adds nothing
          }
        }
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
      } else if (in.isDefinite() && !(result instanceof Integral)) {
        // a definite integral's value as evaluation leaves it: functions of decimals folded
        // (log(15.625) in an antiderivative evaluated at the bounds), so evaluating it again
        // changes nothing
        boolean outer = folding;
        folding = true;
        try {
          result = inFrame(null, bound, result);
        } finally {
          folding = outer;
        }
      }
      if (traced && innerLevel(typed)) {
        levels.put(typed, new Expr[] {canon(in), result});
      }
      if (whole) {
        commandTrace = carried(iterated(typed, in, r.trace()), result);
      }
      return result;
    }

    /**
     * A command's trace carried to its value when the value was folded after the operation: a
     * normalisation at the root puts the folded value in place, so the statement's trace still
     * chains to the simplifier's.
     */
    private static Trace carried(Trace t, Expr value) {
      if (t == Trace.NONE || value.equals(t.result())) {
        return t;
      }
      List<Trace.Normalisation> ns = new ArrayList<>(t.normalisations());
      ns.add(new Trace.Normalisation(t.rewrites().size(), Path.ROOT, value));
      return new Trace(t.initial(), value, t.rewrites(), ns);
    }

    /**
     * The facts {@code min ≤ x ≤ max} of a definite integral's variable when its bounds evaluate to
     * constants and the facts are of an accepted form; null otherwise.
     */
    private Relation[] range(Integral i) {
      if (!i.isDefinite() || folding) {
        return null;
      }
      Expr lo = eval(i.lower());
      Expr hi = eval(i.upper());
      if (!constantValue(lo) || !constantValue(hi)) {
        return null;
      }
      for (Expr[] pair : List.of(new Expr[] {lo, hi}, new Expr[] {hi, lo})) {
        Relation ge = (Relation) canon(Relation.of(Relation.Op.GE, i.var(), pair[0]));
        Relation le = (Relation) canon(Relation.of(Relation.Op.LE, i.var(), pair[1]));
        try {
          Assumptions.EMPTY.with(ge).with(le);
          return new Relation[] {ge, le};
        } catch (IllegalArgumentException notAccepted) {
          // the other order, or no facts
        }
      }
      return null;
    }

    private static boolean constantValue(Expr e) {
      if (e instanceof Symbol || e.equals(Constant.INFINITY) || e.equals(Constant.UNDEFINED)) {
        return false;
      }
      for (Expr o : Operands.of(e)) {
        if (!constantValue(o)) {
          return false;
        }
      }
      return true;
    }

    /** Whether {@code typed} is an inner level of the iterated integral that is the statement. */
    private boolean innerLevel(Expr typed) {
      for (Expr e = root; e instanceof Integral i && i.isDefinite(); e = i.integrand()) {
        if (i.integrand() == typed) {
          return true;
        }
      }
      return false;
    }

    /**
     * The trace of an iterated integral that is the statement: one {@code integrate.iterated} step
     * per inner level, innermost first, each from the whole integral with that level as it was
     * integrated to the same with its value, then the outermost level's own trace. A level whose
     * value is still an integral, or one not recorded, leaves the outer trace alone.
     */
    private Trace iterated(Expr typed, Integral in, Trace outer) {
      List<Expr[]> inner = new ArrayList<>();
      for (Expr e = typed; e instanceof Integral i && i.integrand() instanceof Integral; ) {
        e = i.integrand();
        Expr[] level = levels.get(e);
        if (level == null || containsIntegral(level[1])) {
          return outer;
        }
        inner.add(level);
      }
      if (inner.isEmpty()) {
        return outer;
      }
      List<Rewrite> rewrites = new ArrayList<>();
      for (int j = inner.size() - 1; j >= 0; j--) {
        Expr before = wrap(inner, j, inner.get(j)[0], in);
        Expr after = wrap(inner, j, inner.get(j)[1], in);
        TreeMap<String, Expr> values = new TreeMap<>();
        values.put("integral", inner.get(j)[0]);
        values.put("result", canon(inner.get(j)[1]));
        rewrites.add(
            new Rewrite(
                before,
                after,
                Calculus.INTEGRATE,
                Calculus.ITERATED,
                Path.ROOT,
                Bindings.EMPTY,
                values));
      }
      Trace own =
          new Trace(rewrites.getFirst().before(), rewrites.getLast().after(), rewrites, List.of());
      return own.result().equals(outer.initial()) ? Trace.concat(own, outer) : outer;
    }

    /**
     * Level {@code j}'s integral replaced by {@code x} inside the levels around it, as they were
     * integrated: level {@code j − 1}'s integral with {@code x} for its integrand, and so on out to
     * the outermost, {@code in}.
     */
    private Expr wrap(List<Expr[]> inner, int j, Expr x, Integral in) {
      Expr e = x;
      for (int k = j - 1; k >= -1; k--) {
        Integral around = k >= 0 ? (Integral) inner.get(k)[0] : in;
        e = Integral.definite(e, around.var(), around.lower(), around.upper());
      }
      return canon(e);
    }

    private static boolean containsIntegral(Expr e) {
      if (e instanceof Integral) {
        return true;
      }
      for (Expr o : Operands.of(e)) {
        if (containsIntegral(o)) {
          return true;
        }
      }
      return false;
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
     * {@code lineintegral(f, r, t, a, b)} and {@code surfaceintegral(f, r, [u, a, b], [v, c, d])}
     * ({@link VectorIntegrals}): the field evaluated with {@code x, y, z} bound, the path or
     * surface with its parameters bound, an inner bracket's bounds with the outer parameter bound,
     * the other bounds evaluated; the facts without those of the enclosing binders' names, the
     * parameters and the coordinates; the solver's providers and numerics. The reduced integral
     * that stays goes on to the numeric path.
     */
    private Expr vectorIntegral(Apply a) {
      if (folding) {
        return a;
      }
      String name = a.head().name();
      List<Expr> args = a.args();
      List<Symbol> coordinates = VectorIntegrals.COORDINATES;
      Expr field = scoped(args.getFirst(), coordinates);
      List<Symbol> parameters = new ArrayList<>();
      List<Expr> bounds = new ArrayList<>();
      Expr shape;
      if (name.equals("lineintegral")) {
        if (!(args.get(2) instanceof Symbol t)) {
          throw new EvaluationException("lineintegral needs a variable", a);
        }
        parameters.add(t);
        shape = scoped(args.get(1), List.of(t));
        bounds.add(eval(args.get(3)));
        bounds.add(eval(args.get(4)));
      } else {
        List<Expr> inner = bracket(args.get(2));
        List<Expr> outer = bracket(args.get(3));
        if (inner == null || outer == null) {
          throw new EvaluationException(
              "surfaceintegral needs brackets [u, a, b] and [v, c, d]", a);
        }
        Symbol u = (Symbol) inner.getFirst();
        Symbol v = (Symbol) outer.getFirst();
        if (u.equals(v)) {
          throw new EvaluationException("surfaceintegral needs two parameters", a);
        }
        parameters.add(u);
        parameters.add(v);
        shape = scoped(args.get(1), List.of(u, v));
        bounds.add(scoped(inner.get(1), List.of(v)));
        bounds.add(scoped(inner.get(2), List.of(v)));
        bounds.add(eval(outer.get(1)));
        bounds.add(eval(outer.get(2)));
      }
      // a parameter the field mentions is a free symbol there, never the parameter: the parameter
      // takes a fresh name in the path or surface and the bounds, as integrate's capture check does
      for (int k = 0; k < parameters.size(); k++) {
        Symbol p = parameters.get(k);
        if (occurs(field, p)) {
          Symbol fresh = freshFor(Apply.of(p, field, shape, new ListExpr(bounds)), p);
          TreeMap<String, Expr> renaming = new TreeMap<>(Map.of(p.name(), fresh));
          shape = Substitution.apply(shape, renaming);
          bounds.replaceAll(bd -> Substitution.apply(bd, renaming));
          parameters.set(k, fresh);
        }
      }
      Assumptions facts = env.assumptions();
      for (String bn : bound) {
        facts = facts.without(Symbol.of(bn));
      }
      for (Symbol s : parameters) {
        facts = facts.without(s);
      }
      for (Symbol s : coordinates) {
        facts = facts.without(s);
      }
      long nodes = checkSize(a, a);
      boolean whole = traced && a == root;
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
        throw new EvaluationException(
            name + " did not finish (rule set " + ex.ruleSet() + ", rule " + ex.rule() + ")", a);
      }
      if (whole) {
        commandTrace = r.trace();
      }
      return r.expr();
    }

    /**
     * {@code laplace(f, t, s)} or {@code ilaplace(F, s, t)} whose first argument has been evaluated
     * with its variable bound ({@link Laplace#of}): the facts without those of the enclosing
     * binders' names and the two variables. The result's variable, held, must be a name; it is free
     * in the result, as a derivative's variable is, so a value it has here is put in.
     */
    private Expr transform(Expr node, Apply typed) {
      if (folding || !(node instanceof Apply call)) {
        return node;
      }
      String name = call.head().name();
      if (!(call.args().get(1) instanceof Symbol v) || !(call.args().get(2) instanceof Symbol w)) {
        throw new EvaluationException(name + " needs two variables", node);
      }
      if (v.equals(w)) {
        throw new EvaluationException(name + " needs two different variables", node);
      }
      Assumptions facts = env.assumptions();
      for (String bn : bound) {
        facts = facts.without(Symbol.of(bn));
      }
      facts = facts.without(v).without(w);
      long nodes = checkSize(call, call);
      boolean whole = traced && typed == root;
      Context context =
          new Context(facts, mc, whole, Math.toIntExact(REWRITE_CAP + REWRITE_CAP_PER_NODE * nodes))
              .withCancellation(cancellation);
      Rewriter.Result r;
      try {
        r = Laplace.of(call, context);
      } catch (AlgebraLimitException limit) {
        throw new EvaluationException(limit.getMessage(), node);
      } catch (RewriteLimitException ex) {
        throw new EvaluationException(
            name + " did not finish (rule set " + ex.ruleSet() + ", rule " + ex.rule() + ")", node);
      }
      Expr result =
          r.expr() instanceof Apply stays && stays.head().equals(call.head())
              ? r.expr()
              : freeVariable(r.expr(), w, w);
      if (whole) {
        Trace t = r.trace();
        if (!result.equals(t.result())) {
          // the folded result: a normalisation at the root carries the trace to it
          List<Trace.Normalisation> ns = new ArrayList<>(t.normalisations());
          ns.add(new Trace.Normalisation(t.rewrites().size(), Path.ROOT, result));
          t = new Trace(t.initial(), result, t.rewrites(), ns);
        }
        commandTrace = t;
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
     * {@code lagrange(f, constraints, variables)} whose function and constraints have been
     * evaluated in the scope of its variables ({@link Lagrange#of}), under the solver's context:
     * the facts without those of the enclosing binders' names and the variables, the default
     * providers and the session's numerics. A variable the capture check renamed is named back in
     * the points unless its written name occurs in them from outside, as {@code solve} does.
     */
    private Expr lagrange(Expr node, Apply typed) {
      if (folding || !(node instanceof Apply call)) {
        return node;
      }
      Assumptions facts = env.assumptions();
      for (String name : bound) {
        facts = facts.without(Symbol.of(name));
      }
      List<Symbol> vars = Substitution.boundBy(call);
      for (Symbol v : vars) {
        facts = facts.without(v);
      }
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
        r =
            Lagrange.of(
                call,
                context,
                m ->
                    occurs(typed, m)
                        || bound.contains(m.name())
                        || env.definition(m.name()).isPresent()
                        || (frame != null && frame.lookup(m.name()) != null)
                        || !env.assumptions().facts(m).equals(Facts.NONE));
      } catch (IllegalArgumentException wrong) {
        throw new EvaluationException(wrong.getMessage(), node);
      } catch (AlgebraLimitException limit) {
        throw new EvaluationException(limit.getMessage(), node);
      } catch (RewriteLimitException ex) {
        throw new EvaluationException(
            "lagrange did not finish (rule set " + ex.ruleSet() + ", rule " + ex.rule() + ")",
            node);
      }
      Expr result = r.expr();
      List<Symbol> written = Substitution.boundBy(typed);
      if (result instanceof SetExpr && written.size() == vars.size()) {
        for (int k = 0; k < vars.size(); k++) {
          if (!vars.get(k).equals(written.get(k)) && !occurs(result, written.get(k))) {
            result =
                canon(
                    Substitution.apply(
                        result, new TreeMap<>(Map.of(vars.get(k).name(), written.get(k)))));
          }
        }
      }
      if (whole) {
        commandTrace = r.trace();
      }
      return result;
    }

    /**
     * The value of a vector calculus command whose function or field has been evaluated in the
     * scope of its variables ({@link Multivariable#of}): the facts without those about the
     * enclosing binders' names and the variables, the cap for the call's size and the cancellation.
     * A wrong argument is the command's error. Each variable is free in the result, as a
     * derivative's is: a value its name as written has here is put in, and a fresh name the capture
     * check gave it is renamed back.
     */
    private Expr vectorCalculus(Expr node, Apply typed) {
      if (folding || !(node instanceof Apply call)) {
        return node;
      }
      Assumptions facts = env.assumptions();
      for (String name : bound) {
        facts = facts.without(Symbol.of(name));
      }
      List<Symbol> vars = Substitution.boundBy(call);
      for (Symbol v : vars) {
        facts = facts.without(v);
      }
      long nodes = checkSize(call, call);
      boolean whole = traced && typed == root;
      Context context =
          new Context(facts, mc, whole, Math.toIntExact(REWRITE_CAP + REWRITE_CAP_PER_NODE * nodes))
              .withCancellation(cancellation);
      Rewriter.Result r;
      try {
        r = Multivariable.of(call, context);
      } catch (IllegalArgumentException wrong) {
        throw new EvaluationException(wrong.getMessage(), node);
      } catch (RewriteLimitException ex) {
        throw new EvaluationException(
            call.head().name()
                + " did not finish (rule set "
                + ex.ruleSet()
                + ", rule "
                + ex.rule()
                + ")",
            node);
      }
      List<Symbol> written = Substitution.boundBy(typed);
      Expr result = r.expr();
      for (int k = 0; k < vars.size(); k++) {
        Symbol w = written.size() == vars.size() ? written.get(k) : vars.get(k);
        result = freeVariable(result, vars.get(k), w);
      }
      if (whole) {
        commandTrace = r.trace();
      }
      return result;
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
