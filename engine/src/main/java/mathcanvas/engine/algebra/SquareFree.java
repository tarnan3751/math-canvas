package mathcanvas.engine.algebra;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.number.RealNumeric;

/**
 * Square-free decomposition by Yun's algorithm (Yun, "On square-free decomposition algorithms",
 * SYMSAC 1976; Geddes, Czapor and Labahn, ch. 8). In several variables the polynomial is taken in
 * its first variable: its content in that variable is decomposed recursively, and Yun's algorithm
 * with the partial derivative splits the primitive part.
 */
public final class SquareFree {

  /**
   * A square-free decomposition: {@code p = unit · ∏ parts[i]^(i+1)}. Each part is primitive over ℤ
   * with a positive sign ({@link Gcd#sign}), square-free, and coprime to the others; a part may be
   * 1, the last never is.
   *
   * @param unit the rational unit
   * @param parts the product of the factors of multiplicity {@code i+1}, at index {@code i}
   */
  public record Decomposition(RealNumeric unit, List<Polynomial> parts) {

    /** Copies the parts. */
    public Decomposition {
      parts = List.copyOf(parts);
    }
  }

  private SquareFree() {}

  /**
   * Decomposes {@code p}.
   *
   * @param p a non-zero polynomial
   * @return its square-free decomposition
   * @throws ArithmeticException if {@code p} is zero
   * @throws AlgebraLimitException if a degree exceeds {@link Polynomial#MAX_DEGREE}
   */
  public static Decomposition of(Polynomial p) {
    if (p.isZero()) {
      throw new ArithmeticException("the zero polynomial has no square-free decomposition");
    }
    AlgebraLimitException.checkDegree(p);
    List<Polynomial> parts = primitive(Gcd.primitivePart(p));
    RealNumeric lead = p.leadingCoefficient();
    for (int i = 0; i < parts.size(); i++) {
      lead = Q.divide(lead, Q.pow(parts.get(i).leadingCoefficient(), i + 1));
    }
    return new Decomposition(lead, parts);
  }

  /** The parts of a primitive polynomial with a positive sign ({@link Gcd#sign}). */
  private static List<Polynomial> primitive(Polynomial p) {
    if (p.isConstant()) {
      return List.of();
    }
    Expr v = p.variables().getFirst();
    Polynomial c = Gcd.content(p, v);
    List<Polynomial> parts = yun(Division.exactQuotient(p, c).orElseThrow(), v);
    List<Polynomial> contentParts = primitive(c);
    List<Polynomial> out = new ArrayList<>();
    for (int i = 0; i < Math.max(parts.size(), contentParts.size()); i++) {
      Polynomial a = i < parts.size() ? parts.get(i) : Polynomial.ONE;
      Polynomial b = i < contentParts.size() ? contentParts.get(i) : Polynomial.ONE;
      out.add(a.multiply(b));
    }
    return out;
  }

  /** Yun's algorithm on a polynomial primitive in {@code v} of positive degree in it. */
  private static List<Polynomial> yun(Polynomial a, Expr v) {
    Polynomial b = a.derivative(v);
    Polynomial c = Gcd.gcd(a, b);
    Polynomial w = Division.exactQuotient(a, c).orElseThrow();
    Polynomial y = Division.exactQuotient(b, c).orElseThrow();
    Polynomial z = y.subtract(w.derivative(v));
    List<Polynomial> out = new ArrayList<>();
    while (w.degree(v) > 0) {
      Polynomial g = Gcd.gcd(w, z);
      out.add(Gcd.primitivePart(g));
      w = Division.exactQuotient(w, g).orElseThrow();
      y = Division.exactQuotient(z, g).orElseThrow();
      z = y.subtract(w.derivative(v));
    }
    while (!out.isEmpty() && out.getLast().isConstant()) {
      out.removeLast();
    }
    return out;
  }
}
