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
    assertThat(run("--angle", "deg", "-e", "sin(30)").out()).isEqualTo("1/2\n");
  }

  @Test
  void aParseErrorStopsTheScriptWithItsPositionAndACaret() {
    Run r = run("-e", "1 + 1\n\tx + (2 *\n3");
    assertThat(r.status()).isEqualTo(1);
    assertThat(r.out()).isEqualTo("2\n");
    assertThat(r.err()).isEqualTo("<eval>:3:2: missing `)`\n3\n ^\n");
    Run bad = run("-e", "a := 1; 2 3");
    assertThat(bad.out()).isEmpty();
    assertThat(bad.err())
        .isEqualTo(
            "<eval>:1:11: a number cannot come right after a factor: put `*` between them, as in `x*2`, or put the number first, as in `2x`\na := 1; 2 3\n          ^\n");
  }

  @Test
  void anEvaluationErrorIsReportedAtItsNode() {
    Run r = run("-e", "f(x) := x\n\t1 + f(1, 2)\n5");
    assertThat(r.status()).isEqualTo(1);
    assertThat(r.out()).isEqualTo("f(x) := x\n");
    assertThat(r.err()).isEqualTo("<eval>:2:6: `f` takes 1 argument\n\t1 + f(1, 2)\n\t    ^\n");
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
    assertThat(r.err()).isEqualTo(b + ":3:4: an expression is missing here\nb +\n   ^\n");
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
            "<stdin>:2:4: an expression is missing here\na +\n   ^\n"
                + "<stdin>:6:3: a number cannot come right after a factor: put `*` between them, as in `x*2`, or put the number first, as in `2x`\n2 3\n  ^\n");
  }

  @Test
  void theReplEndsAtExitAndShowsPromptsOnATerminal() {
    assertThat(run("1\nexit\n2\n", false).out()).isEqualTo("1\n");
    assertThat(run("1\n  quit  \n2\n", false).out()).isEqualTo("1\n");
    assertThat(run("(1 +\n2)\n", true).out()).isEqualTo("> … 3\n> \n");
  }

  @Test
  void programsPrintTheirOutputAndTheReplJoinsTheirLines() {
    String program = "function f(n)\n  for k in 1..n\n    print(k^2)\n  end\n  n\nend\nf(3)\n";
    assertThat(run("-e", program).out()).isEqualTo("function f(n)\n1\n4\n9\n3\n");
    assertThat(run(program, true).out()).isEqualTo("> … … … … … function f(n)\n> 1\n4\n9\n3\n> \n");
    // what a failing statement printed comes before its error
    Run r = run("-e", "for k in 1..3 do print(k); if k = 2 then [1] + [1, 2] end end");
    assertThat(r.out()).isEqualTo("1\n2\n");
    assertThat(r.err()).startsWith("<eval>:1:42: cannot add vectors of lengths 1 and 2\n");
  }

  @Test
  void stepsNarrateARun() {
    assertThat(run("--steps", "-e", "for k in 1..2 do k end").out())
        .isEqualTo(
            "  step 1: Line 1: pass 1 of 2 through the loop, with k\n  = 1\n"
                + "  step 2: Line 1: k\n  = 1\n"
                + "  step 3: Line 1: pass 2 of 2 through the loop, with k\n  = 2\n"
                + "  step 4: Line 1: k\n  = 2\n");
  }

  @Test
  void stepsPrintsTheValueOfEveryTracedStatement() {
    String none = "  (no steps to show)\n";
    assertThat(run("--steps", "-e", "1/2 + 1/3").out()).isEqualTo(none + "5/6\n");
    assertThat(run("-e", "steps(1/2 + 1/3)").out()).isEqualTo(none + "5/6\n");
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
    assertThat(eval.err()).isEqualTo("<eval>:2:5: an expression is missing here\n\tb +\n\t   ^\n");
    Path file = Files.writeString(dir.resolve("crlf.mc"), "1 + 1\r\n[1,\r 2] * [3, 4]\r\nx +\r\n");
    Run script = run(file.toString());
    assertThat(script.out()).isEqualTo("2\n11\n");
    assertThat(script.err()).isEqualTo(file + ":4:4: an expression is missing here\nx +\n   ^\n");
    Run repl = run("1\r\n2 3\r\n", false);
    assertThat(repl.out()).isEqualTo("1\n");
    assertThat(repl.err())
        .isEqualTo(
            "<stdin>:2:3: a number cannot come right after a factor: put `*` between them, as in `x*2`, or put the number first, as in `2x`\n2 3\n  ^\n");
    for (Run r : List.of(eval, script, repl, run("--version"), run("--bogus"))) {
      assertThat(r.out() + r.err()).doesNotContain("\r");
    }
  }

  @Test
  void statsFollowTheRunOnStandardError() {
    Run plain = run("-e", "simplify(sin(x)^2 + cos(x)^2)");
    Run counted = run("--stats", "-e", "simplify(sin(x)^2 + cos(x)^2)");

    assertThat(counted.status()).isZero();
    assertThat(counted.out()).isEqualTo(plain.out()).isEqualTo("1\n");
    assertThat(plain.err()).isEmpty();
    List<String> lines = counted.err().lines().toList();
    assertThat(lines.getFirst()).matches("stats: [1-9][0-9]* rule tries, [1-9][0-9]* rewrites");
    assertThat(lines).hasSizeBetween(2, 11);
    assertThat(lines.subList(1, lines.size()))
        .allMatch(l -> l.matches("stats: +[0-9]+ tries +[0-9]+ rewrites  [a-z-]+ [a-z0-9-]+"));
    // a run that fails still reports its counts, after the error
    Run failed = run("--stats", "-e", "1 +");
    assertThat(failed.status()).isEqualTo(1);
    assertThat(failed.err()).startsWith(run("-e", "1 +").err()).contains("\nstats: ");
  }

  @Test
  void versionAndHelp() {
    assertThat(run("--version"))
        .isEqualTo(new Run(0, "mathcanvas-cli " + Version.current() + "\n", ""));
    assertThat(run("--help").out()).startsWith("usage: mathcanvas-cli [options] [file.mc ...]\n");
  }
}
