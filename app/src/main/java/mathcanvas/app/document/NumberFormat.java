package mathcanvas.app.document;

/**
 * How the worksheet shows numeric results ({@code PLAN.md} §7, Phase 2). Stored and persisted now;
 * the worksheet applies it from Phase 2c.
 */
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
