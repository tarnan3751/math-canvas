package mathcanvas.engine.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Canonical;
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
import mathcanvas.engine.match.PatternNode.ApplyP;
import mathcanvas.engine.match.PatternNode.Literal;
import mathcanvas.engine.match.PatternNode.PowerP;
import mathcanvas.engine.match.PatternNode.ProductP;
import mathcanvas.engine.match.PatternNode.Seq;
import mathcanvas.engine.match.PatternNode.Structural;
import mathcanvas.engine.match.PatternNode.SumP;
import mathcanvas.engine.match.PatternNode.Var;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;

/**
 * A pattern: a compiled tree, the defaults of its optional variables and its conditions.
 *
 * <p>{@link #of} compiles an expression written in the language, and {@link #parse} a text: a
 * symbol whose name ends in {@code _} is a pattern variable ({@code x_}), in {@code __} a sequence
 * variable matching one or more operands ({@code rest__}), in {@code ___} zero or more ({@code
 * rest___}). The tree is canonicalised first, so its literal parts are in the form the subjects are
 * in; "left to right" below is the order of the canonical pattern. A sequence variable may stand
 * only as a direct operand of a sum or product (at most one there) or as an argument of an
 * application; a sequence variable may not occur twice.
 *
 * <p>{@link #optional} gives a variable a default, used when the variable has no operand to match:
 * the variable must occur once, as a direct operand of a sum or product pattern or as the exponent
 * of a power pattern. {@link #where} attaches conditions, checked as soon as the variables they
 * name are bound.
 *
 * @param root the compiled tree
 * @param defaults the defaults of the optional variables, by name
 * @param conditions the conditions, in the order attached
 */
public record Pattern(
    PatternNode root, SortedMap<String, Expr> defaults, List<Condition> conditions) {

  /**
   * A condition on a match.
   *
   * @param variables the variables it reads, checked once they are all bound; empty to check it
   *     when the match is complete
   * @param test the test, of the bindings and the assumptions
   */
  public record Condition(List<String> variables, BiPredicate<Bindings, Assumptions> test) {

    /**
     * Copies the variables.
     *
     * @throws NullPointerException if an argument is null
     */
    public Condition {
      variables = List.copyOf(variables);
      Objects.requireNonNull(test, "test");
    }
  }

  /**
   * Copies the defaults and conditions.
   *
   * @throws NullPointerException if an argument is null
   */
  public Pattern {
    Objects.requireNonNull(root, "root");
    defaults = Collections.unmodifiableSortedMap(new TreeMap<>(defaults));
    conditions = List.copyOf(conditions);
  }

  /**
   * Compiles {@code e}, canonicalised, into a pattern with no defaults and no conditions.
   *
   * @param e the pattern as an expression
   * @return the pattern
   * @throws IllegalArgumentException if a sequence variable stands where it may not, two stand in
   *     one sum or product, one occurs twice, or a name is only underscores
   */
  public static Pattern of(Expr e) {
    PatternNode root = compile(Canonical.of(e), false);
    List<String> sequences = new ArrayList<>();
    collect(root, new ArrayList<>(), sequences);
    if (sequences.stream().distinct().count() != sequences.size()) {
      throw new IllegalArgumentException("a sequence variable occurs twice: " + sequences);
    }
    return new Pattern(root, new TreeMap<>(), List.of());
  }

  /**
   * Parses {@code source} in the empty environment and compiles it as {@link #of} does. The
   * variables are ordinary identifiers to the lexer ({@code a_ * x_^n_}, {@code log(a_ rest__)}); a
   * name followed by {@code (} is a call only for a builtin, so a variable head ({@code h_(u_)}) is
   * written with {@link #of} on a built tree.
   *
   * @param source the pattern in the language
   * @return the pattern
   * @throws mathcanvas.engine.parse.ParseException if the text is not one expression
   * @throws IllegalArgumentException as {@link #of} does
   */
  public static Pattern parse(String source) {
    return of(Parser.parseExpression(source, Names.NONE));
  }

  /**
   * Whether {@code name} names a pattern or sequence variable: it ends in {@code _}.
   *
   * @param name a symbol's name
   * @return whether it is a variable
   */
  public static boolean isVariable(String name) {
    return name.endsWith("_");
  }

  /**
   * Whether {@code name} names a sequence variable: it ends in {@code __}.
   *
   * @param name a symbol's name
   * @return whether it is a sequence variable
   */
  public static boolean isSequence(String name) {
    return name.endsWith("__");
  }

  /**
   * Returns the names of the pattern's variables, sorted.
   *
   * @return the names
   */
  public SortedSet<String> variables() {
    List<String> vars = new ArrayList<>();
    List<String> seqs = new ArrayList<>();
    collect(root, vars, seqs);
    TreeSet<String> names = new TreeSet<>(vars);
    names.addAll(seqs);
    return Collections.unmodifiableSortedSet(names);
  }

  /**
   * Returns this pattern with a default for {@code name}.
   *
   * @param name the variable
   * @param value its default, canonicalised
   * @return the pattern
   * @throws IllegalArgumentException if {@code name} is not a pattern variable occurring once as a
   *     direct operand of a sum or product pattern or as the exponent of a power pattern
   */
  public Pattern optional(String name, Expr value) {
    List<String> vars = new ArrayList<>();
    collect(root, vars, new ArrayList<>());
    if (vars.stream().filter(name::equals).count() != 1 || !optionalPlace(root, name)) {
      throw new IllegalArgumentException(
          name
              + " must occur once, as an operand of a sum or product or as an exponent, to have"
              + " a default");
    }
    TreeMap<String, Expr> d = new TreeMap<>(defaults);
    d.put(name, Canonical.of(value));
    return new Pattern(root, d, conditions);
  }

  /**
   * Returns this pattern with a condition on one variable, checked as soon as it is bound; a
   * sequence variable's condition sees its combined value.
   *
   * @param name the variable
   * @param test the test of its value
   * @return the pattern
   * @throws IllegalArgumentException if the pattern has no such variable
   */
  public Pattern where(String name, Predicate<Expr> test) {
    Objects.requireNonNull(test, "test");
    return where(List.of(name), (b, a) -> test.test(b.get(name)));
  }

  /**
   * Returns this pattern with a condition on several variables, checked as soon as they are all
   * bound.
   *
   * @param names the variables it reads
   * @param test the test of the bindings and the assumptions
   * @return the pattern
   * @throws IllegalArgumentException if the pattern lacks one of the variables
   */
  public Pattern where(List<String> names, BiPredicate<Bindings, Assumptions> test) {
    SortedSet<String> vars = variables();
    for (String n : names) {
      if (!vars.contains(n)) {
        throw new IllegalArgumentException("the pattern has no variable " + n);
      }
    }
    List<Condition> c = new ArrayList<>(conditions);
    c.add(new Condition(names, test));
    return new Pattern(root, defaults, c);
  }

  /**
   * Returns this pattern with a condition on the whole match, checked when the match is complete.
   *
   * @param test the test of the bindings and the assumptions
   * @return the pattern
   */
  public Pattern where(BiPredicate<Bindings, Assumptions> test) {
    List<Condition> c = new ArrayList<>(conditions);
    c.add(new Condition(List.of(), test));
    return new Pattern(root, defaults, c);
  }

  // Compilation.

  private static PatternNode compile(Expr e, boolean sequenceAllowed) {
    if (e instanceof Symbol s && isVariable(s.name())) {
      return variable(s.name(), sequenceAllowed);
    }
    if (!hasVariable(e)) {
      return new Literal(e);
    }
    return switch (e) {
      case Sum s -> {
        List<PatternNode> ops = new ArrayList<>();
        Seq rest = nary(s.terms(), ops);
        yield new SumP(ops, rest);
      }
      case Product p -> {
        List<PatternNode> ops = new ArrayList<>();
        Seq rest = nary(p.factors(), ops);
        yield new ProductP(ops, rest);
      }
      case Power p -> new PowerP(compile(p.base(), false), compile(p.exponent(), false));
      case Apply a -> {
        PatternNode head =
            isVariable(a.head().name()) ? variable(a.head().name(), false) : new Literal(a.head());
        yield new ApplyP(head, a.args().stream().map(x -> compile(x, true)).toList());
      }
      case Num _, Constant _, Bool _, Symbol _ -> new Literal(e);
      case Lambda _,
          Relation _,
          ListExpr _,
          Vector _,
          SetExpr _,
          Matrix _,
          Interval _,
          Piecewise _,
          Derivative _,
          Integral _,
          Limit _,
          BigSum _,
          BigProduct _ ->
          new Structural(e, Operands.of(e).stream().map(x -> compile(x, false)).toList());
    };
  }

  private static Seq nary(List<Expr> operands, List<PatternNode> out) {
    Seq rest = null;
    for (Expr o : operands) {
      PatternNode n = compile(o, true);
      if (n instanceof Seq s) {
        if (rest != null) {
          throw new IllegalArgumentException(
              "a sum or product pattern takes one sequence variable: "
                  + rest.name()
                  + ", "
                  + s.name());
        }
        rest = s;
      } else {
        out.add(n);
      }
    }
    return rest;
  }

  private static PatternNode variable(String name, boolean sequenceAllowed) {
    if (name.chars().allMatch(c -> c == '_')) {
      throw new IllegalArgumentException("a pattern variable needs a name: " + name);
    }
    if (!isSequence(name)) {
      return new Var(name);
    }
    if (!sequenceAllowed) {
      throw new IllegalArgumentException(
          name + " may stand only in a sum, a product or an argument list");
    }
    return new Seq(name, name.endsWith("___"));
  }

  private static boolean hasVariable(Expr e) {
    if (e instanceof Symbol s) {
      return isVariable(s.name());
    }
    if (e instanceof Apply a && isVariable(a.head().name())) {
      return true;
    }
    for (Expr o : Operands.of(e)) {
      if (hasVariable(o)) {
        return true;
      }
    }
    return false;
  }

  private static void collect(PatternNode n, List<String> vars, List<String> seqs) {
    switch (n) {
      case Literal _ -> {}
      case Var v -> vars.add(v.name());
      case Seq s -> seqs.add(s.name());
      case SumP s -> {
        s.operands().forEach(o -> collect(o, vars, seqs));
        if (s.rest() != null) {
          seqs.add(s.rest().name());
        }
      }
      case ProductP p -> {
        p.operands().forEach(o -> collect(o, vars, seqs));
        if (p.rest() != null) {
          seqs.add(p.rest().name());
        }
      }
      case PowerP p -> {
        collect(p.base(), vars, seqs);
        collect(p.exponent(), vars, seqs);
      }
      case ApplyP a -> {
        collect(a.head(), vars, seqs);
        a.args().forEach(o -> collect(o, vars, seqs));
      }
      case Structural s -> s.operands().forEach(o -> collect(o, vars, seqs));
    }
  }

  /** Whether {@code name} occurs where a default is allowed. */
  private static boolean optionalPlace(PatternNode n, String name) {
    return switch (n) {
      case Literal _, Var _, Seq _ -> false;
      case SumP s ->
          s.operands().contains(new Var(name))
              || s.operands().stream().anyMatch(o -> optionalPlace(o, name));
      case ProductP p ->
          p.operands().contains(new Var(name))
              || p.operands().stream().anyMatch(o -> optionalPlace(o, name));
      case PowerP p ->
          p.exponent().equals(new Var(name))
              || optionalPlace(p.base(), name)
              || optionalPlace(p.exponent(), name);
      case ApplyP a -> a.args().stream().anyMatch(o -> optionalPlace(o, name));
      case Structural s -> s.operands().stream().anyMatch(o -> optionalPlace(o, name));
    };
  }
}
