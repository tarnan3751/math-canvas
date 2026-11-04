package mathcanvas.app.help;

import java.util.List;
import java.util.Objects;

/**
 * The help of one name: a function, a command, a reserved name or an operator, as the language
 * reference, {@code docs/language.md}, describes it.
 *
 * @param name the name, or the operator as written ({@code ^}, {@code d/dx})
 * @param category the reference's heading it is listed under: {@code Functions}, {@code Commands},
 *     {@code Operators}, …
 * @param section the reference's section, {@code 12.1}
 * @param arity the arguments it takes, as the reference writes them, or empty
 * @param alias another way to write it, or empty
 * @param description what the reference says of it, one line, or empty
 * @param examples its examples, in the reference's order
 */
public record HelpEntry(
    String name,
    String category,
    String section,
    String arity,
    String alias,
    String description,
    List<Example> examples) {

  /**
   * Checks the fields and copies the examples.
   *
   * @throws NullPointerException if a field is null
   */
  public HelpEntry {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(section, "section");
    Objects.requireNonNull(arity, "arity");
    Objects.requireNonNull(alias, "alias");
    Objects.requireNonNull(description, "description");
    examples = List.copyOf(examples);
  }

  /**
   * An example: what to type, and what the reference says it gives.
   *
   * @param input the statement
   * @param result its result as the Unicode printer prints it in the exact format, radians and an
   *     empty environment
   */
  public record Example(String input, String result) {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     */
    public Example {
      Objects.requireNonNull(input, "input");
      Objects.requireNonNull(result, "result");
    }
  }
}
