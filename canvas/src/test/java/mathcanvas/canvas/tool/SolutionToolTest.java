package mathcanvas.canvas.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mathcanvas.canvas.hit.Hit;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Recomputes;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.SlopeField;
import mathcanvas.canvas.model.SolutionCurve;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Window;
import mathcanvas.canvas.sample.Samplers;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

/** The solution tool over scripted clicks, on a canvas recomputed with the real sampler. */
class SolutionToolTest {

  private static final Window W = new Window(-4, 4, -4, 4);
  private static final Sampler SAMPLER = new Samplers().over(Environment.EMPTY, Settings.DEFAULT);
  private static final Style S = Style.DEFAULT;

  private static CanvasState recomputed(CanvasState c) {
    return c.recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
  }

  private static SlopeField field(ObjectId id, String slope) {
    return new SlopeField(id, S, Parser.parseExpression(slope, Names.NONE), 20);
  }

  private static Scene scene(CanvasState c) {
    return new Scene(c, W, Scene.Slopes.NONE);
  }

  private static Pointer on(ObjectId id, Hit.Kind kind, double x, double y) {
    Hit hit = new Hit(0, kind, kind == Hit.Kind.POINT ? -1 : 0, 0, x, y, 1);
    return new Pointer(x, y, Optional.of(new Pointer.Picked(id, hit)));
  }

  private static CanvasState apply(CanvasState c, Edit e) {
    CanvasState out = c;
    for (CanvasObject o : ((Edit.Add) e).made(c.nextObjectId())) {
      out = out.add(id -> o);
    }
    return recomputed(out);
  }

  @Test
  void aClickAddsANamedPointAndTheSolutionThroughItAsOneStep() {
    CanvasState c = recomputed(CanvasState.EMPTY.add(id -> field(id, "x - y")));
    SolutionTool tool = new SolutionTool();
    List<Edit> edits = new ArrayList<>();
    assertThat(tool.press(scene(c), Pointer.at(1, 2), edits::add)).isTrue();
    assertThat(edits).hasSize(1);
    Edit.Add add = (Edit.Add) edits.getFirst();
    assertThat(add.objects()).extracting(Edit.New::named).containsExactly(true, false);
    c = apply(c, add);
    assertThat(c.object(new ObjectId(2))).contains(new FreePoint(new ObjectId(2), S, 1, 2));
    assertThat(c.object(new ObjectId(3)))
        .contains(new SolutionCurve(new ObjectId(3), S, new ObjectId(1), new ObjectId(2)));
    assertThat(c.geometryOf(new ObjectId(3))).isInstanceOf(Geometry.Curve.class);

    // dragging on moves the new point
    assertThat(tool.inProgress()).isTrue();
    edits.clear();
    tool.drag(scene(c), Pointer.at(0, -1), edits::add);
    assertThat(edits).containsExactly(new Edit.Move(new ObjectId(2), 0, -1, Double.NaN));
    tool.release(scene(c), Pointer.at(0, -1), edits::add);
    assertThat(tool.inProgress()).isFalse();
  }

  @Test
  void aClickOnAPointTakesItAndTheFieldIsTheOneClickedOrTheLast() {
    CanvasState c =
        recomputed(
            CanvasState.EMPTY
                .add(id -> field(id, "x"))
                .add(id -> new FreePoint(id, S, 1, 1))
                .add(id -> field(id, "y")));
    SolutionTool tool = new SolutionTool();
    List<Edit> edits = new ArrayList<>();
    tool.press(scene(c), on(new ObjectId(2), Hit.Kind.POINT, 1, 1), edits::add);
    CanvasState d = apply(c, edits.getFirst());
    // the last slope field drawn, through the existing point, no new point
    assertThat(d.objects()).hasSize(4);
    assertThat(d.object(new ObjectId(4)))
        .contains(new SolutionCurve(new ObjectId(4), S, new ObjectId(3), new ObjectId(2)));
    assertThat(tool.inProgress()).isFalse();

    // a click on the first field's marks uses it
    edits.clear();
    tool.press(scene(c), on(new ObjectId(1), Hit.Kind.POLYLINE, -1, 0), edits::add);
    CanvasState e = apply(c, edits.getFirst());
    assertThat(e.object(new ObjectId(5)))
        .contains(new SolutionCurve(new ObjectId(5), S, new ObjectId(1), new ObjectId(4)));
  }

  @Test
  void withNoSlopeFieldDrawnAPressPassesToTheView() {
    SolutionTool tool = new SolutionTool();
    List<Edit> edits = new ArrayList<>();
    assertThat(tool.press(scene(CanvasState.EMPTY), Pointer.at(0, 0), edits::add)).isFalse();
    CanvasState hidden =
        CanvasState.EMPTY.add(id -> field(id, "x").withStyle(new Style(0, 2, false, false, false)));
    assertThat(tool.press(scene(hidden), Pointer.at(0, 0), edits::add)).isFalse();
    assertThat(edits).isEmpty();
  }
}
