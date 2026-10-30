package mathcanvas.canvas.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.builtin.Builtins;
import mathcanvas.engine.parse.Lexer;
import mathcanvas.engine.parse.ParseException;
import mathcanvas.engine.parse.Token;
import mathcanvas.engine.parse.TokenKind;

/**
 * A slider: an engine variable whose value is set by dragging, between a minimum and a maximum in
 * steps. The numbers are exact decimals, so the variable is bound to the engine exactly ({@code
 * 0.3} is 3/10, not the nearest double); they are kept without trailing zeros, so {@code 0.5} and
 * {@code 0.50} are one value and an edit of the scale alone changes nothing. Objects that read the
 * variable depend on the slider. The name is checked here: one identifier as the engine's lexer
 * reads it, not a builtin, constant, domain or keyword.
 *
 * @param id the id
 * @param style the style
 * @param variable the variable's name
 * @param min the least value
 * @param max the greatest value, above the least
 * @param step the step, positive
 * @param value the value, from the least to the greatest
 */
public record Slider(
    ObjectId id,
    Style style,
    String variable,
    BigDecimal min,
    BigDecimal max,
    BigDecimal step,
    BigDecimal value)
    implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if the name is not a variable name, the range is empty, the
   *     step is not positive or the value is outside the range
   */
  public Slider {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(variable, "variable");
    Objects.requireNonNull(min, "min");
    Objects.requireNonNull(max, "max");
    Objects.requireNonNull(step, "step");
    Objects.requireNonNull(value, "value");
    if (!isVariableName(variable)) {
      throw new IllegalArgumentException("not a variable name: " + variable);
    }
    min = min.stripTrailingZeros();
    max = max.stripTrailingZeros();
    step = step.stripTrailingZeros();
    value = value.stripTrailingZeros();
    if (min.compareTo(max) >= 0) {
      throw new IllegalArgumentException("a slider's minimum is below its maximum");
    }
    if (step.signum() <= 0) {
      throw new IllegalArgumentException("a slider's step is positive");
    }
    if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
      throw new IllegalArgumentException("a slider's value is in its range");
    }
  }

  /**
   * Whether a name can be a slider's variable: exactly one identifier as the engine's lexer reads
   * it (so in its normalised spelling, {@code x_1} and not {@code x₁}), and not the name of a
   * builtin function or command, a constant, a domain or a reserved keyword.
   *
   * @param name the name
   * @return true if it can
   */
  public static boolean isVariableName(String name) {
    Token t;
    Token end;
    try {
      Lexer lexer = new Lexer(name);
      t = lexer.next();
      end = lexer.next();
    } catch (ParseException e) {
      return false;
    }
    return t.is(TokenKind.IDENT)
        && t.text().equals(name)
        && end.is(TokenKind.EOF)
        && Builtins.lookup(name).isEmpty()
        && !Builtins.isConstant(name)
        && !Builtins.isDomain(name)
        && !Builtins.isKeyword(name);
  }

  @Override
  public List<ObjectId> parents() {
    return List.of();
  }

  @Override
  public Set<String> reads() {
    return Set.of();
  }

  @Override
  public Slider withStyle(Style style) {
    return new Slider(id, style, variable, min, max, step, value);
  }

  /**
   * This slider at another value.
   *
   * @param value the value, in the range
   * @return the slider
   */
  public Slider withValue(BigDecimal value) {
    return new Slider(id, style, variable, min, max, step, value);
  }
}
