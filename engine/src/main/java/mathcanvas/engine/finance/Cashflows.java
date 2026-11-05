package mathcanvas.engine.finance;

import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.RealNumeric;

/**
 * Cash flows ({@code docs/language.md} §14.28): {@code npv(r, [c₁, …])}, the first flow at period 1
 * as in Excel, and the amortization schedule {@code amortization(r, n, pv)}.
 */
public final class Cashflows {

  /** The most rows of an amortization schedule. */
  public static final int MAX_PERIODS = 1000;

  private Cashflows() {}

  /**
   * The expression of the net present value {@code Σ cₖ/(1 + r)ᵏ}, from {@code k = 1}, to be
   * evaluated.
   *
   * @param r the rate
   * @param flows the cash flows
   * @return the expression
   */
  public static Expr npv(Expr r, List<Expr> flows) {
    List<Expr> terms = new ArrayList<>();
    terms.add(Num.ZERO);
    for (int k = 0; k < flows.size(); k++) {
      terms.add(Product.of(flows.get(k), Power.of(Sum.of(Num.ONE, r), Num.of(-(k + 1)))));
    }
    return Sum.of(terms);
  }

  /**
   * The amortization schedule of a loan {@code pv} over {@code n} periods at the rate {@code r},
   * paid at the end of each period: rows of the period, the payment ({@code pmt}), the interest
   * ({@code −balance·r}), the principal (payment − interest) and the balance after it, in Excel's
   * signs, so the payments are negative for a positive loan and the balance falls to 0.
   *
   * @param r the rate, above −1
   * @param n the periods, an integer from 1 to {@link #MAX_PERIODS}
   * @param pv the loan
   * @param mc the precision of decimal arithmetic
   * @return the schedule
   * @throws Tvm.FinanceException if {@code n} is out of range
   */
  public static Matrix amortization(RealNumeric r, RealNumeric n, RealNumeric pv, MathContext mc) {
    if (!(n instanceof Int k)
        || k.signum() <= 0
        || k.value().intValue() > MAX_PERIODS
        || k.value().bitLength() > 31) {
      throw new Tvm.FinanceException(
          "`amortization`'s periods must be an integer from 1 to " + MAX_PERIODS);
    }
    int periods = k.value().intValue();
    RealNumeric payment;
    if (r.isZero()) {
      payment = (RealNumeric) pv.negate().divide(n, mc);
    } else {
      RealNumeric g = (RealNumeric) r.add(Int.ONE, mc).pow(periods, mc);
      payment =
          (RealNumeric)
              r.multiply(pv, mc).multiply(g, mc).divide(g.subtract(Int.ONE, mc), mc).negate();
    }
    List<List<Expr>> rows = new ArrayList<>();
    RealNumeric balance = pv;
    for (int p = 1; p <= periods; p++) {
      RealNumeric interest = (RealNumeric) balance.multiply(r, mc).negate();
      RealNumeric principal = (RealNumeric) payment.subtract(interest, mc);
      balance = (RealNumeric) balance.add(principal, mc);
      rows.add(
          List.of(
              Num.of(p), Num.of(payment), Num.of(interest), Num.of(principal), Num.of(balance)));
    }
    return Matrix.of(rows);
  }
}
