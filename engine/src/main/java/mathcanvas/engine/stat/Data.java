package mathcanvas.engine.stat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;

/**
 * Reading the data of a statistics call: a sample is a vector or a list whose entries are real
 * numbers or exact real constants ({@code π}, {@code √2}), with an optional second list of counts,
 * non-negative integers; paired data is a matrix of two columns or two samples of one length. An
 * argument or entry that holds a symbol may still stand for data, so the call stays (the readers
 * return empty); any other argument is an {@link IllegalArgumentException} with the language's
 * message. A decimal entry is read as the rational it denotes, and the sample remembers that it
 * held one, so that the result is rounded once at the end ({@link #decimal}).
 */
final class Data {

  /** The digits beyond the precision a constant's value is compared at. */
  private static final int GUARD = 10;

  private Data() {}

  /**
   * One value of a sample.
   *
   * @param value the exact value: a decimal as the rational it denotes
   * @param original the entry as given
   * @param key the value's number, by which entries are ordered and tied
   * @param count how many times it occurs
   */
  record Entry(Expr value, Expr original, RealNumeric key, BigInteger count) {}

  /**
   * A sample.
   *
   * @param entries its values in the given order
   * @param n the number of values, the counts summed
   * @param decimal whether an entry held a decimal
   */
  record Sample(List<Entry> entries, BigInteger n, boolean decimal) {

    /** The entries ordered by value, ties in the given order. */
    List<Entry> sorted() {
      List<Entry> out = new ArrayList<>(entries);
      out.sort((a, b) -> a.key().compareValue(b.key()));
      return out;
    }

    /** The values, each as often as its count, for a sample without counts. */
    List<Expr> values() {
      return entries.stream().map(Entry::value).toList();
    }
  }

  /**
   * Reads a sample.
   *
   * @param name the function, for messages
   * @param data the data argument
   * @param counts the counts argument, or null
   * @param least the fewest values the function needs
   * @param ctx the precision, numerics and cancellation
   * @return the sample, or empty when an argument holds a symbol
   */
  static Optional<Sample> sample(String name, Expr data, Expr counts, int least, Context ctx) {
    Optional<List<Expr>> items = items(name, data);
    if (items.isEmpty()) {
      return Optional.empty();
    }
    List<BigInteger> cs = null;
    if (counts != null) {
      Optional<List<Expr>> raw = items(name, counts);
      if (raw.isEmpty()) {
        return Optional.empty();
      }
      if (raw.get().size() != items.get().size()) {
        throw new IllegalArgumentException("`" + name + "` needs one count per value");
      }
      cs = new ArrayList<>();
      for (Expr c : raw.get()) {
        if (!(c instanceof Num num && num.value() instanceof Int k && k.signum() >= 0)) {
          throw new IllegalArgumentException(
              "`" + name + "` needs counts that are non-negative integers");
        }
        cs.add(k.value());
      }
    }
    List<Entry> entries = new ArrayList<>();
    BigInteger n = BigInteger.ZERO;
    boolean decimal = false;
    for (int i = 0; i < items.get().size(); i++) {
      Expr item = items.get().get(i);
      if (holdsSymbol(item)) {
        return Optional.empty();
      }
      BigInteger count = cs == null ? BigInteger.ONE : cs.get(i);
      if (count.signum() == 0) {
        continue;
      }
      decimal |= holdsDecimal(item);
      Expr exact = exact(item);
      entries.add(new Entry(exact, item, key(name, exact, ctx), count));
      n = n.add(count);
    }
    if (n.compareTo(BigInteger.valueOf(least)) < 0) {
      throw new IllegalArgumentException(
          "`" + name + "` needs at least " + least + (least == 1 ? " value" : " values"));
    }
    return Optional.of(new Sample(List.copyOf(entries), n, decimal));
  }

  /**
   * Reads paired data: a matrix of two columns, or two samples of one length; no counts.
   *
   * @param name the function, for messages
   * @param args the arguments: the matrix, or the two samples
   * @param least the fewest pairs the function needs
   * @param ctx the precision, numerics and cancellation
   * @return the two samples, or empty when an argument holds a symbol
   */
  static Optional<List<Sample>> pairs(String name, List<Expr> args, int least, Context ctx) {
    Expr xs;
    Expr ys;
    if (args.size() == 1) {
      Expr m = args.getFirst();
      if (!(m instanceof Matrix matrix)) {
        if (holdsSymbol(m) && !(m instanceof Vector) && !(m instanceof ListExpr)) {
          return Optional.empty();
        }
        throw new IllegalArgumentException(pairsMessage(name));
      }
      if (matrix.rows().getFirst().size() != 2) {
        throw new IllegalArgumentException(pairsMessage(name));
      }
      xs = new Vector(matrix.rows().stream().map(List::getFirst).toList());
      ys = new Vector(matrix.rows().stream().map(r -> r.get(1)).toList());
    } else {
      xs = args.get(0);
      ys = args.get(1);
    }
    Optional<List<Expr>> a = items(name, xs);
    Optional<List<Expr>> b = items(name, ys);
    if (a.isEmpty() || b.isEmpty()) {
      return Optional.empty();
    }
    if (a.get().size() != b.get().size()) {
      throw new IllegalArgumentException(pairsMessage(name));
    }
    Optional<Sample> sx = sample(name, xs, null, least, ctx);
    Optional<Sample> sy = sample(name, ys, null, least, ctx);
    if (sx.isEmpty() || sy.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(List.of(sx.get(), sy.get()));
  }

  private static String pairsMessage(String name) {
    return "`" + name + "` needs two lists of the same length or a matrix of two columns";
  }

  /** The entries of a vector or a list; empty when the argument holds a symbol. */
  private static Optional<List<Expr>> items(String name, Expr data) {
    List<Expr> items;
    if (data instanceof Vector v) {
      items = v.items();
    } else if (data instanceof ListExpr l) {
      for (Expr item : l.items()) {
        if (!scalar(item)) {
          throw new IllegalArgumentException(message(name));
        }
      }
      items = l.items();
    } else if (holdsSymbol(data) && !(data instanceof Matrix)) {
      return Optional.empty();
    } else {
      throw new IllegalArgumentException(message(name));
    }
    return Optional.of(items);
  }

  private static boolean scalar(Expr e) {
    return !(e instanceof Vector
        || e instanceof Matrix
        || e instanceof ListExpr
        || e instanceof mathcanvas.engine.expr.SetExpr
        || e instanceof mathcanvas.engine.expr.Relation
        || e instanceof mathcanvas.engine.expr.Lambda
        || e instanceof Bool);
  }

  static String message(String name) {
    return "`" + name + "` needs data: a list or vector of real numbers";
  }

  /**
   * The value of a real constant or number, to order it by.
   *
   * @throws IllegalArgumentException if it is complex, infinite or undefined
   */
  private static RealNumeric key(String name, Expr e, Context ctx) {
    if (e instanceof Num n) {
      if (n.value() instanceof RealNumeric r) {
        return r;
      }
      throw new IllegalArgumentException("`" + name + "` needs real data");
    }
    MathContext w = new MathContext(ctx.precision().getPrecision() + GUARD, RoundingMode.HALF_EVEN);
    Optional<Numeric> v = ctx.numerics().value(e, w);
    if (v.isPresent() && v.get() instanceof RealNumeric r) {
      return r;
    }
    if (v.isPresent()) {
      throw new IllegalArgumentException("`" + name + "` needs real data");
    }
    throw new IllegalArgumentException(message(name));
  }

  /**
   * A real constant or number as the language reads it: a number of the tower, or a closed
   * expression with a real value. Empty when it holds a symbol.
   *
   * @param name the function, for messages
   * @param e the argument
   * @param what what the argument is, for messages ("σ", "p")
   * @param ctx the numerics
   * @return its exact form and value, or empty
   * @throws IllegalArgumentException if it has no real value
   */
  static Optional<Entry> real(String name, Expr e, String what, Context ctx) {
    if (holdsSymbol(e)) {
      return Optional.empty();
    }
    Expr exact = exact(e);
    RealNumeric k;
    try {
      k = key(name, exact, ctx);
    } catch (IllegalArgumentException notReal) {
      throw new IllegalArgumentException("`" + name + "` needs a real " + what);
    }
    return Optional.of(new Entry(exact, e, k, BigInteger.ONE));
  }

  static boolean holdsSymbol(Expr e) {
    if (e instanceof Symbol) {
      return true;
    }
    for (Expr o : Operands.of(e)) {
      if (holdsSymbol(o)) {
        return true;
      }
    }
    return false;
  }

  static boolean holdsDecimal(Expr e) {
    if (e instanceof Num n) {
      return !n.value().isExact();
    }
    for (Expr o : Operands.of(e)) {
      if (holdsDecimal(o)) {
        return true;
      }
    }
    return false;
  }

  /** The expression with every decimal the rational it denotes. */
  static Expr exact(Expr e) {
    if (e instanceof Num n) {
      return n.value() instanceof Real r ? Num.of(rational(r.value())) : n;
    }
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty() || !holdsDecimal(e)) {
      return e;
    }
    List<Expr> out = new ArrayList<>(ops.size());
    for (Expr o : ops) {
      out.add(exact(o));
    }
    return Operands.rebuild(e, out);
  }

  private static RealNumeric rational(BigDecimal d) {
    BigInteger unscaled = d.unscaledValue();
    int scale = d.scale();
    return scale <= 0
        ? Int.of(unscaled.multiply(BigInteger.TEN.pow(-scale)))
        : Rational.of(unscaled, BigInteger.TEN.pow(scale));
  }
}
