package mathcanvas.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import mathcanvas.engine.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

  /** What a run printed and returned. */
  record Run(int status, String out, String err) {}

  static Run run(String input, boolean interactive, String... args) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int status =
        Main.run(
            args,
            new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8),
            interactive);
    return new Run(
        status, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
  }

  static Run run(String... args) {
    return run("", false, args);
  }

  @Test
  void evalPrintsInEachPrinter() {
    assertThat(run("-e", "1/3 + 1/6")).isEqualTo(new Run(0, "1/2\n", ""));
    assertThat(run("--ascii", "-e", "1/3 + 1/6").out()).isEqualTo("1/2\n");
    assertThat(run("--latex", "-e", "1/3 + 1/6").out()).isEqualTo("\\frac{1}{2}\n");
    assertThat(run("-e", "sqrt(2) x^2").out()).isEqualTo("x² √2\n");
    assertThat(run("--ascii", "-e", "sqrt(2) x^2").out()).isEqualTo("x^2*sqrt(2)\n");
  }

  @Test
  void definitionsEchoAndQuietStatementsPrintNothing() {
    assertThat(run("-e", "f(x) := x^2 + 1\nf(2)\na := 3;\na + 1; 7").out())
        .isEqualTo("f(x) := x² + 1\n5\n7\n");
    assertThat(run("--ascii", "-e", "g := t -> t^2").out()).isEqualTo("g := t -> t^2\n");
  }

  @Test
  void settingsApply() {
    assertThat(run("--precision", "5", "-e", "numeric(1/3)").out()).isEqualTo("0.33333\n");
    assertThat(run("--angle", "deg", "-e", "sin(30)").out()).isEqualTo("sin(π/6)\n");
  }

  @Test
  void aParseErrorStopsTheScriptWithItsPositionAndACaret() {
    Run r = run("-e", "1 + 1\n\tx + (2 *\n3");
    assertThat(r.status()).isEqualTo(1);
    assertThat(r.out()).isEqualTo("2\n");
    assertThat(r.err()).isEqualTo("<eval>:3:2: expected `)`\n3\n ^\n");
    Run bad = run("-e", "a := 1; 2 3");
    assertThat(bad.out()).isEmpty();
    assertThat(bad.err())
        .isEqualTo(
            "<eval>:1:11: a number cannot follow a factor; write 2x or x*2\na := 1; 2 3\n          ^\n");
  }

  @Test
  void anEvaluationErrorIsReportedAtItsNode() {
    Run r = run("-e", "f(x) := x\n\t1 + f(1, 2)\n5");
    assertThat(r.status()).isEqualTo(1);
    assertThat(r.out()).isEqualTo("f(x) := x\n");
    assertThat(r.err()).isEqualTo("<eval>:2:6: f takes 1 argument\n\t1 + f(1, 2)\n\t    ^\n");
  }

  @Test
  void anErrorWithNoNodePositionIsReportedAtTheStatement() {
    Run r = run("-e", "f(x) := x + [1, 2]\n  π; f(1)");
    assertThat(r.err()).startsWith("<eval>:2:6: cannot add a scalar and a vector\n");
  }

  @Test
  void filesRunInOrderInOneSession(@TempDir Path dir) throws IOException {
    Path a = Files.writeString(dir.resolve("a.mc"), "a := 2;\n");
    Path b = Files.writeString(dir.resolve("b.mc"), "a^10\n# a comment\nb +\n");
    Run r = run(a.toString(), b.toString());
    assertThat(r.status()).isEqualTo(1);
    assertThat(r.out()).isEqualTo("1024\n");
    assertThat(r.err()).isEqualTo(b + ":3:4: expected an operand\nb +\n   ^\n");
  }

  @Test
  void anUnreadableFileIsAnError(@TempDir Path dir) throws IOException {
    assertThat(run(dir.resolve("none.mc").toString()))
        .isEqualTo(
            new Run(
                1,
                "",
                "mathcanvas-cli: cannot read " + dir.resolve("none.mc") + ": no such file\n"));
    Path latin = Files.write(dir.resolve("latin.mc"), new byte[] {'x', (byte) 0xE9, '\n'});
    assertThat(run(latin.toString()).err()).endsWith(": not UTF-8 text\n");
  }

  @Test
  void theReplGoesOnAfterAnErrorJoinsBracketsAndEndsAtEof() {
    Run r = run("a := 5\na +\n[1,\n 2] * [3,\n 4]\n2 3\na\n", false);
    assertThat(r.status()).isZero();
    assertThat(r.out()).isEqualTo("a := 5\n11\n5\n");
    assertThat(r.err())
        .isEqualTo(
            "<stdin>:2:4: expected an operand\na +\n   ^\n"
                + "<stdin>:6:3: a number cannot follow a factor; write 2x or x*2\n2 3\n  ^\n");
  }

  @Test
  void theReplEndsAtExitAndShowsPromptsOnATerminal() {
    assertThat(run("1\nexit\n2\n", false).out()).isEqualTo("1\n");
    assertThat(run("1\n  quit  \n2\n", false).out()).isEqualTo("1\n");
    assertThat(run("(1 +\n2)\n", true).out()).isEqualTo("> … 3\n> \n");
  }

  @Test
  void stepsPrintsTheValueOfEveryTracedStatement() {
    assertThat(run("--steps", "-e", "1/2 + 1/3").out()).isEqualTo("5/6\n");
    assertThat(run("-e", "steps(1/2 + 1/3)").out()).isEqualTo("5/6\n");
  }

  @Test
  void badCommandLinesExitWithTwo() {
    for (String[] args :
        new String[][] {
          {"--bogus"},
          {"-e"},
          {"--precision", "0"},
          {"--precision", "x"},
          {"--angle", "turn"},
          {"--check"},
          {"--check", "-e", "1"},
          {"-"}
        }) {
      Run r = run(args);
      assertThat(r.status()).as(String.join(" ", args)).isEqualTo(2);
      assertThat(r.err()).startsWith("mathcanvas-cli: ").contains("usage: mathcanvas-cli");
      assertThat(r.out()).isEmpty();
    }
  }

  @Test
  void outputEndsLinesInLfAndCrLfInputKeepsItsPositions(@TempDir Path dir) throws IOException {
    Run eval = run("-e", "a := 1\r\n\tb +\r\n");
    assertThat(eval.out()).isEqualTo("a := 1\n");
    assertThat(eval.err()).isEqualTo("<eval>:2:5: expected an operand\n\tb +\n\t   ^\n");
    Path file = Files.writeString(dir.resolve("crlf.mc"), "1 + 1\r\n[1,\r 2] * [3, 4]\r\nx +\r\n");
    Run script = run(file.toString());
    assertThat(script.out()).isEqualTo("2\n11\n");
    assertThat(script.err()).isEqualTo(file + ":4:4: expected an operand\nx +\n   ^\n");
    Run repl = run("1\r\n2 3\r\n", false);
    assertThat(repl.out()).isEqualTo("1\n");
    assertThat(repl.err())
        .isEqualTo("<stdin>:2:3: a number cannot follow a factor; write 2x or x*2\n2 3\n  ^\n");
    for (Run r : List.of(eval, script, repl, run("--version"), run("--bogus"))) {
      assertThat(r.out() + r.err()).doesNotContain("\r");
    }
  }

  @Test
  void versionAndHelp() {
    assertThat(run("--version"))
        .isEqualTo(new Run(0, "mathcanvas-cli " + Version.current() + "\n", ""));
    assertThat(run("--help").out()).startsWith("usage: mathcanvas-cli [options] [file.mc ...]\n");
  }
}
