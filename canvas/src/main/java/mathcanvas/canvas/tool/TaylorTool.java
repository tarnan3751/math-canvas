package mathcanvas.canvas.tool;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Style;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;

/**
 * The Taylor tool: a click on a function graph picks it, then its centre {@code a}, a click (its
 * abscissa) or typed, adds the graph of {@code taylor(body, x, a, n)} in the next series colour,
 * with its order the slider {@code n}, which the same step adds (0 to 10, step 1, at 2) when the
 * canvas has none; the graph recomputes when {@code n} moves, since its body reads it.
 */
public final class TaylorTool extends GraphNumberTool {

  /** The variable of the order. */
  public static final String ORDER = "n";

  /** Makes the Taylor tool. */
  public TaylorTool() {}

  @Override
  String next() {
    return "a";
  }

  @Override
  void take(Expr centre, double value, Consumer<Edit> edits) {
    List<Edit.New> news = new ArrayList<>(2);
    Picks.sliderIfAbsent(
        news,
        scene.canvas(),
        ORDER,
        BigDecimal.ZERO,
        BigDecimal.TEN,
        BigDecimal.ONE,
        BigDecimal.TWO);
    FunctionGraph g = graph;
    Expr body = Apply.of(Symbol.of("taylor"), g.body(), g.variable(), centre, Symbol.of(ORDER));
    news.add(
        new Edit.New(
            ids -> new FunctionGraph(ids.getLast(), Style.DEFAULT, body, g.variable(), g.domain()),
            false));
    edits.accept(new Edit.Add(news));
    cancel();
  }
}
