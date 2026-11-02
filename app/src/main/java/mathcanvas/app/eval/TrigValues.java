package mathcanvas.app.eval;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import mathcanvas.app.text.Messages;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.CancelledException;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.EvaluationException;
import mathcanvas.engine.eval.Evaluator;
import mathcanvas.engine.eval.NumericForm;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.Display;
import mathcanvas.engine.print.Format;
import mathcanvas.engine.print.LayoutPrinter;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.print.layout.Layout;
import mathcanvas.render.typeset.Box;

/**
 * The unit circle view's engine work, run on the worker: an angle typed in the current angle mode
 * read to radians, and the sine, cosine and tangent of an angle, exact at the special angles and
 * decimal otherwise, printed and typeset here so the view only shows the boxes.
 */
public final class TrigValues {

  /** The functions, in the order of the values. */
  public static final List<String> FUNCTIONS = List.of("sin", "cos", "tan");

  /** The size the values are typeset at. */
  public static final double SIZE = Typeset.STEP_SIZE;

  /**
   * An angle.
   *
   * @param radians the angle in radians, typed, or null for one known only as a double (dragged)
   * @param value its value as a double, reduced into {@code [0, 2π)} for a typed one
   * @param special the multiple {@code k} of {@code π/12} a typed angle is exactly, from 0 to 23,
   *     or empty
   */
  public record Angle(Expr radians, double value, OptionalInt special) {

    /**
     * An angle known only as a double, a dragged one.
     *
     * @param value the angle in radians
     * @return the angle
     */
    public static Angle of(double value) {
      return new Angle(null, value, OptionalInt.empty());
    }
  }

  /**
   * One function's value.
   *
   * @param text the value as text, or null where the function is undefined; the bundle's
   *     "unavailable" when printing or typesetting it failed
   * @param box the value typeset, or null where it is undefined or over the typesetting cap
   */
  public record Value(String text, Box box) {}

  private TrigValues() {}

  /**
   * Reads an angle typed in the environment's angle mode, as the evaluator reads the argument of a
   * trigonometric function ({@code docs/language.md} §14.2): a value that is a plain number (an
   * integer, a rational or a decimal) is in the mode's unit, any other real value ({@code π/6},
   * {@code 30°}) in radians.
   *
   * @param text the text
   * @param env the environment it is evaluated in, after the last cell
   * @param cancellation the request to stop
   * @return the angle, or null when the text's value is not a real number
   * @throws mathcanvas.engine.parse.ParseException if the text is not one expression
   * @throws EvaluationException if its evaluation fails
   */
  public static Angle read(String text, Environment env, Cancellation cancellation) {
    Expr typed = Canonical.of(Parser.parseExpression(text, env.names()));
    Expr value = Evaluator.evaluate(typed, env, false, cancellation).value();
    AngleMode mode = env.settings().angleMode();
    Expr radians =
        value instanceof Num n && n.value() instanceof RealNumeric
            ? Evaluator.evaluate(
                    Product.of(value, mode.toRadians()), radians(env), false, cancellation)
                .value()
            : value;
    double at = toDouble(NumericForm.numeric(radians, 17, cancellation));
    if (!Double.isFinite(at)) {
      return null;
    }
    // the special angle it is exactly: radians·12/π an integer
    Expr twelfths =
        Evaluator.evaluate(
                Product.of(radians, Num.of(12), Power.of(Constant.PI, Num.MINUS_ONE)),
                radians(env),
                false,
                cancellation)
            .value();
    OptionalInt special =
        twelfths instanceof Num n && n.value() instanceof Int k
            ? OptionalInt.of(k.value().mod(BigInteger.valueOf(24)).intValue())
            : OptionalInt.empty();
    return new Angle(radians, reduced(radians, at, cancellation), special);
  }

  /**
   * An angle reduced into {@code [0, 2π)} exactly: the fraction of a turn it is, {@code
   * radians/(2π)}, taken at enough digits for its integer part and 20 more, so a huge angle lands
   * where its sine and cosine are.
   */
  private static double reduced(Expr radians, double estimate, Cancellation cancellation) {
    int whole = (int) Math.max(0, Math.ceil(Math.log10(Math.abs(estimate) / (2 * Math.PI) + 1)));
    int digits = Math.min(Settings.MAX_PRECISION, whole + 20);
    Expr turns =
        NumericForm.numeric(
            Product.of(radians, Power.of(Product.of(Num.of(2), Constant.PI), Num.MINUS_ONE)),
            digits,
            cancellation);
    if (!(turns instanceof Num n && n.value() instanceof RealNumeric r)) {
      return estimate;
    }
    BigDecimal q = r.toReal(new MathContext(digits)).value();
    BigDecimal fraction = q.subtract(q.setScale(0, RoundingMode.FLOOR));
    double at = fraction.doubleValue() * 2 * Math.PI;
    return at >= 2 * Math.PI ? 0 : at;
  }

  /**
   * The sine, cosine and tangent of an angle, evaluated in radians: exact at a special angle {@code
   * k·π/12} (a multiple of 15°), otherwise decimals at a number of digits, of the angle as typed
   * or, for a dragged one, of its double as a decimal.
   *
   * @param angle the angle
   * @param special the special angle's {@code k}, or empty
   * @param env the environment, after the last cell
   * @param digits the decimals' significant digits
   * @param cancellation the request to stop
   * @return the three values, in the order of {@link #FUNCTIONS}
   */
  public static List<Value> values(
      Angle angle, OptionalInt special, Environment env, int digits, Cancellation cancellation) {
    boolean exact = special.isPresent();
    Expr radians =
        exact
            ? Product.of(
                Num.of(special.getAsInt()), Constant.PI, Power.of(Num.of(12), Num.MINUS_ONE))
            : angle.radians() != null ? angle.radians() : Num.of(Real.of(angle.value()));
    Environment in = radians(env);
    Format format = Format.decimal(digits);
    List<Value> out = new ArrayList<>();
    for (String f : FUNCTIONS) {
      Expr call = Apply.of(Symbol.of(f), radians);
      Expr v;
      try {
        v = Evaluator.evaluate(call, in, false, cancellation).value();
        if (!exact) {
          v = NumericForm.numeric(v, digits, cancellation);
        }
        // a real number, which tan(π/2), undefined, is not
        if (Double.isNaN(toDouble(exact ? NumericForm.numeric(v, 17, cancellation) : v))) {
          v = null;
        }
      } catch (EvaluationException e) {
        v = null;
      }
      if (v == null) {
        out.add(new Value(null, null));
        continue;
      }
      try {
        String text;
        Layout layout;
        if (exact) {
          Expr shown = Display.of(v, cancellation);
          text = UnicodePrinter.INSTANCE.print(shown, cancellation);
          layout = Typeset.capped(LayoutPrinter.INSTANCE.print(shown, cancellation));
        } else {
          text = UnicodePrinter.INSTANCE.withFormat(format).display(v, cancellation);
          layout =
              Typeset.capped(LayoutPrinter.INSTANCE.withFormat(format).display(v, cancellation));
        }
        out.add(new Value(text, Typeset.box(layout, SIZE, false, cancellation)));
      } catch (CancelledException stopped) {
        throw stopped;
      } catch (RuntimeException failed) {
        // a failure printing or typesetting the value shows it as unavailable, never hangs the view
        out.add(new Value(Messages.text("trig.unavailable"), null));
      }
    }
    return out;
  }

  private static Environment radians(Environment env) {
    return env.withSettings(env.settings().withAngleMode(AngleMode.RADIANS));
  }

  private static double toDouble(Expr e) {
    return e instanceof Num n && n.value() instanceof RealNumeric r ? r.toDouble() : Double.NaN;
  }
}
