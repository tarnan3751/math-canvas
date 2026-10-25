package mathcanvas.engine.simplify;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mathcanvas.engine.algebra.AlgebraLimitException;
import mathcanvas.engine.algebra.Division;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.provider.Gcd;
import mathcanvas.engine.provider.Providers;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Match;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;

/**
 * The rule set {@code rational}: a quotient's common polynomial factors cancel, its numeric content
 * is gathered into one coefficient in lowest terms, and a sum of fractions is written over a common
 * denominator when that makes it no larger. Numerators and denominators are polynomials over ℚ in
 * the algebra's kernels ({@code docs/reference/engine.md}, <b>Polynomials and kernels</b>), so the
 * identities hold wherever the kernels are defined, and cancelling removes removable singularities
 * but never adds a pole. Every gcd comes from the gcd provider ({@code ctx.providers()}),
 * normalised; when no provider answers, or a node is no polynomial in scalar kernels, or it passes
 * the algebra's bounds, the rule declines. No assumption is consulted. A gcd that does not divide
 * its arguments is a provider bug, not a decline: the exact quotients by it throw.
 *
 * <p>The set terminates by a lexicographic measure: the number of fraction terms of sums, which
 * {@code combine-fractions} lowers and the others never raise (a product with a negative power
 * stays a product under the canonical form); the degree of the denominators in the kernels, which
 * {@code cancel-common-factor} lowers; and whether some quotient's numeric content is not in lowest
 * terms, which {@code cancel-common-content} (or {@code cancel-denominator-content} for a lone
 * power) clears in one step without touching the degrees or the primitive parts ({@code
 * docs/reference/engine.md}, <b>Termination</b>).
 */
final class RationalRules {

  private RationalRules() {}

  /** The most total degree of the denominators {@code combine-fractions} brings together. */
  static final int MAX_COMBINED_DEGREE = 200;

  static final RuleSet RULES =
      new RuleSet(
          "rational",
          List.of(
              Rule.computed(
                  "cancel-common-factor",
                  Pattern.parse("n__ * d_^k_")
                      .where("d_", d -> !(d instanceof Num))
                      .where("k_", RationalRules::negativeInteger),
                  RationalRules::cancelFactor),
              Rule.computed(
                  "cancel-common-content",
                  Pattern.parse("n_ * rest__"),
                  RationalRules::cancelContent),
              // A lone negative power is no product, so the same rule for it matches powers.
              Rule.computed(
                  "cancel-denominator-content",
                  Pattern.parse("d_^k_")
                      .where("d_", d -> !(d instanceof Num))
                      .where("k_", RationalRules::negativeInteger),
                  RationalRules::cancelContent),
              Rule.computed(
                  "combine-fractions",
                  Pattern.parse("c___ * d_^k_ + e___ * f_^j_ + rest___")
                      .where("d_", d -> !(d instanceof Num))
                      .where("f_", f -> !(f instanceof Num))
                      .where("k_", RationalRules::negativeInteger)
                      .where("j_", RationalRules::negativeInteger),
                  RationalRules::combine)));

  /** Whether {@code e} is a negative integer in the {@code int} range. */
  private static boolean negativeInteger(Expr e) {
    return e instanceof Num n
        && n.value() instanceof Int i
        && i.signum() < 0
        && i.value().bitLength() < 31;
  }

  /** The exponent of a factor that is a power to a non-zero integer in the {@code int} range. */
  private static int exponent(Expr f) {
    return f instanceof Power p
            && p.exponent() instanceof Num n
            && n.value() instanceof Int i
            && i.signum() != 0
            && i.value().bitLength() < 31
        ? i.value().intValue()
        : 1;
  }

  /** The base of a factor whose exponent {@link #exponent} reads. */
  private static Expr base(Expr f) {
    return f instanceof Power p && exponent(f) != 1 ? p.base() : f;
  }

  /** A polynomial factor of a product: its index, its base as a polynomial and its exponent. */
  private record Factor(int index, Polynomial poly, int m) {}

  /** The non-numeric factors of a product that are powers of polynomials, with their exponents. */
  private static List<Factor> polynomialFactors(List<Expr> factors) {
    List<Factor> out = new ArrayList<>();
    for (int i = 0; i < factors.size(); i++) {
      Expr f = factors.get(i);
      if (f instanceof Num || f instanceof Power p && p.base() instanceof Num) {
        continue;
      }
      Optional<Polynomial> p = polynomial(base(f));
      // A factor that is identically zero though not written as 0, (2b - 1) - (2b - 1), stays a
      // candidate: its gcd with a denominator is that denominator, which then cancels whole.
      if (p.isPresent() && (!p.get().isConstant() || p.get().isZero())) {
        out.add(new Factor(i, p.get(), exponent(f)));
      }
    }
    return out;
  }

  /**
   * Cancels the first common factor of a numerator factor {@code N^m} and a denominator factor
   * {@code D^k} ({@code k < 0}) of the product, in order: the primitive part {@code g} of their gcd
   * from the provider, when it is not constant, divides both, {@code N^m·D^k} becoming {@code
   * g^(m+k)·(N/g)^m·(D/g)^k}. The contents stay where they are ({@code cancel-common-content}); so
   * {@code (x² - 1)/(x - 1)} is {@code x + 1} and {@code (x + 1)^5/(x² - 1)} stays factored as
   * {@code (x + 1)^4/(x - 1)}.
   */
  private static Optional<Expr> cancelFactor(Match match) {
    if (!(match.subject() instanceof Product p)) {
      return Optional.empty();
    }
    Context ctx = match.context();
    List<Expr> factors = p.factors();
    try {
      List<Factor> all = polynomialFactors(factors);
      for (Factor d : all) {
        if (d.m() > 0 || d.poly().isZero()) {
          continue;
        }
        for (Factor n : all) {
          if (n.m() < 0) {
            continue;
          }
          Optional<Polynomial> gcd = gcd(n.poly(), d.poly(), ctx);
          if (gcd.isEmpty()) {
            return Optional.empty();
          }
          Polynomial g = mathcanvas.engine.algebra.Gcd.primitivePart(gcd.get());
          if (g.isConstant()) {
            continue;
          }
          Polynomial nq = Division.exactQuotient(n.poly(), g).orElseThrow();
          Polynomial dq = Division.exactQuotient(d.poly(), g).orElseThrow();
          List<Expr> out = new ArrayList<>();
          for (int i = 0; i < factors.size(); i++) {
            if (i != n.index() && i != d.index()) {
              out.add(factors.get(i));
            }
          }
          out.add(Power.of(nq.toExpr(), Num.of(n.m())));
          out.add(Power.of(dq.toExpr(), Num.of(d.m())));
          out.add(Power.of(g.toExpr(), Num.of(n.m() + d.m())));
          return Optional.of(Product.of(out));
        }
      }
      return Optional.empty();
    } catch (AlgebraLimitException | ArithmeticException limit) {
      return Optional.empty();
    }
  }

  /**
   * Gathers a quotient's numeric content into one coefficient in lowest terms, as {@code cancel}
   * does once the primitive parts are coprime. With the coefficient {@code P/Q}, the numerator's
   * content {@code cN = P·Π content(Nᵢ)^mᵢ = a/b} and the denominator's {@code cD = Q·Π
   * content(Dⱼ)^|kⱼ| = c/d} in lowest terms, it fires when {@code b ≠ 1}, {@code d ≠ 1} or {@code
   * gcd(a, c) ≠ 1}: every polynomial factor becomes primitive (its own sign kept, since a sign
   * normal form is out of scope) and the coefficient is {@code cN/cD}. So {@code 2/(2x + 2)} is
   * {@code 1/(x + 1)}, {@code (x/2 + 1)/3} is {@code (x + 2)/6}, and {@code (2x + 2)/(3x - 3)} and
   * {@code 1/(2x + 2)} stay. Only a quotient takes part, one with a denominator: a product with a
   * negative power of a polynomial or a coefficient that is a fraction ({@code
   * cancel-common-content}; {@code 3(x/2 + 1)} has no denominator and stays), or a negative power
   * on its own ({@code cancel-denominator-content}: {@code 1/(x/2 + 1)} is {@code 2/(x + 2)}). The
   * rewriter visits a product's factors first, so a negative power inside a product is made
   * primitive by the second before the first sees the product: {@code (x - 2)²/(2(x - 1)(x +
   * 1/2))}, whose whole content is already 1, becomes {@code (x - 2)²/((x - 1)(2x + 1))}, no
   * larger.
   */
  private static Optional<Expr> cancelContent(Match match) {
    List<Expr> factors =
        switch (match.subject()) {
          case Product p -> p.factors();
          case Power w -> List.of(w);
          default -> List.of();
        };
    if (factors.isEmpty()) {
      return Optional.empty();
    }
    Expr first = factors.getFirst();
    RealNumeric coefficient =
        first instanceof Num n && (n.value() instanceof Int || n.value() instanceof Rational)
            ? (RealNumeric) n.value()
            : Int.ONE;
    boolean quotient =
        coefficient instanceof Rational
            || factors.stream().anyMatch(f -> exponent(f) < 0 && !(base(f) instanceof Num));
    if (!quotient) {
      return Optional.empty();
    }
    try {
      List<Factor> all = polynomialFactors(factors);
      if (all.isEmpty() || all.stream().anyMatch(f -> f.poly().isZero())) {
        return Optional.empty();
      }
      RealNumeric numeratorContent = Int.of(numerator(coefficient).abs());
      RealNumeric denominatorContent = Int.of(denominator(coefficient));
      List<Polynomial> primitive = new ArrayList<>();
      for (Factor f : all) {
        RealNumeric c = mathcanvas.engine.algebra.Gcd.rationalContent(f.poly());
        primitive.add(f.poly().multiply(Rational.of(denominator(c), numerator(c))));
        RealNumeric power = pow(c, Math.abs(f.m()));
        if (power == null) {
          return Optional.empty();
        }
        if (f.m() > 0) {
          numeratorContent = multiply(numeratorContent, power);
        } else {
          denominatorContent = multiply(denominatorContent, power);
        }
      }
      BigInteger a = numerator(numeratorContent);
      BigInteger b = denominator(numeratorContent);
      BigInteger c = numerator(denominatorContent);
      BigInteger d = denominator(denominatorContent);
      if (b.equals(BigInteger.ONE) && d.equals(BigInteger.ONE) && a.gcd(c).equals(BigInteger.ONE)) {
        return Optional.empty();
      }
      List<Expr> out = new ArrayList<>();
      int k = 0;
      boolean[] polynomialAt = new boolean[factors.size()];
      for (Factor f : all) {
        polynomialAt[f.index()] = true;
      }
      BigInteger sign = BigInteger.valueOf(coefficient.signum());
      out.add(Num.of(Rational.of(sign.multiply(a).multiply(d), b.multiply(c))));
      for (int i = 0; i < factors.size(); i++) {
        Expr f = factors.get(i);
        if (polynomialAt[i]) {
          Factor pf = all.get(k);
          out.add(Power.of(primitive.get(k).toExpr(), Num.of(pf.m())));
          k++;
        } else if (!(i == 0
            && f instanceof Num n
            && n.value() instanceof RealNumeric r
            && r.isExact())) {
          out.add(f);
        }
      }
      return Optional.of(Product.of(out));
    } catch (AlgebraLimitException | ArithmeticException limit) {
      return Optional.empty();
    }
  }

  /**
   * Writes the sum over a common denominator, the numerator expanded, when the result is no larger
   * than the sum by {@link #size}. The common denominator is the lcm of the terms' denominators
   * expanded, with the primitive part of its gcd with the numerator cancelled; when the distinct
   * denominator bases are pairwise coprime (their gcds' primitive parts are 1), it is also built as
   * the product of the bases to their highest powers, kept factored, with a base that divides the
   * numerator cancelled whole and a part of one left to {@code cancel-common-factor}, and the
   * smaller of the two results by {@link #size} is taken, the factored one on a tie. Every fraction
   * term is combined, or none; a sum whose denominators' total degree passes {@link
   * #MAX_COMBINED_DEGREE}, or whose terms' denominator sizes multiply past {@link
   * Polynomial#MAX_TERMS}, is declined before anything is built.
   */
  private static Optional<Expr> combine(Match match) {
    if (!(match.subject() instanceof Sum s)) {
      return Optional.empty();
    }
    Context ctx = match.context();
    try {
      List<Expr> bases = new ArrayList<>();
      List<Polynomial> basePolys = new ArrayList<>();
      List<Integer> highest = new ArrayList<>();
      List<List<Expr>> numerators = new ArrayList<>();
      List<int[]> powers = new ArrayList<>();
      long degree = 0;
      long size = 1;
      for (Expr t : s.terms()) {
        List<Expr> fs = t instanceof Product p ? p.factors() : List.of(t);
        List<Expr> num = new ArrayList<>();
        int[] termPowers = new int[0];
        long termSize = 1;
        for (Expr f : fs) {
          if (f instanceof Power w && negativeInteger(w.exponent()) && !(w.base() instanceof Num)) {
            int k = -((Int) ((Num) w.exponent()).value()).value().intValue();
            int j = bases.indexOf(w.base());
            if (j < 0) {
              Optional<Polynomial> b = polynomial(w.base());
              if (b.isEmpty()) {
                return Optional.empty();
              }
              bases.add(w.base());
              basePolys.add(b.get());
              highest.add(0);
              j = bases.size() - 1;
            }
            highest.set(j, Math.max(highest.get(j), k));
            int[] grown = java.util.Arrays.copyOf(termPowers, bases.size());
            grown[j] += k;
            termPowers = grown;
            degree += (long) k * basePolys.get(j).totalDegree();
            termSize = saturatingTimes(termSize, pow(basePolys.get(j).terms().size(), k));
          } else {
            num.add(f);
          }
        }
        numerators.add(num);
        powers.add(termPowers);
        size = saturatingTimes(size, termSize);
      }
      if (degree > MAX_COMBINED_DEGREE || size > Polynomial.MAX_TERMS) {
        return Optional.empty();
      }
      boolean coprime = true;
      for (int i = 0; i < basePolys.size() && coprime; i++) {
        for (int j = i + 1; j < basePolys.size() && coprime; j++) {
          Optional<Polynomial> g = gcd(basePolys.get(i), basePolys.get(j), ctx);
          if (g.isEmpty()) {
            return Optional.empty();
          }
          coprime = mathcanvas.engine.algebra.Gcd.primitivePart(g.get()).isConstant();
        }
      }
      // Both forms when the bases are coprime: the factored one can be the larger once the
      // expanded lcm canonicalises, as (√x - 1)(√x + 1) does to x - 1.
      Optional<Expr> expanded = overLcm(basePolys, numerators, powers, ctx);
      Optional<Expr> best = expanded;
      if (coprime) {
        Optional<Expr> factored = overFactored(bases, basePolys, highest, numerators, powers, ctx);
        if (factored.isPresent()
            && (expanded.isEmpty() || size(factored.get()) <= size(expanded.get()))) {
          best = factored;
        }
      }
      return best.filter(r -> size(r) <= size(s));
    } catch (AlgebraLimitException | ArithmeticException limit) {
      return Optional.empty();
    }
  }

  /** The sum over the product of its pairwise coprime denominator bases. */
  private static Optional<Expr> overFactored(
      List<Expr> bases,
      List<Polynomial> basePolys,
      List<Integer> highest,
      List<List<Expr>> numerators,
      List<int[]> powers,
      Context ctx) {
    Polynomial numerator = Polynomial.ZERO;
    for (int i = 0; i < numerators.size(); i++) {
      Optional<Polynomial> n = polynomial(product(numerators.get(i)));
      if (n.isEmpty()) {
        return Optional.empty();
      }
      Polynomial term = n.get();
      int[] p = powers.get(i);
      for (int j = 0; j < basePolys.size(); j++) {
        int have = j < p.length ? p[j] : 0;
        term = term.multiply(basePolys.get(j).pow(highest.get(j) - have));
      }
      numerator = numerator.add(term);
    }
    List<Integer> left = new ArrayList<>(highest);
    for (int j = 0; j < basePolys.size(); j++) {
      while (left.get(j) > 0 && !numerator.isZero()) {
        Optional<Polynomial> q = Division.exactQuotient(numerator, basePolys.get(j));
        if (q.isEmpty()) {
          break;
        }
        numerator = q.get();
        left.set(j, left.get(j) - 1);
      }
    }
    List<Expr> out = new ArrayList<>();
    out.add(numerator.toExpr());
    for (int j = 0; j < bases.size(); j++) {
      if (left.get(j) > 0) {
        out.add(Power.of(bases.get(j), Num.of(-left.get(j))));
      }
    }
    return Optional.of(Canonical.of(product(out), ctx.precision()));
  }

  /** The sum over the expanded lcm of its terms' denominators. */
  private static Optional<Expr> overLcm(
      List<Polynomial> basePolys, List<List<Expr>> numerators, List<int[]> powers, Context ctx) {
    List<Polynomial> dens = new ArrayList<>();
    for (int[] p : powers) {
      Polynomial den = Polynomial.ONE;
      for (int j = 0; j < p.length; j++) {
        if (p[j] > 0) {
          den = den.multiply(basePolys.get(j).pow(p[j]));
        }
      }
      dens.add(den);
    }
    Polynomial lcm = Polynomial.ONE;
    for (Polynomial d : dens) {
      if (d.isConstant()) {
        continue;
      }
      if (lcm.isConstant()) {
        lcm = d;
        continue;
      }
      Optional<Polynomial> g = gcd(lcm, d, ctx);
      if (g.isEmpty()) {
        return Optional.empty();
      }
      lcm = Division.exactQuotient(lcm, g.get()).orElseThrow().multiply(d);
    }
    Polynomial numerator = Polynomial.ZERO;
    for (int i = 0; i < numerators.size(); i++) {
      Optional<Polynomial> n = polynomial(product(numerators.get(i)));
      if (n.isEmpty()) {
        return Optional.empty();
      }
      numerator =
          numerator.add(n.get().multiply(Division.exactQuotient(lcm, dens.get(i)).orElseThrow()));
    }
    if (!numerator.isZero()) {
      Optional<Polynomial> g = gcd(numerator, lcm, ctx);
      if (g.isEmpty()) {
        return Optional.empty();
      }
      Polynomial pg = mathcanvas.engine.algebra.Gcd.primitivePart(g.get());
      if (!pg.isConstant()) {
        numerator = Division.exactQuotient(numerator, pg).orElseThrow();
        lcm = Division.exactQuotient(lcm, pg).orElseThrow();
      }
    }
    return Optional.of(
        Canonical.of(
            Product.of(numerator.toExpr(), Power.of(lcm.toExpr(), Num.MINUS_ONE)),
            ctx.precision()));
  }

  /**
   * The size of an expression: its nodes, a number counting its digits (numerator and denominator,
   * or both parts) rather than one, so that combining fractions does not trade a few nodes for
   * coefficients of many digits.
   */
  static long size(Expr e) {
    if (e instanceof Num n) {
      return digits(n.value());
    }
    long total = 1;
    for (Expr o : Operands.of(e)) {
      total += size(o);
    }
    return total;
  }

  private static long digits(Numeric v) {
    return switch (v) {
      case Int i -> i.value().abs().toString().length();
      case Rational q -> q.num().abs().toString().length() + q.den().toString().length();
      case Real r -> r.value().unscaledValue().abs().toString().length();
      case mathcanvas.engine.number.Complex c -> digits(c.re()) + digits(c.im());
    };
  }

  /** The provider's gcd, normalised, or empty when no provider answers. */
  private static Optional<Polynomial> gcd(Polynomial a, Polynomial b, Context ctx) {
    return ctx.providers().attempt(new Gcd(a, b), ctx, Steps.NONE).map(Providers.Outcome::value);
  }

  /** {@code e} as a polynomial in its kernels, or empty when a node is refused as a kernel. */
  private static Optional<Polynomial> polynomial(Expr e) {
    try {
      return Optional.of(Polynomial.of(e));
    } catch (IllegalArgumentException refused) {
      return Optional.empty();
    }
  }

  private static Expr product(List<Expr> factors) {
    return switch (factors.size()) {
      case 0 -> Num.ONE;
      case 1 -> factors.getFirst();
      default -> Product.of(factors);
    };
  }

  private static long pow(long base, int n) {
    long r = 1;
    for (int i = 0; i < n && r != Long.MAX_VALUE && base > 1; i++) {
      r = saturatingTimes(r, base);
    }
    return r;
  }

  private static long saturatingTimes(long a, long b) {
    long r = a * b;
    return a != 0 && (r / a != b || r < 0) ? Long.MAX_VALUE : r;
  }

  /** {@code c^n}, or null when it would pass the canonical form's folding bound. */
  private static RealNumeric pow(RealNumeric c, int n) {
    BigInteger num = numerator(c);
    BigInteger den = denominator(c);
    if ((long) Math.max(num.bitLength(), den.bitLength()) * n > Canonical.MAX_FOLD_BITS) {
      return null;
    }
    return Rational.of(num.pow(n), den.pow(n));
  }

  private static RealNumeric multiply(RealNumeric a, RealNumeric b) {
    return Rational.of(
        numerator(a).multiply(numerator(b)), denominator(a).multiply(denominator(b)));
  }

  private static BigInteger numerator(RealNumeric q) {
    return q instanceof Rational r ? r.num() : ((Int) q).value();
  }

  private static BigInteger denominator(RealNumeric q) {
    return q instanceof Rational r ? r.den() : BigInteger.ONE;
  }
}
