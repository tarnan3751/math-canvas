package mathcanvas.engine.eval;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Based;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.finance.Cashflows;
import mathcanvas.engine.finance.Newton;
import mathcanvas.engine.finance.Tvm;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Trace;

/**
 * The finance commands of the evaluator (§14.28): the time value of money, {@code tvm}, the cash
 * flows, and {@code rate} and {@code irr} by Newton's method, evaluated in a statement's {@link
 * Evaluator.State}.
 */
final class FinanceCommands {

  private final Evaluator.State state;

  FinanceCommands(Evaluator.State state) {
    this.state = state;
  }

  /**
   * The time value of money (§14.28): the closed forms evaluated as the expressions {@link Tvm}
   * builds, so exact data give exact values; {@code npv} likewise; the schedule of {@code
   * amortization}; and {@code rate} and {@code irr} by Newton's method, their value marked numeric
   * with the step {@code numeric.rate} or {@code numeric.irr}. A call with an argument that is not
   * a real number (or, for {@code npv} and {@code irr}, a list of them) stays.
   */
  Expr finance(Apply a, List<Expr> args) {
    String name = a.head().name();
    Expr unevaluated = state.held(a, args);
    try {
      if (Tvm.NAMES.contains(name)) {
        Expr f = Tvm.apply(name, args);
        return f == null ? unevaluated : state.eval(f);
      }
      List<Expr> flows = null;
      int flowsAt = name.equals("npv") ? 1 : name.equals("irr") ? 0 : -1;
      if (flowsAt >= 0) {
        if (!(args.get(flowsAt) instanceof Vector v)
            || !v.items().stream().allMatch(FinanceCommands::isRealNumber)) {
          return unevaluated;
        }
        flows = v.items();
      }
      for (int k = 0; k < args.size(); k++) {
        if (k != flowsAt && !isRealNumber(args.get(k))) {
          return unevaluated;
        }
      }
      return switch (name) {
        case "npv" ->
            Tvm.real(args.getFirst()).compareValue(Int.MINUS_ONE) <= 0
                ? Constant.UNDEFINED
                : state.eval(Cashflows.npv(args.getFirst(), flows));
        case "amortization" ->
            Tvm.real(args.getFirst()).compareValue(Int.MINUS_ONE) <= 0
                ? Constant.UNDEFINED
                : Cashflows.amortization(
                    Tvm.real(args.get(0)),
                    Tvm.real(args.get(1)),
                    Tvm.real(args.get(2)),
                    state.mc());
        default -> rate(a, name, args, flows);
      };
    } catch (Tvm.FinanceException x) {
      throw new EvaluationException(x.getMessage(), a);
    }
  }

  /** The parts of {@code tvm} in the order its value lists them. */
  private static final List<String> TVM_PARTS = List.of("n", "i", "pv", "pmt", "fv");

  /**
   * {@code tvm(n = …, i = …, pv = …, pmt = …, fv = …[, type = …])} (§14.28): four of the five parts
   * by name, the fifth found through {@code fv}, {@code pv}, {@code pmt}, {@code nper} or {@code
   * rate}; the value is the list of the five relations.
   */
  Expr tvm(Apply a) {
    TreeMap<String, Expr> parts = new TreeMap<>();
    for (Expr arg : a.args()) {
      if (!(arg instanceof Relation r
          && r.op() == Relation.Op.EQ
          && r.left() instanceof Symbol s
          && (TVM_PARTS.contains(s.name()) || s.name().equals("type")))) {
        throw new EvaluationException("`tvm`'s parts are n, i, pv, pmt, fv and type", arg);
      }
      if (parts.containsKey(s.name())) {
        throw new EvaluationException("`tvm` takes each part once", arg);
      }
      Expr value = Based.unwrap(state.eval(r.right()));
      if (!isRealNumber(value)) {
        throw new EvaluationException("`tvm` needs a number for " + s.name(), arg);
      }
      parts.put(s.name(), value);
    }
    List<String> missing = TVM_PARTS.stream().filter(p -> !parts.containsKey(p)).toList();
    if (missing.size() != 1) {
      throw new EvaluationException(
          "`tvm` needs four of n, i, pv, pmt and fv, as in `tvm(n = 10, i = 5 %, pv = -1000,"
              + " pmt = 0)`",
          a);
    }
    Expr type = parts.getOrDefault("type", Num.ZERO);
    String unknown = missing.getFirst();
    Expr n = parts.get("n");
    Expr i = parts.get("i");
    Expr pv = parts.get("pv");
    Expr pmt = parts.get("pmt");
    Expr fv = parts.get("fv");
    List<Expr> args =
        switch (unknown) {
          case "fv" -> List.of(i, n, pmt, pv, type);
          case "pv" -> List.of(i, n, pmt, fv, type);
          case "pmt" -> List.of(i, n, pv, fv, type);
          case "n" -> List.of(i, pmt, pv, fv, type);
          default -> List.of(n, pmt, pv, fv, type);
        };
    String function =
        switch (unknown) {
          case "n" -> "nper";
          case "i" -> "rate";
          default -> unknown;
        };
    Apply call = new Apply(Symbol.of(function), args);
    parts.put(unknown, finance(call, args));
    List<Expr> out = new ArrayList<>();
    for (String p : TVM_PARTS) {
      out.add(new Relation(Relation.Op.EQ, Symbol.of(p), parts.get(p)));
    }
    ListExpr value = new ListExpr(out);
    if (unknown.equals("i") && state.traced() && state.atTop(a)) {
      // the rate is numeric: its step, as rate's
      state.commandTrace(numericStep(a, value, "rate"));
    }
    return value;
  }

  private static boolean isRealNumber(Expr e) {
    return e instanceof Num n && n.value() instanceof RealNumeric;
  }

  /** {@code rate} or {@code irr}: numeric, marked, with its step. */
  private Expr rate(Apply a, String name, List<Expr> args, List<Expr> flows) {
    RealNumeric value;
    if (name.equals("irr")) {
      RealNumeric guess = args.size() > 1 ? Tvm.real(args.get(1)) : Real.of(new BigDecimal("0.1"));
      value =
          Newton.irr(
              flows.stream().map(Tvm::real).toList(), guess, state.mc(), state.cancellation());
    } else {
      RealNumeric type = args.size() > 4 ? Tvm.real(args.get(4)) : Int.ZERO;
      if (!(type.isZero() || type.isOne())) {
        throw new EvaluationException("`rate`'s type must be 0 (end) or 1 (beginning)", a);
      }
      RealNumeric guess = args.size() > 5 ? Tvm.real(args.get(5)) : Real.of(new BigDecimal("0.1"));
      value =
          Newton.rate(
              Tvm.real(args.get(0)),
              Tvm.real(args.get(1)),
              Tvm.real(args.get(2)),
              args.size() > 3 ? Tvm.real(args.get(3)) : Int.ZERO,
              type,
              guess,
              state.mc(),
              state.cancellation());
    }
    Expr result = Num.of(value);
    state.markNumeric();
    if (state.traced() && state.atTop(a)) {
      state.commandTrace(numericStep(state.held(a, args), result, name));
    }
    return result;
  }

  /** The one step of a numeric rate, {@code numeric.rate} or {@code numeric.irr}. */
  private Trace numericStep(Expr call, Expr result, String name) {
    Bindings digits =
        new Bindings(
            List.of(
                new Bindings.Binding("digits_", Num.of(state.mc().getPrecision()), null, null)));
    return new Trace(
        call,
        result,
        List.of(new Rewrite(call, result, Newton.RULE_SET, name, Path.ROOT, digits)),
        List.of());
  }
}
