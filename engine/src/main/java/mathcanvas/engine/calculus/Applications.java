package mathcanvas.engine.calculus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;
import mathcanvas.engine.simplify.Simplifier;
import mathcanvas.engine.solve.Solver;

/**
 * The applications of integration and the optimisation helpers ({@code docs/language.md} §14.15):
 * {@code arclength}, {@code area}, {@code volume}, {@code shellvolume}, {@code extrema}, {@code
 * maximize} and {@code minimize}. Each sets its quantity up as a textbook would and hands every
 * integral to {@link Calculus#integrate} and every root to {@link Solver#solve}; nothing here
 * integrates by its own means. The steps are rewrites of the whole expression in the rule set
 * {@code applications}, named {@code <command>.<step>}, followed for a single integral by that
 * integral's own steps.
 *
 * <p>The unsigned quantities need {@code a ≤ b}: equal bounds give 0, {@code a > b} is an error,
 * and an order the sign decision ({@link Definite#sign}) cannot settle leaves the call as it is.
 * Zeros and poles are used only where the sets the solver gives are provably complete: the
 * expression is a rational function of the variable alone with rational coefficients, whose
 * numerator and denominator the solver solves or isolates by Sturm's theorem; any other root set,
 * or an undecidable sign, leaves the call with the note {@code <command>.none}, or for an area or
 * volume the integral of the absolute value with the note {@code <command>.crossings}, handed to
 * {@link Calculus#integrate} and then to the numeric path.
 */
public final class Applications {

  private Applications() {}

  /** The rule set of the steps. */
  public static final String RULE_SET = "applications";

  /** The steps the commands record, {@code <command>.<step>}, each with its narration text. */
  public static final Set<String> STEPS =
      Set.of(
          "arclength.formula",
          "arclength.curve",
          "arclength.integrand",
          "arclength.empty",
          "arclength.none");

  /**
   * Evaluates an application command whose arguments have been evaluated, its variable bound.
   *
   * @param call the call, {@code arclength(f, x, a, b)} and the others
   * @param context the assumptions (without facts about the bound variable), precision, trace, cap,
   *     providers and numerics
   * @return the value and its trace; the call itself, or the integral it set up, when it cannot be
   *     settled
   * @throws IllegalArgumentException with the language's message for a wrong argument
   */
  public static Rewriter.Result of(Apply call, Context context) {
    Steps steps = new Steps(Canonical.of(call, context.precision()), context);
    String name = call.head().name();
    List<Expr> args = call.args();
    return switch (name) {
      case "arclength" ->
          arclength(steps, args.get(0), variable(args.get(1)), args.get(2), args.get(3));
      default -> throw new IllegalStateException("not an application command: " + name);
    };
  }

  private static Symbol variable(Expr e) {
    if (!(e instanceof Symbol s)) {
      throw new IllegalArgumentException("the variable must be a name");
    }
    return s;
  }

  /** The steps of one command, each a rewrite of the whole expression, chained. */
  private static final class Steps {
    final Expr call;
    final Context context;
    final List<Rewrite> rewrites = new ArrayList<>();
    Expr current;

    Steps(Expr call, Context context) {
      this.call = call;
      this.context = context;
      this.current = call;
    }

    String command() {
      return ((Apply) call).head().name();
    }

    /** Records the step {@code <command>.<step>} to {@code after}, with named values. */
    void step(String step, Expr after, TreeMap<String, Expr> values) {
      Expr next = Canonical.of(after, context.precision());
      if (context.traceEnabled()) {
        rewrites.add(
            new Rewrite(
                current,
                next,
                RULE_SET,
                command() + "." + step,
                Path.ROOT,
                Bindings.EMPTY,
                values));
      }
      current = next;
    }

    Rewriter.Result result() {
      return new Rewriter.Result(current, trace());
    }

    Trace trace() {
      return context.traceEnabled() ? new Trace(call, current, rewrites, List.of()) : Trace.NONE;
    }

    /** The call as it is, with the note {@code <command>.none}. */
    Rewriter.Result none() {
      step("none", current, new TreeMap<>());
      return result();
    }

    /** These steps, then an integral's own ({@link Calculus#integrate}) from where they ended. */
    Rewriter.Result then(Rewriter.Result integral) {
      if (!context.traceEnabled()) {
        return new Rewriter.Result(integral.expr(), Trace.NONE);
      }
      return new Rewriter.Result(integral.expr(), Trace.concat(trace(), integral.trace()));
    }
  }

  private static TreeMap<String, Expr> values(Object... pairs) {
    TreeMap<String, Expr> out = new TreeMap<>();
    for (int k = 0; k < pairs.length; k += 2) {
      out.put((String) pairs[k], (Expr) pairs[k + 1]);
    }
    return out;
  }

  private static Expr simplify(Expr e, Context ctx) {
    return Simplifier.simplify(e, ctx.withTrace(false)).expr();
  }

  private static Expr derivative(Expr f, Symbol x, Context ctx) {
    return Calculus.differentiate(f, x, 1, ctx.withTrace(false)).expr();
  }

  private static Expr minus(Expr a, Expr b) {
    return Sum.of(a, Product.of(Num.MINUS_ONE, b));
  }

  /** The order of the bounds: −1 for {@code a < b}, 0 for equal, 1 for {@code a > b}, 2 unknown. */
  private static int order(Expr a, Expr b, Context ctx) {
    int s = Definite.sign(minus(b, a), ctx);
    return s == 2 ? 2 : -s;
  }

  /**
   * The bounds of an unsigned quantity: empty to go on, the value 0 for equal bounds, the call for
   * an order that cannot be decided; an error for {@code a > b}.
   */
  private static Optional<Rewriter.Result> bounds(Steps steps, Expr a, Expr b) {
    int o = order(a, b, steps.context);
    if (o == 1) {
      throw new IllegalArgumentException(steps.command() + " needs a ≤ b");
    }
    if (o == 0) {
      steps.step("empty", Num.ZERO, values());
      return Optional.of(steps.result());
    }
    return o == 2 ? Optional.of(steps.none()) : Optional.empty();
  }

  private static Rewriter.Result arclength(Steps steps, Expr f, Symbol x, Expr a, Expr b) {
    Context ctx = steps.context;
    Optional<Rewriter.Result> early = bounds(steps, a, b);
    if (early.isPresent()) {
      return early.get();
    }
    Expr formula;
    String step = "formula";
    List<Expr> parts =
        f instanceof ListExpr l ? l.items() : f instanceof Vector v ? v.items() : List.of();
    if (!parts.isEmpty()) {
      if (parts.size() != 2) {
        throw new IllegalArgumentException("arclength needs a function or a pair [x(t), y(t)]");
      }
      formula =
          Power.of(
              Sum.of(
                  Power.of(derivative(parts.get(0), x, ctx), Num.of(2)),
                  Power.of(derivative(parts.get(1), x, ctx), Num.of(2))),
              half());
      step = "curve";
    } else {
      formula = Power.of(Sum.of(Num.ONE, Power.of(derivative(f, x, ctx), Num.of(2))), half());
    }
    steps.step(step, Integral.definite(formula, x, a, b), values());
    Expr integrand = simplify(formula, ctx);
    if (!integrand.equals(Canonical.of(formula, steps.context.precision()))) {
      steps.step("integrand", Integral.definite(integrand, x, a, b), values());
    }
    return steps.then(Calculus.integrate(integrand, x, a, b, ctx));
  }

  private static Expr half() {
    return Num.of(Rational.of(1, 2));
  }
}
