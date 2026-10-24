/**
 * The engine's numbers: an exact tower {@link mathcanvas.engine.number.Int} (BigInteger) ⊂ {@link
 * mathcanvas.engine.number.Rational} ⊂ {@link mathcanvas.engine.number.Real} (BigDecimal) ⊂ {@link
 * mathcanvas.engine.number.Complex}, sealed under {@link mathcanvas.engine.number.Numeric}, with
 * the non-complex kinds sealed under {@link mathcanvas.engine.number.RealNumeric}.
 *
 * <p><b>Normal forms.</b> Every value has exactly one representation, so the records' own {@code
 * equals} is value equality within a kind: a rational is in lowest terms with a positive
 * denominator other than 1 (a rational with denominator 1 is an {@code Int}); a real carries its
 * {@code BigDecimal} with trailing zeros stripped ({@code 1.0} and {@code 1.00} are one value); a
 * complex never has an exact-zero imaginary part (it would be its real part). The canonical
 * constructors reject a non-normal state; the {@code of} factories normalise and demote.
 *
 * <p><b>Promotion and contagion.</b> A binary operation promotes the lower operand to the higher
 * kind, operates, and demotes the result when it is exactly representable lower. Inexactness is
 * contagious and never demoted: any operation with a {@code Real} operand returns a {@code Real},
 * or a {@code Complex} with at least one {@code Real} part, even when its value is an integer.
 *
 * <p><b>Precision.</b> Operations that can be inexact take a {@link java.math.MathContext}; exact
 * kinds ignore it. A {@code Real} result is the exact result of the operation rounded once to the
 * context. Contexts live on operations, never on numbers. {@code double} appears only in {@link
 * mathcanvas.engine.number.RealNumeric#toDouble()} and {@link
 * mathcanvas.engine.number.Real#of(double)}.
 *
 * <p>The package is pure: no static mutable state, no caches, no clock, no randomness, no I/O.
 * {@code docs/reference/engine.md}, section <b>Numbers</b>, is the reference.
 */
package mathcanvas.engine.number;
