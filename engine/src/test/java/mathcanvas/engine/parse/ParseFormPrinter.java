package mathcanvas.engine.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Lambda;
import mathcanvas.engine.expr.Limit;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Piecewise;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;

/**
 * The parse form of {@code docs/language.md} §1, for the corpus: a raw tree in ASCII command form,
 * in the order it holds its operands, with {@code *} for every multiplication, parentheses where
 * grouping matters, and the notation of postfix operators spelled out ({@code factorial(…)}, {@code
 * (…)/100}, {@code (…)*pi/180}). Independent of the production printers, so the corpus checks the
 * parser against the document rather than against them.
 */
final class ParseFormPrinter {

  private static final Num HALF = Num.of(Rational.of(1, 2));

  private ParseFormPrinter() {}

  /** The parse form of one statement, with {@code ;} after a quiet one. */
  static String print(Statement s) {
    String text =
        switch (s) {
          case Statement.Definition d ->
              d.name().name()
                  + (d.params().isEmpty()
                      ? ""
                      : "("
                          + d.params().stream().map(Symbol::name).collect(Collectors.joining(", "))
                          + ")")
                  + " := "
                  + print(d.body());
          case Statement.Expression e -> print(e.expr());
        };
    return s.quiet() ? text + ";" : text;
  }

  static String print(Expr e) {
    return switch (e) {
      case Num n -> number(n);
      case Constant c ->
          switch (c.kind()) {
            case PI -> "pi";
            case E -> "e";
            case INFINITY -> "inf";
            case UNDEFINED -> "undefined";
            case INTEGERS -> "Integers";
            case RATIONALS -> "Rationals";
            case REALS -> "Reals";
            case COMPLEXES -> "Complexes";
          };
      case Bool b -> Boolean.toString(b.value());
      case Symbol s -> s.name();
      case mathcanvas.engine.expr.Unit u -> "_" + u.name();
      case Sum s -> sum(s);
      case Product p -> isNegation(p) ? negation(p.factors().get(1)) : product(p);
      case Power p ->
          p.exponent().equals(HALF)
              ? "sqrt(" + print(p.base()) + ")"
              : (isAtom(p.base()) ? print(p.base()) : "(" + print(p.base()) + ")")
                  + "^"
                  + (isExponentAtom(p.exponent())
                      ? print(p.exponent())
                      : "(" + print(p.exponent()) + ")");
      case Apply a -> call(a.head().name(), a.args());
      case Lambda l ->
          (l.params().size() == 1
                  ? l.params().getFirst().name()
                  : "("
                      + l.params().stream().map(Symbol::name).collect(Collectors.joining(", "))
                      + ")")
              + " -> "
              + (l.body() instanceof Lambda ? "(" + print(l.body()) + ")" : print(l.body()));
      case Relation r -> side(r.left()) + " " + op(r.op()) + " " + side(r.right());
      case ListExpr l -> "[" + items(l.items()) + "]";
      case Vector v -> "[" + items(v.items()) + "]";
      case Matrix m ->
          "["
              + m.rows().stream()
                  .map(row -> "[" + items(row) + "]")
                  .collect(Collectors.joining(", "))
              + "]";
      case SetExpr s -> "{" + items(s.items()) + "}";
      case Interval i -> throw new IllegalArgumentException("no parse form: " + i);
      case Piecewise p -> throw new IllegalArgumentException("no parse form: " + p);
      case Derivative d ->
          d.order() == 1
              ? call("diff", List.of(d.expr(), d.var()))
              : call("diff", List.of(d.expr(), d.var(), Num.of(d.order())));
      case Integral i ->
          i.isDefinite()
              ? call("integrate", List.of(i.integrand(), i.var(), i.lower(), i.upper()))
              : call("integrate", List.of(i.integrand(), i.var()));
      case Limit l -> {
        List<Expr> args = new ArrayList<>(List.of(l.expr(), l.var(), l.point()));
        switch (l.direction()) {
          case LEFT -> args.add(Symbol.of("left"));
          case RIGHT -> args.add(Symbol.of("right"));
          case BOTH -> {}
        }
        yield call("limit", args);
      }
      case BigSum s -> call("sum", List.of(s.body(), s.var(), s.from(), s.to()));
      case BigProduct s -> call("product", List.of(s.body(), s.var(), s.from(), s.to()));
    };
  }

  private static String number(Num n) {
    if (n.value().equals(Complex.I)) {
      return "i";
    }
    if (n.value() instanceof Int || n.value() instanceof Real) {
      return n.value().toString();
    }
    throw new IllegalArgumentException("not a typed number: " + n);
  }

  private static boolean isNegation(Expr e) {
    return e instanceof Product p
        && p.factors().size() == 2
        && p.factors().getFirst().equals(Num.MINUS_ONE);
  }

  private static boolean isAtom(Expr e) {
    return switch (e) {
      case Num n -> !(n.value() instanceof Int k && k.signum() < 0);
      case Constant c -> true;
      case Bool b -> true;
      case Symbol s -> true;
      case mathcanvas.engine.expr.Unit u -> true;
      case Apply a -> true;
      case ListExpr l -> true;
      case Vector v -> true;
      case Matrix m -> true;
      case SetExpr s -> true;
      case Derivative d -> true;
      case Integral i -> true;
      case Limit l -> true;
      case BigSum s -> true;
      case BigProduct s -> true;
      case Power p -> p.exponent().equals(HALF);
      default -> false;
    };
  }

  private static boolean isExponentAtom(Expr e) {
    return switch (e) {
      case Num n -> n.value() instanceof Int k && k.signum() >= 0 || n.value().equals(Complex.I);
      case Constant c -> true;
      case Symbol s -> true;
      case mathcanvas.engine.expr.Unit u -> true;
      case Apply a -> true;
      case Derivative d -> true;
      case Integral i -> true;
      case Limit l -> true;
      case BigSum s -> true;
      case BigProduct s -> true;
      case Power p -> p.exponent().equals(HALF);
      default -> false;
    };
  }

  /** A sum's term or a relation's side: parenthesised when it is a sum, relation or lambda. */
  private static boolean isLoose(Expr e) {
    return e instanceof Sum || e instanceof Relation || e instanceof Lambda;
  }

  private static String sum(Sum s) {
    StringBuilder text = new StringBuilder(term(s.terms().getFirst()));
    for (Expr t : s.terms().subList(1, s.terms().size())) {
      if (isNegation(t)) {
        Expr operand = ((Product) t).factors().get(1);
        text.append(" - ")
            .append(
                isLoose(operand) || isNegation(operand)
                    ? "(" + print(operand) + ")"
                    : print(operand));
      } else {
        text.append(" + ").append(term(t));
      }
    }
    return text.toString();
  }

  private static String term(Expr t) {
    return isLoose(t) ? "(" + print(t) + ")" : print(t);
  }

  private static String negation(Expr operand) {
    boolean bare =
        operand instanceof Symbol
            || operand instanceof Constant
            || (operand instanceof Num n && !(n.value() instanceof Int k && k.signum() < 0));
    return "-" + (bare ? print(operand) : "(" + print(operand) + ")");
  }

  private static String factor(Expr f) {
    return isLoose(f) || f instanceof Product ? "(" + print(f) + ")" : print(f);
  }

  private static String product(Product p) {
    String acc = factor(p.factors().getFirst());
    boolean quotient = false;
    for (Expr f : p.factors().subList(1, p.factors().size())) {
      if (quotient) {
        acc = "(" + acc + ")";
        quotient = false;
      }
      if (f instanceof Power power && power.exponent().equals(Num.MINUS_ONE)) {
        acc += "/" + factor(power.base());
        quotient = true;
      } else {
        acc += "*" + factor(f);
      }
    }
    return acc;
  }

  private static String side(Expr e) {
    return e instanceof Relation || e instanceof Lambda ? "(" + print(e) + ")" : print(e);
  }

  private static String op(Relation.Op op) {
    return switch (op) {
      case EQ -> "=";
      case NE -> "!=";
      case LT -> "<";
      case LE -> "<=";
      case GT -> ">";
      case GE -> ">=";
      case IN -> "in";
    };
  }

  private static String call(String head, List<Expr> args) {
    return head + "(" + items(args) + ")";
  }

  private static String items(List<Expr> items) {
    StringBuilder text = new StringBuilder();
    for (Expr item : items) {
      text.append(text.isEmpty() ? "" : ", ").append(print(item));
    }
    return text.toString();
  }
}
