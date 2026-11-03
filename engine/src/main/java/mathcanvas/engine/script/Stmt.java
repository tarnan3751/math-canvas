package mathcanvas.engine.script;

import java.util.List;
import java.util.Objects;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Position;

/**
 * One statement of a block ({@code docs/language.md} §19), its expressions raw as the parser built
 * them and its start the position of its first token. Statements are not expressions: they never
 * reach the canonical form, the matcher or a printer.
 */
public sealed interface Stmt {

  /**
   * Where the statement begins.
   *
   * @return the position of its first token
   */
  Position start();

  /**
   * {@code name := value} inside a block: the value evaluated now and bound.
   *
   * @param name the name assigned
   * @param value the right side, as parsed
   * @param start the position of its first token
   */
  record Assign(Symbol name, Expr value, Position start) implements Stmt {

    /** Checks the operands. */
    public Assign {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(start, "start");
    }
  }

  /**
   * An expression evaluated for its value.
   *
   * @param expr the expression, as parsed
   * @param start the position of its first token
   */
  record Evaluate(Expr expr, Position start) implements Stmt {

    /** Checks the operands. */
    public Evaluate {
      Objects.requireNonNull(expr, "expr");
      Objects.requireNonNull(start, "start");
    }
  }

  /**
   * {@code print(value)} as a statement: the value appended to the run's output.
   *
   * @param value the expression printed, as parsed
   * @param start the position of its first token
   */
  record Print(Expr value, Position start) implements Stmt {

    /** Checks the operands. */
    public Print {
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(start, "start");
    }
  }

  /**
   * One branch of an if: its condition and its block.
   *
   * @param condition the condition, as parsed
   * @param body the block it runs
   */
  record Branch(Expr condition, Program body) {

    /** Checks the operands. */
    public Branch {
      Objects.requireNonNull(condition, "condition");
      Objects.requireNonNull(body, "body");
    }
  }

  /**
   * {@code if c then … else if … else … end}: the first branch whose condition is true runs, else
   * the otherwise block.
   *
   * @param branches the branches, at least one
   * @param otherwise the block after {@code else}, empty when there is none
   * @param start the position of its first token
   */
  record If(List<Branch> branches, Program otherwise, Position start) implements Stmt {

    /** Checks and copies the operands. */
    public If {
      branches = List.copyOf(branches);
      if (branches.isEmpty()) {
        throw new IllegalArgumentException("an if needs a branch");
      }
      Objects.requireNonNull(otherwise, "otherwise");
      Objects.requireNonNull(start, "start");
    }
  }

  /** What a for loop iterates. */
  sealed interface Source {}

  /**
   * An integer range {@code from..to step step}.
   *
   * @param from the first value, as parsed
   * @param to the last value, as parsed
   * @param step the step, as parsed, or null for 1
   */
  record Range(Expr from, Expr to, Expr step) implements Source {

    /** Checks the operands. */
    public Range {
      Objects.requireNonNull(from, "from");
      Objects.requireNonNull(to, "to");
    }
  }

  /**
   * The items of a list, a vector or a set, or the rows of a matrix.
   *
   * @param items the expression, as parsed
   */
  record Items(Expr items) implements Source {

    /** Checks the operand. */
    public Items {
      Objects.requireNonNull(items, "items");
    }
  }

  /**
   * {@code for variable in source … end}.
   *
   * @param variable the loop variable, local to the loop
   * @param source what it iterates
   * @param body the block run for each value
   * @param start the position of its first token
   */
  record For(Symbol variable, Source source, Program body, Position start) implements Stmt {

    /** Checks the operands. */
    public For {
      Objects.requireNonNull(variable, "variable");
      Objects.requireNonNull(source, "source");
      Objects.requireNonNull(body, "body");
      Objects.requireNonNull(start, "start");
    }
  }

  /**
   * {@code while condition … end}.
   *
   * @param condition the condition, as parsed
   * @param body the block run while it is true
   * @param start the position of its first token
   */
  record While(Expr condition, Program body, Position start) implements Stmt {

    /** Checks the operands. */
    public While {
      Objects.requireNonNull(condition, "condition");
      Objects.requireNonNull(body, "body");
      Objects.requireNonNull(start, "start");
    }
  }

  /**
   * {@code return value}, in a function's body.
   *
   * @param value the value, as parsed
   * @param start the position of its first token
   */
  record Return(Expr value, Position start) implements Stmt {

    /** Checks the operands. */
    public Return {
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(start, "start");
    }
  }

  /**
   * {@code break}, in a loop.
   *
   * @param start the position of its first token
   */
  record Break(Position start) implements Stmt {

    /** Checks the operand. */
    public Break {
      Objects.requireNonNull(start, "start");
    }
  }

  /**
   * {@code continue}, in a loop.
   *
   * @param start the position of its first token
   */
  record Continue(Position start) implements Stmt {

    /** Checks the operand. */
    public Continue {
      Objects.requireNonNull(start, "start");
    }
  }

  /**
   * {@code global a, b}, in a function's body: those names are the document's in the whole body.
   *
   * @param names the names
   * @param start the position of its first token
   */
  record Global(List<Symbol> names, Position start) implements Stmt {

    /** Checks and copies the operands. */
    public Global {
      names = Program.names(names);
      Objects.requireNonNull(start, "start");
    }
  }
}
