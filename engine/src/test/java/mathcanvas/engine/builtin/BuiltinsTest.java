package mathcanvas.engine.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.OptionalInt;
import mathcanvas.engine.builtin.Builtin.EiBinding;
import mathcanvas.engine.builtin.Builtin.Kind;
import org.junit.jupiter.api.Test;

class BuiltinsTest {

  private static Builtin get(String name) {
    return Builtins.lookup(name).orElseThrow();
  }

  @Test
  void everyNameOfTheNamespaceIsRegistered() {
    for (String name :
        List.of(
            "abs sign floor ceil trunc round mod gcd lcm factorial binomial sqrt cbrt root exp ln",
            "log log10 log2 sin cos tan cot sec csc asin acos atan acot asec acsc atan2 sinh cosh",
            "tanh coth sech csch asinh acosh atanh acoth asech acsch re im conj arg min max",
            "numerator denominator nth length dot cross solve diff integrate limit sum product",
            "taylor expand factor cancel simplify collect apart subs assume assumptions numeric exact steps",
            "clear help det rref eigenvalues grad slider",
            "transpose inverse rank trace linsolve nullspace colspace gramschmidt normalize norm",
            "identity zeros size charpoly eigenvectors diagonalize",
            "arclength area volume shellvolume extrema maximize minimize",
            "lu qr cholesky svd lstsq expm",
            "dms degrees",
            "triangle trianglearea polygonarea circumradius inradius distance midpoint slope",
            "lineequation circleequation parabola ellipse hyperbola conic")) {
      for (String n : name.split(" ")) {
        assertThat(Builtins.isCallable(n)).as(n).isTrue();
      }
    }
    assertThat(Builtins.all()).hasSize(128);
  }

  @Test
  void aliasesResolveToTheirName() {
    assertThat(get("arcsin").name()).isEqualTo("asin");
    assertThat(get("arccsc").name()).isEqualTo("acsc");
    assertThat(Builtins.isPrefix("arctan")).isTrue();
  }

  @Test
  void prefixSet() {
    for (String n : List.of("sin", "acoth", "ln", "log", "log10", "log2", "exp", "sqrt", "cbrt")) {
      assertThat(Builtins.isPrefix(n)).as(n).isTrue();
    }
    for (String n : List.of("abs", "gcd", "root", "atan2", "x", "solve")) {
      assertThat(Builtins.isPrefix(n)).as(n).isFalse();
    }
  }

  @Test
  void arities() {
    assertThat(get("sin").accepts(1)).isTrue();
    assertThat(get("sin").accepts(2)).isFalse();
    assertThat(get("integrate").accepts(3)).isFalse();
    assertThat(get("integrate").accepts(4)).isTrue();
    assertThat(get("gcd").accepts(1)).isFalse();
    assertThat(get("gcd").accepts(7)).isTrue();
    assertThat(get("assumptions").accepts(0)).isTrue();
    assertThat(get("sin").arityText()).isEqualTo("1 argument");
    assertThat(get("solve").arityText()).isEqualTo("2 arguments");
    assertThat(get("integrate").arityText()).isEqualTo("2 or 4 arguments");
    assertThat(get("log").arityText()).isEqualTo("1 or 2 arguments");
    assertThat(get("gcd").arityText()).isEqualTo("2 or more arguments");
    assertThat(get("assumptions").arityText()).isEqualTo("no arguments");
    assertThat(get("slider").arityText()).isEqualTo("3 arguments");
  }

  @Test
  void commandsHoldAndBind() {
    Builtin limit = get("limit");
    assertThat(limit.kind()).isEqualTo(Kind.COMMAND);
    assertThat(limit.isHeld(1)).isTrue();
    assertThat(limit.isHeld(3)).isFalse();
    assertThat(limit.isHeld(4)).isTrue();
    assertThat(limit.binder()).isEqualTo(OptionalInt.of(2));
    assertThat(get("subs").isHeld(9)).isTrue();
    assertThat(get("integrate").eiBinding()).isEqualTo(EiBinding.WITH_BOUNDS);
    assertThat(get("diff").eiBinding()).isEqualTo(EiBinding.NO);
    assertThat(get("solve").eiBinding()).isEqualTo(EiBinding.YES);
    assertThat(get("sin").binder()).isEmpty();
  }

  @Test
  void theBinderMayDependOnTheArity() {
    Builtin twoForms =
        new Builtin(
            "twoforms",
            Kind.COMMAND,
            List.of(4, 5),
            false,
            false,
            java.util.Set.of(1, 2, 3),
            false,
            OptionalInt.of(2),
            EiBinding.YES,
            java.util.Map.of(5, 3));
    assertThat(twoForms.binderFor(4)).isEqualTo(OptionalInt.of(2));
    assertThat(twoForms.binderFor(5)).isEqualTo(OptionalInt.of(3));
    assertThat(get("integrate").binderFor(4)).isEqualTo(OptionalInt.of(2));
    assertThat(get("sin").binderFor(1)).isEmpty();
  }

  @Test
  void limIsANotationWordNotACall() {
    assertThat(get("lim").kind()).isEqualTo(Kind.NOTATION);
    assertThat(Builtins.isCallable("lim")).isFalse();
    assertThat(get("grad").kind()).isEqualTo(Kind.RESERVED);
    assertThat(Builtins.isCallable("grad")).isTrue();
  }

  @Test
  void reservedWords() {
    assertThat(Builtins.isConstant("e")).isTrue();
    assertThat(Builtins.isConstant("undefined")).isTrue();
    assertThat(Builtins.isConstant("x")).isFalse();
    assertThat(Builtins.isDomain("Reals")).isTrue();
    assertThat(Builtins.isDomain("reals")).isFalse();
    assertThat(Builtins.isKeyword("if")).isTrue();
    assertThat(Builtins.isKeyword("left")).isFalse();
    assertThat(Builtins.lookup("left")).isEmpty();
  }
}
