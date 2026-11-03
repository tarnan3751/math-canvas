package mathcanvas.engine.algebra;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprOrder;
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
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;

/**
 * An immutable sparse polynomial with rational coefficients in finitely many variables.
 *
 * <p>The variables are <b>kernels</b>: symbols, or subexpressions the polynomial treats as opaque
 * indeterminates ({@code sin(x)}, {@code π}, {@code x^-1}, {@code x^(1/2)}, {@code 2^x}, a decimal
 * or complex number), sorted by {@link ExprOrder} with no repeats. Every variable occurs in some
 * term, so two polynomials are equal exactly when they are equal as polynomials. The terms are
 * sorted in descending {@link Monomial#ORDER} (graded lexicographic) with distinct monomials and
 * non-zero exact coefficients ({@link Int} or {@link Rational}).
 *
 * <p>Treating kernels as independent is sound: putting values for the kernels is a ring
 * homomorphism, so every identity the polynomial arithmetic proves holds for the expressions. It is
 * not complete: {@code x} and {@code x^(1/2)} are two variables, so a statement that two
 * polynomials are coprime or that one is irreducible holds over the kernels, not necessarily over
 * the expressions they stand for.
 *
 * @param variables the kernels, sorted by {@link ExprOrder}, each occurring in some term
 * @param terms the terms, in descending monomial order
 */
public record Polynomial(List<Expr> variables, List<Term> terms) {

  /**
   * One term: a monomial and its coefficient.
   *
   * @param monomial the monomial, over the polynomial's variables
   * @param coefficient the coefficient, an exact non-zero rational
   */
  public record Term(Monomial monomial, RealNumeric coefficient) {}

  /** The most terms a product or power may have; a larger one throws. */
  public static final int MAX_TERMS = 100_000;

  /**
   * The most coefficient bits a product or power may be estimated to hold (2²⁸, 32 MB), checked
   * before it is built; a larger one throws.
   */
  public static final long MAX_TOTAL_BITS = 1L << 28;

  /**
   * The highest degree in any one kernel that {@code gcd}, {@code lcm}, {@code factor}, {@code
   * apart} and {@code cancel} accept; their dense univariate algorithms are at least quadratic in
   * it.
   */
  public static final int MAX_DEGREE = 10_000;

  /** The zero polynomial, with no terms. */
  public static final Polynomial ZERO = new Polynomial(List.of(), List.of());

  /** The polynomial 1. */
  public static final Polynomial ONE = constant(Int.ONE);

  /**
   * Checks the invariants and copies the lists.
   *
   * @throws IllegalArgumentException if the variables are not sorted and distinct, a variable does
   *     not occur, a monomial has the wrong length, a coefficient is zero or not an exact rational,
   *     or the terms are not in strictly descending order
   */
  public Polynomial {
    variables = List.copyOf(variables);
    terms = List.copyOf(terms);
    for (int i = 1; i < variables.size(); i++) {
      if (ExprOrder.INSTANCE.compare(variables.get(i - 1), variables.get(i)) >= 0) {
        throw new IllegalArgumentException("the variables are not sorted: " + variables);
      }
    }
    boolean[] occurs = new boolean[variables.size()];
    Monomial previous = null;
    for (Term t : terms) {
      if (t.monomial().size() != variables.size()) {
        throw new IllegalArgumentException("a monomial over other variables: " + t);
      }
      if (!Q.isRational(t.coefficient()) || t.coefficient().isZero()) {
        throw new IllegalArgumentException("a coefficient that is zero or inexact: " + t);
      }
      if (previous != null && Monomial.ORDER.compare(previous, t.monomial()) <= 0) {
        throw new IllegalArgumentException("the terms are not in descending order");
      }
      previous = t.monomial();
      for (int i = 0; i < occurs.length; i++) {
        occurs[i] |= t.monomial().exponent(i) > 0;
      }
    }
    for (int i = 0; i < occurs.length; i++) {
      if (!occurs[i]) {
        throw new IllegalArgumentException("the variable does not occur: " + variables.get(i));
      }
    }
  }

  // Construction.

  /**
   * Returns the constant polynomial {@code c}.
   *
   * @param c an exact rational
   * @return the constant, {@link #ZERO} for 0
   */
  public static Polynomial constant(RealNumeric c) {
    if (c.isZero()) {
      return ZERO;
    }
    return new Polynomial(List.of(), List.of(new Term(Monomial.one(0), c)));
  }

  /**
   * Returns the polynomial {@code v}, one variable to the first power.
   *
   * @param kernel the variable
   * @return the polynomial
   */
  public static Polynomial variable(Expr kernel) {
    return new Polynomial(List.of(kernel), List.of(new Term(Monomial.power(1, 0, 1), Int.ONE)));
  }

  /**
   * Converts an expression, collecting every kernel it contains into the variable list. The
   * expression is canonicalised first. Exact rationals are coefficients; sums and products convert
   * operand by operand; a power of a non-numeric base with an exact integer exponent {@code n ≥ 0}
   * is a power of the converted base; {@code b^-n} is the kernel {@code b^-1} to the {@code n};
   * {@code b^(p/q)} is the kernel {@code b^(1/q)} to the {@code p} (with {@code b^(-1/q)} for
   * {@code p < 0}). The other scalar expressions are kernels: symbols, {@code π} and {@code e},
   * applications, decimal and complex numbers, a power of a number the canonical form left unfolded
   * ({@code 3^100000000}), other powers, and the unevaluated forms. Nothing else is: {@code ∞},
   * {@code undefined}, the domains, truth values, relations, lists, sets, intervals, lambdas,
   * vectors and matrices are refused.
   *
   * @param e the expression
   * @return the polynomial
   * @throws IllegalArgumentException if {@code e} contains a factor that does not commute or a node
   *     that is refused as a kernel
   * @throws AlgebraLimitException if a power would have more than {@link #MAX_TERMS} terms
   */
  public static Polynomial of(Expr e) {
    return of(e, Cancellation.NEVER);
  }

  /**
   * Converts an expression as {@link #of(Expr)} does, checking {@code cancellation} at every node
   * converted and every term multiplied.
   *
   * @param e the expression
   * @param cancellation the request to stop
   * @return the polynomial
   * @throws IllegalArgumentException if {@code e} contains a factor that does not commute or a node
   *     that is refused as a kernel
   * @throws AlgebraLimitException if a power would have more than {@link #MAX_TERMS} terms
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Polynomial of(Expr e, Cancellation cancellation) {
    Expr c = Canonical.of(e);
    if (!Canonical.commutes(c)) {
      throw new IllegalArgumentException("a polynomial's variables commute: " + c);
    }
    return convert(c, cancellation);
  }

  /**
   * Converts an expression that is polynomial in {@code variables}.
   *
   * @param e the expression
   * @param variables the kernels allowed
   * @return the polynomial, or empty when {@code e} has a kernel outside {@code variables}
   * @throws IllegalArgumentException if {@code e} contains a factor that does not commute or a node
   *     that is refused as a kernel
   * @throws AlgebraLimitException if a power would have more than {@link #MAX_TERMS} terms
   */
  public static Optional<Polynomial> of(Expr e, List<? extends Expr> variables) {
    Polynomial p = of(e);
    List<Expr> allowed = variables.stream().<Expr>map(Canonical::of).toList();
    return allowed.containsAll(p.variables) ? Optional.of(p) : Optional.empty();
  }

  /** Converts a canonical expression whose factors commute. */
  private static Polynomial convert(Expr e, Cancellation cancellation) {
    cancellation.check();
    return switch (e) {
      case Num n when Q.isRational(n.value()) -> constant((RealNumeric) n.value());
      case Sum s -> sum(s.terms().stream().map(t -> convert(t, cancellation)).toList());
      case Product f ->
          product(f.factors().stream().map(t -> convert(t, cancellation)).toList(), cancellation);
      // a power of a number that the canonical form left unfolded is too large to compute
      case Power w when w.base() instanceof Num -> kernel(e);
      case Power w when w.exponent() instanceof Num n && n.value() instanceof Int k -> {
        BigInteger v = k.value();
        // exponents from -Integer.MAX_VALUE to Integer.MAX_VALUE; beyond, the power is a kernel
        if (v.signum() >= 0 && v.bitLength() <= 31) {
          yield convert(w.base(), cancellation).pow(v.intValue(), cancellation);
        }
        if (v.signum() < 0 && v.negate().bitLength() <= 31) {
          yield kernelPower(
              Canonical.of(Power.of(w.base(), Num.MINUS_ONE)), -v.intValue(), cancellation);
        }
        yield kernel(e);
      }
      case Power w
          when w.exponent() instanceof Num n
              && n.value() instanceof Rational r
              && r.num().abs().bitLength() <= 31 -> {
        int p = r.num().intValue();
        RealNumeric root = Rational.of(BigInteger.valueOf(Integer.signum(p)), r.den());
        yield kernelPower(
            Canonical.of(Power.of(w.base(), Num.of(root))), Math.abs(p), cancellation);
      }
      default -> kernel(e);
    };
  }

  private static Polynomial kernelPower(Expr kernel, int k, Cancellation cancellation) {
    if (!(kernel instanceof Power)) {
      return convert(kernel, cancellation).pow(k, cancellation);
    }
    refuse(kernel);
    return new Polynomial(List.of(kernel), List.of(new Term(Monomial.power(1, 0, k), Int.ONE)));
  }

  /**
   * Whether an expression converts to a polynomial in scalar kernels: its factors commute, and it
   * holds no node that is refused as a kernel ({@link #of(Expr)}). Application arguments and the
   * unevaluated forms are not looked into.
   *
   * @param e a canonical expression
   * @return true if {@link #of(Expr)} would not refuse it for its kernels
   */
  public static boolean isScalar(Expr e) {
    return Canonical.commutes(e) && !refused(e);
  }

  private static Polynomial kernel(Expr e) {
    refuse(e);
    return variable(e);
  }

  private static void refuse(Expr kernel) {
    if (refused(kernel)) {
      throw new IllegalArgumentException("not a scalar kernel: " + kernel);
    }
  }

  /**
   * Whether an expression is no scalar: {@code ∞}, {@code undefined}, a domain, a truth value, a
   * relation, list, set, interval, lambda, vector or matrix, or a sum, product or power with one.
   * The arguments of applications and the unevaluated forms are not looked into: those are scalars
   * whatever they hold.
   */
  private static boolean refused(Expr e) {
    return switch (e) {
      case Constant c -> !c.equals(Constant.PI) && !c.equals(Constant.E);
      case Bool _,
          Relation _,
          ListExpr _,
          SetExpr _,
          Interval _,
          Lambda _,
          mathcanvas.engine.expr.Vector _,
          Matrix _ ->
          true;
      case Sum s -> s.terms().stream().anyMatch(Polynomial::refused);
      case Product p -> p.factors().stream().anyMatch(Polynomial::refused);
      case Power p -> refused(p.base()) || refused(p.exponent());
      case Num _,
          mathcanvas.engine.expr.Based _,
          Symbol _,
          mathcanvas.engine.expr.Unit _,
          Apply _,
          Piecewise _,
          Derivative _,
          Integral _,
          Limit _,
          BigSum _,
          BigProduct _ ->
          false;
    };
  }

  /** The sum of polynomials, merged in one pass over their terms. */
  private static Polynomial sum(List<Polynomial> ps) {
    List<Expr> vs = List.of();
    for (Polynomial p : ps) {
      vs = union(vs, p.variables);
    }
    Map<Monomial, RealNumeric> m = new TreeMap<>(Monomial.ORDER);
    for (Polynomial p : ps) {
      for (Term t : p.terms) {
        m.merge(lift(t.monomial(), p.variables, vs), t.coefficient(), Q::add);
      }
    }
    return build(vs, m);
  }

  /** The product of polynomials: the single terms combined first, then the sums multiplied in. */
  private static Polynomial product(List<Polynomial> ps, Cancellation cancellation) {
    List<Expr> vs = List.of();
    for (Polynomial p : ps) {
      if (p.isZero()) {
        return ZERO;
      }
      if (p.terms.size() == 1) {
        vs = union(vs, p.variables);
      }
    }
    int[] exponents = new int[vs.size()];
    RealNumeric c = Int.ONE;
    Polynomial result = null;
    for (Polynomial p : ps) {
      if (p.terms.size() == 1) {
        Term t = p.terms.getFirst();
        int[] x = lift(t.monomial(), p.variables, vs).raw();
        for (int i = 0; i < x.length; i++) {
          exponents[i] = Math.addExact(exponents[i], x[i]);
        }
        c = Q.multiply(c, t.coefficient());
      }
    }
    result = build(vs, Map.of(new Monomial(exponents), c));
    for (Polynomial p : ps) {
      if (p.terms.size() > 1) {
        result = result.multiply(p, cancellation);
      }
    }
    return result;
  }

  /**
   * Builds a polynomial from a map of coefficients over {@code variables}, dropping zero terms and
   * the variables that no longer occur.
   */
  static Polynomial build(List<Expr> variables, Map<Monomial, RealNumeric> coefficients) {
    int n = variables.size();
    boolean[] occurs = new boolean[n];
    List<Term> terms = new ArrayList<>(coefficients.size());
    for (Map.Entry<Monomial, RealNumeric> en : coefficients.entrySet()) {
      if (en.getValue().isZero()) {
        continue;
      }
      terms.add(new Term(en.getKey(), en.getValue()));
      int[] x = en.getKey().raw();
      for (int i = 0; i < n; i++) {
        occurs[i] |= x[i] > 0;
      }
    }
    int kept = 0;
    for (boolean b : occurs) {
      kept += b ? 1 : 0;
    }
    if (kept < n) {
      List<Expr> vs = new ArrayList<>(kept);
      int[] index = new int[kept];
      for (int i = 0, j = 0; i < n; i++) {
        if (occurs[i]) {
          vs.add(variables.get(i));
          index[j++] = i;
        }
      }
      List<Term> trimmed = new ArrayList<>(terms.size());
      for (Term t : terms) {
        int[] x = t.monomial().raw();
        int[] y = new int[kept];
        for (int j = 0; j < kept; j++) {
          y[j] = x[index[j]];
        }
        trimmed.add(new Term(new Monomial(y), t.coefficient()));
      }
      variables = vs;
      terms = trimmed;
    }
    terms.sort((a, b) -> Monomial.ORDER.compare(b.monomial(), a.monomial()));
    return new Polynomial(variables, terms);
  }

  // Conversion back.

  /**
   * Returns the canonical expression of the polynomial.
   *
   * @return {@code Canonical.of} of the sum of its terms
   */
  public Expr toExpr() {
    return Canonical.of(raw());
  }

  /**
   * Returns the raw sum of the terms, for a caller that canonicalises under its own precision (a
   * decimal kernel's powers are rounded there).
   */
  Expr raw() {
    if (terms.isEmpty()) {
      return Num.ZERO;
    }
    List<Expr> sum = new ArrayList<>(terms.size());
    for (Term t : terms) {
      List<Expr> product = new ArrayList<>();
      if (!t.coefficient().isOne()) {
        product.add(Num.of(t.coefficient()));
      }
      for (int i = 0; i < variables.size(); i++) {
        int k = t.monomial().exponent(i);
        if (k == 1) {
          product.add(variables.get(i));
        } else if (k > 1) {
          product.add(Power.of(variables.get(i), Num.of(k)));
        }
      }
      sum.add(
          switch (product.size()) {
            case 0 -> Num.ONE;
            case 1 -> product.getFirst();
            default -> Product.of(product);
          });
    }
    return sum.size() == 1 ? sum.getFirst() : Sum.of(sum);
  }

  // Queries.

  /**
   * Whether this is the zero polynomial.
   *
   * @return true if there are no terms
   */
  public boolean isZero() {
    return terms.isEmpty();
  }

  /**
   * Whether this is a constant, zero included.
   *
   * @return true if there are no variables
   */
  public boolean isConstant() {
    return variables.isEmpty();
  }

  /**
   * Returns the coefficients as a map in descending monomial order.
   *
   * @return an unmodifiable view
   */
  public SortedMap<Monomial, RealNumeric> coefficients() {
    TreeMap<Monomial, RealNumeric> m = new TreeMap<>(Monomial.ORDER.reversed());
    for (Term t : terms) {
      m.put(t.monomial(), t.coefficient());
    }
    return Collections.unmodifiableSortedMap(m);
  }

  /**
   * Returns the constant term.
   *
   * @return the coefficient of the monomial 1, 0 when there is none
   */
  public RealNumeric constantTerm() {
    if (terms.isEmpty() || terms.getLast().monomial().degree() != 0) {
      return Int.ZERO;
    }
    return terms.getLast().coefficient();
  }

  /**
   * Returns the coefficient of the leading term in the monomial order.
   *
   * @return the leading coefficient, 0 for the zero polynomial
   */
  public RealNumeric leadingCoefficient() {
    return terms.isEmpty() ? Int.ZERO : terms.getFirst().coefficient();
  }

  /**
   * Returns the total degree.
   *
   * @return the largest total degree of a term; -1 for the zero polynomial
   */
  public long totalDegree() {
    long d = -1;
    for (Term t : terms) {
      d = Math.max(d, t.monomial().degree());
    }
    return d;
  }

  /**
   * Returns the highest degree in any one variable.
   *
   * @return the largest exponent of any variable; 0 for a constant
   */
  public int maxDegree() {
    int d = 0;
    for (Term t : terms) {
      for (int e : t.monomial().raw()) {
        d = Math.max(d, e);
      }
    }
    return d;
  }

  /**
   * Returns the degree in one variable.
   *
   * @param v a kernel
   * @return the largest exponent of {@code v}; 0 when it does not occur, -1 for the zero polynomial
   */
  public int degree(Expr v) {
    if (terms.isEmpty()) {
      return -1;
    }
    int i = variables.indexOf(v);
    if (i < 0) {
      return 0;
    }
    int d = 0;
    for (Term t : terms) {
      d = Math.max(d, t.monomial().exponent(i));
    }
    return d;
  }

  /**
   * Returns the coefficient of {@code v^k}, a polynomial in the other variables.
   *
   * @param v a kernel
   * @param k the exponent
   * @return the coefficient
   */
  public Polynomial coefficient(Expr v, int k) {
    int i = variables.indexOf(v);
    if (i < 0) {
      return k == 0 ? this : ZERO;
    }
    Map<Monomial, RealNumeric> m = new TreeMap<>(Monomial.ORDER);
    for (Term t : terms) {
      if (t.monomial().exponent(i) == k) {
        int[] x = t.monomial().exponents();
        x[i] = 0;
        m.put(new Monomial(x), t.coefficient());
      }
    }
    return build(variables, m);
  }

  /**
   * Returns the leading coefficient in one variable.
   *
   * @param v a kernel
   * @return the coefficient of {@code v^degree(v)}
   */
  public Polynomial leadingCoefficient(Expr v) {
    return coefficient(v, Math.max(0, degree(v)));
  }

  /**
   * Returns the degree of a polynomial in at most one variable.
   *
   * @return the degree; -1 for the zero polynomial
   * @throws IllegalStateException if there are several variables
   */
  public int degree() {
    univariate();
    return (int) totalDegree();
  }

  /**
   * Returns the coefficient of {@code v^k} in a polynomial in at most one variable {@code v}.
   *
   * @param k the exponent
   * @return the coefficient
   * @throws IllegalStateException if there are several variables
   */
  public RealNumeric coefficient(int k) {
    univariate();
    for (Term t : terms) {
      if (t.monomial().degree() == k) {
        return t.coefficient();
      }
    }
    return Int.ZERO;
  }

  private void univariate() {
    if (variables.size() > 1) {
      throw new IllegalStateException("a polynomial in several variables: " + variables);
    }
  }

  // Arithmetic.

  /**
   * Returns {@code this + other}.
   *
   * @param other the addend
   * @return the sum
   */
  public Polynomial add(Polynomial other) {
    if (other.isZero()) {
      return this;
    }
    if (isZero()) {
      return other;
    }
    List<Expr> vs = union(variables, other.variables);
    Map<Monomial, RealNumeric> m = new TreeMap<>(Monomial.ORDER);
    for (Term t : terms) {
      m.put(lift(t.monomial(), variables, vs), t.coefficient());
    }
    for (Term t : other.terms) {
      m.merge(lift(t.monomial(), other.variables, vs), t.coefficient(), Q::add);
    }
    return build(vs, m);
  }

  /**
   * Returns {@code -this}.
   *
   * @return the negation
   */
  public Polynomial negate() {
    List<Term> ts = new ArrayList<>(terms.size());
    for (Term t : terms) {
      ts.add(new Term(t.monomial(), t.coefficient().negate()));
    }
    return new Polynomial(variables, ts);
  }

  /**
   * Returns {@code this - other}.
   *
   * @param other the subtrahend
   * @return the difference
   */
  public Polynomial subtract(Polynomial other) {
    return add(other.negate());
  }

  /**
   * Returns {@code c · this}.
   *
   * @param c an exact rational
   * @return the product
   */
  public Polynomial multiply(RealNumeric c) {
    if (c.isZero()) {
      return ZERO;
    }
    List<Term> ts = new ArrayList<>(terms.size());
    for (Term t : terms) {
      ts.add(new Term(t.monomial(), Q.multiply(t.coefficient(), c)));
    }
    return new Polynomial(variables, ts);
  }

  /**
   * Returns {@code this · other}.
   *
   * @param other the multiplier
   * @return the product
   * @throws AlgebraLimitException if the product has more than {@link #MAX_TERMS} terms, or its
   *     estimated size passes {@link #MAX_TOTAL_BITS}: its terms (the fewer of the pairs of terms
   *     and the monomials in the box of its degrees) times the sum of the largest coefficients'
   *     bits
   */
  public Polynomial multiply(Polynomial other) {
    return multiply(other, Cancellation.NEVER);
  }

  /** {@link #multiply(Polynomial)}, checking {@code cancellation} once per term of this. */
  Polynomial multiply(Polynomial other, Cancellation cancellation) {
    if (isZero() || other.isZero()) {
      return ZERO;
    }
    List<Expr> vs = union(variables, other.variables);
    checkTotalBits(productTerms(other, vs), 1, maxBits() + other.maxBits());
    List<Monomial> a = new ArrayList<>(terms.size());
    for (Term t : terms) {
      a.add(lift(t.monomial(), variables, vs));
    }
    List<Monomial> b = new ArrayList<>(other.terms.size());
    for (Term t : other.terms) {
      b.add(lift(t.monomial(), other.variables, vs));
    }
    Map<Monomial, RealNumeric> m = new TreeMap<>(Monomial.ORDER);
    for (int i = 0; i < a.size(); i++) {
      cancellation.check();
      RealNumeric ci = terms.get(i).coefficient();
      for (int j = 0; j < b.size(); j++) {
        m.merge(
            a.get(i).multiply(b.get(j)), Q.multiply(ci, other.terms.get(j).coefficient()), Q::add);
      }
      if (m.size() > MAX_TERMS) {
        throw tooLarge();
      }
    }
    return build(vs, m);
  }

  /**
   * Returns {@code this^n}: by the binomial theorem for two terms, otherwise by repeated squaring.
   *
   * @param n the exponent, at least 0
   * @return the power; {@code 0^0} is 1
   * @throws IllegalArgumentException if {@code n} is negative
   * @throws AlgebraLimitException if the power can have more than {@link #MAX_TERMS} terms or more
   *     than {@link #MAX_TOTAL_BITS} coefficient bits (checked at every multiplication, and for two
   *     terms as {@code (n+1)·n·(1 + bits(a) + bits(b))}), or a coefficient's power passes the
   *     folding bound
   */
  public Polynomial pow(int n) {
    return pow(n, Cancellation.NEVER);
  }

  /** {@link #pow(int)}, checking {@code cancellation} in every multiplication. */
  Polynomial pow(int n, Cancellation cancellation) {
    if (n < 0) {
      throw new IllegalArgumentException("a negative exponent: " + n);
    }
    if (n == 0) {
      return ONE;
    }
    if (n == 1 || isZero()) {
      return this;
    }
    if (terms.size() == 1) {
      Term t = terms.getFirst();
      int[] x = t.monomial().exponents();
      for (int i = 0; i < x.length; i++) {
        x[i] = Math.multiplyExact(x[i], n);
      }
      return new Polynomial(
          variables, List.of(new Term(new Monomial(x), Q.pow(t.coefficient(), n))));
    }
    if (mayExceedCap(n)) {
      throw tooLarge();
    }
    if (terms.size() == 2) {
      return binomial(n, cancellation);
    }
    Polynomial result = ONE;
    Polynomial square = this;
    for (int k = n; ; k >>= 1) {
      if ((k & 1) != 0) {
        result = result.multiply(square, cancellation);
      }
      if (k == 1) {
        return result;
      }
      square = square.multiply(square, cancellation);
    }
  }

  /**
   * Whether both the multinomial count and the count of monomials of degree at most n·deg exceed
   * the cap.
   */
  private boolean mayExceedCap(int n) {
    int t = terms.size();
    BigInteger dense = BigInteger.valueOf(n).multiply(BigInteger.valueOf(totalDegree()));
    return binomialExceeds(BigInteger.valueOf(n + (long) t - 1), t - 1)
        && binomialExceeds(dense.add(BigInteger.valueOf(variables.size())), variables.size());
  }

  /**
   * Whether C(top, k) exceeds {@link #MAX_TERMS}; C(top - k + i, i) grows with i, so it stops
   * early.
   */
  private static boolean binomialExceeds(BigInteger top, int k) {
    BigInteger cap = BigInteger.valueOf(MAX_TERMS);
    BigInteger r = BigInteger.ONE;
    BigInteger base = top.subtract(BigInteger.valueOf(k));
    for (int i = 1; i <= k; i++) {
      r = r.multiply(base.add(BigInteger.valueOf(i))).divide(BigInteger.valueOf(i));
      if (r.compareTo(cap) > 0) {
        return true;
      }
    }
    return false;
  }

  private Polynomial binomial(int n, Cancellation cancellation) {
    Term a = terms.get(0);
    Term b = terms.get(1);
    // the coefficients' powers are built term by term: bound them as Q.pow would
    Q.checkPower(a.coefficient(), n);
    Q.checkPower(b.coefficient(), n);
    // the central binomial coefficient has about n bits
    checkTotalBits(n + 1L, n, 1 + bits(a.coefficient()) + bits(b.coefficient()));
    Map<Monomial, RealNumeric> m = new TreeMap<>(Monomial.ORDER);
    BigInteger c = BigInteger.ONE;
    RealNumeric[] bPowers = new RealNumeric[n + 1];
    bPowers[0] = Int.ONE;
    for (int k = 1; k <= n; k++) {
      bPowers[k] = Q.multiply(bPowers[k - 1], b.coefficient());
    }
    RealNumeric aPower = Int.ONE;
    for (int k = n; k >= 0; k--) {
      cancellation.check();
      // the term C(n, k) a^(n-k) b^k, walked from k = n down so a's power grows
      int[] x = new int[variables.size()];
      for (int i = 0; i < x.length; i++) {
        x[i] =
            Math.addExact(
                Math.multiplyExact(a.monomial().exponent(i), n - k),
                Math.multiplyExact(b.monomial().exponent(i), k));
      }
      m.put(new Monomial(x), Q.multiply(Q.multiply(Q.of(c), aPower), bPowers[k]));
      aPower = Q.multiply(aPower, a.coefficient());
      // C(n, k-1) = C(n, k) · k / (n - k + 1)
      c = c.multiply(BigInteger.valueOf(k)).divide(BigInteger.valueOf(n - k + 1));
    }
    return build(variables, m);
  }

  /**
   * Returns the partial derivative in one variable.
   *
   * @param v a kernel
   * @return {@code ∂this/∂v}, as a polynomial in the kernels (a kernel that depends on {@code v} is
   *     not differentiated through)
   */
  public Polynomial derivative(Expr v) {
    int i = variables.indexOf(v);
    if (i < 0) {
      return ZERO;
    }
    Map<Monomial, RealNumeric> m = new TreeMap<>(Monomial.ORDER);
    for (Term t : terms) {
      int k = t.monomial().exponent(i);
      if (k > 0) {
        int[] x = t.monomial().exponents();
        x[i] = k - 1;
        m.put(new Monomial(x), Q.multiply(t.coefficient(), Int.of(k)));
      }
    }
    return build(variables, m);
  }

  // Variable lists.

  /** Returns the sorted union of two sorted variable lists. */
  static List<Expr> union(List<Expr> a, List<Expr> b) {
    if (a.equals(b) || b.isEmpty()) {
      return a;
    }
    if (a.isEmpty()) {
      return b;
    }
    List<Expr> out = new ArrayList<>(a.size() + b.size());
    int i = 0;
    int j = 0;
    while (i < a.size() || j < b.size()) {
      if (j == b.size()) {
        out.add(a.get(i++));
      } else if (i == a.size()) {
        out.add(b.get(j++));
      } else {
        int c = ExprOrder.INSTANCE.compare(a.get(i), b.get(j));
        if (c < 0) {
          out.add(a.get(i++));
        } else if (c > 0) {
          out.add(b.get(j++));
        } else {
          out.add(a.get(i++));
          j++;
        }
      }
    }
    return out;
  }

  /** Re-indexes a monomial over {@code from} to the superset {@code to}. */
  static Monomial lift(Monomial m, List<Expr> from, List<Expr> to) {
    if (from.size() == to.size()) {
      return m;
    }
    int[] x = new int[to.size()];
    int j = 0;
    for (int i = 0; i < from.size(); i++) {
      while (!to.get(j).equals(from.get(i))) {
        j++;
      }
      x[j] = m.exponent(i);
    }
    return new Monomial(x);
  }

  /**
   * Returns the terms re-indexed to a superset of the variables, as a map in ascending order.
   *
   * @param to a sorted superset of the variables
   * @return the coefficients over {@code to}
   */
  TreeMap<Monomial, RealNumeric> over(List<Expr> to) {
    TreeMap<Monomial, RealNumeric> m = new TreeMap<>(Monomial.ORDER);
    for (Term t : terms) {
      m.put(lift(t.monomial(), variables, to), t.coefficient());
    }
    return m;
  }

  /**
   * A bound on the product's terms: the fewer of the pairs of terms and the monomials in the box of
   * its degrees, {@code ∏ (deg_a v + deg_b v + 1)}.
   */
  private long productTerms(Polynomial other, List<Expr> vs) {
    long pairs = (long) terms.size() * other.terms.size();
    long box = 1;
    for (Expr v : vs) {
      long side = (long) Math.max(0, degree(v)) + Math.max(0, other.degree(v)) + 1;
      if (box > pairs / side) {
        return pairs;
      }
      box *= side;
    }
    return Math.min(pairs, box);
  }

  /** The largest coefficient's bit length, numerator and denominator together. */
  private long maxBits() {
    long b = 0;
    for (Term t : terms) {
      b = Math.max(b, bits(t.coefficient()));
    }
    return b;
  }

  private static long bits(RealNumeric c) {
    return (long) Q.num(c).bitLength() + Q.den(c).bitLength();
  }

  /** Throws when the estimate {@code a·b·c} (all non-negative) passes {@link #MAX_TOTAL_BITS}. */
  private static void checkTotalBits(long a, long b, long c) {
    long ab = a == 0 || b <= MAX_TOTAL_BITS / a ? a * b : MAX_TOTAL_BITS + 1;
    if (ab > MAX_TOTAL_BITS || (ab != 0 && c > MAX_TOTAL_BITS / ab)) {
      throw new AlgebraLimitException("the polynomial is too large");
    }
  }

  private static AlgebraLimitException tooLarge() {
    return new AlgebraLimitException("a polynomial of more than 100,000 terms");
  }
}
