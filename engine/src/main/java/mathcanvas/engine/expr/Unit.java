package mathcanvas.engine.expr;

import java.util.Objects;

/**
 * A unit of measurement, written {@code _m}, {@code _km}, {@code _degC} ({@code docs/language.md}
 * §14.26): a leaf that every operation treats as a positive real constant. A quantity is a product
 * of a magnitude and units. What a unit means (its dimension and its factor to the coherent SI
 * unit) is the table's, in {@code mathcanvas.engine.units}, which builds every unit; this node only
 * names it and carries its place in the order.
 *
 * @param name the normalised ASCII spelling without the underscore: {@code km}, {@code um} for
 *     {@code µm}, {@code kohm} for {@code kΩ}
 * @param rank the unit's place in {@link ExprOrder}, given by the table: SI derived units, then the
 *     base units, the accepted units, the imperial and US units and the temperatures, the coherent
 *     unit first within each
 */
public record Unit(String name, int rank) implements Expr {

  /**
   * Checks the name.
   *
   * @throws NullPointerException if {@code name} is null
   * @throws IllegalArgumentException if {@code name} is empty
   */
  public Unit {
    Objects.requireNonNull(name, "name");
    if (name.isEmpty()) {
      throw new IllegalArgumentException("a unit needs a name");
    }
  }

  @Override
  public String toString() {
    return "Unit " + name;
  }
}
