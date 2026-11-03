package mathcanvas.engine.algebra;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.numbertheory.Factorizer;
import mathcanvas.engine.provider.Steps;

/**
 * Factoring over ℚ, the default {@code factor} provider's algorithm.
 *
 * <ol>
 *   <li>The rational content and sign are taken out ({@code pull-content}), then the largest
 *       monomial dividing every term ({@code pull-power}).
 *   <li>Yun's square-free decomposition ({@code square-free-split}).
 *   <li>Each square-free part is split into irreducibles:
 *       <ul>
 *         <li>in several variables, the content in each variable is split off first ({@code
 *             split-content}), and a part of degree 1 in some variable is irreducible;
 *         <li>in one variable: the rational roots {@code p/q}, {@code p | a₀}, {@code q | aₙ}, cut
 *             by the Cauchy bound and by {@code (q−p) | f(1)} and {@code (q+p) | f(−1)} and checked
 *             exactly ({@code rational-root}); a part of degree 2 or 3 with no root is irreducible;
 *             one of degree 4 to {@link #KRONECKER_MAX_DEGREE} goes to Kronecker's method (Knuth,
 *             vol. 2, §4.6.2): a factor of degree {@code d} takes divisors of {@code f(a)} at
 *             {@code d+1} integer points, and each combination is interpolated and tried ({@code
 *             kronecker-split}), at most {@link #KRONECKER_CAP} combinations in one {@code factor}
 *             call, its parts and the bivariate images' factorizations counted together;
 *         <li>in two variables, a homogeneous part is factored through {@code f(x, 1)}, each factor
 *             {@code g} becoming {@code y^deg(g)·g(x/y)} ({@code homogeneous-split});
 *         <li>any other part in two variables: Kronecker's substitution {@code y → x^D}, {@code D =
 *             deg_x f + 1}, turns the part into a univariate polynomial of degree at most {@link
 *             #SUBSTITUTED_MAX_DEGREE}; it is factored, and every sub-multiset of its irreducible
 *             factors, at most {@link #SUBSETS_CAP}, is mapped back and tried as a divisor ({@code
 *             bivariate-split}). Each factor {@code h} of the part has {@code deg_x h < D}, so its
 *             image is such a product and maps back to it: when no image divides, the part is
 *             irreducible if the substituted polynomial's factorization is complete. In practice an
 *             image's parts above degree 8 are split only by their rational roots, so many ordinary
 *             products of two bivariate factors come back incomplete.
 *       </ul>
 *   <li>A part beyond these bounds (degree above {@link #KRONECKER_MAX_DEGREE} with no further
 *       roots, three or more variables, a coefficient that cannot be factored within {@link
 *       Factorizer.Budget#SHORT}, or a cap reached) stays as it is and the factorization is marked
 *       incomplete; no step is recorded for it, since nothing changes.
 * </ol>
 *
 * <p>Each step that changes the partial factorization is recorded with the whole of it before and
 * after, when the steps are wanted: a linear part is not searched for roots, and a step whose
 * expression is unchanged is not recorded. A degree above {@link Polynomial#MAX_DEGREE} in some
 * kernel is refused with {@link AlgebraLimitException}.
 */
public final class Factoring {

  /** The highest degree Kronecker's method is tried on. */
  public static final int KRONECKER_MAX_DEGREE = 8;

  /** The most interpolations Kronecker's method makes in one {@code factor} call. */
  public static final int KRONECKER_CAP = 200_000;

  /** The most candidates the rational-root search tries for one part. */
  public static final int ROOT_CAP = 100_000;

  /** The highest degree of a substituted bivariate part that is factored. */
  public static final int SUBSTITUTED_MAX_DEGREE = 64;

  /** The most sub-multisets of a substituted part's factors that are tried. */
  public static final int SUBSETS_CAP = 4096;

  /** The rule set name of the steps, the narration bundle's prefix. */
  public static final String RULE_SET = "factor";

  private Factoring() {}

  /**
   * Factors {@code p} over ℚ.
   *
   * @param p the polynomial
   * @param steps where the steps are recorded
   * @return the factorization in normal form; 0 factors as unit 0 with no factors
   * @throws AlgebraLimitException if a degree exceeds {@link Polynomial#MAX_DEGREE}
   */
  public static Factorization factor(Polynomial p, Steps steps) {
    return factor(p, steps, Cancellation.NEVER);
  }

  /**
   * Factors {@code p} as {@link #factor(Polynomial, Steps)} does, checking {@code cancellation} at
   * every candidate root, every Kronecker combination and every step of the gcds and square-free
   * splits.
   *
   * @param p a polynomial
   * @param steps where the steps are recorded
   * @param cancellation the request to stop
   * @return the factorization in normal form; 0 factors as unit 0 with no factors
   * @throws AlgebraLimitException if a degree exceeds {@link Polynomial#MAX_DEGREE}
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Factorization factor(Polynomial p, Steps steps, Cancellation cancellation) {
    AlgebraLimitException.checkDegree(p);
    return factor(p, steps, new long[1], cancellation);
  }

  /** Factors {@code p}, counting Kronecker's interpolations in the shared {@code budget}. */
  private static Factorization factor(
      Polynomial p, Steps steps, long[] budget, Cancellation cancellation) {
    if (p.isConstant()) {
      return new Factorization(p.leadingCoefficient(), List.of(), true);
    }
    return new Run(p, steps, budget, cancellation).run();
  }

  /** One factoring: the partial factorization so far and the steps recorded. */
  private static final class Run {

    private final Polynomial input;
    private final Steps steps;
    private final boolean recording;
    private RealNumeric unit;
    private final List<Factorization.Factor> factors = new ArrayList<>();
    private boolean complete = true;
    // Kronecker's interpolations so far in this factor call, the parts and the recursion together
    private final long[] budget;
    private final Cancellation cancellation;

    Run(Polynomial input, Steps steps, long[] budget, Cancellation cancellation) {
      this.input = input;
      this.steps = steps;
      this.budget = budget;
      this.cancellation = cancellation;
      this.recording = steps != Steps.NONE;
      this.unit = Int.ONE;
    }

    Factorization run() {
      Polynomial pp = Gcd.primitivePart(input);
      RealNumeric c = Q.divide(input.leadingCoefficient(), pp.leadingCoefficient());
      if (!c.isOne()) {
        Expr before = expr(List.of(new Factorization.Factor(input, 1)), Int.ONE);
        unit = c;
        record(
            "pull-content",
            before,
            List.of(new Factorization.Factor(pp, 1)),
            bind("c_", Num.of(c)));
      }
      Polynomial rest = pullPower(pp);
      factors.add(new Factorization.Factor(rest, 1));
      if (!rest.isConstant()) {
        splitSquareFree();
      }
      factors.removeIf(f -> f.base().isConstant());
      return new Factorization(unit, factors, complete).normalised();
    }

    private Polynomial pullPower(Polynomial p) {
      int n = p.variables().size();
      int[] min = new int[n];
      Arrays.fill(min, Integer.MAX_VALUE);
      for (Polynomial.Term t : p.terms()) {
        for (int i = 0; i < n; i++) {
          min[i] = Math.min(min[i], t.monomial().exponent(i));
        }
      }
      Map<Monomial, RealNumeric> one = new TreeMap<>(Monomial.ORDER);
      one.put(new Monomial(min), Int.ONE);
      Polynomial m = Polynomial.build(p.variables(), one);
      if (m.isConstant()) {
        return p;
      }
      Polynomial rest = Division.exactQuotient(p, m).orElseThrow();
      Expr before = expr(List.of(new Factorization.Factor(p, 1)), unit);
      for (int i = 0; i < n; i++) {
        if (min[i] > 0) {
          factors.add(new Factorization.Factor(Polynomial.variable(p.variables().get(i)), min[i]));
        }
      }
      if (recording) {
        List<Factorization.Factor> after = new ArrayList<>(factors);
        after.add(new Factorization.Factor(rest, 1));
        record("pull-power", before, after, bind("m_", m.toExpr()));
      }
      return rest;
    }

    /** Splits the last factor, of multiplicity 1, by Yun's algorithm and then into irreducibles. */
    private void splitSquareFree() {
      Polynomial p = factors.removeLast().base();
      SquareFree.Decomposition d = SquareFree.of(p, cancellation);
      List<Factorization.Factor> parts = new ArrayList<>();
      for (int i = 0; i < d.parts().size(); i++) {
        if (!d.parts().get(i).isConstant()) {
          parts.add(new Factorization.Factor(d.parts().get(i), i + 1));
        }
      }
      Expr before = recording ? expr(withLast(p, 1), unit) : null;
      unit = Q.multiply(unit, d.unit());
      factors.addAll(parts);
      if (parts.size() > 1 || parts.getFirst().multiplicity() > 1) {
        record("square-free-split", before, factors, Bindings.EMPTY);
      }
      for (Factorization.Factor part : parts) {
        int k = factors.indexOf(part);
        split(k);
      }
    }

    /** Splits factor {@code k} (square-free, primitive) into irreducibles, in place. */
    private void split(int k) {
      Factorization.Factor f = factors.get(k);
      Polynomial p = f.base();
      if (p.isConstant()) {
        return;
      }
      if (p.variables().size() == 1) {
        univariate(k);
        return;
      }
      for (Expr v : p.variables()) {
        Polynomial c = Gcd.content(p, v, cancellation);
        if (!c.isConstant()) {
          Polynomial q = Division.exactQuotient(p, c).orElseThrow();
          replace(k, List.of(c, q), "split-content", bind("c_", c.toExpr(), "v_", v));
          split(k + 1);
          split(k);
          return;
        }
      }
      for (Expr v : p.variables()) {
        if (p.degree(v) == 1) {
          return;
        }
      }
      if (p.variables().size() == 2) {
        bivariate(k);
        return;
      }
      stop(k);
    }

    // Univariate.

    private void univariate(int k) {
      Polynomial p = factors.get(k).base();
      Expr x = p.variables().getFirst();
      Roots roots = rationalRoots(k, x, ZPoly.of(p));
      k = roots.index();
      ZPoly f = ZPoly.of(factors.get(k).base());
      int n = f.degree();
      if (n <= 1) {
        return;
      }
      if (!roots.complete()) {
        stop(k);
        return;
      }
      if (n <= 3) {
        return;
      }
      if (n > KRONECKER_MAX_DEGREE) {
        stop(k);
        return;
      }
      kronecker(k, x, f);
    }

    /**
     * The outcome of a rational-root search.
     *
     * @param index where the factor left is
     * @param complete whether every candidate was tried
     */
    private record Roots(int index, boolean complete) {}

    /** Divides factor {@code k} by its rational roots, each a new linear factor before it. */
    private Roots rationalRoots(int k, Expr x, ZPoly f) {
      if (f.degree() <= 1) {
        return new Roots(k, true);
      }
      Optional<List<BigInteger>> ps = Factorizer.divisors(f.c()[0], ROOT_CAP, cancellation);
      Optional<List<BigInteger>> qs = Factorizer.divisors(f.lc(), ROOT_CAP, cancellation);
      if (ps.isEmpty() || qs.isEmpty() || (long) ps.get().size() * qs.get().size() * 2 > ROOT_CAP) {
        return new Roots(k, false);
      }
      BigInteger bound = BigInteger.ZERO;
      for (int i = 0; i < f.degree(); i++) {
        bound = bound.max(f.c()[i].abs());
      }
      BigInteger lead = f.lc().abs();
      for (BigInteger q : qs.get()) {
        for (BigInteger p0 : ps.get()) {
          if (!p0.gcd(q).equals(BigInteger.ONE)) {
            continue;
          }
          // Cauchy: |p/q| <= 1 + max|a_i|/|a_n|
          if (p0.multiply(lead).compareTo(q.multiply(lead.add(bound))) > 0) {
            continue;
          }
          for (BigInteger p : List.of(p0, p0.negate())) {
            cancellation.check();
            // a linear part is its own root's factor: dividing it would change nothing
            if (f.degree() <= 1) {
              return new Roots(k, true);
            }
            BigInteger f1 = f.evaluate(BigInteger.ONE);
            BigInteger fm1 = f.evaluate(BigInteger.ONE.negate());
            BigInteger qmp = q.subtract(p);
            BigInteger qpp = q.add(p);
            if (qmp.signum() != 0 && f1.mod(qmp.abs()).signum() != 0) {
              continue;
            }
            if (qpp.signum() != 0 && fm1.mod(qpp.abs()).signum() != 0) {
              continue;
            }
            if (f.evaluateScaled(p, q).signum() != 0) {
              continue;
            }
            ZPoly linear = new ZPoly(new BigInteger[] {p.negate(), q});
            ZPoly quotient = f.exactQuotient(linear);
            Polynomial lin = linear.toPolynomial(x);
            replace(
                k,
                List.of(lin, quotient.toPolynomial(x)),
                "rational-root",
                bind("f_", lin.toExpr(), "r_", Num.of(Q.of(p, q))));
            k++;
            f = quotient;
          }
        }
      }
      return new Roots(k, true);
    }

    private void kronecker(int k, Expr x, ZPoly f) {
      int n = f.degree();
      for (int d = 2; d <= n / 2; d++) {
        cancellation.check();
        List<BigInteger> points = points(f, d + 1);
        List<List<BigInteger>> divisors = new ArrayList<>();
        long count = 1L << d;
        for (BigInteger a : points) {
          Optional<List<BigInteger>> ds =
              Factorizer.divisors(f.evaluate(a), KRONECKER_CAP, cancellation);
          if (ds.isEmpty()) {
            stop(k);
            return;
          }
          divisors.add(ds.get());
          count *= ds.get().size();
          if (count > KRONECKER_CAP) {
            break;
          }
        }
        budget[0] += count;
        if (count > KRONECKER_CAP || budget[0] > KRONECKER_CAP) {
          stop(k);
          return;
        }
        ZPoly g = search(f, points, divisors, d, cancellation);
        if (g != null) {
          ZPoly h = f.exactQuotient(g);
          replace(
              k,
              List.of(g.toPolynomial(x), h.toPolynomial(x)),
              "kronecker-split",
              bind("f_", g.toPolynomial(x).toExpr()));
          // g has the least degree of any factor, so it is irreducible; h may split further
          if (h.degree() >= 4) {
            kronecker(k + 1, x, h);
          }
          return;
        }
      }
    }

    /** The {@code count} integers nearest 0 with the smallest non-zero |f(a)|. */
    private static List<BigInteger> points(ZPoly f, int count) {
      List<BigInteger> scanned = new ArrayList<>();
      for (int i = 0; scanned.size() < count + 4; i++) {
        BigInteger a = BigInteger.valueOf(i % 2 == 0 ? i / 2 : -(i + 1) / 2);
        if (f.evaluate(a).signum() != 0) {
          scanned.add(a);
        }
      }
      List<BigInteger> sorted = new ArrayList<>(scanned);
      sorted.sort((a, b) -> f.evaluate(a).abs().compareTo(f.evaluate(b).abs()));
      return sorted.subList(0, count);
    }

    /** Tries every combination of divisor values; returns a factor of degree d, or null. */
    private static ZPoly search(
        ZPoly f,
        List<BigInteger> points,
        List<List<BigInteger>> divisors,
        int d,
        Cancellation cancellation) {
      int m = points.size();
      int[] index = new int[m];
      int[] sign = new int[m];
      BigInteger lead = f.lc();
      while (true) {
        cancellation.check();
        BigInteger[] values = new BigInteger[m];
        for (int i = 0; i < m; i++) {
          BigInteger v = divisors.get(i).get(index[i]);
          values[i] = sign[i] < 0 ? v.negate() : v;
        }
        ZPoly g = interpolate(points, values);
        if (g != null && g.degree() == d && lead.mod(g.lc().abs()).signum() == 0) {
          ZPoly h = f.exactQuotient(g);
          if (h != null) {
            return g.lc().signum() < 0 ? g.times(BigInteger.ONE.negate()) : g;
          }
        }
        // next combination: the first value keeps a positive sign
        int i = 0;
        while (i < m) {
          if (i > 0 && sign[i] == 0) {
            sign[i] = -1;
            break;
          }
          sign[i] = 0;
          index[i]++;
          if (index[i] < divisors.get(i).size()) {
            break;
          }
          index[i] = 0;
          i++;
        }
        if (i == m) {
          return null;
        }
      }
    }

    /**
     * The polynomial of degree below {@code points.size()} through the values, by Newton's divided
     * differences; null when its coefficients are not all integers.
     */
    private static ZPoly interpolate(List<BigInteger> points, BigInteger[] values) {
      int m = points.size();
      RealNumeric[] dd = new RealNumeric[m];
      for (int i = 0; i < m; i++) {
        dd[i] = Q.of(values[i]);
      }
      for (int j = 1; j < m; j++) {
        for (int i = m - 1; i >= j; i--) {
          RealNumeric num = Q.subtract(dd[i], dd[i - 1]);
          BigInteger den = points.get(i).subtract(points.get(i - j));
          dd[i] = Q.divide(num, Q.of(den));
        }
      }
      if (!dd[m - 1].isInteger()) {
        return null;
      }
      // Horner on the Newton form: c = dd[m-1]; c = c·(x - p_i) + dd[i]
      RealNumeric[] c = new RealNumeric[m];
      Arrays.fill(c, Int.ZERO);
      c[0] = dd[m - 1];
      for (int i = m - 2; i >= 0; i--) {
        BigInteger p = points.get(i);
        for (int k = m - 1; k >= 1; k--) {
          c[k] = Q.subtract(c[k - 1], Q.multiply(c[k], Q.of(p)));
        }
        c[0] = Q.subtract(dd[i], Q.multiply(c[0], Q.of(p)));
      }
      BigInteger[] out = new BigInteger[m];
      for (int i = 0; i < m; i++) {
        if (!c[i].isInteger()) {
          return null;
        }
        out[i] = Q.num(c[i]);
      }
      return new ZPoly(out);
    }

    // Bivariate.

    private void bivariate(int k) {
      Polynomial p = factors.get(k).base();
      if (homogeneous(p)) {
        homogeneous(k);
        return;
      }
      Expr x = p.variables().get(0);
      int dx = p.degree(x) + 1;
      long degree = 0;
      for (Polynomial.Term t : p.terms()) {
        degree = Math.max(degree, t.monomial().exponent(0) + (long) dx * t.monomial().exponent(1));
      }
      if (degree > SUBSTITUTED_MAX_DEGREE) {
        stop(k);
        return;
      }
      Map<Monomial, RealNumeric> sub = new TreeMap<>(Monomial.ORDER);
      for (Polynomial.Term t : p.terms()) {
        int e = t.monomial().exponent(0) + dx * t.monomial().exponent(1);
        sub.merge(Monomial.power(1, 0, e), t.coefficient(), Q::add);
      }
      Polynomial g = Polynomial.build(List.of(x), sub);
      Factorization fg = Factoring.factor(g, Steps.NONE, budget, cancellation);
      List<Factorization.Factor> fs = fg.factors();
      long subsets = 1;
      for (Factorization.Factor f : fs) {
        subsets *= f.multiplicity() + 1;
        if (subsets > SUBSETS_CAP) {
          stop(k);
          return;
        }
      }
      int[] e = new int[fs.size()];
      while (true) {
        int i = 0;
        while (i < e.length && e[i] == fs.get(i).multiplicity()) {
          e[i] = 0;
          i++;
        }
        if (i == e.length) {
          // no divisor among the images: irreducible when the image's factorization is complete
          if (!fg.complete()) {
            stop(k);
          }
          return;
        }
        e[i]++;
        cancellation.check();
        Polynomial h = Polynomial.ONE;
        for (int j = 0; j < e.length; j++) {
          h = h.multiply(fs.get(j).base().pow(e[j]));
        }
        Polynomial back = unsubstitute(h, p.variables(), dx);
        if (back.isConstant() || back.totalDegree() >= p.totalDegree()) {
          continue;
        }
        Optional<Polynomial> q = Division.exactQuotient(p, back);
        if (q.isPresent()) {
          Polynomial b = Gcd.primitivePart(back);
          replace(
              k,
              List.of(b, Division.exactQuotient(p, b).orElseThrow()),
              "bivariate-split",
              bind("f_", b.toExpr()));
          split(k + 1);
          split(k);
          return;
        }
      }
    }

    private static boolean homogeneous(Polynomial p) {
      long d = p.totalDegree();
      return p.terms().stream().allMatch(t -> t.monomial().degree() == d);
    }

    /**
     * Factors a homogeneous part in {@code x} and {@code y}: {@code f(x, 1)} is factored and each
     * factor {@code g} becomes {@code y^deg(g)·g(x/y)}. The part has no factor {@code y} (the
     * monomial content was taken out), so the degrees add up and the factorization is complete when
     * that of {@code f(x, 1)} is.
     */
    private void homogeneous(int k) {
      Polynomial p = factors.get(k).base();
      Expr x = p.variables().get(0);
      Map<Monomial, RealNumeric> dehomogenised = new TreeMap<>(Monomial.ORDER);
      for (Polynomial.Term t : p.terms()) {
        dehomogenised.put(Monomial.power(1, 0, t.monomial().exponent(0)), t.coefficient());
      }
      Factorization fq =
          Factoring.factor(
              Polynomial.build(List.of(x), dehomogenised), Steps.NONE, budget, cancellation);
      if (fq.factors().size() == 1 && fq.factors().getFirst().multiplicity() == 1) {
        if (!fq.complete()) {
          stop(k);
        }
        return;
      }
      List<Polynomial> parts = new ArrayList<>();
      for (Factorization.Factor f : fq.factors()) {
        Map<Monomial, RealNumeric> m = new TreeMap<>(Monomial.ORDER);
        int d = (int) f.base().totalDegree();
        for (Polynomial.Term t : f.base().terms()) {
          int i = (int) t.monomial().degree();
          m.put(new Monomial(new int[] {i, d - i}), t.coefficient());
        }
        Polynomial h = Polynomial.build(p.variables(), m);
        for (int j = 0; j < f.multiplicity(); j++) {
          parts.add(h);
        }
      }
      parts.set(0, parts.getFirst().multiply(fq.unit()));
      replace(k, parts, "homogeneous-split", bind("v_", p.variables().get(1)));
      if (!fq.complete()) {
        stop(k + parts.size() - 1);
      }
    }

    /** Maps {@code x^e} back to {@code x^(e mod D) y^(e div D)}. */
    private static Polynomial unsubstitute(Polynomial h, List<Expr> vs, int dx) {
      Map<Monomial, RealNumeric> m = new TreeMap<>(Monomial.ORDER);
      for (Polynomial.Term t : h.terms()) {
        int e = (int) t.monomial().degree();
        m.merge(new Monomial(new int[] {e % dx, e / dx}), t.coefficient(), Q::add);
      }
      return Polynomial.build(vs, m);
    }

    // The partial factorization.

    /**
     * Leaves factor {@code k} as it is: the factorization is incomplete. Nothing changes, so no
     * step.
     */
    private void stop(int k) {
      complete = false;
    }

    /** Replaces factor {@code k} by {@code parts}, each with its multiplicity, and records it. */
    private void replace(int k, List<Polynomial> parts, String rule, Bindings bindings) {
      Expr before = recording ? expr(factors, unit) : null;
      int m = factors.get(k).multiplicity();
      factors.remove(k);
      for (int i = parts.size() - 1; i >= 0; i--) {
        factors.add(k, new Factorization.Factor(parts.get(i), m));
      }
      record(rule, before, factors, bindings);
    }

    private List<Factorization.Factor> withLast(Polynomial p, int m) {
      List<Factorization.Factor> l = new ArrayList<>(factors);
      l.add(new Factorization.Factor(p, m));
      return l;
    }

    /** Records a step that changed the expression; a step that changed nothing is not one. */
    private void record(String rule, Expr before, List<Factorization.Factor> after, Bindings b) {
      if (recording) {
        Expr a = expr(after, unit);
        if (!a.equals(before)) {
          steps.record(rule, before, a, b);
        }
      }
    }

    private static Expr expr(List<Factorization.Factor> fs, RealNumeric unit) {
      List<Factorization.Factor> nonConstant = new ArrayList<>();
      RealNumeric u = unit;
      for (Factorization.Factor f : fs) {
        if (f.base().isConstant()) {
          u = Q.multiply(u, Q.pow(f.base().leadingCoefficient(), f.multiplicity()));
        } else {
          nonConstant.add(f);
        }
      }
      return new Factorization(u, nonConstant, true).toExpr();
    }

    private static Bindings bind(String name, Expr value) {
      return new Bindings(List.of(new Bindings.Binding(name, value, null, null)));
    }

    private static Bindings bind(String n1, Expr v1, String n2, Expr v2) {
      return new Bindings(
          List.of(
              new Bindings.Binding(n1, v1, null, null), new Bindings.Binding(n2, v2, null, null)));
    }
  }
}
