package mathcanvas.engine.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Lambda;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.print.AsciiPrinter;
import mathcanvas.engine.print.Printer;
import mathcanvas.engine.print.TypedShape;
import mathcanvas.engine.print.UnicodePrinter;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * The parser is total (PLAN §8, fuzzing): any text gives a script or a {@link ParseException},
 * never another exception, a stack overflow or a hang. Inputs are random strings over the
 * language's alphabet, random sequences of its tokens, and random mutations of the corpus inputs.
 * Whatever parses is also echoed: printed faithfully by the ASCII and the Unicode printer, it reads
 * back to the same value.
 */
class ParserFuzzProperties {

  private static final long BOUND_NANOS = 1_000_000_000L;

  private static final String ALPHABET =
      "0123456789.eEdxyikn+-*/^!%°=<>()[]{}⟨⟩|,;:→∈≤≥≠√∛∜∫Σ∑Π∏×·÷−²³⁻₁ \n\t#_πℤℝ∞∂" + "̸́ ";

  private static final List<String> TOKENS =
      List.of(
          "x",
          "y",
          "e",
          "i",
          "d",
          "dx",
          "dy",
          "dk",
          "k",
          "n",
          "f",
          "θ",
          "x₁",
          "sin",
          "cos",
          "log",
          "sqrt",
          "abs",
          "gcd",
          "mod",
          "lim",
          "sum",
          "product",
          "diff",
          "integrate",
          "limit",
          "solve",
          "clear",
          "assumptions",
          "left",
          "right",
          "if",
          "in",
          "pi",
          "inf",
          "true",
          "Reals",
          "d/dx",
          "d^2/dx^2",
          "d²/dx²",
          "d/dθ",
          "0",
          "1",
          "2",
          "1.5",
          "2e3",
          ".5",
          "+",
          "-",
          "*",
          "/",
          "^",
          "**",
          "!",
          "%",
          "°",
          "=",
          "!=",
          "<",
          "<=",
          "->",
          ":=",
          "(",
          ")",
          "[",
          "]",
          "{",
          "}",
          "⟨",
          "⟩",
          "|",
          ",",
          ";",
          "\n",
          "√",
          "∛",
          "∜",
          "∫",
          "Σ",
          "Π",
          "×",
          "²",
          "⁻¹",
          "π",
          "∞",
          "ℤ",
          "∈");

  @Provide
  Arbitrary<String> characters() {
    return Arbitraries.strings().withChars(ALPHABET).ofMaxLength(40);
  }

  @Provide
  Arbitrary<String> tokens() {
    return Arbitraries.of(TOKENS)
        .list()
        .ofMaxSize(30)
        .flatMap(list -> Arbitraries.of(" ", "").map(sep -> String.join(sep, list)));
  }

  @Provide
  Arbitrary<String> mutations() {
    List<String> inputs =
        LanguageCorpusTest.entries().stream().map(LanguageCorpusTest.Entry::input).toList();
    return Arbitraries.randomValue(
        random -> mutate(inputs.get(random.nextInt(inputs.size())), random));
  }

  private static String mutate(String text, Random random) {
    StringBuilder out = new StringBuilder(text);
    int edits = 1 + random.nextInt(3);
    for (int k = 0; k < edits; k++) {
      int at = out.isEmpty() ? 0 : random.nextInt(out.length() + 1);
      switch (random.nextInt(4)) {
        case 0 -> {
          if (at < out.length()) {
            out.deleteCharAt(at);
          }
        }
        case 1 -> out.insert(at, TOKENS.get(random.nextInt(TOKENS.size())));
        case 2 -> out.insert(at, ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        default -> {
          int end = Math.min(out.length(), at + random.nextInt(6));
          out.insert(at, out.substring(at, end));
        }
      }
    }
    return out.toString();
  }

  /** Parses within the time bound to a script or a ParseException, and echoes what parses. */
  private static void total(String text) {
    long start = System.nanoTime();
    Script script;
    try {
      script = Parser.parseScript(text, Names.NONE);
    } catch (ParseException e) {
      assertThat(e.line()).isPositive();
      assertThat(e.column()).isPositive();
      script = null;
    }
    assertThat(System.nanoTime() - start).as(text).isLessThan(BOUND_NANOS);
    if (script == null) {
      return;
    }
    // Each statement is echoed in the environment it was parsed in.
    Set<String> functions = new HashSet<>();
    for (Statement s : script.statements()) {
      switch (s) {
        case Statement.Expression expression -> {
          Names names = Set.copyOf(functions)::contains;
          echoes(AsciiPrinter.INSTANCE, expression.expr(), names, text);
          echoes(UnicodePrinter.INSTANCE, expression.expr(), names, text);
          if (expression.expr() instanceof Apply a
              && a.head().name().equals("clear")
              && a.args().size() == 1
              && a.args().getFirst() instanceof Symbol name) {
            functions.remove(name.name());
          }
        }
        case Statement.Definition d -> {
          if (!d.params().isEmpty() || d.body() instanceof Lambda) {
            functions.add(d.name().name());
          } else {
            functions.remove(d.name().name());
          }
        }
      }
    }
  }

  private static void echoes(Printer printer, Expr e, Names names, String source) {
    String text = printer.print(e);
    Expr back;
    try {
      back = Parser.parseExpression(text, names);
    } catch (ParseException x) {
      throw new AssertionError(x + " in the echo " + text + " of " + source, x);
    }
    assertThat(back).as(text + " of " + source).isEqualTo(TypedShape.of(e));
  }

  @Property(tries = 10_000)
  void randomCharacters(@ForAll("characters") String text) {
    total(text);
  }

  @Property(tries = 10_000)
  void randomTokens(@ForAll("tokens") String text) {
    total(text);
  }

  @Property(tries = 10_000)
  void corpusMutations(@ForAll("mutations") String text) {
    total(text);
  }
}
