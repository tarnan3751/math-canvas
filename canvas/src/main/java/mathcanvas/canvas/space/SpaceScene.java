package mathcanvas.canvas.space;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import mathcanvas.canvas.geom.Ticks;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.SpaceObject;
import mathcanvas.canvas.paint.Anchor;
import mathcanvas.canvas.paint.Arrowhead;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.Stroke;
import mathcanvas.canvas.paint.TextStyle;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.number.Real;

/**
 * What the 3D view draws, and its drawing (docs/reference/canvas.md, **The 3D view**). Built from a
 * canvas's geometry, it holds each visible object in space in the coordinates of the axes box's
 * cube, which it fits to their bounds; {@link #paint} draws them through any {@link Painter} whose
 * viewport maps world coordinates to the view's pixels one to one, so the screen and an SVG export
 * draw the same picture.
 *
 * <p>Each frame projects the vertices through the camera, collects the primitives (a surface's
 * quads, a space curve's segments, a gradient's arrow), drops those with a vertex undefined,
 * outside the box or behind the eye, sorts them by the depth of their centre, farthest first, and
 * paints them in that order (the painter's algorithm, with no splitting: a quad that crosses
 * another, as the two sheets of a self-intersecting parametric surface may, is drawn whole): each
 * quad filled in its object's colour shaded flat by one directional light fixed to the camera (its
 * face normal against the light, two-sided, a back face a fifth darker), then its edges on the
 * lattice of {@link #ORBIT_CELLS} cells a side stroked opaque in a darker shade. While the camera
 * moves the view draws the coarse mesh, every {@code n / 50}-th vertex, whose every edge is a
 * lattice edge, so the moving and the resting pictures show the same wireframe. The axes box is
 * drawn first, its three faces away from the eye edged and gridded at the ticks, and its tick
 * labels and axis names last, along the edges nearest the eye at the bottom and the leftmost
 * vertical edge. A segment is stroked in its curve's colour and width; a gradient is its point and,
 * unless the gradient is zero or its tip would be outside the box, an arrow {@link #ARROW} cube
 * units long along the direction of steepest ascent on the surface, {@code (f_x, f_y, f_x² + f_y²)}
 * mapped into the cube, its head in pixels; a gradient whose point is outside the box is not drawn.
 *
 * <p>{@link #hit} finds the curve or gradient point nearest a pixel, by projected distance;
 * surfaces are not hit. {@link #pick} finds the point of a surface {@code z = f(x, y)} drawn
 * frontmost under a pixel, for the Gradient tool. Neither knows what hides what: a curve behind a
 * surface is hit where it would be drawn.
 *
 * <p>Immutable; {@link #paint} allocates its own scratch, so a scene may be painted from any one
 * thread at a time.
 */
public final class SpaceScene {

  /** The cells a side of the coarse mesh drawn while the camera moves, and of the edge lattice. */
  public static final int ORBIT_CELLS = 50;

  /** How far outside the box, in cube units, a vertex still counts as inside: rounding. */
  static final double SLACK = 1e-6;

  /** The shades of a colour a face may take. */
  static final int SHADES = 64;

  /** The share of a face's light that does not depend on its angle to the light. */
  static final double AMBIENT = 0.25;

  /** How much darker a face seen from its back is. */
  static final double BACK = 0.8;

  /** How much darker an edge is than its face. */
  static final double EDGE = 0.6;

  /** The font size of the tick labels and the axis names. */
  static final double LABEL_SIZE = 12;

  /** How far a tick label stands off its edge, in pixels. */
  static final double LABEL_GAP = 14;

  /** How far beyond the end of its edge an axis name is, in cube units. */
  static final double NAME_BEYOND = 0.15;

  /** How far an axis name stands off its edge, in pixels. */
  static final double NAME_GAP = 30;

  /** How near two texts may be, in pixels, before the later is left out. */
  static final double CROWDED = 18;

  /** How far inside the view's edges a label's centre is kept, in pixels. */
  static final double INSET = 13;

  /** The length of a gradient's arrow, in cube units. */
  static final double ARROW = 0.3;

  /** The radius of a gradient's point, in pixels, before its width. */
  static final double POINT_RADIUS = 2;

  /** An empty scene in the default box. */
  public static final SpaceScene EMPTY =
      new SpaceScene(Box3.DEFAULT, List.of(), List.of(), List.of());

  /**
   * A surface as drawn: its vertices in the cube, NaN where undefined or outside the box, and its
   * shades.
   */
  record Sheet(
      ObjectId id,
      boolean graph,
      int nu,
      int nv,
      float[] x,
      float[] y,
      float[] z,
      Rgba[] faces,
      Stroke[] edges) {}

  /** A space curve as drawn: its points in the cube, NaN at gaps and outside the box. */
  record Strand(ObjectId id, float[] x, float[] y, float[] z, Stroke stroke) {}

  /**
   * A gradient as drawn: its point in the cube and the unit direction of its arrow there, or null
   * when the gradient is zero.
   */
  record Arrow(ObjectId id, double[] base, double[] direction, Rgba colour, double width) {}

  /** The point of a surface a pixel shows: its object and its world {@code x} and {@code y}. */
  public record Pick(ObjectId surface, double x, double y) {}

  private final Box3 box;
  private final List<Sheet> sheets;
  private final List<Strand> strands;
  private final List<Arrow> arrows;

  private SpaceScene(Box3 box, List<Sheet> sheets, List<Strand> strands, List<Arrow> arrows) {
    this.box = box;
    this.sheets = List.copyOf(sheets);
    this.strands = List.copyOf(strands);
    this.arrows = List.copyOf(arrows);
  }

  /**
   * The scene of a canvas: its visible objects in space, in the box fitting them.
   *
   * @param state the objects and their geometry
   * @param theme the colours
   * @return the scene
   */
  public static SpaceScene build(CanvasState state, CanvasTheme theme) {
    List<double[]> bounds = new ArrayList<>();
    List<CanvasObject> shown = new ArrayList<>();
    for (CanvasObject o : state.objects()) {
      if (!(o instanceof SpaceObject) || !o.style().visible()) {
        continue;
      }
      Optional<double[]> b =
          state.geometryOf(o.id()) instanceof Geometry.Spatial g
              ? switch (g) {
                case Geometry.Mesh m -> m.bounds();
                case Geometry.Path3 c -> c.bounds();
                case Geometry.Gradient d ->
                    Optional.of(new double[] {d.x(), d.x(), d.y(), d.y(), d.z(), d.z()});
              }
              : Optional.empty();
      if (b.isPresent()) {
        bounds.add(b.get());
        shown.add(o);
      }
    }
    Box3 box = Box3.fit(bounds);
    List<Sheet> sheets = new ArrayList<>();
    List<Strand> strands = new ArrayList<>();
    List<Arrow> arrows = new ArrayList<>();
    for (CanvasObject o : shown) {
      Rgba colour = theme.series().get(o.style().colour());
      switch ((Geometry.Spatial) state.geometryOf(o.id())) {
        case Geometry.Mesh m -> sheets.add(sheet(o.id(), m, box, colour));
        case Geometry.Path3 c ->
            strands.add(
                new Strand(
                    o.id(),
                    cube(c.xs(), c.ys(), c.zs(), box, 0),
                    cube(c.xs(), c.ys(), c.zs(), box, 1),
                    cube(c.xs(), c.ys(), c.zs(), box, 2),
                    Stroke.solid(colour, o.style().width())));
        case Geometry.Gradient g ->
            arrow(o.id(), g, box, colour, o.style().width()).ifPresent(arrows::add);
      }
    }
    return new SpaceScene(box, sheets, strands, arrows);
  }

  /** One coordinate of points in the cube, NaN for a point outside the box. */
  private static float[] cube(float[] xs, float[] ys, float[] zs, Box3 box, int axis) {
    float[] out = new float[xs.length];
    for (int k = 0; k < xs.length; k++) {
      double cx = box.toCube(0, xs[k]);
      double cy = box.toCube(1, ys[k]);
      double cz = box.toCube(2, zs[k]);
      boolean inside =
          Math.abs(cx) <= 1 + SLACK && Math.abs(cy) <= 1 + SLACK && Math.abs(cz) <= 1 + SLACK;
      out[k] = inside ? (float) (axis == 0 ? cx : axis == 1 ? cy : cz) : Float.NaN;
    }
    return out;
  }

  /**
   * A gradient's arrow: the direction of steepest ascent on the surface, {@code (f_x, f_y, f_x² +
   * f_y²)}, a tangent vector, mapped into the cube axis by axis (so it stays tangent to the surface
   * as drawn) and made a unit there. A gradient whose point is outside the box is not drawn, and
   * its arrow is left out when its tip would be.
   */
  private static Optional<Arrow> arrow(
      ObjectId id, Geometry.Gradient g, Box3 box, Rgba colour, double w) {
    double[] base = {box.toCube(0, g.x()), box.toCube(1, g.y()), box.toCube(2, g.z())};
    if (!inside(base[0], base[1], base[2])) {
      return Optional.empty();
    }
    double dx = g.fx() * 2 / (box.xMax() - box.xMin());
    double dy = g.fy() * 2 / (box.yMax() - box.yMin());
    double dz = (g.fx() * g.fx() + g.fy() * g.fy()) * 2 / (box.zMax() - box.zMin());
    double n = Math.sqrt(dx * dx + dy * dy + dz * dz);
    double[] direction = n > 0 && Double.isFinite(n) ? new double[] {dx / n, dy / n, dz / n} : null;
    if (direction != null
        && !inside(
            base[0] + ARROW * direction[0],
            base[1] + ARROW * direction[1],
            base[2] + ARROW * direction[2])) {
      direction = null;
    }
    return Optional.of(new Arrow(id, base, direction, colour, w));
  }

  /** Whether a point of the cube is in the box, to rounding. */
  private static boolean inside(double cx, double cy, double cz) {
    return Math.abs(cx) <= 1 + SLACK && Math.abs(cy) <= 1 + SLACK && Math.abs(cz) <= 1 + SLACK;
  }

  private static Sheet sheet(ObjectId id, Geometry.Mesh m, Box3 box, Rgba colour) {
    int size = m.xs().length;
    float[] x = new float[size];
    float[] y = new float[size];
    float[] z = new float[size];
    for (int k = 0; k < size; k++) {
      double cx = box.toCube(0, m.xs()[k]);
      double cy = box.toCube(1, m.ys()[k]);
      double cz = box.toCube(2, m.zs()[k]);
      boolean inside =
          Math.abs(cx) <= 1 + SLACK && Math.abs(cy) <= 1 + SLACK && Math.abs(cz) <= 1 + SLACK;
      x[k] = inside ? (float) cx : Float.NaN;
      y[k] = inside ? (float) cy : Float.NaN;
      z[k] = inside ? (float) cz : Float.NaN;
    }
    Rgba[] faces = new Rgba[SHADES];
    Stroke[] edges = new Stroke[SHADES];
    for (int s = 0; s < SHADES; s++) {
      double light = (s + 0.5) / SHADES;
      faces[s] = scaled(colour, light);
      edges[s] = Stroke.solid(scaled(colour, light * EDGE), 1);
    }
    return new Sheet(id, m.height().isPresent(), m.nu(), m.nv(), x, y, z, faces, edges);
  }

  private static Rgba scaled(Rgba c, double f) {
    return new Rgba(c.red() * f, c.green() * f, c.blue() * f, 1);
  }

  /**
   * The axes box the scene fits.
   *
   * @return the box
   */
  public Box3 box() {
    return box;
  }

  /**
   * The quads a frame of this scene paints.
   *
   * @param coarse whether the camera is moving, so the coarse mesh is drawn
   * @return the number of quads before any is dropped
   */
  public int quads(boolean coarse) {
    int n = 0;
    for (Sheet s : sheets) {
      int su = coarse ? step(s.nu()) : 1;
      int sv = coarse ? step(s.nv()) : 1;
      n += ceil(s.nu(), su) * ceil(s.nv(), sv);
    }
    return n;
  }

  private static int step(int cells) {
    return Math.max(1, cells / ORBIT_CELLS);
  }

  private static int ceil(int a, int b) {
    return (a + b - 1) / b;
  }

  /**
   * Paints the scene: the axes box, the objects in depth order, the labels.
   *
   * @param p the painter, whose viewport's world coordinates are the view's pixels (its {@code y}
   *     up from the bottom)
   * @param camera the camera
   * @param theme the colours of the box and the labels
   * @param coarse whether to draw the coarse mesh, while the camera moves
   * @param labelWidth a label's width in pixels, for the ticks' spacing
   */
  public void paint(
      Painter p,
      Camera camera,
      CanvasTheme theme,
      boolean coarse,
      ToDoubleFunction<String> labelWidth) {
    Viewport vp = p.viewport();
    Projection projection = new Projection(camera, vp.width(), vp.height());
    Frame f = new Frame(p, projection);
    double[] eye = camera.eye();
    int[] far = new int[3];
    for (int a = 0; a < 3; a++) {
      far[a] = eye[a] >= 0 ? -1 : 1;
    }
    Ticks[] ticks = new Ticks[3];
    double[][] labelEdges = labelEdges(projection, far);
    for (int a = 0; a < 3; a++) {
      double[] e = labelEdges[a];
      double[] s0 = new double[3];
      double[] s1 = new double[3];
      projection.project(e[0], e[1], e[2], s0);
      projection.project(e[3], e[4], e[5], s1);
      double pixels = Math.max(1, Math.hypot(s1[0] - s0[0], s1[1] - s0[1]));
      ticks[a] = Ticks.forAxis(box.min(a), box.max(a), pixels, labelWidth);
    }
    backFaces(f, far, ticks, theme);
    objects(f, coarse);
    labels(f, labelEdges, ticks, theme);
  }

  /** The painter, the projection, and the mapping of pixels to the painter's world. */
  private record Frame(Painter p, Projection projection) {

    double wx(double px) {
      return p.viewport().toWorldX(px);
    }

    double wy(double py) {
      return p.viewport().toWorldY(py);
    }

    /** A segment between two points of the cube, unless an end is behind the eye. */
    void segment(double[] a, double[] b, Stroke stroke) {
      double[] s0 = new double[3];
      double[] s1 = new double[3];
      projection.project(a[0], a[1], a[2], s0);
      projection.project(b[0], b[1], b[2], s1);
      if (s0[2] < Projection.NEAR || s1[2] < Projection.NEAR) {
        return;
      }
      p.beginPath();
      p.moveTo(wx(s0[0]), wy(s0[1]));
      p.lineTo(wx(s1[0]), wy(s1[1]));
      p.stroke(stroke);
    }
  }

  /**
   * The edge each axis's tick labels go along, as {@code {x0, y0, z0, x1, y1, z1}} in the cube:
   * {@code x}'s and {@code y}'s at the bottom on the side nearest the eye, {@code z}'s the vertical
   * edge furthest left on the screen.
   */
  private static double[][] labelEdges(Projection projection, int[] far) {
    double[][] out = new double[3][];
    out[0] = new double[] {-1, -far[1], -1, 1, -far[1], -1};
    out[1] = new double[] {-far[0], -1, -1, -far[0], 1, -1};
    double best = Double.POSITIVE_INFINITY;
    double[] s = new double[3];
    for (int sx = -1; sx <= 1; sx += 2) {
      for (int sy = -1; sy <= 1; sy += 2) {
        projection.project(sx, sy, 0, s);
        if (s[0] < best) {
          best = s[0];
          out[2] = new double[] {sx, sy, -1, sx, sy, 1};
        }
      }
    }
    return out;
  }

  /** The three faces of the box away from the eye: their edges and their grids at the ticks. */
  private void backFaces(Frame f, int[] far, Ticks[] ticks, CanvasTheme theme) {
    Stroke edge = Stroke.solid(theme.axis(), 1);
    Stroke grid = Stroke.solid(theme.grid(), 1);
    for (int a = 0; a < 3; a++) {
      int b = (a + 1) % 3;
      int c = (a + 2) % 3;
      // the grid across the face at the ticks of its two other axes
      for (int other : new int[] {b, c}) {
        int across = other == b ? c : b;
        Ticks t = ticks[other];
        for (long i = t.first(box.min(other)); i <= t.last(box.max(other)); i++) {
          double v = box.toCube(other, t.value(i));
          double[] p0 = new double[3];
          double[] p1 = new double[3];
          p0[a] = far[a];
          p1[a] = far[a];
          p0[other] = v;
          p1[other] = v;
          p0[across] = -1;
          p1[across] = 1;
          f.segment(p0, p1, grid);
        }
      }
      // the face's four edges
      for (int sb = -1; sb <= 1; sb += 2) {
        double[] p0 = new double[3];
        double[] p1 = new double[3];
        p0[a] = far[a];
        p1[a] = far[a];
        p0[b] = sb;
        p1[b] = sb;
        p0[c] = -1;
        p1[c] = 1;
        f.segment(p0, p1, edge);
        double[] q0 = new double[3];
        double[] q1 = new double[3];
        q0[a] = far[a];
        q1[a] = far[a];
        q0[c] = sb;
        q1[c] = sb;
        q0[b] = -1;
        q1[b] = 1;
        f.segment(q0, q1, edge);
      }
    }
  }

  /**
   * The tick labels and the axis names, after everything else: each name beyond the far end of its
   * edge, and a tick label left out where it would sit on one already written (where two edges meet
   * at a corner).
   */
  private void labels(Frame f, double[][] edges, Ticks[] ticks, CanvasTheme theme) {
    Projection projection = f.projection();
    double[] centre = new double[3];
    projection.project(0, 0, 0, centre);
    TextStyle style = new TextStyle(LABEL_SIZE, theme.text());
    Anchor middle = new Anchor(Anchor.Horizontal.MIDDLE, Anchor.Vertical.MIDDLE);
    String[] names = {"x", "y", "z"};
    List<double[]> placed = new ArrayList<>();
    for (int a = 0; a < 3; a++) {
      double[] e = edges[a];
      Ticks t = ticks[a];
      for (long i = t.first(box.min(a)); i <= t.last(box.max(a)); i++) {
        double[] at = {e[0], e[1], e[2]};
        at[a] = box.toCube(a, t.value(i));
        label(f, at, centre, LABEL_GAP, t.label(i), middle, style, placed);
      }
      double[] end = {e[3], e[4], e[5]};
      end[a] = 1 + NAME_BEYOND;
      label(f, end, centre, NAME_GAP, names[a], middle, style, placed);
    }
  }

  /**
   * A text at a point of the cube, moved a gap in pixels away from the box's centre on the screen
   * and kept {@link #INSET} pixels inside the view, unless it would sit within {@link #CROWDED}
   * pixels of a text already placed.
   */
  private static void label(
      Frame f,
      double[] at,
      double[] centre,
      double gap,
      String text,
      Anchor anchor,
      TextStyle style,
      List<double[]> placed) {
    double[] s = new double[3];
    f.projection().project(at[0], at[1], at[2], s);
    if (s[2] < Projection.NEAR) {
      return;
    }
    double dx = s[0] - centre[0];
    double dy = s[1] - centre[1];
    double n = Math.hypot(dx, dy);
    if (n == 0) {
      dx = 0;
      dy = 1;
      n = 1;
    }
    // moved out from the centre, but kept inside the view, so no label is cut at its edge
    Projection pr = f.projection();
    double x = Math.clamp(s[0] + gap * dx / n, INSET, Math.max(INSET, pr.width() - INSET));
    double y = Math.clamp(s[1] + gap * dy / n, INSET, Math.max(INSET, pr.height() - INSET));
    for (double[] q : placed) {
      if (Math.hypot(q[0] - x, q[1] - y) < CROWDED) {
        return;
      }
    }
    placed.add(new double[] {x, y});
    f.p().text(text, f.wx(s[0]), f.wy(s[1]), x - s[0], y - s[1], anchor, style);
  }

  /** The vertices of every object projected once a frame: pixels and depths, per object. */
  private record Projected(float[][] sx, float[][] sy, float[][] sd) {}

  private Projected project(Projection projection, Frame f) {
    int count = sheets.size() + strands.size();
    float[][] sx = new float[count][];
    float[][] sy = new float[count][];
    float[][] sd = new float[count][];
    double[] out = new double[3];
    for (int k = 0; k < count; k++) {
      float[] x = k < sheets.size() ? sheets.get(k).x() : strands.get(k - sheets.size()).x();
      float[] y = k < sheets.size() ? sheets.get(k).y() : strands.get(k - sheets.size()).y();
      float[] z = k < sheets.size() ? sheets.get(k).z() : strands.get(k - sheets.size()).z();
      sx[k] = new float[x.length];
      sy[k] = new float[x.length];
      sd[k] = new float[x.length];
      for (int v = 0; v < x.length; v++) {
        projection.project(x[v], y[v], z[v], out);
        sx[k][v] = (float) (f == null ? out[0] : f.wx(out[0]));
        sy[k][v] = (float) (f == null ? out[1] : f.wy(out[1]));
        sd[k][v] = (float) out[2];
      }
    }
    return new Projected(sx, sy, sd);
  }

  /** The objects' primitives in depth order: quads, then segments, then arrows, by index. */
  private void objects(Frame f, boolean coarse) {
    int quads = quads(coarse);
    int segments = 0;
    for (Strand c : strands) {
      segments += Math.max(0, c.x().length - 1);
    }
    int total = quads + segments + arrows.size();
    if (total == 0) {
      return;
    }
    Projection projection = f.projection();
    Projected pr = project(projection, f);
    long[] keys = new long[total];
    int count = 0;
    int[] starts = new int[sheets.size() + 1];
    for (int k = 0; k < sheets.size(); k++) {
      Sheet sheet = sheets.get(k);
      int su = coarse ? step(sheet.nu()) : 1;
      int sv = coarse ? step(sheet.nv()) : 1;
      int cu = ceil(sheet.nu(), su);
      int cv = ceil(sheet.nv(), sv);
      int row = sheet.nu() + 1;
      float[] sd = pr.sd()[k];
      for (int j = 0; j < cv; j++) {
        int v0 = j * sv;
        int v1 = Math.min(v0 + sv, sheet.nv());
        for (int i = 0; i < cu; i++) {
          int u0 = i * su;
          int u1 = Math.min(u0 + su, sheet.nu());
          float a = sd[v0 * row + u0];
          float b = sd[v0 * row + u1];
          float c = sd[v1 * row + u1];
          float d = sd[v1 * row + u0];
          // NaN (undefined, outside the box) fails the test, as a vertex behind the eye does
          if (!(Math.min(Math.min(a, b), Math.min(c, d)) >= Projection.NEAR)) {
            continue;
          }
          keys[count++] = key((a + b + c + d) / 4, starts[k] + j * cu + i);
        }
      }
      starts[k + 1] = starts[k] + cu * cv;
    }
    int[] strandStarts = new int[strands.size() + 1];
    strandStarts[0] = quads;
    for (int k = 0; k < strands.size(); k++) {
      float[] sd = pr.sd()[sheets.size() + k];
      for (int v = 0; v + 1 < sd.length; v++) {
        if (sd[v] >= Projection.NEAR && sd[v + 1] >= Projection.NEAR) {
          keys[count++] = key((sd[v] + sd[v + 1]) / 2, strandStarts[k] + v);
        }
      }
      strandStarts[k + 1] = strandStarts[k] + Math.max(0, sd.length - 1);
    }
    double[] s = new double[3];
    for (int k = 0; k < arrows.size(); k++) {
      double[] b = arrows.get(k).base();
      projection.project(b[0], b[1], b[2], s);
      if (s[2] >= Projection.NEAR) {
        keys[count++] = key((float) s[2], quads + segments + k);
      }
    }
    Arrays.sort(keys, 0, count);
    double[] light = projection.light();
    for (int q = 0; q < count; q++) {
      int index = (int) keys[q];
      if (index < quads) {
        int k = 0;
        while (starts[k + 1] <= index) {
          k++;
        }
        quad(f, pr, k, index - starts[k], coarse, light);
      } else if (index < quads + segments) {
        int k = 0;
        while (strandStarts[k + 1] <= index) {
          k++;
        }
        int v = index - strandStarts[k];
        int o = sheets.size() + k;
        Painter p = f.p();
        p.beginPath();
        p.moveTo(pr.sx()[o][v], pr.sy()[o][v]);
        p.lineTo(pr.sx()[o][v + 1], pr.sy()[o][v + 1]);
        p.stroke(strands.get(k).stroke());
      } else {
        arrow(f, arrows.get(index - quads - segments));
      }
    }
  }

  private static long key(float depth, int index) {
    // farthest first: a positive float's bits grow with it, so their complement sorts the depths
    // descending; the index breaks ties
    return ((long) (Integer.MAX_VALUE - Float.floatToIntBits(depth)) << 32) | index;
  }

  /** A quad of a sheet: filled in its shade, and its lattice edges. */
  private void quad(Frame f, Projected pr, int k, int local, boolean coarse, double[] light) {
    Sheet sheet = sheets.get(k);
    Projection projection = f.projection();
    int su = coarse ? step(sheet.nu()) : 1;
    int sv = coarse ? step(sheet.nv()) : 1;
    int lu = coarse ? 1 : step(sheet.nu());
    int lv = coarse ? 1 : step(sheet.nv());
    int cu = ceil(sheet.nu(), su);
    int i = local % cu;
    int j = local / cu;
    int row = sheet.nu() + 1;
    int u0 = i * su;
    int u1 = Math.min(u0 + su, sheet.nu());
    int v0 = j * sv;
    int v1 = Math.min(v0 + sv, sheet.nv());
    int a = v0 * row + u0;
    int b = v0 * row + u1;
    int c = v1 * row + u1;
    int d = v1 * row + u0;
    float[] x = sheet.x();
    float[] y = sheet.y();
    float[] z = sheet.z();
    // the face normal, the cross product of the diagonals
    double ax = x[c] - x[a];
    double ay = y[c] - y[a];
    double az = z[c] - z[a];
    double bx = x[d] - x[b];
    double by = y[d] - y[b];
    double bz = z[d] - z[b];
    double nx = ay * bz - az * by;
    double ny = az * bx - ax * bz;
    double nz = ax * by - ay * bx;
    double norm = Math.sqrt(nx * nx + ny * ny + nz * nz);
    double lit = norm == 0 ? 1 : Math.abs(nx * light[0] + ny * light[1] + nz * light[2]) / norm;
    double shade = AMBIENT + (1 - AMBIENT) * lit;
    double[] toEye = new double[3];
    projection.toEye(x[a], y[a], z[a], toEye);
    if (nx * toEye[0] + ny * toEye[1] + nz * toEye[2] < 0) {
      shade *= BACK;
    }
    int level = (int) Math.min(SHADES - 1, shade * SHADES);
    float[] px = pr.sx()[k];
    float[] py = pr.sy()[k];
    Painter p = f.p();
    p.beginPath();
    p.moveTo(px[a], py[a]);
    p.lineTo(px[b], py[b]);
    p.lineTo(px[c], py[c]);
    p.lineTo(px[d], py[d]);
    p.closePath();
    p.fill(sheet.faces()[level]);
    // the lattice's edges along this quad: its first row and column when they lie on the lattice,
    // and the mesh's last row and column
    boolean bottom = (v0 / sv) % lv == 0;
    boolean left = (u0 / su) % lu == 0;
    boolean top = v1 == sheet.nv();
    boolean right = u1 == sheet.nu();
    if (bottom || left || top || right) {
      p.beginPath();
      if (bottom) {
        p.moveTo(px[a], py[a]);
        p.lineTo(px[b], py[b]);
      }
      if (right) {
        p.moveTo(px[b], py[b]);
        p.lineTo(px[c], py[c]);
      }
      if (top) {
        p.moveTo(px[c], py[c]);
        p.lineTo(px[d], py[d]);
      }
      if (left) {
        p.moveTo(px[d], py[d]);
        p.lineTo(px[a], py[a]);
      }
      p.stroke(sheet.edges()[level]);
    }
  }

  /** A gradient: its point, and its arrow unless the gradient is zero or its tip is outside. */
  private static void arrow(Frame f, Arrow a) {
    Projection projection = f.projection();
    Painter p = f.p();
    double[] b = a.base();
    double[] s0 = new double[3];
    projection.project(b[0], b[1], b[2], s0);
    if (a.direction() != null) {
      double[] d = a.direction();
      double[] s1 = new double[3];
      projection.project(b[0] + ARROW * d[0], b[1] + ARROW * d[1], b[2] + ARROW * d[2], s1);
      if (s1[2] >= Projection.NEAR) {
        double x0 = f.wx(s0[0]);
        double y0 = f.wy(s0[1]);
        double x1 = f.wx(s1[0]);
        double y1 = f.wy(s1[1]);
        p.beginPath();
        p.moveTo(x0, y0);
        p.lineTo(x1, y1);
        p.stroke(Stroke.solid(a.colour(), a.width()));
        Arrowhead.paint(p, x1, y1, x1 - x0, y1 - y0, 8 + 2 * a.width(), a.colour());
      }
    }
    p.point(f.wx(s0[0]), f.wy(s0[1]), POINT_RADIUS + a.width(), a.colour());
  }

  /**
   * The curve or gradient drawn nearest a pixel, within a tolerance: a gradient's point or a
   * curve's vertex, by its projected distance; surfaces are not hit.
   *
   * @param projection the view's projection
   * @param px the pixel's {@code x}
   * @param py the pixel's {@code y}, down
   * @param tolerance the distance in pixels
   * @return the object, or empty
   */
  public Optional<ObjectId> hit(Projection projection, double px, double py, double tolerance) {
    double best = tolerance;
    ObjectId found = null;
    double[] s = new double[3];
    for (Arrow a : arrows) {
      projection.project(a.base()[0], a.base()[1], a.base()[2], s);
      double d = Math.hypot(s[0] - px, s[1] - py);
      if (s[2] >= Projection.NEAR && d <= best) {
        best = d;
        found = a.id();
      }
    }
    if (found != null) {
      return Optional.of(found); // a point first, as on the plane
    }
    for (Strand c : strands) {
      for (int v = 0; v < c.x().length; v++) {
        projection.project(c.x()[v], c.y()[v], c.z()[v], s);
        double d = Math.hypot(s[0] - px, s[1] - py);
        if (s[2] >= Projection.NEAR && d <= best) {
          best = d;
          found = c.id();
        }
      }
    }
    return Optional.ofNullable(found);
  }

  /**
   * The point of a surface {@code z = f(x, y)} drawn frontmost under a pixel, in the full mesh: the
   * nearest of the quads whose projection holds the pixel, the point found within it by the pixel's
   * barycentric coordinates in one of its two triangles.
   *
   * @param projection the view's projection
   * @param px the pixel's {@code x}
   * @param py the pixel's {@code y}, down
   * @return the surface and the world {@code x} and {@code y} there, or empty
   */
  public Optional<Pick> pick(Projection projection, double px, double py) {
    Projected pr = project(projection, null);
    double nearest = Double.POSITIVE_INFINITY;
    Pick out = null;
    for (int k = 0; k < sheets.size(); k++) {
      Sheet sheet = sheets.get(k);
      if (!sheet.graph()) {
        continue;
      }
      int row = sheet.nu() + 1;
      float[] sx = pr.sx()[k];
      float[] sy = pr.sy()[k];
      float[] sd = pr.sd()[k];
      for (int j = 0; j < sheet.nv(); j++) {
        for (int i = 0; i < sheet.nu(); i++) {
          int a = j * row + i;
          int[][] triangles = {{a, a + 1, a + row + 1}, {a, a + row + 1, a + row}};
          for (int[] t : triangles) {
            double[] w = barycentric(sx, sy, t, px, py);
            if (w == null) {
              continue;
            }
            double depth = w[0] * sd[t[0]] + w[1] * sd[t[1]] + w[2] * sd[t[2]];
            if (depth >= Projection.NEAR && depth < nearest) {
              nearest = depth;
              double cx = 0;
              double cy = 0;
              for (int m = 0; m < 3; m++) {
                cx += w[m] * sheet.x()[t[m]];
                cy += w[m] * sheet.y()[t[m]];
              }
              out = new Pick(sheet.id(), box.toWorld(0, cx), box.toWorld(1, cy));
            }
          }
        }
      }
    }
    return Optional.ofNullable(out);
  }

  /**
   * A picked coordinate as the decimal an object keeps, as a click on the plane is kept: rounded to
   * the power of ten at or below a thousandth of the box's extent along its axis, so a pick gives
   * {@code 1.25}, not {@code 1.2498046875}.
   *
   * @param value the coordinate
   * @param axis 0 for {@code x}, 1 for {@code y}, 2 for {@code z}
   * @return the decimal
   */
  public Expr number(double value, int axis) {
    int exponent = (int) Math.floor(Math.log10((box.max(axis) - box.min(axis)) / 1000));
    BigDecimal step = BigDecimal.ONE.scaleByPowerOfTen(exponent);
    BigDecimal rounded =
        new BigDecimal(value)
            .divide(step, MathContext.DECIMAL64)
            .setScale(0, RoundingMode.HALF_EVEN)
            .multiply(step);
    return new Num(Real.of(rounded));
  }

  /** The barycentric weights of a pixel in a projected triangle, or null outside it. */
  private static double[] barycentric(float[] sx, float[] sy, int[] t, double px, double py) {
    double x0 = sx[t[0]];
    double y0 = sy[t[0]];
    double x1 = sx[t[1]];
    double y1 = sy[t[1]];
    double x2 = sx[t[2]];
    double y2 = sy[t[2]];
    double det = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2);
    if (!(Math.abs(det) > 0)) {
      return null; // degenerate, or a vertex undefined
    }
    double w0 = ((y1 - y2) * (px - x2) + (x2 - x1) * (py - y2)) / det;
    double w1 = ((y2 - y0) * (px - x2) + (x0 - x2) * (py - y2)) / det;
    double w2 = 1 - w0 - w1;
    return w0 >= 0 && w1 >= 0 && w2 >= 0 ? new double[] {w0, w1, w2} : null;
  }
}
