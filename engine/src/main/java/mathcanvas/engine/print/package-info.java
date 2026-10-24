/**
 * The text printers of {@code docs/language.md} §15: {@link mathcanvas.engine.print.AsciiPrinter},
 * {@link mathcanvas.engine.print.UnicodePrinter} and {@link mathcanvas.engine.print.LatexPrinter}.
 * A printer's {@code print} is faithful to the tree it is given, so a raw parse echoes as typed;
 * {@link mathcanvas.engine.print.Display} turns a canonical value into its conventional shape
 * first. The ASCII and Unicode output parses back (§15.4). {@code docs/reference/engine.md},
 * section <b>Printing</b>, is the reference.
 */
package mathcanvas.engine.print;
