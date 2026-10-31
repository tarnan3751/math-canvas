package mathcanvas.engine.calculus;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Context;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

/**
 * Integrands that the rule set {@code integrate} must integrate whole, by family, each with an
 * interval of the reals where it is defined and smooth: polynomials; polynomials times an
 * exponential, sine or cosine of a linear argument; the substitution shapes {@code c·g′·h(g)}; the
 * by-parts shapes; powers and products of the circular functions; reciprocals and reciprocal square
 * roots of quadratics of known sign; and sums of up to three of these.
 */
final class IntegrateArbitraries {

  private IntegrateArbitraries() {}

  static final Symbol X = Symbol.of("x");

  /** The families, the first five of which the symbolic round trip is asserted on. */
  enum Family {
    POLYNOMIAL,
    POLYNOMIAL_EXP,
    POLYNOMIAL_TRIG,
    SUBSTITUTION,
    BY_PARTS,
    TRIG,
    QUADRATIC,
    SUM;

    boolean symbolic() {
      return this == POLYNOMIAL
          || this == POLYNOMIAL_EXP
          || this == POLYNOMIAL_TRIG
          || this == SUBSTITUTION
          || this == BY_PARTS;
    }
  }

  /**
   * An integrand of a family, with the interval {@code (lo, hi)} where it is defined and smooth.
   */
  record Case(Expr f, Family family, double lo, double hi) {
    @Override
    public String toString() {
      return family + " " + f + " on (" + lo + ", " + hi + ")";
    }
  }

  private static Expr parse(String text) {
    return Canonical.of(Parser.parseExpression(text, Names.NONE));
  }

  private static final String[] COEFFICIENTS = {"1", "2", "-1", "3", "1/2", "-3", "5/3", "-1/4"};
  private static final String[] SLOPES = {"1", "2", "-1", "3", "1/2", "-2"};
  private static final String[] SHIFTS = {"0", "1", "-1", "1/3"};

  private static String pick(String[] a, Random r) {
    return a[r.nextInt(a.length)];
  }

  private static String linear(Random r) {
    return "(" + pick(SLOPES, r) + "x + " + pick(SHIFTS, r) + ")";
  }

  private static String polynomial(Random r, int min, int max) {
    int degree = min + r.nextInt(max - min + 1);
    StringBuilder s = new StringBuilder("(" + pick(COEFFICIENTS, r) + "x^" + degree);
    for (int k = degree - 1; k >= 0; k--) {
      if (r.nextInt(3) > 0) {
        s.append(" + ").append(pick(COEFFICIENTS, r)).append("x^").append(k);
      }
    }
    return s.append(")").toString();
  }

  /** The inner functions of substitution, with their intervals. */
  private record Inner(String g, double lo, double hi) {}

  private static final List<Inner> INNER =
      List.of(
          new Inner("x^2", -3, 3),
          new Inner("x^3 + 1", -2, 2),
          new Inner("sin(x)", -3, 3),
          new Inner("cos(x)", -3, 3),
          new Inner("e^x", -2, 2),
          new Inner("log(x)", 0.2, 5),
          new Inner("x^2 + 1", -3, 3));

  /** The outer functions of substitution, in {@code u}. */
  private static final List<String> OUTER =
      List.of("sin(u)", "cos(u)", "e^u", "u^3", "u^2", "sec(u)^2", "1/(1 + u^2)");

  static Arbitrary<Case> cases() {
    return Arbitraries.randomValue(IntegrateArbitraries::draw);
  }

  static Case draw(Random r) {
    Family family = Family.values()[r.nextInt(Family.values().length)];
    return family == Family.SUM ? sum(r) : one(family, r);
  }

  private static Case sum(Random r) {
    int n = 2 + r.nextInt(2);
    List<Case> parts = new ArrayList<>();
    for (int k = 0; k < n; k++) {
      parts.add(one(Family.values()[r.nextInt(Family.SUM.ordinal())], r));
    }
    double lo = parts.stream().mapToDouble(Case::lo).max().orElseThrow();
    double hi = parts.stream().mapToDouble(Case::hi).min().orElseThrow();
    if (hi - lo < 0.5) {
      return parts.getFirst();
    }
    List<Expr> terms = new ArrayList<>();
    for (Case c : parts) {
      terms.add(Product.of(parse(pick(COEFFICIENTS, r)), c.f()));
    }
    Expr sum = Canonical.of(Sum.of(terms));
    // fractions the simplifier combines into one quotient are a rational function, 4a-2's
    if (!(mathcanvas.engine.simplify.Simplifier.simplify(sum, Context.DEFAULT).expr()
        instanceof Sum)) {
      return parts.getFirst();
    }
    return new Case(sum, Family.SUM, lo, hi);
  }

  private static Case one(Family family, Random r) {
    return switch (family) {
      case POLYNOMIAL -> new Case(parse(polynomial(r, 0, 6)), family, -3, 3);
      case POLYNOMIAL_EXP ->
          new Case(parse(polynomial(r, 1, 3) + " * e^" + linear(r)), family, -2, 2);
      case POLYNOMIAL_TRIG ->
          new Case(
              parse(polynomial(r, 1, 3) + " * " + (r.nextBoolean() ? "sin" : "cos") + linear(r)),
              family,
              -3,
              3);
      case SUBSTITUTION -> substitution(r);
      case BY_PARTS -> byParts(r);
      case TRIG -> trig(r);
      case QUADRATIC -> quadratic(r);
      case SUM -> throw new IllegalArgumentException("a sum is drawn by sum");
    };
  }

  private static Case substitution(Random r) {
    Inner g = INNER.get(r.nextInt(INNER.size()));
    Expr inner = parse(g.g());
    Expr derivative = Calculus.differentiate(inner, X, 1, Context.DEFAULT).expr();
    String outer = OUTER.get(r.nextInt(OUTER.size()));
    Expr h = IntegrateRules.replace(parse(outer), Symbol.of("u"), inner, Context.DEFAULT);
    Expr f = Canonical.of(Product.of(parse(pick(COEFFICIENTS, r)), derivative, h));
    double lo = g.lo();
    double hi = g.hi();
    if (outer.startsWith("sec")) {
      // sec(g)² needs |g| < π/2: g within (-1.2, 1.2)
      return new Case(f, Family.SUBSTITUTION, Math.max(lo, -0.3), Math.min(hi, 0.3));
    }
    return new Case(f, Family.SUBSTITUTION, lo, hi);
  }

  private static Case byParts(Random r) {
    int n = r.nextInt(5);
    String c = pick(COEFFICIENTS, r);
    return switch (r.nextInt(8)) {
      case 0 -> new Case(parse(c + " x^" + n + " e^x"), Family.BY_PARTS, -2, 2);
      case 1 -> new Case(parse(c + " x^" + n + " log(x)"), Family.BY_PARTS, 0.2, 5);
      case 2 -> new Case(parse(c + " x^" + n + " sin(x)"), Family.BY_PARTS, -3, 3);
      case 3 -> new Case(parse(c + " x^" + n + " cos(x)"), Family.BY_PARTS, -3, 3);
      case 4 -> new Case(parse(c + " atan(x)"), Family.BY_PARTS, -3, 3);
      case 5 -> new Case(parse(c + " asin(x)"), Family.BY_PARTS, -0.9, 0.9);
      case 6 -> new Case(parse(c + " log(x)^2"), Family.BY_PARTS, 0.2, 5);
      default ->
          new Case(
              parse(
                  c
                      + " e^("
                      + pick(SLOPES, r)
                      + "x) "
                      + (r.nextBoolean() ? "sin" : "cos")
                      + "("
                      + pick(SLOPES, r)
                      + "x)"),
              Family.BY_PARTS,
              -2,
              2);
    };
  }

  private static Case trig(Random r) {
    String c = pick(COEFFICIENTS, r);
    int m = r.nextInt(6);
    int n = r.nextInt(6);
    return switch (r.nextInt(6)) {
      case 0 ->
          new Case(parse(c + " sin(x)^" + Math.max(m, 1) + " cos(x)^" + n), Family.TRIG, -3, 3);
      case 1 ->
          new Case(parse(c + " sin(" + (1 + m) + "x) cos(" + (2 + n) + "x)"), Family.TRIG, -3, 3);
      case 2 -> new Case(parse(c + " tan(x)^" + (1 + m)), Family.TRIG, -1.2, 1.2);
      case 3 -> new Case(parse(c + " sec(x)^" + (1 + m)), Family.TRIG, -1.2, 1.2);
      case 4 -> new Case(parse(c + " cot(x)^" + (1 + m)), Family.TRIG, 0.3, 2.8);
      default -> new Case(parse(c + " csc(x)^" + (1 + m)), Family.TRIG, 0.3, 2.8);
    };
  }

  private static Case quadratic(Random r) {
    int b = r.nextInt(7) - 3;
    int c = 1 + r.nextInt(5);
    // x² + bx + (b²/4 + c) > 0 everywhere
    String q = "x^2 + " + b + "x + " + (b * b + 4 * c) + "/4";
    // c perfect squares, so the roots of the opposite-sign forms are rational
    int k = 1 + r.nextInt(3);
    double root = k;
    return switch (r.nextInt(6)) {
      case 0 -> new Case(parse("1/(" + q + ")"), Family.QUADRATIC, -3, 3);
      case 1 -> new Case(parse("1/sqrt(" + q + ")"), Family.QUADRATIC, -3, 3);
      case 2 ->
          // (x − b/2)² − k² > 0 beyond b/2 + k
          new Case(
              Algebra.expand(parse("1/((x - " + b + "/2)^2 - " + k * k + ")"), Context.DEFAULT)
                  .expr(),
              Family.QUADRATIC,
              b / 2.0 + root + 0.2,
              b / 2.0 + root + 3);
      case 3 ->
          // k² − (x − b/2)², the k < 0 reciprocal, between its roots
          new Case(
              Canonical.of(
                  Power.of(
                      Algebra.expand(parse(k * k + " - (x - " + b + "/2)^2"), Context.DEFAULT)
                          .expr(),
                      parse("-1"))),
              Family.QUADRATIC,
              b / 2.0 - root + 0.2,
              b / 2.0 + root - 0.2);
      case 4 ->
          // 1/√((x − b/2)² − k²) beyond b/2 + k
          new Case(
              Canonical.of(
                  Power.of(
                      Algebra.expand(parse("(x - " + b + "/2)^2 - " + k * k), Context.DEFAULT)
                          .expr(),
                      parse("-1/2"))),
              Family.QUADRATIC,
              b / 2.0 + root + 0.2,
              b / 2.0 + root + 3);
      default -> {
        // c − (x − b/2)² > 0 on (b/2 − √c/2, b/2 + √c/2)
        String p = c + " - (x - " + b + "/2)^2";
        double half = Math.sqrt(c) / 2;
        Expr expanded = Algebra.expand(parse(p), Context.DEFAULT).expr();
        yield new Case(
            Canonical.of(Power.of(expanded, parse("-1/2"))),
            Family.QUADRATIC,
            b / 2.0 - half,
            b / 2.0 + half);
      }
    };
  }
}
