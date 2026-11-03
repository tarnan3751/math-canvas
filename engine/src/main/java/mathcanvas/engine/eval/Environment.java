package mathcanvas.engine.eval;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Lambda;
import mathcanvas.engine.parse.Names;

/**
 * Everything a statement is evaluated in: the definitions, the assumptions and the session
 * settings. Immutable; each change returns a new environment. Names are keys of a sorted map, so
 * nothing here hashes an expression.
 *
 * @param definitions the definitions by name
 * @param assumptions the facts recorded by {@code assume}
 * @param settings the precision and the angle mode
 */
public record Environment(
    SortedMap<String, Definition> definitions, Assumptions assumptions, Settings settings) {

  /** No definitions, no assumptions, the default settings. */
  public static final Environment EMPTY =
      new Environment(new TreeMap<>(), Assumptions.EMPTY, Settings.DEFAULT);

  /**
   * Copies the definitions and checks the fields.
   *
   * @throws NullPointerException if an operand is null
   */
  public Environment {
    definitions = Collections.unmodifiableSortedMap(new TreeMap<>(definitions));
    Objects.requireNonNull(assumptions, "assumptions");
    Objects.requireNonNull(settings, "settings");
  }

  /**
   * Returns this environment with {@code name} bound to {@code definition}, replacing any earlier
   * binding. A value whose body is a lambda is stored as the function it denotes, so {@code f := x
   * -> x^2} defines {@code f} exactly as {@code f(x) := x^2} does ({@code docs/language.md} §8.2).
   *
   * @param name the name
   * @param definition the definition
   * @return the environment
   */
  public Environment with(String name, Definition definition) {
    Objects.requireNonNull(name, "name");
    Definition d =
        definition instanceof Definition.Value(Lambda l)
            ? new Definition.Function(l.params(), l.body())
            : Objects.requireNonNull(definition, "definition");
    TreeMap<String, Definition> map = new TreeMap<>(definitions);
    map.put(name, d);
    return new Environment(map, assumptions, settings);
  }

  /**
   * Returns this environment without a binding for {@code name}.
   *
   * @param name the name
   * @return the environment; this one when {@code name} is not bound
   */
  public Environment without(String name) {
    if (!definitions.containsKey(name)) {
      return this;
    }
    TreeMap<String, Definition> map = new TreeMap<>(definitions);
    map.remove(name);
    return new Environment(map, assumptions, settings);
  }

  /**
   * Returns this environment with other assumptions.
   *
   * @param a the assumptions
   * @return the environment
   */
  public Environment withAssumptions(Assumptions a) {
    return new Environment(definitions, a, settings);
  }

  /**
   * Returns this environment with other settings.
   *
   * @param s the settings
   * @return the environment
   */
  public Environment withSettings(Settings s) {
    return new Environment(definitions, assumptions, s);
  }

  /**
   * The definition of a name.
   *
   * @param name the name
   * @return its definition, or empty when it is unbound
   */
  public Optional<Definition> definition(String name) {
    return Optional.ofNullable(definitions.get(name));
  }

  /**
   * Whether a name is a user-defined function.
   *
   * @param name the name
   * @return true if it is bound to a {@link Definition.Function} or a {@link Definition.Procedure}
   */
  public boolean isFunction(String name) {
    return definitions.get(name) instanceof Definition.Function
        || definitions.get(name) instanceof Definition.Procedure;
  }

  /**
   * The parser's view of this environment: {@code name(} is a call for a user-defined function
   * ({@code docs/language.md} §11.3).
   *
   * @return the names
   */
  public Names names() {
    return this::isFunction;
  }
}
