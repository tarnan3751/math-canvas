package mathcanvas.engine.testing;

import java.util.Collection;
import java.util.Random;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Relation.Op;
import mathcanvas.engine.expr.Symbol;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

/**
 * Random facts for the rule sets' properties: each symbol gets none (most often), a sign ({@code x
 * > 0}, {@code x >= 0}, {@code x < 0}, {@code x <= 0}), an integer domain, a parity, or the complex
 * domain.
 */
public final class RandomAssumptions {

  private RandomAssumptions() {}

  /**
   * Returns random assumptions about {@code symbols}.
   *
   * @param symbols the symbols
   * @return the generator
   */
  public static Arbitrary<Assumptions> about(Collection<Symbol> symbols) {
    return Arbitraries.randomValue(random -> draw(symbols, random));
  }

  /**
   * Draws assumptions about {@code symbols}.
   *
   * @param symbols the symbols
   * @param random the source
   * @return the assumptions
   */
  public static Assumptions draw(Collection<Symbol> symbols, Random random) {
    Assumptions a = Assumptions.EMPTY;
    for (Symbol s : symbols) {
      Relation r =
          switch (random.nextInt(12)) {
            case 0 -> Relation.of(Op.GT, s, Num.ZERO);
            case 1 -> Relation.of(Op.GE, s, Num.ZERO);
            case 2 -> Relation.of(Op.LT, s, Num.ZERO);
            case 3 -> Relation.of(Op.LE, s, Num.ZERO);
            case 4 -> Relation.of(Op.IN, s, Constant.INTEGERS);
            case 5 -> parity(s, 0);
            case 6 -> parity(s, 1);
            case 7 -> Relation.of(Op.IN, s, Constant.COMPLEXES);
            default -> null;
          };
      if (r != null) {
        a = a.with(r);
      }
    }
    return a;
  }

  private static Relation parity(Symbol s, int remainder) {
    return Relation.of(Op.EQ, Apply.of(Symbol.of("mod"), s, Num.of(2)), Num.of(remainder));
  }
}
