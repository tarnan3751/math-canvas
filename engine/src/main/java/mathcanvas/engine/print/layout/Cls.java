package mathcanvas.engine.print.layout;

/** The TeX classes of math atoms, which decide the space between neighbours (TeXbook ch. 18). */
public enum Cls {
  /** An ordinary atom: a variable, a number. */
  ORD,
  /** A large operator or an operator name: {@code ∑}, {@code sin}. */
  OP,
  /** A binary operator: {@code +}, {@code -}. */
  BIN,
  /** A relation: {@code =}, {@code ≤}, {@code →}. */
  REL,
  /** An opening fence. */
  OPEN,
  /** A closing fence or a postfix sign. */
  CLOSE,
  /** Punctuation: the comma. */
  PUNCT,
  /** A delimited subformula or a fraction. */
  INNER
}
