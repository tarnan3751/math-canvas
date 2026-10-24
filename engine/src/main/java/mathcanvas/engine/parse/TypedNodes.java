package mathcanvas.engine.parse;

import java.util.List;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Limit;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;

/**
 * The typed nodes of the notation commands ({@code docs/language.md} §10, §12.2): {@code diff},
 * {@code integrate}, {@code limit}, {@code sum} and {@code product} build a {@link Derivative},
 * {@link Integral}, {@link Limit}, {@link BigSum} or {@link BigProduct} when their arguments fit
 * the node's fields (a symbol variable, a positive integer order), and {@code sqrt(x)} is the power
 * {@code x^(1/2)}. The parser builds every call through here, and so does the evaluator once a
 * command's arguments are evaluated, so a call whose arguments come to fit is the same node as one
 * typed that way.
 */
public final class TypedNodes {

  private TypedNodes() {}

  /**
   * The node of an application: the typed node when its arguments fit. A four-argument {@code
   * limit} stays an application, since its side is the parser's literal word.
   *
   * @param a the application
   * @return the typed node or {@code a}
   */
  public static Expr of(Apply a) {
    Expr e = of(a.head().name(), a.args(), null);
    return e instanceof Apply ? a : e;
  }

  /**
   * The node of a call as the parser builds it: the typed node of a notation command when its
   * arguments fit, otherwise the application.
   *
   * @param head the builtin's name
   * @param args the arguments
   * @param direction the side of a {@code limit}, read from its literal word, or null
   * @return the typed node or the application
   */
  public static Expr of(String head, List<Expr> args, Limit.Direction direction) {
    Symbol var = args.size() > 1 && args.get(1) instanceof Symbol s ? s : null;
    switch (head) {
      case "sqrt" -> {
        return Exprs.sqrt(args.getFirst());
      }
      case "diff" -> {
        if (var != null && args.size() == 2) {
          return Derivative.of(args.getFirst(), var, 1);
        }
        if (var != null
            && args.get(2) instanceof Num n
            && n.value() instanceof Int k
            && k.signum() > 0
            && k.value().bitLength() < 32) {
          return Derivative.of(args.getFirst(), var, k.value().intValue());
        }
      }
      case "integrate" -> {
        if (var != null && args.size() == 2) {
          return Integral.indefinite(args.getFirst(), var);
        }
        if (var != null) {
          return Integral.definite(args.getFirst(), var, args.get(2), args.get(3));
        }
      }
      case "limit" -> {
        if (var != null && (args.size() == 3 || direction != null)) {
          return Limit.of(
              args.getFirst(),
              var,
              args.get(2),
              direction == null ? Limit.Direction.BOTH : direction);
        }
      }
      case "sum" -> {
        if (var != null) {
          return BigSum.of(args.getFirst(), var, args.get(2), args.get(3));
        }
      }
      case "product" -> {
        if (var != null) {
          return BigProduct.of(args.getFirst(), var, args.get(2), args.get(3));
        }
      }
      default -> {}
    }
    return Apply.of(Symbol.of(head), args);
  }
}
