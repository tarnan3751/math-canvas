package mathcanvas.engine.eval;

import java.io.Serial;
import java.util.Objects;
import mathcanvas.engine.expr.Expr;

/**
 * An evaluation error ({@code docs/language.md} §16): a statement that parsed but whose evaluation
 * the language defines as an error, such as a definition cycle or a function used as a factor.
 * Mathematical failures that have a value are not errors: {@code 1/0} is {@code undefined}.
 *
 * <p>The error carries the raw node that failed, as the parser or a stored definition built it, by
 * identity: the parser does not yet record positions, and a side table from nodes to positions will
 * map this node to the place in the text it came from.
 */
public final class EvaluationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final transient Expr node;

  /**
   * Creates the error.
   *
   * @param message the message, the gist of §16
   * @param node the raw node that failed
   */
  public EvaluationException(String message, Expr node) {
    super(message);
    this.node = Objects.requireNonNull(node, "node");
  }

  /**
   * The raw node that failed, the same object the parser or a definition held.
   *
   * @return the node
   */
  public Expr node() {
    return node;
  }
}
