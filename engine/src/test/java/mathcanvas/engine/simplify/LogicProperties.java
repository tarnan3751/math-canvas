package mathcanvas.engine.simplify;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Random;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

/**
 * The rule set {@code logic} is sound and terminates: random formulas in three or four variables
 * over every operator and the constants simplify, within the cap, to formulas with the same truth
 * table, and every rewrite of the trace preserves it.
 */
class LogicProperties {

  private static final List<Symbol> VARS =
      List.of(Symbol.of("p"), Symbol.of("q"), Symbol.of("r"), Symbol.of("s"));

  private static Expr formula(Random random, int depth, int vars) {
    if (depth == 0 || random.nextInt(4) == 0) {
      int k = random.nextInt(vars + 2);
      return k < vars ? VARS.get(k) : Bool.of(k == vars);
    }
    Expr a = formula(random, depth - 1, vars);
    Expr b = formula(random, depth - 1, vars);
    return switch (random.nextInt(6)) {
      case 0 -> Apply.of(Symbol.of("and"), a, b);
      case 1 -> Apply.of(Symbol.of("or"), a, b);
      case 2 -> Apply.of(Symbol.of("xor"), a, b);
      case 3 -> Apply.of(Symbol.of("implies"), a, b);
      default -> Apply.of(Symbol.of("not"), a);
    };
  }

  /** The truth value of a formula under an assignment. */
  static boolean value(Expr e, Map<String, Boolean> at) {
    return switch (e) {
      case Bool b -> b.value();
      case Symbol s -> at.get(s.name());
      case Apply a ->
          switch (a.head().name()) {
            case "and" -> a.args().stream().allMatch(x -> value(x, at));
            case "or" -> a.args().stream().anyMatch(x -> value(x, at));
            case "xor" -> a.args().stream().filter(x -> value(x, at)).count() % 2 == 1;
            case "not" -> !value(a.args().getFirst(), at);
            case "implies" -> !value(a.args().getFirst(), at) || value(a.args().get(1), at);
            default -> throw new IllegalArgumentException("not a formula: " + e);
          };
      default -> throw new IllegalArgumentException("not a formula: " + e);
    };
  }

  private static void sameTable(Expr a, Expr b, int vars) {
    for (int mask = 0; mask < 1 << vars; mask++) {
      Map<String, Boolean> at = new java.util.HashMap<>();
      for (int i = 0; i < vars; i++) {
        at.put(VARS.get(i).name(), (mask >> i & 1) == 1);
      }
      assertThat(value(b, at)).as("%s and %s at %s", a, b, at).isEqualTo(value(a, at));
    }
  }

  @Property(tries = 1000, seed = "8601")
  void everyRewritePreservesTheTruthTable(@ForAll long seed) {
    Random random = new Random(seed);
    int vars = 3 + random.nextInt(2);
    Expr e = Canonical.of(formula(random, 5, vars));
    Rewriter.Result r =
        Simplifier.simplify(e, List.of(RuleSets.LOGIC), Context.DEFAULT.withTrace(true));
    sameTable(e, r.expr(), vars);
    for (var w : r.trace().rewrites()) {
      sameTable(w.before(), w.after(), vars);
    }
  }
}
