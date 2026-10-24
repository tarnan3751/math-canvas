package mathcanvas.engine.rule;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import mathcanvas.engine.expr.Expr;

/** A rule's right-hand side. */
public sealed interface Replacement {

  /**
   * A template instantiated with the match's bindings ({@link
   * mathcanvas.engine.match.Bindings#substituteInto}).
   *
   * @param template the template, in the language's pattern-variable notation
   */
  record Template(Expr template) implements Replacement {

    /**
     * Checks the template.
     *
     * @throws NullPointerException if {@code template} is null
     */
    public Template {
      Objects.requireNonNull(template, "template");
    }
  }

  /**
   * A right-hand side that needs computation. It computes only the replacement: locating the
   * subexpression and rewriting it go through the matcher and the rewriter. Empty means the rule
   * does not apply after all.
   *
   * @param function the computation
   */
  record Computed(Function<Match, Optional<Expr>> function) implements Replacement {

    /**
     * Checks the function.
     *
     * @throws NullPointerException if {@code function} is null
     */
    public Computed {
      Objects.requireNonNull(function, "function");
    }
  }
}
