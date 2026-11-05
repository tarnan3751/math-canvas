package mathcanvas.engine.match;

import static mathcanvas.engine.testing.Inputs.v;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprArbitraries;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class MatcherProperties {

  private static final Symbol F = Symbol.of("f");
  private static final Symbol G = Symbol.of("g");
  private static final Symbol H = Symbol.of("h");

  /** Linear patterns whose variables sit under distinct heads, so every instance is unambiguous. */
  private static final List<Expr> LINEAR =
      List.of(
          Sum.of(Apply.of(F, v("x_"), v("y_")), Apply.of(G, v("z_"))),
          Product.of(Apply.of(F, v("x_")), Apply.of(G, v("y_"), v("z_"))),
          Power.of(Apply.of(F, v("x_")), Apply.of(G, v("y_"))),
          Sum.of(Apply.of(F, v("x_")), Product.of(Apply.of(G, v("y_")), Apply.of(H, v("z_")))));

  /** Patterns with several matches on most subjects. */
  private static final List<Pattern> FAMILY =
      List.of(
          Pattern.of(Sum.of(v("x_"), v("y_"))),
          Pattern.of(Product.of(v("x_"), v("y_"))),
          Pattern.of(Sum.of(v("x_"), v("rest___"))),
          Pattern.of(Product.of(v("x_"), v("y_"), v("rest___"))),
          Pattern.of(Product.of(v("a_"), Power.of(v("x_"), v("n_"))))
              .optional("a_", Num.ONE)
              .optional("n_", Num.ONE),
          Pattern.of(Sum.of(v("a_"), Product.of(v("b_"), v("x_"))))
              .optional("a_", Num.ZERO)
              .optional("b_", Num.ONE),
          Pattern.of(Apply.of(F, v("a___"), v("b___"))),
          Pattern.of(Apply.of(v("h_"), v("u_"), v("rest___"))),
          Pattern.of(Power.of(v("x_"), v("n_"))).optional("n_", Num.ONE));

  /**
   * Canonical subjects, the matcher's precondition. The canonical form is a fixpoint, which the
   * properties rely on (a value that canonicalised further inside an instance could not rebind as
   * itself), so a sample that is not one fails here rather than being skipped.
   */
  @Provide
  Arbitrary<Expr> subjects() {
    return ExprArbitraries.exprs().map(MatcherProperties::canonical);
  }

  private static Expr canonical(Expr raw) {
    Expr c = Canonical.of(raw);
    assertThat(Canonical.of(c)).as("the canonical form of %s is a fixpoint", raw).isEqualTo(c);
    return c;
  }

  @Provide
  Arbitrary<Pattern> family() {
    return Arbitraries.of(FAMILY);
  }

  @Property(tries = 1000)
  void anInstanceRebindsItsValues(
      @ForAll("subjects") Expr x,
      @ForAll("subjects") Expr y,
      @ForAll("subjects") Expr z,
      @ForAll("linear") Expr shape) {
    Bindings values =
        Bindings.EMPTY
            .with(new Bindings.Binding("x_", x, null, null))
            .with(new Bindings.Binding("y_", y, null, null))
            .with(new Bindings.Binding("z_", z, null, null));
    Pattern p = Pattern.of(shape);
    Expr instance = Canonical.of(values.substituteInto(shape));
    Map<String, Expr> expected = MatcherTest.m(values);
    expected.keySet().retainAll(p.variables());
    Optional<Bindings> rebound = Matcher.match(p, instance);
    assertThat(rebound.map(MatcherTest::m)).as(instance.toString()).contains(expected);
  }

  @Provide
  Arbitrary<Expr> linear() {
    return Arbitraries.of(LINEAR);
  }

  @Property(tries = 1000)
  void anArgumentSequenceRebindsItsArguments(
      @ForAll("subjects") Expr x, @ForAll("argLists") List<Expr> rest) {
    List<Expr> args = new ArrayList<>();
    args.add(x);
    args.addAll(rest);
    Bindings b =
        Matcher.match(Pattern.of(Apply.of(F, v("x_"), v("r___"))), Apply.of(F, args)).orElseThrow();
    assertThat(b.get("x_")).isEqualTo(x);
    assertThat(b.sequence("r___")).isEqualTo(rest);
  }

  @Provide
  Arbitrary<List<Expr>> argLists() {
    return subjects().list().ofMaxSize(4);
  }

  @Property(tries = 2000)
  void allHasNoDuplicates(@ForAll("family") Pattern p, @ForAll("subjects") Expr s) {
    List<Map<String, Expr>> matches = Matcher.all(p, s).limit(2000).map(MatcherTest::m).toList();
    assertThat(matches).doesNotHaveDuplicates();
    assertThat(Matcher.match(p, s).map(MatcherTest::m)).isEqualTo(matches.stream().findFirst());
  }

  @Property(tries = 2000)
  void combinedSequencesAreCanonical(@ForAll("family") Pattern p, @ForAll("subjects") Expr s) {
    Matcher.all(p, s)
        .limit(200)
        .forEach(
            b -> {
              for (String name : b.names()) {
                if (Pattern.isSequence(name)) {
                  Expr value = b.get(name);
                  assertThat(Canonical.of(value)).as(name + " in " + s).isEqualTo(value);
                }
              }
            });
  }

  @Property(tries = 3000)
  void matchingNeverThrows(@ForAll("any") Expr raw, @ForAll("subjects") Expr s) {
    Pattern p;
    try {
      p = Pattern.of(asPattern(raw));
    } catch (IllegalArgumentException misplaced) {
      return;
    }
    assertThat(Matcher.all(p, s).limit(500).count()).isGreaterThanOrEqualTo(0);
    Matcher.match(p, s);
  }

  @Provide
  Arbitrary<Expr> any() {
    return ExprArbitraries.exprs();
  }

  /** The generator's symbols as variables: a and x plain, b a ___ and y a __ sequence. */
  private static final Map<String, Expr> AS_VARIABLES =
      Map.of("a", v("a_"), "b", v("b___"), "x", v("x_"), "y", v("y__"));

  private static Expr asPattern(Expr e) {
    if (e instanceof Symbol s) {
      return AS_VARIABLES.getOrDefault(s.name(), s);
    }
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    List<Expr> out = new ArrayList<>();
    for (int i = 0; i < ops.size(); i++) {
      out.add(Operands.isBound(e, i) ? ops.get(i) : asPattern(ops.get(i)));
    }
    return Operands.rebuild(e, out);
  }
}
