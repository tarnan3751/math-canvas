package mathcanvas.engine.expr;

import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;

/**
 * The canonical form: Cohen's automatic simplification (Joel S. Cohen, <i>Computer Algebra and
 * Symbolic Computation: Mathematical Methods</i>, §3.2), adapted to the engine's kinds. It is the
 * one transformation in the engine that is not a named rule: structural, untraced, and limited to
 * the list below. Distribution, expansion, radicals, trigonometry and logarithms are rule sets.
 *
 * <ul>
 *   <li><b>Bottom-up</b>: operands are canonicalised first; nested sums and products are flattened.
 *   <li><b>Numbers</b> in a sum or a product fold through the tower, in sorted order, so the result
 *       does not depend on the operand order. {@code Power(Num, Num)} folds when the result is
 *       exact or an operand is already inexact ({@code 4^(1/2)} is 2, {@code 2^(1/2)} stays), and
 *       an exact integer power only while its result stays under {@link #MAX_FOLD_BITS}.
 *   <li><b>Sum</b>: like terms combine by their non-numeric part ({@code 2x + 3x} is {@code 5x});
 *       an exact zero is dropped, but a zero coefficient on a non-commuting part stays ({@code A -
 *       A} is {@code 0·A}); one term is unwrapped, none is 0; the terms are sorted by {@link
 *       ExprOrder}.
 *   <li><b>Product</b>: factors with equal bases combine by adding exponents ({@code x·x} is {@code
 *       x^2}); an exact 1 is dropped; one factor is unwrapped, none is 1. Commuting factors are
 *       sorted; vectors, matrices, and sums, products and powers built from them do not commute,
 *       keep their typed order after them and never combine. An exact 0 coefficient makes the
 *       product 0, or, with non-commuting factors, {@code 0} times those factors alone, the zero of
 *       their shape ({@code 0·x·A} is {@code 0·A}).
 *   <li><b>Power</b>: {@code x^1 = x}, {@code x^0 = 1} (except for a non-commuting {@code x}, whose
 *       {@code x^0} stays, the identity of its shape), {@code 1^x = 1}, {@code 0^n} by the tower
 *       for a numeric {@code n} (0 for {@code Re n > 0}, undefined otherwise), {@code (x^a)^n =
 *       x^(a·n)} and {@code (x·y)^n = x^n·y^n} for an exact integer {@code n}.
 *   <li><b>Infinity and undefined</b>: an undefined operand makes a sum, product or power
 *       undefined; a cancellation to exact zero (of a coefficient sum, an exponent sum, or a zero
 *       coefficient) over a part built arithmetically from {@code ∞} or undefined is undefined, as
 *       are {@code ∞^0} and {@code 1^∞}.
 *   <li><b>Relation</b> between two numbers, or a number and {@code ±∞}, becomes a {@link Bool}; an
 *       ordering with a complex number stays. <b>Set</b>: sorted, duplicates removed. Every other
 *       kind keeps its structure with its operands canonicalised; nothing is evaluated.
 * </ul>
 *
 * <p>Identity rules test exactness as well as value: {@code x + 0.0}, {@code 1.0·x}, {@code x^1.0}
 * and {@code x^0.0} stay. {@code Canonical.of(Canonical.of(e))} equals {@code Canonical.of(e)}.
 * Grouping and sorting use {@link ExprOrder}, never a hash.
 */
public final class Canonical {

  /** The context of the no-context overload: 34 digits, half-even. */
  public static final MathContext DEFAULT_CONTEXT = MathContext.DECIMAL128;

  /** The largest exact integer power folded, in bits: the base's bit length times the exponent. */
  public static final long MAX_FOLD_BITS = 1L << 20;

  private final MathContext mc;

  private Canonical(MathContext mc) {
    this.mc = mc;
  }

  /**
   * Returns the canonical form of {@code e}, rounding inexact arithmetic to {@link
   * #DEFAULT_CONTEXT}.
   *
   * @param e the expression
   * @return its canonical form
   */
  public static Expr of(Expr e) {
    return of(e, DEFAULT_CONTEXT);
  }

  /**
   * Returns the canonical form of {@code e}, rounding inexact arithmetic to {@code mc}.
   *
   * @param e the expression
   * @param mc the context inexact results are rounded to
   * @return its canonical form
   */
  public static Expr of(Expr e, MathContext mc) {
    return new Canonical(mc).canon(e);
  }

  private Expr canon(Expr e) {
    return switch (e) {
      case Num _, Constant _, Bool _, Symbol _ -> e;
      case Sum s -> sum(flatten(s));
      case Product p -> product(flatten(p));
      case Power p -> power(canon(p.base()), canon(p.exponent()));
      case Apply a -> new Apply(a.head(), all(a.args()));
      case Lambda l -> new Lambda(l.params(), canon(l.body()));
      case Relation r -> relation(r.op(), canon(r.left()), canon(r.right()));
      case ListExpr l -> new ListExpr(all(l.items()));
      case Vector v -> new Vector(all(v.items()));
      case Matrix m -> new Matrix(m.rows().stream().map(this::all).toList());
      case SetExpr s -> set(all(s.items()));
      case Interval i ->
          new Interval(canon(i.lower()), canon(i.upper()), i.lowerClosed(), i.upperClosed());
      case Piecewise p ->
          new Piecewise(
              p.pieces().stream()
                  .map(piece -> new Piecewise.Piece(canon(piece.value()), canon(piece.condition())))
                  .toList(),
              canon(p.otherwise()));
      case Derivative d -> new Derivative(canon(d.expr()), d.var(), d.order());
      case Integral i ->
          i.isDefinite()
              ? Integral.definite(canon(i.integrand()), i.var(), canon(i.lower()), canon(i.upper()))
              : Integral.indefinite(canon(i.integrand()), i.var());
      case Limit l -> new Limit(canon(l.expr()), l.var(), canon(l.point()), l.direction());
      case BigSum s -> new BigSum(canon(s.body()), s.var(), canon(s.from()), canon(s.to()));
      case BigProduct p -> new BigProduct(canon(p.body()), p.var(), canon(p.from()), canon(p.to()));
    };
  }

  private List<Expr> all(List<Expr> items) {
    return items.stream().map(this::canon).toList();
  }

  // Flattening. Raw nesting of the same kind is walked with a stack, not recursion, so a long
  // left-nested chain such as the parser may build does not recurse once per operand. Operands
  // come out in typed order, which non-commuting factors keep.

  private List<Expr> flatten(Sum s) {
    List<Expr> out = new ArrayList<>();
    Deque<Expr> stack = new ArrayDeque<>();
    pushReversed(stack, s.terms());
    while (!stack.isEmpty()) {
      Expr t = stack.pop();
      if (t instanceof Sum inner) {
        pushReversed(stack, inner.terms());
      } else {
        addTerm(out, canon(t));
      }
    }
    return out;
  }

  private List<Expr> flatten(Product p) {
    List<Expr> out = new ArrayList<>();
    Deque<Expr> stack = new ArrayDeque<>();
    pushReversed(stack, p.factors());
    while (!stack.isEmpty()) {
      Expr f = stack.pop();
      if (f instanceof Product inner) {
        pushReversed(stack, inner.factors());
      } else {
        addFactor(out, canon(f));
      }
    }
    return out;
  }

  private static void pushReversed(Deque<Expr> stack, List<Expr> items) {
    for (int k = items.size() - 1; k >= 0; k--) {
      stack.push(items.get(k));
    }
  }

  private static void addTerm(List<Expr> out, Expr t) {
    if (t instanceof Sum s) {
      out.addAll(s.terms());
    } else {
      out.add(t);
    }
  }

  private static void addFactor(List<Expr> out, Expr f) {
    if (f instanceof Product p) {
      out.addAll(p.factors());
    } else {
      out.add(f);
    }
  }

  // Sums. The terms are canonical and none is a sum.

  private Expr sum(List<Expr> terms) {
    List<Numeric> numbers = new ArrayList<>();
    Map<Expr, List<Numeric>> groups = new TreeMap<>(ExprOrder.INSTANCE);
    for (Expr t : terms) {
      if (t.equals(Constant.UNDEFINED)) {
        return Constant.UNDEFINED;
      }
      if (t instanceof Num n) {
        numbers.add(n.value());
      } else {
        Numeric c =
            t instanceof Product p && p.factors().getFirst() instanceof Num n ? n.value() : Int.ONE;
        groups.computeIfAbsent(rest(t), k -> new ArrayList<>()).add(c);
      }
    }
    List<Expr> out = new ArrayList<>();
    boolean again = false;
    for (Map.Entry<Expr, List<Numeric>> group : groups.entrySet()) {
      Expr rest = group.getKey();
      Numeric c = add(group.getValue());
      if (exactZero(c) && infinite(rest)) {
        return Constant.UNDEFINED;
      }
      if (exactZero(c) && commutes(rest)) {
        continue;
      }
      // A zero coefficient on a non-commuting rest is the zero vector or matrix of its shape.
      Expr term =
          exactZero(c)
              ? product(flattenFactors(List.of(Num.ZERO, rest)))
              : withCoefficient(c, rest);
      if (term instanceof Sum s) {
        out.addAll(s.terms());
        again = true;
      } else {
        out.add(term);
      }
    }
    // A combined term can share its rest with another: two zero coefficients on a non-commuting
    // part each give 0·M. The grouping repeats until no two terms share a rest; a pass reruns
    // only when groups merge or a sum is flattened, so it ends.
    if (!again && sharesKey(out, Canonical::rest)) {
      again = true;
    }
    if (!numbers.isEmpty()) {
      Numeric constant = add(numbers);
      if (!exactZero(constant)) {
        out.add(new Num(constant));
      }
    }
    if (again) {
      return sum(out);
    }
    out.sort(ExprOrder.INSTANCE);
    return switch (out.size()) {
      case 0 -> Num.ZERO;
      case 1 -> out.getFirst();
      default -> new Sum(out);
    };
  }

  /** The non-numeric part of a term, by which like terms combine: {@code x} for {@code 2x}. */
  private static Expr rest(Expr t) {
    if (t instanceof Product p && p.factors().getFirst() instanceof Num) {
      List<Expr> rest = p.factors().subList(1, p.factors().size());
      return rest.size() == 1 ? rest.getFirst() : new Product(rest);
    }
    return t;
  }

  /** The base of a commuting factor, by which factors combine: {@code x} for {@code x^2}. */
  private static Expr base(Expr f) {
    return f instanceof Power p ? p.base() : f;
  }

  /** Whether two of the non-numeric items have the same key under the expression order. */
  private static boolean sharesKey(List<Expr> items, Function<Expr, Expr> key) {
    Set<Expr> seen = new TreeSet<>(ExprOrder.INSTANCE);
    for (Expr item : items) {
      if (!(item instanceof Num) && !seen.add(key.apply(item))) {
        return true;
      }
    }
    return false;
  }

  /** {@code c·rest} in canonical form, for a coefficient that is not an exact zero. */
  private static Expr withCoefficient(Numeric c, Expr rest) {
    if (exactOne(c)) {
      return rest;
    }
    List<Expr> factors = new ArrayList<>();
    factors.add(new Num(c));
    addFactor(factors, rest);
    return new Product(factors);
  }

  // Products. The factors are canonical and none is a product.

  private Expr product(List<Expr> factors) {
    List<Numeric> numbers = new ArrayList<>();
    Map<Expr, List<Expr>> groups = new TreeMap<>(ExprOrder.INSTANCE);
    List<Expr> fixed = new ArrayList<>();
    for (Expr f : factors) {
      if (f.equals(Constant.UNDEFINED)) {
        return Constant.UNDEFINED;
      }
      if (f instanceof Num n) {
        numbers.add(n.value());
      } else if (!commutes(f)) {
        fixed.add(f);
      } else if (f instanceof Power p) {
        groups.computeIfAbsent(p.base(), k -> new ArrayList<>()).add(p.exponent());
      } else {
        groups.computeIfAbsent(f, k -> new ArrayList<>()).add(Num.ONE);
      }
    }
    Numeric coefficient = numbers.isEmpty() ? Int.ONE : multiply(numbers);
    if (exactZero(coefficient)) {
      for (Expr f : factors) {
        if (!(f instanceof Num) && infinite(f)) {
          return Constant.UNDEFINED;
        }
      }
      if (fixed.isEmpty()) {
        return Num.ZERO;
      }
      // Zero times the non-commuting factors is the zero vector or matrix of their shape; the
      // commuting factors only multiply the zero.
      List<Expr> zero = new ArrayList<>();
      zero.add(Num.ZERO);
      zero.addAll(fixed);
      return new Product(zero);
    }
    List<Expr> out = new ArrayList<>();
    boolean again = false;
    for (Map.Entry<Expr, List<Expr>> group : groups.entrySet()) {
      Expr base = group.getKey();
      List<Expr> exponents = group.getValue();
      Expr exponent = exponents.size() == 1 ? exponents.getFirst() : sum(flattenTerms(exponents));
      if (exponents.size() > 1 && isExactZero(exponent) && infinite(base)) {
        return Constant.UNDEFINED;
      }
      Expr f = exponents.size() == 1 && exponent.equals(Num.ONE) ? base : power(base, exponent);
      if (f instanceof Num || f instanceof Product || f.equals(Constant.UNDEFINED)) {
        again = true;
      }
      out.add(f);
    }
    // A rebuilt factor can have a new base that another group holds: (b^-1)^(1/2) groups under
    // b^-1, and its square b^-1 under b. The grouping repeats until no two factors share a base
    // and no factor is a number, a product or undefined. It ends: a pass reruns only when two
    // groups merge or a factor is flattened, and power() gives a factor a new base only by
    // removing a level of nesting, distributing over a product or folding a number.
    if (!again && sharesKey(out, Canonical::base)) {
      again = true;
    }
    if (again) {
      List<Expr> all = new ArrayList<>();
      all.add(new Num(coefficient));
      for (Expr f : out) {
        addFactor(all, f);
      }
      all.addAll(fixed);
      return product(all);
    }
    out.sort(ExprOrder.INSTANCE);
    if (!exactOne(coefficient)) {
      out.addFirst(new Num(coefficient));
    }
    out.addAll(fixed);
    return switch (out.size()) {
      case 0 -> Num.ONE;
      case 1 -> out.getFirst();
      default -> new Product(out);
    };
  }

  private static List<Expr> flattenTerms(List<Expr> terms) {
    List<Expr> out = new ArrayList<>();
    for (Expr t : terms) {
      addTerm(out, t);
    }
    return out;
  }

  /**
   * Whether a factor commutes: vectors, matrices, and sums, products and powers built from them do
   * not. The matcher and the printers use the same test, so the one definition lives here.
   *
   * @param f the factor
   * @return true if {@code f} commutes with every other factor
   */
  public static boolean commutes(Expr f) {
    if (f instanceof Vector || f instanceof Matrix) {
      return false;
    }
    if (f instanceof Sum s) {
      return s.terms().stream().allMatch(Canonical::commutes);
    }
    if (f instanceof Product p) {
      return p.factors().stream().allMatch(Canonical::commutes);
    }
    if (f instanceof Power p) {
      return commutes(p.base()) && commutes(p.exponent());
    }
    return true;
  }

  // Powers. The operands are canonical.

  private Expr power(Expr base, Expr exponent) {
    if (base.equals(Constant.UNDEFINED) || exponent.equals(Constant.UNDEFINED)) {
      return Constant.UNDEFINED;
    }
    if (base instanceof Num b && exponent instanceof Num e) {
      return foldPower(b.value(), e.value());
    }
    if (isExactZero(exponent)) {
      // A non-commuting base keeps its zero power, the identity of its shape, as 0·M is its zero.
      if (!commutes(base)) {
        return new Power(base, exponent);
      }
      return infinite(base) ? Constant.UNDEFINED : Num.ONE;
    }
    if (exponent.equals(Num.ONE)) {
      return base;
    }
    if (base.equals(Num.ONE)) {
      return infinite(exponent) ? Constant.UNDEFINED : Num.ONE;
    }
    if (exponent instanceof Num e && e.value() instanceof Int) {
      if (base instanceof Power inner) {
        return power(inner.base(), product(flattenFactors(List.of(inner.exponent(), exponent))));
      }
      if (base instanceof Product p && p.factors().stream().allMatch(Canonical::commutes)) {
        List<Expr> factors = new ArrayList<>();
        for (Expr f : p.factors()) {
          addFactor(factors, power(f, exponent));
        }
        return product(factors);
      }
    }
    return new Power(base, exponent);
  }

  private static List<Expr> flattenFactors(List<Expr> factors) {
    List<Expr> out = new ArrayList<>();
    for (Expr f : factors) {
      addFactor(out, f);
    }
    return out;
  }

  /**
   * {@code b^e} for two numbers: folded when the result is exact or an operand is inexact, and for
   * an exact integer power only under {@link #MAX_FOLD_BITS}; a zero base the tower rejects is
   * undefined, any other rejected power stays.
   */
  private Expr foldPower(Numeric b, Numeric e) {
    boolean exact = b.isExact() && e.isExact();
    if (exact && exactOne(b)) {
      return Num.ONE;
    }
    if (exact && !b.isZero() && tooLarge(b, e)) {
      return new Power(new Num(b), new Num(e));
    }
    Numeric result;
    try {
      result = b.pow(e, exact ? MathContext.UNLIMITED : mc);
    } catch (ArithmeticException | IllegalArgumentException rejected) {
      return b.isZero() ? Constant.UNDEFINED : new Power(new Num(b), new Num(e));
    }
    return !exact || result.isExact() ? new Num(result) : new Power(new Num(b), new Num(e));
  }

  /**
   * Whether an exact power is out of reach: its result would exceed {@link #MAX_FOLD_BITS}, or its
   * exponent is {@code p/q} with {@code q} above the base's bit length, so that the base cannot be
   * a perfect {@code q}-th power (and the tower's root search would be needlessly long).
   */
  private static boolean tooLarge(Numeric b, Numeric e) {
    if (b.isOne() || b.negate().isOne()) {
      return false;
    }
    long bits = Math.max(bitLength(b.re()), bitLength(b.im()));
    BigInteger n =
        switch (e.re()) {
          case Int k -> k.value();
          case Rational q -> {
            if (q.den().compareTo(BigInteger.valueOf(bits)) > 0) {
              yield null;
            }
            yield q.num();
          }
          case Real _ -> BigInteger.ZERO;
        };
    return n == null
        || n.abs().bitLength() > Integer.SIZE
        || bits * n.abs().longValueExact() > MAX_FOLD_BITS;
  }

  /** The bit length of an exact number: a rational's numerator and denominator together. */
  private static long bitLength(RealNumeric x) {
    return switch (x) {
      case Int i -> i.value().bitLength();
      case Rational q -> q.num().bitLength() + q.den().bitLength();
      case Real _ -> 0;
    };
  }

  // Relations and sets.

  private static Expr relation(Relation.Op op, Expr left, Expr right) {
    if (op == Relation.Op.IN) {
      return new Relation(op, left, right);
    }
    if (left instanceof Num a && right instanceof Num b) {
      Numeric x = a.value();
      Numeric y = b.value();
      if (op == Relation.Op.EQ || op == Relation.Op.NE) {
        boolean equal = x.re().compareValue(y.re()) == 0 && x.im().compareValue(y.im()) == 0;
        return Bool.of(equal == (op == Relation.Op.EQ));
      }
      if (x instanceof RealNumeric p && y instanceof RealNumeric q) {
        return decide(op, p.compareValue(q));
      }
      return new Relation(op, left, right);
    }
    int leftInfinity = infinitySign(left);
    int rightInfinity = infinitySign(right);
    if (left instanceof Num a && rightInfinity != 0) {
      return decideAgainstInfinity(op, a.value(), -rightInfinity, left, right);
    }
    if (right instanceof Num b && leftInfinity != 0) {
      return decideAgainstInfinity(op, b.value(), leftInfinity, left, right);
    }
    return new Relation(op, left, right);
  }

  /** A number against {@code ±∞}; {@code order} is the sign of {@code left - right}. */
  private static Expr decideAgainstInfinity(
      Relation.Op op, Numeric number, int order, Expr left, Expr right) {
    return switch (op) {
      case EQ -> Bool.FALSE;
      case NE -> Bool.TRUE;
      default -> number instanceof Complex ? new Relation(op, left, right) : decide(op, order);
    };
  }

  private static Bool decide(Relation.Op op, int order) {
    return Bool.of(
        switch (op) {
          case EQ -> order == 0;
          case NE -> order != 0;
          case LT -> order < 0;
          case LE -> order <= 0;
          case GT -> order > 0;
          case GE -> order >= 0;
          case IN -> throw new IllegalArgumentException("membership is not an order");
        });
  }

  /** 1 for {@code ∞}, -1 for {@code -∞} ({@code Product(-1, ∞)}), else 0. */
  private static int infinitySign(Expr e) {
    if (e.equals(Constant.INFINITY)) {
      return 1;
    }
    if (e instanceof Product p
        && p.factors().size() == 2
        && p.factors().getFirst().equals(Num.MINUS_ONE)
        && p.factors().get(1).equals(Constant.INFINITY)) {
      return -1;
    }
    return 0;
  }

  private static Expr set(List<Expr> items) {
    List<Expr> sorted = new ArrayList<>(items);
    sorted.sort(ExprOrder.INSTANCE);
    List<Expr> out = new ArrayList<>();
    for (Expr item : sorted) {
      if (out.isEmpty() || ExprOrder.INSTANCE.compare(out.getLast(), item) != 0) {
        out.add(item);
      }
    }
    return new SetExpr(out);
  }

  // Numbers.

  private Numeric add(List<Numeric> numbers) {
    List<Numeric> sorted = new ArrayList<>(numbers);
    sorted.sort(ExprOrder::compareNumbers);
    Numeric total = sorted.getFirst();
    for (Numeric n : sorted.subList(1, sorted.size())) {
      total = total.add(n, mc);
    }
    return total;
  }

  private Numeric multiply(List<Numeric> numbers) {
    List<Numeric> sorted = new ArrayList<>(numbers);
    sorted.sort(ExprOrder::compareNumbers);
    Numeric total = sorted.getFirst();
    for (Numeric n : sorted.subList(1, sorted.size())) {
      total = total.multiply(n, mc);
    }
    return total;
  }

  private static boolean exactZero(Numeric n) {
    return n.isZero() && n.isExact();
  }

  private static boolean exactOne(Numeric n) {
    return n.isOne() && n.isExact();
  }

  private static boolean isExactZero(Expr e) {
    return e instanceof Num n && exactZero(n.value());
  }

  /** Whether {@code e} is built by sums, products and powers from {@code ∞} or undefined. */
  private static boolean infinite(Expr e) {
    if (e instanceof Constant c) {
      return c.kind() == Constant.Kind.INFINITY || c.kind() == Constant.Kind.UNDEFINED;
    }
    if (e instanceof Sum s) {
      return s.terms().stream().anyMatch(Canonical::infinite);
    }
    if (e instanceof Product p) {
      return p.factors().stream().anyMatch(Canonical::infinite);
    }
    if (e instanceof Power p) {
      return infinite(p.base()) || infinite(p.exponent());
    }
    return false;
  }
}
