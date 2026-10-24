package mathcanvas.engine.parse;

/** The kinds of token ({@code docs/language.md} §3). */
public enum TokenKind {
  /** An integer literal: ASCII digits. */
  INT,
  /** A decimal literal: a point or an exponent. */
  DECIMAL,
  /** An identifier, with subscripts normalised to {@code _} and ASCII; also reserved words. */
  IDENT,
  /** {@code +}. */
  PLUS,
  /** {@code -} or {@code −}. */
  MINUS,
  /** {@code *}, {@code ·} or {@code ⋅}. */
  STAR,
  /** {@code /} or {@code ÷}. */
  SLASH,
  /** {@code ^} or {@code **}. */
  CARET,
  /** {@code ×}, the cross product. */
  CROSS,
  /** Postfix {@code !}. */
  BANG,
  /** Postfix {@code %}. */
  PERCENT,
  /** Postfix {@code °}. */
  DEGREE,
  /** A run of superscript digits with an optional {@code ⁻}; the text is the integer. */
  SUPERSCRIPT,
  /** {@code =}. */
  EQ,
  /** {@code !=} or {@code ≠}. */
  NE,
  /** {@code <}. */
  LT,
  /** {@code <=} or {@code ≤}. */
  LE,
  /** {@code >}. */
  GT,
  /** {@code >=} or {@code ≥}. */
  GE,
  /** {@code in} or {@code ∈}. */
  IN,
  /** {@code ->} or {@code →}. */
  ARROW,
  /** {@code :=}. */
  DEFINE,
  /** {@code (}. */
  LPAREN,
  /** {@code )}. */
  RPAREN,
  /** {@code [}. */
  LBRACKET,
  /** {@code ]}. */
  RBRACKET,
  /** <code>{</code>. */
  LBRACE,
  /** <code>}</code>. */
  RBRACE,
  /** {@code ⟨}. */
  LANGLE,
  /** {@code ⟩}. */
  RANGLE,
  /** {@code |}. */
  BAR,
  /** {@code ,}. */
  COMMA,
  /** {@code ;}. */
  SEMI,
  /** A newline outside every bracket: a statement terminator. */
  NEWLINE,
  /** {@code π}. */
  PI,
  /** {@code ∞}. */
  INFINITY,
  /** {@code ℤ ℚ ℝ ℂ}. */
  DOMAIN,
  /** {@code √}. */
  SQRT,
  /** {@code ∛}. */
  CBRT,
  /** {@code ∜}. */
  QRT,
  /** {@code ∫}. */
  INTEGRAL,
  /** {@code Σ} or {@code ∑}. */
  SIGMA,
  /** {@code Π} or {@code ∏}. */
  CAPITAL_PI,
  /** The derivative notation, one token; the text is the variable and the order is carried. */
  DERIVATIVE,
  /** A lexical error, standing where the lexer stopped. */
  ERROR,
  /** The end of the input. */
  EOF
}
