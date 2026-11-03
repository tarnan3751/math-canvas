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
      Set.of(
          "if",
          "then",
          "else",
          "for",
          "while",
          "function",
          "return",
          "end",
          "do",
          "break",
          "continue",
          "global",
          "step");

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
    // Phase 9a: print(e) is e; as a statement in a program it is printed (§19)
    function(names, "print", false, List.of(1), false);
    // Phase 9a: updating lists, for programs (§19)
    function(names, "append", false, List.of(2), false);
    function(names, "setnth", false, List.of(3), false);
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
    // Phase 8b: the special functions (§12.1)
    for (String name : List.of("erf", "erfc", "gamma")) {
      function(names, name, false, List.of(1), false);
    }
    function(names, "beta", false, List.of(2), false);
    // Phase 8b: descriptive statistics (§14.25), a sample with an optional list of counts
    for (String name :
        List.of(
            "mean",
            "median",
            "mode",
            "variance",
            "pvariance",
            "stdev",
            "pstdev",
            "range",
            "quartiles",
            "iqr",
            "skewness",
            "kurtosis",
            "summary",
            "frequencies",
            "cov",
            "cor")) {
      function(names, name, false, List.of(1, 2), false);
    }
    function(names, "sort", false, List.of(1), false);
    function(names, "cumsum", false, List.of(1), false);
    function(names, "percentile", false, List.of(2, 3), false);
    function(names, "zscore", false, List.of(2, 3), false);
    // Phase 8b: the probability distributions (§14.25), TI's names
    for (String name : List.of("normalpdf", "invnorm")) {
      function(names, name, false, List.of(1, 2, 3), false);
    }
    function(names, "normalcdf", false, List.of(2, 3, 4), false);
    for (String name :
        List.of(
            "tpdf",
            "invt",
            "chi2pdf",
            "invchi2",
            "poissonpdf",
            "poissoncdf",
            "geometpdf",
            "geometcdf",
            "exppdf")) {
      function(names, name, false, List.of(2), false);
    }
    for (String name :
        List.of("tcdf", "chi2cdf", "fpdf", "invf", "binompdf", "binomcdf", "expcdf", "unifpdf")) {
      function(names, name, false, List.of(3), false);
    }
    for (String name : List.of("fcdf", "hypergeompdf", "hypergeomcdf", "unifcdf")) {
      function(names, name, false, List.of(4), false);
    }
    // Phase 8b: regression (§14.25)
    for (String name :
        List.of("linreg", "quadreg", "cubicreg", "quartreg", "expreg", "lnreg", "powreg")) {
      function(names, name, false, List.of(1, 2), false);
    }
    function(names, "polyreg", false, List.of(3), false);
    function(names, "residuals", false, List.of(3), false);
    function(names, "predict", false, List.of(2), false);
    // Phase 8b: hypothesis tests (§14.25); a trailing alternative and pooled are held words
    command(names, "ztest", List.of(3, 4, 5), false, Set.of(), true, 0, EiBinding.NO);
    command(names, "ttest", List.of(2, 3, 4, 5), false, Set.of(), true, 0, EiBinding.NO);
    command(names, "ttest2", List.of(2, 3, 4), false, Set.of(), true, 0, EiBinding.NO);
    command(names, "pairedttest", List.of(1, 2, 3), false, Set.of(), true, 0, EiBinding.NO);
    command(names, "proptest", List.of(3, 4), false, Set.of(), true, 0, EiBinding.NO);
    command(names, "proptest2", List.of(4, 5), false, Set.of(), true, 0, EiBinding.NO);
    function(names, "chi2gof", false, List.of(2), false);
    function(names, "chi2test", false, List.of(1), false);
    function(names, "anova", false, List.of(2), true);
    // Phase 8b: confidence intervals (§14.25); tinterval2 holds the word pooled
    function(names, "zinterval", false, List.of(3, 4), false);
    function(names, "tinterval", false, List.of(2, 4), false);
    function(names, "propinterval", false, List.of(3), false);
    function(names, "propinterval2", false, List.of(5), false);
    command(names, "tinterval2", List.of(3, 4), false, Set.of(), true, 0, EiBinding.NO);
    // numeric linear algebra
    // The numeric decompositions (§12.1, §14.17).
    for (String name : List.of("lu", "qr", "cholesky", "svd", "expm")) {
      function(names, name, false, List.of(1), false);
    }
    function(names, "lstsq", false, List.of(2), false);
    // degrees, minutes and seconds (§14.18)
    function(names, "dms", false, List.of(1), false);
    function(names, "degrees", false, List.of(3), false);
    // shape formulas, coordinate geometry and conics (§14.18)
    for (String name : List.of("polygonarea")) {
      function(names, name, false, List.of(1), false);
    }
    for (String name :
        List.of("distance", "midpoint", "slope", "lineequation", "circleequation", "parabola")) {
      function(names, name, false, List.of(2), false);
    }
    for (String name :
        List.of("trianglearea", "circumradius", "inradius", "ellipse", "hyperbola", "conic")) {
      function(names, name, false, List.of(3), false);
    }
    // integers in other bases (§14.27)
    for (String name : List.of("hex", "bin", "oct")) {
      function(names, name, false, List.of(1), false);
    }
    for (String name : List.of("base", "digits")) {
      function(names, name, false, List.of(2), false);
    }
    // the bitwise functions (§14.27)
    for (String name : List.of("bitnot", "bitcount")) {
      function(names, name, false, List.of(1), false);
    }
    for (String name : List.of("bitand", "bitor", "bitxor", "shiftleft", "shiftright", "bit")) {
      function(names, name, false, List.of(2), false);
    }
    // the time value of money (§14.28)
    for (String name : List.of("pv", "fv", "pmt", "nper")) {
      function(names, name, false, List.of(3, 4, 5), false);
    }
    function(names, "rate", false, List.of(3, 4, 5, 6), false);
    for (String name : List.of("npv", "effective", "nominal")) {
      function(names, name, false, List.of(2), false);
    }
    function(names, "irr", false, List.of(1, 2), false);
    function(names, "amortization", false, List.of(3), false);
    // the solver: each part's name held, as triangle's
    command(names, "tvm", List.of(4, 5), false, Set.of(), true, 0, EiBinding.NO);
    // units (§14.26)
    for (String name : List.of("units", "magnitude")) {
      function(names, name, false, List.of(1), false);
    }
    command(names, "convert", List.of(2), false, Set.of(), false, 0, EiBinding.NO);
    // the triangle solver: each part's name held, as assume's left sides
    command(names, "triangle", List.of(3), false, Set.of(), true, 0, EiBinding.NO);
    // Commands (§12.2).
    command(names, "solve", List.of(2), false, Set.of(1, 2), false, 2, EiBinding.YES);
    command(names, "diff", List.of(2), true, Set.of(1, 2), false, 2, EiBinding.NO);
    command(names, "integrate", List.of(2), true, Set.of(1, 2), false, 2, EiBinding.WITH_BOUNDS);
    command(names, "limit", List.of(3, 4), false, Set.of(1, 2, 4), false, 2, EiBinding.YES);
    // The applications of integration and the optimisation helpers (§14.15): area(f, g, x, a, b)
    // and volume(f, g, x, a, b) bind their third argument.
    for (String name : List.of("arclength", "shellvolume", "maximize", "minimize")) {
      command(names, name, List.of(4), false, Set.of(1, 2), false, 2, EiBinding.YES);
    }
    for (String name : List.of("area", "volume")) {
      command(
          names, name, List.of(4, 5), false, Set.of(1, 2), false, 2, EiBinding.YES, Map.of(5, 3));
    }
    command(names, "extrema", List.of(2), false, Set.of(1, 2), false, 2, EiBinding.YES);
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
    // linear algebra (§14.16): det's method is held, as limit's side is
    for (String name :
        List.of(
            "transpose",
            "inverse",
            "rank",
            "rref",
            "trace",
            "nullspace",
            "colspace",
            "gramschmidt",
            "normalize",
            "identity",
            "size",
            "eigenvalues",
            "eigenvectors",
            "diagonalize")) {
      function(names, name, false, List.of(1), false);
    }
    function(names, "linsolve", false, List.of(2), false);
    function(names, "zeros", false, List.of(2), false);
    function(names, "norm", false, List.of(1, 2), false);
    command(names, "det", List.of(1, 2), false, Set.of(2), false, 0, EiBinding.NO);
    command(names, "charpoly", List.of(2), false, Set.of(2), false, 0, EiBinding.NO);
    // Phase 7b: differential equations (§14.22); the arguments are held, and the second declares
    // the unknown functions for the whole call
    command(names, "dsolve", List.of(2), true, Set.of(), true, 0, EiBinding.NO);
    command(names, "odesolve", List.of(5), true, Set.of(), true, 0, EiBinding.NO);
    // Phase 7a-1: multivariable calculus (§14.20); the variables, a list, are bound as solve's
    for (String name : List.of("grad", "jacobian", "hessian", "divergence", "curl", "laplacian")) {
      command(names, name, List.of(2), false, Set.of(1, 2), false, 2, EiBinding.NO);
    }
    command(names, "lagrange", List.of(3), false, Set.of(1, 2, 3), false, 3, EiBinding.NO);
    // Phase 7a-2: line and surface integrals (§14.20); the field's x, y, z are bound in it
    command(names, "lineintegral", List.of(5), false, Set.of(1, 2, 3), false, 3, EiBinding.YES);
    command(
        names, "surfaceintegral", List.of(4), false, Set.of(1, 2, 3, 4), false, 0, EiBinding.NO);
    // Phase 7a-2: Laplace transforms (§14.21); the result's variable is held and free in it
    for (String name : List.of("laplace", "ilaplace")) {
      command(names, name, List.of(3), false, Set.of(1, 2, 3), false, 2, EiBinding.NO);
    }
    // Phase 8a: recurrences (§14.24), whose second argument declares the unknown as dsolve's does
    command(names, "rsolve", List.of(2), true, Set.of(), true, 0, EiBinding.NO);
    // Phase 8a: number theory (§14.23)
    for (String name :
        List.of(
            "isprime",
            "nextprime",
            "prevprime",
            "ifactor",
            "divisors",
            "numdivisors",
            "sigma",
            "totient",
            "mobius")) {
      function(names, name, false, List.of(1), false);
    }
    function(names, "primes", false, List.of(2), false);
    function(names, "invmod", false, List.of(2), false);
    function(names, "crt", false, List.of(2), false);
    function(names, "powmod", false, List.of(3), false);
    for (String name : List.of("fibonacci", "lucas", "catalan", "bell", "partitions")) {
      function(names, name, false, List.of(1), false);
    }
    for (String name : List.of("permutations", "multinomial", "stirling2")) {
      function(names, name, false, List.of(2), false);
    }
    command(names, "diophantine", List.of(2), false, Set.of(1, 2), false, 2, EiBinding.YES);
    // Phase 8a: the boolean operators (§8.4), whose words are tokens, not callable names
    function(names, "and", false, List.of(2), true);
    function(names, "or", false, List.of(2), true);
    function(names, "xor", false, List.of(2), true);
    function(names, "not", false, List.of(1), false);
    function(names, "implies", false, List.of(2), false);
    // Phase 8a: sets (§14.24)
    for (String name : List.of("union", "intersect", "setdiff", "symdiff", "cartesian")) {
      function(names, name, false, List.of(2), false);
    }
    function(names, "subsets", false, List.of(1), false);
    // the truth table: the expression and the names held, the names bound
    command(names, "truthtable", List.of(2), false, Set.of(1, 2), false, 2, EiBinding.NO);
    // Reserved later-phase names (§12.3).
    reserved(names, "slider", Kind.RESERVED, List.of(3));
    // The notation word (§10.3).
    reserved(names, "lim", Kind.NOTATION, List.of(0));
    BY_NAME = Collections.unmodifiableMap(names);

    Map<String, String> aliases = new TreeMap<>();
    for (String trig : TRIG) {
      aliases.put("arc" + trig, "a" + trig);
    }
    aliases.put("choose", "binomial");
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
