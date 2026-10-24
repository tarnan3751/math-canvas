package mathcanvas.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckerTest {

  private static Checker.Report check(String text) throws Options.UsageException {
    return Checker.check("t.mc", text, Options.parse(new String[] {"--check", "t.mc"}));
  }

  @Test
  void matchingExpectationsPass() throws Exception {
    Checker.Report r =
        check(
            """
            # Setup statements carry no expectation.
            a := 2;
            f(x) := x^2
            # => f(x) := x²
            f(a); f(3); f(4)
            # => 16
            [1,
             2] * [3, 4]
            # => 11
            1 + [1, 2]
            # !! a scalar and a vector
            2 3
            # !! a number cannot follow
            steps(1/2 + 1/3)
            # steps:
            # => 5/6
            """);
    assertThat(r).isEqualTo(new Checker.Report(8, 0, ""));
  }

  @Test
  void aDifferenceIsAFailureWithADiff() throws Exception {
    Checker.Report r =
        check(
            """
            1/3 + 1/6
            # => 1/3
            1 + 1
            # => 2
            1 + [1, 2]
            # !! lengths
            5!
            """);
    assertThat(r.statements()).isEqualTo(3);
    assertThat(r.failures()).isEqualTo(2);
    assertThat(r.diff())
        .isEqualTo(
            """
            --- t.mc (expected)
            +++ t.mc (actual)
            @@ t.mc:1 @@
             1/3 + 1/6
            -# => 1/3
            +# => 1/2
            @@ t.mc:5 @@
             1 + [1, 2]
            -# !! lengths
            +# !! cannot add a scalar and a vector
            """);
  }

  @Test
  void anUnexpectedErrorInSetupFails() throws Exception {
    Checker.Report r = check("a := 1\nnth([1], 2);\na\n# => 1\n");
    assertThat(r.failures()).isEqualTo(1);
    assertThat(r.diff())
        .contains("@@ t.mc:2 @@\n nth([1], 2);\n+# !! nth: index 2 is out of range 1 to 1\n");
  }

  @Test
  void missingOutputAndUnexpectedStepsFail() throws Exception {
    Checker.Report quiet = check("1 + 1;\n# => 2\n");
    assertThat(quiet.failures()).isEqualTo(1);
    assertThat(quiet.diff()).contains("-# => 2\n");
    Checker.Report steps = check("1 + 1\n# steps:\n#   step 1: add\n#   = 2\n# => 2\n");
    assertThat(steps.failures()).isEqualTo(1);
    assertThat(steps.diff()).contains("-# steps:\n-#   step 1: add\n-#   = 2\n-# => 2\n+# => 2\n");
  }

  @Test
  void aStepsBlockEndsAtTheFirstLineThatIsNotAStep() throws Exception {
    assertThat(check("1 + 1\n# steps:\n# => 2\n#   step 1: add\n").failures()).isZero();
    assertThat(check("1 + 1\n# steps:\n#    an indented comment\n# => 2\n").failures()).isZero();
    Checker.Report step = check("1 + 1\n# steps:\n#   step 1: add\n#    note\n#   = 2\n# => 2\n");
    assertThat(step.diff())
        .isEqualTo(
            "--- t.mc (expected)\n+++ t.mc (actual)\n@@ t.mc:1 @@\n 1 + 1\n"
                + "-# steps:\n-#   step 1: add\n-# => 2\n+# => 2\n");
  }

  @Test
  void aCrLfFileChecksLikeAnLfOne(@TempDir Path dir) throws Exception {
    Checker.Report r = check("a := 2;\r\na^3\r\n# => 8\r\n5!\r# => 12\r\n");
    assertThat(r.failures()).isEqualTo(1);
    assertThat(r.diff())
        .isEqualTo(
            "--- t.mc (expected)\n+++ t.mc (actual)\n@@ t.mc:4 @@\n 5!\n-# => 12\n+# => 120\n");
    Path file = Files.writeString(dir.resolve("crlf.mc"), "2^3^2\r\n# => 512\r\n");
    MainTest.Run run = MainTest.run("--check", file.toString());
    assertThat(run.out())
        .doesNotContain("\r")
        .endsWith("corpus: 1 files, 1 statements, 0 failures\n");
  }

  @Test
  void anExpectationBeforeAnyStatementFails() throws Exception {
    Checker.Report r = check("# => 1\n1\n");
    assertThat(r.failures()).isEqualTo(1);
    assertThat(r.diff()).contains("@@ t.mc:1 @@\n # => 1\n-# => 1\n+# (no statement before)\n");
  }

  @Test
  void checkAllReportsEachFileAndTheTotals(@TempDir Path dir) throws IOException {
    Path good = Files.writeString(dir.resolve("good.mc"), "2^3^2\n# => 512\n-2^2\n# => -4\n");
    Path bad = Files.writeString(dir.resolve("bad.mc"), "5!\n# => 12\n");
    MainTest.Run r = MainTest.run("--check", good.toString(), bad.toString());
    assertThat(r.status()).isEqualTo(1);
    assertThat(r.out())
        .isEqualTo(
            "ok "
                + good
                + " (2 statements)\n"
                + "FAILED "
                + bad
                + " (1 statements, 1 failure)\n"
                + "--- "
                + bad
                + " (expected)\n+++ "
                + bad
                + " (actual)\n@@ "
                + bad
                + ":1 @@\n 5!\n-# => 12\n+# => 120\n"
                + "corpus: 2 files, 3 statements, 1 failures\n");
    assertThat(MainTest.run("--check", good.toString()).status()).isZero();
  }
}
