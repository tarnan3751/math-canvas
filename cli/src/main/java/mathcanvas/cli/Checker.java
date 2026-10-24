package mathcanvas.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * The golden corpus checker ({@code mathcanvas-cli --check file.mc …}). A corpus file is an
 * ordinary script whose expectations are comments on the lines after a statement:
 *
 * <ul>
 *   <li>{@code # => text}: a line the statement prints on standard output, one per printed line, in
 *       order;
 *   <li>{@code # !! gist}: the statement fails, with a message containing {@code gist};
 *   <li>{@code # steps:} followed by step lines, each {@code #}, exactly three spaces and the text
 *       ({@code step 1: …}, then {@code = …}): the narration steps it prints before its value, each
 *       printed line being the step line after {@code # }. The block ends at the first line not of
 *       that form, so an indented comment after it is never read as a step.
 * </ul>
 *
 * <p>An expectation belongs to the statements that start on the last line, before it, on which a
 * statement starts; their printed lines are compared in order. A statement with no expectation is
 * run for its effect and its output is ignored, but it must not fail. The file runs in a session of
 * its own, past failures, and is compared statement by statement: the report is {@code ok <file>
 * (<n> statements)}, or {@code FAILED <file> (…)} followed by a unified diff of the expected and
 * the actual expectation lines of each statement that differs, which can be pasted back into the
 * file.
 */
final class Checker {

  /**
   * The check of one file.
   *
   * @param statements the statements that carry an expectation
   * @param failures the statements that differ from their expectations
   * @param diff the unified diff of the differences, empty when there is none
   */
  record Report(int statements, int failures, String diff) {}

  /** What a group of statements is expected to print, from the comments after it. */
  private static final class Expected {
    final List<String> lines = new ArrayList<>();
    final List<String> comments = new ArrayList<>();
    String gist;
  }

  private Checker() {}

  /**
   * Checks every file of the command line, printing a line per file, the diffs, and a summary line
   * {@code corpus: <files> files, <statements> statements, <failures> failures}.
   *
   * @param options the command line, its inputs the files
   * @param out where the report goes
   * @return true if every file passed
   */
  static boolean checkAll(Options options, PrintStream out) {
    int statements = 0;
    int failures = 0;
    for (Options.Input input : options.inputs()) {
      String name = input.name();
      String text;
      try {
        text = Main.read(Path.of(name));
      } catch (IOException e) {
        out.println("FAILED " + name);
        out.println(Main.cannotRead(name, e));
        failures++;
        continue;
      }
      Report r = check(name, text, options);
      statements += r.statements();
      failures += r.failures();
      if (r.failures() == 0) {
        out.println("ok " + name + " (" + r.statements() + " statements)");
      } else {
        out.println(
            "FAILED "
                + name
                + " ("
                + r.statements()
                + " statements, "
                + r.failures()
                + (r.failures() == 1 ? " failure)" : " failures)"));
        out.print(r.diff());
      }
    }
    out.println(
        "corpus: "
            + options.inputs().size()
            + " files, "
            + statements
            + " statements, "
            + failures
            + " failures");
    return failures == 0;
  }

  /**
   * Checks one corpus file in a session of its own.
   *
   * @param name the file's name, for the diff
   * @param text its text
   * @param options the printer and settings to run it with
   * @return the report
   */
  static Report check(String name, String text, Options options) {
    Runner runner =
        new Runner(options.printer(), options.precision(), options.angle(), options.steps());
    TreeMap<Integer, List<Runner.Result>> actual = new TreeMap<>();
    for (Units.Unit unit : Units.of(text)) {
      for (Runner.Result r : runner.run(unit, false)) {
        actual.computeIfAbsent(r.line(), k -> new ArrayList<>()).add(r);
      }
    }
    String[] lines = text.split("\r\n|\r|\n", -1);
    TreeMap<Integer, Expected> expected = new TreeMap<>();
    StringBuilder diff = new StringBuilder();
    int failures = 0;
    boolean steps = false;
    for (int k = 1; k <= lines.length; k++) {
      String line = lines[k - 1].stripLeading();
      boolean stepLine = steps && isStepLine(line);
      steps = stepLine || line.equals("# steps:");
      if (!stepLine && !steps && !line.startsWith("# =>") && !line.startsWith("# !!")) {
        continue;
      }
      Integer owner = actual.lowerKey(k);
      if (owner == null) {
        failures++;
        diff.append(hunk(name, k, lines[k - 1], List.of(line), List.of("# (no statement before)")));
        continue;
      }
      Expected e = expected.computeIfAbsent(owner, o -> new Expected());
      e.comments.add(line);
      if (stepLine) {
        e.lines.add(line.substring(2));
      } else if (line.startsWith("# =>")) {
        e.lines.add(line.substring(line.startsWith("# => ") ? 5 : 4));
      } else if (line.startsWith("# !!")) {
        e.gist = line.substring(4).strip();
      }
    }
    int statements = 0;
    for (int owner : actual.keySet()) {
      List<Runner.Result> results = actual.get(owner);
      Expected e = expected.get(owner);
      if (e != null) {
        statements += results.size();
      }
      if (!matches(results, e)) {
        failures++;
        diff.append(
            hunk(
                name, owner, lines[owner - 1], e == null ? List.of() : e.comments, shown(results)));
      }
    }
    String header =
        diff.isEmpty() ? "" : "--- " + name + " (expected)\n+++ " + name + " (actual)\n";
    return new Report(statements, failures, header + diff);
  }

  /** Whether a line is {@code #}, exactly three spaces, then text: a line of a steps block. */
  private static boolean isStepLine(String line) {
    return line.startsWith("#   ") && line.length() > 4 && line.charAt(4) != ' ';
  }

  private static boolean matches(List<Runner.Result> results, Expected e) {
    List<String> printed = new ArrayList<>();
    List<String> messages = new ArrayList<>();
    for (Runner.Result r : results) {
      printed.addAll(r.lines());
      if (r.failure() != null) {
        messages.add(r.failure().message());
      }
    }
    if (e == null) {
      return messages.isEmpty();
    }
    boolean errorsMatch =
        e.gist == null ? messages.isEmpty() : messages.stream().anyMatch(m -> m.contains(e.gist));
    return errorsMatch && printed.equals(e.lines);
  }

  /** The results as expectation comments, as a corpus file would hold them. */
  private static List<String> shown(List<Runner.Result> results) {
    List<String> out = new ArrayList<>();
    for (Runner.Result r : results) {
      boolean inSteps = false;
      for (String line : r.lines()) {
        boolean step = line.startsWith("  ");
        if (step && !inSteps) {
          out.add("# steps:");
        }
        inSteps = step;
        out.add(step ? "# " + line : "# => " + line);
      }
      if (r.failure() != null) {
        out.add("# !! " + r.failure().message());
      }
    }
    return out;
  }

  private static String hunk(
      String name, int line, String source, List<String> expected, List<String> actual) {
    StringBuilder out = new StringBuilder();
    out.append("@@ ").append(name).append(':').append(line).append(" @@\n");
    out.append(' ').append(source).append('\n');
    expected.forEach(l -> out.append('-').append(l).append('\n'));
    actual.forEach(l -> out.append('+').append(l).append('\n'));
    return out.toString();
  }
}
