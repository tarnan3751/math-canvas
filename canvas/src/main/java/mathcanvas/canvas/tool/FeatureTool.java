package mathcanvas.canvas.tool;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import mathcanvas.canvas.hit.Hit;
import mathcanvas.canvas.model.Feature;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;

/**
 * The roots tool and the extrema tool: a click on a function graph adds every feature of the tool's
 * kinds the graph has now in the window ({@link Edit.Find}). A click on anything else passes to the
 * view, which pans. The preview highlights the graph under the pointer.
 */
public final class FeatureTool implements Tool {

  private final List<Feature> features;
  private Scene scene;
  private ObjectId hovered;

  /**
   * A tool finding features.
   *
   * @param features the kinds it adds
   */
  public FeatureTool(List<Feature> features) {
    this.features = List.copyOf(features);
  }

  /**
   * The roots tool.
   *
   * @return it
   */
  public static FeatureTool roots() {
    return new FeatureTool(List.of(Feature.ROOT));
  }

  /**
   * The extrema tool: the minima and the maxima.
   *
   * @return it
   */
  public static FeatureTool extrema() {
    return new FeatureTool(List.of(Feature.MIN, Feature.MAX));
  }

  @Override
  public boolean press(Scene scene, Pointer pointer, Consumer<Edit> edits) {
    move(scene, pointer);
    if (hovered == null) {
      return false;
    }
    edits.accept(new Edit.Find(hovered, features));
    return true;
  }

  @Override
  public void drag(Scene scene, Pointer pointer, Consumer<Edit> edits) {}

  @Override
  public void release(Scene scene, Pointer pointer, Consumer<Edit> edits) {}

  @Override
  public void move(Scene scene, Pointer pointer) {
    this.scene = scene;
    hovered = graph(scene, pointer).orElse(null);
  }

  private static Optional<ObjectId> graph(Scene scene, Pointer pointer) {
    return pointer
        .picked()
        .filter(p -> p.hit().kind() == Hit.Kind.POLYLINE)
        .map(Pointer.Picked::id)
        .filter(id -> scene.canvas().object(id).orElse(null) instanceof FunctionGraph);
  }

  @Override
  public void cancel() {
    scene = null;
    hovered = null;
  }

  @Override
  public boolean inProgress() {
    return false;
  }

  @Override
  public void paint(Painter painter, CanvasTheme theme) {
    if (scene != null && hovered != null) {
      Preview.curve(painter, theme, scene.canvas(), hovered);
    }
  }
}
