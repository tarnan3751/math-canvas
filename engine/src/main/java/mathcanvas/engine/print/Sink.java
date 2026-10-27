package mathcanvas.engine.print;

import java.util.List;
import mathcanvas.engine.expr.Constant;

/**
 * What {@link InfixPrinter} builds beside each piece of text: nothing for the text printers ({@link
 * #NONE}), the layout tree for the layout printer. The printer makes every decision (parentheses,
 * separators, notation) on the text; a sink only records the structure that text has, so its output
 * always reads as the text does. A {@link Trail} names the subexpression a part prints, null for a
 * part that prints none.
 *
 * @param <L> what the sink builds
 */
interface Sink<L> {

  /** The role of an operator sign, from which a sink decides its spacing. */
  enum Role {
    /** A binary operator: {@code +}, {@code -}, {@code ·}, {@code ×}. */
    BINARY,
    /** A relation or an arrow: {@code =}, {@code ≤}, {@code ∈}, {@code →}. */
    RELATION,
    /** A comma between items. */
    PUNCTUATION,
    /** A postfix sign: {@code !}. */
    POSTFIX,
    /** A large operator: {@code ∫}, {@code ∑}, {@code ∏}. */
    LARGE,
    /** An operator name written as a word: {@code lim}. */
    LIMIT,
    /** The differential {@code d}. */
    DIFFERENTIAL
  }

  /** A name: a symbol, a bound variable, a parameter. */
  L name(String name, Trail at);

  /** The head of a call. */
  L head(String name);

  /** A non-negative number literal. */
  L number(String literal, Trail at);

  /** A constant, printed as {@code text}. */
  L constant(Constant.Kind kind, String text, Trail at);

  /** The imaginary unit. */
  L imaginary(Trail at);

  /** A word: {@code true}, {@code false}. */
  L word(String text, Trail at);

  /** An operator sign. */
  L operator(String symbol, Role role);

  /** A space of the text, or the thin space before a differential. */
  L space(boolean thin);

  /** Parts side by side. */
  L row(List<L> items, Trail at);

  /** A product's parts: a row, or its one part when a quotient took in the rest. */
  L group(List<L> items, Trail at);

  /** A piece in parentheses. */
  L wrap(L inner);

  /** A body between two fences ({@code 0} for none). */
  L fenced(int open, L body, int close, Trail at);

  /** A quotient, with the parentheses and spacing its linear form has. */
  L fraction(L num, L den, boolean numWrapped, boolean denWrapped, boolean spaced);

  /** A power with {@code ^}, its exponent parenthesised in the text when {@code wrapped}. */
  L power(L base, L exponent, boolean wrapped, Trail at);

  /** A power whose exponent is written in superscript digits. */
  L powerDigits(L base, L digits, Trail at);

  /** A radical, its radicand parenthesised in the text when {@code wrapped}. */
  L radical(L index, L radicand, boolean wrapped, Trail at);

  /** A limit point with its side sign, {@code 0+}. */
  L side(L point, String sign);

  /** A big operator with its bounds and its body. */
  L bigOperator(L operator, L lower, L upper, L body, boolean limits, Trail at);

  /** A matrix by rows. */
  L matrix(List<List<L>> rows, Trail at);

  /** A piecewise expression. */
  L piecewise(List<L> values, List<L> conditions, L otherwise, Trail at);

  /** The text printers' sink, which builds nothing. */
  Sink<Void> NONE =
      new Sink<>() {
        @Override
        public Void name(String name, Trail at) {
          return null;
        }

        @Override
        public Void head(String name) {
          return null;
        }

        @Override
        public Void number(String literal, Trail at) {
          return null;
        }

        @Override
        public Void constant(Constant.Kind kind, String text, Trail at) {
          return null;
        }

        @Override
        public Void imaginary(Trail at) {
          return null;
        }

        @Override
        public Void word(String text, Trail at) {
          return null;
        }

        @Override
        public Void operator(String symbol, Role role) {
          return null;
        }

        @Override
        public Void space(boolean thin) {
          return null;
        }

        @Override
        public Void row(List<Void> items, Trail at) {
          return null;
        }

        @Override
        public Void group(List<Void> items, Trail at) {
          return null;
        }

        @Override
        public Void wrap(Void inner) {
          return null;
        }

        @Override
        public Void fenced(int open, Void body, int close, Trail at) {
          return null;
        }

        @Override
        public Void fraction(
            Void num, Void den, boolean numWrapped, boolean denWrapped, boolean spaced) {
          return null;
        }

        @Override
        public Void power(Void base, Void exponent, boolean wrapped, Trail at) {
          return null;
        }

        @Override
        public Void powerDigits(Void base, Void digits, Trail at) {
          return null;
        }

        @Override
        public Void radical(Void index, Void radicand, boolean wrapped, Trail at) {
          return null;
        }

        @Override
        public Void side(Void point, String sign) {
          return null;
        }

        @Override
        public Void bigOperator(
            Void operator, Void lower, Void upper, Void body, boolean limits, Trail at) {
          return null;
        }

        @Override
        public Void matrix(List<List<Void>> rows, Trail at) {
          return null;
        }

        @Override
        public Void piecewise(List<Void> values, List<Void> conditions, Void otherwise, Trail at) {
          return null;
        }
      };
}
