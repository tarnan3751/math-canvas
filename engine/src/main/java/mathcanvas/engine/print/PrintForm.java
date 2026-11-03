package mathcanvas.engine.print;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import mathcanvas.engine.builtin.Builtins;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
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
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;

/**
 * What the printers share: the precedence levels of {@code docs/language.md} §4.1, the shapes the
 * printers recognise, the raw shape a number reads back as, and the Unicode forms of subscripts and
 * superscripts.
 */
final class PrintForm {

  /** A lambda. */
  static final int LAMBDA = 10;

  /** {@code implies} (Phase 8a). */
  static final int IMPLIES = 12;

  /** {@code or}. */
  static final int OR = 14;

  /** {@code xor}. */
  static final int XOR = 15;

  /** {@code and}. */
  static final int AND = 16;

  /** {@code not}, whose operand is a relation or tighter. */
  static final int NOT = 18;

  /** A relation. */
  static final int RELATION = 20;

  /** A sum or difference. */
  static final int SUM = 30;

  /** A product, quotient or cross product. */
  static final int PRODUCT = 40;

  /** A prefix form: a sign, a radical, a big operator. */
  static final int PREFIX = 50;

  /** A power. */
  static final int POWER = 60;

  /** An integral in notation: closed by its differential, but not a primary. */
  static final int INTEGRAL = 65;

  /** A postfix form. */
  static final int POSTFIX = 70;

  /** A primary: a name, a literal, a call, a bracketed form. */
  static final int ATOM = 100;

  private static final Num HALF = Num.of(Rational.of(1, 2));
  private static final Num I = Num.of(Complex.I);

  private PrintForm() {}

  /** Whether {@code e} is {@code -x}: {@code Product(-1, x)} with exactly two factors. */
  static boolean isNegation(Expr e) {
    return e instanceof Product p
        && p.factors().size() == 2
        && p.factors().getFirst().equals(Num.MINUS_ONE);
  }

  /** Whether {@code e} is {@code x^-1} with the exponent the number {@code -1}. */
  static boolean isReciprocal(Expr e) {
    return e instanceof Power p && p.exponent().equals(Num.MINUS_ONE);
  }

  /** Whether {@code e} is {@code x^(1/2)} with the exponent the number {@code 1/2}. */
  static boolean isSqrt(Expr e) {
    return e instanceof Power p && p.exponent().equals(HALF);
  }

  /** Whether {@code e} is a call of {@code name} with {@code arity} arguments. */
  static boolean isCall(Expr e, String name, int arity) {
    return e instanceof Apply a && a.head().name().equals(name) && a.args().size() == arity;
  }

  /**
   * The literal of a non-negative integer or decimal, as the tower writes it: a decimal always has
   * a point, so it reads back as inexact ({@code 2000.0}, {@code 1.180591620717411303424e+21}; see
   * {@link Real#toString}).
   */
  static String literal(Numeric n) {
    return n.toString();
  }

  /** Whether a number prints as a literal: a non-negative integer or decimal. */
  static boolean isLiteral(Numeric n) {
    return (n instanceof Int || n instanceof Real) && ((RealNumeric) n).signum() >= 0;
  }

  /**
   * The raw tree a number reads back as: {@code -3} is {@code Product(-1, 3)}, {@code 1/2} is
   * {@code Product(1, Power(2, -1))}, {@code 1 - 2i} is a sum. Literals and {@code i} are
   * themselves.
   */
  static Expr typed(Numeric n) {
    return switch (n) {
      case Int x -> x.signum() < 0 ? Product.of(Num.MINUS_ONE, Num.of(x.negate())) : Num.of(x);
      case Real x -> x.signum() < 0 ? Product.of(Num.MINUS_ONE, Num.of(x.negate())) : Num.of(x);
      case Rational q ->
          Product.of(typed(q.numerator()), Power.of(Num.of(q.denominator()), Num.MINUS_ONE));
      case Complex c -> {
        RealNumeric re = c.re();
        RealNumeric im = c.im();
        if (Display.dropsRealPart(c)) {
          yield im.signum() >= 0 ? imaginary(im) : negate(imaginary(im.negate()));
        }
        yield Sum.of(
            typed(re),
            im.signum() >= 0 ? imaginary(im) : Product.of(Num.MINUS_ONE, imaginary(im.negate())));
      }
    };
  }

  /** {@code m·i} for a non-negative {@code m}: {@code i}, {@code 2i}, {@code i/2}, {@code 3i/2}. */
  private static Expr imaginary(RealNumeric m) {
    if (m.isOne() && m.isExact()) {
      return I;
    }
    if (m instanceof Rational q) {
      Num den = Num.of(q.denominator());
      return q.numerator().isOne()
          ? Product.of(I, Power.of(den, Num.MINUS_ONE))
          : Product.of(Num.of(q.numerator()), I, Power.of(den, Num.MINUS_ONE));
    }
    return Product.of(Num.of(m), I);
  }

  /** The negation of an imaginary shape, with the sign on its first factor: {@code -2i}. */
  private static Expr negate(Expr imaginary) {
    if (imaginary instanceof Product p) {
      List<Expr> factors = new ArrayList<>(p.factors());
      factors.set(0, Product.of(Num.MINUS_ONE, factors.getFirst()));
      return Product.of(factors);
    }
    return Product.of(Num.MINUS_ONE, imaginary);
  }

  /**
   * The Unicode form of a name: a subscriptable tail after the last {@code _} is subscripted, and
   * the primes of the prime notation are written {@code ′}.
   */
  static String subscripted(String name) {
    int prime = name.indexOf('\'');
    if (prime > 0) {
      return subscripted(name.substring(0, prime)) + "′".repeat(name.length() - prime);
    }
    int cut = name.lastIndexOf('_');
    if (cut < 1 || cut == name.length() - 1) {
      return name;
    }
    String tail = subscripts(name.substring(cut + 1));
    return tail == null ? name : name.substring(0, cut) + tail;
  }

  /** The subscript characters of a text, or null when one of its characters has none. */
  static String subscripts(String text) {
    StringBuilder out = new StringBuilder();
    for (char c : text.toCharArray()) {
      int s = subscript(c);
      if (s == 0) {
        return null;
      }
      out.appendCodePoint(s);
    }
    return out.toString();
  }

  private static int subscript(char c) {
    if (c >= '0' && c <= '9') {
      return 0x2080 + c - '0';
    }
    return switch (c) {
      case 'a' -> 'ₐ';
      case 'e' -> 'ₑ';
      case 'o' -> 'ₒ';
      case 'x' -> 'ₓ';
      case 'h' -> 'ₕ';
      case 'k' -> 'ₖ';
      case 'l' -> 'ₗ';
      case 'm' -> 'ₘ';
      case 'n' -> 'ₙ';
      case 'p' -> 'ₚ';
      case 's' -> 'ₛ';
      case 't' -> 'ₜ';
      case 'i' -> 'ᵢ';
      case 'r' -> 'ᵣ';
      case 'u' -> 'ᵤ';
      case 'v' -> 'ᵥ';
      case 'j' -> 'ⱼ';
      default -> 0;
    };
  }

  /**
   * Whether a derivative prints with {@code ∂} (§10.1): its body has two or more free symbols. A
   * derivative's variable is free in it; a binder's variable is not free in its body (a lambda, a
   * definite integral, a limit, a sum or a product).
   *
   * @param d the outermost derivative
   * @return true for {@code ∂}
   */
  static boolean partial(Derivative d) {
    Set<String> names = new TreeSet<>();
    free(d.expr(), names);
    return names.size() >= 2;
  }

  private static void free(Expr e, Set<String> out) {
    if (e instanceof Symbol s) {
      out.add(s.name());
      return;
    }
    Symbol hidden =
        switch (e) {
          case Integral i when i.isDefinite() -> i.var();
          case Limit l -> l.var();
          case BigSum b -> b.var();
          case BigProduct b -> b.var();
          default -> null;
        };
    List<Expr> ops = Operands.of(e);
    for (int k = 0; k < ops.size(); k++) {
      if (Operands.isBound(e, k) && !(e instanceof Derivative)) {
        continue;
      }
      Set<String> inner = new TreeSet<>();
      free(ops.get(k), inner);
      if (hidden != null && k == 0) {
        inner.remove(hidden.name());
      }
      if (e instanceof Lambda l) {
        l.params().forEach(p -> inner.remove(p.name()));
      }
      out.addAll(inner);
    }
  }

  /** The superscript digits of a non-negative integer. */
  static String superscript(String digits) {
    StringBuilder out = new StringBuilder();
    for (char c : digits.toCharArray()) {
      out.append("⁰¹²³⁴⁵⁶⁷⁸⁹".charAt(c - '0'));
    }
    return out.toString();
  }

  /**
   * Whether an integral must be printed in command form, because the notation would not read back
   * (§15.2): its variable begins with {@code d}, {@code d} and its variable spell a builtin, or its
   * integrand contains a name of the form {@code d} + something.
   */
  static boolean integralNeedsCommandForm(Integral integral) {
    String var = integral.var().name();
    return var.startsWith("d")
        || Builtins.lookup("d" + var).isPresent()
        || containsDName(integral.integrand());
  }

  private static boolean isDName(Symbol s) {
    return s.name().length() > 1 && s.name().startsWith("d") && Builtins.lookup(s.name()).isEmpty();
  }

  private static boolean anyDName(List<Expr> items) {
    for (Expr item : items) {
      if (containsDName(item)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsDName(Expr e) {
    return switch (e) {
      case Num n -> false;
      case mathcanvas.engine.expr.Based b -> false;
      case Constant c -> false;
      case Bool b -> false;
      case Symbol s -> isDName(s);
      case mathcanvas.engine.expr.Unit u -> false;
      case Sum s -> anyDName(s.terms());
      case Product p -> anyDName(p.factors());
      case Power p -> containsDName(p.base()) || containsDName(p.exponent());
      case Apply a -> isDName(a.head()) || anyDName(a.args());
      case Lambda l -> l.params().stream().anyMatch(PrintForm::isDName) || containsDName(l.body());
      case Relation r -> containsDName(r.left()) || containsDName(r.right());
      case ListExpr l -> anyDName(l.items());
      case Vector v -> anyDName(v.items());
      case SetExpr s -> anyDName(s.items());
      case Matrix m -> m.rows().stream().anyMatch(PrintForm::anyDName);
      case Interval i -> containsDName(i.lower()) || containsDName(i.upper());
      case Piecewise p -> {
        boolean found = containsDName(p.otherwise());
        for (Piecewise.Piece q : p.pieces()) {
          found |= containsDName(q.value()) || containsDName(q.condition());
        }
        yield found;
      }
      case Derivative d -> isDName(d.var()) || containsDName(d.expr());
      case Integral i ->
          isDName(i.var())
              || containsDName(i.integrand())
              || (i.isDefinite() && (containsDName(i.lower()) || containsDName(i.upper())));
      case Limit l -> isDName(l.var()) || containsDName(l.expr()) || containsDName(l.point());
      case BigSum s ->
          isDName(s.var())
              || containsDName(s.body())
              || containsDName(s.from())
              || containsDName(s.to());
      case BigProduct s ->
          isDName(s.var())
              || containsDName(s.body())
              || containsDName(s.from())
              || containsDName(s.to());
    };
  }
}
