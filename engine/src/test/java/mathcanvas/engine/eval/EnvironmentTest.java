package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Lambda;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

class EnvironmentTest {

  static final Symbol X = Symbol.of("x");

  @Test
  void emptyHasNothing() {
    assertThat(Environment.EMPTY.definitions()).isEmpty();
    assertThat(Environment.EMPTY.assumptions()).isEqualTo(Assumptions.EMPTY);
    assertThat(Environment.EMPTY.settings()).isEqualTo(Settings.DEFAULT);
    assertThat(Environment.EMPTY.definition("a")).isEmpty();
  }

  @Test
  void withBindsAndReplaces() {
    Environment env = Environment.EMPTY.with("a", new Definition.Value(Num.of(1)));
    assertThat(env.definition("a")).contains(new Definition.Value(Num.of(1)));
    Environment again = env.with("a", new Definition.Value(Num.of(2)));
    assertThat(again.definition("a")).contains(new Definition.Value(Num.of(2)));
    assertThat(env.definition("a")).contains(new Definition.Value(Num.of(1)));
  }

  @Test
  void aLambdaValueIsAFunction() {
    Environment env =
        Environment.EMPTY.with("f", new Definition.Value(Lambda.of(X, Power.of(X, Num.of(2)))));
    assertThat(env.definition("f"))
        .contains(new Definition.Function(List.of(X), Power.of(X, Num.of(2))));
    assertThat(env.isFunction("f")).isTrue();
  }

  @Test
  void withoutUnbinds() {
    Environment env = Environment.EMPTY.with("a", new Definition.Value(Num.of(1)));
    assertThat(env.without("a").definition("a")).isEmpty();
    assertThat(Environment.EMPTY.without("a")).isSameAs(Environment.EMPTY);
  }

  @Test
  void namesFollowTheFunctions() {
    Environment env =
        Environment.EMPTY
            .with("f", new Definition.Function(List.of(X), X))
            .with("a", new Definition.Value(X));
    assertThat(env.names().isUserFunction("f")).isTrue();
    assertThat(env.names().isUserFunction("a")).isFalse();
    assertThat(env.names().isUserFunction("g")).isFalse();
    assertThat(Parser.parseExpression("f(2)", env.names()).toString()).startsWith("Apply");
    assertThat(Parser.parseExpression("a(2)", env.names())).isInstanceOf(Product.class);
  }

  @Test
  void assumptionsAndSettingsAreReplaced() {
    Assumptions a =
        Assumptions.EMPTY.with((Relation) Canonical.of(Relation.of(Relation.Op.GT, X, Num.of(0))));
    Environment env =
        Environment.EMPTY.withAssumptions(a).withSettings(Settings.DEFAULT.withPrecision(50));
    assertThat(env.assumptions()).isEqualTo(a);
    assertThat(env.settings().precision()).isEqualTo(50);
  }

  @Test
  void functionsCheckTheirParameters() {
    assertThatThrownBy(() -> new Definition.Function(List.of(), X))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Definition.Function(List.of(X, X), X))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void settingsBoundThePrecision() {
    assertThat(Settings.DEFAULT.mathContext())
        .isEqualTo(new MathContext(34, RoundingMode.HALF_EVEN));
    assertThat(new Settings(10_000, AngleMode.DEGREES).precision()).isEqualTo(10_000);
    assertThatThrownBy(() -> new Settings(0, AngleMode.RADIANS))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Settings.DEFAULT.withPrecision(10_001))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void angleFactors() {
    assertThat(Canonical.of(AngleMode.DEGREES.toRadians()))
        .isEqualTo(Canonical.of(Product.of(Constant.PI, Power.of(Num.of(180), Num.MINUS_ONE))));
    assertThat(
            Canonical.of(
                Product.of(AngleMode.GRADIANS.toRadians(), AngleMode.GRADIANS.fromRadians())))
        .isEqualTo(Num.ONE);
    assertThat(AngleMode.RADIANS.toRadians()).isEqualTo(Num.ONE);
  }
}
