package mathcanvas.engine.print;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import java.util.Set;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Parser;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

/**
 * The Phase 8a operators read back (§15.4): random trees of the boolean operators over names, truth
 * values, relations and memberships, and of the set operators over names and sets, print in the
 * ASCII and the Unicode printers to text that parses to the same canonical tree.
 */
class OperatorRoundTripProperties {

  private static Expr app(String head, Expr... args) {
    return Apply.of(Symbol.of(head), args);
  }

  private static Expr set(Random r, int depth) {
    if (depth == 0 || r.nextInt(3) == 0) {
      return r.nextBoolean()
          ? Symbol.of(r.nextBoolean() ? "A" : "B")
          : SetExpr.of(Num.of(r.nextInt(3)), Symbol.of("x"));
    }
    String head = new String[] {"union", "intersect", "setdiff"}[r.nextInt(3)];
    return app(head, set(r, depth - 1), set(r, depth - 1));
  }

  private static Expr formula(Random r, int depth) {
    if (depth == 0 || r.nextInt(4) == 0) {
      return switch (r.nextInt(5)) {
        case 0 -> Symbol.of("p");
        case 1 -> Symbol.of("q");
        case 2 -> Bool.of(r.nextBoolean());
        case 3 -> Relation.of(Relation.Op.LT, Symbol.of("x"), Sum.of(Symbol.of("y"), Num.ONE));
        default -> Relation.of(Relation.Op.IN, Symbol.of("x"), set(r, 2));
      };
    }
    return switch (r.nextInt(5)) {
      case 0 -> app("and", formula(r, depth - 1), formula(r, depth - 1));
      case 1 -> app("or", formula(r, depth - 1), formula(r, depth - 1));
      case 2 -> app("xor", formula(r, depth - 1), formula(r, depth - 1));
      case 3 -> app("implies", formula(r, depth - 1), formula(r, depth - 1));
      default -> app("not", formula(r, depth - 1));
    };
  }

  @Property(tries = 500, seed = "8602")
  void randomTreesReadBack(@ForAll long seed) {
    Expr c = Canonical.of(formula(new Random(seed), 4));
    for (Printer printer : new Printer[] {AsciiPrinter.INSTANCE, UnicodePrinter.INSTANCE}) {
      String text = printer.display(c);
      assertThat(Canonical.of(Parser.parseExpression(text, Set.<String>of()::contains)))
          .as(text)
          .isEqualTo(c);
    }
  }
}
