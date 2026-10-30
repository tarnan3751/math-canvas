package mathcanvas.canvas.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.InequalityRegion;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Polyline;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Text;
import mathcanvas.canvas.paint.Anchor;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.GridPainter;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.Stroke;
import mathcanvas.canvas.paint.TextStyle;

/**
 * Builds the view's content from the canvas's objects and their geometry (docs/reference/canvas.md,
 * <b>Graph kinds</b>), in drawing order: a curve's branches as one polyline, a region as a
 * translucent fill and its boundary, a point as a disc, a text at its anchor, and a label beside a
 * point whose label is shown. An object's colour is its series slot in the theme; its {@link Style}
 * becomes the {@link Stroke}. A hidden object is neither drawn nor hit; an undefined one and a
 * slider draw nothing. Pure computation over immutable values: built on the worker, rebuilt when
 * the theme changes.
 */
public final class CanvasContent {

  /** The dash and the gap of a dashed stroke, in multiples of its width. */
  static final double DASH = 3;

  /** The gap of a dashed stroke, in multiples of its width. */
  static final double GAP = 2;

  /** The opacity of a region's fill. */
  static final double FILL_OPACITY = 0.25;

  /** A point's radius beyond its stroke width, in pixels. */
  static final double POINT_RADIUS = 2;

  /** How far a label sits from its point, right and up, in pixels. */
  static final double LABEL_OFFSET = 6;

  private CanvasContent() {}

  /**
   * The content and which object each of its hit items is.
   *
   * @param content the content
   * @param items the object of each polyline and point, by its item number in the hit index
   */
  public record Built(PlotContent content, Map<Integer, ObjectId> items) {

    /** Copies the map. */
    public Built {
      items = Map.copyOf(items);
    }
  }

  /**
   * Builds the content of a canvas.
   *
   * @param state the objects and their geometry
   * @param labels the label of each object that has one
   * @param theme the colours
   * @return the content
   */
  public static Built build(CanvasState state, Map<ObjectId, String> labels, CanvasTheme theme) {
    List<PlotContent.Fill> fills = new ArrayList<>();
    List<PlotContent.Curve> curves = new ArrayList<>();
    List<ObjectId> curveIds = new ArrayList<>();
    List<PlotContent.Marker> points = new ArrayList<>();
    List<ObjectId> pointIds = new ArrayList<>();
    List<PlotContent.Label> texts = new ArrayList<>();
    for (CanvasObject o : state.objects()) {
      Style style = o.style();
      if (!style.visible()) {
        continue;
      }
      Rgba colour = theme.series().get(style.colour());
      Stroke stroke = stroke(colour, style.width(), style.dashed());
      switch (state.geometryOf(o.id())) {
        case Geometry.Point p when o instanceof Text t ->
            texts.add(
                new PlotContent.Label(
                    t.text(),
                    p.x(),
                    p.y(),
                    0,
                    0,
                    new Anchor(Anchor.Horizontal.START, Anchor.Vertical.BASELINE),
                    new TextStyle(GridPainter.LABEL_SIZE, colour)));
        case Geometry.Point p -> {
          points.add(new PlotContent.Marker(p.x(), p.y(), POINT_RADIUS + style.width(), colour));
          pointIds.add(o.id());
          String label = labels.get(o.id());
          if (style.labelVisible() && label != null) {
            texts.add(
                new PlotContent.Label(
                    label,
                    p.x(),
                    p.y(),
                    LABEL_OFFSET,
                    -LABEL_OFFSET,
                    new Anchor(Anchor.Horizontal.START, Anchor.Vertical.BOTTOM),
                    new TextStyle(GridPainter.LABEL_SIZE, colour)));
          }
        }
        case Geometry.Curve c -> {
          if (!c.branches().isEmpty()) {
            double[][] joined = joined(c.branches());
            curves.add(new PlotContent.Curve(joined[0], joined[1], stroke));
            curveIds.add(o.id());
          }
        }
        case Geometry.Region r -> {
          if (!r.rings().isEmpty()) {
            double[][] joined = joined(r.rings());
            fills.add(new PlotContent.Fill(joined[0], joined[1], colour.withOpacity(FILL_OPACITY)));
            boolean strict =
                o instanceof InequalityRegion q
                    && switch (q.inequality().op()) {
                      case LT, GT -> true;
                      default -> false;
                    };
            curves.add(
                new PlotContent.Curve(
                    joined[0], joined[1], stroke(colour, style.width(), strict || style.dashed())));
            curveIds.add(o.id());
          }
        }
        case Geometry.Value v -> {}
        case Geometry.Undefined u -> {}
      }
    }
    Map<Integer, ObjectId> items = new HashMap<>();
    for (int k = 0; k < curveIds.size(); k++) {
      items.put(k, curveIds.get(k));
    }
    for (int k = 0; k < pointIds.size(); k++) {
      items.put(curveIds.size() + k, pointIds.get(k));
    }
    return new Built(PlotContent.of(fills, curves, points, texts), items);
  }

  /** A stroke of an object's colour and width, dashed in proportion to the width. */
  static Stroke stroke(Rgba colour, double width, boolean dashed) {
    return dashed
        ? Stroke.dashed(colour, width, DASH * width, GAP * width)
        : Stroke.solid(colour, width);
  }

  /** Polylines end to end, each followed by a NaN that breaks the path there. */
  private static double[][] joined(List<Polyline> lines) {
    int n = 0;
    for (Polyline l : lines) {
      n += l.size() + 1;
    }
    double[] xs = new double[n];
    double[] ys = new double[n];
    int at = 0;
    for (Polyline l : lines) {
      System.arraycopy(l.xs(), 0, xs, at, l.size());
      System.arraycopy(l.ys(), 0, ys, at, l.size());
      at += l.size();
      xs[at] = Double.NaN;
      ys[at] = Double.NaN;
      at++;
    }
    return new double[][] {xs, ys};
  }
}
