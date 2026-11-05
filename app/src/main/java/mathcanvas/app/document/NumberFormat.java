package mathcanvas.app.document;

/** How the worksheet shows numeric results, stored and persisted with the document. */
public enum NumberFormat {
  /** Exact values as the engine gives them. */
  EXACT,
  /** Decimals at the session precision. */
  DECIMAL,
  /** Scientific notation. */
  SCIENTIFIC,
  /** Fractions. */
  FRACTION
}
