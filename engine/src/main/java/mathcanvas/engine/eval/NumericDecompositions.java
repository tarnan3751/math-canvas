package mathcanvas.engine.eval;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.provider.Decompose;
import mathcanvas.engine.provider.Decompose.Kind;
import mathcanvas.engine.provider.Decomposition;
import mathcanvas.engine.provider.Providers;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Numerics;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Trace;

/**
 * The numeric linear algebra builtins ({@code docs/language.md} §12.1, §14.17): {@code lu}, {@code
 * qr}, {@code cholesky}, {@code svd}, {@code lstsq} and {@code expm}, through the {@link Decompose}
 * provider. Each entry of the matrix is taken to a {@code double} through the context's numerics at
 * {@link #INPUT_DIGITS} digits; the factors come back as matrices of {@code Real} entries rounded
 * to the context's digits but at most {@link #DIGITS}, a {@code double}'s, so they show they are
 * inexact. The results are numeric under every number format.
 *
 * <p>A matrix with an entry that holds a symbol, or an argument that is not a collection and holds
 * a symbol (a name that may be bound to a matrix), leaves the call, with the terminal note {@code
 * decomposition.symbolic}; any other argument that is not a matrix of real numbers is an error. A
 * value is recorded as the step {@code numeric.decomposition} ({@code digits_} the digits), which
 * marks the statement's value as numeric.
 */
final class NumericDecompositions {

  /** The rule set of the value's step. */
  static final String RULE_SET = "numeric";

  /** The value's step, narrated by {@code numeric.decomposition}. */
  static final String STEP = "decomposition";

  /** The rule set of the note when the call stays, narrated by {@code decomposition.symbolic}. */
  static final String NOTE_SET = "decomposition";

  /** The note when the call stays. */
  static final String NOTE = "symbolic";

  /** The most significant digits of an entry of a result: a {@code double}'s. */
  static final int DIGITS = 16;

  /** The digits an entry of the input is evaluated to before it is taken to a {@code double}. */
  static final int INPUT_DIGITS = 17;

  private static final Map<String, Kind> KINDS =
      Map.of(
          "lu", Kind.LU,
          "qr", Kind.QR,
          "cholesky", Kind.CHOLESKY,
          "svd", Kind.SVD,
          "lstsq", Kind.LEAST_SQUARES,
          "expm", Kind.EXP);

  /**
   * The value of a call, with its step.
   *
   * @param value the value, or the call when it stays
   * @param numeric whether the value was computed numerically
   * @param step the step, null when untraced
   */
  record Outcome(Expr value, boolean numeric, Trace step) {}

  private NumericDecompositions() {}

  /**
   * Returns whether a builtin function's name is one of these.
   *
   * @param name the name
   * @return whether it is
   */
  static boolean handles(String name) {
    return KINDS.containsKey(name);
  }

  /**
   * Evaluates a call whose arguments are evaluated and canonical.
   *
   * @param head the function's name
   * @param args the arguments
   * @param mc the context's precision
   * @param numerics the numeric values of closed expressions
   * @param traced whether to record the step
   * @param node the raw call, for errors
   * @param c the request to stop
   * @return the value and its step
   * @throws EvaluationException if an argument is not a matrix of real numbers, or the matrix has
   *     no such decomposition
   */
  static Outcome of(
      Symbol head,
      List<Expr> args,
      MathContext mc,
      Numerics numerics,
      boolean traced,
      Expr node,
      Cancellation c) {
    String name = head.name();
    Kind kind = KINDS.get(name);
    Expr call = Canonical.of(new Apply(head, args), mc, c);
    String what =
        kind == Kind.LEAST_SQUARES
            ? "`lstsq` needs a matrix and a vector"
            : "`" + name + "` needs a matrix";
    double[][] a = matrix(args.getFirst(), name, what, numerics, node);
    double[] b = null;
    boolean symbolic = a == null;
    if (kind == Kind.LEAST_SQUARES) {
      Expr rhs = args.get(1);
      if (rhs instanceof Vector v) {
        b = new double[v.items().size()];
        for (int i = 0; i < b.length; i++) {
          Optional<Double> x = entry(v.items().get(i), name, numerics, node);
          if (x.isEmpty()) {
            symbolic = true;
            break;
          }
          b[i] = x.get();
        }
      } else if (!holdsSymbol(rhs)) {
        throw new EvaluationException(what, node);
      } else {
        symbolic = true;
      }
      if (!symbolic && b.length != a.length) {
        throw new EvaluationException(
            "`lstsq` needs a vector of " + a.length + (a.length == 1 ? " entry" : " entries"),
            node);
      }
    }
    if (symbolic) {
      Bindings named = new Bindings(List.of(new Bindings.Binding("f_", head, null, null)));
      return new Outcome(call, false, traced ? step(call, call, NOTE_SET, NOTE, named) : null);
    }
    Context ctx =
        new Context(
            Assumptions.EMPTY, mc, false, Evaluator.REWRITE_CAP, Providers.defaults(), numerics, c);
    Decomposition d = ctx.providers().run(new Decompose(kind, a, b), ctx, Steps.NONE).value();
    if (d.status() != Decomposition.Status.OK) {
      throw new EvaluationException(message(name, d.status()), node);
    }
    MathContext out = new MathContext(Math.min(mc.getPrecision(), DIGITS), RoundingMode.HALF_EVEN);
    List<Expr> factors = new ArrayList<>();
    for (double[][] f : d.factors()) {
      factors.add(toMatrix(f, out));
    }
    Expr value =
        switch (kind) {
          case LU, QR, SVD -> new ListExpr(factors);
          case CHOLESKY, EXP -> factors.getFirst();
          case LEAST_SQUARES ->
              new Vector(
                  ((Matrix) factors.getFirst()).rows().stream().map(List::getFirst).toList());
        };
    value = Canonical.of(value, mc, c);
    Bindings digits =
        new Bindings(List.of(new Bindings.Binding("digits_", Num.of(DIGITS), null, null)));
    return new Outcome(value, true, traced ? step(call, value, RULE_SET, STEP, digits) : null);
  }

  /**
   * The matrix's entries as {@code double}s; null when an entry holds a symbol, or the argument is
   * not a collection and holds one.
   */
  private static double[][] matrix(
      Expr arg, String name, String what, Numerics numerics, Expr node) {
    if (!(arg instanceof Matrix m)) {
      if (arg instanceof Vector || arg instanceof ListExpr || !holdsSymbol(arg)) {
        throw new EvaluationException(what, node);
      }
      return null;
    }
    double[][] a = new double[m.rows().size()][];
    for (int i = 0; i < a.length; i++) {
      List<Expr> row = m.rows().get(i);
      a[i] = new double[row.size()];
      for (int j = 0; j < a[i].length; j++) {
        Optional<Double> x = entry(row.get(j), name, numerics, node);
        if (x.isEmpty()) {
          return null;
        }
        a[i][j] = x.get();
      }
    }
    return a;
  }

  /** An entry as a {@code double}; empty when it holds a symbol. */
  private static Optional<Double> entry(Expr e, String name, Numerics numerics, Expr node) {
    if (holdsSymbol(e)) {
      return Optional.empty();
    }
    Optional<Numeric> value =
        numerics.value(e, new MathContext(INPUT_DIGITS, RoundingMode.HALF_EVEN));
    if (!(value.orElse(null) instanceof RealNumeric r)) {
      throw new EvaluationException("`" + name + "` needs a real matrix", node);
    }
    double x = r.toDouble();
    // an entry that overflows, or a non-zero one that underflows to 0
    if (!Double.isFinite(x) || x == 0 && r.signum() != 0) {
      throw new EvaluationException("`" + name + "`'s entries are too large to approximate", node);
    }
    return Optional.of(x);
  }

  private static boolean holdsSymbol(Expr e) {
    if (e instanceof Symbol) {
      return true;
    }
    for (Expr o : Operands.of(e)) {
      if (holdsSymbol(o)) {
        return true;
      }
    }
    return false;
  }

  private static String message(String name, Decomposition.Status status) {
    return switch (status) {
      case OK -> throw new IllegalArgumentException("not a failure");
      case NOT_SQUARE -> "`" + name + "` needs a square matrix";
      case SINGULAR ->
          "`" + name + "` needs a matrix that is not singular or too close to singular";
      case NOT_SPD -> "`" + name + "` needs a symmetric positive-definite matrix";
      case RANK_DEFICIENT -> "`" + name + "` needs a matrix whose columns are independent";
      case NOT_FINITE -> "`" + name + "`'s result is too large to approximate";
    };
  }

  private static Matrix toMatrix(double[][] f, MathContext out) {
    List<List<Expr>> rows = new ArrayList<>(f.length);
    for (double[] row : f) {
      List<Expr> entries = new ArrayList<>(row.length);
      for (double x : row) {
        entries.add(Num.of(Real.of(BigDecimal.valueOf(x).round(out))));
      }
      rows.add(entries);
    }
    return new Matrix(rows);
  }

  private static Trace step(Expr before, Expr after, String ruleSet, String rule, Bindings b) {
    Rewrite r = new Rewrite(before, after, ruleSet, rule, Path.ROOT, b);
    return new Trace(before, after, List.of(r), List.of());
  }
}
