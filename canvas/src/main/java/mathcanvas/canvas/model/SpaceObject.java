package mathcanvas.canvas.model;

/**
 * An object of the 3D view (docs/reference/canvas.md, **The 3D view**): drawn in space, never on
 * the plane. The plane's content, hit testing and tools skip these objects, and the 3D view skips
 * every other kind. Code over objects matches this interface as one case where it treats every kind
 * in space alike.
 */
public sealed interface SpaceObject extends CanvasObject
    permits Surface, ParametricSurface, SpaceCurve, GradientArrow {}
