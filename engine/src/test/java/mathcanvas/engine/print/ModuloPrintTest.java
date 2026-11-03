package mathcanvas.engine.print;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import java.util.Set;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Parser;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

/**
 * The infix {@code mod} (§9.5): every printer writes it infix, with the parentheses §15.2 states,
 * and random trees mixing it with sums, products, quotients, powers and signs read back.
 */
class ModuloPrintTest {

  private static final Symbol A = Symbol.of("a");
  private static final Symbol B = Symbol.of("b");
  private static final Symbol X = Symbol.of("x");

  private static Expr mod(Expr a, Expr b) {
    return Apply.of(Symbol.of("mod"), a, b);
  }

  private static void prints(Expr raw, String ascii, String unicode, String latex) {
    Expr c = Canonical.of(raw);
    assertThat(AsciiPrinter.INSTANCE.display(c)).isEqualTo(ascii);
    assertThat(UnicodePrinter.INSTANCE.display(c)).isEqualTo(unicode);
    assertThat(LatexPrinter.INSTANCE.display(c)).isEqualTo(latex);
  }

  @Test
  void theOperatorAndItsParentheses() {
    prints(mod(A, B), "a mod b", "a mod b", "a \\bmod b");
    prints(mod(Product.of(Num.of(2), X), Num.of(3)), "2*x mod 3", "2x mod 3", "2x \\bmod 3");
    prints(Product.of(X, mod(A, B)), "x*(a mod b)", "x·(a mod b)", "x(a \\bmod b)");
    prints(Exprs.neg(mod(A, B)), "-(a mod b)", "-(a mod b)", "-(a \\bmod b)");
    prints(mod(Sum.of(A, Num.ONE), B), "(a + 1) mod b", "(a + 1) mod b", "(a+1) \\bmod b");
    prints(mod(A, Product.of(Num.of(2), B)), "a mod (2*b)", "a mod (2b)", "a \\bmod (2b)");
    prints(mod(A, Exprs.neg(B)), "a mod (-b)", "a mod (-b)", "a \\bmod (-b)");
    prints(mod(mod(A, B), X), "a mod b mod x", "a mod b mod x", "a \\bmod b \\bmod x");
    prints(mod(A, mod(B, X)), "a mod (b mod x)", "a mod (b mod x)", "a \\bmod (b \\bmod x)");
    prints(Power.of(mod(A, B), Num.of(2)), "(a mod b)^2", "(a mod b)²", "(a \\bmod b)^{2}");
    prints(Sum.of(X, Exprs.neg(mod(A, B))), "x - a mod b", "x - a mod b", "x-a \\bmod b");
  }

  /** A random tree of sums, products, quotients, powers, signs and {@code mod}. */
  private static Expr tree(Random random, int depth) {
    if (depth == 0) {
      return switch (random.nextInt(4)) {
        case 0 -> A;
        case 1 -> B;
        case 2 -> X;
        default -> Num.of(random.nextInt(7) - 2);
      };
    }
    Expr l = tree(random, random.nextInt(depth));
    Expr r = tree(random, random.nextInt(depth));
    return switch (random.nextInt(6)) {
      case 0, 1 -> mod(l, r);
      case 2 -> Sum.of(l, r);
      case 3 -> Product.of(l, r);
      case 4 -> Product.of(l, Exprs.inv(r));
      default -> random.nextBoolean() ? Exprs.neg(l) : Power.of(l, Num.of(2));
    };
  }

  @Property(tries = 500, seed = "8201")
  void randomTreesReadBack(@ForAll long seed) {
    Expr c = Canonical.of(tree(new Random(seed), 4));
    for (Printer printer : new Printer[] {AsciiPrinter.INSTANCE, UnicodePrinter.INSTANCE}) {
      String text = printer.display(c);
      assertThat(Canonical.of(Parser.parseExpression(text, Set.<String>of()::contains)))
          .as(text)
          .isEqualTo(c);
    }
  }
}
