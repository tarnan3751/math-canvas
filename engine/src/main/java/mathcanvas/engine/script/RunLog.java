package mathcanvas.engine.script;

import java.util.List;
import mathcanvas.engine.expr.Expr;

/**
 * What a statement's run left beside its value ({@code docs/language.md} §19): the values its
 * {@code print} statements printed, in order, and, when it was traced, what its outermost block
 * did, for the narration ({@link RunNarration}).
 *
 * @param printed the values printed, canonical
 * @param events what the outermost block did, empty unless traced
 */
public record RunLog(List<Expr> printed, List<RunEvent> events) {

  /** The log of a statement that printed nothing and recorded nothing. */
  public static final RunLog NONE = new RunLog(List.of(), List.of());

  /** Copies the values. */
  public RunLog {
    printed = List.copyOf(printed);
    events = List.copyOf(events);
  }
}
