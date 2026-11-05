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

  /** Whether a name is in the prefix set (§7.2). */
  private static boolean prefix(String name) {
    return Builtins.lookup(name).map(Builtin::prefix).orElse(false);
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
            "clear help det rref eigenvalues grad slider plot",
            "jacobian hessian divergence curl laplacian lagrange lineintegral surfaceintegral laplace ilaplace",
            "transpose inverse rank trace linsolve nullspace colspace gramschmidt normalize norm",
            "identity zeros size charpoly eigenvectors diagonalize",
            "arclength area volume shellvolume extrema maximize minimize",
            "lu qr cholesky svd lstsq expm",
            "dms degrees",
            "triangle trianglearea polygonarea circumradius inradius distance midpoint slope",
            "lineequation circleequation parabola ellipse hyperbola conic",
            // Differential equations
            "dsolve odesolve",
            // Number theory, combinatorics, sets and logic
            "isprime nextprime prevprime primes ifactor divisors numdivisors sigma totient mobius",
            "powmod invmod crt diophantine",
            "choose permutations multinomial fibonacci lucas catalan bell stirling2 partitions",
            "union intersect setdiff symdiff subsets cartesian",
            "and or xor not implies truthtable rsolve",
            // Units, bases, bits and finance
            "units magnitude convert",
            "hex bin oct base digits",
            "bitand bitor bitxor bitnot shiftleft shiftright bit bitcount",
            "pv fv pmt nper rate npv irr effective nominal amortization",
            "tvm",
            // Special functions and statistics
            "erf erfc gamma beta",
            "mean median mode variance pvariance stdev pstdev range quartiles iqr percentile",
            "zscore sort cumsum summary skewness kurtosis cov cor frequencies",
            "normalpdf normalcdf invnorm tpdf tcdf invt chi2pdf chi2cdf invchi2 fpdf fcdf invf",
            "binompdf binomcdf poissonpdf poissoncdf geometpdf geometcdf hypergeompdf",
            "hypergeomcdf exppdf expcdf unifpdf unifcdf",
            "linreg quadreg cubicreg quartreg polyreg expreg lnreg powreg residuals predict",
            "ztest ttest ttest2 pairedttest proptest proptest2 chi2gof chi2test anova",
            "zinterval tinterval tinterval2 propinterval propinterval2",
            // Programs
            "print append setnth")) {
      for (String n : name.split(" ")) {
        assertThat(Builtins.isCallable(n)).as(n).isTrue();
      }
    }
    assertThat(Builtins.all()).hasSize(278);
  }

  @Test
  void aliasesResolveToTheirName() {
    assertThat(get("arcsin").name()).isEqualTo("asin");
    assertThat(get("arccsc").name()).isEqualTo("acsc");
    assertThat(get("choose").name()).isEqualTo("binomial");
    assertThat(prefix("arctan")).isTrue();
  }

  @Test
  void prefixSet() {
    for (String n : List.of("sin", "acoth", "ln", "log", "log10", "log2", "exp", "sqrt", "cbrt")) {
      assertThat(prefix(n)).as(n).isTrue();
    }
    for (String n : List.of("abs", "gcd", "root", "atan2", "x", "solve")) {
      assertThat(prefix(n)).as(n).isFalse();
    }
  }

  @Test
  void arities() {
    assertThat(get("sin").accepts(1)).isTrue();
    assertThat(get("sin").accepts(2)).isFalse();
    // integrate(f, [x, a, b], [y, c, d]) takes 3: the parser refuses a count but 2 and 4 otherwise
    assertThat(get("integrate").accepts(3)).isTrue();
    assertThat(get("integrate").accepts(4)).isTrue();
    assertThat(get("gcd").accepts(1)).isFalse();
    assertThat(get("gcd").accepts(7)).isTrue();
    assertThat(get("assumptions").accepts(0)).isTrue();
    assertThat(get("sin").arityText()).isEqualTo("1 argument");
    assertThat(get("solve").arityText()).isEqualTo("2 arguments");
    assertThat(get("integrate").arityText()).isEqualTo("2 or more arguments");
    assertThat(get("log").arityText()).isEqualTo("1 or 2 arguments");
    assertThat(get("gcd").arityText()).isEqualTo("2 or more arguments");
    assertThat(get("assumptions").arityText()).isEqualTo("no arguments");
    assertThat(get("slider").arityText()).isEqualTo("3 arguments");
    assertThat(get("plot").arityText()).isEqualTo("1 or more arguments");
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
    assertThat(get("slider").kind()).isEqualTo(Kind.RESERVED);
    assertThat(Builtins.isCallable("slider")).isTrue();
    assertThat(get("plot").kind()).isEqualTo(Kind.RESERVED);
    assertThat(Builtins.isCallable("plot")).isTrue();
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
