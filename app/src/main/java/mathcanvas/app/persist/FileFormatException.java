package mathcanvas.app.persist;

import java.io.Serial;

/** A file that is JSON but not a file this release can read: a wrong shape or version. */
public final class FileFormatException extends Exception {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * An exception.
   *
   * @param message what is wrong
   */
  public FileFormatException(String message) {
    super(message);
  }
}
