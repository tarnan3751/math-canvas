package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.print.AsciiPrinter;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;

/**
 * Random equations of every {@code dsolve} class, with random rational coefficients: every solution
 * returned satisfies the equation, which the test checks itself by substituting the solution and
 * its derivatives into the equation's text and simplifying, or else at random points with random
 * values of the constants; every solution satisfies the initial conditions given, at equilibria
 * too; and one condition on a first-order equation never gives {@code {}}.
 */
class DsolveProperties {

  private static final String[] SMALL = {"1", "2", "3", "-1", "-2", "1/2", "-3/2", "5"};
  private static final String[] FORCING = {
    "0", "1", "x", "x^2", "e^(2x)", "e^(-x)", "sin(x)", "cos(3x)", "x e^x", "e^x sin(x)"
  };

  /**
   * An equation, its unknowns, its conditions, and for one first-order equation its right side
   * {@code f(x, Y)} with {@code y' = f}, for an implicit solution's check.
   */
  record Case(String kind, String equations, String unknowns, String conditions, String f) {}

  @Provide
  Arbitrary<Case> cases() {
    return Arbitraries.randomValue(DsolveProperties::generate);
  }

  static Case generate(Random r) {
    String a = pick(r, SMALL);
    String b = pick(r, SMALL);
    return switch (r.nextInt(7)) {
      case 0 -> {
        String p = pick(r, new String[] {a, a + "/x", a + " x", "2x/(x^2 + 1)"});
        String q = pick(r, new String[] {b, b + " x", b + " e^(" + a + " x)", "x^2", "sin(x)"});
        yield new Case(
            "linear",
            "y'(x) + (" + p + ") y(x) = " + q,
            "y(x)",
            r.nextBoolean() ? "" : ", y(1) = " + pick(r, new String[] {b, "0.75", "-1.5"}),
            "(" + q + ") - (" + p + ") Y");
      }
      case 1 -> {
        String g = pick(r, new String[] {"x", a, a + " x^2", "cos(x)", "e^x", "1/x"});
        String h = pick(r, new String[] {"Y", "Y^2", "1 + Y^2", a + " Y + " + b, "Y^3"});
        yield new Case(
            "separable",
            "y'(x) = (" + g + ")(" + h.replace("Y", "y(x)") + ")",
            "y(x)",
            r.nextBoolean()
                ? ""
                : ", y(0) = "
                    + pick(r, new String[] {"1", "2", "1/2", "0", "-1", "-" + b + "/" + a}),
            "(" + g + ")(" + h + ")");
      }
      case 2 -> {
        String n = pick(r, new String[] {"2", "3", "-1"});
        String p = pick(r, new String[] {a, a + " x"});
        String q = pick(r, new String[] {b, b + " x", "e^x"});
        yield new Case(
            "bernoulli",
            "y'(x) = (" + p + ") y(x) + (" + q + ") y(x)^" + n,
            "y(x)",
            r.nextBoolean()
                ? ""
                : ", y(0) = " + pick(r, new String[] {"0", "1", "-1", "1/2", "0.5"}),
            "(" + p + ") Y + (" + q + ") Y^" + n);
      }
      case 3 -> {
        // Φ = c₁ x² Y + c₂ Y³ + c₃ x Y + c₄ x³: M = Φₓ, N = Φ_Y
        String c1 = pick(r, SMALL);
        String c2 = pick(r, SMALL);
        String c3 = pick(r, SMALL);
        String c4 = pick(r, SMALL);
        String m = "2(" + c1 + ") x Y + (" + c3 + ") Y + 3(" + c4 + ") x^2";
        String nn = "(" + c1 + ") x^2 + 3(" + c2 + ") Y^2 + (" + c3 + ") x";
        yield new Case(
            "exact",
            (m + " + (" + nn + ") y'(x) = 0").replace("Y", "y(x)"),
            "y(x)",
            "",
            "-(" + m + ")/(" + nn + ")");
      }
      case 4, 5 -> {
        int order = 2 + r.nextInt(3);
        List<String> factors = new ArrayList<>();
        int left = order;
        while (left > 0) {
          if (left >= 2 && r.nextInt(3) == 0) {
            factors.add("((r - " + pick(r, SMALL) + ")^2 + " + (1 + r.nextInt(4)) + ")");
            left -= 2;
          } else {
            factors.add("(r - " + pick(r, new String[] {"0", "1", "-1", "2", "-3"}) + ")");
            left -= 1;
          }
        }
        String poly = String.join(" ", factors);
        String forcing = pick(r, FORCING);
        StringBuilder conditions = new StringBuilder();
        if (r.nextInt(3) == 0) {
          for (int j = 0; j < order; j++) {
            conditions.append(", y").append("'".repeat(j)).append("(0) = ").append(pick(r, SMALL));
          }
        }
        yield new Case("constant", poly + " = " + forcing, "y(x)", conditions.toString(), null);
      }
      default -> {
        String[] e = {"0", "1", "-1", "2", "-2", "3"};
        String forcing = r.nextInt(3) == 0 ? " + " + pick(r, SMALL) : "";
        yield new Case(
            "system",
            "[x'(t) = ("
                + pick(r, e)
                + ") x(t) + ("
                + pick(r, e)
                + ") y(t)"
                + forcing
                + ", y'(t) = ("
                + pick(r, e)
                + ") x(t) + ("
                + pick(r, e)
                + ") y(t)]",
            "[x(t), y(t)]",
            r.nextBoolean() ? "" : ", x(0) = 1, y(0) = " + pick(r, SMALL),
            null);
      }
    };
  }

  private static String pick(Random r, String[] from) {
    return from[r.nextInt(from.length)];
  }

  @Property(tries = 150)
  void everySolutionSatisfiesTheEquation(@ForAll("cases") Case c) {
    String equations = equations(c);
    Outcome o = Run.last("dsolve(" + equations + ", " + c.unknowns() + c.conditions() + ")");
    if (o instanceof Outcome.Failed f) {
      throw new AssertionError(c + " failed: " + f.error().getMessage(), f.error());
    }
    Expr value = ((Outcome.Value) o).value();
    boolean solved = !(value instanceof Apply);
    if (Set.of("linear", "separable", "bernoulli").contains(c.kind())
        && !c.conditions().isEmpty()) {
      // one regular condition on a first-order equation always has a local solution
      assertThat(value).as(c.toString()).isNotEqualTo(new SetExpr(List.of()));
    }
    Statistics.label(c.kind()).collect(solved);
    if (!solved) {
      return;
    }
    List<Expr> solutions = value instanceof SetExpr s ? s.items() : List.of(value);
    for (Expr solution : solutions) {
      checkSolution(c, equations, solution);
    }
  }

  /** The equations of a case: a constant-coefficient one written out from its polynomial. */
  private static String equations(Case c) {
    if (!c.kind().equals("constant")) {
      return c.equations();
    }
    String[] sides = c.equations().split(" = ");
    Expr poly = Run.value("expand(" + sides[0] + ")");
    // Σ aⱼ rʲ as Σ aⱼ y⁽ʲ⁾(x)
    List<String> terms = new ArrayList<>();
    String p = AsciiPrinter.INSTANCE.print(poly);
    for (int j = 0; j <= 8; j++) {
      String coefficient =
          j == 0
              ? "subs(" + p + ", r = 0)"
              : "subs(diff(" + p + ", r, " + j + "), r = 0)/" + factorial(j);
      Expr aj = Run.value(coefficient);
      if (!aj.equals(Num.ZERO)) {
        terms.add("(" + AsciiPrinter.INSTANCE.print(aj) + ") y" + "'".repeat(j) + "(x)");
      }
    }
    return String.join(" + ", terms) + " = " + sides[1];
  }

  private static long factorial(int j) {
    long f = 1;
    for (int k = 2; k <= j; k++) {
      f *= k;
    }
    return f;
  }

  private static void checkSolution(Case c, String equations, Expr solution) {
    List<Expr> relations =
        switch (solution) {
          case ListExpr l -> l.items();
          case Vector v -> v.items();
          default -> List.of(solution);
        };
    String variable = c.kind().equals("system") ? "t" : "x";
    if (!(relations.getFirst() instanceof Relation first && first.left() instanceof Apply)) {
      // implicit Φ(x, y(x)) = C: Φₓ + Φ_Y f = 0, with y(x) as Y
      Relation rel = (Relation) solution;
      String phi = AsciiPrinter.INSTANCE.print(rel.left()).replace("y(x)", "Y");
      assertZero(
          c, "diff(" + phi + ", x) + diff(" + phi + ", Y) (" + c.f() + ")", List.of("x", "Y"));
      return;
    }
    String text = equations;
    List<String> conditions = new ArrayList<>();
    for (Expr r : relations) {
      Relation rel = (Relation) r;
      String f = ((Apply) rel.left()).head().name();
      String value = "(" + AsciiPrinter.INSTANCE.print(rel.right()) + ")";
      for (int j = 4; j >= 0; j--) {
        String applied = f + "'".repeat(j) + "(" + variable + ")";
        String derivative = j == 0 ? value : "diff(" + value + ", " + variable + ", " + j + ")";
        text = text.replace(applied, derivative);
      }
      if (!c.conditions().isEmpty()) {
        for (String cond : c.conditions().substring(2).split(", ")) {
          String[] parts = cond.split(" = ");
          String head = parts[0].substring(0, parts[0].indexOf('('));
          if (head.replace("'", "").equals(f)) {
            int j = head.length() - f.length();
            String at = parts[0].substring(parts[0].indexOf('(') + 1, parts[0].length() - 1);
            String derivative = j == 0 ? value : "diff(" + value + ", " + variable + ", " + j + ")";
            conditions.add(
                "subs(" + derivative + ", " + variable + " = " + at + ") - (" + parts[1] + ")");
          }
        }
      }
    }
    List<String> sides = new ArrayList<>();
    if (text.startsWith("[")) {
      for (String eq : text.substring(1, text.length() - 1).split(", (?=diff)")) {
        sides.add(eq);
      }
    } else {
      sides.add(text);
    }
    for (String eq : sides) {
      String[] lr = eq.split(" = ", 2);
      assertZero(c, "(" + lr[0] + ") - (" + lr[1] + ")", List.of(variable));
    }
    for (String condition : conditions) {
      assertZero(c, condition, List.of());
    }
  }

  /** Whether a residual is 0: simplified, or numerically at random points and constants. */
  private static void assertZero(Case c, String residual, List<String> variables) {
    Expr r = Run.value("simplify(expand(" + residual + "))");
    if (r.equals(Num.ZERO)) {
      return;
    }
    Random random = new Random(residual.hashCode());
    int defined = 0;
    for (int i = 0; i < 12 && defined < 6; i++) {
      StringBuilder at = new StringBuilder();
      for (String v : variables) {
        at.append(", ").append(v).append(" = ").append(1 + random.nextInt(40)).append("/17");
      }
      for (int k = 1; k <= 4; k++) {
        at.append(", C_").append(k).append(" = ").append(random.nextInt(19) - 9).append("/7");
      }
      Outcome o = Run.last("numeric(subs(" + residual + at + "), 30)");
      if (!(o instanceof Outcome.Value v) || !(v.value() instanceof Num n)) {
        continue;
      }
      RealNumeric x = n.value().modulus(java.math.MathContext.DECIMAL64);
      defined++;
      assertThat(x.compareValue(mathcanvas.engine.number.Real.of(new BigDecimal("1e-15"))))
          .as(c + ": " + residual + " at " + at)
          .isLessThanOrEqualTo(0);
    }
    assertThat(defined).as(c + ": " + residual + " is defined nowhere").isGreaterThan(0);
  }
}
