/**
 * Typesetting: the engine's layout tree and the font's metrics to a box tree with positions and
 * sizes in pixels. The box tree is the seam every consumer reads: {@link
 * mathcanvas.render.MathView} draws it as JavaFX nodes, and the canvas's painter will draw it too.
 * Nothing here references JavaFX. {@code docs/reference/render.md}, section <b>Typesetting</b>, is
 * the reference.
 */
package mathcanvas.render.typeset;
