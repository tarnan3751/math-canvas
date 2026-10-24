package mathcanvas.engine.builtin;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * One name of the Phase 1 namespace ({@code docs/language.md} §12): a function, a command, a
 * reserved later-phase name, or the notation word {@code lim}.
 *
 * @param name the name a call is built with ({@code asin} for the alias {@code arcsin})
 * @param kind what the name is
 * @param arities the argument counts accepted, ascending
 * @param variadic whether any count above the largest of {@code arities} is accepted too
 * @param prefix whether the name may be applied without parentheses ({@code sin x}, §7.2)
 * @param held the 1-based argument positions passed to the command as parsed
 * @param holdsAll whether every argument is held ({@code subs}, {@code assume})
 * @param binder the 1-based position of the argument naming a bound variable, if any
 * @param eiBinding whether {@code e} and {@code i} may be that bound variable (§11.2)
 */
public record Builtin(
    String name,
    Kind kind,
    List<Integer> arities,
    boolean variadic,
    boolean prefix,
    Set<Integer> held,
    boolean holdsAll,
    OptionalInt binder,
    EiBinding eiBinding) {

  /** What a builtin name is. */
  public enum Kind {
    /** Evaluates its arguments, then itself when they permit. */
    FUNCTION,
    /** Declares held argument positions. */
    COMMAND,
    /** A later-phase name: parses as a call and fails on evaluation. */
    RESERVED,
    /** A notation word ({@code lim}): never called. */
    NOTATION
  }

  /** Whether {@code e} and {@code i} may be a command's bound variable. */
  public enum EiBinding {
    /** Never: the binding could escape into the result. */
    NO,
    /** Always. */
    YES,
    /** Only when the call has bounds ({@code integrate} with four arguments). */
    WITH_BOUNDS
  }

  /**
   * Checks and copies the operands.
   *
   * @throws NullPointerException if an operand is null
   * @throws IllegalArgumentException if {@code arities} is empty
   */
  public Builtin {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(kind, "kind");
    arities = List.copyOf(arities);
    held = Set.copyOf(held);
    Objects.requireNonNull(binder, "binder");
    Objects.requireNonNull(eiBinding, "eiBinding");
    if (arities.isEmpty()) {
      throw new IllegalArgumentException("a builtin needs an arity: " + name);
    }
  }

  /**
   * Whether a call may have {@code count} arguments.
   *
   * @param count the number of arguments
   * @return true if the count is accepted
   */
  public boolean accepts(int count) {
    return arities.contains(count) || (variadic && count > arities.getLast());
  }

  /**
   * The accepted counts in words: {@code "1 argument"}, {@code "2 or 4 arguments"}, {@code "2 or
   * more arguments"}, {@code "no arguments"}.
   *
   * @return the description
   */
  public String arityText() {
    if (variadic) {
      return arities.getFirst() + " or more arguments";
    }
    if (arities.size() == 1) {
      int n = arities.getFirst();
      return n == 0 ? "no arguments" : n + (n == 1 ? " argument" : " arguments");
    }
    StringBuilder text = new StringBuilder();
    for (int k = 0; k < arities.size(); k++) {
      if (k > 0) {
        text.append(k == arities.size() - 1 ? " or " : ", ");
      }
      text.append(arities.get(k));
    }
    return text.append(" arguments").toString();
  }

  /**
   * Whether the argument at a 1-based position is held.
   *
   * @param position the position, from 1
   * @return true if the command receives it as parsed
   */
  public boolean isHeld(int position) {
    return holdsAll || held.contains(position);
  }

  /**
   * Whether the name can be called with {@code name(…)}.
   *
   * @return false only for a notation word
   */
  public boolean isCallable() {
    return kind != Kind.NOTATION;
  }
}
