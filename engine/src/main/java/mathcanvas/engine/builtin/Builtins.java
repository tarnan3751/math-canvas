package mathcanvas.engine.builtin;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import mathcanvas.engine.builtin.Builtin.EiBinding;
import mathcanvas.engine.builtin.Builtin.Kind;

/**
 * The reserved names of {@code docs/language.md} §3.3 and the namespace of §12: one source of truth
 * for the parser and the evaluator. Immutable.
 */
public final class Builtins {

  private static final Map<String, Builtin> BY_NAME;
  private static final Map<String, String> ALIASES;

  private static final Set<String> CONSTANTS =
      Set.of("pi", "e", "i", "inf", "infinity", "true", "false", "undefined");
  private static final Set<String> DOMAINS = Set.of("Integers", "Rationals", "Reals", "Complexes");
  private static final Set<String> KEYWORDS =
      Set.of("and", "or", "not", "if", "then", "else", "for", "while", "function", "return");

  private static final List<String> TRIG = List.of("sin", "cos", "tan", "cot", "sec", "csc");

  static {
    Map<String, Builtin> names = new TreeMap<>();
    // Functions (§12.1).
    for (String name :
        List.of(
            "abs",
            "sign",
            "floor",
            "ceil",
            "trunc",
            "round",
            "factorial",
            "re",
            "im",
            "conj",
            "arg",
            "numerator",
            "denominator",
            "length")) {
      function(names, name, false, List.of(1), false);
    }
    for (String name : List.of("mod", "binomial", "root", "atan2", "nth", "dot", "cross")) {
      function(names, name, false, List.of(2), false);
    }
    function(names, "gcd", false, List.of(2), true);
    function(names, "lcm", false, List.of(2), true);
    function(names, "min", false, List.of(1), true);
    function(names, "max", false, List.of(1), true);
    for (String name : List.of("sqrt", "cbrt", "exp", "ln", "log10", "log2")) {
      function(names, name, true, List.of(1), false);
    }
    function(names, "log", true, List.of(1, 2), false);
    for (String trig : TRIG) {
      for (String name : List.of(trig, "a" + trig, trig + "h", "a" + trig + "h")) {
        function(names, name, true, List.of(1), false);
      }
    }
    // Commands (§12.2).
    command(names, "solve", List.of(2), false, Set.of(1, 2), false, 2, EiBinding.YES);
    command(names, "diff", List.of(2, 3), false, Set.of(1, 2), false, 2, EiBinding.NO);
    command(
        names, "integrate", List.of(2, 4), false, Set.of(1, 2), false, 2, EiBinding.WITH_BOUNDS);
    command(names, "limit", List.of(3, 4), false, Set.of(1, 2, 4), false, 2, EiBinding.YES);
    // The applications of integration and the optimisation helpers (§14.15): area(f, g, x, a, b)
    // binds its third argument.
    for (String name : List.of("arclength")) {
      command(names, name, List.of(4), false, Set.of(1, 2), false, 2, EiBinding.YES);
    }
    for (String name : List.of("area")) {
      command(
          names, name, List.of(4, 5), false, Set.of(1, 2), false, 2, EiBinding.YES, Map.of(5, 3));
    }
    command(names, "sum", List.of(4), false, Set.of(1, 2), false, 2, EiBinding.YES);
    command(names, "product", List.of(4), false, Set.of(1, 2), false, 2, EiBinding.YES);
    command(names, "taylor", List.of(4), false, Set.of(1, 2), false, 2, EiBinding.NO);
    for (String name : List.of("expand", "factor", "cancel", "simplify", "exact")) {
      command(names, name, List.of(1), false, Set.of(), false, 0, EiBinding.NO);
    }
    command(names, "collect", List.of(2), false, Set.of(2), false, 0, EiBinding.NO);
    command(names, "apart", List.of(2), false, Set.of(2), false, 0, EiBinding.NO);
    command(names, "subs", List.of(2), true, Set.of(), true, 0, EiBinding.NO);
    command(names, "assume", List.of(1), true, Set.of(), true, 0, EiBinding.NO);
    command(names, "assumptions", List.of(0), false, Set.of(), false, 0, EiBinding.NO);
    command(names, "numeric", List.of(1, 2), false, Set.of(), false, 0, EiBinding.NO);
    for (String name : List.of("steps", "clear", "help")) {
      command(names, name, List.of(1), false, Set.of(1), false, 0, EiBinding.NO);
    }
    // Reserved later-phase names (§12.3).
    for (String name : List.of("det", "rref", "eigenvalues", "grad")) {
      reserved(names, name, Kind.RESERVED, List.of(1));
    }
    reserved(names, "slider", Kind.RESERVED, List.of(3));
    // The notation word (§10.3).
    reserved(names, "lim", Kind.NOTATION, List.of(0));
    BY_NAME = Collections.unmodifiableMap(names);

    Map<String, String> aliases = new TreeMap<>();
    for (String trig : TRIG) {
      aliases.put("arc" + trig, "a" + trig);
    }
    ALIASES = Collections.unmodifiableMap(aliases);
  }

  private Builtins() {}

  private static void function(
      Map<String, Builtin> names,
      String name,
      boolean prefix,
      List<Integer> arities,
      boolean variadic) {
    names.put(
        name,
        new Builtin(
            name,
            Kind.FUNCTION,
            arities,
            variadic,
            prefix,
            Set.of(),
            false,
            OptionalInt.empty(),
            EiBinding.NO,
            Map.of()));
  }

  private static void command(
      Map<String, Builtin> names,
      String name,
      List<Integer> arities,
      boolean variadic,
      Set<Integer> held,
      boolean holdsAll,
      int binder,
      EiBinding eiBinding) {
    command(names, name, arities, variadic, held, holdsAll, binder, eiBinding, Map.of());
  }

  private static void command(
      Map<String, Builtin> names,
      String name,
      List<Integer> arities,
      boolean variadic,
      Set<Integer> held,
      boolean holdsAll,
      int binder,
      EiBinding eiBinding,
      Map<Integer, Integer> binderByArity) {
    names.put(
        name,
        new Builtin(
            name,
            Kind.COMMAND,
            arities,
            variadic,
            false,
            held,
            holdsAll,
            binder == 0 ? OptionalInt.empty() : OptionalInt.of(binder),
            eiBinding,
            binderByArity));
  }

  private static void reserved(
      Map<String, Builtin> names, String name, Kind kind, List<Integer> arities) {
    names.put(
        name,
        new Builtin(
            name,
            kind,
            arities,
            false,
            false,
            Set.of(),
            false,
            OptionalInt.empty(),
            EiBinding.NO,
            Map.of()));
  }

  /**
   * Looks a name up, resolving the {@code arc…} aliases ({@code arcsin} gives {@code asin}).
   *
   * @param name the name as written
   * @return the builtin, or empty when the name is not one
   */
  public static Optional<Builtin> lookup(String name) {
    return Optional.ofNullable(BY_NAME.get(ALIASES.getOrDefault(name, name)));
  }

  /**
   * Every builtin, in name order, aliases excluded.
   *
   * @return the builtins
   */
  public static List<Builtin> all() {
    return List.copyOf(BY_NAME.values());
  }

  /**
   * Whether a name is in the prefix set (§7.2).
   *
   * @param name the name
   * @return true if it may be applied without parentheses
   */
  public static boolean isPrefix(String name) {
    return lookup(name).map(Builtin::prefix).orElse(false);
  }

  /**
   * Whether a name is a builtin function, command or reserved name, which {@code name(} calls.
   *
   * @param name the name
   * @return true if the name is callable
   */
  public static boolean isCallable(String name) {
    return lookup(name).map(Builtin::isCallable).orElse(false);
  }

  /**
   * Whether a name is a reserved constant: {@code pi e i inf infinity true false undefined}.
   *
   * @param name the name
   * @return true for a constant
   */
  public static boolean isConstant(String name) {
    return CONSTANTS.contains(name);
  }

  /**
   * Whether a name is the ASCII spelling of a domain: {@code Integers Rationals Reals Complexes}.
   *
   * @param name the name
   * @return true for a domain
   */
  public static boolean isDomain(String name) {
    return DOMAINS.contains(name);
  }

  /**
   * Whether a name is a keyword reserved for Phase 9.
   *
   * @param name the name
   * @return true for a keyword
   */
  public static boolean isKeyword(String name) {
    return KEYWORDS.contains(name);
  }
}
