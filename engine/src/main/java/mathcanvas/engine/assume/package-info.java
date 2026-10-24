/**
 * Assumptions: facts about symbols, recorded from relations, and the queries rules ask of them.
 *
 * <p>{@link mathcanvas.engine.assume.Assumptions} is an immutable context of {@link
 * mathcanvas.engine.assume.Facts} per symbol (a domain, bounds, a parity, non-zero). A symbol is
 * real unless it is assumed complex. The queries ({@code isReal}, {@code isPositive}, …) answer a
 * {@link mathcanvas.engine.assume.Tri} by a structural inference over the expression: a query, not
 * a rewrite. {@code docs/reference/engine.md}, section <b>Assumptions</b>, is the reference.
 */
package mathcanvas.engine.assume;
