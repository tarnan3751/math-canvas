package mathcanvas.engine.linalg;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;

/**
 * The linear-algebra builtins ({@code docs/language.md} §14.16) on evaluated arguments: {@code
 * det}, {@code transpose}, {@code inverse}, {@code rank}, {@code rref}, {@code trace}, {@code
 * linsolve}, {@code nullspace}, {@code colspace}, {@code gramschmidt}, {@code normalize}, {@code
 * norm}, {@code identity}, {@code zeros} and {@code size}, and the eigen-theory: {@code charpoly},
 * {@code eigenvalues}, {@code eigenvectors} and {@code diagonalize} ({@link Eigen}). An argument
 * that may still stand for a matrix (it holds a symbol) leaves the call as it is; an argument of
 * the wrong kind is an {@link IllegalArgumentException} with the language's message. The narrated
 * operations record their steps ({@link Recorder}), owned by {@link LinalgNarrator}.
 */
public final class Linalg {

  /** The functions this class evaluates. */
  public static final Set<String> FUNCTIONS =
      Set.of(
          "det",
          "transpose",
          "inverse",
          "rank",
          "rref",
          "trace",
          "linsolve",
          "nullspace",
          "colspace",
          "gramschmidt",
          "normalize",
          "norm",
          "identity",
          "zeros",
          "size",
          "charpoly",
          "eigenvalues",
          "eigenvectors",
          "diagonalize");

  /** The rule sets of the steps, which {@link LinalgNarrator} owns. */
  public static final Set<String> RULE_SETS =
      Set.of(
          RowReduction.RULE_SET,
          Determinants.RULE_SET,
          Inverses.RULE_SET,
          "rank",
          "linsolve",
          "nullspace",
          "colspace",
          GramSchmidt.RULE_SET,
          Eigen.RULE_SET,
          "diagonalize");

  /** Every step the operations record, {@code <rule set>.<rule>}. */
  public static final Set<String> STEPS =
      Set.of(
          "rref.swap",
          "rref.scale",
          "rref.add",
          "rref.done",
          "det.formula",
          "det.cofactor",
          "det.minors",
          "det.swap",
          "det.eliminate",
          "det.pivots",
          "det.zero-column",
          "inverse.adjugate",
          "inverse.augment",
          "inverse.read",
          "rank.count",
          "linsolve.augment",
          "linsolve.unique",
          "linsolve.general",
          "linsolve.inconsistent",
          "nullspace.basis",
          "colspace.pivots",
          "gramschmidt.project",
          "gramschmidt.dependent",
          "gramschmidt.done",
          "eigen.charpoly",
          "eigen.roots",
          "eigen.nullspace",
          "eigen.unsolved",
          "diagonalize.assemble",
          "diagonalize.not-diagonalizable");

  /** The step of the numeric path, in the rule set {@code numeric}. */
  public static final String NUMERIC_STEP = "numeric.eigen";

  /**
   * A value and whether the numeric rule gave it.
   *
   * @param result the value and its steps
   * @param numeric whether a root no exact method gave is in it, numerically
   */
  public record Outcome(Rewriter.Result result, boolean numeric) {}

  private Linalg() {}

  /**
   * Evaluates a call whose arguments are evaluated ({@code det}'s method as written).
   *
   * @param call the call
   * @param ctx the assumptions, precision, trace, cap and cancellation
   * @return the value and its steps; the call itself when an argument may still be a matrix
   * @throws IllegalArgumentException with the language's message for a wrong argument
   * @throws mathcanvas.engine.algebra.AlgebraLimitException past the algebra's bounds
   */
  public static Rewriter.Result apply(Apply call, Context ctx) {
    return apply(call, ctx, 0).result();
  }

  /**
   * Evaluates a call as {@link #apply(Apply, Context)} does, under the numeric rule when {@code
   * digits} is set: eigenvalues the solver finds only numerically are then decimals of that many
   * digits, and the outcome says so.
   *
   * @param call the call
   * @param ctx the assumptions, precision, trace, cap, numerics and cancellation
   * @param digits the digits of the numeric rule, 0 when it is not in force
   * @return the value, its steps and whether it is numeric
   * @throws IllegalArgumentException with the language's message for a wrong argument
   * @throws mathcanvas.engine.algebra.AlgebraLimitException past the algebra's bounds
   */
  public static Outcome apply(Apply call, Context ctx, int digits) {
    String name = call.head().name();
    List<Expr> args = call.args();
    Expr a = args.getFirst();
    Entries en = new Entries(ctx);
    Recorder steps = new Recorder(call, ctx);
    switch (name) {
      case "identity", "zeros" -> {
        List<Integer> dims = new ArrayList<>();
        for (Expr d : args) {
          if (open(d)) {
            return exact(stays(call, ctx));
          }
          if (!(d instanceof Num n
              && n.value() instanceof Int k
              && k.signum() > 0
              && k.value().compareTo(BigInteger.valueOf(MAX_ENTRIES)) <= 0)) {
            throw new IllegalArgumentException(
                name.equals("identity")
                    ? "`identity` needs a positive integer"
                    : "`zeros` needs positive integers");
          }
          dims.add(k.value().intValue());
        }
        int rows = dims.getFirst();
        int columns = dims.getLast();
        if ((long) rows * columns > MAX_ENTRIES) {
          throw new IllegalArgumentException("the result is too large");
        }
        return exact(silent(filled(rows, columns, name.equals("identity")), ctx));
      }
      default -> {}
    }
    if (open(a)
        || (args.size() > 1
            && !name.equals("det")
            && !name.equals("charpoly")
            && open(args.get(1)))) {
      return exact(stays(call, ctx));
    }
    switch (name) {
      case "eigenvalues", "eigenvectors", "diagonalize" -> {
        Eigen.Outcome o = Eigen.eigen(name, square(name, a), en, steps, digits);
        return new Outcome(steps.result(), o.numeric());
      }
      default -> {}
    }
    return exact(
        switch (name) {
          case "charpoly" -> {
            if (!(args.get(1) instanceof Symbol x)) {
              throw new IllegalArgumentException("`charpoly` needs a variable");
            }
            Eigen.charpoly(square(name, a), x, en, steps);
            yield steps.result();
          }
          case "size" -> {
            if (a instanceof Matrix m) {
              yield silent(Vector.of(Num.of(m.rows().size()), Num.of(columns(m))), ctx);
            }
            if (a instanceof Vector v) {
              yield silent(Vector.of(Num.of(v.items().size())), ctx);
            }
            // the count of a list's or a set's items
            if (a instanceof mathcanvas.engine.expr.ListExpr l) {
              yield silent(Num.of(l.items().size()), ctx);
            }
            if (a instanceof mathcanvas.engine.expr.SetExpr s) {
              yield silent(Num.of(s.items().size()), ctx);
            }
            throw new IllegalArgumentException("`size` needs a vector, a matrix, a list or a set");
          }
          case "transpose" -> {
            if (a instanceof Vector v) {
              yield silent(Matrix.of(List.of(v.items())), ctx);
            }
            yield silent(transpose(matrix(name, a)), ctx);
          }
          case "trace" -> {
            Matrix m = square(name, a);
            Expr total = Num.ZERO;
            for (int i = 0; i < m.rows().size(); i++) {
              total = en.add(total, m.rows().get(i).get(i));
            }
            yield silent(total, ctx);
          }
          case "det" -> {
            Matrix m = square(name, a);
            Determinants.Method method =
                m.rows().size() <= Determinants.DEFAULT_COFACTOR
                    ? Determinants.Method.COFACTOR
                    : Determinants.Method.ELIMINATION;
            if (args.size() == 2) {
              method =
                  switch (args.get(1) instanceof Symbol s ? s.name() : "") {
                    case "cofactor" -> Determinants.Method.COFACTOR;
                    case "elimination" -> Determinants.Method.ELIMINATION;
                    default ->
                        throw new IllegalArgumentException(
                            "`det`'s method must be cofactor or elimination");
                  };
            }
            Determinants.det(m, method, en, steps);
            yield steps.result();
          }
          case "inverse" -> {
            Inverses.inverse(square(name, a), en, steps);
            yield steps.result();
          }
          case "rank" -> {
            Subspaces.rank(matrix(name, a), en, steps);
            yield steps.result();
          }
          case "rref" -> {
            Subspaces.rref(matrix(name, a), en, steps);
            yield steps.result();
          }
          case "nullspace" -> {
            Subspaces.nullspace(matrix(name, a), en, steps);
            yield steps.result();
          }
          case "colspace" -> {
            Subspaces.colspace(matrix(name, a), en, steps);
            yield steps.result();
          }
          case "linsolve" -> {
            if (!(a instanceof Matrix m)) {
              throw new IllegalArgumentException(Subspaces.LINSOLVE_SHAPES);
            }
            Subspaces.linsolve(m, args.get(1), en, steps);
            yield steps.result();
          }
          case "gramschmidt" -> {
            GramSchmidt.orthogonalize(vectors(a), en, steps);
            yield steps.result();
          }
          case "norm" -> {
            Expr order = args.size() == 2 ? args.get(1) : Num.of(2);
            yield silent(Norms.norm(vector(name, a), order, en), ctx);
          }
          case "normalize" -> silent(Norms.normalize(vector(name, a), en), ctx);
          default -> throw new IllegalStateException("not a linear-algebra function: " + name);
        });
  }

  private static Outcome exact(Rewriter.Result r) {
    return new Outcome(r, false);
  }

  /**
   * The inverse of a square matrix, without steps: {@code M^-1}.
   *
   * @param m the matrix
   * @param ctx the assumptions, precision and cancellation
   * @return the inverse
   * @throws IllegalArgumentException "the matrix is singular", or for a matrix that is not square
   */
  public static Expr inverse(Matrix m, Context ctx) {
    Context quiet = ctx.withTrace(false);
    return Inverses.inverse(square("inverse", m), new Entries(quiet), new Recorder(m, quiet));
  }

  /** The most entries {@code identity} and {@code zeros} build. */
  static final int MAX_ENTRIES = 1_000_000;

  /** Whether an argument may still stand for a matrix or a number: it holds a symbol. */
  private static boolean open(Expr e) {
    if (e instanceof Symbol) {
      return true;
    }
    if (e instanceof Matrix || e instanceof Vector || e instanceof ListExpr) {
      return false;
    }
    for (Expr op : Operands.of(e)) {
      if (op instanceof Symbol || open(op)) {
        return true;
      }
    }
    return false;
  }

  private static Rewriter.Result stays(Apply call, Context ctx) {
    return new Rewriter.Result(new Entries(ctx).canon(call), Trace.NONE);
  }

  private static Rewriter.Result silent(Expr value, Context ctx) {
    return new Rewriter.Result(new Entries(ctx).canon(value), Trace.NONE);
  }

  private static Matrix matrix(String name, Expr a) {
    if (a instanceof Matrix m) {
      return m;
    }
    throw new IllegalArgumentException("`" + name + "` needs a matrix");
  }

  private static Matrix square(String name, Expr a) {
    if (a instanceof Matrix m && m.rows().size() == columns(m)) {
      return m;
    }
    throw new IllegalArgumentException("`" + name + "` needs a square matrix");
  }

  private static Vector vector(String name, Expr a) {
    if (a instanceof Vector v) {
      return v;
    }
    throw new IllegalArgumentException("`" + name + "` needs a vector");
  }

  /** The vectors of {@code gramschmidt}: the rows of a matrix, which a list of them makes. */
  private static List<List<Expr>> vectors(Expr a) {
    if (a instanceof Matrix m) {
      return m.rows();
    }
    if (a instanceof ListExpr l && l.items().stream().allMatch(v -> v instanceof Vector)) {
      throw new IllegalArgumentException("`gramschmidt` needs vectors of the same length");
    }
    throw new IllegalArgumentException("`gramschmidt` needs a list of vectors");
  }

  private static Matrix transpose(Matrix m) {
    List<List<Expr>> out = new ArrayList<>();
    for (int j = 0; j < columns(m); j++) {
      List<Expr> row = new ArrayList<>();
      for (List<Expr> r : m.rows()) {
        row.add(r.get(j));
      }
      out.add(row);
    }
    return Matrix.of(out);
  }

  private static Expr filled(int rows, int columns, boolean identity) {
    List<List<Expr>> out = new ArrayList<>(rows);
    for (int i = 0; i < rows; i++) {
      List<Expr> row = new ArrayList<>(columns);
      for (int j = 0; j < columns; j++) {
        row.add(identity && i == j ? Num.ONE : Num.ZERO);
      }
      out.add(row);
    }
    return Matrix.of(out);
  }

  private static int columns(Matrix m) {
    return m.rows().getFirst().size();
  }
}
