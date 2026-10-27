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
import mathcanvas.engine.print.Sink.Role;

/**
 * The ASCII and Unicode printers (§15.1–§15.3) and the walk behind the layout printer: one
 * precedence-driven printer with a style flag. Each subtree prints to a {@link Piece} that carries
 * its precedence and the facts its parent needs to decide parentheses and juxtaposition; a parent
 * parenthesises a child exactly where the child would otherwise read back differently. Beside the
 * text, each piece holds what a {@link Sink} built for it: nothing for the text printers, the
 * layout tree for {@link LayoutPrinter}, which therefore always has the Unicode printer's
 * structure.
 *
 * @param <L> what the sink builds
 */
final class InfixPrinter<L> implements Printer {

  static final InfixPrinter<Void> ASCII = new InfixPrinter<>(false, Sink.NONE, Format.EXACT);
  static final InfixPrinter<Void> UNICODE = new InfixPrinter<>(true, Sink.NONE, Format.EXACT);

  /** A text that begins with a call, {@code sin(x)}: a number before it takes a space. */
  private static final Pattern CALL = Pattern.compile("[\\p{L}\\p{N}_]+\\(");

  /** A number literal with an exponent marker: {@code 1.0e+21}, {@code 3.5e-1}. */
  private static final Pattern SCIENTIFIC = Pattern.compile("[0-9][0-9.]*e[+-][0-9]+");

  private static final Pattern D_END = Pattern.compile("d(\\^[0-9]+|[⁰¹²³⁴⁵⁶⁷⁸⁹]+)?$");

  /**
   * The head of a notation that begins with a letter at the start of a printed factor: the
   * derivative ({@code d/dx}; a quotient that would read as one is printed spaced, {@code d / dx},
   * so only the notation matches) and the limit ({@code lim[}, which no name can begin).
   */
  private static final Pattern LETTER_NOTATION = Pattern.compile("d[⁰¹²³⁴⁵⁶⁷⁸⁹]*/d|lim\\[");

  private final boolean unicode;
  private final Sink<L> sink;
  private final Format format;

  InfixPrinter(boolean unicode, Sink<L> sink, Format format) {
    this.unicode = unicode;
    this.sink = sink;
    this.format = format;
  }

  @Override
  public Format format() {
    return format;
  }

  @Override
  public InfixPrinter<L> withFormat(Format f) {
    return new InfixPrinter<>(unicode, sink, f);
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
   * @param out what the sink built for it
   */
  private record Piece<L>(
      String text,
      int prec,
      boolean minus,
      boolean open,
      boolean literal,
      boolean bare,
      boolean exponentSafe,
      boolean product,
      L out) {

    static <L> Piece<L> atom(String text, boolean bare, boolean exponentSafe, L out) {
      return new Piece<>(text, ATOM, false, false, false, bare, exponentSafe, false, out);
    }

    static <L> Piece<L> of(String text, int prec, L out) {
      return new Piece<>(text, prec, false, false, false, false, false, false, out);
    }
  }

  private Piece<L> wrapped(Piece<L> p) {
    return Piece.atom("(" + p.text() + ")", false, false, sink.wrap(p.out()));
  }

  @Override
  public String print(Expr e) {
    return piece(e, Trail.ROOT).text();
  }

  /** What the sink builds for a tree printed as it holds its operands. */
  L build(Expr e) {
    return piece(e, Trail.ROOT).out();
  }

  /** A list of what the sink built, which may be nulls. */
  @SafeVarargs
  private static <L> List<L> list(L... items) {
    List<L> out = new ArrayList<>(items.length);
    for (L item : items) {
      out.add(item);
    }
    return out;
  }

  private Piece<L> piece(Expr e, Trail at) {
    return switch (e) {
      case Num n -> number(n, at);
      case Constant c -> {
        String text = constant(c);
        yield Piece.atom(text, true, true, sink.constant(c.kind(), text, at));
      }
      case Bool b -> {
        String text = b.value() ? "true" : "false";
        yield Piece.atom(text, true, true, sink.word(text, at));
      }
      case Symbol s -> name(s, at);
      case Sum s -> sum(s, at);
      case Product p -> PrintForm.isNegation(p) ? negation(p.factors().get(1), at) : product(p, at);
      case Power p -> power(p, at);
      case Apply a -> apply(a, at);
      case Lambda l -> lambda(l, at);
      case Relation r -> relation(r, at);
      case ListExpr l -> fenced('[', items(l.items(), at), ']', at);
      case Vector v ->
          unicode
              ? fenced('⟨', items(v.items(), at), '⟩', at)
              : fenced('[', items(v.items(), at), ']', at);
      case Matrix m -> {
        StringBuilder text = new StringBuilder("[");
        List<List<L>> rows = new ArrayList<>();
        int columns = m.rows().getFirst().size();
        for (int r = 0; r < m.rows().size(); r++) {
          List<L> cells = new ArrayList<>();
          List<Expr> row = m.rows().get(r);
          StringBuilder line = new StringBuilder();
          for (int c = 0; c < row.size(); c++) {
            Piece<L> cell = piece(row.get(c), at.child(r * columns + c));
            line.append(c > 0 ? ", " : "").append(cell.text());
            cells.add(cell.out());
          }
          text.append(text.length() > 1 ? ", [" : "[").append(line).append(']');
          rows.add(cells);
        }
        yield Piece.atom(text.append(']').toString(), false, false, sink.matrix(rows, at));
      }
      case SetExpr s -> fenced('{', items(s.items(), at), '}', at);
      case Interval i -> {
        Piece<L> lower = piece(i.lower(), at.child(0));
        Piece<L> upper = piece(i.upper(), at.child(1));
        int open = i.lowerClosed() ? '[' : '(';
        int close = i.upperClosed() ? ']' : ')';
        L body =
            sink.row(
                list(
                    lower.out(),
                    sink.operator(",", Role.PUNCTUATION),
                    sink.space(false),
                    upper.out()),
                null);
        yield Piece.atom(
            Character.toString(open)
                + lower.text()
                + ", "
                + upper.text()
                + Character.toString(close),
            false,
            false,
            sink.fenced(open, body, close, at));
      }
      case Piecewise p -> piecewise(p, at);
      case Derivative d -> derivative(d, at);
      case Integral i -> integral(i, at);
      case Limit l -> limit(l, at);
      case BigSum s -> big("∑", "sum", s.body(), s.var(), s.from(), s.to(), at);
      case BigProduct s -> big("∏", "product", s.body(), s.var(), s.from(), s.to(), at);
    };
  }

  // Leaves.

  private Piece<L> number(Num n, Trail at) {
    if (n.value().equals(Complex.I)) {
      return Piece.atom("i", true, true, sink.imaginary(at));
    }
    switch (NumberFormat.leaf(n.value(), format)) {
      case NumberFormat.Literal l -> {
        Piece<L> literal =
            new Piece<>(
                l.text(),
                ATOM,
                false,
                false,
                // A scientific literal is set off from a following name: 1.5e+3 x.
                !l.text().contains("e"),
                true,
                false,
                false,
                sink.number(l.text(), l.negative() ? null : at));
        return l.negative() ? negation(literal, at) : literal;
      }
      case NumberFormat.Mixed m -> {
        // A mixed number reads as one number, its sign included: -3 1/2.
        String text = m.whole() + " " + m.num() + "/" + m.den();
        L out = sink.mixed(m.whole(), m.num(), m.den(), m.negative() ? null : at);
        return new Piece<>(
            m.negative() ? "-" + text : text,
            PRODUCT,
            m.negative(),
            false,
            false,
            false,
            false,
            true,
            m.negative() ? sink.row(list(sink.operator("-", Role.BINARY), out), at) : out);
      }
      case NumberFormat.Shape shape -> {
        return piece(shape.shape(), at.freeze());
      }
      case NumberFormat.Unchanged u -> {}
    }
    if (PrintForm.isLiteral(n.value())) {
      String text = PrintForm.literal(n.value());
      return new Piece<>(
          text,
          ATOM,
          false,
          false,
          true,
          true,
          n.value() instanceof Int,
          false,
          sink.number(text, at));
    }
    return piece(PrintForm.typed(n.value()), at.freeze());
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

  private Piece<L> name(Symbol s, Trail at) {
    return Piece.atom(nameText(s.name()), true, true, sink.name(s.name(), at));
  }

  private String nameText(String name) {
    return unicode ? PrintForm.subscripted(name) : name;
  }

  // Arithmetic.

  private Piece<L> sum(Sum s, Trail at) {
    StringBuilder text = new StringBuilder();
    List<L> items = new ArrayList<>();
    Piece<L> first = term(piece(s.terms().getFirst(), at.child(0)));
    text.append(first.text());
    items.add(first.out());
    Piece<L> last = first;
    for (int k = 1; k < s.terms().size(); k++) {
      Expr t = s.terms().get(k);
      Trail tk = at.child(k);
      items.add(sink.space(false));
      if (PrintForm.isNegation(t)) {
        last = piece(((Product) t).factors().get(1), tk.child(1));
        if (last.prec() < PRODUCT || last.minus()) {
          last = wrapped(last);
        }
        text.append(" - ");
        items.add(
            sink.row(list(sink.operator("-", Role.BINARY), sink.space(false), last.out()), tk));
      } else {
        last = term(piece(t, tk));
        text.append(" + ");
        items.add(sink.operator("+", Role.BINARY));
        items.add(sink.space(false));
        items.add(last.out());
      }
      text.append(last.text());
    }
    return new Piece<>(
        text.toString(),
        SUM,
        first.minus(),
        last.open(),
        false,
        false,
        false,
        false,
        sink.row(items, at));
  }

  private Piece<L> term(Piece<L> p) {
    return p.prec() < PRODUCT ? wrapped(p) : p;
  }

  private Piece<L> negation(Expr operand, Trail at) {
    return negation(piece(operand, at.child(1)), at);
  }

  /** {@code -p}, the negation at {@code at}. */
  private Piece<L> negation(Piece<L> p, Trail at) {
    if (p.prec() < PREFIX || p.minus()) {
      p = wrapped(p);
    }
    return new Piece<>(
        "-" + p.text(),
        PREFIX,
        true,
        p.open(),
        p.literal(),
        false,
        false,
        false,
        sink.row(list(sink.operator("-", Role.BINARY), p.out()), at));
  }

  private Piece<L> product(Product product, Trail at) {
    List<Expr> factors = product.factors();
    List<Piece<L>> plain = new ArrayList<>();
    List<Piece<L>> parts = new ArrayList<>();
    List<Boolean> divided = new ArrayList<>();
    for (int k = 0; k < factors.size(); k++) {
      Expr f = factors.get(k);
      boolean divide = k > 0 && PrintForm.isReciprocal(f);
      Piece<L> p = divide ? piece(((Power) f).base(), at.child(k).child(0)) : piece(f, at.child(k));
      boolean wrap = k == 0 ? p.prec() < PRODUCT || p.product() : p.prec() < PREFIX || p.minus();
      plain.add(p);
      parts.add(wrap ? wrapped(p) : p);
      divided.add(divide);
    }
    for (int k = 0; k < parts.size() - 1; k++) {
      if (parts.get(k).open()) {
        parts.set(k, wrapped(parts.get(k)));
      }
    }
    StringBuilder text = new StringBuilder(parts.getFirst().text());
    // The layout: the parts so far, which a divided factor takes in as its numerator. The
    // numerator and the denominator are given unparenthesised, the fraction bar grouping them.
    List<L> run = new ArrayList<>(list(parts.getFirst().out()));
    int solo = 0;
    for (int k = 1; k < parts.size(); k++) {
      Piece<L> part = parts.get(k);
      if (!divided.get(k)) {
        String separator = separator(parts.get(k - 1), part);
        text.append(separator);
        if (separator.equals(" ")) {
          run.add(sink.space(false));
        } else if (!separator.isEmpty()) {
          run.add(sink.operator(separator, Role.BINARY));
        }
        run.add(part.out());
        solo = -1;
      } else {
        boolean spaced = derivativeLike(text, part.text());
        text.append(spaced ? " / " : "/");
        boolean numWrapped = solo >= 0 && parts.get(solo) != plain.get(solo);
        L num =
            numWrapped
                ? plain.get(solo).out()
                : run.size() == 1 ? run.getFirst() : sink.row(run, null);
        boolean denWrapped = part != plain.get(k);
        L fraction =
            sink.fraction(
                num, (denWrapped ? plain.get(k) : part).out(), numWrapped, denWrapped, spaced);
        run = new ArrayList<>(list(fraction));
        solo = -1;
      }
      text.append(part.text());
    }
    return new Piece<>(
        text.toString(),
        PRODUCT,
        parts.getFirst().minus(),
        parts.getLast().open(),
        false,
        false,
        false,
        true,
        sink.group(run, at));
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
  private String separator(Piece<L> left, Piece<L> right) {
    if (!unicode) {
      return "*";
    }
    String l = left.text();
    int last = l.codePointBefore(l.length());
    int first = right.text().codePointAt(0);
    if (Character.isDigit(first) || first == '.' || first == '[') {
      return "·";
    }
    // Every notation is set off by a space (2 ∑[k = 1, n] k, 2 ∫ x dx); those beginning with a
    // letter would otherwise be written against a number (2d/dx x², 2lim[x → 0] f).
    if (LETTER_NOTATION.matcher(right.text()).lookingAt()) {
      return " ";
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

  private Piece<L> power(Power p, Trail at) {
    if (PrintForm.isSqrt(p)) {
      return unicode
          ? radical("√", null, p.base(), at.child(0), at)
          : call("sqrt", List.of(p.base()), at);
    }
    Piece<L> base = piece(p.base(), at.child(0));
    // A literal with an exponent marker is parenthesised too: 1.0e+21^x would read as 1.0e+(21^x).
    if (base.prec() < ATOM || SCIENTIFIC.matcher(base.text()).matches()) {
      base = wrapped(base);
    }
    if (unicode && p.exponent() instanceof Num n && n.value() instanceof Int k && k.signum() >= 0) {
      return Piece.of(
          base.text() + PrintForm.superscript(k.toString()),
          POWER,
          sink.powerDigits(base.out(), sink.number(k.toString(), at.child(1)), at));
    }
    InfixPrinter<L> exponents =
        format.forExponents() == format ? this : withFormat(format.forExponents());
    Piece<L> exponent = exponents.piece(p.exponent(), at.child(1));
    boolean wrap = !exponent.exponentSafe();
    return Piece.of(
        base.text() + "^" + (wrap ? wrapped(exponent) : exponent).text(),
        POWER,
        sink.power(base.out(), exponent.out(), wrap, at));
  }

  private Piece<L> radical(String sign, L index, Expr operand, Trail operandAt, Trail at) {
    Piece<L> p = piece(operand, operandAt);
    boolean wrap = !p.bare();
    return Piece.of(
        sign + (wrap ? wrapped(p) : p).text(), PREFIX, sink.radical(index, p.out(), wrap, at));
  }

  // Applications.

  private Piece<L> apply(Apply a, Trail at) {
    String head = a.head().name();
    List<Expr> args = a.args();
    if (unicode) {
      if (head.equals("abs") && args.size() == 1) {
        Piece<L> arg = piece(args.getFirst(), at.child(0));
        Piece<L> inner = arg.prec() < SUM ? wrapped(arg) : arg;
        return barOutsideBrackets(inner.text())
            ? Piece.atom(
                head + "(" + arg.text() + ")",
                false,
                true,
                callOut(head, sink.fenced('(', arg.out(), ')', null), at))
            : Piece.atom(
                "|" + inner.text() + "|", false, false, sink.fenced('|', inner.out(), '|', at));
      } else if (head.equals("factorial") && args.size() == 1) {
        Piece<L> p = piece(args.getFirst(), at.child(0));
        if (p.prec() < POSTFIX) {
          p = wrapped(p);
        }
        return Piece.of(
            p.text() + "!", POSTFIX, sink.row(list(p.out(), sink.operator("!", Role.POSTFIX)), at));
      } else if (head.equals("cbrt") && args.size() == 1) {
        return radical("∛", sink.number("3", null), args.getFirst(), at.child(0), at);
      } else if (head.equals("root") && args.size() == 2 && args.get(1).equals(Num.of(4))) {
        return radical("∜", sink.number("4", null), args.getFirst(), at.child(0), at);
      } else if (head.equals("cross") && args.size() == 2) {
        return cross(args.getFirst(), args.get(1), at);
      }
    }
    String name = nameText(head);
    if (head.equals("limit")
        && args.size() == 4
        && args.get(1) instanceof Symbol
        && args.get(3) instanceof Symbol side
        && (side.name().equals("left") || side.name().equals("right"))) {
      // A bare left or right there would read back as the side of a Limit, not as a symbol.
      Piece<L> first = items(args.subList(0, 3), at);
      Piece<L> last = wrapped(name(side, at.child(3)));
      L body =
          sink.row(
              list(
                  first.out(), sink.operator(",", Role.PUNCTUATION), sink.space(false), last.out()),
              null);
      return Piece.atom(
          name + "(" + first.text() + ", " + last.text() + ")",
          false,
          true,
          callOut(head, sink.fenced('(', body, ')', null), at));
    }
    return call(head, args, at);
  }

  private Piece<L> call(String head, List<Expr> args, Trail at) {
    Piece<L> items = items(args, at);
    return Piece.atom(
        nameText(head) + "(" + items.text() + ")",
        false,
        true,
        callOut(head, sink.fenced('(', items.out(), ')', null), at));
  }

  private L callOut(String head, L arguments, Trail at) {
    return sink.row(list(sink.head(head), arguments), at);
  }

  private Piece<L> cross(Expr u, Expr v, Trail at) {
    Piece<L> left = piece(u, at.child(0));
    if (left.prec() < PRODUCT || left.open()) {
      left = wrapped(left);
    }
    Piece<L> right = piece(v, at.child(1));
    if (right.prec() < PREFIX || right.minus()) {
      right = wrapped(right);
    }
    return new Piece<>(
        left.text() + " × " + right.text(),
        PRODUCT,
        left.minus(),
        right.open(),
        false,
        false,
        false,
        false,
        sink.row(
            list(
                left.out(),
                sink.space(false),
                sink.operator("×", Role.BINARY),
                sink.space(false),
                right.out()),
            at));
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

  private Piece<L> lambda(Lambda l, Trail at) {
    List<Expr> params = new ArrayList<>(l.params());
    Piece<L> head =
        params.size() == 1 ? name(l.params().getFirst(), at.child(0)) : wrapped(items(params, at));
    Piece<L> body = piece(l.body(), at.child(params.size()));
    String arrow = unicode ? "→" : "->";
    return Piece.of(
        head.text() + " " + arrow + " " + body.text(),
        LAMBDA,
        sink.row(
            list(
                head.out(),
                sink.space(false),
                sink.operator(arrow, Role.RELATION),
                sink.space(false),
                body.out()),
            at));
  }

  private Piece<L> relation(Relation r, Trail at) {
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
    Piece<L> left = operand(r.left(), SUM, at.child(0));
    Piece<L> right = operand(r.right(), SUM, at.child(1));
    return Piece.of(
        left.text() + " " + op + " " + right.text(),
        RELATION,
        sink.row(
            list(
                left.out(),
                sink.space(false),
                sink.operator(op, Role.RELATION),
                sink.space(false),
                right.out()),
            at));
  }

  /** A child printed in a position that needs at least {@code level}. */
  private Piece<L> operand(Expr e, int level, Trail at) {
    Piece<L> p = piece(e, at);
    return p.prec() < level ? wrapped(p) : p;
  }

  /** Items separated by commas, operand {@code k} of the node at {@code at} the k-th. */
  private Piece<L> items(List<Expr> items, Trail at) {
    StringBuilder text = new StringBuilder();
    List<L> out = new ArrayList<>();
    for (int k = 0; k < items.size(); k++) {
      Piece<L> item = piece(items.get(k), at.child(k));
      if (k > 0) {
        text.append(", ");
        out.add(sink.operator(",", Role.PUNCTUATION));
        out.add(sink.space(false));
      }
      text.append(item.text());
      out.add(item.out());
    }
    return Piece.atom(text.toString(), false, false, sink.row(out, null));
  }

  private Piece<L> fenced(int open, Piece<L> items, int close, Trail at) {
    return Piece.atom(
        Character.toString(open) + items.text() + Character.toString(close),
        false,
        false,
        sink.fenced(open, items.out(), close, at));
  }

  private Piece<L> piecewise(Piecewise p, Trail at) {
    List<Expr> args = new ArrayList<>();
    for (Piecewise.Piece q : p.pieces()) {
      args.add(q.value());
      args.add(q.condition());
    }
    args.add(p.otherwise());
    StringBuilder text = new StringBuilder("piecewise(");
    List<L> values = new ArrayList<>();
    List<L> conditions = new ArrayList<>();
    L otherwise = null;
    for (int k = 0; k < args.size(); k++) {
      Piece<L> arg = piece(args.get(k), at.child(k));
      text.append(k > 0 ? ", " : "").append(arg.text());
      if (k == args.size() - 1) {
        otherwise = arg.out();
      } else {
        (k % 2 == 0 ? values : conditions).add(arg.out());
      }
    }
    return Piece.atom(
        text.append(')').toString(),
        false,
        true,
        sink.piecewise(values, conditions, otherwise, at));
  }

  // Notation.

  private Piece<L> derivative(Derivative d, Trail at) {
    if (!unicode) {
      List<Expr> args = new ArrayList<>(List.of(d.expr(), d.var()));
      if (d.order() != 1) {
        args.add(Num.of(d.order()));
      }
      return call("diff", args, at);
    }
    Piece<L> var = name(d.var(), at.child(1));
    String n = Integer.toString(d.order());
    String order = PrintForm.superscript(n);
    L operator;
    String head;
    if (d.order() == 1) {
      head = "d/d" + var.text();
      operator =
          sink.fraction(
              sink.operator("d", Role.DIFFERENTIAL),
              sink.row(list(sink.operator("d", Role.DIFFERENTIAL), var.out()), null),
              false,
              false,
              false);
    } else {
      head = "d" + order + "/d" + var.text() + order;
      operator =
          sink.fraction(
              sink.powerDigits(sink.operator("d", Role.DIFFERENTIAL), sink.number(n, null), null),
              sink.row(
                  list(
                      sink.operator("d", Role.DIFFERENTIAL),
                      sink.powerDigits(var.out(), sink.number(n, null), null)),
                  null),
              false,
              false,
              false);
    }
    return body(head, operator, null, null, false, d.expr(), at);
  }

  private Piece<L> integral(Integral i, Trail at) {
    if (!unicode || PrintForm.integralNeedsCommandForm(i)) {
      List<Expr> args = new ArrayList<>(List.of(i.integrand(), i.var()));
      if (i.isDefinite()) {
        args.add(i.lower());
        args.add(i.upper());
      }
      return call("integrate", args, at);
    }
    String bounds = "";
    L lower = null;
    L upper = null;
    if (i.isDefinite()) {
      Piece<L> from = operand(i.lower(), SUM, at.child(2));
      Piece<L> to = operand(i.upper(), SUM, at.child(3));
      bounds = "[" + from.text() + ", " + to.text() + "]";
      lower = from.out();
      upper = to.out();
    }
    // A [ right after ∫ is the bounds, so an integrand that begins with one is parenthesised.
    Piece<L> integrand = piece(i.integrand(), at.child(0));
    if (integrand.prec() < SUM || integrand.text().startsWith("[")) {
      integrand = wrapped(integrand);
    }
    Piece<L> var = name(i.var(), at.child(1));
    L body =
        sink.row(
            list(
                integrand.out(),
                sink.space(true),
                sink.operator("d", Role.DIFFERENTIAL),
                var.out()),
            null);
    return Piece.of(
        "∫" + bounds + " " + integrand.text() + " d" + var.text(),
        INTEGRAL,
        sink.bigOperator(sink.operator("∫", Role.LARGE), lower, upper, body, false, at));
  }

  private Piece<L> limit(Limit l, Trail at) {
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
      return call("limit", args, at);
    }
    Piece<L> var = name(l.var(), at.child(1));
    Piece<L> point = operand(l.point(), SUM, at.child(2));
    L lower =
        sink.row(
            list(
                var.out(),
                sink.space(false),
                sink.operator("→", Role.RELATION),
                sink.space(false),
                side.isEmpty() ? point.out() : sink.side(point.out(), side)),
            null);
    return body(
        "lim[" + var.text() + " → " + point.text() + side + "]",
        sink.operator("lim", Role.LIMIT),
        lower,
        null,
        true,
        l.expr(),
        at);
  }

  private Piece<L> big(
      String sign, String command, Expr body, Symbol var, Expr from, Expr to, Trail at) {
    if (!unicode) {
      return call(command, List.of(body, var, from, to), at);
    }
    Piece<L> index = name(var, at.child(1));
    Piece<L> first = operand(from, SUM, at.child(2));
    Piece<L> last = operand(to, SUM, at.child(3));
    L lower =
        sink.row(
            list(
                index.out(),
                sink.space(false),
                sink.operator("=", Role.RELATION),
                sink.space(false),
                first.out()),
            null);
    return body(
        sign + "[" + index.text() + " = " + first.text() + ", " + last.text() + "]",
        sink.operator(sign, Role.LARGE),
        lower,
        last.out(),
        true,
        body,
        at);
  }

  /** A big operator: its head, then its body, which runs to the end of the term. */
  private Piece<L> body(
      String head, L operator, L lower, L upper, boolean limits, Expr body, Trail at) {
    Piece<L> p = piece(body, at.child(0));
    if (p.prec() < PRODUCT) {
      p = wrapped(p);
    }
    return new Piece<>(
        head + " " + p.text(),
        PREFIX,
        false,
        true,
        false,
        false,
        false,
        false,
        sink.bigOperator(operator, lower, upper, p.out(), limits, at));
  }
}
