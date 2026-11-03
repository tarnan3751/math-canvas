package mathcanvas.engine.numbertheory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.parse.Brackets;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;

/**
 * The language's number-theory functions ({@code docs/language.md} §14.23): {@link #apply} takes a
 * call whose arguments are evaluated and gives its value, or the call itself when it stays. An
 * argument that is not a number leaves the call; a number that is not an integer where one is
 * needed is an {@code IllegalArgumentException}, which the evaluator reports at the call.
 *
 * <p>{@code ifactor}'s value is a display-only form, the application {@code factored(sign, p₁, e₁,
 * …)}: the canonical form keeps it, the printers write it as the product {@code 2³ · 3² · 5}, and
 * the evaluator puts its integer ({@link #plain}) wherever the value is used, so it is kept only at
 * the top of a statement and in collections ({@code docs/reference/engine.md}, <b>The canonical
 * form</b>).
 */
public final class NumberTheory {

  /** The functions this class evaluates. */
  public static final Set<String> FUNCTIONS =
      Set.of(
          "isprime",
          "nextprime",
          "prevprime",
          "primes",
          "ifactor",
          "divisors",
          "numdivisors",
          "sigma",
          "totient",
          "mobius");

  /** The head of {@code ifactor}'s display-only value. */
  public static final String FACTORED = "factored";

  /** The rule set of {@code ifactor}'s steps. */
  public static final String IFACTOR = "ifactor";

  /** {@code ifactor}'s steps, each narrated by the key {@code ifactor.<step>}. */
  public static final List<String> IFACTOR_STEPS = List.of("factors", "prime", "unfinished");

  /** The most divisors {@code divisors} lists. */
  public static final int MAX_DIVISORS = 100_000;

  private NumberTheory() {}

  /**
   * Evaluates a number-theory call.
   *
   * @param call the call, its arguments evaluated and canonical
   * @param ctx the trace switch and the cancellation
   * @return the value, or the call when it stays, with the steps
   * @throws IllegalArgumentException if an argument is a number of the wrong kind
   */
  public static Rewriter.Result apply(Apply call, Context ctx) {
    String name = call.head().name();
    List<BigInteger> n = new ArrayList<>();
    for (Expr arg : call.args()) {
      if (!(arg instanceof Num num)) {
        return stays(call);
      }
      if (!(num.value() instanceof Int i)) {
        throw new IllegalArgumentException(
            name + (call.args().size() == 1 ? " takes an integer" : " takes integers"));
      }
      n.add(i.value());
    }
    BigInteger x = n.getFirst();
    return switch (name) {
      case "isprime" -> silent(Bool.of(Primes.isPrime(x)));
      case "nextprime" -> silent(num(Primes.next(x, ctx.cancellation())));
      case "prevprime" ->
          silent(
              Primes.previous(x, ctx.cancellation())
                  .<Expr>map(NumberTheory::num)
                  .orElse(Constant.UNDEFINED));
      case "primes" ->
          silent(
              Brackets.of(
                  Primes.between(x, n.get(1), ctx.cancellation()).stream()
                      .<Expr>map(NumberTheory::num)
                      .toList()));
      case "ifactor" -> ifactor(call, x, ctx);
      default -> divisorFunction(call, name, x, ctx);
    };
  }

  private static Num num(BigInteger v) {
    return Num.of(Int.of(v));
  }

  private static Rewriter.Result silent(Expr value) {
    return new Rewriter.Result(value, Trace.NONE);
  }

  private static Rewriter.Result stays(Apply call) {
    return new Rewriter.Result(call, Trace.NONE);
  }

  /** {@code ifactor(n)}: 0 and ±1 themselves, a prime itself, otherwise the held product. */
  private static Rewriter.Result ifactor(Apply call, BigInteger x, Context ctx) {
    if (x.abs().compareTo(BigInteger.ONE) <= 0) {
      return silent(num(x));
    }
    Factorizer.Factorization f = Factorizer.factor(x, Factorizer.Budget.FULL, ctx.cancellation());
    if (!f.complete()) {
      if (!ctx.traceEnabled()) {
        return stays(call);
      }
      TreeMap<String, Expr> values = new TreeMap<>();
      if (!f.primes().isEmpty()) {
        values.put("found", list(f.primes().keySet()));
      }
      values.put("left", list(f.unfinished().keySet()));
      return traced(ctx, call, call, "unfinished", values);
    }
    Expr value = factored(f.sign(), f.primes());
    if (!ctx.traceEnabled()) {
      return silent(value);
    }
    TreeMap<String, Expr> values = new TreeMap<>();
    for (String method : List.of("trial", "power", "rho", "ecm")) {
      List<BigInteger> found = new ArrayList<>();
      for (Factorizer.Event e : f.events()) {
        if (e.method().equals(method) && !found.contains(e.factor())) {
          found.add(e.factor());
        }
      }
      if (!found.isEmpty()) {
        values.put(method, list(found));
      }
    }
    if (values.containsKey("trial")) {
      // the cofactor trial division leaves is prime when it is one of the primes, no other method
      // having touched it; said only when that is proven
      BigInteger cofactor = x.abs();
      for (Expr p : ((Vector) values.get("trial")).items()) {
        BigInteger q = ((Int) ((Num) p).value()).value();
        cofactor = cofactor.divide(q.pow(f.primes().get(q)));
      }
      if (cofactor.compareTo(BigInteger.ONE) > 0
          && f.primes().containsKey(cofactor)
          && Primes.isProven(cofactor)) {
        values.put("cofactor", num(cofactor));
      }
    }
    List<BigInteger> probable =
        f.primes().keySet().stream().filter(p -> !Primes.isProven(p)).toList();
    if (!probable.isEmpty()) {
      values.put("probable", list(probable));
    }
    String step = f.events().isEmpty() ? "prime" : "factors";
    return traced(ctx, call, value, step, values);
  }

  private static Expr list(java.util.Collection<BigInteger> items) {
    return Vector.of(items.stream().<Expr>map(NumberTheory::num).toList());
  }

  private static Rewriter.Result traced(
      Context ctx, Apply call, Expr value, String step, TreeMap<String, Expr> values) {
    if (!ctx.traceEnabled()) {
      return silent(value);
    }
    Rewrite r = new Rewrite(call, value, IFACTOR, step, Path.ROOT, Bindings.EMPTY, values);
    return new Rewriter.Result(value, new Trace(call, value, List.of(r), List.of()));
  }

  /**
   * The display-only product {@code sign · Π pᵉ}: the plain integer when it is 0, ±1 or ±p.
   *
   * @param sign −1 or 1
   * @param primes the primes and their exponents
   * @return the value
   */
  public static Expr factored(int sign, SortedMap<BigInteger, Integer> primes) {
    if (primes.isEmpty() || primes.size() == 1 && primes.values().iterator().next() == 1) {
      BigInteger v = primes.isEmpty() ? BigInteger.ONE : primes.firstKey();
      return num(sign < 0 ? v.negate() : v);
    }
    List<Expr> args = new ArrayList<>();
    args.add(Num.of(sign));
    for (Map.Entry<BigInteger, Integer> e : primes.entrySet()) {
      args.add(num(e.getKey()));
      args.add(Num.of(e.getValue()));
    }
    return new Apply(Symbol.of(FACTORED), args);
  }

  /**
   * Whether an expression is {@code ifactor}'s display-only product.
   *
   * @param e the expression
   * @return true for {@code factored(…)}
   */
  public static boolean isFactored(Expr e) {
    return e instanceof Apply a && a.head().name().equals(FACTORED);
  }

  /**
   * The integer of a display-only product.
   *
   * @param a {@code factored(sign, p₁, e₁, …)}
   * @return {@code sign · Π pᵢ^eᵢ}
   */
  public static BigInteger value(Apply a) {
    List<Expr> args = a.args();
    BigInteger v = ((Int) ((Num) args.getFirst()).value()).value();
    for (int k = 1; k < args.size(); k += 2) {
      BigInteger p = ((Int) ((Num) args.get(k)).value()).value();
      int e = ((Int) ((Num) args.get(k + 1)).value()).value().intValueExact();
      v = v.multiply(p.pow(e));
    }
    return v;
  }

  /**
   * An expression with every display-only product in it replaced by its integer: the value itself,
   * or the items of a collection, nested collections included. Display-only values occur nowhere
   * else, since every other use replaces them.
   *
   * @param e the expression
   * @return it, with the integers put in
   */
  public static Expr plain(Expr e) {
    return switch (e) {
      case Apply a when isFactored(a) -> num(value(a));
      case ListExpr l -> changed(l.items(), l, ListExpr::of);
      case Vector v -> changed(v.items(), v, Vector::of);
      case SetExpr s -> changed(s.items(), s, SetExpr::of);
      case Matrix m -> {
        List<List<Expr>> rows = new ArrayList<>();
        boolean any = false;
        for (List<Expr> row : m.rows()) {
          List<Expr> out = row.stream().map(NumberTheory::plain).toList();
          any |= !out.equals(row);
          rows.add(out);
        }
        yield any ? Matrix.of(rows) : m;
      }
      default -> e;
    };
  }

  private static Expr changed(
      List<Expr> items, Expr whole, java.util.function.Function<List<Expr>, Expr> rebuild) {
    List<Expr> out = items.stream().map(NumberTheory::plain).toList();
    return out.equals(items) ? whole : rebuild.apply(out);
  }

  /** {@code divisors}, {@code numdivisors}, {@code sigma}, {@code totient}, {@code mobius}. */
  private static Rewriter.Result divisorFunction(
      Apply call, String name, BigInteger x, Context ctx) {
    if (x.signum() == 0) {
      return silent(Constant.UNDEFINED);
    }
    Factorizer.Factorization f = Factorizer.factor(x, Factorizer.Budget.FULL, ctx.cancellation());
    if (!f.complete()) {
      return stays(call);
    }
    SortedMap<BigInteger, Integer> primes = f.primes();
    return switch (name) {
      case "divisors" -> {
        Optional<List<BigInteger>> ds = Factorizer.divisors(primes, MAX_DIVISORS);
        if (ds.isEmpty()) {
          throw new IllegalArgumentException(
              "divisors lists at most " + MAX_DIVISORS + " divisors");
        }
        yield silent(Brackets.of(ds.get().stream().<Expr>map(NumberTheory::num).toList()));
      }
      case "numdivisors" -> {
        BigInteger d = BigInteger.ONE;
        for (int e : primes.values()) {
          d = d.multiply(BigInteger.valueOf(e + 1L));
        }
        yield silent(num(d));
      }
      case "sigma" -> {
        BigInteger s = BigInteger.ONE;
        for (Map.Entry<BigInteger, Integer> e : primes.entrySet()) {
          BigInteger p = e.getKey();
          s =
              s.multiply(
                  p.pow(e.getValue() + 1)
                      .subtract(BigInteger.ONE)
                      .divide(p.subtract(BigInteger.ONE)));
        }
        yield silent(num(s));
      }
      case "totient" -> {
        BigInteger t = BigInteger.ONE;
        for (Map.Entry<BigInteger, Integer> e : primes.entrySet()) {
          BigInteger p = e.getKey();
          t = t.multiply(p.pow(e.getValue() - 1)).multiply(p.subtract(BigInteger.ONE));
        }
        yield silent(num(t));
      }
      case "mobius" -> {
        boolean squareFree = primes.values().stream().allMatch(e -> e == 1);
        yield silent(Num.of(!squareFree ? 0 : primes.size() % 2 == 0 ? 1 : -1));
      }
      default -> throw new IllegalStateException("no number-theory function " + name);
    };
  }
}
