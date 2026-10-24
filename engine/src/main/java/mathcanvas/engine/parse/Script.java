package mathcanvas.engine.parse;

import java.util.List;

/**
 * A parsed script: its statements in order, empty statements left out.
 *
 * @param statements the statements
 */
public record Script(List<Statement> statements) {

  /**
   * Copies the statements.
   *
   * @throws NullPointerException if a statement is null
   */
  public Script {
    statements = List.copyOf(statements);
  }
}
