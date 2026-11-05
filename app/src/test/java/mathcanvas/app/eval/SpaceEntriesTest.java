package mathcanvas.app.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import mathcanvas.app.document.DocumentState;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.GradientArrow;
import mathcanvas.canvas.model.ParametricSurface;
import mathcanvas.canvas.model.SpaceCurve;
import mathcanvas.canvas.model.Surface;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Definition;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

/** The 3D view's rows: surfaces with their ranges, and the refusals. */
class SpaceEntriesTest {

  private static final Environment ENV =
      Environment.EMPTY.with("a", new Definition.Value(Parser.parseExpression("5", Names.NONE)));

  private static Entries.Read read(String text, Optional<CanvasObject> target) {
    return Entries.read(text, target, true, ENV, Set.of(), Cancellation.NEVER);
  }

  private static Surface made(String text) {
    Entries.Read r = read(text, Optional.empty());
    assertThat(r.error()).isNull();
    return (Surface) r.command().apply(DocumentState.EMPTY).canvas().objects().getFirst();
  }

  private static Expr canonical(String text) {
    return Canonical.of(Parser.parseExpression(text, Names.NONE), Canonical.DEFAULT_CONTEXT);
  }

  private static Interval range(String lo, String hi) {
    return Interval.of(canonical(lo), canonical(hi), true, true);
  }

  @Test
  void aHeightIsASurfaceOverTheDefaultSquare() {
    Surface s = made("z = x^2 - y^2 + 0x");
    assertThat(s.body()).isEqualTo(canonical("x^2 - y^2"));
    assertThat(s.xDomain()).isEqualTo(Surface.DEFAULT_DOMAIN);
    assertThat(s.yDomain()).isEqualTo(Surface.DEFAULT_DOMAIN);
    assertThat(s.n()).isEqualTo(Surface.DEFAULT_N);
  }

  @Test
  void rangesFollowAtTheTopLevelsCommas() {
    Surface s = made("z = max(x, y), x in [-2, a], y ∈ [0, 2pi]");
    assertThat(s.body()).isEqualTo(canonical("max(x, y)"));
    assertThat(s.xDomain()).isEqualTo(range("-2", "a"));
    assertThat(s.yDomain()).isEqualTo(range("0", "2pi"));
    assertThat(SpaceEntries.parts("z = f(x, y), x in [1, 2]"))
        .isEqualTo(List.of("z = f(x, y)", " x in [1, 2]"));
  }

  @Test
  void aSurfacesRowKeepsItsRangesAndCellsUnlessGiven() {
    Surface was = made("z = x, x in [0, 1]").withN(200);
    Entries.Read r = read("z = y, y in [3, 4]", Optional.of(was));
    DocumentState s =
        r.command()
            .apply(
                DocumentState.EMPTY.withCanvas(
                    DocumentState.EMPTY.canvas().add(id -> was), java.util.Map.of()));
    Surface now = (Surface) s.canvas().objects().getFirst();
    assertThat(now.body()).isEqualTo(canonical("y"));
    assertThat(now.xDomain()).isEqualTo(range("0", "1"));
    assertThat(now.yDomain()).isEqualTo(range("3", "4"));
    assertThat(now.n()).isEqualTo(200);
  }

  @Test
  void whatIsNoSurfaceIsRefused() {
    assertThat(read("y = x^2", Optional.empty()).error())
        .startsWith(
            "this cannot be drawn in the 3D view, which draws a surface z = f(x, y), a vector");
    assertThat(read("z = z + x", Optional.empty()).error())
        .contains("cannot be drawn in the 3D view");
    assertThat(read("z = x, x = 3", Optional.empty()).error())
        .isEqualTo("a range is written name in [a, b], for example x in [-2, 2]");
    assertThat(read("z = x, x in [1, 2, 3]", Optional.empty()).error()).contains("name in [a, b]");
    assertThat(read("z = x, x in [1, 2], x in [3, 4]", Optional.empty()).error())
        .isEqualTo("the range of x is given twice");
    assertThat(read("z = x, t in [1, 2]", Optional.empty()).error())
        .isEqualTo("t is not a variable of this object; its variables are x, y");
    assertThat(read("z = (x", Optional.empty()).error()).isNotNull();
  }

  @Test
  void thePlanesBlankRowStillReadsThePlane() {
    Entries.Read r =
        Entries.read("y = x", Optional.empty(), false, ENV, Set.of(), Cancellation.NEVER);
    assertThat(r.command().apply(DocumentState.EMPTY).canvas().objects().getFirst())
        .isInstanceOf(FunctionGraph.class);
  }

  // Parametric surfaces, space curves and gradients.

  private static CanvasObject one(String text) {
    Entries.Read r = read(text, Optional.empty());
    assertThat(r.error()).isNull();
    return r.command().apply(DocumentState.EMPTY).canvas().objects().getFirst();
  }

  @Test
  void aVectorInUAndVIsAParametricSurfaceAndInTACurve() {
    ParametricSurface sphere =
        (ParametricSurface) one("[cos(u) sin(v), sin(u) sin(v), cos(v)], v in [0, pi/2]");
    assertThat(sphere.x()).isEqualTo(canonical("cos(u) sin(v)"));
    assertThat(sphere.uDomain()).isEqualTo(ParametricSurface.U_DOMAIN);
    assertThat(sphere.vDomain()).isEqualTo(range("0", "pi/2"));
    assertThat(sphere.n()).isEqualTo(Surface.DEFAULT_N);
    SpaceCurve helix = (SpaceCurve) one("⟨cos t, sin t, a t / 4⟩, t in [0, 6pi]");
    assertThat(helix.z()).isEqualTo(canonical("a t / 4"));
    assertThat(helix.domain()).isEqualTo(range("0", "6pi"));
    assertThat(((SpaceCurve) one("[t, t^2, 1]")).domain()).isEqualTo(ParametricSurface.U_DOMAIN);
    assertThat(read("[1, 2, 3]", Optional.empty()).error())
        .isEqualTo("a vector [x, y, z] here is a surface in u and v or a curve in t");
    assertThat(read("[t, t, t], u in [0, 1]", Optional.empty()).error())
        .isEqualTo("u is not a variable of this object; its variables are t");
  }

  @Test
  void aGradientIsTakenOnTheSurfaceOfItsBodyOrOnANewOne() {
    Entries.Read alone = read("grad(z = x^2 - y^2, 1, a)", Optional.empty());
    assertThat(alone.error()).isNull();
    DocumentState both = alone.command().apply(DocumentState.EMPTY);
    assertThat(both.canvas().objects()).hasSize(2);
    Surface s = (Surface) both.canvas().objects().get(0);
    GradientArrow g = (GradientArrow) both.canvas().objects().get(1);
    assertThat(g.surface()).isEqualTo(s.id());
    assertThat(g.y()).isEqualTo(canonical("a"));
    // with the surface on the canvas, only the gradient is added, on it
    Entries.Read on =
        Entries.read(
            "grad(z = x^2 - y^2 + 0, 2, 3)",
            Optional.empty(),
            true,
            both.canvas(),
            ENV,
            Set.of(),
            Cancellation.NEVER);
    DocumentState three = on.command().apply(both);
    assertThat(three.canvas().objects()).hasSize(3);
    assertThat(((GradientArrow) three.canvas().objects().get(2)).surface()).isEqualTo(s.id());
    // its row moves its point
    Entries.Read moved = read("[4, 5]", Optional.of(g));
    GradientArrow at =
        (GradientArrow)
            ((mathcanvas.app.document.Command.ReplaceObject) moved.command()).replacement();
    assertThat(at.x()).isEqualTo(canonical("4"));
    assertThat(at.surface()).isEqualTo(s.id());
  }
}
