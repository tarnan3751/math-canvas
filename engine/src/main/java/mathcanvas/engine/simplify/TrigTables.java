package mathcanvas.engine.simplify;

import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BinaryOperator;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.assume.Facts;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprOrder;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;

/**
 * What the trigonometric rules compute: the exact values at multiples of {@code π/12} and the
 * reduction of an angle to {@code [0, π/2]}, the inverse functions' values, the sign a parity rule
 * reads off an argument and the negation it writes, the range test of {@code asin(sin u)}, and the
 * normal form of a product's circular factors.
 */
final class TrigTables {

  private TrigTables() {}

  /** The circular functions, in the order of their tables. */
  static final List<String> CIRCULAR = List.of("sin", "cos", "tan", "cot", "sec", "csc");

  /**
   * The values at {@code kπ/12} for {@code k = 0 … 6}, canonical; null at a pole. Every entry is in
   * the radical set's fixed form.
   */
  private static final Map<String, List<Expr>> TABLE =
      Map.of(
          "sin",
          values(
              "0",
              "(sqrt(6) - sqrt(2))/4",
              "1/2",
              "sqrt(2)/2",
              "sqrt(3)/2",
              "(sqrt(6) + sqrt(2))/4",
              "1"),
          "cos",
          values(
              "1",
              "(sqrt(6) + sqrt(2))/4",
              "sqrt(3)/2",
              "sqrt(2)/2",
              "1/2",
              "(sqrt(6) - sqrt(2))/4",
              "0"),
          "tan",
          values("0", "2 - sqrt(3)", "sqrt(3)/3", "1", "sqrt(3)", "2 + sqrt(3)", null),
          "cot",
          values(null, "2 + sqrt(3)", "sqrt(3)", "1", "sqrt(3)/3", "2 - sqrt(3)", "0"),
          "sec",
          values(
              "1", "sqrt(6) - sqrt(2)", "2*sqrt(3)/3", "sqrt(2)", "2", "sqrt(6) + sqrt(2)", null),
          "csc",
          values(
              null, "sqrt(6) + sqrt(2)", "2", "sqrt(2)", "2*sqrt(3)/3", "sqrt(6) - sqrt(2)", "1"));

  /**
   * The inverse functions' values: for each, the canonical arguments {@code v ≥ 0} it has an exact
   * value at, to that value's multiple of {@code π/12}.
   */
  private static final Map<String, Map<Expr, Integer>> INVERSE = inverseTables();

  /** A rational just below {@code π/2}: 1.57079632679489661923, so {@code π/2} exceeds it. */
  private static final RealNumeric HALF_PI_BELOW =
      Rational.of(new BigInteger("157079632679489661923"), BigInteger.TEN.pow(20));

  private static List<Expr> values(String... sources) {
    List<Expr> out = new ArrayList<>();
    for (String s : sources) {
      out.add(s == null ? null : Canonical.of(Rules.template(s)));
    }
    return out;
  }

  private static Map<String, Map<Expr, Integer>> inverseTables() {
    Map<String, Map<Expr, Integer>> out = new TreeMap<>();
    // asin and acsc from the sine and cosecant rows, acos and asec from the cosine and secant rows
    // (π/2 - asin v and acos(1/v)), atan and acot from the tangent and cotangent rows. acot(0) is
    // π/2, as acot(x) = atan(1/x) gives on the right.
    out.put("asin", inverse("sin", 0, 6));
    out.put("acos", inverse("cos", 0, 6));
    out.put("atan", inverse("tan", 0, 5));
    out.put("acot", inverse("cot", 1, 6));
    out.put("asec", inverse("sec", 0, 5));
    out.put("acsc", inverse("csc", 1, 6));
    return out;
  }

  private static Map<Expr, Integer> inverse(String f, int from, int to) {
    Map<Expr, Integer> m = new TreeMap<>(ExprOrder.INSTANCE);
    for (int k = from; k <= to; k++) {
      m.put(key(TABLE.get(f).get(k)), k);
    }
    return m;
  }

  /**
   * The form a value is looked up in: canonical, with a rational coefficient of a sum distributed
   * over its terms, so {@code (√6 - √2)/4}, {@code √6/4 - √2/4} and, negated, {@code (√2 - √6)/4}
   * and {@code -(√6 - √2)/4} all meet one entry.
   */
  private static Expr key(Expr v) {
    Expr c = Canonical.of(v);
    if (c instanceof Product p
        && p.factors().size() == 2
        && p.factors().getFirst() instanceof Num n
        && p.factors().get(1) instanceof Sum s) {
      List<Expr> terms = new ArrayList<>();
      for (Expr t : s.terms()) {
        terms.add(Product.of(n, t));
      }
      return Canonical.of(Sum.of(terms));
    }
    return c;
  }

  /**
   * The coefficient {@code q} of an angle {@code qπ}, or of {@code 0}.
   *
   * @param x an argument
   * @return {@code q}, an exact rational, when {@code x} is {@code 0}, {@code π} or {@code q·π};
   *     else null
   */
  static RealNumeric piCoefficient(Expr x) {
    if (x instanceof Num n && n.value().isExact() && n.value().isZero()) {
      return Int.ZERO;
    }
    if (x.equals(Constant.PI)) {
      return Int.ONE;
    }
    if (x instanceof Product p
        && p.factors().size() == 2
        && p.factors().get(1).equals(Constant.PI)
        && Rules.rational(p.factors().getFirst()) instanceof RealNumeric q) {
      return q;
    }
    return null;
  }

  /**
   * The exact value of {@code f(qπ)}: the angle reduced by the period and the symmetries of {@code
   * f} to {@code rπ} with {@code r} in {@code [0, 1/2]}, then the table when {@code 12r} is an
   * integer, else {@code ±f(rπ)}.
   *
   * @param f a circular function
   * @param q the angle's coefficient of {@code π}
   * @return the value, {@code undefined} at a pole, raw
   */
  static Expr specialValue(String f, RealNumeric q) {
    BigInteger n = numerator(q);
    BigInteger d = denominator(q);
    BigInteger twoD = d.shiftLeft(1);
    BigInteger t;
    boolean negative = false;
    switch (f) {
      case "sin", "csc" -> {
        t = n.mod(twoD);
        if (t.compareTo(d) >= 0) {
          t = t.subtract(d);
          negative = true;
        }
        if (t.shiftLeft(1).compareTo(d) > 0) {
          t = d.subtract(t);
        }
      }
      case "cos", "sec" -> {
        t = n.mod(twoD);
        if (t.compareTo(d) > 0) {
          t = twoD.subtract(t);
        }
        if (t.shiftLeft(1).compareTo(d) > 0) {
          t = d.subtract(t);
          negative = true;
        }
      }
      default -> {
        t = n.mod(d);
        if (t.shiftLeft(1).compareTo(d) > 0) {
          t = d.subtract(t);
          negative = true;
        }
      }
    }
    Expr value;
    BigInteger[] twelfths = t.multiply(BigInteger.valueOf(12)).divideAndRemainder(d);
    if (twelfths[1].signum() == 0) {
      value = TABLE.get(f).get(twelfths[0].intValueExact());
      if (value == null) {
        return Constant.UNDEFINED;
      }
    } else {
      value = Apply.of(Symbol.of(f), Product.of(Num.of(Rational.of(t, d)), Constant.PI));
    }
    return negative ? Product.of(Num.MINUS_ONE, value) : value;
  }

  /**
   * The exact value of an inverse circular function at {@code x}, when {@code x} or {@code -x} is
   * in its table, however a rational coefficient of a sum is written: the odd ones negate the angle
   * of {@code -x}, {@code acos} and {@code asec} take {@code π} minus it. A negation that is 0 is
   * not looked up.
   *
   * @param f an inverse circular function
   * @param x the canonical argument
   * @return the angle, raw, or empty
   */
  static Optional<Expr> inverseValue(String f, Expr x) {
    Map<Expr, Integer> table = INVERSE.get(f);
    Integer k = table.get(key(x));
    if (k != null) {
      return Optional.of(twelfthsOfPi(k));
    }
    Expr negated = key(negate(x));
    // A zero that is not written as 0 (x - (x + 1) + 1) negates to 0: the odd functions' rule
    // does not hold for acot at 0 (acot(0) is π/2), so its negation is no evidence.
    if (negated.equals(Num.ZERO)) {
      return Optional.empty();
    }
    k = table.get(negated);
    if (k == null) {
      return Optional.empty();
    }
    return Optional.of(
        f.equals("acos") || f.equals("asec")
            ? Sum.of(Constant.PI, Product.of(Num.MINUS_ONE, twelfthsOfPi(k)))
            : Product.of(Num.MINUS_ONE, twelfthsOfPi(k)));
  }

  private static Expr twelfthsOfPi(int k) {
    return Product.of(Num.of(Rational.of(k, 12)), Constant.PI);
  }

  /**
   * {@code q} reduced modulo the period ({@code 2} for {@code sin}, {@code cos}, {@code sec} and
   * {@code csc}, {@code 1} for {@code tan} and {@code cot}) into {@code (-1, 1]} or {@code (-1/2,
   * 1/2]}.
   *
   * @param f a circular function
   * @param q an exact rational
   * @return the reduced value
   */
  static RealNumeric reduceShift(String f, RealNumeric q) {
    BigInteger n = numerator(q);
    BigInteger d = denominator(q);
    BigInteger period = f.equals("tan") || f.equals("cot") ? d : d.shiftLeft(1);
    BigInteger t = n.mod(period);
    if (t.shiftLeft(1).compareTo(period) > 0) {
      t = t.subtract(period);
    }
    return Rational.of(t, d);
  }

  private static BigInteger numerator(RealNumeric q) {
    return q instanceof Rational r ? r.num() : ((Int) q).value();
  }

  private static BigInteger denominator(RealNumeric q) {
    return q instanceof Rational r ? r.den() : BigInteger.ONE;
  }

  /**
   * Whether an argument looks negative: a negative real number, a product with a negative real
   * coefficient, or a sum whose display-leading term does. The display-leading term is the first
   * term that is not a number in descending total degree, ties in canonical order, the order the
   * printer shows ({@code mathcanvas.engine.print.Display}, which this mirrors and must change
   * with). The negation of an argument that looks negative never does.
   *
   * @param u a canonical argument
   * @return whether it looks negative
   */
  static boolean looksNegative(Expr u) {
    return switch (u) {
      case Num n -> n.value() instanceof RealNumeric r && r.signum() < 0;
      case Product p -> p.factors().getFirst() instanceof Num n && looksNegative(n);
      case Sum s -> {
        Expr leading = null;
        RealNumeric degree = null;
        for (Expr t : s.terms()) {
          if (t instanceof Num) {
            continue;
          }
          RealNumeric d = degree(t);
          if (leading == null || d.compareValue(degree) > 0) {
            leading = t;
            degree = d;
          }
        }
        yield leading != null && looksNegative(leading);
      }
      default -> false;
    };
  }

  /** The total degree of a term: the sum of the numeric exponents of its symbols. */
  private static RealNumeric degree(Expr t) {
    if (t instanceof Product p) {
      Numeric sum = Int.ZERO;
      for (Expr f : p.factors()) {
        sum = sum.add(degree(f), MathContext.DECIMAL128);
      }
      return (RealNumeric) sum;
    }
    if (t instanceof Symbol) {
      return Int.ONE;
    }
    if (t instanceof Power p
        && p.base() instanceof Symbol
        && p.exponent() instanceof Num n
        && n.value() instanceof RealNumeric r) {
      return r;
    }
    return Int.ZERO;
  }

  /**
   * Returns {@code -u}, raw, with the sign taken into a number, a product's coefficient or every
   * term of a sum: the canonical form does not distribute {@code -1} over a sum, so {@code -1·u} of
   * a sum would still look negative.
   *
   * @param u a canonical expression
   * @return its negation
   */
  static Expr negate(Expr u) {
    return switch (u) {
      case Num n -> Num.of(n.value().negate());
      case Product p when p.factors().getFirst() instanceof Num n -> {
        List<Expr> factors = new ArrayList<>(p.factors());
        factors.set(0, Num.of(n.value().negate()));
        yield Product.of(factors);
      }
      case Sum s -> Sum.of(s.terms().stream().map(TrigTables::negate).toList());
      default -> Product.of(Num.MINUS_ONE, u);
    };
  }

  /**
   * Whether {@code u} is known to lie in {@code [-π/2, π/2]}, or in {@code (-π/2, π/2)} when {@code
   * open}: a real number, a rational multiple of {@code π}, or a symbol whose bounds lie within. A
   * bound within {@code 10^-20} of {@code π/2} answers false, which is sound.
   *
   * @param u a canonical expression
   * @param a the assumptions
   * @param open whether the ends are excluded
   * @return whether it is known
   */
  static boolean withinHalfPi(Expr u, Assumptions a, boolean open) {
    RealNumeric q = piCoefficient(u);
    if (q != null) {
      int c = q.abs().compareValue(Rational.of(1, 2));
      return open ? c < 0 : c <= 0;
    }
    if (u instanceof Num n && n.value() instanceof RealNumeric r) {
      return r.abs().compareValue(HALF_PI_BELOW) <= 0;
    }
    if (u instanceof Symbol s) {
      Facts f = a.facts(s);
      return f.lower() != null
          && f.upper() != null
          && f.lower().value().negate().compareValue(HALF_PI_BELOW) <= 0
          && f.upper().value().compareValue(HALF_PI_BELOW) <= 0;
    }
    return false;
  }

  // The sum rules' pairs.

  /**
   * Replaces, in a sum, every pair of an anchor term {@code d·f(u)²} and its partner term {@code
   * partner(d, u)} by {@code result(d, u)}, each term in at most one pair, anchors in canonical
   * order. {@code d} and {@code u} must commute.
   *
   * @param subject a canonical sum
   * @param f the squared function of the anchor
   * @param partner the partner term of {@code d} and {@code u}
   * @param result the term replacing the pair
   * @return the sum with the pairs replaced, raw, or empty when there is none
   */
  static Optional<Expr> collapsePairs(
      Expr subject, String f, BinaryOperator<Expr> partner, BinaryOperator<Expr> result) {
    if (!(subject instanceof Sum s)) {
      return Optional.empty();
    }
    List<Expr> terms = s.terms();
    Map<Expr, Integer> index = new TreeMap<>(ExprOrder.INSTANCE);
    for (int i = 0; i < terms.size(); i++) {
      index.put(terms.get(i), i);
    }
    boolean[] used = new boolean[terms.size()];
    List<Expr> out = new ArrayList<>();
    for (int i = 0; i < terms.size(); i++) {
      if (used[i]) {
        continue;
      }
      for (Expr[] du : anchors(terms.get(i), f)) {
        Integer j = index.get(Canonical.of(partner.apply(du[0], du[1])));
        if (j != null && j != i && !used[j]) {
          used[i] = true;
          used[j] = true;
          out.add(result.apply(du[0], du[1]));
          break;
        }
      }
    }
    if (out.isEmpty()) {
      return Optional.empty();
    }
    for (int i = 0; i < terms.size(); i++) {
      if (!used[i]) {
        out.add(terms.get(i));
      }
    }
    return Optional.of(out.size() == 1 ? out.getFirst() : Sum.of(out));
  }

  /** The ways to read a term as {@code d·f(u)²}, as pairs {@code {d, u}} of commuting values. */
  private static List<Expr[]> anchors(Expr term, String f) {
    List<Expr> factors = term instanceof Product p ? p.factors() : List.of(term);
    List<Expr[]> out = new ArrayList<>();
    for (int k = 0; k < factors.size(); k++) {
      if (factors.get(k) instanceof Power p
          && p.exponent().equals(Num.of(2))
          && p.base() instanceof Apply a
          && a.head().name().equals(f)
          && a.args().size() == 1) {
        List<Expr> rest = new ArrayList<>(factors);
        rest.remove(k);
        Expr d =
            switch (rest.size()) {
              case 0 -> Num.ONE;
              case 1 -> rest.getFirst();
              default -> Product.of(rest);
            };
        Expr u = a.args().getFirst();
        if (Canonical.commutes(d) && Canonical.commutes(u)) {
          out.add(new Expr[] {d, u});
        }
      }
    }
    return out;
  }

  // The normal form of a product's circular factors.

  /** A factor's contribution to the net exponents of sine and cosine, per unit exponent. */
  private static final Map<String, int[]> SINE_COSINE =
      Map.of(
          "sin", new int[] {1, 0},
          "cos", new int[] {0, 1},
          "tan", new int[] {1, -1},
          "cot", new int[] {-1, 1},
          "sec", new int[] {0, -1},
          "csc", new int[] {-1, 0});

  /**
   * The circular factors of a product, grouped by argument, written in the normal form of their net
   * exponents {@code A} of {@code sin} and {@code B} of {@code cos}: {@code sin^A cos^B} when
   * neither is negative, {@code tan} or {@code cot} for each pair of opposite signs, then the
   * leftover as {@code sin}, {@code cos}, {@code sec} or {@code csc}; {@code csc^-A sec^-B} when
   * both are negative. A group is rewritten only when that makes it strictly smaller in node count,
   * or as small with fewer negative exponents. Only integer exponents count; other factors stay.
   *
   * @param subject a canonical product, power or application
   * @return the product with its groups rewritten, raw, or empty when none is
   */
  static Optional<Expr> combineFactors(Expr subject) {
    List<Expr> factors = subject instanceof Product p ? p.factors() : List.of(subject);
    Map<Expr, List<Expr>> groups = new TreeMap<>(ExprOrder.INSTANCE);
    List<Expr> others = new ArrayList<>();
    for (Expr f : factors) {
      Expr u = circularArgument(f);
      if (u == null) {
        others.add(f);
      } else {
        groups.computeIfAbsent(u, k -> new ArrayList<>()).add(f);
      }
    }
    boolean changed = false;
    List<Expr> out = new ArrayList<>(others);
    for (Map.Entry<Expr, List<Expr>> g : groups.entrySet()) {
      long a = 0;
      long b = 0;
      int size = 0;
      int negatives = 0;
      for (Expr f : g.getValue()) {
        long e = exponent(f);
        int[] w = SINE_COSINE.get(head(f));
        a += w[0] * e;
        b += w[1] * e;
        size += nodes(f);
        negatives += e < 0 ? 1 : 0;
      }
      List<Expr> normal = normalForm(g.getKey(), a, b);
      int normalSize = 0;
      for (Expr f : normal) {
        normalSize += nodes(f);
      }
      if (normalSize < size || normalSize == size && negatives > 0) {
        out.addAll(normal);
        changed = true;
      } else {
        out.addAll(g.getValue());
      }
    }
    if (!changed) {
      return Optional.empty();
    }
    return Optional.of(
        switch (out.size()) {
          case 0 -> Num.ONE;
          case 1 -> out.getFirst();
          default -> Product.of(out);
        });
  }

  /** The argument of a circular function or of its power to an integer exponent, else null. */
  private static Expr circularArgument(Expr f) {
    Expr base = f;
    if (f instanceof Power p) {
      if (!(p.exponent() instanceof Num n
          && n.value() instanceof Int i
          && i.value().bitLength() < 31)) {
        return null;
      }
      base = p.base();
    }
    return base instanceof Apply a && a.args().size() == 1 && CIRCULAR.contains(a.head().name())
        ? a.args().getFirst()
        : null;
  }

  private static long exponent(Expr f) {
    return f instanceof Power p ? ((Int) ((Num) p.exponent()).value()).value().longValue() : 1;
  }

  private static String head(Expr f) {
    Expr base = f instanceof Power p ? p.base() : f;
    return ((Apply) base).head().name();
  }

  private static List<Expr> normalForm(Expr u, long a, long b) {
    List<Expr> out = new ArrayList<>();
    if (a >= 0 && b >= 0) {
      add(out, "sin", u, a);
      add(out, "cos", u, b);
    } else if (a > 0) {
      long k = Math.min(a, -b);
      add(out, "tan", u, k);
      add(out, "sin", u, a - k);
      add(out, "sec", u, -b - k);
    } else if (b > 0) {
      long k = Math.min(-a, b);
      add(out, "cot", u, k);
      add(out, "cos", u, b - k);
      add(out, "csc", u, -a - k);
    } else {
      add(out, "csc", u, -a);
      add(out, "sec", u, -b);
    }
    return out;
  }

  private static void add(List<Expr> out, String f, Expr u, long n) {
    if (n == 0) {
      return;
    }
    Expr call = Apply.of(Symbol.of(f), u);
    out.add(n == 1 ? call : Power.of(call, Num.of(n)));
  }

  /** The number of nodes of {@code e}: every node counts one. */
  static int nodes(Expr e) {
    int n = 1;
    for (Expr o : Operands.of(e)) {
      n += nodes(o);
    }
    return n;
  }
}
