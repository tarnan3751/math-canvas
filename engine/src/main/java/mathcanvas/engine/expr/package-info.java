/**
 * The engine's expressions: the sealed {@link mathcanvas.engine.expr.Expr} with one immutable
 * record per node kind, the total order {@link mathcanvas.engine.expr.ExprOrder} and the canonical
 * form {@link mathcanvas.engine.expr.Canonical}.
 *
 * <p><b>Raw and canonical.</b> The records are plain: a factory validates its arguments and builds
 * the node exactly as given, so the parser can produce what was typed ({@code 2^3^2}, {@code x +
 * x}). {@link mathcanvas.engine.expr.Canonical#of(Expr)} brings a tree to canonical form, Cohen's
 * automatic simplification (Joel S. Cohen, <i>Computer Algebra and Symbolic Computation:
 * Mathematical Methods</i>, chapter 3). Nothing canonicalises on construction, and whether a node
 * is canonical is not stored on it.
 *
 * <p><b>Equality.</b> Records compare structurally, which on canonical forms is mathematical
 * equality for a large class of expressions ({@code x + 2x} and {@code 3x} have one canonical
 * form). There is no intern table; {@code hashCode} is recomputed on each call, so the canonical
 * form sorts and groups by the order, never by hash.
 *
 * <p>There is no subtraction or division node: {@code a - b} is {@code Sum(a, Product(-1, b))} and
 * {@code a / b} is {@code Product(a, Power(b, -1))} ({@link mathcanvas.engine.expr.Exprs}). The
 * package is pure: no static mutable state, no hash-ordered iteration, no clock, no randomness, no
 * I/O. {@code docs/reference/engine.md}, section <b>Expressions</b>, is the reference.
 */
package mathcanvas.engine.expr;
