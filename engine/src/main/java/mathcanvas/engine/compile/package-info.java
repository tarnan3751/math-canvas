/**
 * Compilation of an expression to a {@code double} evaluator for plotting ({@code PLAN.md} §5.5):
 * {@link mathcanvas.engine.compile.Compiled#compile} turns an evaluated value into an immutable,
 * thread-safe function of its arguments that agrees with the engine's exact value wherever that
 * value is real, and is NaN wherever it is undefined or not real. The only {@code double}
 * arithmetic of the engine outside explicit numeric requests lives here. {@code
 * docs/reference/engine.md}, section <b>Compilation</b>, is the reference.
 */
package mathcanvas.engine.compile;
