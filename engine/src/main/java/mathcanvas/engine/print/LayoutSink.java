package mathcanvas.engine.print;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import mathcanvas.engine.builtin.Builtins;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.print.layout.BigOp;
import mathcanvas.engine.print.layout.Cls;
import mathcanvas.engine.print.layout.Delimited;
import mathcanvas.engine.print.layout.Fraction;
import mathcanvas.engine.print.layout.Glyph;
import mathcanvas.engine.print.layout.Layout;
import mathcanvas.engine.print.layout.Matrix;
import mathcanvas.engine.print.layout.Piecewise;
import mathcanvas.engine.print.layout.Radical;
import mathcanvas.engine.print.layout.Row;
import mathcanvas.engine.print.layout.Scripts;
import mathcanvas.engine.print.layout.Space;
import mathcanvas.engine.print.layout.Style;
import mathcanvas.engine.print.layout.Text;
import mathcanvas.engine.print.layout.UnitText;
import mathcanvas.engine.rule.Path;

/**
 * The sink that builds the layout tree. Letters: a one-letter Latin or lower-case Greek name is
 * italic, an upper-case Greek letter and a name of several letters upright; {@code e}, {@code i},
 * {@code π} and the differential {@code d} are italic, {@code ∞} and the number sets upright. A
 * builtin's name, or any name of several letters, heading a call is an upright operator name. A
 * name {@code base_tail} is the base with the tail as a subscript. A quotient whose numerator
 * begins with a minus sign carries the sign before its bar.
 */
final class LayoutSink implements Sink<Layout> {

  static final LayoutSink INSTANCE = new LayoutSink();

  private LayoutSink() {}

  private static Path path(Trail at) {
    return at == null ? null : at.path();
  }

  @Override
  public Layout name(String name, Trail at) {
    int prime = name.indexOf('\'');
    if (prime > 0) {
      // the prime notation: the name, then its primes
      return new Row(
          List.of(
              name(name.substring(0, prime), null),
              new Text("′".repeat(name.length() - prime), Style.UPRIGHT, Cls.ORD, null)),
          path(at));
    }
    int cut = name.lastIndexOf('_');
    if (cut < 1 || cut == name.length() - 1) {
      return identifier(name, path(at));
    }
    String tail = name.substring(cut + 1);
    Scripts.Form form =
        PrintForm.subscripted(name).equals(name)
            ? Scripts.Form.UNDERSCORE
            : Scripts.Form.CHARACTERS;
    return new Scripts(
        identifier(name.substring(0, cut), null),
        identifier(tail, null),
        null,
        form,
        null,
        path(at));
  }

  private static Layout identifier(String name, Path path) {
    if (name.codePointCount(0, name.length()) != 1) {
      return new Text(name, Style.UPRIGHT, Cls.ORD, path);
    }
    int c = name.codePointAt(0);
    boolean italic = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= 'α' && c <= 'ω');
    return new Glyph(c, italic ? Style.ITALIC : Style.UPRIGHT, Cls.ORD, path);
  }

  @Override
  public Layout head(String name) {
    int prime = name.indexOf('\'');
    if (prime > 0) {
      return new Row(
          List.of(
              head(name.substring(0, prime)),
              new Text("′".repeat(name.length() - prime), Style.UPRIGHT, Cls.ORD, null)),
          null);
    }
    if (Builtins.lookup(name).isPresent()
        || (name.indexOf('_') < 0 && name.codePointCount(0, name.length()) > 1)) {
      return new Text(name, Style.UPRIGHT, Cls.OP, null);
    }
    return name(name, null);
  }

  @Override
  public Layout number(String literal, Trail at) {
    return new Text(literal, Style.UPRIGHT, Cls.ORD, path(at));
  }

  @Override
  public Layout mixed(String whole, String num, String den, Trail at) {
    return new Row(
        List.of(
            new Text(whole, Style.UPRIGHT, Cls.ORD, null),
            new Space(Space.Kind.LINEAR),
            new Fraction(
                new Text(num, Style.UPRIGHT, Cls.ORD, null),
                new Text(den, Style.UPRIGHT, Cls.ORD, null),
                false,
                false,
                false,
                null)),
        path(at));
  }

  @Override
  public Layout constant(Constant.Kind kind, String text, Trail at) {
    return switch (kind) {
      case PI, E -> new Glyph(text.codePointAt(0), Style.ITALIC, Cls.ORD, path(at));
      case UNDEFINED -> new Text(text, Style.UPRIGHT, Cls.ORD, path(at));
      case INFINITY, INTEGERS, RATIONALS, REALS, COMPLEXES ->
          new Glyph(text.codePointAt(0), Style.UPRIGHT, Cls.ORD, path(at));
    };
  }

  @Override
  public Layout imaginary(Trail at) {
    return new Glyph('i', Style.ITALIC, Cls.ORD, path(at));
  }

  @Override
  public Layout unit(String shown, String linear, Trail at) {
    return new UnitText(shown, linear, path(at));
  }

  @Override
  public Layout word(String text, Trail at) {
    return new Text(text, Style.UPRIGHT, Cls.ORD, path(at));
  }

  @Override
  public Layout operator(String symbol, Role role) {
    int c = symbol.codePointAt(0);
    return switch (role) {
      case BINARY -> new Glyph(c, Style.UPRIGHT, Cls.BIN, null);
      case RELATION -> new Glyph(c, Style.UPRIGHT, Cls.REL, null);
      case PUNCTUATION -> new Glyph(c, Style.UPRIGHT, Cls.PUNCT, null);
      case POSTFIX -> new Glyph(c, Style.UPRIGHT, Cls.CLOSE, null);
      case LARGE -> new Glyph(c, Style.UPRIGHT, Cls.OP, null);
      case LIMIT -> new Text(symbol, Style.UPRIGHT, Cls.OP, null);
      case DIFFERENTIAL -> new Glyph(c, Style.ITALIC, Cls.ORD, null);
      case SOLIDUS -> new Glyph(c, Style.UPRIGHT, Cls.ORD, null);
    };
  }

  @Override
  public Layout space(boolean thin) {
    return new Space(thin ? Space.Kind.THIN : Space.Kind.LINEAR);
  }

  @Override
  public Layout row(List<Layout> items, Trail at) {
    return new Row(items, path(at));
  }

  @Override
  public Layout group(List<Layout> items, Trail at) {
    return items.size() == 1 ? withPath(items.getFirst(), path(at)) : row(items, at);
  }

  @Override
  public Layout wrap(Layout inner) {
    return new Delimited('(', inner, ')', inner.path());
  }

  @Override
  public Layout fenced(int open, Layout body, int close, Trail at) {
    return new Delimited(open, body, close, path(at));
  }

  @Override
  public Layout fraction(
      Layout num, Layout den, boolean numWrapped, boolean denWrapped, boolean spaced) {
    Layout sign = null;
    if (!numWrapped && isNegation(num)) {
      sign = ((Row) num).items().getFirst();
      num = ((Row) num).items().get(1);
      // Only the printer's own parentheses (wrap) come off: an interval is fenced by its own
      // brackets, which may differ, as (a, e] does.
      if (num instanceof Delimited d && isWrap(d)) {
        num = d.body();
        numWrapped = true;
      }
    } else if (!numWrapped
        && num instanceof Row r
        && r.path() == null
        && isNegation(r.items().getFirst())) {
      List<Layout> items = new ArrayList<>(r.items());
      sign = ((Row) items.getFirst()).items().getFirst();
      items.set(0, ((Row) items.getFirst()).items().get(1));
      num = new Row(items, null);
    }
    Fraction fraction = new Fraction(num, den, numWrapped, denWrapped, spaced, null);
    return sign == null ? fraction : new Row(List.of(sign, fraction), null);
  }

  /** Whether a delimited layout is the printer's parenthesising of its body ({@link #wrap}). */
  private static boolean isWrap(Delimited d) {
    return d.open() == '(' && d.close() == ')' && Objects.equals(d.path(), d.body().path());
  }

  /** Whether a layout is a negation as the printer builds one: the sign and its operand. */
  private static boolean isNegation(Layout l) {
    return l instanceof Row r
        && r.items().size() == 2
        && r.items().getFirst() instanceof Glyph g
        && g.codePoint() == '-'
        && g.cls() == Cls.BIN;
  }

  @Override
  public Layout power(Layout base, Layout exponent, boolean wrapped, Trail at) {
    return new Scripts(
        base,
        null,
        exponent,
        null,
        wrapped ? Scripts.Form.CARET_WRAPPED : Scripts.Form.CARET,
        path(at));
  }

  @Override
  public Layout powerDigits(Layout base, Layout digits, Trail at) {
    return new Scripts(base, null, digits, null, Scripts.Form.CHARACTERS, path(at));
  }

  @Override
  public Layout radical(Layout index, Layout radicand, boolean wrapped, Trail at) {
    return new Radical(index, radicand, wrapped, path(at));
  }

  @Override
  public Layout side(Layout point, String sign) {
    return new Scripts(
        point,
        null,
        new Glyph(sign.codePointAt(0), Style.UPRIGHT, Cls.ORD, null),
        null,
        Scripts.Form.APPENDED,
        null);
  }

  @Override
  public Layout bigOperator(
      Layout operator, Layout lower, Layout upper, Layout body, boolean limits, Trail at) {
    return new BigOp(operator, lower, upper, body, limits, path(at));
  }

  @Override
  public Layout matrix(List<List<Layout>> rows, Trail at) {
    return new Matrix(rows, '[', ']', path(at));
  }

  @Override
  public Layout piecewise(
      List<Layout> values, List<Layout> conditions, Layout otherwise, Trail at) {
    List<Piecewise.Piece> pieces = new ArrayList<>();
    for (int k = 0; k < values.size(); k++) {
      pieces.add(new Piecewise.Piece(values.get(k), conditions.get(k)));
    }
    return new Piecewise(pieces, otherwise, path(at));
  }

  /** A layout with another path. */
  private static Layout withPath(Layout l, Path p) {
    return switch (l) {
      case Row r -> new Row(r.items(), p);
      case Glyph g -> new Glyph(g.codePoint(), g.style(), g.cls(), p);
      case Text t -> new Text(t.text(), t.style(), t.cls(), p);
      case UnitText u -> new UnitText(u.shown(), u.linear(), p);
      case Space s -> new Row(List.of(s), p);
      case Fraction f ->
          new Fraction(f.num(), f.den(), f.numWrapped(), f.denWrapped(), f.spaced(), p);
      case Scripts s -> new Scripts(s.base(), s.sub(), s.sup(), s.subForm(), s.supForm(), p);
      case Radical r -> new Radical(r.index(), r.radicand(), r.wrapped(), p);
      case Delimited d -> new Delimited(d.open(), d.body(), d.close(), p);
      case Matrix m -> new Matrix(m.rows(), m.open(), m.close(), p);
      case BigOp b -> new BigOp(b.operator(), b.lower(), b.upper(), b.body(), b.limits(), p);
      case Piecewise w -> new Piecewise(w.pieces(), w.otherwise(), p);
    };
  }
}
