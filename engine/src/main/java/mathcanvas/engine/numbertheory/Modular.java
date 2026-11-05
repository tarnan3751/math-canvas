package mathcanvas.engine.numbertheory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.parse.Brackets;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;

/**
 * Modular arithmetic with its steps ({@code docs/language.md} §14.23): {@code powmod} by square and
 * multiply from the exponent's lowest bit, {@code invmod} by the extended Euclidean algorithm and
 * {@code crt} by merging the congruences two at a time. Each step is a rewrite at the root in the
 * rule set {@code nt} that replaces the whole state, so the steps chain from the call to the value:
 * {@code powmod}'s states are {@code r · powmod(b, e, m)}, {@code invmod}'s {@code t mod m} and
 * {@code crt}'s the call on fewer congruences.
 */
final class Modular {

  /** The rule set of the steps. */
  static final String RULE_SET = "nt";

  /** The steps, each narrated by the key {@code nt.<step>}. */
  static final List<String> STEPS =
      List.of(
          "powmod-square",
          "powmod-multiply",
          "powmod-inverse",
          "powmod-long",
          "powmod-none",
          "invmod-euclid",
          "invmod-reduce",
          "invmod-none",
          "crt-merge",
          "crt-done",
          "crt-conflict");

  /** The exponents of at most this many bits are narrated bit by bit. */
  static final int NARRATED_BITS = 64;

  private static final Symbol POWMOD = Symbol.of("powmod");
  private static final Symbol MOD = Symbol.of("mod");

  private final Context ctx;
  private final List<Rewrite> rewrites = new ArrayList<>();
  private final Expr call;
  private Expr current;

  private Modular(Apply call, Context ctx) {
    this.ctx = ctx;
    this.call = canon(call);
    this.current = this.call;
  }

  private Expr canon(Expr e) {
    return Canonical.of(e, ctx.precision(), ctx.cancellation());
  }

  private static Num num(BigInteger v) {
    return Num.of(Int.of(v));
  }

  /** Records the step {@code rule} to {@code after}, with named values (name, value, …). */
  private void step(String rule, Expr after, Object... pairs) {
    Expr next = canon(after);
    if (ctx.traceEnabled()) {
      TreeMap<String, Expr> values = new TreeMap<>();
      for (int k = 0; k < pairs.length; k += 2) {
        values.put((String) pairs[k], (Expr) pairs[k + 1]);
      }
      rewrites.add(new Rewrite(current, next, RULE_SET, rule, Path.ROOT, Bindings.EMPTY, values));
    }
    current = next;
  }

  private Rewriter.Result result() {
    Trace trace =
        ctx.traceEnabled() && !rewrites.isEmpty()
            ? new Trace(call, current, rewrites, List.of())
            : Trace.NONE;
    return new Rewriter.Result(current, trace);
  }

  private static void positive(String name, BigInteger m) {
    if (m.signum() <= 0) {
      throw new IllegalArgumentException("`" + name + "`'s modulus must be a positive integer");
    }
  }

  /**
   * {@code powmod(a, k, m)}: {@code a^k mod m} in {@code [0, m)}; a negative {@code k} through the
   * inverse, {@code undefined} when there is none, and {@code powmod(0, 0, m)} {@code undefined} as
   * {@code 0^0} is, except modulo 1, where every value is 0; the test is on {@code a} itself, so
   * {@code powmod(5, 0, 5)} is 1.
   */
  static Rewriter.Result powmod(Apply call, BigInteger a, BigInteger k, BigInteger m, Context ctx) {
    positive("powmod", m);
    Modular run = new Modular(call, ctx);
    if (m.equals(BigInteger.ONE)) {
      return new Rewriter.Result(Num.ZERO, Trace.NONE);
    }
    BigInteger base = a.mod(m);
    if (k.signum() == 0 && a.signum() == 0) {
      return new Rewriter.Result(Constant.UNDEFINED, Trace.NONE);
    }
    if (k.signum() == 0) {
      return new Rewriter.Result(Num.ONE, Trace.NONE);
    }
    if (k.signum() < 0) {
      BigInteger g = base.gcd(m);
      if (!g.equals(BigInteger.ONE)) {
        run.step("powmod-none", Constant.UNDEFINED, "a", num(a), "m", num(m), "g", num(g));
        return run.result();
      }
      BigInteger inverse = base.modInverse(m);
      k = k.negate();
      run.step(
          "powmod-inverse",
          powmodState(BigInteger.ONE, inverse, k, m),
          "a",
          num(a),
          "b",
          num(inverse),
          "m",
          num(m));
      base = inverse;
    }
    if (!ctx.traceEnabled() || k.bitLength() > NARRATED_BITS) {
      run.step("powmod-long", num(base.modPow(k, m)), "bits", Num.of(k.bitLength()));
      return run.result();
    }
    BigInteger r = BigInteger.ONE;
    BigInteger e = k;
    boolean first = true;
    while (e.signum() > 0) {
      Num binary = first ? num(new BigInteger(k.toString(2))) : null;
      if (e.testBit(0)) {
        BigInteger next = r.multiply(base).mod(m);
        e = e.subtract(BigInteger.ONE);
        run.step(
            "powmod-multiply",
            powmodState(next, base, e, m),
            withBinary(binary, k, "b", num(base), "r", num(next), "m", num(m)));
        r = next;
        if (r.signum() == 0) {
          // the result is 0 whatever the rest of the exponent
          break;
        }
      } else {
        BigInteger square = base.multiply(base).mod(m);
        e = e.shiftRight(1);
        run.step(
            "powmod-square",
            powmodState(r, square, e, m),
            withBinary(binary, k, "b", num(base), "s", num(square), "m", num(m)));
        base = square;
      }
      first = false;
    }
    return run.result();
  }

  private static Object[] withBinary(Num binary, BigInteger k, Object... pairs) {
    if (binary == null) {
      return pairs;
    }
    Object[] out = new Object[pairs.length + 4];
    System.arraycopy(pairs, 0, out, 0, pairs.length);
    out[pairs.length] = "binary";
    out[pairs.length + 1] = binary;
    out[pairs.length + 2] = "k";
    out[pairs.length + 3] = num(k);
    return out;
  }

  /** {@code r · powmod(b, e, m)}, or {@code r} once the exponent or the result is 0. */
  private static Expr powmodState(BigInteger r, BigInteger b, BigInteger e, BigInteger m) {
    if (e.signum() == 0 || r.signum() == 0) {
      return num(r);
    }
    Expr rest = Apply.of(POWMOD, num(b), num(e), num(m));
    return r.equals(BigInteger.ONE) ? rest : Product.of(num(r), rest);
  }

  /**
   * {@code invmod(a, m)}: the inverse in {@code [0, m)}, {@code undefined} when {@code gcd(a, m) ≠
   * 1}; the steps give the extended Euclidean algorithm's table, one row per division: the
   * remainder, the quotient and the remainder's coefficient of {@code a}.
   */
  static Rewriter.Result invmod(Apply call, BigInteger a, BigInteger m, Context ctx) {
    positive("invmod", m);
    if (m.equals(BigInteger.ONE)) {
      return new Rewriter.Result(Num.ZERO, Trace.NONE);
    }
    Modular run = new Modular(call, ctx);
    BigInteger r0 = m;
    BigInteger r1 = a.mod(m);
    BigInteger t0 = BigInteger.ZERO;
    BigInteger t1 = BigInteger.ONE;
    List<List<Expr>> table = new ArrayList<>();
    List<Expr> quotients = new ArrayList<>();
    while (r1.signum() != 0) {
      ctx.cancellation().check();
      BigInteger q = r0.divide(r1);
      table.add(List.of(num(r1), num(q), num(t1)));
      quotients.add(num(q));
      BigInteger r2 = r0.subtract(q.multiply(r1));
      BigInteger t2 = t0.subtract(q.multiply(t1));
      r0 = r1;
      r1 = r2;
      t0 = t1;
      t1 = t2;
    }
    if (!r0.equals(BigInteger.ONE)) {
      run.step("invmod-none", Constant.UNDEFINED, "a", num(a), "m", num(m), "g", num(r0));
      return run.result();
    }
    BigInteger s = BigInteger.ONE.subtract(t0.multiply(a.mod(m))).divide(m);
    if (ctx.traceEnabled()) {
      run.step(
          "invmod-euclid",
          Apply.of(MOD, num(t0), num(m)),
          "a",
          num(a.mod(m)),
          "m",
          num(m),
          "s",
          num(s),
          "t",
          num(t0),
          "quotients",
          Vector.of(quotients),
          "table",
          Matrix.of(table));
    }
    run.step("invmod-reduce", num(t0.mod(m)), "m", num(m));
    return run.result();
  }

  /**
   * {@code crt(residues, moduli)}: the least non-negative solution of the congruences, merged two
   * at a time (moduli need not be coprime); {@code undefined} when two disagree.
   */
  static Rewriter.Result crt(Apply call, List<BigInteger> rs, List<BigInteger> ms, Context ctx) {
    if (rs.size() != ms.size()) {
      throw new IllegalArgumentException(
          "`crt` takes a list of residues and a list of moduli of the same length");
    }
    if (rs.isEmpty()) {
      throw new IllegalArgumentException("`crt` needs at least one congruence");
    }
    for (BigInteger m : ms) {
      if (m.signum() <= 0) {
        throw new IllegalArgumentException("`crt`'s moduli must be positive integers");
      }
    }
    Modular run = new Modular(call, ctx);
    List<BigInteger> residues = new ArrayList<>();
    for (int i = 0; i < rs.size(); i++) {
      residues.add(rs.get(i).mod(ms.get(i)));
    }
    List<BigInteger> moduli = new ArrayList<>(ms);
    while (residues.size() > 1) {
      ctx.cancellation().check();
      BigInteger r1 = residues.removeFirst();
      BigInteger m1 = moduli.removeFirst();
      BigInteger r2 = residues.removeFirst();
      BigInteger m2 = moduli.removeFirst();
      BigInteger g = m1.gcd(m2);
      BigInteger d = r2.subtract(r1);
      if (d.mod(g).signum() != 0) {
        run.step(
            "crt-conflict",
            Constant.UNDEFINED,
            "a",
            num(r1),
            "m",
            num(m1),
            "b",
            num(r2),
            "n",
            num(m2),
            "g",
            num(g));
        return run.result();
      }
      BigInteger n = m2.divide(g);
      BigInteger t =
          n.equals(BigInteger.ONE)
              ? BigInteger.ZERO
              : d.divide(g).mod(n).multiply(m1.divide(g).mod(n).modInverse(n)).mod(n);
      BigInteger lcm = m1.divide(g).multiply(m2);
      BigInteger x = r1.add(m1.multiply(t)).mod(lcm);
      residues.addFirst(x);
      moduli.addFirst(lcm);
      run.step(
          "crt-merge",
          crtState(residues, moduli),
          "a",
          num(r1),
          "m",
          num(m1),
          "b",
          num(r2),
          "n",
          num(m2),
          "d",
          num(d.mod(m2)),
          "t",
          num(t),
          "k",
          num(n),
          "x",
          num(x),
          "l",
          num(lcm));
    }
    run.step("crt-done", num(residues.getFirst()), "m", num(moduli.getFirst()));
    return run.result();
  }

  private static Expr crtState(List<BigInteger> rs, List<BigInteger> ms) {
    return Apply.of(
        Symbol.of("crt"),
        Brackets.of(rs.stream().<Expr>map(Modular::num).toList()),
        Brackets.of(ms.stream().<Expr>map(Modular::num).toList()));
  }

  /** The integers of a list or a vector of integers; null when an item is not a number. */
  static List<BigInteger> integers(Expr e, String name) {
    List<Expr> items =
        switch (e) {
          case Vector v -> v.items();
          case ListExpr l -> l.items();
          default -> null;
        };
    if (items == null) {
      return null;
    }
    List<BigInteger> out = new ArrayList<>();
    for (Expr item : items) {
      if (!(item instanceof Num n)) {
        return null;
      }
      if (!(n.value() instanceof Int i)) {
        throw new IllegalArgumentException("`" + name + "` takes lists of integers");
      }
      out.add(i.value());
    }
    return out;
  }
}
