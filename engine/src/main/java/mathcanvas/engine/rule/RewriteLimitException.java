package mathcanvas.engine.rule;

import java.io.Serial;

/** A rewrite needed more rewrites than its context's cap: a rule set that does not terminate. */
public final class RewriteLimitException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final String ruleSet;
  private final String rule;
  private final transient Path path;

  RewriteLimitException(String ruleSet, String rule, Path path, int cap) {
    super(
        "more than "
            + cap
            + " rewrites: the last rule was "
            + ruleSet
            + "."
            + rule
            + " at "
            + path.indices());
    this.ruleSet = ruleSet;
    this.rule = rule;
    this.path = path;
  }

  /**
   * Returns the rule set's name.
   *
   * @return the name
   */
  public String ruleSet() {
    return ruleSet;
  }

  /**
   * Returns the name of the rule that would have exceeded the cap.
   *
   * @return the name
   */
  public String rule() {
    return rule;
  }

  /**
   * Returns where that rule matched.
   *
   * @return the path
   */
  public Path path() {
    return path;
  }
}
