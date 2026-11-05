package mathcanvas.app.help;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import mathcanvas.app.json.JsonIo;
import mathcanvas.engine.builtin.Builtin;
import mathcanvas.engine.builtin.Builtins;
import org.junit.jupiter.api.Test;

class HelpEntriesTest {

  @Test
  void everyBuiltinHasAnEntry() {
    List<String> missing =
        Builtins.all().stream()
            .map(Builtin::name)
            .filter(n -> HelpEntries.lookup(n).isEmpty())
            .toList();

    assertThat(missing).as("builtins without a help entry in docs/language.md §12").isEmpty();
  }

  @Test
  void everyEntryHasAnExample() {
    List<String> bare =
        HelpEntries.all().stream()
            .filter(e -> e.examples().isEmpty())
            .map(HelpEntry::name)
            .toList();

    assertThat(bare).as("help entries without an example in docs/language.md").isEmpty();
  }

  @Test
  void theEntriesCarryWhatTheReferenceSays() {
    HelpEntry sqrt = HelpEntries.lookup("sqrt").orElseThrow();
    assertThat(sqrt.category()).isEqualTo("Functions");
    assertThat(sqrt.section()).isEqualTo("12.1");
    assertThat(sqrt.arity()).isEqualTo("1");
    assertThat(sqrt.alias()).isEqualTo("√");

    HelpEntry solve = HelpEntries.lookup("solve").orElseThrow();
    assertThat(solve.category()).isEqualTo("Commands");
    assertThat(solve.description()).startsWith("solve(rel | {rels}, var | [vars])");
    assertThat(solve.examples().getFirst())
        .isEqualTo(
            new HelpEntry.Example("solve(sin(x) = 1/2, x)", "{2πk + π/6, 2πk + 5π/6}, k ∈ ℤ"));

    // a function's entry comes before the operator's of the same spelling
    assertThat(HelpEntries.lookup("mod").orElseThrow().category()).isEqualTo("Functions");
    HelpEntry power = HelpEntries.lookup("^").orElseThrow();
    assertThat(power.category()).isEqualTo("Operators");
    assertThat(power.examples()).extracting(HelpEntry.Example::input).isNotEmpty();
    assertThat(HelpEntries.lookup("lim")).isPresent();
    assertThat(HelpEntries.lookup("nosuchname")).isEmpty();
  }

  @Test
  void gettingStartedIsTheFirstEntryWithEveryStep() {
    HelpEntry first = HelpEntries.all().getFirst();
    assertThat(first.name()).isEqualTo("Getting started");
    assertThat(first.section()).isEqualTo(HelpEntries.GETTING_STARTED);
    assertThat(first.description()).startsWith("Work out an expression");
    assertThat(first.examples())
        .extracting(HelpEntry.Example::input)
        .containsExactly(
            "sqrt(8) + 1/3",
            "f(x) := x^2 - 2x",
            "plot(x^2 - 2x)",
            "a := slider(-3, 3, 1)",
            "steps(solve(x^2 - 2x = 0, x))");
    // its steps are no other entry's examples
    assertThat(HelpEntries.lookup("sqrt").orElseThrow().examples())
        .extracting(HelpEntry.Example::input)
        .doesNotContain("sqrt(8) + 1/3");
  }

  @Test
  void aFileOfAnotherShapeIsRefused() throws IOException {
    assertThatThrownBy(() -> HelpEntries.decode(JsonIo.parse("{\"entries\": [{\"name\": 1}]}")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(HelpEntries.decode(JsonIo.parse("{\"version\": 1, \"entries\": []}"))).isEmpty();
  }
}
