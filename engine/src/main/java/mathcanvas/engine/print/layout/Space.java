package mathcanvas.engine.print.layout;

import java.util.Objects;
import mathcanvas.engine.rule.Path;

/**
 * A space of the text. A linear space has no width when typeset, since the spacing classes of its
 * neighbours decide the gap; a thin space is the one before a differential.
 *
 * @param kind the kind
 */
public record Space(Kind kind) implements Layout {

  /** The kinds of space. */
  public enum Kind {
    /** A space the linear text has, which the typesetter leaves to the spacing classes. */
    LINEAR,
    /** A thin space (3 mu). */
    THIN
  }

  /** Checks the kind. */
  public Space {
    Objects.requireNonNull(kind);
  }

  @Override
  public Path path() {
    return null;
  }
}
