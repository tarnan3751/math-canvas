package mathcanvas.engine.solve;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.Cancellation;

/**
 * The real roots of a square-free polynomial over ℤ, isolated exactly by Sturm's theorem: the
 * number of distinct real roots in {@code (a, b]} is {@code V(a) − V(b)}, the sign changes of the
 * Sturm sequence {@code S, S', −rem(S, S'), …} (Sturm 1835; Cohen, <i>A Course in Computational
 * Algebraic Number Theory</i>, §4.1.3). Bisection of the Cauchy bound's interval at dyadic points
 * gives each root an interval of its own with a sign change at its ends, or the root itself when a
 * point hits it.
 */
final class RealRoots {

  /** A dyadic rational {@code n/2^k}, {@code d = 2^k}. */
  record Dyadic(BigInteger n, BigInteger d) {

    /** The midpoint, over the common denominator {@code 2·max(d, o.d)}. */
    Dyadic mid(Dyadic o) {
      BigInteger common = d.max(o.d);
      BigInteger sum = n.multiply(common.divide(d)).add(o.n.multiply(common.divide(o.d)));
      return new Dyadic(sum, common.shiftLeft(1));
    }
  }

  /** An isolating interval {@code (lo, hi]} with one root, or the exact root when {@code exact}. */
  record Isolated(Dyadic lo, Dyadic hi, boolean exact) {}

  /** The most bisections of one interval: past the separation of any root in reach. */
  private static final int MAX_DEPTH = 20_000;

  private final IntPoly s;
  private final List<IntPoly> sturm = new ArrayList<>();
  private final Cancellation cancellation;

  RealRoots(IntPoly s, Cancellation cancellation) {
    this.s = s;
    this.cancellation = cancellation;
    sturm.add(s);
    IntPoly a = s;
    IntPoly b = s.derivative().primitive();
    while (!b.isZero()) {
      cancellation.check();
      sturm.add(b);
      IntPoly r = a.positiveRemainder(b);
      a = b;
      b = negate(r);
    }
  }

  private static IntPoly negate(IntPoly p) {
    BigInteger[] out = new BigInteger[p.c().length];
    for (int k = 0; k < out.length; k++) {
      out[k] = p.c()[k].negate();
    }
    return new IntPoly(out);
  }

  /** The number of distinct real roots. */
  int count() {
    return variationsAtInfinity(-1) - variationsAtInfinity(1);
  }

  private int variationsAtInfinity(int side) {
    int changes = 0;
    int last = 0;
    for (IntPoly p : sturm) {
      int sign = p.lead().signum() * (side < 0 && p.degree() % 2 == 1 ? -1 : 1);
      if (sign != 0) {
        if (last != 0 && sign != last) {
          changes++;
        }
        last = sign;
      }
    }
    return changes;
  }

  private int variations(Dyadic t) {
    int changes = 0;
    int last = 0;
    for (IntPoly p : sturm) {
      int sign = p.signAt(t.n(), t.d());
      if (sign != 0) {
        if (last != 0 && sign != last) {
          changes++;
        }
        last = sign;
      }
    }
    return changes;
  }

  /** The isolating intervals, from the left. */
  List<Isolated> isolate() {
    // Cauchy's bound: every root lies in (−B, B), B = 1 + max |a_k / a_n|
    BigInteger lead = s.lead().abs();
    BigInteger max = BigInteger.ZERO;
    for (BigInteger a : s.c()) {
      max = max.max(a.abs());
    }
    BigInteger bound = max.divide(lead).add(BigInteger.TWO);
    Dyadic lo = new Dyadic(bound.negate(), BigInteger.ONE);
    Dyadic hi = new Dyadic(bound, BigInteger.ONE);
    List<Isolated> out = new ArrayList<>();
    split(lo, variations(lo), hi, variations(hi), out, 0);
    return out;
  }

  private void split(Dyadic lo, int vlo, Dyadic hi, int vhi, List<Isolated> out, int depth) {
    cancellation.check();
    int count = vlo - vhi;
    if (count == 0) {
      return;
    }
    if (count == 1) {
      if (s.signAt(hi.n(), hi.d()) == 0) {
        out.add(new Isolated(hi, hi, true));
        return;
      }
      if (s.signAt(lo.n(), lo.d()) != 0) {
        out.add(new Isolated(lo, hi, false));
        return;
      }
    }
    if (depth > MAX_DEPTH) {
      throw new ArithmeticException("real roots too close to separate");
    }
    Dyadic mid = lo.mid(hi);
    int vmid = variations(mid);
    split(lo, vlo, mid, vmid, out, depth + 1);
    split(mid, vmid, hi, vhi, out, depth + 1);
  }
}
