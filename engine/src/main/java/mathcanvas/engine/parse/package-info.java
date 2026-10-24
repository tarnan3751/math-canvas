/**
 * Reading the language of {@code docs/language.md}: the {@link mathcanvas.engine.parse.Lexer},
 * which normalises source text to NFC and keeps the positions of the text as typed, and its {@link
 * mathcanvas.engine.parse.Token}s. Every error is a {@link mathcanvas.engine.parse.ParseException}
 * with a line and a column. {@code docs/reference/engine.md}, section <b>Parsing</b>, is the
 * reference.
 */
package mathcanvas.engine.parse;
