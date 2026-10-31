package mathcanvas.engine.calculus;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Matcher;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.rule.Match;

/**
 * The trigonometric identities of the rule set {@code integrate}: an integrand {@code sin(u)^m
 * cos(u)^n} with an odd exponent of at least 3 loses all but one factor of it through {@code sin² =
 * 1 − cos²} ({@code odd-power}), with both exponents even it is written in {@code cos(2u)} by the
 * half-angle forms ({@code half-angle}), and a product of two sines or cosines of different linear
 * arguments becomes a sum ({@code product-to-sum}). Each shape is read through the matcher; {@code
 * u} is linear in the variable.
 */
final class TrigIdentities {

  private TrigIdentities() {}

  private static final List<Pattern> POWERS =
      List.of(
          Pattern.parse("sin(u_)^m_ * cos(u_)^n_").optional("m_", Num.ONE).optional("n_", Num.ONE),
          Pattern.parse("sin(u_)^m_").optional("m_", Num.ONE),
          Pattern.parse("cos(u_)^n_").optional("n_", Num.ONE));

  private static final List<Pattern> PRODUCTS =
      List.of(
          Pattern.parse("sin(v_) * cos(w_)"),
          Pattern.parse("sin(v_) * sin(w_)"),
          Pattern.parse("cos(v_) * cos(w_)"));

  private static final List<Expr> PRODUCT_FORMS =
      List.of(
          IntegrateRules.template("(sin(v_ + w_) + sin(v_ - w_)) / 2"),
          IntegrateRules.template("(cos(v_ - w_) - cos(v_ + w_)) / 2"),
          IntegrateRules.template("(cos(v_ - w_) + cos(v_ + w_)) / 2"));

  private static final Expr ODD_SIN =
      IntegrateRules.template("sin(u_) * (1 - cos(u_)^2)^k_ * cos(u_)^n_");
  private static final Expr ODD_COS =
      IntegrateRules.template("cos(u_) * (1 - sin(u_)^2)^k_ * sin(u_)^m_");
  private static final Expr HALF_ANGLE =
      IntegrateRules.template("((1 - cos(2 * u_)) / 2)^i_ * ((1 + cos(2 * u_)) / 2)^j_");

  /** The integrand's {@code u}, {@code m} and {@code n}, an absent factor's exponent 0. */
  private record Shape(Expr u, int m, int n) {}

  private static Optional<Shape> shape(Match match) {
    Expr f = match.bindings().get("f_");
    Symbol x = (Symbol) match.bindings().get("x_");
    for (Pattern p : POWERS) {
      Optional<Bindings> b = Matcher.match(p, f, match.context().assumptions());
      if (b.isEmpty()) {
        continue;
      }
      Expr u = b.get().get("u_");
      int m = b.get().contains("m_") ? exponent(b.get().get("m_")) : 0;
      int n = b.get().contains("n_") ? exponent(b.get().get("n_")) : 0;
      if (m < 0 || n < 0 || IntegrateRules.slope(u, x, match.context().assumptions()).isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(new Shape(u, m, n));
    }
    return Optional.empty();
  }

  /** A small non-negative integer exponent, or −1. */
  private static int exponent(Expr e) {
    return e instanceof Num k
            && k.value() instanceof Int i
            && i.signum() >= 0
            && i.value().compareTo(BigInteger.valueOf(1_000)) <= 0
        ? i.value().intValue()
        : -1;
  }

  /** {@code odd-power}: the odd exponent, the smaller when both are, of at least 3. */
  static Optional<Expr> odd(Match match) {
    return shape(match)
        .flatMap(
            s -> {
              boolean sinOdd = s.m() % 2 == 1;
              boolean cosOdd = s.n() % 2 == 1;
              boolean onSin = sinOdd && (!cosOdd || s.m() <= s.n());
              int k = onSin ? s.m() : s.n();
              if (!(sinOdd || cosOdd)
                  || k < 3
                  || (sinOdd && cosOdd && Math.min(s.m(), s.n()) < 3)) {
                return Optional.empty();
              }
              Bindings b =
                  bind(s.u())
                      .with(new Bindings.Binding("k_", Num.of((k - 1) / 2), null, null))
                      .with(new Bindings.Binding("m_", Num.of(s.m()), null, null))
                      .with(new Bindings.Binding("n_", Num.of(s.n()), null, null));
              return Optional.of(integral(match, b.substituteInto(onSin ? ODD_SIN : ODD_COS)));
            });
  }

  /** {@code half-angle}: both exponents even, not both 0. */
  static Optional<Expr> halfAngle(Match match) {
    return shape(match)
        .flatMap(
            s -> {
              if (s.m() % 2 != 0 || s.n() % 2 != 0 || s.m() + s.n() < 2) {
                return Optional.empty();
              }
              Bindings b =
                  bind(s.u())
                      .with(new Bindings.Binding("i_", Num.of(s.m() / 2), null, null))
                      .with(new Bindings.Binding("j_", Num.of(s.n() / 2), null, null));
              return Optional.of(integral(match, b.substituteInto(HALF_ANGLE)));
            });
  }

  /** {@code product-to-sum}: two sines or cosines of different linear arguments. */
  static Optional<Expr> productToSum(Match match) {
    Expr f = match.bindings().get("f_");
    Symbol x = (Symbol) match.bindings().get("x_");
    for (int k = 0; k < PRODUCTS.size(); k++) {
      Optional<Bindings> b = Matcher.match(PRODUCTS.get(k), f, match.context().assumptions());
      if (b.isEmpty()) {
        continue;
      }
      Expr v = b.get().get("v_");
      Expr w = b.get().get("w_");
      if (v.equals(w)
          || IntegrateRules.slope(v, x, match.context().assumptions()).isEmpty()
          || IntegrateRules.slope(w, x, match.context().assumptions()).isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(integral(match, b.get().substituteInto(PRODUCT_FORMS.get(k))));
    }
    return Optional.empty();
  }

  private static Bindings bind(Expr u) {
    return Bindings.EMPTY.with(new Bindings.Binding("u_", u, null, null));
  }

  private static Expr integral(Match match, Expr integrand) {
    return Integral.indefinite(integrand, (Symbol) match.bindings().get("x_"));
  }
}
