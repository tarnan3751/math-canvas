package mathcanvas.app.ui;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mathcanvas.engine.builtin.Builtins;
import mathcanvas.engine.eval.Environment;

/**
 * The worksheet's definition shortcut ({@code PLAN.md} §6): a cell whose whole text is a relation
 * {@code f(x, …) = body}, with a head that is a fresh name (not defined in the environment, not a
 * builtin, constant, domain or keyword) and parameters that are names, is offered as the definition
 * {@code f(x, …) := body}. The parser is unchanged: the worksheet rewrites the cell's text before
 * sending it, and the cell then shows the {@code :=} form.
 */
public final class DefinitionShortcut {

  private static final String NAME = "\\p{L}[\\p{L}\\p{N}_]*";

  /** The head, the parameters, a lone {@code =} (not part of {@code ==}, {@code <=}, …), body. */
  private static final Pattern RELATION =
      Pattern.compile(
          "\\s*("
              + NAME
              + ")\\s*\\(\\s*("
              + NAME
              + "(?:\\s*,\\s*"
              + NAME
              + ")*)\\s*\\)\\s*(?<![<>!:=≤≥≠])=(?!=)(.*\\S.*)",
          Pattern.DOTALL);

  /**
   * An offer to define.
   *
   * @param name the name defined
   * @param definition the cell's text as a definition
   */
  public record Offer(String name, String definition) {}

  private DefinitionShortcut() {}

  /**
   * Whether a cell's text is offered as a definition.
   *
   * @param text the text
   * @param environment the environment the cell is evaluated in
   * @return the offer, or empty
   */
  public static Optional<Offer> offer(String text, Environment environment) {
    Matcher m = RELATION.matcher(text);
    if (!m.matches() || text.indexOf('\n') >= 0) {
      return Optional.empty();
    }
    String name = m.group(1);
    if (environment.definition(name).isPresent()
        || Builtins.lookup(name).isPresent()
        || Builtins.isConstant(name)
        || Builtins.isDomain(name)
        || Builtins.isKeyword(name)) {
      return Optional.empty();
    }
    String params = String.join(", ", m.group(2).split("\\s*,\\s*"));
    return Optional.of(new Offer(name, name + "(" + params + ") := " + m.group(3).strip()));
  }
}
