package mathcanvas.canvas.space;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToDoubleFunction;
import mathcanvas.canvas.geom.Ticks;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.SpaceObject;
import mathcanvas.canvas.paint.Anchor;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.Stroke;
import mathcanvas.canvas.paint.TextStyle;

/**
 * What the 3D view draws, and its drawing (docs/reference/canvas.md, **The 3D view**). Built from a
 * canvas's geometry, it holds each visible object in space in the coordinates of the axes box's
 * cube, which it fits to their bounds; {@link #paint} draws them through any {@link Painter} whose
 * viewport maps world coordinates to the view's pixels one to one, so the screen and an SVG export
 * draw the same picture.
 *
 * <p>Each frame projects the vertices through the camera, collects the primitives (a surface's
 * quads), drops those with a vertex undefined, outside the box or behind the eye, sorts them by the
 * depth of their centre, farthest first, and paints them in that order (the painter's algorithm):
 * each quad filled in its object's colour shaded flat by one directional light fixed to the camera
 * (its face normal against the light, two-sided, a back face a fifth darker), then its edges on the
 * lattice of {@link #ORBIT_CELLS} cells a side stroked opaque in a darker shade. While the camera
 * moves the view draws the coarse mesh, every {@code n / 50}-th vertex, whose every edge is a
 * lattice edge, so the moving and the resting pictures show the same wireframe. The axes box is
 * drawn first, its three faces away from the eye edged and gridded at the ticks, and its tick
 * labels and axis names last, along the edges nearest the eye at the bottom and the leftmost
 * vertical edge.
 *
 * <p>Immutable; {@link #paint} allocates its own scratch, so a scene may be painted from any one
 * thread at a time.
 */
public final class SpaceScene {

  /** The cells a side of the coarse mesh drawn while the camera moves, and of the edge lattice. */
  public static final int ORBIT_CELLS = 50;

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

  /** How far an axis name stands off its edge, in pixels. */
  static final double NAME_GAP = 36;

  /** An empty scene in the default box. */
  public static final SpaceScene EMPTY = new SpaceScene(Box3.DEFAULT, List.of());

  /**
   * A surface as drawn: its vertices in the cube, NaN where undefined or outside the box, and its
   * shades.
   */
  record Sheet(
      ObjectId id, int nu, int nv, float[] x, float[] y, float[] z, Rgba[] faces, Stroke[] edges) {}

  private final Box3 box;
  private final List<Sheet> sheets;

  private SpaceScene(Box3 box, List<Sheet> sheets) {
    this.box = box;
    this.sheets = List.copyOf(sheets);
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
      if (o instanceof SpaceObject && o.style().visible()) {
        if (state.geometryOf(o.id()) instanceof Geometry.Mesh m && m.bounds().isPresent()) {
          bounds.add(m.bounds().get());
          shown.add(o);
        }
      }
    }
    Box3 box = Box3.fit(bounds);
    List<Sheet> sheets = new ArrayList<>();
    for (CanvasObject o : shown) {
      Geometry.Mesh m = (Geometry.Mesh) state.geometryOf(o.id());
      sheets.add(sheet(o.id(), m, box, theme.series().get(o.style().colour())));
    }
    return new SpaceScene(box, sheets);
  }

  private static Sheet sheet(ObjectId id, Geometry.Mesh m, Box3 box, Rgba colour) {
    int size = m.xs().length;
    float[] x = new float[size];
    float[] y = new float[size];
    float[] z = new float[size];
    // a vertex a little outside the box by rounding is inside
    double slack = 1e-6;
    for (int k = 0; k < size; k++) {
      double cx = box.toCube(0, m.xs()[k]);
      double cy = box.toCube(1, m.ys()[k]);
      double cz = box.toCube(2, m.zs()[k]);
      boolean inside =
          Math.abs(cx) <= 1 + slack && Math.abs(cy) <= 1 + slack && Math.abs(cz) <= 1 + slack;
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
    return new Sheet(id, m.nu(), m.nv(), x, y, z, faces, edges);
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

  /** The tick labels and the axis names, after everything else. */
  private void labels(Frame f, double[][] edges, Ticks[] ticks, CanvasTheme theme) {
    Projection projection = f.projection();
    double[] centre = new double[3];
    projection.project(0, 0, 0, centre);
    TextStyle style = new TextStyle(LABEL_SIZE, theme.text());
    Anchor middle = new Anchor(Anchor.Horizontal.MIDDLE, Anchor.Vertical.MIDDLE);
    String[] names = {"x", "y", "z"};
    double[] s = new double[3];
    for (int a = 0; a < 3; a++) {
      double[] e = edges[a];
      Ticks t = ticks[a];
      for (long i = t.first(box.min(a)); i <= t.last(box.max(a)); i++) {
        double v = box.toCube(a, t.value(i));
        double[] at = {e[0], e[1], e[2]};
        at[a] = v;
        label(f, at, centre, LABEL_GAP, t.label(i), middle, style, s);
      }
      double[] mid = {(e[0] + e[3]) / 2, (e[1] + e[4]) / 2, (e[2] + e[5]) / 2};
      label(f, mid, centre, NAME_GAP, names[a], middle, style, s);
    }
  }

  /** A text at a point of the cube, moved a gap away from the box's centre on the screen. */
  private static void label(
      Frame f,
      double[] at,
      double[] centre,
      double gap,
      String text,
      Anchor anchor,
      TextStyle style,
      double[] s) {
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
    f.p().text(text, f.wx(s[0]), f.wy(s[1]), gap * dx / n, gap * dy / n, anchor, style);
  }

  /** The objects' primitives in depth order. */
  private void objects(Frame f, boolean coarse) {
    int total = quads(coarse);
    if (total == 0) {
      return;
    }
    Projection projection = f.projection();
    long[] keys = new long[total];
    int count = 0;
    float[][] sx = new float[sheets.size()][];
    float[][] sy = new float[sheets.size()][];
    float[][] sd = new float[sheets.size()][];
    int[] starts = new int[sheets.size() + 1];
    double[] out = new double[3];
    for (int k = 0; k < sheets.size(); k++) {
      Sheet sheet = sheets.get(k);
      int size = sheet.x().length;
      sx[k] = new float[size];
      sy[k] = new float[size];
      sd[k] = new float[size];
      for (int v = 0; v < size; v++) {
        projection.project(sheet.x()[v], sheet.y()[v], sheet.z()[v], out);
        sx[k][v] = (float) f.wx(out[0]);
        sy[k][v] = (float) f.wy(out[1]);
        sd[k][v] = (float) out[2];
      }
      int su = coarse ? step(sheet.nu()) : 1;
      int sv = coarse ? step(sheet.nv()) : 1;
      int cu = ceil(sheet.nu(), su);
      int cv = ceil(sheet.nv(), sv);
      int row = sheet.nu() + 1;
      for (int j = 0; j < cv; j++) {
        int v0 = j * sv;
        int v1 = Math.min(v0 + sv, sheet.nv());
        for (int i = 0; i < cu; i++) {
          int u0 = i * su;
          int u1 = Math.min(u0 + su, sheet.nu());
          float d =
              sd[k][v0 * row + u0]
                  + sd[k][v0 * row + u1]
                  + sd[k][v1 * row + u1]
                  + sd[k][v1 * row + u0];
          float least =
              Math.min(
                  Math.min(sd[k][v0 * row + u0], sd[k][v0 * row + u1]),
                  Math.min(sd[k][v1 * row + u1], sd[k][v1 * row + u0]));
          // NaN (undefined, outside the box) fails the test, as a vertex behind the eye does
          if (!(least >= Projection.NEAR)) {
            continue;
          }
          int index = starts[k] + j * cu + i;
          // farthest first: a positive float's bits grow with it, so their complement sorts the
          // depths descending
          keys[count++] = ((long) (Integer.MAX_VALUE - Float.floatToIntBits(d)) << 32) | index;
        }
      }
      starts[k + 1] = starts[k] + cu * cv;
    }
    Arrays.sort(keys, 0, count);
    double[] light = projection.light();
    double[] toEye = new double[3];
    Painter p = f.p();
    for (int q = 0; q < count; q++) {
      int index = (int) keys[q];
      int k = 0;
      while (starts[k + 1] <= index) {
        k++;
      }
      Sheet sheet = sheets.get(k);
      int su = coarse ? step(sheet.nu()) : 1;
      int sv = coarse ? step(sheet.nv()) : 1;
      int lu = coarse ? 1 : step(sheet.nu());
      int lv = coarse ? 1 : step(sheet.nv());
      int cu = ceil(sheet.nu(), su);
      int local = index - starts[k];
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
      projection.toEye(x[a], y[a], z[a], toEye);
      if (nx * toEye[0] + ny * toEye[1] + nz * toEye[2] < 0) {
        shade *= BACK;
      }
      int level = (int) Math.min(SHADES - 1, shade * SHADES);
      float[] px = sx[k];
      float[] py = sy[k];
      p.beginPath();
      p.moveTo(px[a], py[a]);
      p.lineTo(px[b], py[b]);
      p.lineTo(px[c], py[c]);
      p.lineTo(px[d], py[d]);
      p.closePath();
      p.fill(sheet.faces()[level]);
      // the lattice's edges along this quad: its first row and column when they lie on the
      // lattice, and the mesh's last row and column
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
  }
}
