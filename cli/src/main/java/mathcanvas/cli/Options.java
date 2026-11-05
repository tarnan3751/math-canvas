package mathcanvas.cli;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.print.AsciiPrinter;
import mathcanvas.engine.print.LatexPrinter;
import mathcanvas.engine.print.Printer;
import mathcanvas.engine.print.UnicodePrinter;

/**
 * The command line: {@code mathcanvas-cli [options] [file.mc …]}.
 *
 * @param printer the output printer
 * @param precision the precision in digits
 * @param angle the angle mode
 * @param steps whether every evaluation prints its steps
 * @param check whether the files are corpus files to check rather than scripts to run
 * @param stats whether to print the rewriter's counts at the end of the run
 * @param version whether to print the version and exit
 * @param help whether to print the usage and exit
 * @param inputs the scripts to run, in order: files, and the text of each {@code --eval}
 */
record Options(
    Printer printer,
    int precision,
    AngleMode angle,
    boolean steps,
    boolean check,
    boolean stats,
    boolean version,
    boolean help,
    List<Input> inputs) {

  /** The usage text. */
  static final String USAGE =
      """
      usage: mathcanvas-cli [options] [file.mc ...]
        Runs each file as a script, in order, in one session; with no file and no
        --eval, reads statements from standard input (a REPL).
      options:
        -e, --eval TEXT     run TEXT as a script
        --ascii             print results in ASCII
        --unicode           print results in Unicode (the default)
        --latex             print results in LaTeX
        --precision N       digits of approximate values, 1 to 10000 (default 34)
        --angle MODE        rad, deg or grad (default rad)
        --steps             print the steps of every result
        --check             check the files as golden corpus files
        --stats             print the rule tries and rewrites of the run at its end
        --version           print the version
        -h, --help          print this help
      """;

  /**
   * One script to run.
   *
   * @param name the name errors give: the file as given, or {@code <eval>}
   * @param text the text of an {@code --eval}, or null for a file to read
   */
  record Input(String name, String text) {}

  /** A command line that is not one: its message goes to standard error, with the usage. */
  static final class UsageException extends Exception {

    private static final long serialVersionUID = 1L;

    UsageException(String message) {
      super(message);
    }
  }

  /**
   * Reads the command line.
   *
   * @param args the arguments
   * @return the options
   * @throws UsageException if an option is unknown, lacks its value or has a bad one
   */
  static Options parse(String[] args) throws UsageException {
    Printer printer = UnicodePrinter.INSTANCE;
    int precision = Settings.DEFAULT.precision();
    AngleMode angle = Settings.DEFAULT.angleMode();
    boolean steps = false;
    boolean check = false;
    boolean stats = false;
    boolean version = false;
    boolean help = false;
    List<Input> inputs = new ArrayList<>();
    for (int k = 0; k < args.length; k++) {
      String arg = args[k];
      switch (arg) {
        case "--ascii" -> printer = AsciiPrinter.INSTANCE;
        case "--unicode" -> printer = UnicodePrinter.INSTANCE;
        case "--latex" -> printer = LatexPrinter.INSTANCE;
        case "--steps" -> steps = true;
        case "--check" -> check = true;
        case "--stats" -> stats = true;
        case "--version" -> version = true;
        case "-h", "--help" -> help = true;
        case "-e", "--eval" -> inputs.add(new Input("<eval>", value(args, k++)));
        case "--precision" -> precision = precision(value(args, k++));
        case "--angle" -> angle = angle(value(args, k++));
        default -> {
          if (arg.startsWith("-")) {
            throw new UsageException("unknown option " + arg);
          }
          inputs.add(new Input(arg, null));
        }
      }
    }
    if (check && inputs.stream().anyMatch(i -> i.text() != null)) {
      throw new UsageException("--check takes files, not --eval");
    }
    if (check && inputs.isEmpty() && !version && !help) {
      throw new UsageException("--check needs a file");
    }
    return new Options(printer, precision, angle, steps, check, stats, version, help, inputs);
  }

  private static String value(String[] args, int k) throws UsageException {
    if (k + 1 >= args.length) {
      throw new UsageException(args[k] + " needs a value");
    }
    return args[k + 1];
  }

  private static int precision(String text) throws UsageException {
    try {
      int digits = Integer.parseInt(text);
      if (Settings.isPrecision(digits)) {
        return digits;
      }
    } catch (NumberFormatException e) {
      // reported below
    }
    throw new UsageException(
        "--precision must be an integer from "
            + Settings.MIN_PRECISION
            + " to "
            + Settings.MAX_PRECISION);
  }

  private static AngleMode angle(String text) throws UsageException {
    return switch (text) {
      case "rad" -> AngleMode.RADIANS;
      case "deg" -> AngleMode.DEGREES;
      case "grad" -> AngleMode.GRADIANS;
      default -> throw new UsageException("--angle must be rad, deg or grad");
    };
  }
}
