package mathcanvas.engine.numbertheory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

/**
 * {@code powmod} and {@code invmod} agree with {@code BigInteger}; {@code crt}'s value satisfies
 * every congruence and lies below the lcm of the moduli, or the congruences disagree; {@code
 * diophantine}'s solutions satisfy the equation at several integers {@code k}, and there are none
 * exactly when the gcd does not divide the right side. Every traced run is a chain.
 */
class ModularProperties {

  private static final Context TRACED = Context.DEFAULT.withTrace(true);

  private static Num n(BigInteger v) {
    return Num.of(Int.of(v));
  }

  private static BigInteger big(Random r, int bits) {
    BigInteger v = new BigInteger(1 + r.nextInt(bits), r);
    return r.nextBoolean() ? v : v.negate();
  }

  private static BigInteger positive(Random r, int bits) {
    return new BigInteger(1 + r.nextInt(bits), r).add(BigInteger.ONE);
  }

  private static BigInteger integer(Expr e) {
    return ((Int) ((Num) e).value()).value();
  }

  @Property(tries = 300, seed = "8401")
  void powmodAgreesWithBigInteger(@ForAll long seed) {
    Random r = new Random(seed);
    BigInteger a = big(r, 80);
    BigInteger k = big(r, 70);
    BigInteger m = positive(r, 60);
    Apply call = Apply.of(Symbol.of("powmod"), n(a), n(k), n(m));
    Rewriter.Result result = Modular.powmod(call, a, k, m, TRACED);
    if (result.trace().rewrites().size() > 0) {
      TraceChecks.assertChain(result);
    }
    BigInteger expected;
    try {
      expected =
          m.equals(BigInteger.ONE)
              ? BigInteger.ZERO
              : k.signum() == 0 && a.signum() == 0 ? null : a.modPow(k, m);
    } catch (ArithmeticException noInverse) {
      expected = null;
    }
    if (expected == null) {
      assertThat(result.expr()).isEqualTo(Constant.UNDEFINED);
    } else {
      assertThat(integer(result.expr())).isEqualTo(expected);
    }
  }

  @Property(tries = 300, seed = "8402")
  void invmodAgreesWithBigInteger(@ForAll long seed) {
    Random r = new Random(seed);
    BigInteger a = big(r, 100);
    BigInteger m = positive(r, 90);
    Apply call = Apply.of(Symbol.of("invmod"), n(a), n(m));
    Rewriter.Result result = Modular.invmod(call, a, m, TRACED);
    if (!result.trace().rewrites().isEmpty()) {
      TraceChecks.assertChain(result);
    }
    if (m.equals(BigInteger.ONE)) {
      assertThat(result.expr()).isEqualTo(Num.ZERO);
    } else if (a.gcd(m).equals(BigInteger.ONE)) {
      assertThat(integer(result.expr())).isEqualTo(a.modInverse(m));
    } else {
      assertThat(result.expr()).isEqualTo(Constant.UNDEFINED);
    }
  }

  @Property(tries = 300, seed = "8403")
  void crtSatisfiesEveryCongruence(@ForAll long seed) {
    Random r = new Random(seed);
    int count = 1 + r.nextInt(4);
    List<BigInteger> rs = new ArrayList<>();
    List<BigInteger> ms = new ArrayList<>();
    BigInteger hidden = big(r, 40);
    for (int i = 0; i < count; i++) {
      BigInteger m = positive(r, 12);
      ms.add(m);
      // half the systems are consistent by construction, the others random
      rs.add(r.nextBoolean() ? hidden.mod(m).add(m.multiply(big(r, 5))) : big(r, 14));
    }
    Apply call =
        Apply.of(
            Symbol.of("crt"),
            mathcanvas.engine.parse.Brackets.of(
                rs.stream().<Expr>map(ModularProperties::n).toList()),
            mathcanvas.engine.parse.Brackets.of(
                ms.stream().<Expr>map(ModularProperties::n).toList()));
    Rewriter.Result result = Modular.crt(call, rs, ms, TRACED);
    TraceChecks.assertChain(result);
    BigInteger lcm = BigInteger.ONE;
    for (BigInteger m : ms) {
      lcm = lcm.divide(lcm.gcd(m)).multiply(m);
    }
    boolean consistent = true;
    for (int i = 0; i < count; i++) {
      for (int j = 0; j < count; j++) {
        BigInteger g = ms.get(i).gcd(ms.get(j));
        consistent &= rs.get(i).subtract(rs.get(j)).mod(g).signum() == 0;
      }
    }
    if (!consistent) {
      assertThat(result.expr()).isEqualTo(Constant.UNDEFINED);
      return;
    }
    BigInteger x = integer(result.expr());
    assertThat(x.signum()).isGreaterThanOrEqualTo(0);
    assertThat(x).isLessThan(lcm);
    for (int i = 0; i < count; i++) {
      assertThat(x.subtract(rs.get(i)).mod(ms.get(i))).isEqualTo(BigInteger.ZERO);
    }
  }

  @Property(tries = 300, seed = "8404")
  void diophantineSolutionsSubstituteBack(@ForAll long seed) {
    Random r = new Random(seed);
    BigInteger a = big(r, 20);
    BigInteger b = big(r, 20);
    BigInteger c = big(r, 30);
    Symbol x = Symbol.of("x");
    Symbol y = Symbol.of("y");
    Symbol k = Symbol.of("k");
    Expr equation =
        Canonical.of(
            Relation.of(Relation.Op.EQ, Sum.of(Product.of(n(a), x), Product.of(n(b), y)), n(c)));
    Expr call = Apply.of(Symbol.of("diophantine"), equation, ListExpr.of(x, y));
    Rewriter.Result result = Diophantine.solve(call, equation, x, y, k, TRACED);
    if (a.signum() == 0 && b.signum() == 0 && c.signum() == 0) {
      assertThat(result.expr()).isEqualTo(call);
      return;
    }
    TraceChecks.assertChain(result);
    BigInteger g = a.gcd(b);
    if (g.signum() == 0 || c.mod(g).signum() != 0) {
      assertThat(result.expr()).isEqualTo(SetExpr.of());
      return;
    }
    List<Expr> pair = ((ListExpr) ((SetExpr) result.expr()).items().getFirst()).items();
    for (int kv = -3; kv <= 3; kv++) {
      BigInteger xv = integer(sub(((Relation) pair.get(0)).right(), kv));
      BigInteger yv = integer(sub(((Relation) pair.get(1)).right(), kv));
      assertThat(a.multiply(xv).add(b.multiply(yv))).isEqualTo(c);
    }
  }

  private static Expr sub(Expr e, int k) {
    return Canonical.of(put(e, Num.of(k)));
  }

  /** {@code e} with the symbol {@code k} replaced. */
  private static Expr put(Expr e, Expr value) {
    if (e.equals(Symbol.of("k"))) {
      return value;
    }
    List<Expr> ops = mathcanvas.engine.match.Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    return mathcanvas.engine.match.Operands.rebuild(
        e, ops.stream().map(o -> put(o, value)).toList());
  }
}
