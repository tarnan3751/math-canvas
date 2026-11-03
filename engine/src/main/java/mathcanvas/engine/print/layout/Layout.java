package mathcanvas.engine.print.layout;

import mathcanvas.engine.rule.Path;

/**
 * A node of the layout tree. Each node but a {@link Space} has the path of the subexpression it
 * prints in the tree that was printed, or null when it prints no single subexpression (an operator
 * sign, the numerator of {@code 2x/3}, the bounds of an integral as a group).
 */
public sealed interface Layout
    permits Row,
        Glyph,
        Text,
        UnitText,
        Space,
        Fraction,
        Scripts,
        Radical,
        Delimited,
        Matrix,
        BigOp,
        Piecewise {

  /**
   * Returns the path of the subexpression this node prints.
   *
   * @return the path, or null
   */
  Path path();

  /**
   * Returns the node's spacing class as an atom of the row it stands in: a glyph's or a text's own
   * class; inner for a fraction, a fenced body, a matrix and a piecewise expression; operator for a
   * big operator; the base's class for a scripted base; ordinary otherwise.
   *
   * @return the class
   */
  default Cls cls() {
    return switch (this) {
      case Glyph g -> g.cls();
      case Text t -> t.cls();
      case UnitText u -> Cls.ORD;
      case Scripts s -> s.base().cls();
      case Fraction f -> Cls.INNER;
      case Delimited d -> Cls.INNER;
      case Matrix m -> Cls.INNER;
      case Piecewise p -> Cls.INNER;
      case BigOp b -> Cls.OP;
      case Row r -> Cls.ORD;
      case Radical r -> Cls.ORD;
      case Space s -> Cls.ORD;
    };
  }
}
