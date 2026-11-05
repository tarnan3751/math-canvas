package mathcanvas.app.eval;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mathcanvas.app.document.Command;
import mathcanvas.app.text.Messages;
import mathcanvas.canvas.model.Arrow;
import mathcanvas.canvas.model.BarChart;
import mathcanvas.canvas.model.BoxPlot;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.ContourPlot;
import mathcanvas.canvas.model.FieldGrid;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.Histogram;
import mathcanvas.canvas.model.ImplicitCurve;
import mathcanvas.canvas.model.InequalityRegion;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Path;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.ResidualPlot;
import mathcanvas.canvas.model.Sample;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.ScatterPlot;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.SlopeField;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.VectorField;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.EvaluationException;
import mathcanvas.engine.eval.Evaluator;
import mathcanvas.engine.eval.NumericForm;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.ParseException;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.UnicodePrinter;

/**
 * Reads the text of an algebra panel row into the command that makes it so, on the worker. The text
 * is parsed with the user functions of the environment after the last cell, so {@code f(x)} is a
 * call when a cell defines {@code f}. An object's expressions are stored as parsed, made canonical
 * at the default context ({@link Canonical#DEFAULT_CONTEXT}), never evaluated, so a stored
 * expression depends neither on the worksheet's values nor on the document's precision.
 *
 * <ul>
 *   <li>{@code y = e}, {@code e} free of {@code y}: a function graph of {@code e} in {@code x};
 *   <li>any other equation: an implicit curve in {@code x} and {@code y};
 *   <li>{@code <}, {@code ≤}, {@code >}, {@code ≥}: an inequality region;
 *   <li>{@code [x, y]} (or {@code ⟨x, y⟩}, or {@code A = [x, y]} naming a new point): a free point,
 *       its coordinates evaluated;
 *   <li>a name whose value is a 2-vector of real numbers, {@code u}: an arrow bound to it, its tail
 *       at the origin; on a bound arrow's row, the name it is bound to (its tail kept);
 *   <li>on a slider's row, a number (or {@code a = number}): the slider's value, an exact decimal;
 *   <li>on the row of a point on a path ({@link #onPath}), a number (or {@code name = number}): its
 *       parameter on the path, the coordinate entry;
 *   <li>{@code slopefield(f)}, {@code dy/dx = f} or {@code y' = f}: a slope field; {@code
 *       vectorfield([P, Q])} or a 2-vector whose entries read {@code x} or {@code y} ({@code [P,
 *       Q]}, {@code F = [P, Q]}): a vector field; {@code contours(f, n)}, {@code contours(f, [c₁,
 *       …])} or {@code contours of f}: a contour plot. {@code slopefield}, {@code vectorfield} and
 *       {@code contours} are calls here whatever the cells define;
 *   <li>{@code histogram(data[, width[, start]])}, {@code barchart([categories, ]values)}, {@code
 *       boxplot(data[, y])}, {@code scatter(xs, ys)} or {@code scatter(matrix)}: a statistical plot
 *       of data that may be a cell's name ({@link #plot}); calls whatever the cells define, and
 *       their rows ({@code histogram of data, bins 2}, {@code histogram of data}) read back;
 *   <li>{@code residuals(xs, ys, fit)} or {@code residuals(matrix, fit)}: a residual plot ({@link
 *       #plot}), its row {@code residuals of fit} keeping the row's data;
 *   <li>a name or a call whose value is a fit, a relation {@code y = …} in {@code x} or a list
 *       whose first item is one ({@code linreg(x, y)}, {@code fit}, {@code nth(fit, 1)}): a
 *       function graph of the relation's right side as the value gives it ({@link #fitted}).
 * </ul>
 *
 * <p>The 3D view's rows are read by {@link SpaceEntries}.
 */
final class Entries {

  private static final Symbol X = ImplicitCurve.X;
  private static final Symbol Y = ImplicitCurve.Y;

  /**
   * What an entry asks for.
   *
   * @param command the command, or null when the entry is refused
   * @param error why it is refused, or null
   */
  record Read(Command command, String error) {

    static Read refused(String key, Object... args) {
      return new Read(null, Messages.text(key, args));
    }
  }

  private Entries() {}

  /**
   * Reads an entry.
   *
   * @param text the row's text
   * @param target the object the row shows, or empty for a new object
   * @param env the environment after the last cell, for the user functions and the numbers
   * @param points the names of the canvas's points, which an arrow is not bound to
   * @param cancellation the request to stop
   * @return the command, or why there is none
   */
  static Read read(
      String text,
      Optional<CanvasObject> target,
      Environment env,
      Set<String> points,
      Cancellation cancellation) {
    return read(text, target, false, env, points, cancellation);
  }

  /**
   * Reads an entry, of the 3D view's blank row when {@code space} is set or of an object in space's
   * row ({@link SpaceEntries}), else of the plane's.
   *
   * @param text the row's text
   * @param target the object the row shows, or empty for a new object
   * @param space whether a new object is one of the 3D view's
   * @param env the environment after the last cell, for the user functions and the numbers
   * @param points the names of the canvas's points, which an arrow is not bound to
   * @param cancellation the request to stop
   * @return the command, or why there is none
   */
  static Read read(
      String text,
      Optional<CanvasObject> target,
      boolean space,
      Environment env,
      Set<String> points,
      Cancellation cancellation) {
    return read(text, target, space, CanvasState.EMPTY, env, points, cancellation);
  }

  /**
   * Reads an entry, as {@link #read(String, Optional, boolean, Environment, Set, Cancellation)}
   * does, a gradient taken on the canvas's surface of its body when there is one.
   *
   * @param text the row's text
   * @param target the object the row shows, or empty for a new object
   * @param space whether a new object is one of the 3D view's
   * @param canvas the canvas
   * @param env the environment after the last cell
   * @param points the names of the canvas's points
   * @param cancellation the request to stop
   * @return the command, or why there is none
   */
  static Read read(
      String text,
      Optional<CanvasObject> target,
      boolean space,
      CanvasState canvas,
      Environment env,
      Set<String> points,
      Cancellation cancellation) {
    // The 3D view's rows
    if (target.isEmpty() ? space : SpaceEntries.reads(target.get())) {
      try {
        return SpaceEntries.read(text, target, canvas, env, cancellation);
      } catch (EvaluationException x) {
        return new Read(null, x.getMessage());
      }
    }
    // A slope field's and a contour plot's rows, read back as typed
    Matcher prime = PRIME.matcher(text);
    Matcher contoursOf = CONTOURS_OF.matcher(text);
    String source =
        prime.matches()
            ? SLOPE_FIELD + "(" + prime.group(1) + ")"
            : contoursOf.matches() ? CONTOURS + "(" + contoursOf.group(1) + ")" : plotRow(text);
    Expr e;
    try {
      e =
          Parser.parseExpression(
              source,
              name ->
                  FIELDS.contains(name)
                      || PLOTS.contains(name)
                      || env.names().isUserFunction(name));
    } catch (ParseException x) {
      return new Read(null, x.getMessage());
    }
    try {
      if (target.isPresent() && target.get() instanceof Slider s) {
        return slider(s, e, env, cancellation);
      }
      Optional<Read> field = field(e, target, env, cancellation);
      if (field.isPresent()) {
        return field.get();
      }
      Optional<Read> plot = plot(e, target, env, cancellation);
      if (plot.isPresent()) {
        return plot.get();
      }
      Optional<Read> fitted = fitted(e, target, env, cancellation);
      if (fitted.isPresent()) {
        return fitted.get();
      }
      if (e instanceof Symbol name
          && (target.isEmpty()
              || target.get() instanceof Arrow a && a.head() instanceof Arrow.Bound)) {
        return vector(name, target, env, points, cancellation);
      }
      if (target.isPresent() && target.get().isVector()) {
        return Read.refused("entry.vectorRow");
      }
      if (e instanceof Vector || e instanceof Relation r && isNamedPoint(r)) {
        return point(e, target, env, cancellation);
      }
      if (!(e instanceof Relation r)) {
        return Read.refused("entry.notObject");
      }
      Expr stored = Canonical.of(r, Canonical.DEFAULT_CONTEXT, cancellation);
      if (!(stored instanceof Relation c)) {
        return Read.refused("entry.notObject");
      }
      Style style = target.map(CanvasObject::style).orElse(Style.DEFAULT);
      Function<ObjectId, CanvasObject> make;
      if (c.op() == Relation.Op.EQ
          && c.left().equals(Y)
          && !ReadNames.of(c.right()).contains(Y.name())) {
        make = id -> new FunctionGraph(id, style, c.right(), X, FunctionGraph.ALL);
      } else if (c.op() == Relation.Op.EQ) {
        make = id -> new ImplicitCurve(id, style, c);
      } else if (Set.of(Relation.Op.LT, Relation.Op.LE, Relation.Op.GT, Relation.Op.GE)
          .contains(c.op())) {
        make = id -> new InequalityRegion(id, style, c);
      } else {
        return Read.refused("entry.notObject");
      }
      return target.isPresent()
          ? new Read(new Command.ReplaceObject(make.apply(target.get().id())), null)
          : new Read(Command.AddObject.inTurn(make), null);
    } catch (EvaluationException x) {
      return new Read(null, x.getMessage());
    }
  }

  /**
   * Reads the coordinate entry of a point on a path: a number, or {@code name = number} whatever
   * the name (the path's variable, {@code x = 2}, reads naturally), evaluated in the environment to
   * a real number, the point's new parameter. The point lands where the path is at that parameter,
   * found with the sampler. An expression that reads a defined name ({@code a + h}, of sliders or
   * of the worksheet's definitions) places the point at that expression, which it then follows; a
   * number places a point that was at an expression at a parameter of its own again. An expression
   * may not read a point's name: the canvas has no edge from that point, so the point would follow
   * it one recompute late.
   *
   * @param text the text
   * @param point the point
   * @param path the path's geometry as the point was read
   * @param env the environment after the last cell
   * @param points the names of the canvas's points
   * @param sampler evaluates the path at the parameter
   * @param cancellation the request to stop
   * @return the move, or why there is none
   */
  static Read onPath(
      String text,
      PointOnPath point,
      Geometry path,
      Environment env,
      Set<String> points,
      Sampler sampler,
      Cancellation cancellation) {
    Expr e;
    try {
      e = Parser.parseExpression(text, env.names());
    } catch (ParseException x) {
      return new Read(null, x.getMessage());
    }
    if (e instanceof Relation r && r.op() == Relation.Op.EQ && r.left() instanceof Symbol) {
      e = r.right();
    }
    double t;
    try {
      Expr value =
          NumericForm.numeric(
              Evaluator.evaluate(e, env, false, cancellation).value(), 17, cancellation);
      if (!(value instanceof Num n)
          || !(n.value() instanceof RealNumeric x)
          || !Double.isFinite(x.toDouble())) {
        return Read.refused("entry.parameter");
      }
      if (readsName(e, points)) {
        return Read.refused("entry.atPoint");
      }
      if (readsDefinition(e, env)) {
        return new Read(
            new Command.ReplaceObject(point.withAt(Optional.of(Canonical.of(e)))), null);
      }
      t = x.toDouble();
    } catch (EvaluationException x) {
      return new Read(null, x.getMessage());
    }
    if (!(path instanceof Geometry.Curve c
        && c.path() instanceof Path.Parametrised q
        && q.kind() == point.kind()
        && q.contains(t))) {
      return Read.refused("entry.offPath");
    }
    Sample at = q.at(t, sampler);
    if (!at.isFinite()) {
      return Read.refused("entry.offPath");
    }
    if (point.at().isPresent()) {
      return new Read(
          new Command.ReplaceObject(
              point.withAt(Optional.empty()).withParameter(q.kind(), t).landedAt(at.x(), at.y())),
          null);
    }
    return new Read(new Command.MovePoint(point.id(), at.x(), at.y(), t), null);
  }

  /** Whether an expression reads one of the names: a query over the tree. */
  private static boolean readsName(Expr e, Set<String> names) {
    if (e instanceof Symbol s && names.contains(s.name())) {
      return true;
    }
    for (Expr op : Operands.of(e)) {
      if (readsName(op, names)) {
        return true;
      }
    }
    return false;
  }

  /** Whether an expression reads a name the environment defines: a query over the tree. */
  private static boolean readsDefinition(Expr e, Environment env) {
    if (e instanceof Symbol s && env.definition(s.name()).isPresent()) {
      return true;
    }
    for (Expr op : Operands.of(e)) {
      if (readsDefinition(op, env)) {
        return true;
      }
    }
    return false;
  }

  // Vector fields, slope fields and contour plots

  /**
   * The entry's functions that make a field or a contour plot, called whatever the cells define.
   */
  private static final String SLOPE_FIELD = "slopefield";

  private static final String VECTOR_FIELD = "vectorfield";

  private static final String CONTOURS = "contours";

  private static final Set<String> FIELDS = Set.of(SLOPE_FIELD, VECTOR_FIELD, CONTOURS);

  /** {@code y' = f} (or {@code y′ = f}), a slope field's row. */
  private static final Pattern PRIME = Pattern.compile("\\s*y\\s*['′]\\s*=(.*)", Pattern.DOTALL);

  /** {@code contours of f}, a contour plot's row, its levels kept. */
  private static final Pattern CONTOURS_OF =
      Pattern.compile("\\s*contours\\s+of\\s+(.*)", Pattern.DOTALL);

  /** The levels of a contour plot entered as {@code contours of f} with no plot's to keep. */
  static final int DEFAULT_LEVELS = 10;

  private static final Expr DY_DX = Canonical.of(Parser.parseExpression("dy/dx", Names.NONE));

  /**
   * A field or a contour plot: {@code slopefield(f)}, {@code dy/dx = f} or {@code y' = f}; {@code
   * vectorfield([P, Q])}, or a 2-vector (bare or named, {@code F = [P, Q]}) whose entries read
   * {@code x} or {@code y}; {@code contours(f, n)} with {@code n} a whole number of levels, {@code
   * contours(f, [c₁, …])} with real values, or {@code contours of f} keeping the row's levels. On a
   * field's row the field keeps its density; a new one has {@link FieldGrid#DEFAULT_DENSITY}. A
   * point's row refuses a field. A name before a field's vector labels nothing: fields are not
   * named objects.
   */
  private static Optional<Read> field(
      Expr e, Optional<CanvasObject> target, Environment env, Cancellation cancellation) {
    Style style = target.map(CanvasObject::style).orElse(Style.DEFAULT);
    int density =
        switch (target.orElse(null)) {
          case VectorField v -> v.density();
          case SlopeField s -> s.density();
          case null, default -> FieldGrid.DEFAULT_DENSITY;
        };
    Function<ObjectId, CanvasObject> make;
    if (e instanceof Apply a && a.head().name().equals(SLOPE_FIELD)) {
      if (a.args().size() != 1) {
        return Optional.of(Read.refused("entry.slopeField"));
      }
      Expr slope = Canonical.of(a.args().getFirst(), Canonical.DEFAULT_CONTEXT, cancellation);
      make = id -> new SlopeField(id, style, slope, density);
    } else if (e instanceof Relation r
        && r.op() == Relation.Op.EQ
        && Canonical.of(r.left()).equals(DY_DX)) {
      Expr slope = Canonical.of(r.right(), Canonical.DEFAULT_CONTEXT, cancellation);
      make = id -> new SlopeField(id, style, slope, density);
    } else if (e instanceof Apply a && a.head().name().equals(VECTOR_FIELD)) {
      if (a.args().size() != 1
          || !(a.args().getFirst() instanceof Vector v)
          || v.items().size() != 2) {
        return Optional.of(Read.refused("entry.field"));
      }
      make = vectorField(v, style, density, cancellation);
    } else if (planeVector(e).isPresent()) {
      make = vectorField(planeVector(e).get(), style, density, cancellation);
    } else if (e instanceof Apply a && a.head().name().equals(CONTOURS)) {
      if (a.args().isEmpty() || a.args().size() > 2) {
        return Optional.of(Read.refused("entry.levels"));
      }
      Expr f = Canonical.of(a.args().getFirst(), Canonical.DEFAULT_CONTEXT, cancellation);
      Optional<ContourPlot.Levels> levels =
          a.args().size() == 2
              ? levels(a.args().get(1), env, cancellation)
              : Optional.of(
                  target.orElse(null) instanceof ContourPlot c
                      ? c.levels()
                      : new ContourPlot.Count(DEFAULT_LEVELS));
      if (levels.isEmpty()) {
        return Optional.of(Read.refused("entry.levels"));
      }
      make = id -> new ContourPlot(id, style, f, levels.get());
    } else {
      return Optional.empty();
    }
    // a point's row keeps a point: a field is entered in the blank row
    if (target.isPresent() && target.get().isPoint()) {
      return Optional.of(Read.refused("entry.notThisKind"));
    }
    return Optional.of(
        target.isPresent()
            ? new Read(new Command.ReplaceObject(make.apply(target.get().id())), null)
            : new Read(Command.AddObject.inTurn(make), null));
  }

  /** A 2-vector, bare or named, whose entries read {@code x} or {@code y}: a vector field. */
  private static Optional<Vector> planeVector(Expr e) {
    Expr v = e instanceof Relation r && isNamedPoint(r) ? r.right() : e;
    if (v instanceof Vector w && w.items().size() == 2) {
      Set<String> names = new HashSet<>(ReadNames.of(w.items().get(0)));
      names.addAll(ReadNames.of(w.items().get(1)));
      if (names.contains(X.name()) || names.contains(Y.name())) {
        return Optional.of(w);
      }
    }
    return Optional.empty();
  }

  private static Function<ObjectId, CanvasObject> vectorField(
      Vector v, Style style, int density, Cancellation cancellation) {
    Expr p = Canonical.of(v.items().get(0), Canonical.DEFAULT_CONTEXT, cancellation);
    Expr q = Canonical.of(v.items().get(1), Canonical.DEFAULT_CONTEXT, cancellation);
    return id -> new VectorField(id, style, p, q, density);
  }

  /**
   * A contour plot's levels: a whole number from 1 to {@link ContourPlot#MAX_LEVELS}, or a list of
   * as many real numbers, kept as expressions (they may read sliders).
   */
  private static Optional<ContourPlot.Levels> levels(
      Expr e, Environment env, Cancellation cancellation) {
    if (e instanceof Vector v) {
      if (v.items().isEmpty() || v.items().size() > ContourPlot.MAX_LEVELS) {
        return Optional.empty();
      }
      List<Expr> values = new ArrayList<>();
      for (Expr item : v.items()) {
        if (!real(item, env, cancellation)) {
          return Optional.empty();
        }
        values.add(Canonical.of(item, Canonical.DEFAULT_CONTEXT, cancellation));
      }
      return Optional.of(new ContourPlot.Values(values));
    }
    Expr value =
        NumericForm.numeric(
            Evaluator.evaluate(e, env, false, cancellation).value(), 17, cancellation);
    if (value instanceof Num n && n.value() instanceof RealNumeric x) {
      double count = x.toDouble();
      if (count >= 1 && count <= ContourPlot.MAX_LEVELS && count == Math.rint(count)) {
        return Optional.of(new ContourPlot.Count((int) count));
      }
    }
    return Optional.empty();
  }

  // The statistical plots

  private static final String HISTOGRAM = "histogram";

  private static final String BAR_CHART = "barchart";

  private static final String BOX_PLOT = "boxplot";

  private static final String SCATTER = "scatter";

  /**
   * The entry's residual plot, {@code residuals(…)} as typed: the engine's {@code residuals} takes
   * three arguments when parsed, so the entry's call ({@link #RESIDUALS_CALL}) and its row ({@link
   * #RESIDUALS_OF}) are parsed under this name, which no builtin has.
   */
  private static final String RESIDUALS = "residualplot";

  /** The entry's functions that make a statistical plot, called whatever the cells define. */
  private static final Set<String> PLOTS =
      Set.of(HISTOGRAM, BAR_CHART, BOX_PLOT, SCATTER, RESIDUALS);

  /**
   * {@code histogram of D, bins W} or {@code …, from S}, a histogram's row, or {@code histogram of
   * D} by Sturges' rule.
   */
  private static final Pattern HISTOGRAM_OF =
      Pattern.compile(
          "\\s*histogram\\s+of\\s+(.+?)"
              + "(?:\\s*,\\s*bins\\s+(.+?)(?:\\s*,\\s*from\\s+(.+?))?)?\\s*",
          Pattern.DOTALL);

  /** {@code box plot of D} or {@code … at height Y}, a box plot's row. */
  private static final Pattern BOX_PLOT_OF =
      Pattern.compile(
          "\\s*box\\s+plot\\s+of\\s+(.+?)(?:\\s+at\\s+height\\s+(.+?))?\\s*", Pattern.DOTALL);

  /** {@code scatter of (X, Y)} or {@code scatter of M}, a scatter plot's row. */
  private static final Pattern SCATTER_OF =
      Pattern.compile("\\s*scatter\\s+of\\s+(.+?)\\s*", Pattern.DOTALL);

  /** {@code bar chart of V}, a bar chart's row, its categories kept. */
  private static final Pattern BAR_CHART_OF =
      Pattern.compile("\\s*bar\\s+chart\\s+of\\s+(.+?)\\s*", Pattern.DOTALL);

  /** {@code residuals of F}, a residual plot's row, its data kept. */
  private static final Pattern RESIDUALS_OF =
      Pattern.compile("\\s*residuals\\s+of\\s+(.+?)\\s*", Pattern.DOTALL);

  /** {@code residuals(…)}, a residual plot entered by its call, whatever its arguments. */
  private static final Pattern RESIDUALS_CALL =
      Pattern.compile("\\s*residuals\\s*\\((.*)\\)\\s*", Pattern.DOTALL);

  /** A statistical plot's row as the call it reads as, or the text itself. */
  private static String plotRow(String text) {
    Matcher m = HISTOGRAM_OF.matcher(text);
    if (m.matches()) {
      return HISTOGRAM
          + "("
          + m.group(1)
          + (m.group(2) == null ? "" : ", " + m.group(2))
          + (m.group(3) == null ? "" : ", " + m.group(3))
          + ")";
    }
    m = BOX_PLOT_OF.matcher(text);
    if (m.matches()) {
      return BOX_PLOT + "(" + m.group(1) + (m.group(2) == null ? "" : ", " + m.group(2)) + ")";
    }
    m = SCATTER_OF.matcher(text);
    if (m.matches()) {
      String of = m.group(1);
      return of.startsWith("(") && of.endsWith(")") ? SCATTER + of : SCATTER + "(" + of + ")";
    }
    m = BAR_CHART_OF.matcher(text);
    if (m.matches()) {
      return BAR_CHART + "(" + m.group(1) + ")";
    }
    m = RESIDUALS_OF.matcher(text);
    if (m.matches()) {
      return RESIDUALS + "(" + m.group(1) + ")";
    }
    m = RESIDUALS_CALL.matcher(text);
    return m.matches() ? RESIDUALS + "(" + m.group(1) + ")" : text;
  }

  /**
   * A statistical plot: {@code histogram(data[, width[, start]])}, {@code barchart(values)} or
   * {@code barchart(categories, values)}, {@code boxplot(data[, y])}, {@code scatter(xs, ys)} or
   * {@code scatter(matrix)}, {@code residuals(xs, ys, fit)} or {@code residuals(matrix, fit)}. The
   * data is checked now (a list of real numbers, two of one length, a matrix of two columns) and
   * stored as written, so a cell's name keeps the plot following the cell, and so is a residual
   * plot's fit, once its value is checked to be a fit ({@link #fitOf}); the categories are a list
   * of names or numbers, kept as their text. On a box plot's row, one with no height keeps the
   * row's; on a bar chart's, one with no categories keeps the row's; on a residual plot's, {@code
   * residuals(fit)} keeps the row's data. A point's row refuses a plot.
   */
  private static Optional<Read> plot(
      Expr e, Optional<CanvasObject> target, Environment env, Cancellation cancellation) {
    if (!(e instanceof Apply a) || !PLOTS.contains(a.head().name())) {
      return Optional.empty();
    }
    Style style = target.map(CanvasObject::style).orElse(Style.DEFAULT);
    List<Expr> args = a.args();
    Function<ObjectId, CanvasObject> make;
    switch (a.head().name()) {
      case HISTOGRAM -> {
        if (args.isEmpty()
            || args.size() > 3
            || !shape(args.get(0), env, cancellation).equals(Optional.of(1))
            || args.size() > 1 && !positive(args.get(1), env, cancellation)
            || args.size() > 2 && !real(args.get(2), env, cancellation)) {
          return Optional.of(Read.refused("entry.histogram"));
        }
        Expr data = stored(args.get(0), cancellation);
        Optional<Expr> width =
            args.size() > 1 ? Optional.of(stored(args.get(1), cancellation)) : Optional.empty();
        Optional<Expr> start =
            args.size() > 2 ? Optional.of(stored(args.get(2), cancellation)) : Optional.empty();
        make = id -> new Histogram(id, style, data, width, start);
      }
      case BAR_CHART -> {
        Optional<List<String>> categories =
            args.size() == 2
                ? categories(args.get(0))
                : Optional.of(
                    target.orElse(null) instanceof BarChart b ? b.categories() : List.of());
        Expr values = args.isEmpty() ? null : args.getLast();
        Optional<Integer> length =
            values == null ? Optional.empty() : length(values, env, cancellation);
        if (args.isEmpty()
            || args.size() > 2
            || categories.isEmpty()
            || length.isEmpty()
            || !categories.get().isEmpty() && categories.get().size() != length.get()) {
          return Optional.of(Read.refused("entry.barChart"));
        }
        Expr stored = stored(values, cancellation);
        make = id -> new BarChart(id, style, categories.get(), stored);
      }
      case BOX_PLOT -> {
        if (args.isEmpty()
            || args.size() > 2
            || !shape(args.get(0), env, cancellation).equals(Optional.of(1))
            || args.size() > 1 && !real(args.get(1), env, cancellation)) {
          return Optional.of(Read.refused("entry.boxPlot"));
        }
        Expr data = stored(args.get(0), cancellation);
        Expr y =
            args.size() > 1
                ? stored(args.get(1), cancellation)
                : target.orElse(null) instanceof BoxPlot b ? b.y() : BoxPlot.DEFAULT_Y;
        make = id -> new BoxPlot(id, style, data, y);
      }
      case RESIDUALS -> {
        Optional<ScatterPlot.Points> points =
            args.size() == 1 && target.orElse(null) instanceof ResidualPlot r
                ? Optional.of(r.points())
                : args.size() < 2
                    ? Optional.empty()
                    : points(args.subList(0, args.size() - 1), env, cancellation);
        if (points.isEmpty()) {
          return Optional.of(Read.refused("entry.residuals"));
        }
        Optional<Read> refused =
            fitOf(Evaluator.evaluate(args.getLast(), env, false, cancellation).value()).refusal();
        if (refused.isPresent()) {
          return refused;
        }
        Expr fit = stored(args.getLast(), cancellation);
        make = id -> new ResidualPlot(id, style, points.get(), fit);
      }
      default -> {
        Optional<ScatterPlot.Points> points = points(args, env, cancellation);
        if (points.isEmpty()) {
          return Optional.of(Read.refused("entry.scatter"));
        }
        make = id -> new ScatterPlot(id, style, points.get());
      }
    }
    if (target.isPresent() && target.get().isPoint()) {
      return Optional.of(Read.refused("entry.notThisKind"));
    }
    return Optional.of(
        target.isPresent()
            ? new Read(new Command.ReplaceObject(make.apply(target.get().id())), null)
            : new Read(Command.AddObject.inTurn(make), null));
  }

  private static Expr stored(Expr e, Cancellation cancellation) {
    return Canonical.of(e, Canonical.DEFAULT_CONTEXT, cancellation);
  }

  /**
   * Paired data, as a scatter plot's and a residual plot's: one matrix of two columns, or two lists
   * of real numbers of one length; empty for anything else.
   */
  private static Optional<ScatterPlot.Points> points(
      List<Expr> args, Environment env, Cancellation cancellation) {
    if (args.size() == 1 && shape(args.get(0), env, cancellation).equals(Optional.of(2))) {
      return Optional.of(new ScatterPlot.Pairs(stored(args.get(0), cancellation)));
    }
    if (args.size() == 2
        && shape(args.get(0), env, cancellation).equals(Optional.of(1))
        && shape(args.get(1), env, cancellation).equals(Optional.of(1))
        && length(args.get(0), env, cancellation).equals(length(args.get(1), env, cancellation))) {
      return Optional.of(
          new ScatterPlot.Columns(
              stored(args.get(0), cancellation), stored(args.get(1), cancellation)));
    }
    return Optional.empty();
  }

  /**
   * What an expression's value is as a fit: its relation {@code y = …} (the value, or the first
   * item of a list, as {@code linreg} gives), or why it is none.
   *
   * @param relation the relation, or null
   * @param refusal why it is no fit in {@code x}, or empty
   */
  private record Fit(Relation relation, Optional<Read> refusal) {}

  /**
   * A value as a fit. A list whose first item is not a relation {@code y = …} is no fit; one whose
   * right side reads a name other than {@code x} (a fit in another variable, {@code y = 2t + 1}) is
   * none either. Anything else that is neither a list nor a relation is no fit with no refusal of
   * its own, so the caller reads it otherwise.
   */
  private static Fit fitOf(Expr value) {
    Expr first = value instanceof ListExpr l && !l.items().isEmpty() ? l.items().getFirst() : value;
    if (!(first instanceof Relation r && r.op() == Relation.Op.EQ && r.left().equals(Y))) {
      return new Fit(null, Optional.of(Read.refused("entry.fit")));
    }
    Set<String> others = new java.util.TreeSet<>(ReadNames.of(r.right()));
    others.remove(X.name());
    if (!others.isEmpty()) {
      return new Fit(null, Optional.of(Read.refused("entry.fitInX", String.join(", ", others))));
    }
    return new Fit(r, Optional.empty());
  }

  /**
   * A fitted curve: a name or a call whose value is a fit ({@code linreg(x, y)}, {@code fit} where
   * {@code fit := linreg(x, y)}, {@code nth(fit, 1)}) is a function graph of its relation's right
   * side as the value gives it, so the row shows {@code y = 1.94x + 0.15} and reads back as that
   * graph. A value that is a list but no fit in {@code x} is refused; any other value is left to
   * the other readings ({@code u} a vector's name). A vector's row and a point's row refuse it.
   */
  private static Optional<Read> fitted(
      Expr e, Optional<CanvasObject> target, Environment env, Cancellation cancellation) {
    if (!(e instanceof Symbol || e instanceof Apply)
        || target.isPresent() && target.get().isVector()) {
      return Optional.empty();
    }
    Expr value = Evaluator.evaluate(e, env, false, cancellation).value();
    if (!(value instanceof ListExpr || value instanceof Relation)) {
      return Optional.empty();
    }
    Fit fit = fitOf(value);
    if (fit.refusal().isPresent()) {
      return value instanceof ListExpr ? fit.refusal() : Optional.empty();
    }
    if (target.isPresent() && target.get().isPoint()) {
      return Optional.of(Read.refused("entry.notThisKind"));
    }
    Style style = target.map(CanvasObject::style).orElse(Style.DEFAULT);
    Expr body = stored(fit.relation().right(), cancellation);
    Function<ObjectId, CanvasObject> make =
        id -> new FunctionGraph(id, style, body, X, FunctionGraph.ALL);
    return Optional.of(
        target.isPresent()
            ? new Read(new Command.ReplaceObject(make.apply(target.get().id())), null)
            : new Read(Command.AddObject.inTurn(make), null));
  }

  /**
   * The number of columns of data: 1 for a vector of finite real numbers, a matrix's columns for a
   * matrix of them, empty for anything else.
   */
  private static Optional<Integer> shape(Expr e, Environment env, Cancellation cancellation) {
    return rows(e, env, cancellation).map(rows -> rows.getFirst().size());
  }

  /** The number of rows of data, as {@link #shape} reads it. */
  private static Optional<Integer> length(Expr e, Environment env, Cancellation cancellation) {
    return rows(e, env, cancellation).map(List::size);
  }

  /** The rows of data: a vector's entries one a row, a matrix's rows, every entry real. */
  private static Optional<List<List<Expr>>> rows(
      Expr e, Environment env, Cancellation cancellation) {
    Expr value = Evaluator.evaluate(e, env, false, cancellation).value();
    List<List<Expr>> rows =
        value instanceof Vector v
            ? v.items().stream().map(List::of).toList()
            : value instanceof Matrix m ? m.rows() : List.of();
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    for (List<Expr> row : rows) {
      for (Expr item : row) {
        if (!(NumericForm.numeric(item, 17, cancellation) instanceof Num n
            && n.value() instanceof RealNumeric x
            && Double.isFinite(x.toDouble()))) {
          return Optional.empty();
        }
      }
    }
    return Optional.of(rows);
  }

  /** Whether a value is a positive finite real number once made numeric. */
  private static boolean positive(Expr e, Environment env, Cancellation cancellation) {
    Expr value =
        NumericForm.numeric(
            Evaluator.evaluate(e, env, false, cancellation).value(), 17, cancellation);
    return value instanceof Num n
        && n.value() instanceof RealNumeric x
        && x.toDouble() > 0
        && Double.isFinite(x.toDouble());
  }

  /** A bar chart's categories, a list of names or numbers as their text, or empty. */
  private static Optional<List<String>> categories(Expr e) {
    if (!(e instanceof Vector v)) {
      return Optional.empty();
    }
    List<String> out = new ArrayList<>();
    for (Expr item : v.items()) {
      if (!(item instanceof Symbol || item instanceof Num)) {
        return Optional.empty();
      }
      out.add(UnicodePrinter.INSTANCE.print(item));
    }
    return Optional.of(out);
  }

  private static boolean isNamedPoint(Relation r) {
    return r.op() == Relation.Op.EQ && r.left() instanceof Symbol && r.right() instanceof Vector;
  }

  private static Read point(
      Expr e, Optional<CanvasObject> target, Environment env, Cancellation cancellation) {
    Optional<String> name = Optional.empty();
    Expr coordinates = e;
    if (e instanceof Relation r) {
      name = Optional.of(((Symbol) r.left()).name());
      coordinates = r.right();
      if (CanvasBindings.isReserved(name.get())) {
        return Read.refused("name.reserved", name.get());
      }
    }
    if (!(coordinates instanceof Vector v) || v.items().size() != 2) {
      return Read.refused("entry.point");
    }
    double[] at = new double[2];
    for (int k = 0; k < 2; k++) {
      Expr value =
          NumericForm.numeric(
              Evaluator.evaluate(v.items().get(k), env, false, cancellation).value(),
              17,
              cancellation);
      if (!(value instanceof Num n)
          || !(n.value() instanceof RealNumeric x)
          || !Double.isFinite(x.toDouble())) {
        return Read.refused("entry.point");
      }
      at[k] = x.toDouble();
    }
    if (target.isPresent()) {
      return target.get() instanceof FreePoint p
          ? new Read(new Command.MovePoint(p.id(), at[0], at[1]), null)
          : Read.refused("entry.notThisKind");
    }
    Function<ObjectId, CanvasObject> make = id -> new FreePoint(id, Style.DEFAULT, at[0], at[1]);
    // an unnamed point takes the next free name when its command runs, not now
    return new Read(new Command.AddObject(make, name, name.isEmpty(), true), null);
  }

  /**
   * An arrow bound to a name whose value is a 2-vector of real numbers, from the origin, or the
   * arrow on the row bound to it instead. A point's name is refused: the arrow would follow it one
   * recompute late, with no edge of the canvas's graph ordering it after the point.
   */
  private static Read vector(
      Symbol name,
      Optional<CanvasObject> target,
      Environment env,
      Set<String> points,
      Cancellation cancellation) {
    if (points.contains(name.name())) {
      return Read.refused("entry.vectorPoint");
    }
    Expr value = Evaluator.evaluate(name, env, false, cancellation).value();
    if (!Slider.isVariableName(name.name())
        || !(value instanceof Vector v)
        || v.items().size() != 2
        || !real(v.items().get(0), env, cancellation)
        || !real(v.items().get(1), env, cancellation)) {
      return Read.refused("entry.vector", name.name());
    }
    Arrow.Bound head = new Arrow.Bound(name.name());
    if (target.isPresent()) {
      Arrow a = (Arrow) target.get();
      return new Read(
          new Command.ReplaceObject(new Arrow(a.id(), a.style(), a.tail(), head)), null);
    }
    return new Read(
        Command.AddObject.inTurn(id -> new Arrow(id, Style.DEFAULT, Optional.empty(), head)), null);
  }

  /** Whether a value is a finite real number once made numeric. */
  private static boolean real(Expr e, Environment env, Cancellation cancellation) {
    Expr value =
        NumericForm.numeric(
            Evaluator.evaluate(e, env, false, cancellation).value(), 17, cancellation);
    return value instanceof Num n
        && n.value() instanceof RealNumeric x
        && Double.isFinite(x.toDouble());
  }

  private static Read slider(Slider s, Expr e, Environment env, Cancellation cancellation) {
    Expr value = e;
    if (e instanceof Relation r
        && r.op() == Relation.Op.EQ
        && r.left() instanceof Symbol name
        && name.name().equals(s.variable())) {
      value = r.right();
    }
    Optional<BigDecimal> exact =
        CanvasBindings.decimal(
            Evaluator.evaluate(value, env.without(s.variable()), false, cancellation).value());
    if (exact.isEmpty()) {
      return Read.refused("entry.sliderValue");
    }
    return new Read(new Command.SetSlider(s.id(), exact.get()), null);
  }
}
