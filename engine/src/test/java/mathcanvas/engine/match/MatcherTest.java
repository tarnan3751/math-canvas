package mathcanvas.engine.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Rational;
import org.junit.jupiter.api.Test;

class MatcherTest {

  static final Symbol A = Symbol.of("a");
  static final Symbol B = Symbol.of("b");
  static final Symbol C = Symbol.of("c");
  static final Symbol X = Symbol.of("x");
  static final Symbol Y = Symbol.of("y");
  static final Symbol F = Symbol.of("f");
  static final Symbol G = Symbol.of("g");

  static Symbol v(String name) {
    return Symbol.of(name);
  }

  static Num n(long k) {
    return Num.of(k);
  }

  static Expr c(Expr e) {
    return Canonical.of(e);
  }

  static Apply f(Expr... args) {
    return Apply.of(F, args);
  }

  static Map<String, Expr> m(Bindings b) {
    return b.asMap();
  }

  static Map<String, Expr> first(Pattern p, Expr subject) {
    return Matcher.match(p, c(subject)).map(MatcherTest::m).orElse(null);
  }

  static List<Map<String, Expr>> all(Pattern p, Expr subject) {
    return Matcher.all(p, c(subject)).map(MatcherTest::m).toList();
  }

  @Test
  void aVariableMatchesAnythingAndARepeatedOneMatchesEqualValues() {
    Pattern p = Pattern.of(v("x_"));
    assertThat(first(p, Sum.of(A, B))).isEqualTo(Map.of("x_", c(Sum.of(A, B))));
    Pattern twice = Pattern.of(f(v("x_"), v("x_")));
    assertThat(first(twice, f(A, A))).isEqualTo(Map.of("x_", A));
    assertThat(first(twice, f(A, B))).isNull();
  }

  @Test
  void literalsMatchByEqualityAndThePatternIsCanonicalised() {
    Pattern p = Pattern.of(Sum.of(X, n(1)));
    assertThat(first(p, Sum.of(n(1), X))).isEmpty();
    assertThat(first(p, Sum.of(n(2), X))).isNull();
    // x_ - 1 is canonicalised to Sum(-1, x_), the shape of y - 1.
    assertThat(first(Pattern.of(Exprs.sub(v("x_"), n(1))), Exprs.sub(Y, n(1))))
        .isEqualTo(Map.of("x_", Y));
    assertThat(
            first(
                Pattern.of(Power.of(v("x_"), n(2))),
                Power.of(X, Num.of(mathcanvas.engine.number.Real.of("2.0")))))
        .isNull();
  }

  @Test
  void sumsMatchCommutativelyInADeterministicOrder() {
    Pattern p = Pattern.of(Sum.of(v("x_"), v("y_")));
    assertThat(all(p, Sum.of(B, A)))
        .containsExactly(Map.of("x_", A, "y_", B), Map.of("x_", B, "y_", A));
    assertThat(first(p, Sum.of(A, B, C))).isNull();
    assertThat(first(p, A)).isNull();
    // f(x_) + g(y_): each operand finds its own term.
    Pattern fg = Pattern.of(Sum.of(f(v("x_")), Apply.of(G, v("y_"))));
    assertThat(all(fg, Sum.of(Apply.of(G, B), f(A)))).containsExactly(Map.of("x_", A, "y_", B));
  }

  @Test
  void allEnumeratesInCanonicalSubjectOrderAndPatternOperandsLeftToRight() {
    // Pattern operands x_ then y_, each over the candidates a < b < c, rest taking the remainder.
    Pattern p = Pattern.of(Sum.of(v("x_"), v("y_"), v("r___")));
    List<List<Expr>> order =
        Matcher.all(p, c(Sum.of(C, B, A)))
            .map(b -> List.of(b.get("x_"), b.get("y_"), b.get("r___")))
            .toList();
    assertThat(order)
        .containsExactly(
            List.of(A, B, C),
            List.of(A, C, B),
            List.of(B, A, C),
            List.of(B, C, A),
            List.of(C, A, B),
            List.of(C, B, A));
  }

  @Test
  void sequenceVariablesInSumsAndProducts() {
    Pattern p = Pattern.of(Sum.of(v("x_"), v("rest___")));
    assertThat(all(p, Sum.of(A, B, C)))
        .containsExactly(
            Map.of("x_", A, "rest___", c(Sum.of(B, C))),
            Map.of("x_", B, "rest___", c(Sum.of(A, C))),
            Map.of("x_", C, "rest___", c(Sum.of(A, B))));
    // ___ with nothing left binds the identity element; __ needs an operand.
    assertThat(first(p, A)).isEqualTo(Map.of("x_", A, "rest___", n(0)));
    assertThat(first(Pattern.of(Product.of(v("x_"), v("rest___"))), A))
        .isEqualTo(Map.of("x_", A, "rest___", n(1)));
    Pattern plus = Pattern.of(Sum.of(v("x_"), v("rest__")));
    assertThat(first(plus, A)).isNull();
    assertThat(first(plus, Sum.of(A, B))).isEqualTo(Map.of("x_", A, "rest__", B));
    Pattern prod = Pattern.of(Product.of(n(2), v("rest___")));
    assertThat(first(prod, Product.of(n(2), X, Y)))
        .isEqualTo(Map.of("rest___", c(Product.of(X, Y))));
    assertThat(first(prod, Product.of(n(3), X))).isNull();
    Bindings b = Matcher.match(p, c(Sum.of(A, B, C))).orElseThrow();
    assertThat(b.sequence("rest___")).containsExactly(B, C);
  }

  @Test
  void optionalDefaults() {
    Pattern ax = Pattern.of(Product.of(v("a_"), v("x_"))).optional("a_", n(1));
    assertThat(first(ax, X)).isEqualTo(Map.of("a_", n(1), "x_", X));
    assertThat(all(ax, Product.of(n(2), X)))
        .containsExactly(Map.of("a_", n(2), "x_", X), Map.of("a_", X, "x_", n(2)));
    Pattern xn = Pattern.of(Power.of(v("x_"), v("n_"))).optional("n_", n(1));
    assertThat(first(xn, Y)).isEqualTo(Map.of("x_", Y, "n_", n(1)));
    assertThat(all(xn, Power.of(Y, n(3))))
        .containsExactly(
            Map.of("x_", Y, "n_", n(3)), Map.of("x_", c(Power.of(Y, n(3))), "n_", n(1)));
    // a_*x_^n_ matches x with both defaults, and 3x^2 with none.
    Pattern axn =
        Pattern.of(Product.of(v("a_"), Power.of(v("x_"), v("n_"))))
            .optional("a_", n(1))
            .optional("n_", n(1));
    assertThat(first(axn, X)).isEqualTo(Map.of("a_", n(1), "x_", X, "n_", n(1)));
    assertThat(first(axn, Product.of(n(3), Power.of(X, n(2)))))
        .isEqualTo(Map.of("a_", n(3), "x_", X, "n_", n(2)));
    // Rubi's (a_. + b_. x_)^m_. against x.
    Pattern linear =
        Pattern.of(Power.of(Sum.of(v("a_"), Product.of(v("b_"), v("x_"))), v("m_")))
            .optional("a_", n(0))
            .optional("b_", n(1))
            .optional("m_", n(1));
    assertThat(first(linear, X)).isEqualTo(Map.of("a_", n(0), "b_", n(1), "x_", X, "m_", n(1)));
    assertThat(
            first(
                linear.where("x_", e -> e.equals(Y)),
                Power.of(Sum.of(n(1), Product.of(n(2), Y)), n(3))))
        .isEqualTo(Map.of("a_", n(1), "b_", n(2), "x_", Y, "m_", n(3)));
    Pattern sum = Pattern.of(Sum.of(v("a_"), v("x_"))).optional("a_", n(0));
    assertThat(first(sum, X)).isEqualTo(Map.of("a_", n(0), "x_", X));
  }

  @Test
  void conditionsPruneAsSoonAsTheirVariablesAreBound() {
    AtomicInteger calls = new AtomicInteger();
    Pattern p =
        Pattern.of(Product.of(v("x_"), v("y_"), v("z_")))
            .where(
                "x_",
                e -> {
                  calls.incrementAndGet();
                  return false;
                });
    assertThat(first(p, Product.of(A, B, C))).isNull();
    // Checked once per candidate for x_, never after y_ and z_ are chosen.
    assertThat(calls.get()).isEqualTo(3);

    Pattern integer =
        Pattern.of(Power.of(v("x_"), v("n_")))
            .where("n_", e -> e instanceof Num k && k.value().isInteger());
    assertThat(first(integer, Power.of(X, n(2)))).isEqualTo(Map.of("x_", X, "n_", n(2)));
    assertThat(first(integer, Power.of(X, Y))).isNull();

    AtomicInteger pairs = new AtomicInteger();
    Pattern ordered =
        Pattern.of(Sum.of(v("x_"), v("y_")))
            .where(
                List.of("x_", "y_"),
                (b, a) -> {
                  pairs.incrementAndGet();
                  return b.get("x_").equals(B);
                });
    assertThat(first(ordered, Sum.of(A, B))).isEqualTo(Map.of("x_", B, "y_", A));
    assertThat(pairs.get()).isEqualTo(2);

    Pattern whole = Pattern.of(Sum.of(v("x_"), v("y_"))).where((b, a) -> b.get("y_").equals(A));
    assertThat(first(whole, Sum.of(A, B))).isEqualTo(Map.of("x_", B, "y_", A));
  }

  @Test
  void conditionsSeeTheAssumptions() {
    Pattern p =
        Pattern.of(Product.of(v("x_"), v("rest___")))
            .where(List.of("x_"), (b, a) -> a.isPositive(b.get("x_")) == Tri.TRUE);
    Assumptions yPositive = Assumptions.EMPTY.with(Relation.of(Relation.Op.GT, Y, n(0)));
    assertThat(Matcher.match(p, c(Product.of(X, Y)), yPositive).map(MatcherTest::m))
        .contains(Map.of("x_", Y, "rest___", X));
    assertThat(Matcher.match(p, c(Product.of(X, Y)))).isEmpty();
  }

  @Test
  void argumentListsMatchPositionallyWithSequences() {
    Pattern p = Pattern.of(f(v("x_"), v("rest___")));
    assertThat(first(p, f(A))).isEqualTo(Map.of("x_", A, "rest___", new ListExpr(List.of())));
    assertThat(first(p, f(A, B, C)))
        .isEqualTo(Map.of("x_", A, "rest___", new ListExpr(List.of(B, C))));
    assertThat(first(p, f())).isNull();
    Pattern two = Pattern.of(f(v("a___"), v("b__")));
    assertThat(all(two, f(n(1), n(2))))
        .containsExactly(
            Map.of("a___", new ListExpr(List.of()), "b__", new ListExpr(List.of(n(1), n(2)))),
            Map.of("a___", new ListExpr(List.of(n(1))), "b__", new ListExpr(List.of(n(2)))));
    assertThat(first(Pattern.of(f(v("x_"))), Apply.of(G, A))).isNull();
    assertThat(first(Pattern.of(f(v("x_"))), f(A, B))).isNull();
  }

  @Test
  void anInstanceRebindsItsCanonicalValues() {
    // The CI counterexample of MatcherProperties.anInstanceRebindsItsValues, with z canonical:
    // drawn as REALS^(1/3)·x·x^(x - 1/2), which is not, z failed to rebind as itself.
    Expr x = c(Power.of(X, n(2)));
    Expr y = c(Sum.of(Constant.INTEGERS, Bool.of(true)));
    Expr z =
        c(
            Product.of(
                Power.of(Constant.REALS, Num.of(Rational.of(1, 3))),
                X,
                Power.of(X, Sum.of(Num.of(Rational.of(-1, 2)), X))));
    assertThat(c(z)).isEqualTo(z);
    Expr instance = c(Sum.of(f(x, y), Apply.of(G, z)));
    assertThat(first(Pattern.of(Sum.of(f(v("x_"), v("y_")), Apply.of(G, v("z_")))), instance))
        .isEqualTo(Map.of("x_", x, "y_", y, "z_", z));
  }

  @Test
  void aVariableHeadBindsTheFunctionsName() {
    Pattern p = Pattern.of(Apply.of(v("h_"), v("u_")));
    assertThat(first(p, Apply.of(Symbol.of("sin"), X)))
        .isEqualTo(Map.of("h_", Symbol.of("sin"), "u_", X));
    assertThat(first(p, X)).isNull();
  }

  @Test
  void factorsThatDoNotCommuteKeepTheirOrder() {
    Matrix m1 = Matrix.of(List.of(List.of(A)));
    Matrix m2 = Matrix.of(List.of(List.of(B)));
    Expr product = c(Product.of(n(2), m1, m2));
    Pattern rest = Pattern.of(Product.of(v("k_"), v("rest___")));
    assertThat(Matcher.all(rest, product).map(MatcherTest::m).toList())
        .containsExactly(Map.of("k_", n(2), "rest___", Product.of(m1, m2)));
    assertThat(Matcher.match(Pattern.of(Product.of(v("x_"), v("y_"), v("z_"))), product)).isEmpty();
    // One such factor commutes with the scalars.
    Expr single = c(Product.of(n(2), m1));
    assertThat(Matcher.all(Pattern.of(Product.of(v("x_"), v("y_"))), single).map(MatcherTest::m))
        .containsExactly(Map.of("x_", n(2), "y_", m1), Map.of("x_", m1, "y_", n(2)));
  }

  @Test
  void otherKindsMatchPositionally() {
    Pattern eq = Pattern.of(Relation.of(Relation.Op.EQ, v("l_"), v("r_")));
    assertThat(first(eq, Relation.of(Relation.Op.EQ, X, n(2))))
        .isEqualTo(Map.of("l_", X, "r_", n(2)));
    assertThat(first(eq, Relation.of(Relation.Op.NE, X, n(2)))).isNull();
    Pattern d = Pattern.of(Derivative.of(v("u_"), Symbol.of("t_"), 1));
    assertThat(first(d, Derivative.of(Power.of(X, n(2)), X, 1)))
        .isEqualTo(Map.of("u_", c(Power.of(X, n(2))), "t_", X));
    assertThat(first(d, Derivative.of(X, X, 2))).isNull();
  }

  @Test
  void compilationRejectsMisplacedVariables() {
    List<Expr> bad =
        List.of(
            Power.of(v("x___"), n(2)),
            Sum.of(v("a___"), v("b___")),
            Sum.of(f(v("r___")), Apply.of(G, v("r___"))),
            v("_"),
            v("x__"),
            Relation.of(Relation.Op.EQ, v("r___"), n(0)));
    for (Expr e : bad) {
      assertThatThrownBy(() -> Pattern.of(e))
          .as(e.toString())
          .isInstanceOf(IllegalArgumentException.class);
    }
    Pattern p = Pattern.of(Sum.of(f(v("x_")), v("y_")));
    assertThatThrownBy(() -> p.optional("x_", n(0))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> p.optional("z_", n(0))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Pattern.of(Power.of(v("x_"), v("x_"))).optional("x_", n(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Pattern.of(Power.of(v("x_"), n(2))).optional("x_", n(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> p.where("z_", e -> true)).isInstanceOf(IllegalArgumentException.class);
    assertThat(p.variables()).containsExactly("x_", "y_");
  }

  @Test
  void noMatches() {
    assertThat(first(Pattern.of(Power.of(v("x_"), n(2))), X)).isNull();
    assertThat(first(Pattern.of(Product.of(v("x_"), v("y_"))), X)).isNull();
    assertThat(first(Pattern.of(f(v("x_"))), X)).isNull();
    assertThat(first(Pattern.of(Sum.of(n(1), v("x_"))), Sum.of(n(2), X))).isNull();
    List<Map<String, Expr>> none = new ArrayList<>(all(Pattern.of(Sum.of(v("x_"), v("y_"))), X));
    assertThat(none).isEmpty();
  }
}
