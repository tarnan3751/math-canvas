package mathcanvas.engine.algebra;

import java.util.List;
import java.util.Optional;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.rule.Match;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;

/**
 * The rule set {@code expand}: products of sums are distributed and positive integer powers of sums
 * multiplied out, everywhere in the expression, function arguments included. Each rule's right-hand
 * side is the matched node converted to a {@link Polynomial} in its kernels and back, so one
 * rewrite expands the whole node; the rewriter canonicalises it at the context's precision. A node
 * whose expansion would have more than {@link Polynomial#MAX_TERMS} terms is left as it is, and so
 * is one with a factor that does not commute or a node that is not a scalar kernel ({@code ∞}, a
 * relation, a list, …; {@link Polynomial#of(Expr)}).
 *
 * <p>It terminates: an expanded node is a sum of products of kernels, and a kernel is never a sum,
 * so the rules match it again only when the canonical form of the result turns a kernel's power
 * back into a sum ({@code (u^(1/2))²} is {@code u}), which the next rewrite expands.
 */
public final class ExpandRules {

  /** The rule set: {@code binomial-theorem}, {@code expand-power}, {@code distribute}. */
  public static final RuleSet RULES =
      new RuleSet(
          "expand",
          List.of(
              Rule.computed(
                  "binomial-theorem",
                  Pattern.parse("(a_ + b_)^n_").where("n_", ExpandRules::positiveInteger),
                  ExpandRules::expand),
              Rule.computed(
                  "expand-power",
                  Pattern.parse("s_^n_")
                      .where("s_", s -> s instanceof Sum)
                      .where("n_", ExpandRules::positiveInteger),
                  ExpandRules::expand),
              Rule.computed(
                  "distribute",
                  Pattern.parse("s_ * rest__").where("s_", s -> s instanceof Sum),
                  ExpandRules::expand)));

  private ExpandRules() {}

  private static boolean positiveInteger(Expr e) {
    return e instanceof Num n && n.value() instanceof Int i && i.signum() > 0;
  }

  private static Optional<Expr> expand(Match m) {
    try {
      return Optional.of(Polynomial.of(m.subject()).raw());
    } catch (IllegalArgumentException | ArithmeticException | AlgebraLimitException refused) {
      return Optional.empty();
    }
  }
}
