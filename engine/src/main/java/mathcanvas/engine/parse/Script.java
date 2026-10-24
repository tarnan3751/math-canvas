package mathcanvas.engine.parse;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.engine.expr.Expr;

/**
 * A parsed script: its statements in order, empty statements left out, and a side table of where
 * its nodes were typed.
 *
 * <p>The side table maps each node the parser built, by identity, to the position of its first
 * token, so a caller holding a node of the script (such as the node an evaluation error carries)
 * finds where it came from. Nodes are compared by identity because equal nodes typed in two places
 * are two objects. A node that the parser shares between places (a constant such as {@code π}) has
 * no single position and is not in the table; neither is a node built later from the script's
 * nodes.
 */
public final class Script {

  private final List<Statement> statements;
  private final Map<Expr, Position> positions;

  /**
   * Creates the script.
   *
   * @param statements the statements
   * @param positions the side table, identity-keyed; a null value marks a shared node
   */
  Script(List<Statement> statements, IdentityHashMap<Expr, Position> positions) {
    this.statements = List.copyOf(statements);
    this.positions = positions;
  }

  /**
   * The statements, in order.
   *
   * @return the statements
   */
  public List<Statement> statements() {
    return statements;
  }

  /**
   * Where a node of this script was typed: the position of its first token.
   *
   * @param node a node, compared by identity
   * @return its position, or empty when the node is not one the parser built at one place
   */
  public Optional<Position> positionOf(Expr node) {
    return Optional.ofNullable(positions.get(node));
  }
}
