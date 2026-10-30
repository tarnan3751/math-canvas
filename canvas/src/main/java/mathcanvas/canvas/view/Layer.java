package mathcanvas.canvas.view;

/** The canvas view's three stacked layers, bottom to top. */
public enum Layer {
  /** The grid, the axes and the tick labels. */
  GRID,
  /** The plotted objects. */
  OBJECTS,
  /** The interactive overlay: hover, selection, drag previews. */
  OVERLAY
}
