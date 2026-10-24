package mathcanvas.engine.print;

import static mathcanvas.engine.print.PrintForm.ATOM;
import static mathcanvas.engine.print.PrintForm.INTEGRAL;
import static mathcanvas.engine.print.PrintForm.LAMBDA;
import static mathcanvas.engine.print.PrintForm.POSTFIX;
import static mathcanvas.engine.print.PrintForm.POWER;
import static mathcanvas.engine.print.PrintForm.PREFIX;
import static mathcanvas.engine.print.PrintForm.PRODUCT;
import static mathcanvas.engine.print.PrintForm.RELATION;
import static mathcanvas.engine.print.PrintForm.SUM;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
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

/**
 * The ASCII and Unicode printers (§15.1–§15.3): one precedence-driven printer with a style flag.
 * Each subtree prints to a {@link Piece} that carries its precedence and the facts its parent needs
 * to decide parentheses and juxtaposition; a parent parenthesises a child exactly where the child
 * would otherwise read back differently.
 */
final class InfixPrinter implements Printer {

  static final InfixPrinter ASCII = new InfixPrinter(false);
  static final InfixPrinter UNICODE = new InfixPrinter(true);

  /** A text that begins with a call, {@code sin(x)}: a number before it takes a space. */
  private static final Pattern CALL = Pattern.compile("[\\p{L}\\p{N}_]+\\(");

  private static final Pattern D_END = Pattern.compile("d(\\^[0-9]+|[⁰¹²³⁴⁵⁶⁷⁸⁹]+)?$");

  private final boolean unicode;

  private InfixPrinter(boolean unicode) {
    this.unicode = unicode;
  }

  /**
   * A printed subtree.
   *
   * @param text the text
   * @param prec its precedence level (PrintForm)
   * @param minus whether the text begins with {@code -}
   * @param open whether it ends in a big operator that would take in a following factor
   * @param literal whether it is a number literal, possibly negative (for juxtaposition)
   * @param bare whether it is a non-negative number literal, a name or a constant (a radicand)
   * @param exponentSafe whether it may be a {@code ^} exponent unparenthesised
   * @param product whether it is a product or quotient, which a product factor parenthesises
   */
  private record Piece(
      String text,
      int prec,
      boolean minus,
      boolean open,
      boolean literal,
      boolean bare,
      boolean exponentSafe,
      boolean product) {

    static Piece atom(String text, boolean bare, boolean exponentSafe) {
      return new Piece(text, ATOM, false, false, false, bare, exponentSafe, false);
    }

    static Piece of(String text, int prec) {
      return new Piece(text, prec, false, false, false, false, false, false);
    }

    Piece wrapped() {
      return atom("(" + text + ")", false, false);
    }
  }

  @Override
  public String print(Expr e) {
    return piece(e).text();
  }

  private Piece piece(Expr e) {
    return switch (e) {
      case Num n -> number(n);
      case Constant c -> Piece.atom(constant(c), true, true);
      case Bool b -> Piece.atom(b.value() ? "true" : "false", true, true);
      case Symbol s -> Piece.atom(name(s), true, true);
      case Sum s -> sum(s);
      case Product p -> PrintForm.isNegation(p) ? negation(p.factors().get(1)) : product(p);
      case Power p -> power(p);
      case Apply a -> apply(a);
      case Lambda l -> lambda(l);
      case Relation r -> relation(r);
      case ListExpr l -> Piece.atom("[" + items(l.items()) + "]", false, false);
      case Vector v ->
          Piece.atom(
              (unicode ? "⟨" : "[") + items(v.items()) + (unicode ? "⟩" : "]"), false, false);
      case Matrix m -> {
        StringBuilder text = new StringBuilder("[");
        for (List<Expr> row : m.rows()) {
          text.append(text.length() > 1 ? ", [" : "[").append(items(row)).append(']');
        }
        yield Piece.atom(text.append(']').toString(), false, false);
      }
      case SetExpr s -> Piece.atom("{" + items(s.items()) + "}", false, false);
      case Interval i ->
          Piece.atom(
              (i.lowerClosed() ? "[" : "(")
                  + print(i.lower())
                  + ", "
                  + print(i.upper())
                  + (i.upperClosed() ? "]" : ")"),
              false,
              false);
      case Piecewise p -> {
        List<Expr> args = new ArrayList<>();
        for (Piecewise.Piece q : p.pieces()) {
          args.add(q.value());
          args.add(q.condition());
        }
        args.add(p.otherwise());
        yield call("piecewise", args);
      }
      case Derivative d -> derivative(d);
      case Integral i -> integral(i);
      case Limit l -> limit(l);
      case BigSum s -> big("∑", "sum", s.body(), s.var(), s.from(), s.to());
      case BigProduct s -> big("∏", "product", s.body(), s.var(), s.from(), s.to());
    };
  }

  // Leaves.

  private Piece number(Num n) {
    if (n.value().equals(Complex.I)) {
      return Piece.atom("i", true, true);
    }
    if (PrintForm.isLiteral(n.value())) {
      return new Piece(
          PrintForm.literal(n.value()),
          ATOM,
          false,
          false,
          true,
          true,
          n.value() instanceof Int,
          false);
    }
    return piece(PrintForm.typed(n.value()));
  }

  private String constant(Constant c) {
    return switch (c.kind()) {
      case PI -> unicode ? "π" : "pi";
      case E -> "e";
      case INFINITY -> unicode ? "∞" : "inf";
      case UNDEFINED -> "undefined";
      case INTEGERS -> unicode ? "ℤ" : "Integers";
      case RATIONALS -> unicode ? "ℚ" : "Rationals";
      case REALS -> unicode ? "ℝ" : "Reals";
      case COMPLEXES -> unicode ? "ℂ" : "Complexes";
    };
  }

  private String name(Symbol s) {
    return unicode ? PrintForm.subscripted(s.name()) : s.name();
  }

  // Arithmetic.

  private Piece sum(Sum s) {
    StringBuilder text = new StringBuilder();
    Piece first = term(piece(s.terms().getFirst()));
    text.append(first.text());
    Piece last = first;
    for (Expr t : s.terms().subList(1, s.terms().size())) {
      if (PrintForm.isNegation(t)) {
        last = piece(((Product) t).factors().get(1));
        if (last.prec() < PRODUCT || last.minus()) {
          last = last.wrapped();
        }
        text.append(" - ");
      } else {
        last = term(piece(t));
        text.append(" + ");
      }
      text.append(last.text());
    }
    return new Piece(text.toString(), SUM, first.minus(), last.open(), false, false, false, false);
  }

  private static Piece term(Piece p) {
    return p.prec() < PRODUCT ? p.wrapped() : p;
  }

  private Piece negation(Expr operand) {
    Piece p = piece(operand);
    if (p.prec() < PREFIX || p.minus()) {
      p = p.wrapped();
    }
    return new Piece("-" + p.text(), PREFIX, true, p.open(), p.literal(), false, false, false);
  }

  private Piece product(Product product) {
    List<Expr> factors = product.factors();
    List<Piece> parts = new ArrayList<>();
    List<Boolean> divided = new ArrayList<>();
    for (int k = 0; k < factors.size(); k++) {
      Expr f = factors.get(k);
      boolean divide = k > 0 && PrintForm.isReciprocal(f);
      Piece p = piece(divide ? ((Power) f).base() : f);
      boolean wrap = k == 0 ? p.prec() < PRODUCT || p.product() : p.prec() < PREFIX || p.minus();
      parts.add(wrap ? p.wrapped() : p);
      divided.add(divide);
    }
    for (int k = 0; k < parts.size() - 1; k++) {
      if (parts.get(k).open()) {
        parts.set(k, parts.get(k).wrapped());
      }
    }
    StringBuilder text = new StringBuilder(parts.getFirst().text());
    for (int k = 1; k < parts.size(); k++) {
      text.append(
          !divided.get(k)
              ? separator(parts.get(k - 1), parts.get(k))
              : derivativeLike(text, parts.get(k).text()) ? " / " : "/");
      text.append(parts.get(k).text());
    }
    return new Piece(
        text.toString(),
        PRODUCT,
        parts.getFirst().minus(),
        parts.getLast().open(),
        false,
        false,
        false,
        true);
  }

  /**
   * Whether {@code left/right} could read as the derivative notation: {@code left} ends in {@code
   * d}, {@code d^n} or {@code dⁿ} and {@code right} begins with {@code d}. Such a quotient is
   * written with spaces, {@code d / dx}.
   */
  private static boolean derivativeLike(CharSequence left, String right) {
    return right.startsWith("d") && D_END.matcher(left).find();
  }

  /** What goes between two factors (§15.3): {@code *} in ASCII; {@code ·}, nothing or a space. */
  private String separator(Piece left, Piece right) {
    if (!unicode) {
      return "*";
    }
    String l = left.text();
    int last = l.codePointBefore(l.length());
    int first = right.text().codePointAt(0);
    if (Character.isDigit(first) || first == '.' || first == '[') {
      return "·";
    }
    boolean name =
        !left.literal()
            && (Character.isLetterOrDigit(last)
                || last == '_'
                || last == 'π'
                || last == '∞'
                || (last >= 0x2080 && last <= 0x209C)
                || (last >= 0x1D62 && last <= 0x1D65)
                || last == 0x2C7C);
    if (first == '(') {
      return left.literal() || last == ')' ? "" : name ? "·" : " ";
    }
    if (left.literal()
        && ((Character.isLetter(first)
                && first != 'e'
                && first != 'E'
                && !CALL.matcher(right.text()).lookingAt())
            || first == 'π'
            || first == '√'
            || first == '∛'
            || first == '∜'
            || first == '|'
            || first == '⟨')) {
      return "";
    }
    if ((last == 'π' || last == '∞') && Character.isLetter(first)) {
      return "";
    }
    return " ";
  }

  private Piece power(Power p) {
    if (PrintForm.isSqrt(p)) {
      return unicode ? radical("√", p.base()) : call("sqrt", List.of(p.base()));
    }
    Piece base = piece(p.base());
    if (base.prec() < ATOM) {
      base = base.wrapped();
    }
    if (unicode && p.exponent() instanceof Num n && n.value() instanceof Int k && k.signum() >= 0) {
      return Piece.of(base.text() + PrintForm.superscript(k.toString()), POWER);
    }
    Piece exponent = piece(p.exponent());
    if (!exponent.exponentSafe()) {
      exponent = exponent.wrapped();
    }
    return Piece.of(base.text() + "^" + exponent.text(), POWER);
  }

  private Piece radical(String sign, Expr operand) {
    Piece p = piece(operand);
    if (!p.bare()) {
      p = p.wrapped();
    }
    return Piece.of(sign + p.text(), PREFIX);
  }

  // Applications.

  private Piece apply(Apply a) {
    String head = a.head().name();
    List<Expr> args = a.args();
    if (unicode) {
      if (head.equals("abs") && args.size() == 1) {
        Piece arg = piece(args.getFirst());
        Piece inner = arg.prec() < SUM ? arg.wrapped() : arg;
        return barOutsideBrackets(inner.text())
            ? Piece.atom(head + "(" + arg.text() + ")", false, true)
            : Piece.atom("|" + inner.text() + "|", false, false);
      } else if (head.equals("factorial") && args.size() == 1) {
        Piece p = piece(args.getFirst());
        if (p.prec() < POSTFIX) {
          p = p.wrapped();
        }
        return Piece.of(p.text() + "!", POSTFIX);
      } else if (head.equals("cbrt") && args.size() == 1) {
        return radical("∛", args.getFirst());
      } else if (head.equals("root") && args.size() == 2 && args.get(1).equals(Num.of(4))) {
        return radical("∜", args.getFirst());
      } else if (head.equals("cross") && args.size() == 2) {
        return cross(args.getFirst(), args.get(1));
      }
    }
    String name = unicode ? PrintForm.subscripted(head) : head;
    if (head.equals("limit")
        && args.size() == 4
        && args.get(1) instanceof Symbol
        && args.get(3) instanceof Symbol side
        && (side.name().equals("left") || side.name().equals("right"))) {
      // A bare left or right there would read back as the side of a Limit, not as a symbol.
      return Piece.atom(
          name + "(" + items(args.subList(0, 3)) + ", (" + side.name() + "))", false, true);
    }
    return call(name, args);
  }

  private Piece call(String head, List<Expr> args) {
    return Piece.atom(head + "(" + items(args) + ")", false, true);
  }

  private Piece cross(Expr u, Expr v) {
    Piece left = piece(u);
    if (left.prec() < PRODUCT || left.open()) {
      left = left.wrapped();
    }
    Piece right = piece(v);
    if (right.prec() < PREFIX || right.minus()) {
      right = right.wrapped();
    }
    return new Piece(
        left.text() + " × " + right.text(),
        PRODUCT,
        left.minus(),
        right.open(),
        false,
        false,
        false,
        false);
  }

  private static boolean barOutsideBrackets(String text) {
    int depth = 0;
    for (int k = 0; k < text.length(); k++) {
      char c = text.charAt(k);
      if (c == '(' || c == '[' || c == '{' || c == '⟨') {
        depth++;
      } else if (c == ')' || c == ']' || c == '}' || c == '⟩') {
        depth--;
      } else if (c == '|' && depth == 0) {
        return true;
      }
    }
    return false;
  }

  private Piece lambda(Lambda l) {
    String params =
        l.params().size() == 1
            ? name(l.params().getFirst())
            : "(" + l.params().stream().map(this::name).collect(Collectors.joining(", ")) + ")";
    return Piece.of(params + (unicode ? " → " : " -> ") + piece(l.body()).text(), LAMBDA);
  }

  private Piece relation(Relation r) {
    String op =
        switch (r.op()) {
          case EQ -> "=";
          case NE -> unicode ? "≠" : "!=";
          case LT -> "<";
          case LE -> unicode ? "≤" : "<=";
          case GT -> ">";
          case GE -> unicode ? "≥" : ">=";
          case IN -> unicode ? "∈" : "in";
        };
    return Piece.of(operand(r.left(), SUM) + " " + op + " " + operand(r.right(), SUM), RELATION);
  }

  /** A child printed in a position that needs at least {@code level}. */
  private String operand(Expr e, int level) {
    Piece p = piece(e);
    return (p.prec() < level ? p.wrapped() : p).text();
  }

  private String items(List<Expr> items) {
    StringBuilder text = new StringBuilder();
    for (Expr item : items) {
      if (!text.isEmpty()) {
        text.append(", ");
      }
      text.append(print(item));
    }
    return text.toString();
  }

  // Notation.

  private Piece derivative(Derivative d) {
    List<Expr> args = new ArrayList<>(List.of(d.expr(), d.var()));
    if (d.order() != 1) {
      args.add(Num.of(d.order()));
    }
    if (!unicode) {
      return call("diff", args);
    }
    String var = name(d.var());
    String order = PrintForm.superscript(Integer.toString(d.order()));
    String head = d.order() == 1 ? "d/d" + var : "d" + order + "/d" + var + order;
    return body(head, d.expr());
  }

  private Piece integral(Integral i) {
    List<Expr> args = new ArrayList<>(List.of(i.integrand(), i.var()));
    if (i.isDefinite()) {
      args.add(i.lower());
      args.add(i.upper());
    }
    if (!unicode || PrintForm.integralNeedsCommandForm(i)) {
      return call("integrate", args);
    }
    String bounds =
        i.isDefinite() ? "[" + operand(i.lower(), SUM) + ", " + operand(i.upper(), SUM) + "]" : "";
    // A [ right after ∫ is the bounds, so an integrand that begins with one is parenthesised.
    Piece integrand = piece(i.integrand());
    if (integrand.prec() < SUM || integrand.text().startsWith("[")) {
      integrand = integrand.wrapped();
    }
    return Piece.of("∫" + bounds + " " + integrand.text() + " d" + name(i.var()), INTEGRAL);
  }

  private Piece limit(Limit l) {
    String side =
        switch (l.direction()) {
          case BOTH -> "";
          case LEFT -> "-";
          case RIGHT -> "+";
        };
    if (!unicode) {
      List<Expr> args = new ArrayList<>(List.of(l.expr(), l.var(), l.point()));
      if (!side.isEmpty()) {
        args.add(Symbol.of(side.equals("-") ? "left" : "right"));
      }
      return call("limit", args);
    }
    return body("lim[" + name(l.var()) + " → " + operand(l.point(), SUM) + side + "]", l.expr());
  }

  private Piece big(String sign, String command, Expr body, Symbol var, Expr from, Expr to) {
    if (!unicode) {
      return call(command, List.of(body, var, from, to));
    }
    return body(
        sign + "[" + name(var) + " = " + operand(from, SUM) + ", " + operand(to, SUM) + "]", body);
  }

  /** A big operator: its head, then its body, which runs to the end of the term. */
  private Piece body(String head, Expr body) {
    Piece p = piece(body);
    if (p.prec() < PRODUCT) {
      p = p.wrapped();
    }
    return new Piece(head + " " + p.text(), PREFIX, false, true, false, false, false, false);
  }
}
