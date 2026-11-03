package mathcanvas.engine.simplify;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.rule.Match;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;

/**
 * The rule set {@code logic}: the identities of the boolean operators ({@code docs/language.md}
 * §14.24), each valid for every assignment of truth values. A negation moves inward (double
 * negation, De Morgan's laws, the negation of an implication, of an exclusive or and of a relation,
 * an ordering only between real sides); the constants {@code true} and {@code false} are absorbed
 * or decide the operator; a repeated operand, an operand beside its negation, and an operand
 * absorbing a conjunction or disjunction that holds it are removed. {@code and}, {@code or} and
 * {@code xor} are flat and sorted in canonical form, so their rules match the whole application and
 * look through its operands. Every rule moves a negation into a strictly smaller operand or removes
 * nodes, so the set terminates ({@code docs/reference/engine.md}, <b>Simplification</b>).
 */
final class LogicRules {

  private LogicRules() {}

  private static final Symbol AND = Symbol.of("and");
  private static final Symbol OR = Symbol.of("or");
  private static final Symbol XOR = Symbol.of("xor");
  private static final Symbol NOT = Symbol.of("not");
  private static final Symbol IMPLIES = Symbol.of("implies");

  private static final Symbol P = Symbol.of("p_");
  private static final Symbol Q = Symbol.of("q_");
  private static final Symbol ARGS = Symbol.of("args__");

  private static Apply app(Symbol head, Expr... args) {
    return Apply.of(head, args);
  }

  private static Pattern pattern(Expr e) {
    return Pattern.of(e);
  }

  static final RuleSet RULES =
      new RuleSet(
          "logic",
          List.of(
              // negation
              Rule.of("not-true", pattern(app(NOT, Bool.TRUE)), Bool.FALSE),
              Rule.of("not-false", pattern(app(NOT, Bool.FALSE)), Bool.TRUE),
              Rule.of("double-negation", pattern(app(NOT, app(NOT, P))), P),
              Rule.computed(
                  "de-morgan-and",
                  pattern(app(NOT, app(AND, P, ARGS))),
                  m -> Optional.of(deMorgan(OR, m))),
              Rule.computed(
                  "de-morgan-or",
                  pattern(app(NOT, app(OR, P, ARGS))),
                  m -> Optional.of(deMorgan(AND, m))),
              Rule.of(
                  "not-implies", pattern(app(NOT, app(IMPLIES, P, Q))), app(AND, P, app(NOT, Q))),
              Rule.computed("not-xor", pattern(app(NOT, app(XOR, P, ARGS))), LogicRules::notXor),
              Rule.of(
                  "not-equal",
                  pattern(app(NOT, Relation.of(Relation.Op.EQ, P, Q))),
                  Relation.of(Relation.Op.NE, P, Q)),
              Rule.of(
                  "not-unequal",
                  pattern(app(NOT, Relation.of(Relation.Op.NE, P, Q))),
                  Relation.of(Relation.Op.EQ, P, Q)),
              Rule.computed("not-order", pattern(app(NOT, P)), LogicRules::notOrder),
              // and
              Rule.computed(
                  "and-false", pattern(app(AND, P, ARGS)), m -> annihilated(m, Bool.FALSE)),
              Rule.computed("and-true", pattern(app(AND, P, ARGS)), m -> without(m, Bool.TRUE)),
              Rule.computed("and-idempotent", pattern(app(AND, P, ARGS)), LogicRules::once),
              Rule.computed(
                  "and-complement",
                  pattern(app(AND, P, ARGS)),
                  m -> complement(m) ? Optional.of(Bool.FALSE) : Optional.empty()),
              Rule.computed("absorption-and", pattern(app(AND, P, ARGS)), m -> absorb(AND, OR, m)),
              // or
              Rule.computed("or-true", pattern(app(OR, P, ARGS)), m -> annihilated(m, Bool.TRUE)),
              Rule.computed("or-false", pattern(app(OR, P, ARGS)), m -> without(m, Bool.FALSE)),
              Rule.computed("or-idempotent", pattern(app(OR, P, ARGS)), LogicRules::once),
              Rule.computed(
                  "or-complement",
                  pattern(app(OR, P, ARGS)),
                  m -> complement(m) ? Optional.of(Bool.TRUE) : Optional.empty()),
              Rule.computed("absorption-or", pattern(app(OR, P, ARGS)), m -> absorb(OR, AND, m)),
              // xor
              Rule.computed("xor-false", pattern(app(XOR, P, ARGS)), m -> without(m, Bool.FALSE)),
              Rule.computed("xor-true", pattern(app(XOR, P, ARGS)), LogicRules::xorTrue),
              Rule.computed("xor-self", pattern(app(XOR, P, ARGS)), LogicRules::pairsCancel),
              // implies
              Rule.of("implies-true-premise", pattern(app(IMPLIES, Bool.TRUE, Q)), Q),
              Rule.of("implies-false-premise", pattern(app(IMPLIES, Bool.FALSE, Q)), Bool.TRUE),
              Rule.of("implies-true-conclusion", pattern(app(IMPLIES, P, Bool.TRUE)), Bool.TRUE),
              Rule.of(
                  "implies-false-conclusion", pattern(app(IMPLIES, P, Bool.FALSE)), app(NOT, P)),
              Rule.of("implies-self", pattern(app(IMPLIES, P, P)), Bool.TRUE)));

  /** The operands of the matched application. */
  private static List<Expr> operands(Match m) {
    return ((Apply) m.subject()).args();
  }

  /** {@code c} itself when it is an operand (false in a conjunction, true in a disjunction). */
  private static Optional<Expr> annihilated(Match m, Bool c) {
    return operands(m).contains(c) ? Optional.of(c) : Optional.empty();
  }

  /** The application without its operands equal to {@code c}, an identity. */
  private static Optional<Expr> without(Match m, Bool c) {
    List<Expr> args = operands(m);
    if (!args.contains(c)) {
      return Optional.empty();
    }
    List<Expr> rest = new ArrayList<>(args);
    rest.removeIf(c::equals);
    return Optional.of(Apply.of(((Apply) m.subject()).head(), rest));
  }

  /** The application with each repeated operand once; sorted, repeats are neighbours. */
  private static Optional<Expr> once(Match m) {
    List<Expr> args = operands(m);
    List<Expr> rest = new ArrayList<>();
    for (Expr a : args) {
      if (rest.isEmpty() || !rest.getLast().equals(a)) {
        rest.add(a);
      }
    }
    return rest.size() == args.size()
        ? Optional.empty()
        : Optional.of(Apply.of(((Apply) m.subject()).head(), rest));
  }

  /** {@code p ⊕ q ⊕ p} is {@code q}: operands that occur twice cancel. */
  private static Optional<Expr> pairsCancel(Match m) {
    List<Expr> args = operands(m);
    List<Expr> rest = new ArrayList<>();
    for (Expr a : args) {
      if (!rest.isEmpty() && rest.getLast().equals(a)) {
        rest.removeLast();
      } else {
        rest.add(a);
      }
    }
    return rest.size() == args.size() ? Optional.empty() : Optional.of(Apply.of(XOR, rest));
  }

  /** {@code true ⊕ q ⊕ …} is {@code ¬(q ⊕ …)}. */
  private static Optional<Expr> xorTrue(Match m) {
    List<Expr> args = operands(m);
    if (!args.contains(Bool.TRUE)) {
      return Optional.empty();
    }
    List<Expr> rest = new ArrayList<>(args);
    rest.remove(Bool.TRUE);
    return Optional.of(app(NOT, Apply.of(XOR, rest)));
  }

  /** {@code ¬(p ⊕ q ⊕ …)} is {@code ¬p ⊕ q ⊕ …}. */
  private static Optional<Expr> notXor(Match m) {
    List<Expr> args = new ArrayList<>(((Apply) operands(m).getFirst()).args());
    args.set(0, app(NOT, args.getFirst()));
    return Optional.of(Apply.of(XOR, args));
  }

  /** {@code ¬(a ∘ b ∘ …)} as {@code ¬a ∘' ¬b ∘' …}. */
  private static Expr deMorgan(Symbol dual, Match m) {
    Apply inner = (Apply) ((Apply) m.subject()).args().getFirst();
    List<Expr> negated = new ArrayList<>();
    for (Expr operand : inner.args()) {
      negated.add(app(NOT, operand));
    }
    return Apply.of(dual, negated);
  }

  /** {@code ¬(a < b)} as {@code a ≥ b}, and so on, when both sides are real. */
  private static Optional<Expr> notOrder(Match m) {
    if (!(((Apply) m.subject()).args().getFirst() instanceof Relation r)) {
      return Optional.empty();
    }
    Relation.Op flipped =
        switch (r.op()) {
          case LT -> Relation.Op.GE;
          case LE -> Relation.Op.GT;
          case GT -> Relation.Op.LE;
          case GE -> Relation.Op.LT;
          default -> null;
        };
    if (flipped == null
        || m.context().assumptions().isReal(r.left()) != Tri.TRUE
        || m.context().assumptions().isReal(r.right()) != Tri.TRUE) {
      return Optional.empty();
    }
    return Optional.of(Relation.of(flipped, r.left(), r.right()));
  }

  /** Whether the operands hold some {@code p} and {@code ¬p}. */
  private static boolean complement(Match m) {
    List<Expr> args = ((Apply) m.subject()).args();
    for (Expr a : args) {
      if (a instanceof Apply n && n.head().equals(NOT) && args.contains(n.args().getFirst())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Absorption: in {@code p ∘ (p ∘' q) ∘ …} the operand {@code (p ∘' q)} goes, since {@code p}
   * decides it.
   */
  private static Optional<Expr> absorb(Symbol head, Symbol dual, Match m) {
    List<Expr> args = ((Apply) m.subject()).args();
    for (int i = 0; i < args.size(); i++) {
      if (args.get(i) instanceof Apply inner && inner.head().equals(dual)) {
        for (int j = 0; j < args.size(); j++) {
          if (j != i && inner.args().contains(args.get(j))) {
            List<Expr> rest = new ArrayList<>(args);
            rest.remove(i);
            return Optional.of(Apply.of(head, rest));
          }
        }
      }
    }
    return Optional.empty();
  }
}
