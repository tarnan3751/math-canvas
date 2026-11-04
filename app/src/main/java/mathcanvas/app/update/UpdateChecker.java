package mathcanvas.app.update;

import java.util.Optional;

/**
 * What the app asks at the start when the update check is on: {@link UpdateCheck#standard()}'s
 * check in the app, a stub in the tests.
 */
@FunctionalInterface
public interface UpdateChecker {

  /**
   * Asks for a newer release, blocking for at most the check's bound: called off the FX thread.
   *
   * @return a newer release, or empty
   */
  Optional<UpdateCheck.Release> check();
}
