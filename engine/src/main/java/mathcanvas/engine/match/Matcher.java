package mathcanvas.engine.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprOrder;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.match.Bindings.Binding;
import mathcanvas.engine.match.Bindings.Origin;
import mathcanvas.engine.match.Pattern.Condition;
import mathcanvas.engine.match.PatternNode.ApplyP;
import mathcanvas.engine.match.PatternNode.Literal;
import mathcanvas.engine.match.PatternNode.PowerP;
import mathcanvas.engine.match.PatternNode.ProductP;
import mathcanvas.engine.match.PatternNode.Seq;
import mathcanvas.engine.match.PatternNode.Structural;
import mathcanvas.engine.match.PatternNode.SumP;
import mathcanvas.engine.match.PatternNode.Var;

/**
 * Matches patterns against canonical expressions: a backtracking enumeration, lazy, in a
 * deterministic order.
 *
 * <p>A sum or product pattern matches commutatively. The subject's terms or factors (a subject of
 * another kind counts as its only operand) are the candidates; each pattern operand, left to right,
 * takes a distinct candidate, trying them in canonical order and then, for an optional variable,
 * its default; the sequence variable takes what is left, and with no sequence variable nothing may
 * be left. In a product with two or more factors that do not commute, those factors are not
 * candidates: only the sequence variable may take them, together and in order. A literal operand,
 * or a variable already bound, has one possible candidate and is located directly; every other
 * operand is tried only on candidates of a kind it can match; and the count of candidates left is
 * checked against the operands left before each step. A condition is checked as soon as the
 * variables it names are bound. A power pattern whose exponent has a default matches the power's
 * base and exponent first, then the whole subject with the default exponent. Application arguments
 * and the operands of every other kind match positionally; a sequence variable among arguments
 * tries its shortest length first.
 */
public final class Matcher {

  private final Pattern pattern;
  private final Assumptions assumptions;
  private final TreeMap<String, List<Condition>> byVariable = new TreeMap<>();
  private final List<Condition> atEnd = new ArrayList<>();

  private Matcher(Pattern pattern, Assumptions assumptions) {
    this.pattern = pattern;
    this.assumptions = assumptions;
    for (Condition c : pattern.conditions()) {
      if (c.variables().isEmpty()) {
        atEnd.add(c);
      }
      for (String v : c.variables()) {
        byVariable.computeIfAbsent(v, k -> new ArrayList<>()).add(c);
      }
    }
  }

  /**
   * Returns the first match of {@code pattern} against {@code subject} with no assumptions.
   *
   * @param pattern the pattern
   * @param subject the canonical subject
   * @return the bindings of the first match, or empty
   */
  public static Optional<Bindings> match(Pattern pattern, Expr subject) {
    return match(pattern, subject, Assumptions.EMPTY);
  }

  /**
   * Returns the first match of {@code pattern} against {@code subject}.
   *
   * @param pattern the pattern
   * @param subject the canonical subject
   * @param assumptions the assumptions the conditions see
   * @return the bindings of the first match, or empty
   */
  public static Optional<Bindings> match(Pattern pattern, Expr subject, Assumptions assumptions) {
    Iterator<Bindings> it = new Matcher(pattern, assumptions).top(subject);
    return it.hasNext() ? Optional.of(it.next()) : Optional.empty();
  }

  /**
   * Returns every match of {@code pattern} against {@code subject} with no assumptions.
   *
   * @param pattern the pattern
   * @param subject the canonical subject
   * @return the matches, lazily, in the matcher's order
   */
  public static Stream<Bindings> all(Pattern pattern, Expr subject) {
    return all(pattern, subject, Assumptions.EMPTY);
  }

  /**
   * Returns every match of {@code pattern} against {@code subject}.
   *
   * @param pattern the pattern
   * @param subject the canonical subject
   * @param assumptions the assumptions the conditions see
   * @return the matches, lazily, in the matcher's order
   */
  public static Stream<Bindings> all(Pattern pattern, Expr subject, Assumptions assumptions) {
    Iterator<Bindings> it = new Matcher(pattern, assumptions).top(subject);
    return StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED | Spliterator.NONNULL), false);
  }

  private Iterator<Bindings> top(Expr subject) {
    Iterator<Bindings> matches = match(pattern.root(), subject, Bindings.EMPTY);
    return flatMap(
        matches, b -> one(atEnd.stream().allMatch(c -> c.test().test(b, assumptions)) ? b : null));
  }

  // Binding.

  private Bindings bind(String name, Expr value, Bindings b) {
    return bind(new Binding(name, value, null, null), b);
  }

  private Bindings bind(Binding binding, Bindings b) {
    if (b.contains(binding.name())) {
      return b.get(binding.name()).equals(binding.value()) ? b : null;
    }
    Bindings out = b.with(binding);
    for (Condition c : byVariable.getOrDefault(binding.name(), List.of())) {
      if (c.variables().stream().allMatch(out::contains) && !c.test().test(out, assumptions)) {
        return null;
      }
    }
    return out;
  }

  private boolean optional(PatternNode n) {
    return n instanceof Var v && pattern.defaults().containsKey(v.name());
  }

  // Matching one node.

  private Iterator<Bindings> match(PatternNode p, Expr s, Bindings b) {
    return switch (p) {
      case Literal l -> l.expr().equals(s) ? one(b) : empty();
      case Var v -> one(bind(v.name(), s, b));
      case Seq q -> throw new IllegalStateException("a sequence variable outside its place: " + q);
      case PowerP pw -> power(pw, s, b);
      case ApplyP a -> {
        if (!(s instanceof Apply sa)) {
          yield empty();
        }
        Iterator<Bindings> head = match(a.head(), sa.head(), b);
        yield flatMap(head, b1 -> args(a.args(), 0, sa.args(), 0, b1));
      }
      case Structural st ->
          Operands.sameShape(st.shape(), s)
              ? positional(st.operands(), Operands.of(s), 0, b)
              : empty();
      case SumP sp -> nary(sp.operands(), sp.rest(), Origin.SUM, s, b);
      case ProductP pp -> nary(pp.operands(), pp.rest(), Origin.PRODUCT, s, b);
    };
  }

  private Iterator<Bindings> power(PowerP pw, Expr s, Bindings b) {
    Iterator<Bindings> structural =
        s instanceof Power sp
            ? flatMap(match(pw.base(), sp.base(), b), b1 -> match(pw.exponent(), sp.exponent(), b1))
            : empty();
    if (!optional(pw.exponent())) {
      return structural;
    }
    String n = ((Var) pw.exponent()).name();
    return concat(
        structural,
        () -> flatMap(match(pw.base(), s, b), b1 -> one(bind(n, pattern.defaults().get(n), b1))));
  }

  private Iterator<Bindings> positional(
      List<PatternNode> ops, List<Expr> subjects, int i, Bindings b) {
    if (i == ops.size()) {
      return one(b);
    }
    return flatMap(
        match(ops.get(i), subjects.get(i), b), b1 -> positional(ops, subjects, i + 1, b1));
  }

  private Iterator<Bindings> args(
      List<PatternNode> pats, int i, List<Expr> subjects, int j, Bindings b) {
    if (i == pats.size()) {
      return j == subjects.size() ? one(b) : empty();
    }
    PatternNode p = pats.get(i);
    if (p instanceof Seq q) {
      int needed = 0;
      for (int k = i + 1; k < pats.size(); k++) {
        needed += pats.get(k) instanceof Seq s2 && s2.allowEmpty() ? 0 : 1;
      }
      int min = q.allowEmpty() ? 0 : 1;
      int max = subjects.size() - j - needed;
      return indexed(
          min,
          max + 1,
          len -> {
            List<Expr> taken = subjects.subList(j, j + len);
            Binding binding =
                new Binding(q.name(), new ListExpr(taken), List.copyOf(taken), Origin.ARGS);
            return flatMap(one(bind(binding, b)), b1 -> args(pats, i + 1, subjects, j + len, b1));
          });
    }
    if (j >= subjects.size()) {
      return empty();
    }
    return flatMap(match(p, subjects.get(j), b), b1 -> args(pats, i + 1, subjects, j + 1, b1));
  }

  // Commutative matching.

  /** One commutative match in progress: its candidates, operands in processing order, rest. */
  private record Nary(
      List<Expr> candidates,
      int sorted,
      List<Expr> forced,
      List<PatternNode> ops,
      int[] requiredFrom,
      Seq rest,
      Origin origin) {}

  private Iterator<Bindings> nary(
      List<PatternNode> operands, Seq rest, Origin origin, Expr s, Bindings b) {
    List<Expr> all;
    if (origin == Origin.SUM) {
      all = s instanceof Sum sum ? sum.terms() : List.of(s);
    } else {
      all = s instanceof Product product ? product.factors() : List.of(s);
    }
    List<Expr> candidates = all;
    List<Expr> forced = List.of();
    int sorted = all.size();
    if (origin == Origin.PRODUCT) {
      int commuting = 0;
      while (commuting < all.size() && Canonical.commutes(all.get(commuting))) {
        commuting++;
      }
      sorted = commuting;
      if (all.size() - commuting >= 2) {
        candidates = all.subList(0, commuting);
        forced = all.subList(commuting, all.size());
        if (rest == null) {
          return empty();
        }
      }
    }
    // Literal operands first: each has one possible candidate, so the order of matches is kept.
    List<PatternNode> ops = new ArrayList<>();
    for (PatternNode o : operands) {
      if (o instanceof Literal) {
        ops.add(o);
      }
    }
    for (PatternNode o : operands) {
      if (!(o instanceof Literal)) {
        ops.add(o);
      }
    }
    int[] requiredFrom = new int[ops.size() + 1];
    for (int k = ops.size() - 1; k >= 0; k--) {
      requiredFrom[k] = requiredFrom[k + 1] + (optional(ops.get(k)) ? 0 : 1);
    }
    int m = candidates.size();
    if (rest == null) {
      if (m < requiredFrom[0] || m > ops.size()) {
        return empty();
      }
    } else {
      int minRest = rest.allowEmpty() || !forced.isEmpty() ? 0 : 1;
      if (m < requiredFrom[0] + minRest) {
        return empty();
      }
    }
    Nary n = new Nary(candidates, sorted, forced, ops, requiredFrom, rest, origin);
    return assign(n, 0, new int[0], b);
  }

  private Iterator<Bindings> assign(Nary n, int k, int[] used, Bindings b) {
    if (k == n.ops().size()) {
      return finish(n, used, b);
    }
    if (n.candidates().size() - used.length < n.requiredFrom()[k]) {
      return empty();
    }
    PatternNode op = n.ops().get(k);
    Expr known = null;
    if (op instanceof Literal l) {
      known = l.expr();
    } else if (op instanceof Var v && b.contains(v.name())) {
      known = b.get(v.name());
    }
    Iterator<Bindings> real;
    if (known != null) {
      int i = locate(n, known);
      real = i >= 0 && !isUsed(used, i) ? assign(n, k + 1, plus(used, i), b) : empty();
    } else {
      real =
          indexed(
              0,
              n.candidates().size(),
              i -> {
                Expr c = n.candidates().get(i);
                if (isUsed(used, i) || !canMatch(op, c)) {
                  return empty();
                }
                return flatMap(match(op, c, b), b1 -> assign(n, k + 1, plus(used, i), b1));
              });
    }
    if (!optional(op) || known != null) {
      return real;
    }
    String name = ((Var) op).name();
    return concat(
        real,
        () ->
            flatMap(
                one(bind(name, pattern.defaults().get(name), b)),
                b1 -> assign(n, k + 1, used, b1)));
  }

  private Iterator<Bindings> finish(Nary n, int[] used, Bindings b) {
    // Count first: most failed assignments end here without building anything.
    int left = n.candidates().size() - used.length + n.forced().size();
    if (n.rest() == null) {
      return left == 0 ? one(b) : empty();
    }
    if (left == 0 && !n.rest().allowEmpty()) {
      return empty();
    }
    // The candidates not used, in order, copied a run at a time, then the forced factors.
    int[] sorted = used.clone();
    Arrays.sort(sorted);
    List<Expr> operands = new ArrayList<>(left);
    int from = 0;
    for (int u : sorted) {
      operands.addAll(n.candidates().subList(from, u));
      from = u + 1;
    }
    operands.addAll(n.candidates().subList(from, n.candidates().size()));
    operands.addAll(n.forced());
    List<Expr> leftover = List.copyOf(operands);
    Expr value = Bindings.combine(leftover, n.origin());
    return one(bind(new Binding(n.rest().name(), value, leftover, n.origin()), b));
  }

  /** The index of {@code e} among the candidates, or -1. */
  private static int locate(Nary n, Expr e) {
    int i = Collections.binarySearch(n.candidates().subList(0, n.sorted()), e, ExprOrder.INSTANCE);
    if (i >= 0) {
      return i;
    }
    for (int j = n.sorted(); j < n.candidates().size(); j++) {
      if (n.candidates().get(j).equals(e)) {
        return j;
      }
    }
    return -1;
  }

  /** Whether {@code op} can match a subject of {@code s}'s kind, before trying it. */
  private boolean canMatch(PatternNode op, Expr s) {
    return switch (op) {
      case Literal l -> l.expr().equals(s);
      case Var _ -> true;
      case Seq _ -> false;
      case PowerP p -> s instanceof Power || optional(p.exponent());
      case ApplyP a -> {
        if (!(s instanceof Apply sa)) {
          yield false;
        }
        if (a.head() instanceof Literal h && !h.expr().equals(sa.head())) {
          yield false;
        }
        long fixed = a.args().stream().filter(x -> !(x instanceof Seq)).count();
        yield fixed == a.args().size() ? sa.args().size() == fixed : sa.args().size() >= fixed;
      }
      case Structural st -> Operands.sameShape(st.shape(), s);
      case SumP sp -> s instanceof Sum || required(sp.operands(), sp.rest()) <= 1;
      case ProductP pp -> s instanceof Product || required(pp.operands(), pp.rest()) <= 1;
    };
  }

  private int required(List<PatternNode> ops, Seq rest) {
    int r = rest != null && !rest.allowEmpty() ? 1 : 0;
    for (PatternNode o : ops) {
      r += optional(o) ? 0 : 1;
    }
    return r;
  }

  private static boolean isUsed(int[] used, int i) {
    for (int u : used) {
      if (u == i) {
        return true;
      }
    }
    return false;
  }

  private static int[] plus(int[] used, int i) {
    int[] out = new int[used.length + 1];
    System.arraycopy(used, 0, out, 0, used.length);
    out[used.length] = i;
    return out;
  }

  // Lazy iterators.

  private static Iterator<Bindings> empty() {
    return Collections.emptyIterator();
  }

  private static Iterator<Bindings> one(Bindings b) {
    return b == null ? empty() : List.of(b).iterator();
  }

  private static Iterator<Bindings> flatMap(
      Iterator<Bindings> source, Function<Bindings, Iterator<Bindings>> f) {
    return new Lazy() {
      private Iterator<Bindings> current = empty();

      @Override
      Bindings advance() {
        while (!current.hasNext()) {
          if (!source.hasNext()) {
            return null;
          }
          current = f.apply(source.next());
        }
        return current.next();
      }
    };
  }

  private static Iterator<Bindings> concat(
      Iterator<Bindings> first, Supplier<Iterator<Bindings>> second) {
    return new Lazy() {
      private Iterator<Bindings> rest;

      @Override
      Bindings advance() {
        if (first.hasNext()) {
          return first.next();
        }
        if (rest == null) {
          rest = second.get();
        }
        return rest.hasNext() ? rest.next() : null;
      }
    };
  }

  /** The matches of {@code f(i)} for {@code i} from {@code from} below {@code to}, in order. */
  private static Iterator<Bindings> indexed(int from, int to, IntFunction<Iterator<Bindings>> f) {
    return new Lazy() {
      private int next = from;
      private Iterator<Bindings> current = empty();

      @Override
      Bindings advance() {
        while (!current.hasNext()) {
          if (next >= to) {
            return null;
          }
          current = f.apply(next++);
        }
        return current.next();
      }
    };
  }

  /** An iterator computing its next element on demand; {@link #advance} returns null at the end. */
  private abstract static class Lazy implements Iterator<Bindings> {
    private Bindings next;
    private boolean ready;

    abstract Bindings advance();

    @Override
    public boolean hasNext() {
      if (!ready) {
        next = advance();
        ready = true;
      }
      return next != null;
    }

    @Override
    public Bindings next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      ready = false;
      return next;
    }
  }
}
