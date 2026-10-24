package mathcanvas.engine.match;

import java.util.List;
import java.util.Objects;
import mathcanvas.engine.expr.Expr;

/**
 * A compiled pattern: the node kinds that need their own matching, pattern variables, and a
 * positional node for every other kind. {@link Pattern#of} builds it.
 */
public sealed interface PatternNode {

  /**
   * An expression with no pattern variable, matched by equality.
   *
   * @param expr the expression
   */
  record Literal(Expr expr) implements PatternNode {

    /**
     * Checks the expression.
     *
     * @throws NullPointerException if {@code expr} is null
     */
    public Literal {
      Objects.requireNonNull(expr, "expr");
    }
  }

  /**
   * A pattern variable ({@code x_}): matches any one expression; a second occurrence must match an
   * equal one.
   *
   * @param name the name, with its underscore
   */
  record Var(String name) implements PatternNode {}

  /**
   * A sequence variable: {@code rest__} matches one or more operands, {@code rest___} zero or more.
   *
   * @param name the name, with its underscores
   * @param allowEmpty whether it may match no operand
   */
  record Seq(String name, boolean allowEmpty) implements PatternNode {}

  /**
   * A sum, matched commutatively: each operand matches a distinct term, and {@code rest} takes the
   * terms left over.
   *
   * @param operands the operands other than the sequence variable
   * @param rest the sequence variable, or null
   */
  record SumP(List<PatternNode> operands, Seq rest) implements PatternNode {

    /** Copies the operands. */
    public SumP {
      operands = List.copyOf(operands);
    }
  }

  /**
   * A product, matched commutatively: each operand matches a distinct factor, and {@code rest}
   * takes the factors left over.
   *
   * @param operands the operands other than the sequence variable
   * @param rest the sequence variable, or null
   */
  record ProductP(List<PatternNode> operands, Seq rest) implements PatternNode {

    /** Copies the operands. */
    public ProductP {
      operands = List.copyOf(operands);
    }
  }

  /**
   * A power.
   *
   * @param base the base
   * @param exponent the exponent
   */
  record PowerP(PatternNode base, PatternNode exponent) implements PatternNode {}

  /**
   * An application, its arguments matched positionally.
   *
   * @param head a {@link Literal} symbol, or a {@link Var} that binds the head symbol
   * @param args the arguments, sequence variables allowed
   */
  record ApplyP(PatternNode head, List<PatternNode> args) implements PatternNode {

    /** Copies the arguments. */
    public ApplyP {
      args = List.copyOf(args);
    }
  }

  /**
   * Any other kind: the same kind and data as {@code shape} ({@link Operands#sameShape}), the
   * operands matched positionally.
   *
   * @param shape the pattern node as written
   * @param operands its operands, compiled
   */
  record Structural(Expr shape, List<PatternNode> operands) implements PatternNode {

    /** Copies the operands. */
    public Structural {
      operands = List.copyOf(operands);
    }
  }
}
