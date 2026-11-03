package mathcanvas.engine.script;

import java.util.List;
import mathcanvas.engine.expr.Expr;

/**
 * What a statement's run left beside its value ({@code docs/language.md} §19): the values its
 * {@code print} statements printed, in order.
 *
 * @param printed the values printed, canonical
 */
public record RunLog(List<Expr> printed) {

  /** The log of a statement that printed nothing. */
  public static final RunLog NONE = new RunLog(List.of());

  /** Copies the values. */
  public RunLog {
    printed = List.copyOf(printed);
  }
}
