package mathcanvas.engine.calculus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;
import mathcanvas.engine.simplify.Simplifier;

/**
 * The vector calculus builtins of {@code docs/language.md} §14.20 on evaluated arguments: {@code
 * grad}, {@code jacobian}, {@code hessian}, {@code divergence}, {@code curl} and {@code laplacian}.
 * Every partial derivative is {@link Calculus#differentiate}'s, so the rule set {@code diff} does
 * all the differentiating; the result is simplified. The trace is one step in the rule set {@link
 * #RULE_SET}, {@code <command>.partials} (its value {@code partials} lists the partial derivatives
 * taken), from the call to the vector, matrix or scalar the definition builds, then the
 * simplifier's. A function or field of the wrong shape is an {@link IllegalArgumentException} with
 * the language's message.
 */
public final class Multivariable {

  private Multivariable() {}

  /** The rule set of the steps. */
  public static final String RULE_SET = "multivariable";

  /** The commands. */
  public static final Set<String> COMMANDS =
      Set.of("grad", "jacobian", "hessian", "divergence", "curl", "laplacian");

  /** The steps the commands record, {@code <command>.partials}. */
  public static final List<String> STEPS =
      COMMANDS.stream().sorted().map(c -> c + ".partials").toList();

  /**
   * Evaluates a call whose function or field has been evaluated in the scope of its variables.
   *
   * @param call the call, {@code grad(f, [x, y])} and the others
   * @param context the assumptions (without facts about the variables), precision, trace and cap
   * @return the value and its trace
   * @throws IllegalArgumentException with the language's message for a wrong argument
   */
  public static Rewriter.Result of(Apply call, Context context) {
    String name = call.head().name();
    List<Symbol> vars = variables(name, call.args().get(1));
    Expr f = call.args().getFirst();
    Partials p = new Partials(context);
    int n = vars.size();
    Expr built =
        switch (name) {
          case "grad" -> {
            List<Expr> items = new ArrayList<>();
            for (Symbol v : vars) {
              items.add(p.of(scalar(name, f), v));
            }
            yield Vector.of(items.toArray(Expr[]::new));
          }
          case "jacobian" -> {
            List<List<Expr>> rows = new ArrayList<>();
            for (Expr fi : field(name, f, -1)) {
              List<Expr> row = new ArrayList<>();
              for (Symbol v : vars) {
                row.add(p.of(fi, v));
              }
              rows.add(row);
            }
            yield Matrix.of(rows);
          }
          case "hessian" -> {
            Expr g = scalar(name, f);
            List<Expr> first = new ArrayList<>();
            for (Symbol v : vars) {
              first.add(p.of(g, v));
            }
            List<List<Expr>> rows = new ArrayList<>();
            for (int i = 0; i < n; i++) {
              List<Expr> row = new ArrayList<>();
              for (Symbol v : vars) {
                row.add(p.of(first.get(i), v));
              }
              rows.add(row);
            }
            yield Matrix.of(rows);
          }
          case "divergence" -> {
            List<Expr> components = field(name, f, n);
            List<Expr> terms = new ArrayList<>();
            for (int i = 0; i < n; i++) {
              terms.add(p.of(components.get(i), vars.get(i)));
            }
            yield terms.size() == 1 ? terms.getFirst() : Sum.of(terms);
          }
          case "curl" -> {
            if (n != 2 && n != 3) {
              throw new IllegalArgumentException(
                  "curl needs a field of 2 or 3 components and as many variables");
            }
            List<Expr> c = field(name, f, n);
            if (n == 2) {
              yield minus(p.of(c.get(1), vars.get(0)), p.of(c.get(0), vars.get(1)));
            }
            yield Vector.of(
                minus(p.of(c.get(2), vars.get(1)), p.of(c.get(1), vars.get(2))),
                minus(p.of(c.get(0), vars.get(2)), p.of(c.get(2), vars.get(0))),
                minus(p.of(c.get(1), vars.get(0)), p.of(c.get(0), vars.get(1))));
          }
          case "laplacian" -> {
            Expr g = scalar(name, f);
            List<Expr> terms = new ArrayList<>();
            for (Symbol v : vars) {
              terms.add(p.of(p.of(g, v), v));
            }
            yield terms.size() == 1 ? terms.getFirst() : Sum.of(terms);
          }
          default -> throw new IllegalArgumentException("not a vector calculus command: " + name);
        };
    var mc = context.precision();
    Expr value = Canonical.of(built, mc);
    Rewriter.Result simple = Simplifier.simplify(value, context);
    if (!context.traceEnabled()) {
      return new Rewriter.Result(simple.expr(), Trace.NONE);
    }
    Expr start = Canonical.of(call, mc);
    TreeMap<String, Expr> values = new TreeMap<>();
    values.put("partials", Canonical.of(ListExpr.of(p.taken), mc));
    Rewrite step =
        new Rewrite(start, value, RULE_SET, name + ".partials", Path.ROOT, Bindings.EMPTY, values);
    Trace own = new Trace(start, value, List.of(step), List.of());
    return new Rewriter.Result(simple.expr(), Trace.concat(own, simple.trace()));
  }

  /** The partial derivatives taken, each recorded for the narration once. */
  private static final class Partials {
    final Context quiet;
    final List<Expr> taken = new ArrayList<>();

    Partials(Context context) {
      this.quiet = context.withTrace(false);
    }

    Expr of(Expr f, Symbol v) {
      Expr d = Calculus.differentiate(f, v, 1, quiet).expr();
      Expr written = Canonical.of(Relation.of(Relation.Op.EQ, Derivative.of(f, v, 1), d));
      if (!taken.contains(written)) {
        taken.add(written);
      }
      return d;
    }
  }

  private static Expr minus(Expr a, Expr b) {
    return Sum.of(a, Product.of(Num.MINUS_ONE, b));
  }

  /** The distinct variables of a list or vector of names. */
  private static List<Symbol> variables(String name, Expr e) {
    List<Expr> items =
        e instanceof ListExpr l ? l.items() : e instanceof Vector v ? v.items() : List.of();
    List<Symbol> out = new ArrayList<>();
    for (Expr item : items) {
      if (!(item instanceof Symbol s) || out.contains(s)) {
        out.clear();
        break;
      }
      out.add(s);
    }
    if (out.isEmpty()) {
      throw new IllegalArgumentException(name + " needs a list of distinct variables");
    }
    return out;
  }

  /** A scalar function: not a collection or a relation. */
  private static Expr scalar(String name, Expr f) {
    if (!Calculus.isScalar(f)) {
      throw new IllegalArgumentException(name + " needs a scalar function");
    }
    return f;
  }

  /** The components of a field, {@code n} of them unless {@code n} is −1. */
  private static List<Expr> field(String name, Expr f, int n) {
    List<Expr> items =
        f instanceof ListExpr l ? l.items() : f instanceof Vector v ? v.items() : null;
    if (items == null
        || items.isEmpty()
        || (n >= 0 && items.size() != n)
        || !items.stream().allMatch(Calculus::isScalar)) {
      throw new IllegalArgumentException(
          switch (name) {
            case "jacobian" -> "jacobian needs a list of functions";
            case "curl" -> "curl needs a field of 2 or 3 components and as many variables";
            default -> name + " needs a field and variables of one length";
          });
    }
    return items;
  }
}
