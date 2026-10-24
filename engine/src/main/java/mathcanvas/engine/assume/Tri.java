package mathcanvas.engine.assume;

/** A three-valued answer: known true, known false, or not known. */
public enum Tri {
  /** Known to hold. */
  TRUE,
  /** Known not to hold. */
  FALSE,
  /** Not known either way. */
  UNKNOWN;

  /**
   * Returns {@link #TRUE} or {@link #FALSE}.
   *
   * @param value the truth value
   * @return the answer
   */
  public static Tri of(boolean value) {
    return value ? TRUE : FALSE;
  }

  /**
   * Returns the conjunction: false when either is false, true when both are true.
   *
   * @param other the other answer
   * @return the conjunction
   */
  public Tri and(Tri other) {
    if (this == FALSE || other == FALSE) {
      return FALSE;
    }
    return this == TRUE && other == TRUE ? TRUE : UNKNOWN;
  }

  /**
   * Returns the disjunction: true when either is true, false when both are false.
   *
   * @param other the other answer
   * @return the disjunction
   */
  public Tri or(Tri other) {
    if (this == TRUE || other == TRUE) {
      return TRUE;
    }
    return this == FALSE && other == FALSE ? FALSE : UNKNOWN;
  }

  /**
   * Returns the negation; {@link #UNKNOWN} stays.
   *
   * @return the negation
   */
  public Tri not() {
    return switch (this) {
      case TRUE -> FALSE;
      case FALSE -> TRUE;
      case UNKNOWN -> UNKNOWN;
    };
  }
}
