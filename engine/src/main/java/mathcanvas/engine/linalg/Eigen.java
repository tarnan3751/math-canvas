package mathcanvas.engine.linalg;

import static mathcanvas.engine.linalg.Recorder.values;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.algebra.Factoring;
import mathcanvas.engine.algebra.Factorization;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.algebra.SquareFree;
import mathcanvas.engine.eval.NumericForm;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
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
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.parse.Brackets;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.solve.Solver;

/**
 * Eigenvalues, eigenvectors and diagonalization. The characteristic polynomial is Berkowitz's
 * ({@link CharPoly}); its roots come from the solver ({@link Solver}, so through the {@code Solve}
 * providers), factor by factor: for a matrix of rationals the polynomial is split square-free (the
 * multiplicities) and each part factored over ℚ, for a symbolic one each square-free part is solved
 * as it is (generic multiplicities), and for one with a decimal entry the polynomial is solved as
 * the rationals its decimals denote, its roots put at the precision and those within {@code
 * 10^-(p−5)} of one another (relatively) merged, their multiplicities added. Each root is cancelled
 * ({@link Entries#cancel}). A factor the solver can only solve numerically leaves the call, unless
 * the numeric rule is in force ({@code numeric(…)} or a decimal format: then the roots are decimals
 * at its digits and the result is marked numeric). The eigenvectors of a root of an irreducible
 * rational factor {@code q} of degree 2 or more are the null space of {@code A − xI} over {@code
 * ℚ[x]/(q)} ({@link Modular}) with {@code x} replaced by the root and the entry cancelled, exact
 * even for a cosine of a third of an angle, and exact before a decimal root is put in; that path
 * needs every entry of {@code A} an exact rational. Any other root's are the null space of {@code A
 * − λI}, whose symbolic pivots' conditions the {@code eigen.nullspace} and {@code
 * diagonalize.assemble} steps carry; those of a decimal matrix the null space at ten more digits,
 * an entry below {@code 10^-(p−5)} of the largest taken as zero.
 */
final class Eigen {

  /** The rule set of the steps. */
  static final String RULE_SET = "eigen";

  /** The rule set of the numeric step. */
  static final String NUMERIC = "numeric";

  /** The digits beyond the precision a decimal matrix's null space is computed at. */
  static final int GUARD = 10;

  private Eigen() {}

  /**
   * A distinct eigenvalue: its algebraic multiplicity and the irreducible rational factor it is a
   * root of, or null.
   */
  private record Root(Expr value, int multiplicity, Polynomial factor) {}

  /** The value and whether a numeric root is in it. */
  record Outcome(Expr value, boolean numeric) {}

  /** {@code charpoly(M, x)}. */
  static Expr charpoly(Matrix m, Symbol x, Entries en, Recorder steps) {
    if (Subspaces.occurs(m, x)) {
      throw new IllegalArgumentException("charpoly's variable must not occur in the matrix");
    }
    Expr p = polynomial(m, x, en);
    steps.step(RULE_SET, "charpoly", p, values("m", shifted(m, x, en), "x", x));
    return p;
  }

  /**
   * {@code eigenvalues}, {@code eigenvectors} and {@code diagonalize}.
   *
   * @param digits the digits of the numeric rule, 0 when it is not in force
   */
  static Outcome eigen(String name, Matrix m, Entries en, Recorder steps, int digits) {
    int n = m.rows().size();
    Symbol x = fresh(m);
    Expr p = polynomial(m, x, en);
    steps.step(
        RULE_SET,
        "charpoly",
        new Relation(Relation.Op.EQ, p, Num.ZERO),
        values("m", shifted(m, x, en), "x", x));
    boolean decimal = hasDecimal(m);
    Context solving = digits > 0 && !decimal ? withPrecision(en.context(), digits) : en.context();
    List<Root> roots = roots(p, x, rationalEntries(m), decimal, en, solving);
    boolean numeric = !decimal && roots != null && roots.stream().anyMatch(r -> inexact(r.value()));
    if (roots == null
        || roots.stream().mapToInt(Root::multiplicity).sum() != n
        || (numeric && digits == 0)) {
      return unsolved(steps, p);
    }
    Entries vectors = en;
    if (numeric) {
      steps.step(RULE_SET, "unsolved", steps.call(), values("p", p));
      vectors = new Entries(solving);
    }
    List<Expr> list = new ArrayList<>();
    for (Root r : roots) {
      for (int k = 0; k < r.multiplicity(); k++) {
        list.add(r.value());
      }
    }
    if (name.equals("eigenvalues")) {
      Expr value = en.canon(Brackets.of(list));
      if (numeric) {
        steps.step(NUMERIC, "eigen", value, values("digits", Num.of(digits)));
      } else {
        steps.step(RULE_SET, "roots", value, values());
      }
      return new Outcome(steps.current(), numeric);
    }
    if (!numeric) {
      steps.step(RULE_SET, "roots", en.canon(Brackets.of(list)), values());
    }
    List<Expr> pairs = new ArrayList<>();
    List<List<List<Expr>>> bases = new ArrayList<>();
    List<Expr> conditions = new ArrayList<>();
    for (Root r : roots) {
      Subspaces.Kernel kernel = basis(m, r, decimal ? en : vectors, decimal, x);
      List<List<Expr>> basis = kernel.basis();
      if (basis.isEmpty()) {
        // a zero test missed: the root is one, so its null space is not trivial
        return unsolved(steps, p);
      }
      bases.add(basis);
      for (Expr c : kernel.conditions()) {
        if (!conditions.contains(c)) {
          conditions.add(c);
        }
      }
      pairs.add(Brackets.of(List.of(r.value(), Brackets.of(vectorsOf(basis)))));
      if (!numeric) {
        TreeMap<String, Expr> vs = Subspaces.conditions(kernel.conditions());
        vs.put("lambda", r.value());
        steps.step(RULE_SET, "nullspace", en.canon(Brackets.of(pairs)), vs);
      }
    }
    if (name.equals("eigenvectors")) {
      Expr value = en.canon(Brackets.of(pairs));
      if (numeric) {
        TreeMap<String, Expr> vs = Subspaces.conditions(conditions);
        vs.put("digits", Num.of(digits));
        steps.step(NUMERIC, "eigen", value, vs);
      }
      return new Outcome(steps.current(), numeric);
    }
    // diagonalize: P has the eigenvectors as columns, D the eigenvalues on its diagonal
    List<List<Expr>> columns = new ArrayList<>();
    List<Expr> diagonal = new ArrayList<>();
    for (int k = 0; k < roots.size(); k++) {
      for (List<Expr> v : bases.get(k)) {
        columns.add(v);
        diagonal.add(roots.get(k).value());
      }
    }
    if (columns.size() < n) {
      steps.step(
          "diagonalize",
          "not-diagonalizable",
          steps.call(),
          values("k", Num.of(columns.size()), "n", Num.of(n)));
      return new Outcome(steps.current(), false);
    }
    List<List<Expr>> pRows = new ArrayList<>();
    List<List<Expr>> dRows = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      List<Expr> pRow = new ArrayList<>();
      List<Expr> dRow = new ArrayList<>();
      for (int j = 0; j < n; j++) {
        pRow.add(columns.get(j).get(i));
        dRow.add(i == j ? diagonal.get(i) : Num.ZERO);
      }
      pRows.add(pRow);
      dRows.add(dRow);
    }
    Entries.count(pRows);
    Expr value = en.canon(ListExpr.of(Matrix.of(pRows), Matrix.of(dRows)));
    TreeMap<String, Expr> vs = Subspaces.conditions(conditions);
    if (numeric) {
      vs.put("digits", Num.of(digits));
    }
    steps.step(numeric ? NUMERIC : "diagonalize", numeric ? "eigen" : "assemble", value, vs);
    return new Outcome(steps.current(), numeric);
  }

  /** The call stays: the steps go back to it from the characteristic equation. */
  private static Outcome unsolved(Recorder steps, Expr p) {
    steps.step(RULE_SET, "unsolved", steps.call(), values("p", p));
    return new Outcome(steps.current(), false);
  }

  /** {@code det(xI − A)}, cancelled. */
  private static Expr polynomial(Matrix m, Symbol x, Entries en) {
    List<Expr> c = CharPoly.coefficients(Entries.rows(m), en);
    int n = c.size() - 1;
    List<Expr> terms = new ArrayList<>();
    for (int k = 0; k <= n; k++) {
      terms.add(Product.of(c.get(k), new Power(x, Num.of(n - k))));
    }
    Expr sum = en.canon(Sum.of(terms));
    return sum instanceof Num ? sum : Algebra.cancel(sum, en.context());
  }

  /** {@code xI − A}, for the narration. */
  private static Expr shifted(Matrix m, Symbol x, Entries en) {
    List<List<Expr>> rows = new ArrayList<>();
    for (int i = 0; i < m.rows().size(); i++) {
      List<Expr> row = new ArrayList<>();
      for (int j = 0; j < m.rows().size(); j++) {
        Expr e = en.neg(m.rows().get(i).get(j));
        row.add(i == j ? en.canon(Sum.of(x, e)) : e);
      }
      rows.add(row);
    }
    return Matrix.of(rows);
  }

  /**
   * The distinct roots in canonical order, each cancelled, or null when one is not found. A decimal
   * polynomial is solved as the rationals its decimals denote, its roots then put at the precision
   * and those within the relative tolerance of one another merged, their multiplicities added.
   */
  private static List<Root> roots(
      Expr p, Symbol x, boolean rationalEntries, boolean decimal, Entries en, Context ctx) {
    Map<Expr, Root> found = new LinkedHashMap<>();
    if (decimal) {
      List<Root> exact = roots(exactly(p), x, false, false, en, ctx);
      if (exact == null) {
        return null;
      }
      int digits = ctx.precision().getPrecision();
      RealNumeric tolerance = tolerance(digits, ctx.precision());
      List<Num> values = new ArrayList<>();
      List<Integer> multiplicities = new ArrayList<>();
      for (Root r : exact) {
        if (!(NumericForm.numeric(r.value(), digits, ctx.cancellation()) instanceof Num v)) {
          return null;
        }
        int at = -1;
        for (int k = 0; k < values.size() && at < 0; k++) {
          if (close(values.get(k), v, tolerance, ctx.precision())) {
            at = k;
          }
        }
        if (at < 0) {
          values.add(v);
          multiplicities.add(r.multiplicity());
        } else {
          multiplicities.set(at, multiplicities.get(at) + r.multiplicity());
        }
      }
      for (int k = 0; k < values.size(); k++) {
        found.put(values.get(k), new Root(values.get(k), multiplicities.get(k), null));
      }
      return ordered(found, ctx);
    }
    Polynomial poly;
    try {
      poly = Polynomial.of(p, ctx.cancellation());
    } catch (IllegalArgumentException notPolynomial) {
      return null;
    }
    boolean rational = poly.variables().equals(List.of(x));
    SquareFree.Decomposition parts = SquareFree.of(poly, ctx.cancellation());
    for (int i = 0; i < parts.parts().size(); i++) {
      Polynomial part = parts.parts().get(i);
      if (part.degree(x) == 0) {
        continue;
      }
      List<Polynomial> factors = new ArrayList<>();
      if (rational) {
        Factorization f = Factoring.factor(part, Steps.NONE, ctx.cancellation());
        for (Factorization.Factor factor : f.factors()) {
          if (factor.base().degree(x) > 0) {
            factors.add(factor.base());
          }
        }
      } else {
        factors.add(part);
      }
      for (Polynomial factor : factors) {
        SetExpr s = solve(factor.toExpr(), x, ctx);
        if (s == null) {
          return null;
        }
        for (Expr root : s.items()) {
          Expr r = en.cancel(root);
          Root before = found.get(r);
          int multiplicity = (before == null ? 0 : before.multiplicity()) + i + 1;
          // the field ℚ[x]/(q) needs rational entries as well as a rational q
          found.put(r, new Root(r, multiplicity, rational && rationalEntries ? factor : null));
        }
      }
    }
    return ordered(found, ctx);
  }

  /** The roots in the order of the canonical set of them. */
  private static List<Root> ordered(Map<Expr, Root> found, Context ctx) {
    Expr set = Canonical.of(SetExpr.of(List.copyOf(found.keySet())), ctx.precision());
    List<Root> out = new ArrayList<>();
    for (Expr r : ((SetExpr) set).items()) {
      Root root = found.get(r);
      if (root == null) {
        return null;
      }
      out.add(root);
    }
    return out;
  }

  /** The roots of {@code p = 0}, or null when the solver leaves it. */
  private static SetExpr solve(Expr p, Symbol x, Context ctx) {
    Expr r =
        Solver.solve(
                Canonical.of(new Relation(Relation.Op.EQ, p, Num.ZERO)), x, ctx.withTrace(false))
            .expr();
    return r instanceof SetExpr s ? s : null;
  }

  /** A basis of the eigenspace of a root, as lists of entries, and the conditions it assumes. */
  private static Subspaces.Kernel basis(Matrix m, Root r, Entries en, boolean decimal, Symbol x) {
    int n = m.rows().size();
    if (r.factor() != null && r.factor().degree(x) >= 2) {
      Modular field = new Modular(r.factor(), en.context().cancellation());
      List<List<Polynomial>> a = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        List<Polynomial> row = new ArrayList<>();
        for (int j = 0; j < n; j++) {
          RealNumeric e = (RealNumeric) ((Num) m.rows().get(i).get(j)).value();
          Polynomial entry = Polynomial.constant(e);
          row.add(i == j ? entry.subtract(Polynomial.variable(x)) : entry);
        }
        a.add(row);
      }
      List<List<Expr>> out = new ArrayList<>();
      for (List<Polynomial> v : field.nullspace(a)) {
        List<Expr> entries = new ArrayList<>();
        for (Polynomial e : v) {
          entries.add(en.cancel(substitute(e.toExpr(), x, r.value())));
        }
        out.add(entries);
      }
      return new Subspaces.Kernel(out, List.of());
    }
    Entries field = decimal ? tolerant(m, r.value(), en) : en;
    List<List<Expr>> a = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      List<Expr> row = new ArrayList<>();
      for (int j = 0; j < n; j++) {
        Expr e = m.rows().get(i).get(j);
        row.add(i == j ? field.sub(e, r.value()) : e);
      }
      a.add(row);
    }
    Matrix shifted = Matrix.of(a);
    Subspaces.Kernel kernel =
        Subspaces.kernel(shifted, field, new Recorder(shifted, field.context().withTrace(false)));
    List<List<Expr>> out = new ArrayList<>();
    for (List<Expr> v : kernel.basis()) {
      out.add(decimal ? rounded(v, en) : v);
    }
    return new Subspaces.Kernel(out, kernel.conditions());
  }

  /**
   * The arithmetic of a decimal matrix's null space: ten digits beyond the precision, an entry
   * below {@code 10^-(p−5)} of the largest entry of {@code A − λI} taken as zero.
   */
  private static Entries tolerant(Matrix m, Expr lambda, Entries en) {
    int p = en.context().precision().getPrecision();
    Context wide = withPrecision(en.context(), p + GUARD);
    RealNumeric largest = Int.ZERO;
    for (int i = 0; i < m.rows().size(); i++) {
      for (int j = 0; j < m.rows().size(); j++) {
        Expr e = m.rows().get(i).get(j);
        if (i == j) {
          e = new Entries(wide).sub(e, lambda);
        }
        if (e instanceof Num num) {
          RealNumeric size = magnitude(num.value());
          largest = size.compareTo(largest) > 0 ? size : largest;
        }
      }
    }
    MathContext mc = wide.precision();
    RealNumeric scale = tolerance(p, mc);
    return new Entries(wide, (RealNumeric) largest.multiply(scale, mc));
  }

  private static RealNumeric magnitude(Numeric v) {
    RealNumeric re = v.re().abs();
    RealNumeric im = v.im().abs();
    return re.compareTo(im) >= 0 ? re : im;
  }

  private static List<Expr> rounded(List<Expr> v, Entries en) {
    MathContext mc = en.context().precision();
    List<Expr> out = new ArrayList<>();
    for (Expr e : v) {
      out.add(e instanceof Num n ? en.canon(Num.of(n.value().multiply(Int.ONE, mc))) : e);
    }
    return out;
  }

  private static List<Expr> vectorsOf(List<List<Expr>> basis) {
    return basis.stream().map(v -> (Expr) Vector.of(v)).toList();
  }

  /** {@code e} with the symbol {@code x} replaced by {@code value}. */
  private static Expr substitute(Expr e, Symbol x, Expr value) {
    if (e.equals(x)) {
      return value;
    }
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    List<Expr> out = new ArrayList<>();
    for (Expr op : ops) {
      out.add(substitute(op, x, value));
    }
    return Operands.rebuild(e, out);
  }

  /** Whether every entry is an exact rational number. */
  private static boolean rationalEntries(Matrix m) {
    for (List<Expr> row : m.rows()) {
      for (Expr e : row) {
        if (!(e instanceof Num n && n.value() instanceof RealNumeric q && q.isExact())) {
          return false;
        }
      }
    }
    return true;
  }

  /** {@code e} with each decimal replaced by the rational it denotes. */
  private static Expr exactly(Expr e) {
    if (e instanceof Num n && !n.value().isExact()) {
      Numeric v = n.value();
      return Num.of(Complex.of(rational(v.re()), rational(v.im())));
    }
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    List<Expr> out = new ArrayList<>();
    for (Expr op : ops) {
      out.add(exactly(op));
    }
    return Canonical.of(Operands.rebuild(e, out));
  }

  private static RealNumeric rational(RealNumeric x) {
    if (!(x instanceof Real r)) {
      return x;
    }
    BigDecimal d = r.value();
    return d.scale() <= 0
        ? Int.of(d.toBigIntegerExact())
        : Rational.of(d.unscaledValue(), BigInteger.TEN.pow(d.scale()));
  }

  /** {@code 10^-(p−5)}, the relative tolerance of the numeric zero tests. */
  private static RealNumeric tolerance(int p, MathContext mc) {
    return (RealNumeric) Int.of(10).pow(-(p - 5), mc);
  }

  /** Whether two numbers agree within {@code tolerance} relative to the larger, or 1. */
  private static boolean close(Num a, Num b, RealNumeric tolerance, MathContext mc) {
    RealNumeric scale = magnitude(a.value());
    RealNumeric other = magnitude(b.value());
    scale = other.compareTo(scale) > 0 ? other : scale;
    scale = Int.ONE.compareTo(scale) > 0 ? Int.ONE : scale;
    RealNumeric gap = magnitude(a.value().subtract(b.value(), mc));
    return gap.compareTo((RealNumeric) tolerance.multiply(scale, mc)) <= 0;
  }

  private static boolean hasDecimal(Matrix m) {
    for (List<Expr> row : m.rows()) {
      for (Expr e : row) {
        if (inexact(e)) {
          return true;
        }
      }
    }
    return false;
  }

  /** Whether an expression holds an inexact number. */
  static boolean inexact(Expr e) {
    if (e instanceof Num n) {
      return !n.value().isExact();
    }
    for (Expr op : Operands.of(e)) {
      if (inexact(op)) {
        return true;
      }
    }
    return false;
  }

  /** {@code λ}, or {@code λ₁}, … when the matrix holds it. */
  private static Symbol fresh(Matrix m) {
    Symbol x = Symbol.of("λ");
    for (int k = 1; Subspaces.occurs(m, x); k++) {
      x = Symbol.of("λ_" + k);
    }
    return x;
  }

  static Context withPrecision(Context c, int digits) {
    return new Context(
        c.assumptions(),
        new MathContext(digits, RoundingMode.HALF_EVEN),
        c.traceEnabled(),
        c.rewriteCap(),
        c.providers(),
        c.numerics(),
        c.cancellation());
  }
}
