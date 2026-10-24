package mathcanvas.engine.parse;

/**
 * The user-defined functions of the environment a statement is parsed in ({@code docs/language.md}
 * §11.3): {@code name(} is a call if and only if {@code name} is a builtin or one of these.
 */
@FunctionalInterface
public interface Names {

  /** The empty environment: builtins only. */
  Names NONE = name -> false;

  /**
   * Whether a name is a user-defined function: defined with {@code name(params) := …} or as {@code
   * name := params -> …}.
   *
   * @param name the name
   * @return true if {@code name(} calls it
   */
  boolean isUserFunction(String name);
}
