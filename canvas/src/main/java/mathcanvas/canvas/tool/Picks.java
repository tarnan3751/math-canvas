package mathcanvas.canvas.tool;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Window;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.canvas.paint.Stroke;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.number.Real;

/** What the calculus tools share: picking a graph, a clicked abscissa as a number, sliders. */
final class Picks {

  private Picks() {}

  /** The function graph under the pointer, if any. */
  static Optional<FunctionGraph> graph(Scene scene, Snap snap) {
    return snap instanceof Snap.OnPath o
            && scene.canvas().object(o.path()).orElse(null) instanceof FunctionGraph g
        ? Optional.of(g)
        : Optional.empty();
  }

  /** Where a click puts a number: an existing point's abscissa, else the pointer's. */
  static double abscissa(Snap snap) {
    return switch (snap) {
      case Snap.Existing e -> e.x();
      case Snap.OnPath o -> o.at().x();
      case Snap.Free f -> f.x();
    };
  }

  /**
   * A clicked abscissa as the decimal the object keeps: rounded to the power of ten at or below a
   * thousandth of the window's width, so a click gives {@code 1.25}, not {@code 1.2498046875}.
   */
  static Expr number(double x, Window window) {
    int exponent = (int) Math.floor(Math.log10(window.width() / 1000));
    BigDecimal step = BigDecimal.ONE.scaleByPowerOfTen(exponent);
    BigDecimal rounded =
        new BigDecimal(x)
            .divide(step, MathContext.DECIMAL64)
            .setScale(0, RoundingMode.HALF_EVEN)
            .multiply(step);
    return new Num(Real.of(rounded));
  }

  /** The slider of a variable on the canvas, if there is one. */
  static Optional<Slider> slider(CanvasState canvas, String variable) {
    return canvas.objects().stream()
        .filter(o -> o instanceof Slider s && s.variable().equals(variable))
        .map(o -> (Slider) o)
        .findFirst();
  }

  /** Marks an abscissa: a dashed vertical line across the window. */
  static void vertical(Painter p, CanvasTheme theme, double x, Window window) {
    if (!Double.isFinite(x)) {
      return;
    }
    double w = Style.DEFAULT.width();
    p.beginPath();
    p.moveTo(x, window.minY() - window.height());
    p.lineTo(x, window.maxY() + window.height());
    p.stroke(Stroke.dashed(Preview.colour(theme), w, 3 * w, 2 * w));
  }

  /** A graph's ordinate at an abscissa, from its samples; NaN off them. */
  static double ordinate(CanvasState canvas, ObjectId graph, double x) {
    if (!(canvas.geometryOf(graph) instanceof Geometry.Curve c)) {
      return Double.NaN;
    }
    for (var b : c.branches()) {
      for (int k = 0; k + 1 < b.size(); k++) {
        if (b.x(k) <= x && x <= b.x(k + 1)) {
          double f = b.x(k + 1) == b.x(k) ? 0 : (x - b.x(k)) / (b.x(k + 1) - b.x(k));
          return b.y(k) + f * (b.y(k + 1) - b.y(k));
        }
      }
    }
    return Double.NaN;
  }
}
