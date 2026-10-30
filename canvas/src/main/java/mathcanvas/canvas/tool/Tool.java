package mathcanvas.canvas.tool;

import java.util.function.Consumer;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;

/**
 * A canvas tool: a small state machine fed the pointer's events in world coordinates. It gives what
 * it changes as {@link Edit}s to a sink, never touching the document, and paints its preview on the
 * overlay from the last event it saw. Touched by one thread at a time (the FX thread in the app);
 * the scene of each event is the document as it is at that event.
 */
public interface Tool {

  /**
   * The primary button is pressed.
   *
   * @param scene the document
   * @param pointer where
   * @param edits told the edits the press makes
   * @return whether the tool takes the press; one it does not take pans the view
   */
  boolean press(Scene scene, Pointer pointer, Consumer<Edit> edits);

  /**
   * The pointer moves with the button down after a press the tool took.
   *
   * @param scene the document
   * @param pointer where
   * @param edits told the edits the drag makes
   */
  void drag(Scene scene, Pointer pointer, Consumer<Edit> edits);

  /**
   * The button is released after a press the tool took.
   *
   * @param scene the document
   * @param pointer where
   * @param edits told the edits the release makes
   */
  void release(Scene scene, Pointer pointer, Consumer<Edit> edits);

  /**
   * The pointer moves with no button down: the preview follows it.
   *
   * @param scene the document
   * @param pointer where
   */
  void move(Scene scene, Pointer pointer);

  /** Abandons what is in progress (Escape, or another tool chosen) and forgets the preview. */
  void cancel();

  /**
   * Whether a construction is in progress, which {@link #cancel} abandons.
   *
   * @return true between the first and the last click of a construction, or during a drag
   */
  boolean inProgress();

  /**
   * Paints the preview of the last event on the overlay.
   *
   * @param painter the overlay's painter, in world coordinates
   * @param theme the colours
   */
  void paint(Painter painter, CanvasTheme theme);
}
