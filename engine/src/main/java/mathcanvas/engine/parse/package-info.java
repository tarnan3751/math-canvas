/**
 * Reading the language of {@code docs/language.md}: the {@link mathcanvas.engine.parse.Lexer},
 * which normalises source text to NFC and keeps the positions of the text as typed; the Pratt
 * {@link mathcanvas.engine.parse.Parser}, which builds raw expression trees exactly as typed from
 * an expression or a script of {@link mathcanvas.engine.parse.Statement}s, given the user-defined
 * functions as {@link mathcanvas.engine.parse.Names}; and the bracket constructor's rule, {@link
 * mathcanvas.engine.parse.Brackets}. Every error is a {@link
 * mathcanvas.engine.parse.ParseException} with a line and a column. {@code
 * docs/reference/engine.md}, section <b>Parsing</b>, is the reference.
 */
package mathcanvas.engine.parse;
