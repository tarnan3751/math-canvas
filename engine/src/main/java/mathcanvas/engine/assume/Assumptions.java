package mathcanvas.engine.assume;

import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import mathcanvas.engine.assume.Facts.Bound;
import mathcanvas.engine.assume.Facts.Domain;
import mathcanvas.engine.assume.Facts.Parity;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.print.UnicodePrinter;

/**
 * An immutable context of facts about symbols, recorded from relations and queried by rules.
 *
 * <p>A symbol is real unless it is assumed complex ({@code z ∈ ℂ}). {@link #with} accepts the forms
 * of {@code docs/language.md} §14.6 with a symbol on the left: {@code x > c}, {@code x >= c},
 * {@code x < c}, {@code x <= c} for a real number {@code c}; {@code x != 0}; {@code x ∈ ℤ|ℚ|ℝ|ℂ};
 * {@code mod(x, 2) = 0} or {@code = 1}. A bound implies real, a parity integer. The facts combine
 * by tightening. {@code x ∈ ℂ} is the one fact that widens: it is accepted only while nothing
 * recorded about {@code x} says it is real (no domain of ℝ, ℚ or ℤ, no bound, no parity; {@code x
 * != 0} may stand), and a bound or a parity on a complex symbol contradicts it until a domain of ℝ,
 * ℚ or ℤ narrows the symbol back. A relation that contradicts what is recorded throws.
 *
 * @param bySymbol the facts per symbol name
 * @param recorded the relations in force, canonical, in the order recorded
 */
public record Assumptions(SortedMap<String, Facts> bySymbol, List<Relation> recorded) {

  /** No facts: every symbol is real and nothing more is known. */
  public static final Assumptions EMPTY = new Assumptions(new TreeMap<>(), List.of());

  private static final Symbol MOD = Symbol.of("mod");

  /**
   * Copies the facts and the relations, and checks that the facts are what recording the relations
   * in order gives, so no instance escapes the checks of {@link #with}.
   *
   * @throws NullPointerException if an argument is null
   * @throws IllegalArgumentException if a relation is not an accepted form, contradicts the ones
   *     before it, or the facts or relations differ from what recording them gives
   */
  public Assumptions {
    bySymbol = Collections.unmodifiableSortedMap(new TreeMap<>(bySymbol));
    recorded = List.copyOf(recorded);
    State replayed = State.EMPTY;
    for (Relation r : recorded) {
      replayed = replayed.add(r);
    }
    if (!replayed.facts().equals(bySymbol) || !replayed.relations().equals(recorded)) {
      throw new IllegalArgumentException(
          "the facts " + bySymbol + " do not follow from the relations " + recorded);
    }
  }

  /**
   * Returns these assumptions with the fact {@code relation} states recorded.
   *
   * @param relation the relation, in one of the accepted forms
   * @return the assumptions with the fact
   * @throws IllegalArgumentException if the relation is not an accepted form, or contradicts what
   *     is recorded
   */
  public Assumptions with(Relation relation) {
    State state = new State(bySymbol, recorded).add(relation);
    return new Assumptions(state.facts(), state.relations());
  }

  /**
   * Returns these assumptions without the facts about {@code x}: what holds inside a binder that
   * rebinds it.
   *
   * @param x the symbol
   * @return the assumptions with no relation about {@code x}
   */
  public Assumptions without(Symbol x) {
    if (!bySymbol.containsKey(x.name())) {
      return this;
    }
    TreeMap<String, Facts> facts = new TreeMap<>(bySymbol);
    facts.remove(x.name());
    List<Relation> relations = new ArrayList<>();
    for (Relation r : recorded) {
      if (!subject(r).equals(x)) {
        relations.add(r);
      }
    }
    return new Assumptions(facts, relations);
  }

  /**
   * Returns the facts recorded about {@code x}, {@link Facts#NONE} when there are none.
   *
   * @param x the symbol
   * @return its facts
   */
  public Facts facts(Symbol x) {
    return bySymbol.getOrDefault(x.name(), Facts.NONE);
  }

  /**
   * Returns the relations in force as a set, for {@code assumptions()} in the language.
   *
   * @return the canonical set of the relations
   */
  public SetExpr relations() {
    return (SetExpr) Canonical.of(new SetExpr(new ArrayList<>(recorded)));
  }

  /**
   * Whether {@code e} is real.
   *
   * @param e the expression
   * @return the answer
   */
  public Tri isReal(Expr e) {
    return Inference.of(e, this).real();
  }

  /**
   * Whether {@code e} is an integer.
   *
   * @param e the expression
   * @return the answer
   */
  public Tri isInteger(Expr e) {
    return Inference.of(e, this).integer();
  }

  /**
   * Whether {@code e} is rational.
   *
   * @param e the expression
   * @return the answer
   */
  public Tri isRational(Expr e) {
    return Inference.of(e, this).rational();
  }

  /**
   * Whether {@code e} is a positive real.
   *
   * @param e the expression
   * @return the answer
   */
  public Tri isPositive(Expr e) {
    return Inference.of(e, this).signIn(Inference.POS);
  }

  /**
   * Whether {@code e} is a negative real.
   *
   * @param e the expression
   * @return the answer
   */
  public Tri isNegative(Expr e) {
    return Inference.of(e, this).signIn(Inference.NEG);
  }

  /**
   * Whether {@code e} is a non-negative real.
   *
   * @param e the expression
   * @return the answer
   */
  public Tri isNonNegative(Expr e) {
    return Inference.of(e, this).signIn(Inference.ZERO | Inference.POS);
  }

  /**
   * Whether {@code e} is a non-positive real.
   *
   * @param e the expression
   * @return the answer
   */
  public Tri isNonPositive(Expr e) {
    return Inference.of(e, this).signIn(Inference.NEG | Inference.ZERO);
  }

  /**
   * Whether {@code e} is zero.
   *
   * @param e the expression
   * @return the answer
   */
  public Tri isZero(Expr e) {
    return Inference.of(e, this).zero();
  }

  /**
   * Whether {@code e} is not zero.
   *
   * @param e the expression
   * @return the answer
   */
  public Tri isNonZero(Expr e) {
    return Inference.of(e, this).zero().not();
  }

  /**
   * Whether {@code e} is an even integer.
   *
   * @param e the expression
   * @return the answer
   */
  public Tri isEven(Expr e) {
    return Inference.of(e, this).parityIs(Parity.EVEN);
  }

  /**
   * Whether {@code e} is an odd integer.
   *
   * @param e the expression
   * @return the answer
   */
  public Tri isOdd(Expr e) {
    return Inference.of(e, this).parityIs(Parity.ODD);
  }

  private static Domain domainOf(Relation r) {
    Expr d = r.right();
    if (d instanceof Constant c) {
      switch (c.kind()) {
        case INTEGERS -> {
          return Domain.INTEGERS;
        }
        case RATIONALS -> {
          return Domain.RATIONALS;
        }
        case REALS -> {
          return Domain.REALS;
        }
        case COMPLEXES -> {
          return Domain.COMPLEXES;
        }
        default -> {}
      }
    }
    throw new IllegalArgumentException("`assume` takes x ∈ ℤ, ℚ, ℝ or ℂ, not " + show(r));
  }

  /** The tighter of two bounds; {@code direction} is 1 for lower bounds, -1 for upper bounds. */
  private static Bound tighter(Bound old, Bound b, int direction) {
    if (old == null) {
      return b;
    }
    int order = b.value().compareValue(old.value()) * direction;
    if (order != 0) {
      return order > 0 ? b : old;
    }
    return old.strict() ? old : b;
  }

  /** The facts and relations while they are recorded, one relation at a time. */
  private record State(SortedMap<String, Facts> facts, List<Relation> relations) {

    private static final State EMPTY = new State(new TreeMap<>(), List.of());

    State add(Relation relation) {
      Expr canonical = Canonical.of(relation);
      if (!(canonical instanceof Relation r)) {
        throw new IllegalArgumentException(
            "not an assumption about a symbol: " + UnicodePrinter.INSTANCE.print(relation));
      }
      if (r.op() == Relation.Op.EQ && r.left() instanceof Apply a && a.head().equals(MOD)) {
        return parity(r, a);
      }
      if (!(r.left() instanceof Symbol x)) {
        throw new IllegalArgumentException(
            "an assumption needs a name on its left, as in `x > 0`, not " + show(r));
      }
      Facts old = facts.getOrDefault(x.name(), Facts.NONE);
      Facts updated =
          switch (r.op()) {
            case IN -> domain(r, old, domainOf(r));
            case NE -> {
              if (!(r.right() instanceof Num n) || !n.value().isZero()) {
                throw new IllegalArgumentException(
                    "`assume` takes `x != 0` only with 0 on the right, not " + show(r));
              }
              yield new Facts(old.domain(), old.lower(), old.upper(), old.parity(), true);
            }
            case LT, LE, GT, GE -> bound(r, old);
            case EQ ->
                throw new IllegalArgumentException(
                    "`assume` cannot take an equation such as "
                        + show(r)
                        + ": define the value with `:=` instead");
          };
      return record(x, r, updated);
    }

    private State parity(Relation r, Apply mod) {
      if (mod.args().size() != 2
          || !(mod.args().get(0) instanceof Symbol x)
          || !mod.args().get(1).equals(Num.of(2))
          || !(r.right().equals(Num.ZERO) || r.right().equals(Num.ONE))) {
        throw new IllegalArgumentException(
            "an assumption about evenness is `mod(x, 2) = 0` or `mod(x, 2) = 1`, not " + show(r));
      }
      Facts old = facts.getOrDefault(x.name(), Facts.NONE);
      Parity parity = r.right().equals(Num.ZERO) ? Parity.EVEN : Parity.ODD;
      if (old.domain() == Domain.COMPLEXES) {
        throw contradiction(r, x);
      }
      if (old.parity() != Parity.NONE && old.parity() != parity) {
        throw contradiction(r, x);
      }
      Facts updated = new Facts(Domain.INTEGERS, old.lower(), old.upper(), parity, old.nonZero());
      return record(x, r, updated);
    }

    private Facts domain(Relation r, Facts old, Domain domain) {
      Symbol x = (Symbol) r.left();
      if (domain == Domain.COMPLEXES) {
        boolean declaredReal =
            relations.stream()
                .anyMatch(
                    o ->
                        o.op() == Relation.Op.IN
                            && o.left().equals(x)
                            && domainOf(o) != Domain.COMPLEXES);
        if (declaredReal
            || old.lower() != null
            || old.upper() != null
            || old.parity() != Parity.NONE) {
          throw contradiction(r, x);
        }
        return new Facts(Domain.COMPLEXES, null, null, Parity.NONE, old.nonZero());
      }
      Domain narrowed =
          old.domain() == Domain.COMPLEXES || domain.compareTo(old.domain()) < 0
              ? domain
              : old.domain();
      return new Facts(narrowed, old.lower(), old.upper(), old.parity(), old.nonZero());
    }

    private Facts bound(Relation r, Facts old) {
      Symbol x = (Symbol) r.left();
      if (!(r.right() instanceof Num n) || !(n.value() instanceof RealNumeric c)) {
        throw new IllegalArgumentException(
            "an assumption's bound must be a real number, not " + show(r));
      }
      if (old.domain() == Domain.COMPLEXES) {
        throw contradiction(r, x);
      }
      boolean strict = r.op() == Relation.Op.LT || r.op() == Relation.Op.GT;
      Bound b = new Bound(c, strict);
      Bound lower = old.lower();
      Bound upper = old.upper();
      if (r.op() == Relation.Op.GT || r.op() == Relation.Op.GE) {
        lower = tighter(lower, b, 1);
      } else {
        upper = tighter(upper, b, -1);
      }
      return new Facts(old.domain(), lower, upper, old.parity(), old.nonZero());
    }

    private State record(Symbol x, Relation r, Facts updated) {
      if (!consistent(updated)) {
        throw contradiction(r, x);
      }
      TreeMap<String, Facts> updatedFacts = new TreeMap<>(facts);
      updatedFacts.put(x.name(), updated);
      List<Relation> updatedRelations = new ArrayList<>();
      for (Relation old : relations) {
        // A domain that narrows a complex symbol back drops its x ∈ ℂ.
        boolean replaced =
            r.op() == Relation.Op.IN
                && old.op() == Relation.Op.IN
                && old.left().equals(x)
                && domainOf(old) == Domain.COMPLEXES;
        if (!replaced && !old.equals(r)) {
          updatedRelations.add(old);
        }
      }
      updatedRelations.add(r);
      return new State(updatedFacts, updatedRelations);
    }

    private IllegalArgumentException contradiction(Relation r, Symbol x) {
      List<Relation> about = new ArrayList<>();
      for (Relation old : relations) {
        if (subject(old).equals(x)) {
          about.add(old);
        }
      }
      return new IllegalArgumentException(
          show(r)
              + " contradicts what is already assumed: "
              + String.join(", ", about.stream().map(Assumptions::show).toList()));
    }
  }

  /** A canonical relation as the user would write it, quoted for a message. */
  private static String show(Expr r) {
    return "`" + UnicodePrinter.INSTANCE.display(r) + "`";
  }

  /** The symbol a recorded relation is about. */
  private static Expr subject(Relation r) {
    return r.left() instanceof Apply a && a.head().equals(MOD) ? a.args().get(0) : r.left();
  }

  /** Whether some value satisfies every fact. */
  private static boolean consistent(Facts f) {
    Bound lo = f.lower();
    Bound hi = f.upper();
    if (lo != null && hi != null) {
      int order = lo.value().compareValue(hi.value());
      if (order > 0 || (order == 0 && (lo.strict() || hi.strict()))) {
        return false;
      }
      if (order == 0 && f.nonZero() && lo.value().isZero()) {
        return false;
      }
    }
    if (f.domain() != Domain.INTEGERS || (lo == null && hi == null)) {
      return true;
    }
    // The integers in the window, adjusted to the parity, must not run out.
    BigInteger first = lo == null ? null : integerAbove(lo);
    BigInteger last = hi == null ? null : integerBelow(hi);
    if (f.parity() != Parity.NONE) {
      int want = f.parity() == Parity.ODD ? 1 : 0;
      if (first != null && first.mod(BigInteger.TWO).intValue() != want) {
        first = first.add(BigInteger.ONE);
      }
      if (last != null && last.mod(BigInteger.TWO).intValue() != want) {
        last = last.subtract(BigInteger.ONE);
      }
    }
    if (first == null || last == null) {
      return true;
    }
    int order = first.compareTo(last);
    if (order > 0) {
      return false;
    }
    // A single admissible integer must not be the excluded zero.
    return !(order == 0 && f.nonZero() && first.signum() == 0);
  }

  private static BigInteger integerAbove(Bound b) {
    BigInteger floor = floor(b.value());
    boolean integral = b.value().isInteger();
    return integral && !b.strict() ? floor : floor.add(BigInteger.ONE);
  }

  private static BigInteger integerBelow(Bound b) {
    BigInteger floor = floor(b.value());
    boolean integral = b.value().isInteger();
    return integral && b.strict() ? floor.subtract(BigInteger.ONE) : floor;
  }

  private static BigInteger floor(RealNumeric v) {
    return switch (v) {
      case Int i -> i.value();
      case Rational q -> q.num().divide(q.den()).subtract(negativeRemainder(q));
      case Real r -> r.value().setScale(0, RoundingMode.FLOOR).toBigIntegerExact();
    };
  }

  private static BigInteger negativeRemainder(Rational q) {
    // BigInteger division truncates; a negative non-integer quotient needs one more step down.
    return q.num().signum() < 0 ? BigInteger.ONE : BigInteger.ZERO;
  }
}
