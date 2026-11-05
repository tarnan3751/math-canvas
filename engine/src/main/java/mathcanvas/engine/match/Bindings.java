package mathcanvas.engine.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

/**
 * The values of a match's pattern variables, an immutable map from name to expression sorted by
 * name, so two bindings with the same values are equal whatever the order of binding.
 *
 * <p>A sequence variable keeps the operands it matched and where they came from; {@link #get} gives
 * their combination: the canonical sum or product of the terms or factors (the single operand
 * alone, {@code 0} or {@code 1} for none), or a {@link ListExpr} of arguments.
 *
 * @param entries the bindings, sorted by name, one per name
 */
public record Bindings(List<Binding> entries) {

  /** No bindings. */
  public static final Bindings EMPTY = new Bindings(List.of());

  /** Where a sequence variable's operands came from. */
  public enum Origin {
    /** Terms of a sum. */
    SUM,
    /** Factors of a product. */
    PRODUCT,
    /** Arguments of an application. */
    ARGS
  }

  /**
   * One variable's binding.
   *
   * @param name the variable's name, with its underscores
   * @param value the value; for a sequence, the combination of its operands
   * @param sequence a sequence's operands, or null for a pattern variable
   * @param origin where a sequence's operands came from, or null for a pattern variable
   */
  public record Binding(String name, Expr value, List<Expr> sequence, Origin origin) {

    /**
     * Checks the binding.
     *
     * @throws NullPointerException if the name or the value is null
     * @throws IllegalArgumentException if only one of {@code sequence} and {@code origin} is given
     */
    public Binding {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(value, "value");
      if ((sequence == null) != (origin == null)) {
        throw new IllegalArgumentException("a sequence binding needs its operands and origin");
      }
      sequence = sequence == null ? null : List.copyOf(sequence);
    }
  }

  /**
   * Copies the entries.
   *
   * @throws IllegalArgumentException if they are not sorted by name with one per name
   */
  public Bindings {
    entries = List.copyOf(entries);
    for (int i = 1; i < entries.size(); i++) {
      if (entries.get(i - 1).name().compareTo(entries.get(i).name()) >= 0) {
        throw new IllegalArgumentException("bindings must be sorted by name, one per name");
      }
    }
  }

  /**
   * Returns the combined value of a sequence's operands.
   *
   * @param operands the operands
   * @param origin where they came from
   * @return their sum, product or list; an ordered subset of a canonical sum or product is
   *     canonical, so nothing is recomputed
   */
  public static Expr combine(List<Expr> operands, Origin origin) {
    return switch (origin) {
      case SUM ->
          operands.isEmpty()
              ? Num.ZERO
              : operands.size() == 1 ? operands.getFirst() : new Sum(operands);
      case PRODUCT ->
          operands.isEmpty()
              ? Num.ONE
              : operands.size() == 1 ? operands.getFirst() : new Product(operands);
      case ARGS -> new ListExpr(operands);
    };
  }

  /**
   * Whether {@code name} is bound.
   *
   * @param name the variable's name
   * @return whether it is bound
   */
  public boolean contains(String name) {
    return find(name) != null;
  }

  /**
   * Returns the value of {@code name}; for a sequence, its combination.
   *
   * @param name the variable's name
   * @return its value
   * @throws IllegalArgumentException if it is not bound
   */
  public Expr get(String name) {
    return binding(name).value();
  }

  /**
   * Returns the operands a sequence variable matched.
   *
   * @param name the sequence variable's name
   * @return its operands
   * @throws IllegalArgumentException if it is not bound, or is not a sequence variable
   */
  public List<Expr> sequence(String name) {
    Binding b = binding(name);
    if (b.sequence() == null) {
      throw new IllegalArgumentException(name + " is not a sequence variable");
    }
    return b.sequence();
  }

  /**
   * Returns the bound names, sorted.
   *
   * @return the names
   */
  public List<String> names() {
    return entries.stream().map(Binding::name).toList();
  }

  /**
   * Returns these bindings with {@code b} added.
   *
   * @param b the new binding
   * @return the bindings with it
   * @throws IllegalArgumentException if its name is already bound
   */
  public Bindings with(Binding b) {
    List<Binding> out = new ArrayList<>(entries.size() + 1);
    int i = 0;
    while (i < entries.size() && entries.get(i).name().compareTo(b.name()) < 0) {
      out.add(entries.get(i++));
    }
    if (i < entries.size() && entries.get(i).name().equals(b.name())) {
      throw new IllegalArgumentException(b.name() + " is already bound");
    }
    out.add(b);
    while (i < entries.size()) {
      out.add(entries.get(i++));
    }
    return new Bindings(out);
  }

  /**
   * Instantiates {@code template}: each symbol whose name ends in {@code _} is replaced by its
   * value. A sequence variable that is a direct term of a sum, factor of a product or argument of
   * an application splices its operands in when they came from the same kind (terms into a sum,
   * factors into a product, arguments into an argument list); anywhere else it stands for its
   * combined value. A sum or product left with one operand is that operand, with none {@code 0} or
   * {@code 1}. The result is raw.
   *
   * @param template the template
   * @return the instance
   * @throws IllegalArgumentException if a template variable is not bound, or a bound position or an
   *     application's head gets something other than a symbol
   */
  public Expr substituteInto(Expr template) {
    return switch (template) {
      case Symbol s -> Pattern.isVariable(s.name()) ? get(s.name()) : s;
      case Sum s -> {
        List<Expr> terms = spliced(s.terms(), Origin.SUM);
        yield combine(terms, Origin.SUM);
      }
      case Product p -> {
        List<Expr> factors = spliced(p.factors(), Origin.PRODUCT);
        yield combine(factors, Origin.PRODUCT);
      }
      case Apply a -> {
        Symbol head = a.head();
        if (Pattern.isVariable(head.name())) {
          if (!(get(head.name()) instanceof Symbol h)) {
            throw new IllegalArgumentException(
                head.name() + " is the head of an application and needs a symbol");
          }
          head = h;
        }
        yield new Apply(head, spliced(a.args(), Origin.ARGS));
      }
      case Num _,
          mathcanvas.engine.expr.Based _,
          Constant _,
          Bool _,
          mathcanvas.engine.expr.Unit _ ->
          template;
      case Power _,
          Lambda _,
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
          BigProduct _ -> {
        List<Expr> operands = Operands.of(template);
        List<Expr> out = new ArrayList<>(operands.size());
        for (Expr o : operands) {
          out.add(substituteInto(o));
        }
        yield Operands.rebuild(template, out);
      }
    };
  }

  private List<Expr> spliced(List<Expr> operands, Origin origin) {
    List<Expr> out = new ArrayList<>(operands.size());
    for (Expr o : operands) {
      if (o instanceof Symbol s && Pattern.isSequence(s.name())) {
        Binding b = binding(s.name());
        if (b.origin() == origin) {
          out.addAll(b.sequence());
          continue;
        }
      }
      out.add(substituteInto(o));
    }
    return out;
  }

  private Binding find(String name) {
    for (Binding b : entries) {
      if (b.name().equals(name)) {
        return b;
      }
    }
    return null;
  }

  private Binding binding(String name) {
    Binding b = find(name);
    if (b == null) {
      throw new IllegalArgumentException(name + " is not bound");
    }
    return b;
  }
}
