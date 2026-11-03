package mathcanvas.engine.script;

import java.util.List;
import java.util.Objects;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;

/**
 * What a traced run recorded for its narration ({@code docs/language.md} §19.6): each statement's
 * effect, and each loop's turns, of which only the first two and the last are kept.
 */
public sealed interface RunEvent {

  /** What a statement did. */
  enum Kind {
    /** An assignment; its name, its right side and the value bound. */
    ASSIGN,
    /** An expression and its value. */
    EVALUATE,
    /** A {@code print} and the value printed. */
    PRINT,
    /** A {@code return} and the value returned. */
    RETURN,
    /** A condition and the truth value it had. */
    CONDITION
  }

  /**
   * One statement's effect.
   *
   * @param kind what it did
   * @param line its line, from 1, in the text it was typed in
   * @param name the name assigned, or null
   * @param expr its expression, as parsed
   * @param value its value
   */
  record Done(Kind kind, int line, Symbol name, Expr expr, Expr value) implements RunEvent {

    /** Checks the operands. */
    public Done {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(expr, "expr");
      Objects.requireNonNull(value, "value");
    }
  }

  /**
   * Where a long run's narration stops: the events kept reached the cap, and nothing after is.
   *
   * @param count the events kept before it
   */
  record Stopped(int count) implements RunEvent {}

  /**
   * One turn of a loop that was kept.
   *
   * @param index the turn, from 1
   * @param value the loop variable's value, or null for a while loop
   * @param events what the turn did
   */
  record Turn(int index, Expr value, List<RunEvent> events) {

    /** Copies the events. */
    public Turn {
      events = List.copyOf(events);
    }
  }

  /**
   * A loop: its turns, the first two and the last kept, and the value its variable had in the last
   * of the turns between them.
   *
   * @param line the loop's line, from 1
   * @param variable the loop variable, or null for a while loop
   * @param count how many turns it took
   * @param turns the turns kept, in order
   * @param folded the variable's value in the last turn not kept, or null
   */
  record Loop(int line, Symbol variable, int count, List<Turn> turns, Expr folded)
      implements RunEvent {

    /** Copies the turns. */
    public Loop {
      turns = List.copyOf(turns);
    }
  }
}
