/**
 * The bundled math font read from its file, in pure Java: metrics, the character map, TrueType
 * glyph outlines, the OpenType MATH table and the GSUB script-style alternates. JavaFX has no
 * access to glyph identifiers, outlines or the MATH table, and many size variants have no code
 * point, so {@code render} reads the font itself. Nothing here references JavaFX, so the tests run
 * without a display. {@code docs/reference/render.md}, section <b>The font</b>, is the reference.
 */
package mathcanvas.render.font;
