/**
 * The layout tree: an expression as the parts a typesetter places, with the TeX spacing class of
 * each atom and the operand path ({@link mathcanvas.engine.rule.Path}) of the subexpression each
 * node prints. {@link mathcanvas.engine.print.LayoutPrinter} builds it; {@code render} typesets it.
 * {@code docs/reference/engine.md}, section <b>Printing</b>, is the reference.
 */
package mathcanvas.engine.print.layout;
