package mathcanvas.engine.compile;

import java.io.Serial;
import java.util.Objects;

/**
 * An expression that cannot be compiled to a real {@code double} evaluator, refused at compile time
 * with what it holds that cannot be: a function, a kind of expression, a free symbol or a complex
 * value. The message is English, for logs and the CLI; the app words its own from {@link #kind} and
 * {@link #subject}.
 */
public final class UnsupportedException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /** What was refused. */
  public enum Kind {
    /** A function or command with no {@code double} form: the subject is its name. */
    FUNCTION,
    /**
     * A kind of expression that is not a real number: the subject names it ({@code relation}), or
     * is the constant part as written when its value is out of reach ({@code sin(e^(10^30))}).
     */
    FORM,
    /** A symbol that is not an argument: the subject is its name. */
    FREE_SYMBOL,
    /** A complex number, or a constant part whose value is complex: the subject is the part. */
    COMPLEX
  }

  private final Kind kind;
  private final String subject;

  /**
   * Creates the refusal.
   *
   * @param kind what was refused
   * @param subject the name or value refused
   */
  public UnsupportedException(Kind kind, String subject) {
    super(message(kind, subject), null, false, false);
    this.kind = Objects.requireNonNull(kind, "kind");
    this.subject = Objects.requireNonNull(subject, "subject");
  }

  private static String message(Kind kind, String subject) {
    return switch (kind) {
      case FUNCTION, FORM -> "cannot plot " + subject;
      case FREE_SYMBOL -> subject + " has no value to plot with";
      case COMPLEX -> subject + " is not real and cannot be plotted";
    };
  }

  /**
   * What was refused.
   *
   * @return the kind
   */
  public Kind kind() {
    return kind;
  }

  /**
   * The name or part refused: a function's or symbol's name, a kind's name, a complex part.
   *
   * @return the subject
   */
  public String subject() {
    return subject;
  }
}
