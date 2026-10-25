package mathcanvas.engine.rule;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
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

  /**
   * A computed right-hand side that also names values for its narration, such as the gcd a
   * cancellation divided out: the rewriter records them on the {@link Rewrite}, apart from the
   * pattern's bindings, and a narration text shows one as {@code {name}}. Each name is a lowercase
   * word, so it can never be a pattern variable (which ends in {@code _}), and is none of the
   * placeholders every text has ({@code before}, {@code after}, {@code list}).
   *
   * @param names the names the function may return values for
   * @param function the computation; empty when the rule does not apply after all
   */
  record Valued(SortedSet<String> names, Function<Match, Optional<Result>> function)
      implements Replacement {

    /** The placeholders every narration text has, which no value may take. */
    public static final Set<String> RESERVED = Set.of("before", "after", "list");

    /**
     * Checks the names and copies them.
     *
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if a name is not a lowercase word or is reserved
     */
    public Valued {
      Objects.requireNonNull(function, "function");
      names = Collections.unmodifiableSortedSet(new TreeSet<>(names));
      for (String n : names) {
        if (!n.matches("[a-z][a-z0-9]*") || RESERVED.contains(n)) {
          throw new IllegalArgumentException(
              "a value name is a lowercase word, not reserved: " + n);
        }
      }
    }

    /**
     * The computed replacement and its named values.
     *
     * @param expr the replacement, raw
     * @param values the named values, each name one of the replacement's names
     */
    public record Result(Expr expr, SortedMap<String, Expr> values) {

      /** Copies the values. */
      public Result {
        Objects.requireNonNull(expr, "expr");
        values = Collections.unmodifiableSortedMap(new TreeMap<>(values));
      }
    }
  }
}
