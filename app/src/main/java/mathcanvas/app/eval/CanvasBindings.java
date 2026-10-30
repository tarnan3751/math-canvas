package mathcanvas.app.eval;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import mathcanvas.app.document.Command;
import mathcanvas.app.document.DocumentState;
import mathcanvas.app.document.SliderSpec;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Style;
import mathcanvas.engine.eval.Definition;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;

/**
 * The canvas's variables as the cells see them: each slider's variable bound to its exact value,
 * and each named point to the vector of its coordinates as decimals ({@code A := [1.5, -2.0]}, so
 * {@code nth(A, 1)} is its abscissa). A point with no position (undefined, or not yet recomputed)
 * binds nothing. They are bound before the first cell: every cell's environment gets each of them
 * that the cells before it have not defined themselves ({@link #overlay}); the environments the
 * cells store hold none of them, so a stored environment never holds a stale value.
 *
 * @param values each variable's value
 * @param sliders each slider by its variable
 * @param points the names of the named points, defined or not
 */
public record CanvasBindings(
    Map<String, Expr> values, Map<String, Slider> sliders, Set<String> points) {

  /**
   * The names the canvas's curves use for their own variables: the abscissa, the ordinate, a
   * parametric curve's parameter and a polar curve's angle. The app gives none of them to a slider
   * or a point, which would rebind it in every cell.
   */
  public static final Set<String> RESERVED = Set.of("x", "y", "t", "θ");

  /** No canvas variables. */
  public static final CanvasBindings NONE = new CanvasBindings(Map.of(), Map.of(), Set.of());

  /**
   * Copies the collections.
   *
   * @throws NullPointerException if an operand is null
   */
  public CanvasBindings {
    values = Map.copyOf(values);
    sliders = Map.copyOf(sliders);
    points = Set.copyOf(points);
  }

  /**
   * The variables of a document's canvas.
   *
   * @param state the document
   * @return the bindings
   */
  public static CanvasBindings of(DocumentState state) {
    Map<String, Expr> values = new LinkedHashMap<>();
    Map<String, Slider> sliders = new LinkedHashMap<>();
    for (CanvasObject o : state.canvas().objects()) {
      if (o instanceof Slider s) {
        sliders.put(s.variable(), s);
        values.put(s.variable(), exact(s.value()));
      }
    }
    for (Map.Entry<ObjectId, String> e : state.names().entrySet()) {
      CanvasObject o = state.canvas().object(e.getKey()).orElseThrow();
      Geometry g = state.canvas().geometryOf(e.getKey());
      if (o instanceof FreePoint p) {
        values.put(e.getValue(), point(p.x(), p.y()));
      } else if (g instanceof Geometry.Point p) {
        values.put(e.getValue(), point(p.x(), p.y()));
      }
    }
    return new CanvasBindings(
        values, sliders, state.names().values().stream().collect(Collectors.toSet()));
  }

  /**
   * Whether a name is one the app gives to no slider and no point ({@link #RESERVED}).
   *
   * @param name the name
   * @return true if it is reserved
   */
  public static boolean isReserved(String name) {
    return RESERVED.contains(name);
  }

  /**
   * Every canvas variable's name.
   *
   * @return the names: the sliders' variables and the points' names
   */
  public Set<String> names() {
    Set<String> out = new HashSet<>(sliders.keySet());
    out.addAll(points);
    return out;
  }

  /**
   * An environment with every canvas variable it does not define bound to its value.
   *
   * @param env the environment, as a cell stored it
   * @return the environment
   */
  public Environment overlay(Environment env) {
    Environment out = env;
    for (Map.Entry<String, Expr> v : values.entrySet()) {
      if (env.definition(v.getKey()).isEmpty()) {
        out = out.with(v.getKey(), new Definition.Value(v.getValue()));
      }
    }
    return out;
  }

  /**
   * The slider a cell's slider statement makes: the one with the variable, given the new range and
   * its value snapped into it, or a new one at the minimum.
   *
   * @param spec what the statement asks for
   * @return the slider, with the existing one's id and style, or an id of 1 and the default style
   */
  public Slider sliderFor(SliderSpec spec) {
    Slider was = sliders.get(spec.variable());
    Slider shape =
        new Slider(
            was == null ? new ObjectId(1) : was.id(),
            was == null ? Style.DEFAULT : was.style(),
            spec.variable(),
            spec.min(),
            spec.max(),
            spec.step(),
            spec.min());
    return was == null ? shape : shape.withValue(Command.SetSlider.snap(shape, was.value()));
  }

  /**
   * These bindings with a slider set as a cell's slider statement sets it, for the cells after it
   * in the same run.
   *
   * @param spec what the statement asks for
   * @return the bindings
   */
  public CanvasBindings with(SliderSpec spec) {
    Slider s = sliderFor(spec);
    Map<String, Expr> v = new LinkedHashMap<>(values);
    v.put(s.variable(), exact(s.value()));
    Map<String, Slider> sl = new LinkedHashMap<>(sliders);
    sl.put(s.variable(), s);
    return new CanvasBindings(v, sl, points);
  }

  /**
   * The exact number an exact decimal is.
   *
   * @param value the decimal
   * @return the number
   */
  static Num exact(BigDecimal value) {
    if (value.scale() <= 0) {
      return Num.of(Int.of(value.toBigIntegerExact()));
    }
    return Num.of(Rational.of(value.unscaledValue(), BigInteger.TEN.pow(value.scale())));
  }

  /**
   * The exact decimal a number is: an integer, a rational with a terminating expansion, a decimal.
   *
   * @param e a value
   * @return the decimal, or empty when the value is none of those
   */
  static Optional<BigDecimal> decimal(Expr e) {
    if (!(e instanceof Num n)) {
      return Optional.empty();
    }
    if (n.value() instanceof Int i) {
      return Optional.of(new BigDecimal(i.value()));
    }
    if (n.value() instanceof Rational r) {
      try {
        return Optional.of(new BigDecimal(r.num()).divide(new BigDecimal(r.den())));
      } catch (ArithmeticException nonTerminating) {
        return Optional.empty();
      }
    }
    return n.value() instanceof Real r ? Optional.of(r.value()) : Optional.empty();
  }

  private static Expr point(double x, double y) {
    return Vector.of(Num.of(Real.of(x)), Num.of(Real.of(y)));
  }

  /**
   * The canvas variables whose values differ between two bindings, or that one has and the other
   * not.
   *
   * @param other the other bindings
   * @return the names
   */
  public Set<String> changedFrom(CanvasBindings other) {
    Objects.requireNonNull(other, "other");
    Set<String> out = new HashSet<>();
    for (Map.Entry<String, Expr> v : values.entrySet()) {
      if (!v.getValue().equals(other.values.get(v.getKey()))) {
        out.add(v.getKey());
      }
    }
    for (String k : other.values.keySet()) {
      if (!values.containsKey(k)) {
        out.add(k);
      }
    }
    return out;
  }
}
