package mathcanvas.app.persist;

import java.io.Serial;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Where the app keeps its files. In order: the {@code --data-dir=<dir>} or {@code --data-dir <dir>}
 * argument, the system property {@code mathcanvas.dataDir}, then the platform's folder for
 * application data: {@code ~/Library/Application Support/math-canvas} on macOS, {@code
 * %APPDATA%\math-canvas} on Windows ({@code ~\AppData\Roaming\math-canvas} when {@code APPDATA} is
 * unset), and on Linux and other systems {@code $XDG_DATA_HOME/math-canvas} when that variable
 * holds an absolute path, else {@code ~/.local/share/math-canvas}. A relative directory is resolved
 * against the working directory. The directory is created when the app first uses it, not here.
 *
 * <p>A directory that is asked for but not given is an error, never a reason to fall back to the
 * platform's folder, which is the owner's real data: an empty or blank argument, {@code --data-dir}
 * as the last argument or followed by another option, or a blank property. The app reports it and
 * does not start.
 */
public final class DataDirectory {

  /** The argument that names the directory, followed by {@code =} or by the next argument. */
  public static final String ARGUMENT = "--data-dir";

  /** Why a data directory that was asked for is not one. */
  public enum Problem {
    /** {@code --data-dir=} with nothing, or blanks, after it, or a blank next argument. */
    EMPTY_ARGUMENT,
    /** {@code --data-dir} as the last argument, or followed by another option ({@code --…}). */
    MISSING_ARGUMENT,
    /** The system property is set but blank. */
    BLANK_PROPERTY
  }

  /** A data directory that was asked for but not given. */
  public static final class InvalidException extends Exception {

    @Serial private static final long serialVersionUID = 1L;

    private final Problem problem;

    /**
     * An exception.
     *
     * @param problem what is wrong
     */
    public InvalidException(Problem problem) {
      super(problem.name());
      this.problem = problem;
    }

    /**
     * What is wrong.
     *
     * @return the problem
     */
    public Problem problem() {
      return problem;
    }
  }

  /** The system property that names the directory. */
  public static final String PROPERTY = "mathcanvas.dataDir";

  private static final String NAME = "math-canvas";

  private DataDirectory() {}

  /**
   * The data directory of this process: from its arguments, its system properties, its environment
   * and its platform.
   *
   * @param args the application's arguments
   * @return the absolute directory
   * @throws InvalidException if a directory is asked for but not given
   */
  public static Path resolve(List<String> args) throws InvalidException {
    return resolve(
        args,
        System::getProperty,
        System::getenv,
        System.getProperty("os.name"),
        Path.of(System.getProperty("user.home")));
  }

  /**
   * The data directory from the given sources.
   *
   * @param args the application's arguments
   * @param property the system properties, null for an unset one
   * @param env the environment variables, null for an unset one
   * @param osName the {@code os.name} of the platform
   * @param home the user's home directory
   * @return the absolute directory
   * @throws InvalidException if a directory is asked for but not given
   */
  public static Path resolve(
      List<String> args,
      UnaryOperator<String> property,
      UnaryOperator<String> env,
      String osName,
      Path home)
      throws InvalidException {
    Optional<String> named = argument(args);
    if (named.isEmpty()) {
      String p = property.apply(PROPERTY);
      if (p != null && p.isBlank()) {
        throw new InvalidException(Problem.BLANK_PROPERTY);
      }
      named = Optional.ofNullable(p);
    }
    if (named.isPresent()) {
      return Path.of(named.get()).toAbsolutePath();
    }
    String os = osName.toLowerCase(Locale.ROOT);
    if (os.startsWith("mac")) {
      return home.resolve("Library").resolve("Application Support").resolve(NAME);
    }
    if (os.startsWith("windows")) {
      return absolute(env.apply("APPDATA"))
          .orElse(home.resolve("AppData").resolve("Roaming"))
          .resolve(NAME);
    }
    return absolute(env.apply("XDG_DATA_HOME"))
        .orElse(home.resolve(".local").resolve("share"))
        .resolve(NAME);
  }

  /**
   * The directory the arguments name, if one does: the first {@code --data-dir=<dir>} or {@code
   * --data-dir <dir>}.
   *
   * @param args the application's arguments
   * @return the directory, or empty when no argument names one
   * @throws InvalidException if the first such argument gives no directory
   */
  public static Optional<String> argument(List<String> args) throws InvalidException {
    for (int k = 0; k < args.size(); k++) {
      String a = args.get(k);
      String value;
      if (a.equals(ARGUMENT)) {
        if (k + 1 == args.size() || args.get(k + 1).startsWith("--")) {
          throw new InvalidException(Problem.MISSING_ARGUMENT);
        }
        value = args.get(k + 1);
      } else if (a.startsWith(ARGUMENT + "=")) {
        value = a.substring(ARGUMENT.length() + 1);
      } else {
        continue;
      }
      if (value.isBlank()) {
        throw new InvalidException(Problem.EMPTY_ARGUMENT);
      }
      return Optional.of(value);
    }
    return Optional.empty();
  }

  /**
   * The arguments that are not about the data directory: everything but each {@code
   * --data-dir=<dir>}, and each {@code --data-dir} with the argument after it.
   *
   * @param args the application's arguments
   * @return the others, in order
   */
  public static List<String> otherArguments(List<String> args) {
    List<String> out = new ArrayList<>();
    for (int k = 0; k < args.size(); k++) {
      String a = args.get(k);
      if (a.equals(ARGUMENT)) {
        k++;
      } else if (!a.startsWith(ARGUMENT + "=")) {
        out.add(a);
      }
    }
    return out;
  }

  private static Optional<String> nonBlank(String s) {
    return s == null || s.isBlank() ? Optional.empty() : Optional.of(s);
  }

  private static Optional<Path> absolute(String s) {
    return nonBlank(s).map(Path::of).filter(Path::isAbsolute);
  }
}
