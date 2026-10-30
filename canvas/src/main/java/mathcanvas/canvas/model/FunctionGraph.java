package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Symbol;

/**
 * The graph of {@code y = body} over a domain of its variable. An unbounded domain is sampled over
 * the window; its parameter is the abscissa ({@link ParamKind#X}).
 *
 * @param id the id
 * @param style the style
 * @param body the expression in the variable
 * @param variable the variable
 * @param domain where the graph is drawn; its ends may read variables and may be infinite
 */
public record FunctionGraph(ObjectId id, Style style, Expr body, Symbol variable, Interval domain)
    implements CanvasObject {

  /** The whole real line. */
  public static final Interval ALL =
      Interval.of(Exprs.neg(Constant.INFINITY), Constant.INFINITY, false, false);

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   */
  public FunctionGraph {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(variable, "variable");
    Objects.requireNonNull(domain, "domain");
  }

  @Override
  public List<ObjectId> parents() {
    return List.of();
  }

  @Override
  public Set<String> reads() {
    return Reads.of(Set.of(variable), body, domain);
  }

  @Override
  public FunctionGraph withStyle(Style style) {
    return new FunctionGraph(id, style, body, variable, domain);
  }
}
