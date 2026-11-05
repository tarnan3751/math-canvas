package mathcanvas.canvas.tool;

import java.util.function.Consumer;

/**
 * What the tools a click drives share: the scene and the snap of the last event, which a move
 * records, and a drag and a release that do nothing. A tool that drags, or that snaps otherwise,
 * overrides them.
 */
abstract class ClickTool implements Tool {

  /** The document at the last event, or null before one. */
  Scene scene;

  /** What a click at the last event would use, or null before one. */
  Snap snap;

  @Override
  public void drag(Scene scene, Pointer pointer, Consumer<Edit> edits) {}

  @Override
  public void release(Scene scene, Pointer pointer, Consumer<Edit> edits) {}

  @Override
  public void move(Scene scene, Pointer pointer) {
    this.scene = scene;
    this.snap = Snap.of(scene, pointer);
  }
}
