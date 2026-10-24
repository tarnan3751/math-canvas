package mathcanvas.cli;

import java.io.BufferedReader;
import java.io.Console;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import mathcanvas.engine.Version;

/**
 * The command-line entry point: {@code mathcanvas-cli [options] [file.mc …]}. With files or {@code
 * --eval}, runs each as a script in one session, in order, and stops at the first error; with
 * {@code --check}, checks each file as a golden corpus file ({@link Checker}); otherwise reads
 * statements from standard input, a REPL that reports an error and goes on. Text is read and
 * written as UTF-8. Exit status: 0 for a clean run, 1 for an error or a failed check, 2 for a bad
 * command line.
 *
 * <p>Input is parsed a unit at a time ({@link Units}): a line, or the lines an open bracket joins.
 * A unit is parsed whole before its statements run, so a parse error runs none of its statements.
 * The CLI writes no file; it reads only standard input and the files named.
 */
public final class Main {

  private Main() {}

  /**
   * Runs the command line and exits with its status.
   *
   * @param args the arguments
   */
  public static void main(String[] args) {
    PrintStream out =
        new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
    PrintStream err =
        new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
    Console console = System.console();
    boolean interactive = console != null && console.isTerminal();
    System.exit(run(args, System.in, out, err, interactive));
  }

  /**
   * Runs a command line.
   *
   * @param args the arguments
   * @param in standard input
   * @param out standard output
   * @param err standard error
   * @param interactive whether standard input is a terminal, so the REPL shows its prompts
   * @return the exit status
   */
  static int run(
      String[] args, InputStream in, PrintStream out, PrintStream err, boolean interactive) {
    Options options;
    try {
      options = Options.parse(args);
    } catch (Options.UsageException e) {
      err.println("mathcanvas-cli: " + e.getMessage());
      err.print(Options.USAGE);
      return 2;
    }
    if (options.help()) {
      out.print(Options.USAGE);
      return 0;
    }
    if (options.version()) {
      out.println("mathcanvas-cli " + Version.current());
      return 0;
    }
    if (options.check()) {
      return Checker.checkAll(options, out) ? 0 : 1;
    }
    Runner runner =
        new Runner(options.printer(), options.precision(), options.angle(), options.steps());
    if (options.inputs().isEmpty()) {
      return repl(runner, in, out, err, interactive);
    }
    for (Options.Input input : options.inputs()) {
      String text;
      try {
        text = input.text() != null ? input.text() : read(Path.of(input.name()));
      } catch (IOException e) {
        err.println(cannotRead(input.name(), e));
        return 1;
      }
      for (Units.Unit unit : Units.of(text)) {
        for (Runner.Result r : runner.run(unit, true)) {
          r.lines().forEach(out::println);
          if (r.failure() != null) {
            err.print(r.failure().format(input.name()));
            return 1;
          }
        }
      }
    }
    return 0;
  }

  /**
   * Reads a script file as UTF-8.
   *
   * @param file the file
   * @return its text
   * @throws IOException if it cannot be read or is not UTF-8
   */
  static String read(Path file) throws IOException {
    return Files.readString(file, StandardCharsets.UTF_8);
  }

  /**
   * The message for a file that cannot be read.
   *
   * @param name the file as given
   * @param e why
   * @return the message
   */
  static String cannotRead(String name, IOException e) {
    String reason =
        switch (e) {
          case NoSuchFileException missing -> "no such file";
          case MalformedInputException malformed -> "not UTF-8 text";
          default -> e.getMessage();
        };
    return "mathcanvas-cli: cannot read " + name + ": " + reason;
  }

  /**
   * The REPL: reads lines, joins those an open bracket continues, runs each unit and prints its
   * results, and reports an error without stopping. {@code exit} or {@code quit} on a line of its
   * own, or the end of input, ends it. The prompt, {@code > } or {@code … } on a continuation line,
   * is shown when standard input is a terminal.
   */
  private static int repl(
      Runner runner, InputStream in, PrintStream out, PrintStream err, boolean interactive) {
    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    StringBuilder pending = new StringBuilder();
    int lineNumber = 0;
    int first = 1;
    try {
      while (true) {
        if (interactive) {
          out.print(pending.isEmpty() ? "> " : "… ");
          out.flush();
        }
        String line = reader.readLine();
        if (line == null) {
          if (!pending.isEmpty()) {
            print(runner.run(new Units.Unit(pending.toString(), first), false), out, err);
          }
          if (interactive) {
            out.println();
          }
          return 0;
        }
        lineNumber++;
        if (pending.isEmpty() && (line.strip().equals("exit") || line.strip().equals("quit"))) {
          return 0;
        }
        if (pending.isEmpty()) {
          first = lineNumber;
        }
        pending.append(line).append('\n');
        if (!Units.open(pending.toString())) {
          print(runner.run(new Units.Unit(pending.toString(), first), false), out, err);
          pending.setLength(0);
        }
      }
    } catch (IOException e) {
      err.println("mathcanvas-cli: cannot read standard input: " + e.getMessage());
      return 1;
    }
  }

  private static void print(List<Runner.Result> results, PrintStream out, PrintStream err) {
    for (Runner.Result r : results) {
      r.lines().forEach(out::println);
      if (r.failure() != null) {
        out.flush();
        err.print(r.failure().format("<stdin>"));
        err.flush();
      }
    }
  }
}
