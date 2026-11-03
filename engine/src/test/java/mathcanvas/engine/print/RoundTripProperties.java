package mathcanvas.engine.print;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprArbitraries;
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
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.parse.Brackets;
import mathcanvas.engine.parse.Lexer;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.ParseException;
import mathcanvas.engine.parse.Parser;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.RandomDistribution;

/**
 * The round trip of {@code docs/language.md} §15.4. A canonical value displayed by the ASCII or the
 * Unicode printer reads back, after canonicalisation, as itself; a raw tree printed faithfully
 * reads back as the tree the parser builds for its text ({@link TypedShape}). The generated
 * function heads {@code f} and {@code g} are user functions, so their applications read back as
 * calls.
 *
 * <p>The exceptions, which §15.4 lists and {@link #expressible} filters: intervals and piecewise
 * expressions (no syntax yet); a list that the bracket rule would read as a vector or a matrix, and
 * a vector or matrix with an element that is not scalar. Every canonical sample is first checked to
 * be a fixed point of the canonical form, since the round trip is stated for canonical values.
 */
class RoundTripProperties {

  private static final Names FUNCTIONS = Set.of("f", "g")::contains;

  @Provide
  Arbitrary<Expr> canonical() {
    return ExprArbitraries.exprs().map(Canonical::of).filter(RoundTripProperties::expressible);
  }

  @Provide
  Arbitrary<Expr> exactCanonical() {
    return ExprArbitraries.exactExprs().map(Canonical::of);
  }

  @Provide
  Arbitrary<Expr> raw() {
    return ExprArbitraries.exprs().filter(RoundTripProperties::expressible);
  }

  private static void displayed(Printer printer, Expr c) {
    assertThat(Canonical.of(c)).as("a fixed point of the canonical form").isEqualTo(c);
    String text = printer.display(c);
    assertThat(Canonical.of(parse(text))).as(text).isEqualTo(c);
  }

  private static void printed(Printer printer, Expr e) {
    String text = printer.print(e);
    assertThat(parse(text)).as(text).isEqualTo(TypedShape.of(e));
  }

  /** Parses printed text; a failure to parse names the text. */
  private static Expr parse(String text) {
    try {
      return Parser.parseExpression(text, FUNCTIONS);
    } catch (ParseException e) {
      throw new AssertionError(e + " in " + text, e);
    }
  }

  @Example
  void knownCases() {
    // An integer-valued decimal of 22 digits keeps its point, as an exponent, and reads back
    // inexact.
    Expr big = Canonical.of(Power.of(Num.of(Real.of("2.0")), Num.of(70)));
    displayed(AsciiPrinter.INSTANCE, big);
    displayed(UnicodePrinter.INSTANCE, big);
    // A symbol left in limit's fourth argument is not the side of a Limit.
    Expr limit =
        Apply.of(Symbol.of("limit"), Symbol.of("x"), Symbol.of("x"), Num.ZERO, Symbol.of("left"));
    printed(AsciiPrinter.INSTANCE, limit);
    printed(UnicodePrinter.INSTANCE, limit);
    // A folded denominator of 104,969 digits, past the lexer's former cap, reads back.
    Expr third = Canonical.of(Power.of(Num.of(3), Num.of(-220_000)));
    displayed(AsciiPrinter.INSTANCE, third);
    displayed(UnicodePrinter.INSTANCE, third);
    // A rational whose denominator is past the fold limit, from a product of rationals, reads
    // back as itself, since the inverse of an exact number folds whatever its size (the evaluator
    // shows such a fraction only within its cap, but the printer and the parser agree on it).
    Expr b = Num.of(Rational.of(BigInteger.ONE, BigInteger.valueOf(3).pow(200_000)));
    Expr fourth = Canonical.of(Product.of(b, b, b, b));
    assertThat(fourth).isInstanceOf(Num.class);
    displayed(AsciiPrinter.INSTANCE, fourth);
    displayed(UnicodePrinter.INSTANCE, fourth);
    // The longest numeral shown: an integer of MAX_DIGITS nines.
    BigInteger nines = BigInteger.TEN.pow(Lexer.MAX_DIGITS).subtract(BigInteger.ONE);
    displayed(UnicodePrinter.INSTANCE, Num.of(Int.of(nines.negate())));
  }

  /** The most bits an integer of at most {@link Lexer#MAX_DIGITS} digits can have. */
  private static final int HUGE_BITS = BigInteger.TEN.pow(Lexer.MAX_DIGITS).bitLength() - 1;

  /**
   * A {@code k} with {@code 3^k} just under {@link Canonical#MAX_FOLD_BITS} bits, so of at most
   * {@link Canonical#MAX_FOLD_DIGITS} digits: {@code log₂ 3 > 1.585}.
   */
  private static final int FRACTION_THREES = (int) (Canonical.MAX_FOLD_BITS * 1000 / 1585);

  /**
   * The longest numbers a value shown can hold (the evaluator refuses to show longer ones):
   * integers of up to {@link Lexer#MAX_DIGITS} digits, and rationals {@code ±2^m·s/3^k} whose
   * numerator and denominator have up to {@link Canonical#MAX_FOLD_DIGITS} digits. Their shape
   * keeps the gcd of numerator and denominator cheap when they are generated; reading them back
   * costs one gcd at the fraction cap.
   */
  @Provide
  Arbitrary<Expr> hugeNumbers() {
    Arbitrary<Integer> bits =
        Arbitraries.integers().between(1, HUGE_BITS).withDistribution(RandomDistribution.uniform());
    Arbitrary<Expr> integers =
        Combinators.combine(bits, Arbitraries.randoms())
            .as(
                (n, random) -> {
                  BigInteger v = new BigInteger(n, random).setBit(n - 1);
                  return Num.of(Int.of(random.nextBoolean() ? v : v.negate()));
                });
    Arbitrary<Integer> fractionBits =
        Arbitraries.integers()
            .between(1, (int) Canonical.MAX_FOLD_BITS)
            .withDistribution(RandomDistribution.uniform());
    Arbitrary<Integer> threes =
        Arbitraries.integers()
            .between(1, FRACTION_THREES)
            .withDistribution(RandomDistribution.uniform());
    Arbitrary<Expr> rationals =
        Combinators.combine(fractionBits, threes, Arbitraries.integers().between(-1000, 1000))
            .as(
                (m, k, small) -> {
                  BigInteger s = BigInteger.valueOf(small == 0 ? 1 : small);
                  if (s.mod(BigInteger.valueOf(3)).signum() == 0) {
                    s = s.add(BigInteger.ONE);
                  }
                  BigInteger num = s.shiftLeft(Math.max(0, m - s.bitLength()));
                  return Num.of(Rational.of(num, BigInteger.valueOf(3).pow(k)));
                });
    return Arbitraries.oneOf(integers, rationals).map(Canonical::of);
  }

  /** Few tries, each a few seconds: printing and reading a million digits is not free. */
  @Property(tries = 3)
  void hugeNumbersReadBack(@ForAll("hugeNumbers") Expr c) {
    displayed(AsciiPrinter.INSTANCE, c);
    displayed(UnicodePrinter.INSTANCE, c);
  }

  @Property(tries = 2000)
  void asciiDisplayReadsBack(@ForAll("canonical") Expr c) {
    displayed(AsciiPrinter.INSTANCE, c);
  }

  @Property(tries = 2000)
  void unicodeDisplayReadsBack(@ForAll("canonical") Expr c) {
    displayed(UnicodePrinter.INSTANCE, c);
  }

  @Property(tries = 2000)
  void exactValuesReadBack(@ForAll("exactCanonical") Expr c) {
    displayed(AsciiPrinter.INSTANCE, c);
    displayed(UnicodePrinter.INSTANCE, c);
  }

  @Property(tries = 2000)
  void asciiPrintReadsBackAsTyped(@ForAll("raw") Expr e) {
    printed(AsciiPrinter.INSTANCE, e);
  }

  @Property(tries = 2000)
  void unicodePrintReadsBackAsTyped(@ForAll("raw") Expr e) {
    printed(UnicodePrinter.INSTANCE, e);
  }

  @Property(tries = 1000)
  void latexPrintsEveryTree(@ForAll("raw") Expr e) {
    assertThat(LatexPrinter.INSTANCE.print(e)).isNotEmpty();
    assertThat(LatexPrinter.INSTANCE.display(Canonical.of(e))).isNotEmpty();
  }

  /** Whether the grammar can write the tree (§15.4); see the class comment. */
  static boolean expressible(Expr e) {
    return switch (e) {
      case Interval i -> false;
      case Piecewise p -> false;
      case Num n -> true;
      case mathcanvas.engine.expr.Based b -> false;
      case Constant c -> true;
      case Bool b -> true;
      case Symbol s -> true;
      case mathcanvas.engine.expr.Unit u -> true;
      case Sum s -> all(s.terms());
      case Product p -> all(p.factors());
      case Power p -> expressible(p.base()) && expressible(p.exponent());
      case Apply a -> all(a.args());
      case Lambda l -> expressible(l.body());
      case Relation r -> expressible(r.left()) && expressible(r.right());
      case ListExpr l -> Brackets.of(l.items()) instanceof ListExpr && all(l.items());
      case Vector v -> Brackets.allScalar(v.items()) && all(v.items());
      case Matrix m -> m.rows().stream().allMatch(row -> Brackets.allScalar(row) && all(row));
      case SetExpr s -> all(s.items());
      case Derivative d -> expressible(d.expr());
      case Integral i ->
          expressible(i.integrand())
              && (!i.isDefinite() || (expressible(i.lower()) && expressible(i.upper())));
      case Limit l -> expressible(l.expr()) && expressible(l.point());
      case BigSum s -> expressible(s.body()) && expressible(s.from()) && expressible(s.to());
      case BigProduct s -> expressible(s.body()) && expressible(s.from()) && expressible(s.to());
    };
  }

  private static boolean all(List<Expr> items) {
    return items.stream().allMatch(RoundTripProperties::expressible);
  }
}
