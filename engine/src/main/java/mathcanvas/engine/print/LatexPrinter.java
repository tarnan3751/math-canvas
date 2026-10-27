package mathcanvas.engine.print;

import static mathcanvas.engine.print.PrintForm.ATOM;
import static mathcanvas.engine.print.PrintForm.LAMBDA;
import static mathcanvas.engine.print.PrintForm.POSTFIX;
import static mathcanvas.engine.print.PrintForm.POWER;
import static mathcanvas.engine.print.PrintForm.PREFIX;
import static mathcanvas.engine.print.PrintForm.PRODUCT;
import static mathcanvas.engine.print.PrintForm.RELATION;
import static mathcanvas.engine.print.PrintForm.SUM;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/**
 * The LaTeX printer ({@code docs/language.md} §15.1), for export only: its output need not parse.
 * Quotients are {@code \frac}, roots {@code \sqrt}, notation its standard form; function names with
 * a LaTeX operator use it ({@code \sin}), others are {@code \operatorname}; a Greek letter is its
 * command and a longer name is {@code \mathit}.
 */
public final class LatexPrinter implements Printer {

  /** The printer. */
  public static final LatexPrinter INSTANCE = new LatexPrinter(Format.EXACT);

  private static final Set<String> OPERATORS =
      Set.of(
          "sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos", "arctan", "sinh", "cosh",
          "tanh", "coth", "ln", "log", "exp", "gcd", "min", "max", "det", "arg");

  private static final Map<String, String> ARC =
      Map.of("asin", "arcsin", "acos", "arccos", "atan", "arctan");

  private static final Map<String, String> GREEK = greek();

  private static Map<String, String> greek() {
    String names =
        "αalpha βbeta γgamma δdelta εepsilon ζzeta ηeta θtheta ιiota κkappa λlambda μmu νnu ξxi"
            + " ρrho σsigma τtau υupsilon φphi χchi ψpsi ωomega ΓGamma ΔDelta ΘTheta ΛLambda ΞXi"
            + " ΣSigma ΥUpsilon ΦPhi ΨPsi ΩOmega";
    Map<String, String> map = new HashMap<>();
    for (String entry : names.split(" ")) {
      map.put(entry.substring(0, 1), entry.substring(1));
    }
    return Map.copyOf(map);
  }

  private final Format format;

  private LatexPrinter(Format format) {
    this.format = format;
  }

  @Override
  public Format format() {
    return format;
  }

  @Override
  public LatexPrinter withFormat(Format f) {
    return new LatexPrinter(f);
  }

  private record Piece(String text, int prec, boolean minus, boolean product) {
    Piece wrapped() {
      return new Piece("(" + text + ")", ATOM, false, false);
    }
  }

  @Override
  public String print(Expr e) {
    return piece(e).text();
  }

  private static Piece atom(String text) {
    return new Piece(text, ATOM, false, false);
  }

  private Piece piece(Expr e) {
    return switch (e) {
      case Num n -> number(n);
      case Constant c ->
          atom(
              switch (c.kind()) {
                case PI -> "\\pi";
                case E -> "e";
                case INFINITY -> "\\infty";
                case UNDEFINED -> "\\text{undefined}";
                case INTEGERS -> "\\mathbb{Z}";
                case RATIONALS -> "\\mathbb{Q}";
                case REALS -> "\\mathbb{R}";
                case COMPLEXES -> "\\mathbb{C}";
              });
      case Bool b -> atom(b.value() ? "\\text{true}" : "\\text{false}");
      case Symbol s -> atom(name(s.name()));
      case Sum s -> sum(s);
      case Product p ->
          PrintForm.isNegation(p) ? negation(p.factors().get(1)) : product(p.factors());
      case Power p -> power(p);
      case Apply a -> apply(a);
      case Lambda l ->
          new Piece(
              (l.params().size() == 1
                      ? name(l.params().getFirst().name())
                      : "("
                          + l.params().stream()
                              .map(s -> name(s.name()))
                              .collect(Collectors.joining(", "))
                          + ")")
                  + " \\mapsto "
                  + print(l.body()),
              LAMBDA,
              false,
              false);
      case Relation r -> relation(r);
      case ListExpr l -> atom("[" + items(l.items()) + "]");
      case Vector v -> atom("\\langle " + items(v.items()) + "\\rangle");
      case SetExpr s -> atom("\\{" + items(s.items()) + "\\}");
      case Matrix m -> {
        List<String> rows = new ArrayList<>();
        for (List<Expr> row : m.rows()) {
          rows.add(join(row, " & "));
        }
        yield atom("\\begin{pmatrix}" + String.join("\\\\ ", rows) + "\\end{pmatrix}");
      }
      case Interval i ->
          atom(
              (i.lowerClosed() ? "[" : "(")
                  + print(i.lower())
                  + ", "
                  + print(i.upper())
                  + (i.upperClosed() ? "]" : ")"));
      case Piecewise p -> {
        List<String> rows = new ArrayList<>();
        for (Piecewise.Piece q : p.pieces()) {
          rows.add(print(q.value()) + " & " + print(q.condition()));
        }
        rows.add(print(p.otherwise()) + " & \\text{otherwise}");
        yield atom("\\begin{cases}" + String.join("\\\\ ", rows) + "\\end{cases}");
      }
      case Derivative d -> {
        String n = d.order() == 1 ? "" : "^{" + d.order() + "}";
        yield body("\\frac{d" + n + "}{d" + name(d.var().name()) + n + "}", d.expr(), " ");
      }
      case Integral i ->
          new Piece(
              "\\int"
                  + (i.isDefinite() ? "_{" + print(i.lower()) + "}^{" + print(i.upper()) + "}" : "")
                  + " "
                  + operand(i.integrand(), SUM)
                  + "\\,d"
                  + name(i.var().name()),
              PREFIX,
              false,
              false);
      case Limit l -> {
        String side =
            switch (l.direction()) {
              case BOTH -> "";
              case LEFT -> "^{-}";
              case RIGHT -> "^{+}";
            };
        yield body(
            "\\lim_{" + name(l.var().name()) + "\\to " + print(l.point()) + side + "}",
            l.expr(),
            " ");
      }
      case BigSum s -> big("\\sum", s.var(), s.from(), s.to(), s.body());
      case BigProduct s -> big("\\prod", s.var(), s.from(), s.to(), s.body());
    };
  }

  private Piece number(Num n) {
    if (n.value().equals(Complex.I)) {
      return atom("i");
    }
    switch (NumberFormat.leaf(n.value(), format)) {
      case NumberFormat.Literal l -> {
        Piece literal = literal(l.text());
        return l.negative() ? negation(literal) : literal;
      }
      case NumberFormat.Mixed m -> {
        String text = m.whole() + "\\frac{" + m.num() + "}{" + m.den() + "}";
        return new Piece(m.negative() ? "-" + text : text, PRODUCT, m.negative(), true);
      }
      case NumberFormat.Shape shape -> {
        return piece(shape.shape());
      }
      case NumberFormat.Unchanged u -> {}
    }
    if (PrintForm.isLiteral(n.value())) {
      return literal(PrintForm.literal(n.value()));
    }
    return piece(PrintForm.typed(n.value()));
  }

  /** A non-negative literal; a scientific one is written with a power of ten. */
  private static Piece literal(String text) {
    int exponent = text.indexOf('e');
    if (exponent < 0) {
      return atom(text);
    }
    String power = text.substring(exponent + 1);
    return new Piece(
        text.substring(0, exponent)
            + "\\times 10^{"
            + (power.startsWith("+") ? power.substring(1) : power)
            + "}",
        PRODUCT,
        false,
        true);
  }

  private static String name(String name) {
    String base = name;
    String sub = null;
    int cut = name.indexOf('_');
    if (cut > 0 && cut < name.length() - 1) {
      base = name.substring(0, cut);
      sub = name.substring(cut + 1);
    }
    String shown =
        GREEK.containsKey(base)
            ? "\\" + GREEK.get(base)
            : base.codePointCount(0, base.length()) == 1
                ? base
                : "\\mathit{" + base.replace("_", "\\_") + "}";
    return sub == null ? shown : shown + "_{" + sub.replace("_", "\\_") + "}";
  }

  private Piece sum(Sum s) {
    StringBuilder text = new StringBuilder();
    Piece first = operandPiece(s.terms().getFirst(), PRODUCT);
    text.append(first.text());
    for (Expr t : s.terms().subList(1, s.terms().size())) {
      if (PrintForm.isNegation(t)) {
        Piece p = piece(((Product) t).factors().get(1));
        text.append('-').append((p.prec() < PRODUCT || p.minus() ? p.wrapped() : p).text());
      } else {
        text.append('+').append(operand(t, PRODUCT));
      }
    }
    return new Piece(text.toString(), SUM, first.minus(), false);
  }

  private Piece negation(Expr operand) {
    return negation(piece(operand));
  }

  private static Piece negation(Piece operand) {
    Piece p = operand;
    if (p.prec() < PREFIX || p.minus()) {
      p = p.wrapped();
    }
    return new Piece("-" + p.text(), PREFIX, true, false);
  }

  private Piece product(List<Expr> factors) {
    List<String> parts = new ArrayList<>();
    for (int k = 0; k < factors.size(); k++) {
      Expr f = factors.get(k);
      if (k > 0 && PrintForm.isReciprocal(f)) {
        String top = join(parts);
        parts.clear();
        parts.add("\\frac{" + top + "}{" + print(((Power) f).base()) + "}");
        continue;
      }
      Piece p = piece(f);
      boolean wrap = k == 0 ? p.prec() < PRODUCT || p.product() : p.prec() < PREFIX || p.minus();
      parts.add((wrap ? p.wrapped() : p).text());
    }
    return new Piece(join(parts), PRODUCT, parts.getFirst().startsWith("-"), true);
  }

  private static String join(List<String> parts) {
    StringBuilder text = new StringBuilder(parts.getFirst());
    for (int k = 1; k < parts.size(); k++) {
      String left = parts.get(k - 1);
      String right = parts.get(k);
      int last = left.codePointBefore(left.length());
      int first = right.codePointAt(0);
      if (Character.isDigit(first)) {
        text.append("\\cdot ");
      } else if (Character.isLetter(last) && Character.isLetter(first)) {
        text.append(' ');
      }
      text.append(right);
    }
    return text.toString();
  }

  private Piece power(Power p) {
    if (PrintForm.isSqrt(p)) {
      return atom("\\sqrt{" + print(p.base()) + "}");
    }
    Piece base = piece(p.base());
    if (base.prec() < ATOM) {
      base = base.wrapped();
    }
    Printer exponents = format.forExponents() == format ? this : withFormat(format.forExponents());
    return new Piece(base.text() + "^{" + exponents.print(p.exponent()) + "}", POWER, false, false);
  }

  private Piece apply(Apply a) {
    String head = a.head().name();
    List<Expr> args = a.args();
    if (args.size() == 1) {
      switch (head) {
        case "abs" -> {
          return atom("\\lvert " + print(args.getFirst()) + "\\rvert");
        }
        case "factorial" -> {
          return new Piece(operand(args.getFirst(), POSTFIX) + "!", POSTFIX, false, false);
        }
        case "cbrt" -> {
          return atom("\\sqrt[3]{" + print(args.getFirst()) + "}");
        }
        case "sqrt" -> {
          return atom("\\sqrt{" + print(args.getFirst()) + "}");
        }
        default -> {}
      }
    }
    if (args.size() == 2 && head.equals("root")) {
      return atom("\\sqrt[" + print(args.get(1)) + "]{" + print(args.getFirst()) + "}");
    }
    if (args.size() == 2 && head.equals("cross")) {
      Piece left = operandPiece(args.getFirst(), PRODUCT);
      Piece right = operandPiece(args.get(1), PREFIX);
      return new Piece(left.text() + " \\times " + right.text(), PRODUCT, left.minus(), false);
    }
    String latex = ARC.getOrDefault(head, head);
    String shown =
        OPERATORS.contains(latex)
            ? "\\" + latex
            : head.codePointCount(0, head.length()) == 1
                ? name(head)
                : "\\operatorname{" + head.replace("_", "\\_") + "}";
    return atom(shown + "(" + items(args) + ")");
  }

  private Piece relation(Relation r) {
    String op =
        switch (r.op()) {
          case EQ -> " = ";
          case NE -> " \\ne ";
          case LT -> " < ";
          case LE -> " \\le ";
          case GT -> " > ";
          case GE -> " \\ge ";
          case IN -> " \\in ";
        };
    return new Piece(operand(r.left(), SUM) + op + operand(r.right(), SUM), RELATION, false, false);
  }

  private Piece big(String sign, Symbol var, Expr from, Expr to, Expr body) {
    return body(
        sign + "_{" + name(var.name()) + "=" + print(from) + "}^{" + print(to) + "}", body, "");
  }

  private Piece body(String head, Expr body, String gap) {
    return new Piece(head + gap + operand(body, PRODUCT), PREFIX, false, false);
  }

  private Piece operandPiece(Expr e, int level) {
    Piece p = piece(e);
    return p.prec() < level || (level > PRODUCT && p.minus()) ? p.wrapped() : p;
  }

  private String operand(Expr e, int level) {
    return operandPiece(e, level).text();
  }

  private String items(List<Expr> items) {
    return join(items, ", ");
  }

  private String join(List<Expr> items, String separator) {
    StringBuilder text = new StringBuilder();
    for (Expr item : items) {
      if (!text.isEmpty()) {
        text.append(separator);
      }
      text.append(print(item));
    }
    return text.toString();
  }
}
